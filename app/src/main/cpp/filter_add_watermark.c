
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

#include "common.h"

#include "filter_add_watermark.h"

char *filter_str = "movie=%s[wm];[in][wm]overlay=5:5[out]";


static AVFormatContext *ifmt_ctx = NULL;
static AVFormatContext *ofmt_ctx = NULL;

AVFilterContext *buffersink_ctx = NULL;
AVFilterContext *buffersrc_ctx = NULL;
AVFilterGraph *filter_graph = NULL;
static int v_stream_index = -1;

typedef struct StreamContext {
    AVCodecContext *decCtx;
    AVCodecContext *encCtx;
    AVFrame *decFrame;
} StreamContext;
static StreamContext *streamCtx = NULL;

AVPacket *pkt_out = NULL;


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

    streamCtx = av_mallocz_array(ifmt_ctx->nb_streams, sizeof(StreamContext));
    if (!streamCtx) {
        return AVERROR(ENOMEM);
    }

    //handle video and vaudio, not only for AVMEDIA_TYPE_VIDEO
    for (int i = 0; i < ifmt_ctx->nb_streams; i++) {
        AVStream *stream = ifmt_ctx->streams[i];
        av_log(NULL, AV_LOG_ERROR, "codec_id=%d, ", stream->codecpar->codec_id);

        //todo, some mp4 file contained AV_CODEC_ID_BIN_DATA, it will avcodec_find_decoder failed
        //AVCodec *dec = avcodec_find_decoder(stream->codecpar->codec_id);
        AVCodec *dec = NULL;
        if (stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            if (stream->codecpar->codec_id == AV_CODEC_ID_H264) {
                dec = avcodec_find_decoder_by_name("h264_mediacodec");
            } else {
                dec = avcodec_find_decoder(stream->codecpar->codec_id);
            }
        } else if (stream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            dec = avcodec_find_decoder(stream->codecpar->codec_id);
        }

        if (!dec) {
            av_log(NULL, AV_LOG_ERROR, "Failed to find decoder for stream #%u\n", i);
            return AVERROR_DECODER_NOT_FOUND;
        } else {
            av_log(NULL, AV_LOG_ERROR, "find decoder ok\n");
        }

        AVCodecContext *decCtx = avcodec_alloc_context3(dec);
        if (!decCtx) {
            av_log(NULL, AV_LOG_ERROR, "Failed to allocate the decoder context for stream #%u\n",
                   i);
            return AVERROR(ENOMEM);
        }

        ret = avcodec_parameters_to_context(decCtx, stream->codecpar);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "Failed to copy decoder parameters for stream #%u\n", i);
            return ret;
        }

        if (decCtx->codec_type == AVMEDIA_TYPE_VIDEO || decCtx->codec_type == AVMEDIA_TYPE_AUDIO) {
            if (decCtx->codec_type == AVMEDIA_TYPE_VIDEO) {
                ////decCtx->framerate = av_guess_frame_rate(ifmt_ctx, stream, NULL);
                AVRational rate = { 60, 1 };
                decCtx->framerate = rate;
                v_stream_index = i;
            }

            if ((ret = avcodec_open2(decCtx, dec, NULL)) < 0) {
                av_log(NULL, AV_LOG_ERROR, "Cannot open video decoder\n");
                return ret;
            }
        }

        streamCtx[i].decCtx = decCtx;
        streamCtx[i].decFrame = av_frame_alloc();
        if (!streamCtx[i].decFrame) {
            return AVERROR(ENOMEM);
        }
    }

    av_dump_format(ifmt_ctx, 0, filename, 0);
    return 0;
}

