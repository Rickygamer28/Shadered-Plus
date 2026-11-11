package com.example.skyblocks.client;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * A photo's picture, squeezed the way ArcadePicture's are (so ArcadePicture.decode reads it), but chosen for a photo:
 * a palette of 256 colours cut from the picture's own (median cut), and the pixels dithered between them, so skies and
 * shadows shade smoothly rather than in bands.
 */
public final class PhotoCodec {
    private PhotoCodec() {}

    /** The full-colour format's first bytes ('P', 2), then width, height (two bytes each). */
    private static final byte MARK = 'P', VERSION = 2;
    /** Bits kept of each colour channel (262,144 colours), a fine dither hiding the steps. */
    private static final int BITS = 6;
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /**
     * Pixels (0xRRGGBB, w x h, top row first) -> bytes, in full colour: each channel to 6 bits (dithered a touch),
     * each row as the change from the pixel before it, squeezed.
     */
    public static byte[] encodeFull(int[] rgb, int w, int h) {
        int shift = 8 - BITS;
        byte[] raw = new byte[w * h * 3];
        int[] prev = new int[3];
        for (int y = 0; y < h; y++) {
            prev[0] = prev[1] = prev[2] = 0;
            for (int x = 0; x < w; x++) {
                int c = rgb[y * w + x];
                float d = (BAYER[(y & 3) * 4 + (x & 3)] / 16.0F - 0.5F) * (1 << shift);
                int[] ch = {c >> 16 & 255, c >> 8 & 255, c & 255};
                for (int k = 0; k < 3; k++) {
                    int q = Math.max(0, Math.min((1 << BITS) - 1, Math.round((ch[k] + d) / (1 << shift) - 0.5F + 0.5F)));
                    raw[(y * w + x) * 3 + k] = (byte) (q - prev[k]);
                    prev[k] = q;
                }
            }
        }
        Deflater d = new Deflater(9);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2 + 64);
        out.write(MARK);
        out.write(VERSION);
        out.write(w >> 8);
        out.write(w);
        out.write(h >> 8);
        out.write(h);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    /** A picture read back: its size and pixels (0xRRGGBB). */
    public record Picture(int w, int h, int[] rgb) {}

