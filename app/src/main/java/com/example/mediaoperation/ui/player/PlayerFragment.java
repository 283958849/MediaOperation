package com.example.mediaoperation.ui.player;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediaoperation.databinding.FragmentPlayerBinding;

public class PlayerFragment extends Fragment {

    private PlayerViewModel playerViewModel = null;
    private FragmentPlayerBinding binding = null;

    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mHolder = null;
    private SeekBar mSeekBar = null;
    private Button mPlayButton = null;
    private Button mPauseButton = null;
    private Button mStopButton = null;
    private Button mChosseButton = null;
    private EditText mChooseFileEditText = null;

    private MediaPlayer mMediaPlayer = null;
    private Uri mPlayUri = null;
    private boolean mIsPaused = false;
    //private String mPath = "";

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        playerViewModel =
                new ViewModelProvider(this).get(PlayerViewModel.class);

        binding = FragmentPlayerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            //mPath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/test.mp4";
        } else {
            //mPath = getContext().getCacheDir().getPath() + "/test.mp4";
        }

        mSurfaceView = binding.surfaceView;
        mSeekBar = binding.seekBar;
        mPlayButton = binding.playButton;
        mPauseButton = binding.pauseButton;
        mStopButton = binding.stopButton;
        mChosseButton = binding.chooseButton;
        mChooseFileEditText = binding.editTextTextPersonName;
        mHolder = mSurfaceView.getHolder();

        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPlayButton.setEnabled(false);
                mPauseButton.setEnabled(true);
                if (mIsPaused) {
                    mIsPaused = false;
                    mMediaPlayer.start();
                } else {
                    try {
                        //mMediaPlayer = new MediaPlayer();
                        //mMediaPlayer.setDataSource(mPath);
                        //mMediaPlayer.setDisplay(mHolder);

                        //Uri playUri = Uri.parse("file://" + mPath);
                        mMediaPlayer = MediaPlayer.create(getContext(), mPlayUri, mHolder);
                        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                mMediaPlayer.stop();
                                mPlayButton.setEnabled(true);
                            }
                        });
                        //mMediaPlayer.prepare();
                        mIsPaused = false;
                        mMediaPlayer.start();
                        mPauseButton.setEnabled(true);
                        mStopButton.setEnabled(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsPaused) {
                    mIsPaused = true;
                    mMediaPlayer.pause();
                    mPlayButton.setEnabled(true);
                    mPauseButton.setEnabled(false);
                }
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsPaused = false;
                if (mMediaPlayer != null) {
                    mMediaPlayer.stop();
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
                mPlayButton.setEnabled(true);
                mPauseButton.setEnabled(false);
            }
        });

        mChosseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                //intent.setType(“audio/*”);       //选择音频
                intent.setType("video/*;image/*"); //同时选择视频和图片
                //intent.setType("*/*");           //无类型限制
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
            }
        });
        Log.i("PlayerFragment", "===========onCre");

        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.i("PlayerFragment", "================file uri:" + uri);
            String path = uri.getPath();
            mChooseFileEditText.setText(path);
            mPlayUri = uri;
        }
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        releaseMediaPlayer();
        Log.i("PlayerFragment", "===========onDes");
    }
}