static int open_output_file(const char *filename) {
    int ret = 0;
    AVStream *in_stream;
    AVStream *out_stream;
    AVCodec *enc;
    AVCodecContext *encCtx;
    AVCodecContext *decCtx;

    if ((ret = avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, filename)) < 0) {
        //todo, change to log
        av_log(NULL, AV_LOG_ERROR, "can not alloc output context");
        return ret;
    }

    for (int i = 0; i < ifmt_ctx->nb_streams; i++) {
        out_stream = avformat_new_stream(ofmt_ctx, NULL);
        if (!out_stream) {
            av_log(NULL, AV_LOG_ERROR, "can not new stream for output");
            return AVERROR_UNKNOWN;
        }

        in_stream = ifmt_ctx->streams[i];
        decCtx = streamCtx[i].decCtx;
        if (decCtx->codec_type == AVMEDIA_TYPE_VIDEO || decCtx->codec_type == AVMEDIA_TYPE_AUDIO) {
            enc = avcodec_find_encoder(decCtx->codec_id);
            if (!enc) {
                av_log(NULL, AV_LOG_FATAL, "Necessary encoder not found\n");
                return AVERROR_INVALIDDATA;
            }

            encCtx = avcodec_alloc_context3(enc);
            if (!encCtx) {
                av_log(NULL, AV_LOG_FATAL, "Necessary encoder not found\n");
                return AVERROR(ENOMEM);
            }

            if (decCtx->codec_type == AVMEDIA_TYPE_VIDEO) {
                //in fact, encCtx->width must come from decCtx->width
                encCtx->width = decCtx->width;
                encCtx->height = decCtx->height;
                encCtx->sample_aspect_ratio = decCtx->sample_aspect_ratio;
                if (enc->pix_fmts) {
                    encCtx->pix_fmt = enc->pix_fmts[0];
                } else {
                    encCtx->pix_fmt = decCtx->pix_fmt;
                }

                //todo check it out, why ?
                // NOTICE, here works!
                encCtx->time_base = av_inv_q(decCtx->framerate);
                // following can not work
                //encCtx->time_base = av_inv_q(in_stream->time_base);
                //encCtx->time_base = in_stream->time_base;
            } else {
                encCtx->sample_rate = decCtx->sample_rate;
                encCtx->channel_layout = decCtx->channel_layout;
                encCtx->channels = av_get_channel_layout_nb_channels(decCtx->channel_layout);
                encCtx->sample_fmt = enc->sample_fmts[0];
                encCtx->time_base = (AVRational) {1, encCtx->sample_rate};
            }

            if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
                encCtx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

            //open encoder
            ret = avcodec_open2(encCtx, enc, NULL);
            if (ret < 0) {
                av_log(NULL, AV_LOG_ERROR, "Cannot open video encoder for stream #%u\n", i);
                return ret;
            }

            ret = avcodec_parameters_from_context(out_stream->codecpar, encCtx);
            if (ret < 0) {
                av_log(NULL, AV_LOG_ERROR,
                       "Failed to copy encoder parameters to output stream #%u\n", i);
                return ret;
            }

            out_stream->time_base = encCtx->time_base;

            // all actions are for this line purpose
            streamCtx[i].encCtx = encCtx;
        } else if (decCtx->codec_type == AVMEDIA_TYPE_UNKNOWN) {
            av_log(NULL, AV_LOG_FATAL, "Elementary stream #%d is of unknown type, cannot proceed\n",
                   i);
            return AVERROR_INVALIDDATA;
        } else {
            ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
            if (ret < 0) {
                av_log(NULL, AV_LOG_ERROR, "Copying parameters for stream #%u failed\n", i);
                return ret;
            }
            out_stream->time_base = in_stream->time_base;
        }
    }

    //dump output info
    av_dump_format(ofmt_ctx, 0, filename, 1);

    //open the output file handle
    if (!(ofmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ofmt_ctx->pb, filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "can not open the output file handle");
            return ret;
        }
    }

    //write output file header
    if ((ret = avformat_write_header(ofmt_ctx, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "can not write output file header");
        return ret;
    }

    return 0;
}

