package com.example.mediaoperation;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.mediaoperation.databinding.ActivityMainBinding;


public class MainActivity extends AppCompatActivity {

    static {
        //System.loadLibrary("yuv");
        System.loadLibrary("x264");
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("postproc");
        System.loadLibrary("avfilter");
        System.loadLibrary("avdevice");
        System.loadLibrary("native-lib");
    }

    private ActivityMainBinding binding;

    private static final int GET_PERMISSION_RECODE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_multimedia, R.id.navigation_video, R.id.navigation_audio, R.id.navigation_player)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        verifyAudioPermissions(this);

        stringFromJNI();
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        getSupportFragmentManager().findFragmentByTag(PlayerFragment.class.getName()).onActivityResult(requestCode, resultCode, data);
//    }

    private void verifyAudioPermissions(Activity activity) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, GET_PERMISSION_RECODE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, GET_PERMISSION_RECODE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, GET_PERMISSION_RECODE);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i("MainActivity", "HomeFragment++++++++++++++++++++++++WRITE_EXTERNAL_STORAGE");
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, GET_PERMISSION_RECODE);
            }
        }

    }

    public native String stringFromJNI();
    public native void NV21ToI420(byte[] nv21Src, byte[] i420Src, int width, int height);
    public native void I420Rotate90(byte[] i420Src, byte[] i420Des, int width, int height);
    public native void addWaterMarkWithFfmpeg(String inputFile, String markFile, String outputFile);
    public native void pushRtmpWithFfmpeg(String inputFile, String rtmpUrl);
}