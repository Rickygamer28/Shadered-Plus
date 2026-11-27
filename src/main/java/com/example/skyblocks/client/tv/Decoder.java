package com.example.skyblocks.client.tv;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

import com.example.skyblocks.SkyBlocksMod;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.ffmpeg.swscale.SwsFilter;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

/**
 * One stream of a video - its picture or its sound - read and decoded by FFmpeg on a thread of its own, into a short
 * queue: pictures as RGBA (at most 1280 by 720), sound as 16-bit mono at 48 kHz. Every FFmpeg call happens on that
 * thread (it frees everything itself when closed); others only ask it to go somewhere ({@link #seek}) or to close.
 * Times are in microseconds from the start of the media.
 */
final class Decoder {
    static final int RATE = 48000;
    static final int MAX_W = 1280, MAX_H = 720;

    record Picture(long us, ByteBuffer rgba, int w, int h, int gen) {}

    record Sound(long us, short[] samples, int gen) {}

    final boolean video;
    /** Where to read it from: the first that opens (YouTube may refuse one of its copies and give another). */
    private final java.util.List<String> urls;
    private final Map<String, String> options;
    private final Thread thread;
    private volatile boolean closed;
    private final AtomicLong seekTo = new AtomicLong(-1);
    /** Bumped on every seek: what was queued before it is thrown away. */
    volatile int gen;
    final BlockingQueue<Picture> pictures = new ArrayBlockingQueue<>(6);
    final BlockingQueue<Sound> sounds = new ArrayBlockingQueue<>(64);
    /** Pictures' buffers, given back once shown. */
    final BlockingQueue<ByteBuffer> spare = new ArrayBlockingQueue<>(8);

    // ---- what it found, once open (read from other threads)
    volatile boolean open, failed, ended, missing;
    volatile String error = "";
    volatile long durationUs;
    volatile int width, height;

    Decoder(java.util.List<String> urls, Map<String, String> options, boolean video) {
        this.urls = urls;
        this.options = options;
        this.video = video;
        thread = new Thread(this::run, video ? "Shadered+ TV picture" : "Shadered+ TV sound");
        thread.setDaemon(true);
    }

    void start(long atUs) {
        if (atUs > 0) {
            seekTo.set(atUs);
        }
        thread.start();
    }

    void seek(long us) {
        seekTo.set(Math.max(0, us));
    }

    void close() {
        closed = true;
    }

    private static volatile boolean ready;

