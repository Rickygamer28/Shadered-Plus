package com.example.skyblocks.client;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A cabinet's picture, small enough to send ten times a second: its own palette of up to 256 colours (the ones it uses
 * most), each pixel one of them, squeezed. ArcadeFramePayload's.
 */
public final class ArcadePicture {
    private ArcadePicture() {}

    /** Pixels (0xRRGGBB, w x h, top row first) -> bytes. */
    public static byte[] encode(int[] rgb, int w, int h) {
        int[] count = new int[32768];
        for (int i = 0; i < w * h; i++) count[key(rgb[i])]++;
        int distinct = 0;
        for (int c : count) if (c > 0) distinct++;
        int[] keys = new int[distinct];
        int n = 0;
        for (int k = 0; k < count.length; k++) if (count[k] > 0) keys[n++] = k;
        if (distinct > 256) { // (the 256 used most)
            Integer[] boxed = new Integer[distinct];
            for (int i = 0; i < distinct; i++) boxed[i] = keys[i];
            Arrays.sort(boxed, (a, b) -> count[b] - count[a]);
            keys = new int[256];
            for (int i = 0; i < 256; i++) keys[i] = boxed[i];
        }
        int colours = keys.length;
        int[] pr = new int[colours], pg = new int[colours], pb = new int[colours];
        // (each palette colour: the average of the pixels that are it)
        long[] sr = new long[colours], sg = new long[colours], sb = new long[colours], sn = new long[colours];
        int[] map = new int[32768];
        Arrays.fill(map, -1);
        for (int i = 0; i < colours; i++) {
            map[keys[i]] = i;
            pr[i] = (keys[i] >> 10 & 31) << 3 | 4;
            pg[i] = (keys[i] >> 5 & 31) << 3 | 4;
            pb[i] = (keys[i] & 31) << 3 | 4;
        }
        byte[] raw = new byte[1 + colours * 3 + w * h];
        for (int i = 0; i < w * h; i++) {
            int c = rgb[i], k = key(c);
            int m = map[k];
            if (m < 0) { // (not one of them: the nearest)
                int r = k >> 10 & 31, g = k >> 5 & 31, b = k & 31, best = 0, bestD = Integer.MAX_VALUE;
                for (int j = 0; j < colours; j++) {
                    int dr = r - (keys[j] >> 10 & 31), dg = g - (keys[j] >> 5 & 31), db = b - (keys[j] & 31);
                    int d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bestD) {
                        bestD = d;
                        best = j;
                    }
                }
                m = map[k] = best;
            }
            raw[1 + colours * 3 + i] = (byte) m;
            sr[m] += c >> 16 & 255;
            sg[m] += c >> 8 & 255;
            sb[m] += c & 255;
            sn[m]++;
        }
        raw[0] = (byte) (colours - 1);
        for (int i = 0; i < colours; i++) {
            if (sn[i] > 0) {
                pr[i] = (int) (sr[i] / sn[i]);
                pg[i] = (int) (sg[i] / sn[i]);
                pb[i] = (int) (sb[i] / sn[i]);
            }
            raw[1 + i * 3] = (byte) pr[i];
            raw[2 + i * 3] = (byte) pg[i];
            raw[3 + i * 3] = (byte) pb[i];
        }
        Deflater d = new Deflater(6);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
        byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    private static int key(int c) {
        return (c >> 19 & 31) << 10 | (c >> 11 & 31) << 5 | (c >> 3 & 31);
    }

    /** Bytes -> pixels (0xRRGGBB), or null if they don't make one. */
    public static int[] decode(byte[] data, int w, int h) {
        if (w <= 0 || h <= 0) return null;
        Inflater inf = new Inflater();
        inf.setInput(data);
        byte[] raw = new byte[1 + 256 * 3 + w * h];
        int got = 0;
        try {
            while (got < raw.length && !inf.finished()) {
                int n = inf.inflate(raw, got, raw.length - got);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                got += n;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
        int colours = (raw[0] & 255) + 1;
        if (got != 1 + colours * 3 + w * h) return null;
        int[] pal = new int[colours];
        for (int i = 0; i < colours; i++) pal[i] = (raw[1 + i * 3] & 255) << 16 | (raw[2 + i * 3] & 255) << 8 | raw[3 + i * 3] & 255;
        int[] out = new int[w * h];
        int at = 1 + colours * 3;
        for (int i = 0; i < w * h; i++) {
            int m = raw[at + i] & 255;
            out[i] = m < colours ? pal[m] : 0;
        }
        return out;
    }
}
