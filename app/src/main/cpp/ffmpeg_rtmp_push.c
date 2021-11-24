//
// Created by terrylee on 11/19/21.
//

#include "ffmpeg_rtmp_push.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavutil/avutil.h>
#include <libswscale/swscale.h>
#include <libavutil/opt.h>
#include <unistd.h>
#include <libavutil/time.h>

#include "common.h"


static AVFormatContext *ifmt_ctx = NULL;
static AVFormatContext *ofmt_ctx = NULL;

static int open_input_file(const char *filename) {
    int ret;

    if ((ret = avformat_open_input(&ifmt_ctx, filename, NULL, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot open input file: %s\n", filename);
        return ret;
    }

    if ((ret = avformat_find_stream_info(ifmt_ctx, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot find stream information\n");
        return ret;
    }

    av_dump_format(ifmt_ctx, 0, filename, 0);
    return 0;
}

static char rtmp_url[255] ={0};

static int open_output_file(const char *filename) {
    int ret = 0;
    AVStream *in_stream;
    AVStream *out_stream;
    AVCodec *codec;

    memcpy(rtmp_url, filename, strlen(filename));

    if ((ret = avformat_alloc_output_context2(&ofmt_ctx, NULL, "flv", rtmp_url)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "can not alloc output context");
        return ret;
    }

    for (int i = 0; i < ifmt_ctx->nb_streams; i++) {
        in_stream = ifmt_ctx->streams[i];

        codec = avcodec_find_decoder(in_stream->codecpar->codec_id);
        if (!codec) {
            av_log(NULL, AV_LOG_FATAL, "Necessary encoder not found\n");
            return AVERROR_INVALIDDATA;
        }

        out_stream = avformat_new_stream(ofmt_ctx, codec);
        if (!out_stream) {
            av_log(NULL, AV_LOG_ERROR, "can not new stream for output");
            return AVERROR_UNKNOWN;
        }

        ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "Failed to copy encoder parameters #%u\n", i);
            return ret;
        }

        ////out_stream->time_base = encCtx->time_base;
    }

    //open the output file handle
    ret = avio_open(&ofmt_ctx->pb, rtmp_url, AVIO_FLAG_WRITE);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "can not open the output file handle");
        return ret;
    }

    av_dump_format(ofmt_ctx, 0, rtmp_url, 1);

    return 0;
}

static void my_logoutput(void *ptr, int level, const char *fmt, va_list vl) {
    va_list vl2;
    char *line = malloc(128 * sizeof(char));
    static int print_prefix = 1;
    va_copy(vl2, vl);
    av_log_format_line(ptr, level, fmt, vl2, line, 128, &print_prefix);
    va_end(vl2);
    line[127] = '\0';
    JLOG_E("%s", line);
    free(line);
}

int ffmpeg_rtmp_push(char *input_file, char *rtmp_url)
{
    int i;
    int ret;
    unsigned int stream_index;

    AVPacket *pkt_in = av_packet_alloc();
    if (!pkt_in) {
        ret = AVERROR(ENOMEM);
        av_log(NULL, AV_LOG_ERROR, "Could not allocate packet");
        return -1;
    }

    av_log_set_level(AV_LOG_INFO);
    av_log_set_callback(my_logoutput);

    JLOG_E("==============ffmpeg 111");

    if ((ret = open_input_file(input_file)) < 0)
        goto end;
    JLOG_E("==============ffmpeg 222");

    if ((ret = open_output_file(rtmp_url)) < 0)
        goto end;
    JLOG_E("==============ffmpeg 333");

    int64_t start_time = av_gettime();

    //write output file header
    if ((ret = avformat_write_header(ofmt_ctx, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "can not write output file header");
        return ret;
    }

    while (1) {
        if (av_read_frame(ifmt_ctx, pkt_in) < 0) {
            break;
        }

        stream_index = pkt_in->stream_index;
        JLOG_E("==============stream_index:%d, pts:%ld, dts:%ld\n", stream_index, pkt_in->pts, pkt_in->dts);

        AVRational itime = ifmt_ctx->streams[stream_index]->time_base;
        AVRational otime = ofmt_ctx->streams[stream_index]->time_base;
        pkt_in->pts = av_rescale_q_rnd(pkt_in->pts, itime, otime, (enum AVRounding)(AV_ROUND_NEAR_INF | AV_ROUND_NEAR_INF));
        pkt_in->dts = av_rescale_q_rnd(pkt_in->pts, itime, otime, (enum AVRounding)(AV_ROUND_NEAR_INF | AV_ROUND_NEAR_INF));
        pkt_in->duration = av_rescale_q_rnd(pkt_in->duration, itime, otime, (enum AVRounding)(AV_ROUND_NEAR_INF | AV_ROUND_NEAR_INF));
        pkt_in->pos = -1;

        JLOG_E("==============22stream_index:%d, pts:%ld, dts:%ld\n", stream_index, pkt_in->pts, pkt_in->dts);


        //视频帧推送速度
        if (ifmt_ctx->streams[stream_index]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            AVRational tb = ifmt_ctx->streams[stream_index]->time_base;
            //已经过去的时间
            long long now = av_gettime() - start_time;
            long long dts = pkt_in->dts * (1000 * 1000 * av_q2d(tb));
            if (dts > now)
                av_usleep(dts - now);
        }



//        AVStream *in_stream = ifmt_ctx->streams[stream_index];

//        av_packet_rescale_ts(pkt_in, in_stream->time_base, streamCtx[stream_index].decCtx->time_base);
//
//        pkt_in->pts = av_rescale_q_rnd(pkt_in->pts, in_stream->time_base,
//                                       ofmt_ctx->streams[stream_index]->time_base, AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
//        pkt_in->dts = av_rescale_q_rnd(pkt_in->dts, in_stream->time_base,
//                                       ofmt_ctx->streams[stream_index]->time_base, AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
//        pkt_in->duration = av_rescale_q(pkt_in->duration, in_stream->time_base, ofmt_ctx->streams[stream_index]->time_base);
//        pkt_in->pos = -1;

//        if (stream_index == v_stream_index) {
//            uint64_t now_time = av_gettime() - start_time;
//            if (pkt_in->pts > now_time)
//                av_usleep(pkt_in->pts - now_time);
//        }

        ret = av_interleaved_write_frame(ofmt_ctx, pkt_in);
        if (ret < 0) {
            break;
        }

        av_packet_unref(pkt_in);
    }

    av_write_trailer(ofmt_ctx);
    JLOG_I("==============line:%d\n", __LINE__);

    end:
    JLOG_I("==============line:%d\n", __LINE__);

    av_packet_free(&pkt_in);

    avformat_close_input(&ifmt_ctx);
    if (ofmt_ctx && !(ofmt_ctx->oformat->flags & AVFMT_NOFILE))
        avio_closep(&ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);

    if (ret < 0)
        av_log(NULL, AV_LOG_ERROR, "Error occurred: %s\n", av_err2str(ret));

    return ret;
}

