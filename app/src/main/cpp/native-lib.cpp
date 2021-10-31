#include <jni.h>
#include <string>

#include <string>
#include <unistd.h>

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <libyuv.h>


#define LOG_TAG "MeidaOperationNative"
#define JLOG_V(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))
#define JLOG_D(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define JLOG_I(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define JLOG_W(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define JLOG_E(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

extern "C" {


#include "libyuv.h"
#include "add_wav_header.h"


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_mediaoperation_MainActivity_stringFromJNI(JNIEnv *env, jobject) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_mediaoperation_MainActivity_addWaveHeader(JNIEnv *env, jobject,
                                                           jstring pcm_file_path,
                                                           jstring wav_file_path,
                                                           jint channel, jint sample_rate,
                                                           jint sample_size) {

    const char *pcmPath = (char *) env->GetStringUTFChars(pcm_file_path, JNI_FALSE);
    char *wavPath = (char *) env->GetStringUTFChars(wav_file_path, JNI_FALSE);

    int ret = add_wave_header(pcmPath, wavPath, channel, sample_rate, sample_size);
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_I420Rotate90(JNIEnv *env, jobject, jbyteArray i420Src,
                                                        jbyteArray i420Des, jint width,
                                                        jint height) {

    libyuv::RotationMode mode = libyuv::kRotate90;

    jint srcWidth = width;
    jint srcHeight = height;
    jint dstWidth = height;
    jint dstHeight = width;

    jint src_y_size = srcWidth * srcHeight;
    jint src_u_size = ((srcWidth + 1) / 2) * ((srcHeight + 1) / 2);
    jint dst_y_size = dstWidth * dstHeight;
    jint dst_u_size = ((dstWidth + 1) / 2) * ((dstHeight + 1) / 2);

    //jbyte *src_i420 = reinterpret_cast<jbyte *>(i420Src);
    //jbyte *dst_i420 = reinterpret_cast<jbyte *>(i420Des);

    jbyte * src_i420 = env->GetByteArrayElements(i420Src, 0);
    jbyte * dst_i420 = env->GetByteArrayElements(i420Des, 0);

    JLOG_I("=================haha, call ok!");

    int ret = libyuv::I420Rotate(
                       (uint8_t *) src_i420, srcWidth,
                       (uint8_t *) src_i420 + src_y_size, (srcWidth + 1) / 2,
                       (uint8_t *) src_i420 + src_y_size + src_u_size,  (srcWidth + 1) / 2,
                       (uint8_t *) dst_i420, dstWidth,
                       (uint8_t *) dst_i420 + dst_y_size, (dstWidth + 1) / 2,
                       (uint8_t *) dst_i420 + dst_y_size + dst_u_size, (dstWidth + 1) / 2,
                       srcWidth, srcHeight, mode);

    JLOG_I("=================haha, call ok! ret=%d\n", ret);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_NV21ToI420(JNIEnv *env, jobject, jbyteArray nv21Src,
                                                        jbyteArray i420Src, jint width,
                                                        jint height) {

    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = reinterpret_cast<jbyte *>(nv21Src);
    //jbyte * src_nv21_y_data = (jbyte*)env->GetByteArrayElements(nv21Src, 0);
    jbyte *src_nv21_vu_data = reinterpret_cast<jbyte *>(nv21Src + src_y_size);
    jbyte *src_i420_y_data = reinterpret_cast<jbyte *>(i420Src);
    jbyte *src_i420_u_data = reinterpret_cast<jbyte *>(i420Src + src_y_size);
    jbyte *src_i420_v_data = reinterpret_cast<jbyte *>(i420Src + src_y_size + src_u_size);

    JLOG_I("=================haha, call ok!");

    int ret = libyuv::NV21ToI420((const uint8_t *) src_nv21_y_data, width,
                                 (const uint8_t *) src_nv21_vu_data, width,
                                 (uint8_t *) src_i420_y_data, width,
                                 (uint8_t *) src_i420_u_data, width >> 1,
                                 (uint8_t *) src_i420_v_data, width >> 1,
                                 width, height);
    JLOG_I("=================haha, call ok! ret=%d\n", ret);
}

}
