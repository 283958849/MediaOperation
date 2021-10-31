//
// Created by terrylee on 10/20/21.
//

#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <jni.h>

#include "add_wav_header.h"

int add_wave_header(const char *pcm_path, char *wav_path, int channels, int sample_rate, int sample_size)
{
    /*
    FILE *pcm_fd = fopen(pcm_path, "rb");
    if (pcm_fd == NULL) {
        return errno;
    }

    FILE *wav_fd = fopen(wav_path, "wb+");
    if (wav_fd == NULL) {
        return errno;
    }

    wave_header_t header;
    memcpy(header.ChunkID, "RIFF", strlen("RIFF"));
    memcpy(header.Format, "WAVE", strlen("WAVE"));

    wave_fmt_t pcm_fmt;
    memcpy(pcm_fmt.Subchunk1ID, "fmt", strlen("fmt"));
    */

    return JNI_OK;
}