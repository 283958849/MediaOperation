package com.example.mediaoperation.ui.audio;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AudioViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public AudioViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Audio fragment with audio handle.");
    }

    public LiveData<String> getText() {
        return mText;
    }
}