    private static synchronized void init() {
        if (!ready) {
            av_log_set_level(AV_LOG_ERROR);
            avformat_network_init();
            ready = true;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void run() {
        AVFormatContext fmt = null;
        AVCodecContext ctx = null;
        AVPacket pkt = null;
        AVFrame frame = null;
        SwsContext sws = null;
        SwrContext swr = null;
        BytePointer out = null;
        AVIOInterruptCB.Callback_Pointer abort = null;
        if (urls.isEmpty()) {
            missing = true; // (nothing to read: e.g. a song, its sound only)
            error = "nothing to read";
            failed = true;
            return;
        }
        try {
            init();
            abort = new AVIOInterruptCB.Callback_Pointer() {
                @Override
                public int call(Pointer opaque) {
                    return closed ? 1 : 0;
                }
            };
            int r = -1;
            String javaAgent = options.get(JAVA_HTTP);
            for (int u = 0; u < urls.size() && !closed; u++) {
                fmt = avformat_alloc_context();
                fmt.interrupt_callback().callback(abort);
                AVDictionary opts = new AVDictionary(null);
                for (Map.Entry<String, String> e : options.entrySet()) {
                    if (!e.getKey().equals(JAVA_HTTP)) {
                        av_dict_set(opts, e.getKey(), e.getValue(), 0);
                    }
                }
                String url = urls.get(u);
                boolean hlsLink = url.startsWith(YouTube.HLS);
                if (hlsLink) {
                    url = url.substring(YouTube.HLS.length());
                }
                String agent = javaAgent, ytId = null;
                int tagAt = url.indexOf(YouTube.UA_TAG);
                if (tagAt >= 0) {
                    String rest = url.substring(tagAt + YouTube.UA_TAG.length());
                    int idAt = rest.indexOf(YouTube.ID_TAG);
                    if (idAt >= 0) {
                        ytId = rest.substring(idAt + YouTube.ID_TAG.length());
                        rest = rest.substring(0, idAt);
                    }
                    agent = java.net.URLDecoder.decode(rest, java.nio.charset.StandardCharsets.UTF_8);
                    url = url.substring(0, tagAt);
                }
                if (hlsLink) {
                    // (its pieces fetched by Java, given to FFmpeg as one stream)
                    closeSource();
                    try {
                        hls = new HlsSource(url, agent != null ? agent : "Mozilla/5.0");
                    } catch (Exception e) {
                        SkyBlocksMod.LOGGER.info("Shadered+ television: its HLS list wouldn't come ({}), trying another copy", e.getMessage());
                        avformat_free_context(fmt);
                        fmt = null;
                        continue;
                    }
                    BytePointer ioBuf = new BytePointer(av_malloc(IO_BUF));
                    io = avio_alloc_context(ioBuf, IO_BUF, 0, null, reader(), null, seeker());
                    fmt.pb(io);
                    fmt.flags(fmt.flags() | 0x0080); // (AVFMT_FLAG_CUSTOM_IO)
                    url = "";
                } else if (agent != null && url.contains("googlevideo.com/videoplayback")) {
                    // (read by Java, given to FFmpeg as it asks)
                    closeSource();
                    source = new HttpSource(url, agent, ytId);
                    if (!video) {
                        source.fetchAhead(); // (its sound: all of it fetched ahead - YouTube's stopping part way doesn't stop it)
                    }
                    BytePointer ioBuf = new BytePointer(av_malloc(IO_BUF));
                    io = avio_alloc_context(ioBuf, IO_BUF, 0, null, reader(), null, seeker());
                    fmt.pb(io);
                    fmt.flags(fmt.flags() | 0x0080); // (AVFMT_FLAG_CUSTOM_IO)
                    url = "";
                }
                r = avformat_open_input(fmt, url, (AVInputFormat) null, opts);
                av_dict_free(opts);
                if (r >= 0) {
                    break;
                }
                fmt = null; // (freed by FFmpeg when opening fails)
                freeIo();
                closeSource();
                if (u + 1 < urls.size()) {
                    SkyBlocksMod.LOGGER.info("Shadered+ television: its {} wouldn't open ({}), trying another copy", video ? "picture" : "sound", err(r));
                }
            }
            if (r < 0 || fmt == null) {
                fail("couldn't open it (" + (error.isEmpty() ? err(r) : error) + ")");
                return;
            }
            if ((r = avformat_find_stream_info(fmt, (PointerPointer) null)) < 0) {
                fail("couldn't read it (" + err(r) + ")");
                return;
            }
            int index = av_find_best_stream(fmt, video ? AVMEDIA_TYPE_VIDEO : AVMEDIA_TYPE_AUDIO, -1, -1, (PointerPointer) null, 0);
            if (index < 0) {
                missing = true;
                fail(video ? "no picture in it" : "no sound in it");
                return;
            }
            AVStream st = fmt.streams(index);
            AVCodecParameters par = st.codecpar();
            AVCodec codec = avcodec_find_decoder(par.codec_id());
            if (codec == null) {
                fail("its " + (video ? "picture" : "sound") + " is in a format that can't be played");
                return;
            }
            ctx = avcodec_alloc_context3(codec);
            avcodec_parameters_to_context(ctx, par);
            ctx.thread_count(video ? Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)) : 1);
            if ((r = avcodec_open2(ctx, codec, (PointerPointer) null)) < 0) {
                fail("couldn't decode it (" + err(r) + ")");
                return;
            }
            double tb = st.time_base().num() / (double) st.time_base().den();
            long start = fmt.start_time() != AV_NOPTS_VALUE ? fmt.start_time() : 0L;
            durationUs = fmt.duration() != AV_NOPTS_VALUE && fmt.duration() > 0 ? fmt.duration() : 0L;
            if (hls != null) {
                durationUs = hls.durationUs();
                String kind = fmt.iformat() == null ? "" : fmt.iformat().name().getString();
                if (!video && !kind.contains("mpegts")) {
                    countFromUs = 0; // (plain AAC pieces: its times counted)
                    counted = 0;
                }
            }
            int ow = 0, oh = 0;
            if (video) {
                int w = par.width(), h = par.height();
                float k = Math.min(1.0F, Math.min(MAX_W / (float) Math.max(1, w), MAX_H / (float) Math.max(1, h)));
                ow = Math.max(2, Math.round(w * k) & ~1);
                oh = Math.max(2, Math.round(h * k) & ~1);
                width = ow;
                height = oh;
                out = new BytePointer(av_malloc((long) ow * oh * 4));
            }
            pkt = av_packet_alloc();
            frame = av_frame_alloc();
            open = true;

            long dropBefore = -1, lastUs = 0;
            boolean eof = false;
            IntPointer dstStride = new IntPointer(1).put(ow * 4);
            PointerPointer dst = video ? new PointerPointer(1).put(0, out) : null;
            AVChannelLayout mono = new AVChannelLayout();
            av_channel_layout_default(mono, 1);
            BytePointer pcm = null;
            int pcmCap = 0;
            PointerPointer pcmOut = new PointerPointer(1);

            while (!closed) {
                long s = seekTo.getAndSet(-1);
                if (s >= 0) {
                    if (hls != null) {
                        // (back to the piece holding it, its stream started again there)
                        int piece = hls.pieceAt(s);
                        avio_seek(io, hls.jumpTo(piece), 0);
                        avformat_flush(fmt);
                        if (countFromUs >= 0) {
                            countFromUs = hls.pieceStartUs(piece);
                            counted = 0;
                        }
                    } else {
                        av_seek_frame(fmt, -1, s + start, AVSEEK_FLAG_BACKWARD);
                    }
                    avcodec_flush_buffers(ctx);
                    gen++;
                    pictures.clear();
                    sounds.clear();
                    dropBefore = s;
                    eof = false;
                    ended = false;
                }
                if (eof) {
                    sleep(15);
                    continue;
                }
                if (video ? pictures.remainingCapacity() == 0 : sounds.size() > 40) {
                    sleep(4);
                    continue;
                }
                r = av_read_frame(fmt, pkt);
                if (r < 0) {
                    avcodec_send_packet(ctx, null); // (what's left in the decoder, out)
                    eof = true;
                } else if (pkt.stream_index() != index) {
                    av_packet_unref(pkt);
                    continue;
                } else {
                    avcodec_send_packet(ctx, pkt);
                    av_packet_unref(pkt);
                }
                while (!closed && avcodec_receive_frame(ctx, frame) == 0) {
                    long ts = frame.best_effort_timestamp();
                    long us = ts == AV_NOPTS_VALUE ? lastUs : (long) (ts * tb * 1_000_000.0) - start;
                    if (countFromUs >= 0 && frame.sample_rate() > 0) {
                        us = countFromUs + counted * 1_000_000L / frame.sample_rate();
                        counted += frame.nb_samples();
                    }
                    lastUs = us;
                    if (dropBefore >= 0 && us < dropBefore - (video ? 20_000 : 0)) {
                        av_frame_unref(frame);
                        continue;
                    }
                    dropBefore = -1;
                    int g = gen;
                    if (video) {
                        sws = sws_getCachedContext(sws, frame.width(), frame.height(), frame.format(), ow, oh, AV_PIX_FMT_RGBA, SWS_BILINEAR,
                                (SwsFilter) null, (SwsFilter) null, (DoublePointer) null);
                        sws_scale(sws, frame.data(), frame.linesize(), 0, frame.height(), dst, dstStride);
                        ByteBuffer b = spare.poll();
                        if (b == null || b.capacity() != ow * oh * 4) {
                            b = ByteBuffer.allocateDirect(ow * oh * 4).order(ByteOrder.nativeOrder());
                        }
                        b.clear();
                        b.put(out.position(0).limit((long) ow * oh * 4).asByteBuffer());
                        b.flip();
                        while (!closed && gen == g && !pictures.offer(new Picture(us, b, ow, oh, g))) {
                            sleep(3);
                        }
                    } else {
                        if (swr == null) {
                            swr = swr_alloc();
                            AVChannelLayout in = frame.ch_layout();
                            if (in.nb_channels() <= 0) {
                                in = new AVChannelLayout();
                                av_channel_layout_default(in, Math.max(1, ctx.ch_layout().nb_channels()));
                            }
                            av_opt_set_chlayout(swr, "in_chlayout", in, 0);
                            av_opt_set_chlayout(swr, "out_chlayout", mono, 0);
                            av_opt_set_int(swr, "in_sample_rate", frame.sample_rate(), 0);
                            av_opt_set_int(swr, "out_sample_rate", RATE, 0);
                            av_opt_set_sample_fmt(swr, "in_sample_fmt", frame.format(), 0);
                            av_opt_set_sample_fmt(swr, "out_sample_fmt", AV_SAMPLE_FMT_S16, 0);
                            if (swr_init(swr) < 0) {
                                fail("its sound can't be converted");
                                return;
                            }
                        }
                        int want = swr_get_out_samples(swr, frame.nb_samples());
                        if (want > pcmCap) {
                            if (pcm != null) {
                                av_free(pcm);
                            }
                            pcmCap = want + 1024;
                            pcm = new BytePointer(av_malloc((long) pcmCap * 2));
                            pcmOut.put(0, pcm);
                        }
                        int got = swr_convert(swr, pcmOut, pcmCap, frame.extended_data(), frame.nb_samples());
                        if (got > 0) {
                            short[] samples = new short[got];
                            pcm.position(0).limit((long) got * 2).asByteBuffer().order(ByteOrder.nativeOrder()).asShortBuffer().get(samples);
                            while (!closed && gen == g && !sounds.offer(new Sound(us, samples, g))) {
                                sleep(3);
                            }
                        }
                    }
                    av_frame_unref(frame);
                }
                if (eof) {
                    ended = true;
                }
            }
            if (pcm != null) {
                av_free(pcm);
            }
        } catch (Throwable e) {
            SkyBlocksMod.LOGGER.warn("Shadered+ television: {} failed", video ? "picture" : "sound", e);
            fail(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        } finally {
            if (frame != null) {
                av_frame_free(frame);
            }
            if (pkt != null) {
                av_packet_free(pkt);
            }
            if (ctx != null) {
                avcodec_free_context(ctx);
            }
            if (sws != null) {
                sws_freeContext(sws);
            }
            if (swr != null) {
                swr_free(swr);
            }
            if (out != null) {
                av_free(out);
            }
            if (fmt != null) {
                avformat_close_input(fmt);
            }
            freeIo();
            closeSource();
            if (abort != null) {
                abort.close();
            }
            if (readCb != null) {
                readCb.close();
            }
            if (seekCb != null) {
                seekCb.close();
            }
        }
    }

    /** Options' key: read http links by Java with this user agent (YouTube's). */
    static final String JAVA_HTTP = "shadered_java_http";
    private static final int IO_BUF = 64 * 1024;
    private volatile HttpSource source;
    private volatile HlsSource hls;
    /** Its sound's times counted from its samples (HLS sound pieces carry none FFmpeg reads): from this, so many since. */
    private long countFromUs = -1, counted;
    private org.bytedeco.ffmpeg.avformat.AVIOContext io;
    private org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int readCb;
    private org.bytedeco.ffmpeg.avformat.Seek_Pointer_long_int seekCb;
    private byte[] ioBytes = new byte[IO_BUF];

    private org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int reader() {
        if (readCb == null) {
            readCb = new org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int() {
                @Override
                public int call(Pointer opaque, BytePointer buf, int size) {
                    if (closed || source == null && hls == null) {
                        return AVERROR_EXIT;
                    }
                    try {
                        if (ioBytes.length < size) {
                            ioBytes = new byte[size];
                        }
                        int r = hls != null ? hls.read(ioBytes, size) : source.read(ioBytes, size);
                        if (r <= 0) {
                            return AVERROR_EOF;
                        }
                        buf.position(0).capacity(size).limit(size).put(ioBytes, 0, r);
                        return r;
                    } catch (Exception e) {
                        error = e.getMessage() == null ? e.toString() : e.getMessage();
                        return AVERROR_EOF;
                    }
                }
            };
        }
        return readCb;
    }

    private org.bytedeco.ffmpeg.avformat.Seek_Pointer_long_int seeker() {
        if (seekCb == null) {
            seekCb = new org.bytedeco.ffmpeg.avformat.Seek_Pointer_long_int() {
                @Override
                public long call(Pointer opaque, long offset, int whence) {
                    if (hls != null) {
                        if ((whence & AVSEEK_SIZE) != 0 || (whence & 3) == 2) {
                            return -1;
                        }
                        return hls.seek((whence & 3) == 1 ? hls.position() + offset : offset);
                    }
                    if (source == null) {
                        return -1;
                    }
                    if ((whence & AVSEEK_SIZE) != 0) {
                        return source.length();
                    }
                    long to = switch (whence & 3) {
                        case 1 -> source.position() + offset;
                        case 2 -> source.length() < 0 ? -1 : source.length() + offset;
                        default -> offset;
                    };
                    if (to < 0) {
                        return -1;
                    }
                    source.seek(to);
                    return to;
                }
            };
        }
        return seekCb;
    }

    /** Its source's fetching (sound fetched ahead): how much has come, 1 if it isn't fetched ahead. */
    float fetched() {
        HlsSource h = hls;
        if (h != null) {
            return h.failure() == null ? 1.0F : h.fetched();
        }
        HttpSource src = source;
        return src == null ? 1.0F : src.fetched();
    }

    @org.jetbrains.annotations.Nullable
    String fetchProblem() {
        HlsSource h = hls;
        if (h != null) {
            return h.failure();
        }
        HttpSource src = source;
        return src == null ? null : src.fetchProblem();
    }

    private void freeIo() {
        if (io != null) {
            if (io.buffer() != null) {
                av_free(io.buffer());
            }
            avio_context_free(io);
            io = null;
        }
    }

    private void closeSource() {
        if (source != null) {
            source.close();
            source = null;
        }
        if (hls != null) {
            hls.close();
            hls = null;
        }
    }

    private void fail(String why) {
        SkyBlocksMod.LOGGER.warn("Shadered+ television: its {} can't play: {}", video ? "picture" : "sound", why);
        error = why;
        failed = true;
    }

    private static String err(int code) {
        BytePointer b = new BytePointer(256);
        av_strerror(code, b, 256);
        byte[] a = new byte[256];
        b.position(0).limit(256).get(a);
        b.close();
        int n = 0;
        while (n < a.length && a[n] != 0) n++;
        return new String(a, 0, n, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
