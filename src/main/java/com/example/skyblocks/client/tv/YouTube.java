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

    // (as yt-dlp does: visionOS first - since August 2026 YouTube refuses Android VR's downloads; it needs no script run)
    private static final Client[] CLIENTS = {
            new Client("VISIONOS", 101, "1.02",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                    "\"deviceMake\":\"Apple\",\"deviceModel\":\"RealityDevice17,1\",\"osName\":\"visionOS\",\"osVersion\":\"26.5.23O471\","
                            + "\"userAgent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15\""),
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

    /** A link with the user agent to read it with (YouTube's links only work read as the app that asked for them). */
    static final String UA_TAG = "#shadered-ua=";

    /** ...and the video's id (to ask for a fresh link to the same copy when YouTube stops taking the old one). */
    static final String ID_TAG = "&shadered-yt=";

    static String tag(String url, String userAgent, String id) {
        return url + UA_TAG + java.net.URLEncoder.encode(userAgent, java.nio.charset.StandardCharsets.UTF_8) + ID_TAG + id;
    }

    private static final Pattern ITAG = Pattern.compile("[?&]itag=(\\d+)");

    /**
     * A fresh link to the same copy (the same app, the same itag) of a video - YouTube refuses an old link part way
     * through now and then; null if it gave none.
     */
    /** A fresh link to the same copy, from the same app if it'll give one, else from any of them: its link and user agent. */
    @Nullable
    static String[] freshAny(String id, String oldUrl, String userAgent) {
        String same = fresh(id, oldUrl, userAgent);
        if (same != null) {
            return new String[] {same, userAgent};
        }
        for (Client c : CLIENTS) {
            if (!c.userAgent.equals(userAgent)) {
                String u = fresh(id, oldUrl, c.userAgent);
                if (u != null) {
                    return new String[] {u, c.userAgent};
                }
            }
        }
        return null;
    }

    @Nullable
    static String fresh(String id, String oldUrl, String userAgent) {
        Matcher m = ITAG.matcher(oldUrl);
        if (!m.find()) {
            return null;
        }
        String itag = m.group(1);
        for (Client c : CLIENTS) {
            if (!c.userAgent.equals(userAgent)) {
                continue;
            }
            try {
                JsonObject root = player(c, id);
                JsonObject streaming = root == null ? null : obj(root, "streamingData");
                if (streaming == null) {
                    return null;
                }
                for (String list : new String[] {"adaptiveFormats", "formats"}) {
                    JsonArray all = streaming.has(list) ? streaming.getAsJsonArray(list) : null;
                    if (all == null) continue;
                    for (JsonElement el : all) {
                        JsonObject f = el.getAsJsonObject();
                        if (f.has("itag") && itag.equals(f.get("itag").getAsString()) && str(f, "url") != null) {
                            return str(f, "url");
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static final Pattern RES = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    /**
     * From an HLS master list: its picture's list (the best up to 720p, H.264 first) and its sound's (the AAC one, else
     * the picture's own, which carries its sound too); null if it can't be read.
     */
    @Nullable
    private static String[] hlsStreams(String master, String userAgent) {
        try {
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(master)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", userAgent).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return null;
            }
            String[] lines = res.body().split("\n");
            String audio = null, video = null;
            long best = -1;
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.startsWith("#EXT-X-MEDIA:") && l.contains("TYPE=AUDIO")) {
                    Matcher u = URI_ATTR.matcher(l);
                    if (u.find() && (audio == null || l.contains("GROUP-ID=\"234\""))) {
                        audio = u.group(1);
                    }
                } else if (l.startsWith("#EXT-X-STREAM-INF:") && i + 1 < lines.length) {
                    Matcher r = RES.matcher(l);
                    int h = r.find() ? Integer.parseInt(r.group(2)) : 0;
                    if (h > 720) {
                        continue;
                    }
                    long score = h * 10L + (l.contains("avc1") ? 5 : 0);
                    String uri = lines[i + 1].trim();
                    if (score > best && !uri.startsWith("#")) {
                        best = score;
                        video = uri;
                    }
                }
            }
            if (video == null && audio == null) {
                return null;
            }
            return new String[] {video != null ? video : audio, audio != null ? audio : video};
        } catch (Exception e) {
            return null;
        }
    }

    /** YouTube's player answer for a video, asked as this app; null if it didn't answer. */
    @Nullable
    private static JsonObject player(Client c, String id) throws Exception {
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
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? JsonParser.parseString(res.body()).getAsJsonObject() : null;
    }

    static Streams resolve(String id) throws Exception {
        String why = "YouTube didn't answer";
        // (every app's copies that really read, the first app's first: YouTube refuses one app's now and then)
        java.util.List<String> allVideo = new java.util.ArrayList<>(), allAudio = new java.util.ArrayList<>();
        String fTitle = null, fAuthor = "", fThumb = "", fAgent = "";
        long fSeconds = 0;
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
            // (only the copies YouTube really lets this computer read: each tried with a small download first)
            video = working(video, c.userAgent, id);
            audio = working(audio, c.userAgent, id);
            if (hls != null && c.name.equals("VISIONOS")) {
                // (its HLS pieces first: on some networks YouTube cuts this app's whole-file links off after 1 MiB -
                // about a minute of a song - but not its HLS pieces, as ytm-rs found)
                String[] h = hlsStreams(hls, c.userAgent);
                if (h != null) {
                    video.add(0, h[0]);
                    audio.add(0, h[1]);
                }
            }
            if (video.isEmpty() || audio.isEmpty()) {
                why = "YouTube refused every copy of it";
                continue;
            }
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: YouTube's {} copies read", c.name);
            allVideo.addAll(video);
            allAudio.addAll(audio);
            if (fTitle == null) {
                fTitle = title;
                fAuthor = author;
                fThumb = thumb;
                fSeconds = seconds;
                fAgent = c.userAgent;
            }
            if (allVideo.size() > 6) {
                break; // (plenty to try)
            }
        }
        if (fTitle == null) {
            throw new Exception(why);
        }
        return new Streams(allVideo, allAudio, fTitle, false, fAgent, fAuthor, fThumb, fSeconds);
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

    /**
     * From the first copy that gives a small download whole (up to a few tried), the rest after it - each with the
     * user agent to read it with; empty if none did.
     */
    private static java.util.List<String> working(java.util.List<String> urls, String userAgent, String id) {
        for (int i = 0; i < urls.size() && i < 4; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(urls.get(i))).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", userAgent).header("Range", "bytes=0-262143").GET().build();
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if ((res.statusCode() == 200 || res.statusCode() == 206) && res.body().length > 1000) {
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (String u : urls.subList(i, urls.size())) {
                        out.add(u.contains("googlevideo.com") ? tag(u, userAgent, id) : u);
                    }
                    return out;
                }
                com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: a YouTube copy answered {}", res.statusCode());
            } catch (Exception ignored) {
                // (the next)
            }
        }
        return new java.util.ArrayList<>();
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
