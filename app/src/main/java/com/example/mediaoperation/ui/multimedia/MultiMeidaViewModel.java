package com.example.mediaoperation.ui.multimedia;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MultiMeidaViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public MultiMeidaViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Multimedia fragment with v/a handle.");
    }


    public LiveData<String> getText() {
        return mText;
    }
}