//
// Created by terrylee on 11/4/21.
//

#ifndef MEDIAOPERATION_COMMON_H
#define MEDIAOPERATION_COMMON_H

#include <android/log.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>


#define LOG_TAG "MeidaOperationNative"
#define JLOG_V(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))
#define JLOG_D(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define JLOG_I(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define JLOG_W(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define JLOG_E(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#endif //MEDIAOPERATION_COMMON_H
