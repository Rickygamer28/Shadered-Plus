package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A YouTube HLS stream fetched by Java a piece at a time (YouTube cuts its whole-file links off after 1 MiB on some
 * networks, not its pieces - and takes Java's requests where it refuses FFmpeg's), given to FFmpeg as one stream of
 * bytes. A thread fetches the pieces ahead of where it's read (a few behind dropped); going somewhere starts the
 * stream again at the piece holding that time, at a new place along it ({@link #jumpTo}).
 */
final class HlsSource {
    private record Piece(String url, long startUs, long durUs) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int AHEAD = 24;
    private int logged;

    private final String userAgent;
    private final List<Piece> pieces = new ArrayList<>();
    private final byte[][] data;
    private final long totalUs;
    private volatile boolean gone;
    private volatile String failure;
    /** The piece being read, where in it, and where along the stream that piece began. */
    private volatile int readPiece;
    private int readOffset;
    private long pieceStartPos, pos;
    /** Where along the stream each jump started (FFmpeg's going back there maps to that piece). */
    private final List<long[]> starts = new ArrayList<>();

    HlsSource(String playlistUrl, String userAgent) throws IOException {
        this.userAgent = userAgent;
        String text = get(playlistUrl);
        long at = 0, dur = 0;
        URI base = URI.create(playlistUrl);
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                try {
                    dur = (long) (Double.parseDouble(line.substring(8).split(",")[0].trim()) * 1_000_000L);
                } catch (NumberFormatException e) {
                    dur = 0;
                }
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                pieces.add(new Piece(base.resolve(line).toString(), at, dur));
                at += dur;
                dur = 0;
            }
        }
        if (pieces.isEmpty()) {
            throw new IOException("its HLS list is empty");
        }
        totalUs = at;
        data = new byte[pieces.size()][];
        starts.add(new long[] {0, 0});
        Thread t = new Thread(this::fetch, "Shadered+ TV pieces");
        t.setDaemon(true);
        t.start();
    }

    long durationUs() {
        return totalUs;
    }

    String failure() {
        return failure;
    }

    /** How far its pieces have come (0-1). */
    float fetched() {
        int n = 0;
        for (byte[] d : data) if (d != null) n++;
        return Math.max(n, readPiece) / (float) data.length;
    }

    private void fetch() {
        try {
            while (!gone) {
                int from = readPiece;
                int next = -1;
                for (int i = from; i < Math.min(data.length, from + AHEAD); i++) {
                    if (data[i] == null) {
                        next = i;
                        break;
                    }
                }
                for (int i = 0; i < from - 2; i++) {
                    data[i] = null; // (well behind: let go)
                }
                if (next < 0) {
                    Thread.sleep(50);
                    continue;
                }
                IOException last = null;
                for (int attempt = 0; attempt < 6 && !gone; attempt++) {
                    try {
                        long t0 = System.currentTimeMillis();
                        data[next] = getBytes(pieces.get(next).url());
                        if (logged++ < 3) {
                            SkyBlocksMod.LOGGER.info("Shadered+ television: HLS piece {} came ({} KB in {} ms)", next, data[next].length / 1024,
                                    System.currentTimeMillis() - t0);
                        }
                        last = null;
                        break;
                    } catch (IOException e) {
                        last = e;
                        Thread.sleep(300L << Math.min(attempt, 3));
                    }
                }
                if (last != null) {
                    failure = last.getMessage();
                    SkyBlocksMod.LOGGER.info("Shadered+ television: an HLS piece wouldn't come ({}), piece {} of {}", failure, next, data.length);
                    return;
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    /** The piece holding this time (us from its start). */
    int pieceAt(long us) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            if (pieces.get(i).startUs() <= us) return i;
        }
        return 0;
    }

    long pieceStartUs(int i) {
        return pieces.get(i).startUs();
    }

    /** Starts the stream again at piece i, at a new place along it (returned: FFmpeg's to go to). */
    synchronized long jumpTo(int i) {
        long at = pos + (1L << 32);
        readPiece = i;
        readOffset = 0;
        pieceStartPos = at;
        pos = at;
        starts.add(new long[] {at, i});
        return at;
    }

    /** Up to n bytes into b (waiting for its piece if need be); -1 at its end. */
    synchronized int read(byte[] b, int n) throws IOException {
        while (true) {
            if (readPiece >= data.length) {
                return -1;
            }
            byte[] d = data[readPiece];
            if (d == null) {
                if (failure != null) {
                    throw new IOException(failure);
                }
                if (gone) {
                    return -1;
                }
                try {
                    wait(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                continue;
            }
            if (readOffset >= d.length) {
                readPiece++;
                readOffset = 0;
                continue;
            }
            int r = Math.min(n, d.length - readOffset);
            System.arraycopy(d, readOffset, b, 0, r);
            readOffset += r;
            pos += r;
            return r;
        }
    }

    /** FFmpeg going to a place along the stream (only back within where it has read since a start). */
    synchronized long seek(long to) {
        long[] best = null;
        for (long[] s : starts) {
            if (s[0] <= to && (best == null || s[0] > best[0])) best = s;
        }
        if (best == null) {
            return -1;
        }
        // (from that start, the pieces in order, as far as 'to')
        int piece = (int) best[1];
        long at = best[0];
        while (piece < data.length) {
            byte[] d = data[piece];
            if (d == null) {
                if (at == to) {
                    // (the start of a piece still coming: read from there once it has)
                    readPiece = piece;
                    readOffset = 0;
                    pos = to;
                    return to;
                }
                return -1;
            }
            if (at + d.length > to) {
                readPiece = piece;
                readOffset = (int) (to - at);
                pos = to;
                return to;
            }
            at += d.length;
            piece++;
        }
        return -1;
    }

    long position() {
        return pos;
    }

    void close() {
        gone = true;
    }

    private String get(String url) throws IOException {
        return new String(getBytes(url), java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent).GET().build();
        HttpResponse<byte[]> res;
        try {
            res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (res.statusCode() != 200) {
            throw new IOException("YouTube answered " + res.statusCode());
        }
        return res.body();
    }
}
