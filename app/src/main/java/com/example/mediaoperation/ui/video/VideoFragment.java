package com.example.mediaoperation.ui.video;

import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediaoperation.MainActivity;
import com.example.mediaoperation.databinding.FragmentVideoBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoFragment extends Fragment implements Camera.PreviewCallback {

    private com.example.mediaoperation.ui.video.VideoViewModel videoViewModel;
    private FragmentVideoBinding binding;

    private SurfaceView mSurfaceView;
    private Button mStartButton;
    private Button mStopButton;
    private Button mPlayVideoButton;
    private SurfaceHolder mHolder;

    private final String MIME_TYPE = "video/avc";
    private final int FRAME_RATE = 5;
    private final int I_FRAME_GAP = 5;
    private int mBitRate = 0;

    private Camera mCamera = null;
    private final static int mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private byte[] mI420ByteArray;
    private int mWidth = -1;
    private int mHeight = -1;
    private boolean mIsStopEncode = false;
    private boolean mIsStopDecode = false;
    private FileOutputStream mFileOutputStream = null;
    private FileInputStream mFileInputStream = null;
    private String mMp4FilePath = null;
    private boolean mIsOnPreviewHandling = false;

    private MediaCodec mMediaCodec = null;
    private MediaCodec mMediaDeCodec = null;
    private MediaCodec.BufferInfo mBufferInfo = null;
    private MediaFormat mMediaFormat = null;
    private MediaExtractor mMediaExtractor = null;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    MediaFormat mVideoFormat = null;
    MediaFormat mAudioFormat = null;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        videoViewModel = new ViewModelProvider(this).get(com.example.mediaoperation.ui.video.VideoViewModel.class);
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        videoViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        mSurfaceView = binding.surfaceView;
        mStartButton = binding.startButton;
        mStopButton = binding.stopButton;
        mPlayVideoButton = binding.playVideoButton;

        mHolder = mSurfaceView.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mBufferInfo = new MediaCodec.BufferInfo();

        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        mPlayVideoButton.setEnabled(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            mMp4FilePath = Environment.getExternalStorageDirectory().getPath() + "/terrylee15.h264";
        } else {
            mMp4FilePath = getContext().getCacheDir().getPath() + "/terrylee.h264";
        }

        // for test
        mMp4FilePath = getContext().getCacheDir().getPath() + "/terrylee.h264";
        Log.i("HomeFragment", "======================mMp4FilePath: " + mMp4FilePath);

        initCamera(mHolder);  //for mWidth and mHeight, for decoder initialization
        initMediaFormat();

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initCamera(mHolder);
                initEncoder();

                try {
                    mCamera.setPreviewCallback(VideoFragment.this);
                    mCamera.setPreviewDisplay(mHolder);
                    mCamera.startPreview(); //开始预览
                } catch (Exception e) {
                    e.printStackTrace();
                }

                new MediaCodecHandleThread().start();

                mStopButton.setEnabled(true);
                mPlayVideoButton.setEnabled(false);
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStartButton.isEnabled()) {
                    mIsStopEncode = true;
                }
                if (mPlayVideoButton.isEnabled()) {
                    mIsStopDecode = true;
                }
                Toast.makeText(getContext(), "wait the encoder/decoder to finish, please!", Toast.LENGTH_SHORT).show();
            }
        });

        mPlayVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartButton.setEnabled(false);
                mStopButton.setEnabled(true);

                initDecoder();
                new MediaDeCodecHandleThread().start();
            }
        });

        return root;
    }

    private static Handler mHandler = new Handler();

    private void releaseMediaCodec()
    {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }

        if (mMediaDeCodec != null) {
            mMediaDeCodec.stop();
            mMediaDeCodec.release();
            mMediaDeCodec = null;
        }

        if (mMediaExtractor != null) {
            mMediaExtractor.release();
            mMediaExtractor = null;
        }
    }

    private void initEncoder() {
        try {
            if (mMediaCodec == null) {
                mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
                if (mMediaCodec != null) {
                    mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    mMediaCodec.start();
                } else {
                    Log.i("HomeFragment", "----------------creat MediaCodec Failed!");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initDecoder() {
        try {
            if (mMediaDeCodec == null) {
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mHeight, mWidth);
//            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_GAP);
                mMediaDeCodec = MediaCodec.createDecoderByType(MIME_TYPE);
                if (mMediaDeCodec != null) {
                    mMediaDeCodec.configure(mMediaFormat, mHolder.getSurface(), null, 0);
                    mMediaDeCodec.start();
                } else {
                    Log.i("HomeFragment", "----------------create MediaDeCodec Failed!");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initExtractor() {
        try {
            File file = new File(mMp4FilePath);
            if (file.exists()) {
                Log.i("HomeFragment", "----------------mMp4FilePath size:" + file.length());
            } else {
                Log.i("HomeFragment", "----------------mMp4FilePath not exist!");
            }

            mMediaExtractor = new MediaExtractor();
            Log.i("HomeFragment", "----------------create mMediaExtractor!");
            mMediaExtractor.setDataSource(mMp4FilePath);
            int trackCount = mMediaExtractor.getTrackCount();
            Log.i("HomeFragment", "----------------mMediaExtractor trackCount:" + trackCount);

            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mMediaExtractor.getTrackFormat(i);
                String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mineType.startsWith("video/")) {
                    mVideoFormat = trackFormat;
                    mVideoTrackIndex = i;
                }
                if (mineType.startsWith("audio/")) {
                    mAudioFormat = trackFormat;
                    mAudioTrackIndex = i;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MediaDeCodecHandleThread extends Thread
    {
//        public MediaDeCodecHandleThread() {
//            try {
//                File file = new File(mMp4FilePath);
//                if (file.exists()) {
//                    mFileInputStream = new FileInputStream(mMp4FilePath);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        public void run() {
            initExtractor();
            mMediaExtractor.selectTrack(mVideoTrackIndex);

            //int maxBufferSize = mVideoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            int maxBufferSize = mWidth * mHeight * 3 / 2; //todo, why crash with above code?
            ByteBuffer byteBuffer = ByteBuffer.allocate(maxBufferSize);

            while (!mIsStopDecode) {
                int inputBufferId = mMediaDeCodec.dequeueInputBuffer(3000000);    //3s timeout
                Log.i("HomeFragment", "----------------decode inputBufferId:" + inputBufferId);
                if (inputBufferId >= 0) {
                    int readSampleCount = mMediaExtractor.readSampleData(byteBuffer, 0);
                    Log.d("HomeFragment", "video:readSampleCount:" + readSampleCount);
                    if (readSampleCount < 0) {
                        break;
                    }

                    byte[] buffer = new byte[readSampleCount];
                    byteBuffer.get(buffer);
                    mMediaExtractor.advance();

                    ByteBuffer buff = mMediaDeCodec.getInputBuffer(inputBufferId);
                    buff.clear();
                    buff.put(buffer, 0, buffer.length);
                    mMediaDeCodec.queueInputBuffer(inputBufferId, 0, buffer.length, System.nanoTime() / 1000, 0);
                } else {
                    continue;
                }

                int outputBufferId = mMediaDeCodec.dequeueOutputBuffer(mBufferInfo, 3000);
                Log.i("HomeFragment", "----------------decode outputBufferId:" + outputBufferId);
                if (outputBufferId >= 0) {
                    mMediaDeCodec.releaseOutputBuffer(outputBufferId, true);
                }
            }

            //after thread finish, to enable start button
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mStartButton.setEnabled(true);
                    mStopButton.setEnabled(false);
                    releaseMediaCodec();
                    mIsStopDecode = false;
                }
            });
        }
    }

    private class MediaCodecHandleThread extends Thread
    {
        public MediaCodecHandleThread() {
            try {
                File file = new File(mMp4FilePath);
                if (!file.exists()) {
                    boolean b = file.createNewFile();
                }
                mFileOutputStream = new FileOutputStream(mMp4FilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            ByteBuffer outputBuff = null;
            while (!mIsStopEncode) {
                int outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 3000);
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //int outFmt = mMediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    //Log.i("HomeFragment", "----------------outFmt:" + outFmt);

                    ByteBuffer spsb = mMediaCodec.getOutputFormat().getByteBuffer("csd-0");
                    byte[] sps = new byte[spsb.remaining()];
                    spsb.get(sps, 0, sps.length);
                    try {
                        mFileOutputStream.write(sps);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.i("HomeFragment", "----------------sps length:" + sps.length);

                    ByteBuffer ppsb = mMediaCodec.getOutputFormat().getByteBuffer("csd-1");
                    byte[] pps = new byte[ppsb.remaining()];
                    ppsb.get(pps, 0, pps.length);
                    try {
                        mFileOutputStream.write(pps);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.i("HomeFragment", "----------------pps length:" + pps.length);

                    //muxer to do work
                } else { //normal encoded data
                    outputBuff = mMediaCodec.getOutputBuffer(outputBufferId);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mBufferInfo.size = 0;
                    }
                    if (mBufferInfo.size > 0) {
                        outputBuff.position(mBufferInfo.offset);
                        outputBuff.limit(mBufferInfo.offset + mBufferInfo.size);
                        Log.i("HomeFragment", "----------------outputBuff position:" + outputBuff.position());
                        Log.i("HomeFragment", "----------------outputBuff limit:" + outputBuff.limit());

                        byte[] data = new byte[mBufferInfo.size];
                        outputBuff.get(data, 0, mBufferInfo.size);
                        try {
                            //NOTICE: here can not use buff.array(), it will crash, need to copy to data, and use it
                            //mFileOutputStream.write(outputBuff.array(), 0, outputBuff.limit());
                            mFileOutputStream.write(data, 0, outputBuff.limit());
                            mFileOutputStream.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }

            //after thread finish, to enable start button
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    releaseCamera();
                    releaseMediaCodec();

                    mPlayVideoButton.setEnabled(true);
                    mStartButton.setEnabled(true);
                    mStopButton.setEnabled(false);
                    mIsStopEncode = false;
                }
            });

        }
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
                        Log.i("HomeFragment", "-------------mWidth=" + mWidth + ", mHeight=" + mHeight);

                        mI420ByteArray = new byte[mWidth * mHeight * 3 / 2];
                        mBitRate = (mWidth * mHeight * 3 / 2) * 8 * FRAME_RATE;

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
        if (mMediaFormat == null) {
            mMediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mHeight, mWidth);
            mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_GAP);
        }
    }

    public void releaseCamera(){
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

        releaseCamera();

        releaseMediaCodec();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (!mIsOnPreviewHandling) {
            mIsOnPreviewHandling = true;
            byte[] temp = new byte[mWidth * mHeight * 3 / 2];
            //((MainActivity)getActivity()).NV21ToI420(bytes, mI420ByteArray, mWidth, mHeight);
            nv21ToI420(bytes, temp);
            ((MainActivity) getActivity()).I420Rotate90(temp, mI420ByteArray, mWidth, mHeight);

            int inputBufferId = mMediaCodec.dequeueInputBuffer(3000000);    //3s timeout
            Log.i("HomeFragment", "----------------inputBufferId:" + inputBufferId);
            if (inputBufferId >= 0) {
                ByteBuffer buff = mMediaCodec.getInputBuffer(inputBufferId);
                buff.clear();
                buff.put(mI420ByteArray, 0, mI420ByteArray.length);
                mMediaCodec.queueInputBuffer(inputBufferId, 0, mI420ByteArray.length, System.nanoTime() / 1000, 0);
            }
            mIsOnPreviewHandling = false;
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
}
