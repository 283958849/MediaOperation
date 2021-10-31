//
// Created by terrylee on 10/20/21.
//

#ifndef MEDIATEST_ADD_WAV_HEADER_H
#define MEDIATEST_ADD_WAV_HEADER_H

#ifdef __cplusplus
    extern "C" {
#endif

typedef struct {
    unsigned char ChunkID[4]; //内容为"RIFF"
    unsigned long ChunkSize;  //存储文件的字节数（不包含ChunkID和ChunkSize这8个字节）
    char Format[4];  //内容为"WAVE“
} wave_header_t;

typedef struct {
    unsigned char Subchunk1ID[4]; //内容为"fmt"
    unsigned long Subchunk1Size;  //存储该子块的字节数（不含前面的Subchunk1ID和Subchunk1Size这8个字节）
    unsigned short AudioFormat;    //存储音频文件的编码格式，例如若为PCM则其存储值为1。
    unsigned short NumChannels;    //声道数，单声道(Mono)值为1，双声道(Stereo)值为2，等等
    unsigned long SampleRate;     //采样率，如8k，44.1k等
    unsigned long ByteRate;       //每秒存储的bit数，其值 = SampleRate * NumChannels * BitsPerSample / 8
    unsigned short BlockAlign;     //块对齐大小，其值 = NumChannels * BitsPerSample / 8
    unsigned short BitsPerSample;  //每个采样点的bit数，一般为8,16,32等。
} wave_fmt_t;

typedef struct {
    unsigned char Subchunk2ID[4]; //内容为“data”
    unsigned long Subchunk2Size;  //接下来的正式的数据部分的字节数，其值 = NumSamples * NumChannels * BitsPerSample / 8
} wave_data_t;

int add_wave_header(const char *pcm_path, char *wav_path, int channels, int sample_rate, int sample_size);

#ifdef __cplusplus
}
#endif

#endif //MEDIATEST_ADD_WAV_HEADER_H
