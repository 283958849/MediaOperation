package com.example.mediaoperation.ui.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.mediaoperation.MainActivity;
import com.example.mediaoperation.R;
import com.example.mediaoperation.RealPathFromUriUtils;
import com.example.mediaoperation.databinding.FragmentPlayerBinding;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerFragment extends Fragment {

    private PlayerViewModel playerViewModel = null;
    private FragmentPlayerBinding binding = null;

    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mHolder = null;
    private Button mPlayButton = null;
    private Button mPauseButton = null;
    private Button mStopButton = null;
    private Button mChosseButton = null;
    private Button mFilterButton = null;
    private Button mDefaultButton = null;
    private Button mRtmpButton = null;
    private EditText mChooseFileEditText = null;
    private MyHandler mHandler = null;
    private Timer mTimer = null;
    private AlertDialog mAlert = null;

    private MediaPlayer mMediaPlayer = null;
    private Uri mPlayUri = null;
    private Uri mChooseUri = null;
    private boolean mIsPaused = false;
    private String mChooseVideoFile = "";
    private String mFilteredVideoFile = "";
    private String mWaterMarkFile = "";

    private Spinner mServerSpiner = null;
    private ArrayAdapter<CharSequence> mServerAdapter = null;

    private String mRtmpServer = "";

    private static String DEFAULT_RTMP_URL = "rtmp://192.168.8.155:1935/live/test";

    private final static String TAG = "MediaOperation.PlayerFragment";


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        playerViewModel =
                new ViewModelProvider(this).get(PlayerViewModel.class);

        binding = FragmentPlayerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        mHandler = new MyHandler();
        mTimer = new Timer();

        mSurfaceView = binding.surfaceView;
        mPlayButton = binding.playButton;
        mPauseButton = binding.pauseButton;
        mStopButton = binding.stopButton;
        mFilterButton = binding.filterButton;
        mChosseButton = binding.chooseButton;
        mDefaultButton = binding.defaultButton;
        mRtmpButton = binding.rtmpButton;
        mChooseFileEditText = binding.editTextTextPersonName;
        mChooseFileEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)12);
        mHolder = mSurfaceView.getHolder();


        mServerSpiner = binding.spinner;
        mServerSpiner.setPrompt("hello world");
        mServerAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.rtmp_server, android.R.layout.simple_spinner_item);
        mServerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mServerSpiner.setAdapter(mServerAdapter);

        mServerSpiner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mRtmpServer = (String)mServerSpiner.getItemAtPosition(i);
                //Log.i(TAG, "=============selected rtmpserver:" + mRtmpServer);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mRtmpServer = DEFAULT_RTMP_URL;
            }
        });

        usetDefaultPathForReadWriteOperation();

        mDefaultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usetDefaultPathForReadWriteOperation();
            }
        });

        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                } else if (mIsPaused) {
                    mIsPaused = false;
                    mMediaPlayer.start();
                } else {
                    try {
                        //mMediaPlayer = new MediaPlayer();
                        //mMediaPlayer.setDataSource(mPath);
                        //mMediaPlayer.setDisplay(mHolder);

                        mMediaPlayer = MediaPlayer.create(getContext(), mPlayUri, mHolder);
                        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                mMediaPlayer.stop();
                                mMediaPlayer.release();
                                mMediaPlayer = null;
                            }
                        });
                        //mMediaPlayer.prepare();
                        mIsPaused = false;
                        mMediaPlayer.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayer != null && mMediaPlayer.isPlaying() && !mIsPaused) {
                    mIsPaused = true;
                    mMediaPlayer.pause();
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
            }
        });

        mChosseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                //intent.setType(“audio/*”);       //选择音频
                intent.setType("video/*"); //同时选择视频和图片
                //intent.setType("*/*");           //无类型限制
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
            }
        });



        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

//        String copyToPath = path.getPath() + "/Camera/";
//        String cmd = "cp " + mFilteredVideoFile + " " + copyToPath;
//        Log.i(TAG, "===========cmd:" + cmd);
//
//        try {
//            Runtime.getRuntime().exec(cmd);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        mFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popupDialog();

                new Thread() {
                    public void run() {
                        Log.i(TAG, "===========mChooseVideoFile:" + mChooseVideoFile);
                        Log.i(TAG, "===========mWaterMarkFile:" + mWaterMarkFile);
                        Log.i(TAG, "===========mFilteredVideoFile:" + mFilteredVideoFile);
                        ((MainActivity) getActivity()).addWaterMarkWithFfmpeg(mChooseVideoFile, mWaterMarkFile, mFilteredVideoFile);

                        mPlayUri = Uri.parse(mFilteredVideoFile);
                        mChooseFileEditText.setText(mFilteredVideoFile);

                        mAlert.setMessage("finished!");
                        mTimer.schedule(new CloseDialogTask(), 2000);
                    }
                }.start();
            }
        });

        mRtmpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread() {
                    public void run() {
                        ((MainActivity) getActivity()).pushRtmpWithFfmpeg(mChooseVideoFile, mRtmpServer);
                    }
                }.start();
            }
        });

        return root;
    }


    private class CloseDialogTask extends TimerTask
    {
        public void run(){
            mHandler.sendEmptyMessage(0);
        }
    }

    private class MyHandler extends Handler {
        public void handleMessage(Message msg){
            mAlert.dismiss();
        }
    }

    private void popupDialog() {
        AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());
        //builder.setTitle("确认" ) ;
        builder.setMessage("add filtering, please wait..." ) ;
        //builder.setPositiveButton("是" ,  null );
        mAlert = builder.create();
        mAlert.setCanceledOnTouchOutside(false);
        mAlert.show();
    }

    private void usetDefaultPathForReadWriteOperation() {
        String path = getContext().getExternalCacheDir().getPath();

        /* NOTICE:
        here getContext().getCacheDir().getPath() is
        /data/usr/0/com.example.mediaoperation/cache, we have no permission!! realme sometimes can!

        but  getContext().getExternalCacheDir().getPath() is
        /storage/emulated/0/Android/data/com.example.mediaoperation/cache, we have permission!!
        */

        mChooseVideoFile = path + "/flyman.flv";
        mWaterMarkFile = path + "/watermark.png";
        mChooseFileEditText.setText(mChooseVideoFile);

        mFilteredVideoFile = makeNewFilterFileName(mChooseVideoFile);

        mPlayUri = Uri.parse(mChooseVideoFile);

        mRtmpServer = DEFAULT_RTMP_URL;
    }

    private String makeNewFilterFileName(String chooseFileName) {
        String filterFileName = "";
        String suffix = "_wm.";
        String[] str = chooseFileName.split("\\.");
        if (str.length <= 0) {
            suffix += "mp4";
        } else {
            suffix += str[str.length - 1];
        }
        filterFileName = chooseFileName + suffix;
        return filterFileName;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            mChooseUri = data.getData();
            try {
                mChooseVideoFile = RealPathFromUriUtils.getPath(getActivity(), mChooseUri);
            } catch (Exception e) {
                e.printStackTrace();
            }

            mChooseFileEditText.setText(mChooseVideoFile);
            mFilteredVideoFile = makeNewFilterFileName(mChooseVideoFile);
            mPlayUri = mChooseUri;
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
    }
}