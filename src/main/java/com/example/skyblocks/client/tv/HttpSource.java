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

    private final String url, userAgent;
    private long length = -1, pos, inPos = -1, inEnd;
    private InputStream in;

    HttpSource(String url, String userAgent) {
        this.url = url;
        this.userAgent = userAgent;
        Matcher m = CLEN.matcher(url);
        if (m.find()) {
            length = Long.parseLong(m.group(1));
        }
    }

    long length() {
        return length;
    }

    long position() {
        return pos;
    }

    void seek(long to) {
        pos = Math.max(0, to);
    }

    /** Up to n bytes into b; -1 at its end. */
    int read(byte[] b, int n) throws IOException {
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
        if (res.statusCode() != 200 && res.statusCode() != 206) {
            res.body().close();
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
        closeStream();
    }
}
