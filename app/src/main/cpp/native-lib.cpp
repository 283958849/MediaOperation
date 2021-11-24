#include <jni.h>
#include <string>
#include <unistd.h>


extern "C" {
#include "common.h"
#include "libyuv.h"
#include "libavcodec/avcodec.h"
#include "libavcodec/jni.h"
#include "filter_add_watermark.h"
#include "ffmpeg_rtmp_push.h"

extern "C"
JNIEXPORT
jint JNI_OnLoad(JavaVM *vm, void *res) {
    av_jni_set_java_vm(vm, 0);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_mediaoperation_MainActivity_stringFromJNI(JNIEnv *env, jobject) {
    std::string hello = "Hello from C++";

    int ver = (int) avcodec_version();
    JLOG_I("==============ffmpeg version: %d", ver);

    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_I420Rotate90(JNIEnv *env, jobject, jbyteArray i420Src,
                                                          jbyteArray i420Des, jint width, jint height) {
    jint srcWidth = width;
    jint srcHeight = height;
    jint dstWidth = height;
    jint dstHeight = width;

    jint src_y_size = srcWidth * srcHeight;
    jint src_u_size = ((srcWidth + 1) / 2) * ((srcHeight + 1) / 2);
    jint dst_y_size = dstWidth * dstHeight;
    jint dst_u_size = ((dstWidth + 1) / 2) * ((dstHeight + 1) / 2);

    jbyte *src_i420 = env->GetByteArrayElements(i420Src, 0);
    jbyte *dst_i420 = env->GetByteArrayElements(i420Des, 0);

    int ret = libyuv::I420Rotate(
            (uint8_t *) src_i420, srcWidth,
            (uint8_t *) src_i420 + src_y_size, (srcWidth + 1) / 2,
            (uint8_t *) src_i420 + src_y_size + src_u_size, (srcWidth + 1) / 2,
            (uint8_t *) dst_i420, dstWidth,
            (uint8_t *) dst_i420 + dst_y_size, (dstWidth + 1) / 2,
            (uint8_t *) dst_i420 + dst_y_size + dst_u_size, (dstWidth + 1) / 2,
            srcWidth, srcHeight, libyuv::kRotate90);

    //JLOG_I("=================libyuv::I420Rotate ret=%d\n", ret);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_NV21ToI420(JNIEnv *env, jobject, jbyteArray nv21Src,
                                                        jbyteArray i420Des, jint width,
                                                        jint height) {
    jint y_size = width * height;
    jint u_size = ((width + 1) / 2) * ((height + 1) / 2);

    jbyte *src_nv21 = env->GetByteArrayElements(nv21Src, 0);
    jbyte *dst_i420 = env->GetByteArrayElements(i420Des, 0);

    int ret = libyuv::NV21ToI420(
            (uint8_t *) src_nv21, width,
            (uint8_t *) src_nv21 + y_size, width,
            (uint8_t *) dst_i420, width,
            (uint8_t *) dst_i420 + y_size, (width + 1) / 2,
            (uint8_t *) dst_i420 + y_size + u_size, (width + 1) / 2,
            width, height);

    //JLOG_I("=================libyuv::NV21ToI420 ret=%d\n", ret);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_addWaterMarkWithFfmpeg(JNIEnv *env, jobject thiz,
         jstring input_file, jstring mark_file, jstring output_file) {
    char *input_file_str = (char *) env->GetStringUTFChars(input_file, JNI_FALSE);
    char *mark_file_str = (char *) env->GetStringUTFChars(mark_file, JNI_FALSE);
    char *output_file_str = (char *) env->GetStringUTFChars(output_file, JNI_FALSE);
    JLOG_I("=================JNI input=%s, mark=%s, out=%s\n", input_file_str, mark_file_str, output_file_str);

    ffmpeg_add_watermark(input_file_str, mark_file_str, output_file_str);
}

}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_mediaoperation_MainActivity_pushRtmpWithFfmpeg(JNIEnv *env, jobject thiz,
                                                                jstring input_file,
                                                                jstring rtmp_url) {
    char *input_file_str = (char *) env->GetStringUTFChars(input_file, JNI_FALSE);
    char *rtmp_url_str = (char *) env->GetStringUTFChars(rtmp_url, JNI_FALSE);
    ffmpeg_rtmp_push(input_file_str, rtmp_url_str);
}