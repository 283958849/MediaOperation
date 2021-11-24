package com.example.mediaoperation.ui.multimedia;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediaoperation.MainActivity;
import com.example.mediaoperation.databinding.FragmentMultimediaBinding;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


public class MultiMediaFragment extends Fragment {
    private final static String TAG = "MediaOperation.MultiMediaFragment";

    private FragmentMultimediaBinding binding;

    private Button mStartButton = null;
    private Button mStopButton = null;
    private Button mPlayButton = null;

    private Surface mSurface = null;
    private TextureView mTextureView = null;
    private CameraDevice mCameraDevice = null;
    private final static int mCameraID = CameraCharacteristics.LENS_FACING_FRONT; //后摄像头

    private static Handler mMainHandler = null;
    private static Handler mChildHandler = null;
    private CameraManager mCameraManager = null;
    private ImageReader mImageReader = null;
    private CaptureRequest.Builder mCaptureBuilder = null;
    private CameraCaptureSession mCaptureSession = null;

    private MediaFormat mVideoMediaFormat = null;
    private MediaFormat mAudioMediaFormat = null;
    private MediaMuxer mMediaMuxer = null;
    private MediaCodec mVideoMediaCodec = null;
    private MediaCodec mAudioMediaCodec = null;
    private AudioRecord mAudioRecord = null;
    private MediaPlayer mMediaPlayer = null;

    private final static String MIME_TYPE = "video/avc";
    private final static String MIME_TYPE_AAC = "audio/mp4a-latm";  //aac format
    private final static int FRAME_RATE = 10;
    private final static int I_FRAME_GAP = 5;
    private final static int PREVIEW_MIN_SIZE = 1080 * 720;
    private Range<Integer>[] mFpsRanges = null;

    private final static int AUDIO_SAMPLE_RATE = 16000;
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_STEREO;
    private final static int AUDIO_SAMPLE_SIZE = AudioFormat.ENCODING_PCM_16BIT;

    private byte[] mAudioBuffer = null;
    private byte[] mI420Rotated90Buffer = null;
    private int mAudioBufferSize = 0;
    private int mScreenYuvSize = -1;
    private int mWidth = 720;
    private int mHeight = 1080;

    private String mMp4FilePath = null;
    private boolean mIsPlaying = false;
    private boolean mIsRecording = false;
    private boolean mIsMuxing = false;

    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private BlockingQueue<byte[]> mPcmBlockingQueue = null;
    private BlockingQueue<byte[]> mYuvBlockingQueue = null;
    private BlockingQueue<AVData> mMp4BlockingQueue = null;
    private boolean mVideoThreadExit = false;
    private boolean mAudioThreadExit = false;
    private static String mYuvFilePath = "";
    private static String mRotatedYuvFilePath = "";


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        com.example.mediaoperation.ui.multimedia.MultiMeidaViewModel multiMediaViewModel =
                new ViewModelProvider(this).get(com.example.mediaoperation.ui.multimedia.MultiMeidaViewModel.class);
        binding = FragmentMultimediaBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textView;
        multiMediaViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        mStartButton = binding.startButton;
        mStopButton = binding.stopButton;
        mPlayButton = binding.playVideoButton;
        mTextureView = binding.textureView;

//        mTextureView.setAlpha(0.1f);
//        mTextureView.setScaleX(1);
//        mTextureView.setScaleY(0.8f);
//        mTextureView.setRotation(90);

        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        mPlayButton.setEnabled(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            mMp4FilePath = Environment.getExternalStorageDirectory().getPath() + "/terrylee.mp4";
        } else {
            mMp4FilePath = getContext().getExternalCacheDir().getPath() + "/terrylee.mp4";
        }

        // for test
        mMp4FilePath = getContext().getExternalCacheDir().getPath() + "/terrylee.mp4";
        Log.i(TAG, "======================mMp4FilePath: " + mMp4FilePath);

        mYuvFilePath = getContext().getExternalCacheDir().getPath() + "/flyman.yuv";
        mRotatedYuvFilePath = getContext().getExternalCacheDir().getPath() + "/flyman_r90.yuv";


        mPcmBlockingQueue = new LinkedBlockingDeque<>();
        mYuvBlockingQueue = new LinkedBlockingDeque<>();
        mMp4BlockingQueue = new LinkedBlockingDeque<>();

        mMainHandler = new Handler(Looper.getMainLooper());

