package com.example.mediaoperation.ui.multimedia;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


public class MultiMediaFragment extends Fragment implements Camera.PreviewCallback {
    private final static String TAG = "MediaOperation.MultiMediaFragment";

    private FragmentMultimediaBinding binding;

    private SurfaceHolder mHolder = null;
    private Button mStartButton = null;
    private Button mStopButton = null;
    private Button mPlayVideoButton = null;

    private Camera mCamera = null;
    private final static int mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;

    private MediaFormat mVideoMediaFormat = null;
    private MediaFormat mAudioMediaFormat = null;
    private MediaMuxer mMediaMuxer = null;
    private MediaCodec mVideoMediaCodec = null;
    private MediaCodec mAudioMediaCodec = null;
    private AudioRecord mAudioRecord = null;

    private final static String MIME_TYPE = "video/avc";
    private final static String MIME_TYPE_AAC = "audio/mp4a-latm";  //aac format
    private final static int FRAME_RATE = 5;
    private final static int I_FRAME_GAP = 5;

    private final static int AUDIO_SAMPLE_RATE = 16000;
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_STEREO;
    private final static int AUDIO_SAMPLE_SIZE = AudioFormat.ENCODING_PCM_16BIT;

    private byte[] mAudioBuffer = null;
    private byte[] mI420Rotated90Buffer = null;
    private byte[] mNV21Buffer = null;
    private int mAudioBufferSize = 0;
    private int mScreenYuvSize = -1;
    private int mWidth = -1;
    private int mHeight = -1;

    private String mMp4FilePath = null;
    private boolean mIsStopEncode = false;
    private boolean mIsStopPreview = false;
    private BlockingQueue<byte[]> mPcmBlockingQueue = null;
    private BlockingQueue<byte[]> mYuvBlockingQueue = null;
    private BlockingQueue<AVData> mMp4BlockingQueue = null;

    private final static Handler mHandler = new Handler();


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        com.example.mediaoperation.ui.multimedia.MultiMeidaViewModel multiMediaViewModel =
                new ViewModelProvider(this).get(com.example.mediaoperation.ui.multimedia.MultiMeidaViewModel.class);
        binding = FragmentMultimediaBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        multiMediaViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        mStartButton = binding.startButton;
        mStopButton = binding.stopButton;
        mPlayVideoButton = binding.playVideoButton;
        mHolder = binding.surfaceView.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        mPlayVideoButton.setEnabled(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            mMp4FilePath = Environment.getExternalStorageDirectory().getPath() + "/terrylee.mp4";
        } else {
            mMp4FilePath = getContext().getCacheDir().getPath() + "/terrylee.mp4";
        }

        // for test
        mMp4FilePath = getContext().getCacheDir().getPath() + "/terrylee.mp4";
        Log.i("HomeFragment", "======================mMp4FilePath: " + mMp4FilePath);

        initCamera(mHolder);  //for mWidth and mHeight, must before decoder initialization
        initMediaFormat();

        mPcmBlockingQueue = new LinkedBlockingDeque<>(10);
        mYuvBlockingQueue = new LinkedBlockingDeque<>();
        mMp4BlockingQueue = new LinkedBlockingDeque<>();

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initCamera(mHolder);
                initMultiMediaClass();

