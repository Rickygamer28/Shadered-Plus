package com.example.skyblocks.client.tv;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YouTube stream read by Java (which YouTube lets through where FFmpeg's own requests are refused), a piece at a
 * time (1 MB asked for at once) - for the decoder to take from (its own reading and going
 * to places, Decoder's custom input).
 */
final class HttpSource {
    private static final long CHUNK = 1L << 20;
    private static final Pattern CLEN = Pattern.compile("[?&]clen=(\\d+)");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private String url;
    private final String userAgent;
    /** The YouTube video it's of (a fresh link asked for when YouTube stops taking this one), or null. */
    private final String videoId;
    private int refreshes;
    private long length = -1, pos, inPos = -1, inEnd;
    private InputStream in;

    HttpSource(String url, String userAgent, String videoId) {
        this.url = url;
        this.userAgent = userAgent;
        this.videoId = videoId;
        Matcher m = CLEN.matcher(url);
        if (m.find()) {
            length = Long.parseLong(m.group(1));
        }
    }

    // ---- the whole of it fetched ahead (its sound): a download running on, reads taken from what has come

    private static final long MAX_AHEAD = 160L << 20;
    private byte[] data;
    private volatile long have;
    private volatile boolean done, gone;
    private volatile IOException failure;

    /**
     * Fetches all of it, from the start, on a thread of its own (fresh links asked for if YouTube stops taking one) -
     * reads then come from what has arrived, waiting for it only if they get ahead. Only when its length is known and
     * not too big: true if it does.
     */
    boolean fetchAhead() {
        if (length <= 0 || length > MAX_AHEAD) {
            return false;
        }
        data = new byte[(int) length];
        Thread t = new Thread(() -> {
            byte[] buf = new byte[64 * 1024];
            long at = 0;
            try {
                while (at < length && !gone) {
                    pos = at;
                    int r = readNet(buf, (int) Math.min(buf.length, length - at));
                    if (r <= 0) {
                        break;
                    }
                    System.arraycopy(buf, 0, data, (int) at, r);
                    at += r;
                    have = at;
                }
            } catch (IOException e) {
                failure = e;
                com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: fetching its sound stopped at {} of {} bytes ({})", at, length,
                        e.getMessage());
            } finally {
                done = true;
                closeStream();
            }
        }, "Shadered+ TV fetch");
        t.setDaemon(true);
        ahead = true;
        readPos = 0;
        t.start();
        return true;
    }

    private boolean ahead;
    private long readPos;

    long length() {
        return length;
    }

    long position() {
        return ahead ? readPos : pos;
    }

    void seek(long to) {
        if (ahead) {
            readPos = Math.max(0, to);
        } else {
            pos = Math.max(0, to);
        }
    }

    /** Up to n bytes into b; -1 at its end. */
    int read(byte[] b, int n) throws IOException {
        if (!ahead) {
            return readNet(b, n);
        }
        while (have <= readPos && !done && !gone) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        if (readPos >= have) {
            if (failure != null) {
                throw failure;
            }
            return -1;
        }
        int r = (int) Math.min(n, have - readPos);
        System.arraycopy(data, (int) readPos, b, 0, r);
        readPos += r;
        return r;
    }

    /** Up to n bytes into b from the network, at pos; -1 at its end. */
    private int readNet(byte[] b, int n) throws IOException {
        if (length >= 0 && pos >= length) {
            return -1;
        }
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            int r;
            try {
                if (in == null || inPos != pos || pos >= inEnd) {
                    open();
                }
                r = in.read(b, 0, n);
            } catch (IOException e) {
                // (a dropped connection, or YouTube's server busy: again, a little later)
                last = e;
                closeStream();
                try {
                    Thread.sleep(250L << Math.min(attempt, 3));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                continue;
            }
            if (r > 0) {
                pos += r;
                inPos = pos;
                return r;
            }
            closeStream();
            if (length >= 0 && pos >= length) {
                return -1;
            }
            if (length < 0 && r < 0 && pos < inEnd) {
                return -1; // (its end, its length unknown)
            }
        }
        if (last != null) {
            throw last;
        }
        return -1;
    }

    private void open() throws IOException {
        closeStream();
        long end = pos + CHUNK - 1;
        if (length >= 0) {
            end = Math.min(end, length - 1);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent)
                .header("Range", "bytes=" + pos + "-" + end).GET().build();
        HttpResponse<InputStream> res;
        try {
            res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if ((res.statusCode() == 403 || res.statusCode() == 410) && videoId != null && refreshes < 20) {
            // (YouTube stopped taking this link part way: a fresh one to the same copy, going on from here)
            res.body().close();
            refreshes++;
            String fresh = YouTube.fresh(videoId, url, userAgent);
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: YouTube refused part way ({}), {}", res.statusCode(),
                    fresh == null ? "no fresh link" : "going on with a fresh link");
            if (fresh != null) {
                url = fresh;
                open();
                return;
            }
        }
        if (res.statusCode() != 200 && res.statusCode() != 206) {
            res.body().close();
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: YouTube answered {} at byte {}", res.statusCode(), pos);
            throw new IOException("YouTube answered " + res.statusCode());
        }
        in = res.body();
        inPos = pos;
        inEnd = end + 1;
    }

    private void closeStream() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            in = null;
        }
    }

    void close() {
        gone = true;
        if (!ahead) {
            closeStream();
        }
    }
}
