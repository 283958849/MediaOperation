package com.example.mediaoperation.ui.multimedia;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MultiMeidaViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public MultiMeidaViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
    }


    public LiveData<String> getText() {
        return mText;
    }
}