        HandlerThread mHandlerThread = new HandlerThread("Camera2");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper());

        if (mCameraDevice == null) {
            mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        }

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                try {
                    CameraCharacteristics cs = mCameraManager.getCameraCharacteristics(String.valueOf(mCameraID));
                    StreamConfigurationMap map = cs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mFpsRanges = cs.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    for (Range<Integer> range : mFpsRanges) {
                        //Log.i(TAG, "*********************** rang=" + range.getLower() + ", " + range.getUpper());
                    }

                    Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
                    Size bestSize = getBestSizeForCameraPreview(previewSizes, width, height);

                    mWidth = bestSize.getWidth();
                    mHeight = bestSize.getHeight();
                    mScreenYuvSize = mWidth * mHeight * 3 / 2;
                    mI420Rotated90Buffer = new byte[mScreenYuvSize];

                    mTextureView.getSurfaceTexture().setDefaultBufferSize(mWidth, mHeight);
                    Log.i(TAG, "*******************last width=" + mWidth + ", height=" + mHeight);

                    mSurface = new Surface(mTextureView.getSurfaceTexture());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                releaseMediaPlayer();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsRecording) {
                    mIsRecording = true;

                    initImageReader();
                    openCamera();

                    initMediaFormat();
                    initMultiMediaClass();

                    mAudioRecord.startRecording();
                    new GrabPcmDataThread().start();

                    // start to handle video and audio data
                    new VideoCodecHandleThread().start();
                    new AudioCodecHandleThread().start();

                    mStopButton.setEnabled(true);
                    mPlayButton.setEnabled(false);
                }
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "wait codec to finish", Toast.LENGTH_SHORT).show();
                if (mIsRecording) {
                    mIsRecording = false;
                } else if (mIsPlaying) {
                    releaseMediaPlayer();
                    mStartButton.setEnabled(true);
                    mPlayButton.setEnabled(true);
                    mIsPlaying = false;
                }
            }
        });

        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartButton.setEnabled(false);
                mStopButton.setEnabled(true);
                mPlayButton.setEnabled(false);

                initMediaPlayer();
                play();
            }
        });

        return root;
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            if (mMediaPlayer == null) {
                Log.i(TAG, "*********initMediaPlayer failed!");
            } else {
                mMediaPlayer.setSurface(mSurface);
                Log.i(TAG, "*********initMediaPlayer ok!");
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        releaseMediaPlayer();
                        mPlayButton.setEnabled(true);
                        mStartButton.setEnabled(true);
                        mIsPlaying = false;
                    }
                });
            }
        }
    }

    private void play() {
        if (mMediaPlayer == null) {
            return;
        }
        if (mMediaPlayer.isPlaying()) {
            return;
        }
        try {
            Log.i(TAG, "*********play");

            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(getContext(), Uri.parse(mMp4FilePath));
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            mIsPlaying = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (mCameraManager == null) {
            return;
        }

        try {
            mCameraManager.openCamera(String.valueOf(mCameraID), stateCallback, mMainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initImageReader() {
        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YV12, 1);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();

                    int width = image.getWidth();
                    int height = image.getHeight();
                    int ySize = width * height;
                    byte[] bytes = new byte[ySize * 3 / 2]; //plane[0] -> Y

                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    buffer.get(bytes, 0, ySize);
                    Log.i(TAG, "buffer0.size:" + buffer.capacity() + " ys:" + planes[0].getRowStride() + ", ps:" + planes[0].getPixelStride());

                    buffer = planes[1].getBuffer(); //plane[1] -> U
                    Log.i(TAG, "buffer1.size:" + buffer.capacity() + " us:" + planes[1].getRowStride() + ", ps:" + planes[1].getPixelStride());
                    int uvSize = buffer.capacity();
                    buffer.get(bytes, ySize, uvSize);

                    buffer = planes[2].getBuffer(); //plane[2] -> V
                    buffer.get(bytes, ySize + uvSize, buffer.capacity());
                    Log.i(TAG, "buffer2.size:" + buffer.capacity() + " vs:" + planes[2].getRowStride() + ", ps:" + planes[2].getPixelStride());

                    //Log.i(TAG, "*********onImageAvailabel, ySize:" + ySize + ", w " + width + ", h " + height);
                    //test for yuv data correct or incorrect
                    ////writeYuvToFile(bytes);

                    try {
                        mYuvBlockingQueue.put(bytes);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    image.close();
                }
            }, mChildHandler);
        }
    }


    private Size getBestSizeForCameraPreview(Size[] outputSizes, int width, int height) {
        Size bestSize = null;
        List<Size> bestSizeList = new ArrayList<>();

        //计算预览窗口高宽比，高宽比，高宽比
        float ratio = (float) height / width;
        Log.i(TAG, "*******************width=" + width + ", height=" + height + ", ratio=" + ratio);

        for (Size value : outputSizes) {
//            Log.i(TAG, "***********************previewSizes " + value.getWidth() + ", " +
//                    value.getHeight() + ", " + (float) value.getWidth() / value.getHeight());
            if (Math.abs((float) value.getWidth() / value.getHeight() - ratio) < 0.01f) {
                if (value.getWidth() * value.getHeight() > PREVIEW_MIN_SIZE) {
                    Log.i(TAG, "***********************got one!");
                    bestSizeList.add(value);
                }
            }
        }

        if (bestSizeList.size() > 0) {
            bestSize = bestSizeList.get(0); //todo, get max one, not the first
        } else {
            //如果不存在宽高比与预览窗口高宽比一致的输出尺寸，则选择与其宽高比最接近的输出尺寸
            float detRatioMin = Float.MAX_VALUE;
            for (Size size : outputSizes) {
                float curRatio = ((float) size.getWidth()) / size.getHeight();
                if (Math.abs(curRatio - ratio) < detRatioMin) {
                    if (size.getWidth() * size.getHeight() > PREVIEW_MIN_SIZE) {
                        detRatioMin = curRatio;
                        bestSize = size;
                    }
                }
            }

            //如果宽高比最接近的输出尺寸太小，则选择与预览窗口面积最接近的输出尺寸
            if (bestSize == null) {
                long area = (long) width * height;
                long detAreaMin = Long.MAX_VALUE;
                for (Size outputSize : outputSizes) {
                    long curArea = (long) outputSize.getWidth() * outputSize.getHeight();
                    if (Math.abs(curArea - area) < detAreaMin) {
                        detAreaMin = curArea;
                        bestSize = outputSize;
                    }
                }
            }
        }

        Log.i(TAG, "***********bestSize " + bestSize.getWidth() + " X " + bestSize.getHeight()
                + ", ratio=" + (float) bestSize.getWidth() / bestSize.getHeight());

        return bestSize;
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {//打开摄像头
            mCameraDevice = camera;
            if (mCameraDevice == null) {
                return;
            }

            try {
                //if (mCaptureBuilder == null) {
                    mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    /* 自动对焦模式是continuous */
                    mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    /* 闪光灯自动模式 */
                    mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    // todo, here mFpsRanges[0] is [10, 10] for realme, and [15, 15] for G0245D
                    mCaptureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mFpsRanges[0]);
                    mCaptureBuilder.addTarget(mSurface);
                    mCaptureBuilder.addTarget(mImageReader.getSurface());
                //}

                // everytime need to execute here
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface,
                            mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        try {
                            Log.i(TAG, "***********************onConfigured");
                            mCaptureSession = cameraCaptureSession;
                            mCaptureSession.setRepeatingRequest(mCaptureBuilder.build(), null, mChildHandler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.i(TAG, "***********************configure failed!");
                    }
                }, mChildHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {//关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {//发生错误
            Toast.makeText(getContext(), "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };

    private void initMediaFormat() {
        if (mVideoMediaFormat == null) {
            int bitRate = mScreenYuvSize * 8 * FRAME_RATE;
            mVideoMediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mHeight, mWidth); //should exchange width and height here
            mVideoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mVideoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            mVideoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mVideoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_GAP);
        }

        if (mAudioMediaFormat == null) {
            int channel = (AUDIO_CHANNEL == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
            int sampleSize = 0;
            switch (AUDIO_SAMPLE_SIZE) {
                case AudioFormat.ENCODING_PCM_32BIT:
                    sampleSize = 32;
                    break;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                    sampleSize = 24;
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    sampleSize = 16;
                    break;
                case AudioFormat.ENCODING_PCM_8BIT:
                    sampleSize = 8;
                    break;
                default:
                    break;
            }
            mAudioMediaFormat = MediaFormat.createAudioFormat(MIME_TYPE_AAC, AUDIO_SAMPLE_RATE, channel);
            mAudioMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mAudioMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AUDIO_CHANNEL);
            mAudioMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_SAMPLE_RATE * channel * sampleSize);
            mAudioMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channel);
            mAudioMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, AUDIO_SAMPLE_RATE);
        }
    }

    private void initMultiMediaClass() {
        try {
            if (mVideoMediaCodec == null) {
                mVideoMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
                if (mVideoMediaCodec != null) {
                    mVideoMediaCodec.configure(mVideoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    mVideoMediaCodec.start();
                } else {
                    Log.i(TAG, "----------------create mVideoMediaCodec Failed!");
                }
            }

            if (mAudioMediaCodec == null) {
                mAudioMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE_AAC);
                if (mAudioMediaCodec != null) {
                    mAudioMediaCodec.configure(mAudioMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    mAudioMediaCodec.start();
                } else {
                    Log.i(TAG, "----------------create mAudioMediaCodec Failed!");
                }
            }

            if (mMediaMuxer == null) {
                mMediaMuxer = new MediaMuxer(mMp4FilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (mMediaMuxer == null) {
                    Log.i(TAG, "----------------create muxer Failed!");
                }
            }

            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            mAudioBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_SAMPLE_SIZE);
            mAudioBuffer = new byte[mAudioBufferSize];
            Log.i(TAG, "=--------------------------mBsize:" + mAudioBufferSize);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_SAMPLE_SIZE, mAudioBufferSize);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera(){
        if (mCameraDevice != null) {
            try {
                mCameraDevice.close();
                mCameraDevice = null;
                //mImageReader.close();
                //mImageReader = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void releaseMultiMediaClass()
    {
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }

        if (mVideoMediaCodec != null) {
            mVideoMediaCodec.stop();
            mVideoMediaCodec.release();
            mVideoMediaCodec = null;
        }

        if (mAudioMediaCodec != null) {
            mAudioMediaCodec.stop();
            mAudioMediaCodec.release();
            mAudioMediaCodec = null;
        }

        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

        releaseCamera();
        releaseMultiMediaClass();
    }

    private class GrabPcmDataThread extends Thread
    {
        public void run() {
            while (mIsRecording) {
                int size = mAudioRecord.read(mAudioBuffer, 0, mAudioBufferSize);
                Log.i(TAG, "WriteAudioThread*********************** read size: " + size);
                if (size > 0) {
                    try {
                        byte[] data = new byte[size];
                        System.arraycopy(mAudioBuffer, 0, data, 0, size);
                        mPcmBlockingQueue.put(data);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private class AudioCodecHandleThread extends Thread
    {
        public void run() {
            mAudioThreadExit = false;

            ByteBuffer byteBuffer = null;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = System.currentTimeMillis() * 1000;

            while (mIsRecording) {

                int inputBufferId = mAudioMediaCodec.dequeueInputBuffer(3000000);    //3s timeout
                //Log.i(TAG, "----------------audio inputBufferId:" + inputBufferId);
                if (inputBufferId >= 0) {
                    byte[] pcmData = new byte[0];
                    try {
                        pcmData = mPcmBlockingQueue.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    ByteBuffer buff = mAudioMediaCodec.getInputBuffer(inputBufferId);
                    buff.clear();
                    buff.limit(pcmData.length);
                    buff.put(pcmData);

                    //todo, may be this time gap is not very correct
                    long pts = System.currentTimeMillis() * 1000 - presentationTimeUs;
                    mAudioMediaCodec.queueInputBuffer(inputBufferId, 0, pcmData.length, pts, 0);
                }

                int outputBufferId = mAudioMediaCodec.dequeueOutputBuffer(bufferInfo, 3000);
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mAudioMediaCodec.getOutputFormat();
                    mAudioTrackIndex = mMediaMuxer.addTrack(newFormat);
                    Log.e(TAG, "==============mAudioTrackIndex " + mAudioTrackIndex);
                    // NOTICE: after all track added, then we can start to mux!
                    if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                        if (!mIsMuxing) {
                            mMediaMuxer.start();
                            new MuxerHandleThread().start();
                            mIsMuxing = true;
                        }
                    }
                } else { //normal encoded data
                    byteBuffer = mAudioMediaCodec.getOutputBuffer(outputBufferId);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    if (bufferInfo.size > 0) {
                        //Log.i(TAG, "outputBuff position:" + byteBuffer.position() + ", limit:" + byteBuffer.limit());
                        AVData data = new AVData(mAudioTrackIndex, byteBuffer, bufferInfo);
                        mMp4BlockingQueue.add(data);
                    }
                    mAudioMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }

            mAudioThreadExit = true;
        }
    }

    private class VideoCodecHandleThread extends Thread
    {
        public void run() {
            mVideoThreadExit = false;

            ByteBuffer byteBuffer = null;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = System.currentTimeMillis() * 1000;

            while (mIsRecording) {
                int inputBufferId = mVideoMediaCodec.dequeueInputBuffer(3000000);    //3s timeout
                //Log.i(TAG, "----------------video inputBufferId:" + inputBufferId);
                if (inputBufferId >= 0) {
                    byte[] yuvData = null;
                    try {
                        yuvData = mYuvBlockingQueue.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (yuvData == null) {
                        continue;
                    }

                    //here no need to transform, becoz the format of YV12 ImageReader is i420 ([0]y[1]u[2]v)
                    ((MainActivity) getActivity()).I420Rotate90(yuvData, mI420Rotated90Buffer, mWidth, mHeight);

                    //test for rotated90 yuv data correct or incorrect
                    //writeRotatedYuvToFile(mI420Rotated90Buffer);

                    ByteBuffer buff = mVideoMediaCodec.getInputBuffer(inputBufferId);
                    buff.clear();
                    buff.put(mI420Rotated90Buffer);

                    long pts = System.currentTimeMillis() * 1000 - presentationTimeUs;
                    mVideoMediaCodec.queueInputBuffer(inputBufferId, 0, yuvData.length, pts, 0);
                }

                int outputBufferId = mVideoMediaCodec.dequeueOutputBuffer(bufferInfo, 3000);
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mVideoMediaCodec.getOutputFormat();
                    mVideoTrackIndex = mMediaMuxer.addTrack(newFormat);
                    Log.e(TAG, "==============mVideoTrackIndex " + mVideoTrackIndex);

                    if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                        if (!mIsMuxing) {
                            mMediaMuxer.start();
                            new MuxerHandleThread().start();
                            mIsMuxing = true;
                        }
                    }
                } else { //normal encoded data
                    byteBuffer = mVideoMediaCodec.getOutputBuffer(outputBufferId);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    if (bufferInfo.size > 0) {
                        //Log.i(TAG, "outputBuff position:" + byteBuffer.position() + ", limit:" + byteBuffer.limit());
                        AVData data = new AVData(mVideoTrackIndex, byteBuffer, bufferInfo);
                        mMp4BlockingQueue.add(data);
                    }
                    mVideoMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }

            mVideoThreadExit = true;
        }
    }

    private class MuxerHandleThread extends Thread
    {
        public void run() {
            while (mIsRecording) {
                AVData data = mMp4BlockingQueue.poll();
                if (data != null) {
                    if (data.trackIndex >= 0) {
                        mMediaMuxer.writeSampleData(data.trackIndex, data.byteBuffer, data.bufferInfo);
                    }
                }
            }

            while (!mVideoThreadExit || !mAudioThreadExit) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mIsMuxing = false;
            mAudioTrackIndex = -1;
            mVideoTrackIndex = -1;
            mMp4BlockingQueue.clear();
            mPcmBlockingQueue.clear();
            mYuvBlockingQueue.clear();
            try {
                if (mYuvFileOutputStream != null) {
                    mYuvFileOutputStream.close();
                }
                if (mRotatedYuvFileOutputStream != null) {
                    mRotatedYuvFileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            releaseCamera();
            releaseMultiMediaClass();

            //after thread finish, to enable start button
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPlayButton.setEnabled(true);
                    mStartButton.setEnabled(true);
                    mStopButton.setEnabled(false);
                }
            });
        }
    }

    private FileOutputStream mYuvFileOutputStream = null;
    private void writeYuvToFile(byte[] data) {
        try {
            if (mYuvFileOutputStream == null) {
                mYuvFileOutputStream = new FileOutputStream(mYuvFilePath);
            }

            mYuvFileOutputStream.write(data);
            mYuvFileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileOutputStream mRotatedYuvFileOutputStream = null;
    private void writeRotatedYuvToFile(byte[] data) {
        try {
            if (mRotatedYuvFileOutputStream == null) {
                mRotatedYuvFileOutputStream = new FileOutputStream(mRotatedYuvFilePath);
            }

            mRotatedYuvFileOutputStream.write(data);
            mRotatedYuvFileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static class AVData {
        private static int index = -1;
        public int trackIndex;
        public ByteBuffer byteBuffer;
        public MediaCodec.BufferInfo bufferInfo;

        public AVData(int trackIndex, ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
            index++;
            this.trackIndex = trackIndex;
            this.byteBuffer = byteBuffer;
            this.bufferInfo = bufferInfo;

            boolean tag = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            Log.d(TAG, "index: " + index + ", trackIndex: " + trackIndex + ", keyFrame? " + tag);
        }
    }
}
