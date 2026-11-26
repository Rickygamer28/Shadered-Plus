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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Playlists: a YouTube playlist (or mix) link - its videos, read from YouTube's own pages (kept a while) - or several
 * files dropped at once. Each entry is what a single video's source would be.
 */
final class Playlist {
    record Entry(String source, String title) {}

    /** Its entries, and which comes first (a playlist link to one of its videos starts there). */
    record List2(List<Entry> entries, int first) {}

    private static final Pattern LIST = Pattern.compile("[?&]list=([A-Za-z0-9_-]+)");
    private static final Pattern VIDEO = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})");
    private static final Map<String, List<Entry>> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String WEB = "{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20250312.04.00\",\"hl\":\"en\",\"gl\":\"US\"}}";

    /** Whether a source is a playlist. */
    static boolean is(String src) {
        return src.startsWith("files:") || src.contains("youtu") && LIST.matcher(src).find();
    }

    static List2 resolve(String src) throws Exception {
        if (src.startsWith("files:")) {
            List<Entry> out = new ArrayList<>();
            for (String line : src.substring(6).split("\n")) {
                if (!line.isBlank()) {
                    String name = line.replace('\\', '/');
                    out.add(new Entry("file:" + line.trim(), name.substring(name.lastIndexOf('/') + 1)));
                }
            }
            return new List2(out, 0);
        }
        Matcher lm = LIST.matcher(src);
        lm.find();
        String list = lm.group(1);
        Matcher vm = VIDEO.matcher(src);
        String video = vm.find() ? vm.group(1) : null;
        List<Entry> entries = CACHE.get(list);
        if (entries == null) {
            entries = fetch(list, video);
            if (entries.isEmpty()) {
                throw new Exception("couldn't read that playlist (private, or empty?)");
            }
            CACHE.put(list, entries);
        }
        int first = 0;
        if (video != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).source().endsWith(video)) {
                    first = i;
                    break;
                }
            }
        }
        return new List2(entries, first);
    }

    private static List<Entry> fetch(String list, @Nullable String video) throws Exception {
        List<Entry> out = new ArrayList<>();
        // the playlist's own page (all of it, a page at a time)
        JsonObject page = post("browse", "{\"context\":" + WEB + ",\"browseId\":\"VL" + list + "\"}");
        for (int pages = 0; page != null && pages < 30; pages++) {
            List<String> next = new ArrayList<>();
            walk(page, "playlistVideoRenderer", out, next);
            if (next.isEmpty()) {
                break;
            }
            page = post("browse", "{\"context\":" + WEB + ",\"continuation\":\"" + next.get(0) + "\"}");
        }
        if (out.isEmpty()) {
            // (mixes and the like have no page: the list beside a video playing from it)
            JsonObject watch = post("next", "{\"context\":" + WEB + ",\"playlistId\":\"" + list + "\"" + (video != null ? ",\"videoId\":\"" + video + "\"" : "") + "}");
            if (watch != null) {
                walk(watch, "playlistPanelVideoRenderer", out, new ArrayList<>());
            }
        }
        // (once each, in order)
        List<Entry> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Entry e : out) {
            if (seen.add(e.source())) {
                unique.add(e);
            }
        }
        return unique;
    }

    /** Every video of this kind anywhere in it (in order), and where the rest of the list is to be asked for. */
    private static void walk(JsonElement el, String kind, List<Entry> out, List<String> continuations) {
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                walk(e, kind, out, continuations);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has(kind) && o.get(kind).isJsonObject()) {
            JsonObject v = o.getAsJsonObject(kind);
            if (v.has("videoId")) {
                String id = v.get("videoId").getAsString();
                out.add(new Entry("https://www.youtube.com/watch?v=" + id, text(v.get("title"))));
            }
            return;
        }
        if (o.has("continuationCommand") && o.get("continuationCommand").isJsonObject()) {
            JsonObject c = o.getAsJsonObject("continuationCommand");
            if (c.has("token")) {
                continuations.add(c.get("token").getAsString());
            }
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            walk(e.getValue(), kind, out, continuations);
        }
    }

    private static String text(@Nullable JsonElement t) {
        if (t == null || !t.isJsonObject()) {
            return "";
        }
        JsonObject o = t.getAsJsonObject();
        if (o.has("simpleText")) {
            return o.get("simpleText").getAsString();
        }
        if (o.has("runs")) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement r : o.getAsJsonArray("runs")) {
                if (r.isJsonObject() && r.getAsJsonObject().has("text")) {
                    sb.append(r.getAsJsonObject().get("text").getAsString());
                }
            }
            return sb.toString();
        }
        return "";
    }

    @Nullable
    private static JsonObject post(String what, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.youtube.com/youtubei/v1/" + what + "?prettyPrint=false"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36")
                    .header("Origin", "https://www.youtube.com")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? JsonParser.parseString(res.body()).getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Playlist() {}
}