static int init_filters(const char *filters_descr) {
    int ret;
    char args[512];
    const AVFilter *buffersrc = avfilter_get_by_name("buffer");
    const AVFilter *buffersink = avfilter_get_by_name("buffersink");
    AVFilterInOut *outputs = avfilter_inout_alloc();
    AVFilterInOut *inputs = avfilter_inout_alloc();

    filter_graph = avfilter_graph_alloc();
    if (!buffersrc || !buffersink || !outputs || !inputs || !filter_graph) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    JLOG_E("==============ffmpeg 333.3, v_stream_index:%d\n", v_stream_index);

    //NOTICE: here decCtx have no correct time_base
    AVCodecContext *codecCtx = streamCtx[v_stream_index].encCtx;

    snprintf(args, sizeof(args),
             "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
             codecCtx->width, codecCtx->height, codecCtx->pix_fmt,
             codecCtx->time_base.num, codecCtx->time_base.den,
             codecCtx->sample_aspect_ratio.num, codecCtx->sample_aspect_ratio.den);
    JLOG_E("============args:%s\n", args);

    ret = avfilter_graph_create_filter(&buffersrc_ctx, buffersrc, "in",
                                       args, NULL, filter_graph);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot create buffer source:\n %s\n", args);
        goto end;
    }

    JLOG_E("==============ffmpeg 333.21");
    ret = avfilter_graph_create_filter(&buffersink_ctx, buffersink, "out",
                                       NULL, NULL, filter_graph);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot create buffer sink\n");
        goto end;
    }
    JLOG_E("==============ffmpeg 333.22");

    ret = av_opt_set_bin(buffersink_ctx, "pix_fmts",
                         (uint8_t *) &codecCtx->pix_fmt, sizeof(codecCtx->pix_fmt),
                         AV_OPT_SEARCH_CHILDREN);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot set output pixel format\n");
        goto end;
    }

    /* Endpoints for the filter graph. */
    outputs->name = av_strdup("in");
    outputs->filter_ctx = buffersrc_ctx;
    outputs->pad_idx = 0;
    outputs->next = NULL;

    inputs->name = av_strdup("out");
    inputs->filter_ctx = buffersink_ctx;
    inputs->pad_idx = 0;
    inputs->next = NULL;
    JLOG_E("==============ffmpeg 333.23");

    if ((ret = avfilter_graph_parse_ptr(filter_graph, filters_descr, &inputs, &outputs, NULL)) < 0)
        goto end;
    JLOG_E("==============ffmpeg 8888888");

    if ((ret = avfilter_graph_config(filter_graph, NULL)) < 0) {
        JLOG_E("==============ffmpeg, ret:%d\n", ret);
        goto end;
    }
    JLOG_E("==============ffmpeg 99999999");

    end:
    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);

    return ret;
}

static int encode_write_frame(AVFrame *filtered_frame, unsigned int stream_index) {
    int ret;

    ret = avcodec_send_frame(streamCtx[stream_index].encCtx, filtered_frame);
    if (ret < 0)
        return ret;

    while (ret >= 0) {
        ret = avcodec_receive_packet(streamCtx[stream_index].encCtx, pkt_out);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;

        av_packet_rescale_ts(pkt_out, streamCtx[stream_index].encCtx->time_base,
                             ofmt_ctx->streams[stream_index]->time_base);

        ret = av_interleaved_write_frame(ofmt_ctx, pkt_out);
    }

    return ret;
}

static int filter_encode_write_frame(AVFrame *frame, AVFrame *filt_frame, unsigned int stream_index) {
    /* push the decoded frame into the filtergraph */
    int ret = av_buffersrc_add_frame(buffersrc_ctx, frame);
    if (ret < 0) {
        av_log(NULL, AV_LOG_ERROR, "Error while feeding the filtergraph\n");
        return ret;
    }

    /* pull filtered frames from the filtergraph */
    while (1) {
        ret = av_buffersink_get_frame(buffersink_ctx, filt_frame);
        if (ret < 0) {
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
                ret = 0;
            break;
        }

        filt_frame->pict_type = AV_PICTURE_TYPE_NONE;
        ret = encode_write_frame(filt_frame, stream_index);
        av_frame_unref(filt_frame);
        if (ret < 0)
            break;
    }

    return ret;
}

static void my_logoutput(void *ptr, int level, const char *fmt, va_list vl)
{
//    va_list vl2;
//    char *line = malloc(128 * sizeof(char));
//    static int print_prefix = 1;
//    va_copy(vl2, vl);
//    av_log_format_line(ptr, level, fmt, vl2, line, 128, &print_prefix);
//    va_end(vl2);
//    line[127] = '\0';
//    JLOG_E("%s", line);
//    free(line);

    FILE *fp = fopen("/storage/emulated/0/Android/av_log.txt", "w+");
    if (fp) {
        vfprintf(fp, fmt, vl);
        fflush(fp);
        fclose(fp);
    }
}

static void sleep_ms(unsigned int msecs)
{
    struct timeval tval;
    tval.tv_sec=msecs/1000;
    tval.tv_usec=(msecs*1000)%1000000;

    select(0,NULL,NULL,NULL,&tval);
}

