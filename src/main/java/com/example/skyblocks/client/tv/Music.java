package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A YouTube Music song as the television shows it: its cover, title and artist, and its lyrics (from LRCLIB, a free
 * lyrics library - in time with the song when it has them so), all fetched on the link's thread.
 */
public final class Music {
    /** A line of its lyrics and when it's sung (ms; -1 when the lyrics aren't timed). */
    public record Line(long ms, String text) {}

    public final String title, artist;
    /** Its cover (square), until it's made into a texture; null without one. */
    volatile NativeImage cover;
    /** The cover's colour (for the background behind it). */
    public volatile int colour = 0xFF3A3326;
    /** Its lyrics: null still looking, empty none found. */
    public volatile List<Line> lyrics;
    public volatile boolean timed;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern LRC = Pattern.compile("^\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s?(.*)$");

    Music(String title, String artist) {
        this.title = title;
        this.artist = artist;
    }

    /** The song's details, then (still on the link's thread) its cover and lyrics. */
    static Music load(String rawTitle, String rawArtist, String thumbnail, String id, long seconds) {
        String artist = rawArtist.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").trim();
        String title = rawTitle;
        // ("Artist - Title (Official Video)": the title alone)
        if (title.toLowerCase(java.util.Locale.ROOT).startsWith(artist.toLowerCase(java.util.Locale.ROOT) + " - ")) {
            title = title.substring(artist.length() + 3);
        }
        title = title.replaceAll("(?i)\\s*[\\(\\[](official|lyric|lyrics|audio|music video|video|visualizer|hd|4k)[^\\)\\]]*[\\)\\]]", "").trim();
        Music m = new Music(title, artist);
        Thread t = new Thread(() -> {
            m.loadCover(thumbnail, id);
            m.loadLyrics(seconds);
        }, "Shadered+ TV song");
        t.setDaemon(true);
        t.start();
        return m;
    }

    private void loadCover(String thumbnail, String id) {
        List<String> tries = new ArrayList<>();
        if (thumbnail.contains("googleusercontent.com")) {
            tries.add(thumbnail.replaceAll("=w\\d+-h\\d+.*$", "") + "=w544-h544-l90-rj");
        }
        tries.add("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg");
        tries.add("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg");
        for (String url : tries) {
            try {
                HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) {
                    continue;
                }
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(res.body()));
                if (img == null) {
                    continue;
                }
                // (square: the middle of it - a video's picture has the cover in its middle, or is the cover)
                int side = Math.min(img.getWidth(), img.getHeight());
                int x0 = (img.getWidth() - side) / 2, y0 = (img.getHeight() - side) / 2;
                int size = Math.min(512, side);
                NativeImage out = new NativeImage(size, size, false);
                long r = 0, g = 0, b = 0;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int argb = img.getRGB(x0 + x * side / size, y0 + y * side / size);
                        int cr = argb >> 16 & 255, cg = argb >> 8 & 255, cb = argb & 255;
                        out.setPixelRGBA(x, y, 0xFF000000 | cb << 16 | cg << 8 | cr); // (ABGR)
                        r += cr;
                        g += cg;
                        b += cb;
                    }
                }
                long n = (long) size * size;
                colour = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
                cover = out;
                return;
            } catch (Exception e) {
                // (the next)
            }
        }
    }

    private void loadLyrics(long seconds) {
        try {
            String q = "track_name=" + enc(title) + "&artist_name=" + enc(artist);
            JsonObject best = null;
            HttpResponse<String> res = get("https://lrclib.net/api/get?" + q + (seconds > 0 ? "&duration=" + seconds : ""));
            if (res != null && res.statusCode() == 200) {
                best = JsonParser.parseString(res.body()).getAsJsonObject();
            }
            if (best == null || isEmpty(best, "syncedLyrics")) {
                res = get("https://lrclib.net/api/search?" + q);
                if (res != null && res.statusCode() == 200) {
                    JsonArray all = JsonParser.parseString(res.body()).getAsJsonArray();
                    JsonObject plain = null;
                    for (JsonElement e : all) {
                        JsonObject o = e.getAsJsonObject();
                        long d = o.has("duration") && !o.get("duration").isJsonNull() ? (long) o.get("duration").getAsDouble() : 0;
                        if (seconds > 0 && d > 0 && Math.abs(d - seconds) > 8) {
                            continue;
                        }
                        if (!isEmpty(o, "syncedLyrics")) {
                            best = o;
                            break;
                        }
                        if (plain == null && !isEmpty(o, "plainLyrics")) {
                            plain = o;
                        }
                    }
                    if (best == null || isEmpty(best, "syncedLyrics") && isEmpty(best, "plainLyrics")) {
                        best = plain;
                    }
                }
            }
            List<Line> lines = new ArrayList<>();
            if (best != null && !isEmpty(best, "syncedLyrics")) {
                for (String l : best.get("syncedLyrics").getAsString().split("\n")) {
                    Matcher m = LRC.matcher(l.trim());
                    if (m.matches()) {
                        long ms = Long.parseLong(m.group(1)) * 60_000L + (long) (Double.parseDouble(m.group(2)) * 1000);
                        lines.add(new Line(ms, m.group(3).trim()));
                    }
                }
                timed = true;
            } else if (best != null && !isEmpty(best, "plainLyrics")) {
                for (String l : best.get("plainLyrics").getAsString().split("\n")) {
                    if (!l.isBlank()) {
                        lines.add(new Line(-1, l.trim()));
                    }
                }
            }
            lyrics = lines;
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.info("Shadered+ television: no lyrics for {} - {} ({})", artist, title, e.toString());
            lyrics = List.of();
        }
    }

    @Nullable
    private static HttpResponse<String> get(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Shadered+ (Minecraft mod; https://github.com/Rickygamer28/Shadered-Plus)").GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEmpty(JsonObject o, String key) {
        return !o.has(key) || o.get(key).isJsonNull() || o.get(key).getAsString().isBlank();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