    /** Bytes -> a picture, whichever format (full colour, or the first photos' palette one at 160 x 120); null if not one. */
    public static Picture decode(byte[] data) {
        if (data.length > 6 && data[0] == MARK && data[1] == VERSION) {
            int w = (data[2] & 255) << 8 | data[3] & 255, h = (data[4] & 255) << 8 | data[5] & 255;
            if (w <= 0 || h <= 0 || w > 1024 || h > 1024) {
                return null;
            }
            java.util.zip.Inflater inf = new java.util.zip.Inflater();
            inf.setInput(data, 6, data.length - 6);
            byte[] raw = new byte[w * h * 3];
            int got = 0;
            try {
                while (got < raw.length && !inf.finished()) {
                    int n = inf.inflate(raw, got, raw.length - got);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                        break;
                    }
                    got += n;
                }
            } catch (java.util.zip.DataFormatException e) {
                return null;
            } finally {
                inf.end();
            }
            if (got != raw.length) {
                return null;
            }
            int[] out = new int[w * h];
            int shift = 8 - BITS;
            for (int y = 0; y < h; y++) {
                int r = 0, g = 0, b = 0;
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 3;
                    r = (r + raw[i]) & 255;
                    g = (g + raw[i + 1]) & 255;
                    b = (b + raw[i + 2]) & 255;
                    // (back to 8 bits, the low bits filled from the high ones)
                    int rr = r << shift | r >> (BITS - shift), gg = g << shift | g >> (BITS - shift), bb = b << shift | b >> (BITS - shift);
                    out[y * w + x] = (rr & 255) << 16 | (gg & 255) << 8 | (bb & 255);
                }
            }
            return new Picture(w, h, out);
        }
        int[] old = ArcadePicture.decode(data, 160, 120);
        return old == null ? null : new Picture(160, 120, old);
    }

    /** Pixels (0xRRGGBB, w x h, top row first) -> bytes. */
    public static byte[] encode(int[] rgb, int w, int h) {
        int[] pal = palette(rgb, 256);
        int n = pal.length;
        int[] pr = new int[n], pg = new int[n], pb = new int[n];
        for (int i = 0; i < n; i++) {
            pr[i] = pal[i] >> 16 & 255;
            pg[i] = pal[i] >> 8 & 255;
            pb[i] = pal[i] & 255;
        }
        byte[] raw = new byte[1 + n * 3 + w * h];
        raw[0] = (byte) (n - 1);
        for (int i = 0; i < n; i++) {
            raw[1 + i * 3] = (byte) pr[i];
            raw[2 + i * 3] = (byte) pg[i];
            raw[3 + i * 3] = (byte) pb[i];
        }
        // Floyd-Steinberg: each pixel's leftover passed on to the ones not done yet
        float[] er = new float[(w + 2) * 2], eg = new float[(w + 2) * 2], eb = new float[(w + 2) * 2];
        for (int y = 0; y < h; y++) {
            int cur = (y & 1) * (w + 2), nxt = ((y + 1) & 1) * (w + 2);
            for (int x = 0; x < w + 2; x++) {
                er[nxt + x] = eg[nxt + x] = eb[nxt + x] = 0;
            }
            for (int x = 0; x < w; x++) {
                int c = rgb[y * w + x];
                float r = clamp((c >> 16 & 255) + er[cur + x + 1]), g = clamp((c >> 8 & 255) + eg[cur + x + 1]), b = clamp((c & 255) + eb[cur + x + 1]);
                int best = 0;
                float bestD = Float.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    float dr = r - pr[j], dg = g - pg[j], db = b - pb[j];
                    float d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bestD) {
                        bestD = d;
                        best = j;
                    }
                }
                raw[1 + n * 3 + y * w + x] = (byte) best;
                float qr = r - pr[best], qg = g - pg[best], qb = b - pb[best];
                // (a little less than all of it: keeps flat areas calm)
                qr *= 0.85F;
                qg *= 0.85F;
                qb *= 0.85F;
                er[cur + x + 2] += qr * 7 / 16;
                eg[cur + x + 2] += qg * 7 / 16;
                eb[cur + x + 2] += qb * 7 / 16;
                er[nxt + x] += qr * 3 / 16;
                eg[nxt + x] += qg * 3 / 16;
                eb[nxt + x] += qb * 3 / 16;
                er[nxt + x + 1] += qr * 5 / 16;
                eg[nxt + x + 1] += qg * 5 / 16;
                eb[nxt + x + 1] += qb * 5 / 16;
                er[nxt + x + 2] += qr / 16;
                eg[nxt + x + 2] += qg / 16;
                eb[nxt + x + 2] += qb / 16;
            }
        }
        Deflater d = new Deflater(9);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2 + 64);
        byte[] buf = new byte[4096];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    /** Up to {@code most} colours that stand for the picture's: its colours split again and again at the middle of the widest. */
    static int[] palette(int[] rgb, int most) {
        List<int[]> boxes = new ArrayList<>();
        boxes.add(rgb.clone());
        while (boxes.size() < most) {
            int pick = -1, widest = 0, channel = 0;
            for (int i = 0; i < boxes.size(); i++) {
                int[] box = boxes.get(i);
                if (box.length < 2) {
                    continue;
                }
                for (int ch = 0; ch < 3; ch++) {
                    int lo = 255, hi = 0, shift = 16 - ch * 8;
                    for (int c : box) {
                        int v = c >> shift & 255;
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                    // (a box with many pixels is worth splitting a little sooner)
                    int score = (hi - lo) * (int) Math.sqrt(box.length);
                    if (hi > lo && score > widest) {
                        widest = score;
                        pick = i;
                        channel = shift;
                    }
                }
            }
            if (pick < 0) {
                break;
            }
            int[] box = boxes.remove(pick);
            final int sh = channel;
            Integer[] sorted = new Integer[box.length];
            for (int i = 0; i < box.length; i++) {
                sorted[i] = box[i];
            }
            java.util.Arrays.sort(sorted, (a, b) -> (a >> sh & 255) - (b >> sh & 255));
            int half = sorted.length / 2;
            int[] a = new int[half], b = new int[sorted.length - half];
            for (int i = 0; i < sorted.length; i++) {
                if (i < half) a[i] = sorted[i];
                else b[i - half] = sorted[i];
            }
            boxes.add(a);
            boxes.add(b);
        }
        int[] pal = new int[boxes.size()];
        for (int i = 0; i < pal.length; i++) {
            long r = 0, g = 0, b = 0;
            int[] box = boxes.get(i);
            for (int c : box) {
                r += c >> 16 & 255;
                g += c >> 8 & 255;
                b += c & 255;
            }
            int k = Math.max(1, box.length);
            pal[i] = (int) (r / k) << 16 | (int) (g / k) << 8 | (int) (b / k);
        }
        return pal;
    }
}