int ffmpeg_add_watermark(char *input_file, char *mark_file, char *output_file)
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

    pkt_out = av_packet_alloc();
    if (!pkt_out) {
        ret = AVERROR(ENOMEM);
        return ret;
    }

    AVFrame *filt_frame = av_frame_alloc();
    if (!filt_frame) {
        ret = AVERROR(ENOMEM);
        av_log(NULL, AV_LOG_ERROR, "Could not allocate frame");
        return -1;
    }
    //JLOG_I("==============line:%d\n", __LINE__);

    JLOG_E("==============ffmpeg 111");

    if ((ret = open_input_file(input_file)) < 0)
        goto end;
    JLOG_E("==============ffmpeg 222");

    if ((ret = open_output_file(output_file)) < 0)
        goto end;
    JLOG_E("==============ffmpeg 333");

    char filter_des[128];

    //snprintf(args, sizeof(args), "movie=%s[wm];[in][wm]overlay=5:5[out]", mark_file);
    sprintf(filter_des, filter_str, mark_file);
    JLOG_E("==============ffmpeg version: %s", filter_des);

    //in fact, we just create video filter, no audio filter now!
    if ((ret = init_filters(filter_des)) < 0) {
        JLOG_E("==============ffmpeg 333.3, ret:%d\n", ret);
        goto end;
    }
    JLOG_E("==============ffmpeg 333.4, ret:%d\n", ret);


//    av_log_set_level(AV_LOG_INFO);
//    av_log_set_callback(my_logoutput);

    while (1) {
        JLOG_E("==============ffmpeg 333.5, ret:%d\n", ret);
        if (av_read_frame(ifmt_ctx, pkt_in) < 0) {
            break;
        }

        stream_index = pkt_in->stream_index;
        JLOG_E("==============Demuxer gave frame of stream_index:%d\n", stream_index);
        av_log(NULL, AV_LOG_ERROR, "width: %d\n", stream_index);


        if (stream_index == v_stream_index) {
            StreamContext *stream = &streamCtx[stream_index];

            av_packet_rescale_ts(pkt_in, ifmt_ctx->streams[stream_index]->time_base,
                                 stream->decCtx->time_base);

            ret = avcodec_send_packet(stream->decCtx, pkt_in);
            ////JLOG_E("==============ffmpeg 444, ret:%d\n", ret);

            ////sleep_ms(50);

            if (ret < 0) {
                if (ret == AVERROR(EAGAIN)) {
                    av_packet_unref(pkt_in);
                    continue;
                }

                av_log(NULL, AV_LOG_ERROR, "Error while sending a packet to the decoder\n");
                break;
            }

            while (ret >= 0) {
                ret = avcodec_receive_frame(stream->decCtx, stream->decFrame);
                //JLOG_I("==============line:%d, ret=%d\n", __LINE__, ret);
                JLOG_E("==============ffmpeg 444.2 ret = %d\n", ret);


                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                    break;
                } else if (ret < 0) {
                    av_log(NULL, AV_LOG_ERROR, "Error while receiving a frame from the decoder\n");
                    goto end;
                }

                stream->decFrame->pts = stream->decFrame->best_effort_timestamp;

                ret = filter_encode_write_frame(stream->decFrame, filt_frame, stream_index);
                if (ret < 0)
                    goto end;
            }
        } else {
            //JLOG_I("==============line:%d\n", __LINE__);
            av_packet_rescale_ts(pkt_in, ifmt_ctx->streams[stream_index]->time_base,
                                 ofmt_ctx->streams[stream_index]->time_base);
            ret = av_interleaved_write_frame(ofmt_ctx, pkt_in);
            if (ret < 0)
                goto end;
        }
        av_packet_unref(pkt_in);
    }

    // flush filter and encoder
    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        // just for video
        if (i == v_stream_index) {
            ret = filter_encode_write_frame(NULL, filt_frame, i);
            if (ret < 0) {
                av_log(NULL, AV_LOG_ERROR, "Flushing filter failed\n");
                goto end;
            }

            if (streamCtx[i].encCtx->codec->capabilities & AV_CODEC_CAP_DELAY) {
                ret = encode_write_frame(NULL, i);
                if (ret < 0) {
                    av_log(NULL, AV_LOG_ERROR, "Flushing filter failed\n");
                    goto end;
                }
            }
        }
    }

    av_write_trailer(ofmt_ctx);
    JLOG_I("==============line:%d\n", __LINE__);

end:
    JLOG_I("==============line:%d\n", __LINE__);

    av_frame_free(&filt_frame);
    av_packet_free(&pkt_in);
    av_packet_free(&pkt_out);

    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        avcodec_free_context(&streamCtx[i].decCtx);
        avcodec_free_context(&streamCtx[i].encCtx);
        av_frame_free(&streamCtx[i].decFrame);
    }

    avfilter_graph_free(&filter_graph);
    avformat_close_input(&ifmt_ctx);
    if (ofmt_ctx && !(ofmt_ctx->oformat->flags & AVFMT_NOFILE))
        avio_closep(&ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);

    if (ret < 0)
        av_log(NULL, AV_LOG_ERROR, "Error occurred: %s\n", av_err2str(ret));

    return ret;
}

