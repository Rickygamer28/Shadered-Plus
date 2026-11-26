package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;

/** CI only: does the packed FFmpeg load, and can it open a video over https? (logged) */
public final class VideoProbe {
    private static boolean ran;

    public static void run() {
        if (ran) {
            return;
        }
        ran = true;
        Thread t = new Thread(() -> {
            try {
                org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat.class);
                StringBuilder sb = new StringBuilder();
                org.bytedeco.javacpp.PointerPointer<?> opaque = new org.bytedeco.javacpp.PointerPointer<>(1);
                org.bytedeco.javacpp.BytePointer name;
                while ((name = org.bytedeco.ffmpeg.global.avformat.avio_enum_protocols(opaque, 0)) != null) {
                    sb.append(name.getString()).append(' ');
                }
                SkyBlocksMod.LOGGER.info("[tvprobe] ffmpeg {} input protocols: {}", org.bytedeco.ffmpeg.global.avutil.av_version_info().getString(), sb);
                try (org.bytedeco.javacv.FFmpegFrameGrabber g = new org.bytedeco.javacv.FFmpegFrameGrabber(
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")) {
                    g.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA);
                    g.start();
                    org.bytedeco.javacv.Frame f = g.grabImage();
                    SkyBlocksMod.LOGGER.info("[tvprobe] opened: {}x{} {} ms, frame {}x{} stride {}", g.getImageWidth(), g.getImageHeight(),
                            g.getLengthInTime() / 1000, f == null ? -1 : f.imageWidth, f == null ? -1 : f.imageHeight, f == null ? -1 : f.imageStride);
                }
            } catch (Throwable e) {
                SkyBlocksMod.LOGGER.error("[tvprobe] failed", e);
            }
        }, "tv-probe");
        t.setDaemon(true);
        t.start();
    }

    private VideoProbe() {}
}
