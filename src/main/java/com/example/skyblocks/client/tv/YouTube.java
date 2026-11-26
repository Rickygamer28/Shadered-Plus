package com.example.skyblocks.client.tv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Finds where a YouTube video's picture and sound can be read from, asking YouTube's own player service as its apps
 * do (no extra libraries). Tries a couple of the apps, as one may be refused for a video another may play.
 */
final class YouTube {
    /** Where its picture and sound can be read from, best first (the next tried when one is refused). */
    record Streams(java.util.List<String> video, java.util.List<String> audio, String title, boolean live, String userAgent, String author, String thumbnail,
                   long seconds) {}

    private record Client(String name, int id, String version, String userAgent, String extra) {}

    private static final Client[] CLIENTS = {
            new Client("ANDROID_VR", 28, "1.65.10",
                    "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                    "\"deviceMake\":\"Oculus\",\"deviceModel\":\"Quest 3\",\"androidSdkVersion\":32,\"osName\":\"Android\",\"osVersion\":\"12L\""),
            new Client("IOS", 5, "20.10.4", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                    "\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone16,2\",\"osName\":\"iPhone\",\"osVersion\":\"18.3.2.22D82\""),
    };

    private static final Pattern ID = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** The video's id, if it's a YouTube link. */
    @Nullable
    static String id(String link) {
        Matcher m = ID.matcher(link.trim());
        return m.find() ? m.group(1) : null;
    }

    static Streams resolve(String id) throws Exception {
        String why = "YouTube didn't answer";
        for (Client c : CLIENTS) {
            String body = "{\"context\":{\"client\":{\"clientName\":\"" + c.name + "\",\"clientVersion\":\"" + c.version + "\"," + c.extra
                    + ",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + id + "\",\"contentCheckOk\":true,\"racyCheckOk\":true}";
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.youtube.com/youtubei/v1/player?prettyPrint=false"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", c.userAgent)
                    .header("X-YouTube-Client-Name", Integer.toString(c.id))
                    .header("X-YouTube-Client-Version", c.version)
                    .header("Origin", "https://www.youtube.com")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res;
            try {
                res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                why = "couldn't reach YouTube";
                continue;
            }
            if (res.statusCode() != 200) {
                why = "YouTube answered " + res.statusCode();
                continue;
            }
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject status = obj(root, "playabilityStatus");
            String state = status == null ? "" : str(status, "status");
            if (!"OK".equals(state)) {
                String reason = status == null ? null : str(status, "reason");
                why = reason != null && !reason.isEmpty() ? reason : "YouTube won't play it here (" + state + ")";
                continue;
            }
            JsonObject details = obj(root, "videoDetails");
            String title = details == null ? "" : str(details, "title");
            boolean live = details != null && details.has("isLive") && details.get("isLive").getAsBoolean();
            String author = details == null || str(details, "author") == null ? "" : str(details, "author");
            long seconds = 0;
            try {
                seconds = details == null || str(details, "lengthSeconds") == null ? 0 : Long.parseLong(str(details, "lengthSeconds"));
            } catch (NumberFormatException ignored) {
            }
            String thumb = "";
            JsonObject th = details == null ? null : obj(details, "thumbnail");
            if (th != null && th.has("thumbnails")) {
                JsonArray all = th.getAsJsonArray("thumbnails");
                int bestW = -1;
                for (JsonElement t : all) {
                    JsonObject to = t.getAsJsonObject();
                    int tw = to.has("width") ? to.get("width").getAsInt() : 0;
                    if (tw > bestW && str(to, "url") != null) {
                        bestW = tw;
                        thumb = str(to, "url");
                    }
                }
            }
            JsonObject streaming = obj(root, "streamingData");
            if (streaming == null) {
                why = "YouTube gave no way to play it";
                continue;
            }
            String hls = str(streaming, "hlsManifestUrl");
            if (live && hls != null) {
                return new Streams(java.util.List.of(hls), java.util.List.of(hls), title, true, c.userAgent, author, thumb, seconds);
            }
            java.util.List<String> video = ranked(streaming.getAsJsonArray("adaptiveFormats"), true);
            java.util.List<String> audio = ranked(streaming.getAsJsonArray("adaptiveFormats"), false);
            // (the copy with both, last: lower quality, but YouTube seldom refuses it)
            java.util.List<String> muxed = ranked(streaming.getAsJsonArray("formats"), true);
            video.addAll(muxed);
            audio.addAll(muxed);
            if (hls != null) {
                video.add(hls);
                audio.add(hls);
            }
            if (video.isEmpty() || audio.isEmpty()) {
                why = "YouTube gave no stream that can be read";
                continue;
            }
            return new Streams(video, audio, title, live, c.userAgent, author, thumb, seconds);
        }
        throw new Exception(why);
    }

    /**
     * Its pictures (H.264 first, then VP9; up to 720p) or sounds (AAC first, the plain mix before a quietened or
     * dubbed one; higher bit rate first) that come with a plain link, best first.
     */
    private static java.util.List<String> ranked(@Nullable JsonArray formats, boolean video) {
        java.util.List<long[]> scores = new java.util.ArrayList<>();
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (formats == null) {
            return urls;
        }
        for (JsonElement el : formats) {
            JsonObject f = el.getAsJsonObject();
            String url = str(f, "url"), mime = str(f, "mimeType");
            if (url == null || mime == null || mime.startsWith(video ? "audio/" : "video/")) {
                continue;
            }
            long score;
            if (video) {
                int h = f.has("height") ? f.get("height").getAsInt() : 0;
                if (h > 720 || mime.contains("av01")) {
                    continue;
                }
                score = h * 10L + (mime.contains("avc1") ? 5 : mime.contains("vp9") || mime.contains("vp09") ? 3 : 0);
            } else {
                long rate = f.has("bitrate") ? f.get("bitrate").getAsLong() : 0;
                boolean drc = f.has("isDrc") && f.get("isDrc").getAsBoolean();
                JsonObject track = obj(f, "audioTrack");
                boolean original = track == null || !track.has("audioIsDefault") || track.get("audioIsDefault").getAsBoolean();
                score = (mime.contains("mp4a") ? 4_000_000_000L : 0) + (drc ? 0 : 2_000_000_000L) + (original ? 1_000_000_000L : 0) + rate;
            }
            scores.add(new long[] {score, urls.size()});
            urls.add(url);
        }
        scores.sort((x, y) -> Long.compare(y[0], x[0]));
        java.util.List<String> out = new java.util.ArrayList<>();
        for (long[] sc : scores) {
            out.add(urls.get((int) sc[1]));
        }
        return out;
    }

    @Nullable
    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private YouTube() {}
}
