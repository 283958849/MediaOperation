# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("mediatest")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-lib.cpp filter_add_watermark.c ffmpeg_rtmp_push.c)

#set(LIBYUV_DIR ${CMAKE_SOURCE_DIR}/libyuv)
#add_subdirectory(${LIBYUV_DIR})
#include_directories(${LIBYUV_DIR}/include)


set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/libs)

#add_library(yuv
#        SHARED
#        IMPORTED )
#set_target_properties(yuv
#        PROPERTIES IMPORTED_LOCATION
#        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libyuv.so)
add_library(yuv
        STATIC
        IMPORTED )
set_target_properties(yuv
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)

add_library(x264
        SHARED
        IMPORTED )
set_target_properties(x264
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libx264.so)

add_library(avutil
        SHARED
        IMPORTED )
set_target_properties(avutil
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libavutil.so )

add_library(swresample
        SHARED
        IMPORTED )
set_target_properties(swresample
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libswresample.so )

add_library(swscale
        SHARED
        IMPORTED )
set_target_properties(swscale
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libswscale.so )

add_library(avcodec
        SHARED
        IMPORTED )
set_target_properties(avcodec
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libavcodec.so )

add_library(avformat
        SHARED
        IMPORTED )
set_target_properties(avformat
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libavformat.so )

add_library(avfilter
        SHARED
        IMPORTED )
set_target_properties(avfilter
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libavfilter.so )

add_library(avdevice
        SHARED
        IMPORTED )
set_target_properties(avdevice
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libavdevice.so )

add_library(postproc
        SHARED
        IMPORTED )
set_target_properties(postproc
        PROPERTIES IMPORTED_LOCATION
        ${JNI_LIBS_DIR}/${CMAKE_ANDROID_ARCH_ABI}/libpostproc.so )

include_directories(${CMAKE_SOURCE_DIR}/include)


find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        native-lib
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        yuv x264 avutil swresample swscale avcodec avformat avfilter avdevice postproc)