                // start to get video and audio data
                try {
                    mCamera.setPreviewCallback(MultiMediaFragment.this);
                    mCamera.setPreviewDisplay(mHolder);
                    mCamera.startPreview();

                    mAudioRecord.startRecording();
                    new GrabPcmDataThread().start();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // start to handle video and audio data
                new VideoCodecHandleThread().start();
                new AudioCodecHandleThread().start();

                mStopButton.setEnabled(true);
                mPlayVideoButton.setEnabled(false);
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStartButton.isEnabled()) {
                    mIsStopEncode = true;
                    mIsStopPreview = true;
                }
                if (mPlayVideoButton.isEnabled()) {
                    // todo, play mp4 to test
                }
                Toast.makeText(getContext(), "wait the encoder/decoder to finish, please!", Toast.LENGTH_SHORT).show();
            }
        });

        mPlayVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartButton.setEnabled(false);
                mStopButton.setEnabled(true);
            }
        });

        return root;
    }

    private void initCamera(SurfaceHolder holder)
    {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            try {
                if (mCamera == null) {
                    mCamera = Camera.open(mCameraID);
                    if (mCamera != null) {
                        Camera.Parameters para = mCamera.getParameters();
                        mWidth = para.getPreviewSize().width;
                        mHeight = para.getPreviewSize().height;
                        Log.i(TAG, "-------------mWidth=" + mWidth + ", mHeight=" + mHeight);

                        mScreenYuvSize = mWidth * mHeight * 3 / 2;
                        mNV21Buffer = new byte[mScreenYuvSize];
                        mI420Rotated90Buffer = new byte[mScreenYuvSize];

                        //NOTICE: here need to transform data from NV21/YV12 to I420 for H264 encoder!
                        para.setPreviewFormat(ImageFormat.NV21);
                        mCamera.setDisplayOrientation(90); //旋转90度for preview, not for encode
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

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

            Log.i(TAG, "----------------init muxer!");
            if (mMediaMuxer == null) {
                Log.i(TAG, "----------------init muxer 222!");
                mMediaMuxer = new MediaMuxer(mMp4FilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (mMediaMuxer == null) {
                    Log.i(TAG, "----------------init muxer 333!");
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
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
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

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

            try {
                mYuvBlockingQueue.put(bytes);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    private void nv21ToI420(byte[] data, byte[] newData) {
        //1, copy all Y data
        System.arraycopy(data, 0, newData, 0, mWidth * mHeight);

        //2, copy all U dta
        int yIndex = mWidth * mHeight;
        for (int i = mWidth * mHeight; i < data.length; i += 2) {
            newData[yIndex++] = data[i + 1];
        }

        //3, copy all V dta
        for (int i = mWidth * mHeight; i < data.length; i += 2) {
            newData[yIndex++] = data[i];
        }
    }

    private class GrabPcmDataThread extends Thread
    {
        public void run() {
            while (!mIsStopEncode) {
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

    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;

    private class AudioCodecHandleThread extends Thread
    {
        public void run() {
            ByteBuffer byteBuffer = null;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = System.currentTimeMillis() * 1000;

            while (!mIsStopEncode) {

                int inputBufferId = mAudioMediaCodec.dequeueInputBuffer(3000000);    //3s timeout
                Log.i(TAG, "----------------audio inputBufferId:" + inputBufferId);
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

                    // NOTICE: after all track added, then we can start to mux!
                    if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                        mMediaMuxer.start();
                        new MuxerHandleThread().start();
                    }
                } else { //normal encoded data
                    byteBuffer = mAudioMediaCodec.getOutputBuffer(outputBufferId);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    if (bufferInfo.size > 0) {
                        Log.i(TAG, "outputBuff position:" + byteBuffer.position() + ", limit:" + byteBuffer.limit());
                        AVData data = new AVData(mAudioTrackIndex, byteBuffer, bufferInfo);
                        mMp4BlockingQueue.add(data);
                    }
                    mAudioMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }
    }

    private class VideoCodecHandleThread extends Thread
    {
        public void run() {
            ByteBuffer byteBuffer = null;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = System.currentTimeMillis() * 1000;

            while (!mIsStopEncode) {
                int inputBufferId = mVideoMediaCodec.dequeueInputBuffer(3000000);    //3s timeout
                Log.i(TAG, "----------------video inputBufferId:" + inputBufferId);
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

                    // transform and rotate yuv data before send it to H.264 encoder
                    nv21ToI420(yuvData, mNV21Buffer);
                    ((MainActivity) getActivity()).I420Rotate90(mNV21Buffer, mI420Rotated90Buffer, mWidth, mHeight);

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
                    if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                        mMediaMuxer.start();
                        new MuxerHandleThread().start();
                    }
                } else { //normal encoded data
                    byteBuffer = mVideoMediaCodec.getOutputBuffer(outputBufferId);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }
                    if (bufferInfo.size > 0) {
                        Log.i(TAG, "outputBuff position:" + byteBuffer.position() + ", limit:" + byteBuffer.limit());
                        AVData data = new AVData(mVideoTrackIndex, byteBuffer, bufferInfo);
                        mMp4BlockingQueue.add(data);
                    }
                    mVideoMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }
    }

    private class MuxerHandleThread extends Thread
    {
        public void run() {
            while (!mIsStopEncode) {
                AVData data = mMp4BlockingQueue.poll();
                if (data != null) {
                    mMediaMuxer.writeSampleData(data.trackIndex, data.byteBuffer, data.bufferInfo);
                }
            }

            releaseCamera();
            releaseMultiMediaClass();

            //after thread finish, to enable start button
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPlayVideoButton.setEnabled(true);
                    mStartButton.setEnabled(true);
                    mStopButton.setEnabled(false);
                }
            });

            mIsStopEncode = false;
            mIsStopPreview = false;
        }
    }

    private void addADTStoPacket(int sampleRateType, byte[] packet, int packetLen) {
        int profile = 2;
        int chanCfg = 2;
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (sampleRateType << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
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
