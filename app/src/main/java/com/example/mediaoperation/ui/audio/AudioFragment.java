package com.example.mediaoperation.ui.audio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediaoperation.AddWavHeader;
import com.example.mediaoperation.R;
import com.example.mediaoperation.databinding.FragmentAudioBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioFragment extends Fragment {
    private final static String TAG = "MediaOperation.AudioFragment";

    private AudioViewModel audioViewModel;
    private FragmentAudioBinding binding;

    private Button mRecordButton = null;
    private Button mStopButton = null;
    private Button mPlayButton = null;
    private Button mPlayWavButton = null;

    private final static int AUDIO_SAMPLERATE = 16000;
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private final static int AUDIO_SAMPLESIZE = AudioFormat.ENCODING_PCM_16BIT;
    private final static int mode = AudioTrack.MODE_STREAM;
    private final static int mStreamType = AudioManager.STREAM_MUSIC;

    private String mAudioPath = null;
    private String mWavPath = null;

    private AudioTrack mAudioTrack = null;
    private AudioRecord mAudioRecord = null;
    private int mBufferSize = 0;
    private byte[] mBuffer = null;
    private boolean mIsRecording = false;

    private Thread mWriteThread = null;
    private Thread mPlayThread = null;
    private FileOutputStream mFileOutputStream = null;
    private FileInputStream mFileInputStream = null;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        audioViewModel =
                new ViewModelProvider(this).get(AudioViewModel.class);

        binding = FragmentAudioBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textDashboard;
        audioViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        mRecordButton = binding.recordButton;
        mStopButton = binding.stopButton;
        mPlayButton = binding.playButton;

        mAudioPath = getContext().getExternalCacheDir() + "/record2.pcm";
        mWavPath = getContext().getExternalCacheDir() + "/record2.wav";
        Log.i(TAG, "============= mAudioPath:" + mAudioPath + " mWavPath:" + mWavPath);

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAudioRecord == null) {
                    createAudio();
                }

                if (mAudioRecord != null) {
                    mAudioRecord.startRecording();
                    mIsRecording = true;

                    mWriteThread = new WriteAudioThread();
                    mWriteThread.start();
                }
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "============= mStopButton onclick:");
                mIsRecording = false;

            }
        });

        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAudioTrack == null) {
                    createAudio();
                }

                if (mAudioTrack != null) {
                    mAudioTrack.play();
                }

                mPlayThread = new PlayAudioThread();
                mPlayThread.start();
            }
        });

        mPlayWavButton = binding.wavButton;
        mPlayWavButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Notice: bug here:
                //boolean b = AddWavHeader.creatWaveFileFromPcm(mAudioPath, mWavPath, AUDIO_SAMPLERATE, AUDIO_CHANNEL, AUDIO_SAMPLESIZE);
                boolean b = AddWavHeader.creatWaveFileFromPcm(mAudioPath, mWavPath, AUDIO_SAMPLERATE, 2, 16);

                Log.i(TAG, "============= start play wav file:" + mWavPath);
                MediaPlayer mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(mWavPath);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

        releaseAudio();
    }

    private void releaseAudio() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    private void createAudio() {
        mBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLERATE, AUDIO_CHANNEL, AUDIO_SAMPLESIZE);
        mBuffer = new byte[mBufferSize];
        Log.i(TAG, "=--------------------------mBsize:" + mBufferSize);

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_SAMPLERATE, AUDIO_CHANNEL, AUDIO_SAMPLESIZE, mBufferSize);
        mAudioTrack = new AudioTrack(mStreamType, AUDIO_SAMPLERATE, AUDIO_CHANNEL, AUDIO_SAMPLESIZE, mBufferSize, mode);
    }

    private class WriteAudioThread extends Thread
    {
        public void run() {
            try {
                Log.i(TAG, "create file: " + mAudioPath);
                if (!new File(mAudioPath).exists()) {
                    boolean b = new File(mAudioPath).createNewFile();
                    if (b) {
                        Log.i(TAG, "create file: " + mAudioPath + " OK!");
                    } else {
                        Log.i(TAG, "create file: " + mAudioPath + " Failed!");
                    }
                }
                mFileOutputStream = new FileOutputStream(mAudioPath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            while (mIsRecording) {
                int size = mAudioRecord.read(mBuffer, 0, mBufferSize);
                Log.i(TAG, "WriteAudioThread*********************** read size: " + size);

                if (size > 0) {
                    try {
                        mFileOutputStream.write(mBuffer, 0, size);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.i(TAG, "AudioRecord read error occurred!");
                }
            }

            try {
                mFileOutputStream.flush();
                mFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mAudioRecord != null) {
                        Log.i(TAG, "============= stop record");
                        mAudioRecord.stop();
                    }
                }
            });
        }
    };

    private static Handler mHandler = new Handler();

    private class PlayAudioThread extends Thread
    {
        public void run() {
            try {
                if (!new File(mAudioPath).exists()) {
                    new File(mAudioPath).createNewFile();
                }
                mFileInputStream = new FileInputStream(mAudioPath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                int size = mFileInputStream.read(mBuffer, 0, mBufferSize);
                Log.i(TAG, "PlayAudioThread*********************** read size: " + size);

                while (size > 0) {
                    mAudioTrack.write(mBuffer, 0, size);
                    size = mFileInputStream.read(mBuffer, 0, mBufferSize);
                    Log.i(TAG, "PlayAudioThread*********************** read size: " + size);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mAudioTrack.stop();

            try {
                mFileInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}