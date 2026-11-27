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
        boolean music = src.contains("music.youtube.com");
        String key = (music ? "m:" : "") + list;
        List<Entry> entries = CACHE.get(key);
        if (entries == null) {
            entries = fetch(list, video, music ? "https://music.youtube.com/watch?v=" : "https://www.youtube.com/watch?v=", music);
            if (entries.isEmpty()) {
                if (video == null) {
                    throw new Exception("couldn't read that playlist (private, or empty?)");
                }
                // (its list wouldn't come, but there's a video in the link: that, on its own)
                com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: playlist {} wouldn't come - playing its video alone", list);
                return new List2(List.of(new Entry((music ? "https://music.youtube.com/watch?v=" : "https://www.youtube.com/watch?v=") + video, "")), 0);
            }
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: playlist {} read ({} videos)", list, entries.size());
            CACHE.put(key, entries);
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

    /**
     * Which of its entries the television's count is at: in order from where it started, or - shuffled - in a random
     * order (the same for everyone, from the television's seed) that starts at the video it was at when turned on.
     */
    static int index(List2 l, boolean shuffle, long seed, int first, int item) {
        int n = l.entries().size();
        if (!shuffle) {
            return Math.floorMod(l.first() + item, n);
        }
        return order(n, seed, Math.floorMod(first, n))[Math.floorMod(item, n)];
    }

    /** How far along its order the count is (0 first, size - 1 last). */
    static int along(List2 l, boolean shuffle, int item) {
        int n = l.entries().size();
        return shuffle ? Math.floorMod(item, n) : Math.floorMod(item, n);
    }

    /** The count that plays entry {@code index} in order (shuffle turned off, staying on it). */
    static int itemFor(List2 l, int index) {
        return index - l.first();
    }

    private static int[] lastOrder;
    private static long lastKey;

    private static synchronized int[] order(int n, long seed, int anchor) {
        long key = seed * 31 + n * 7919L + anchor;
        if (lastOrder != null && lastKey == key && lastOrder.length == n) {
            return lastOrder;
        }
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        java.util.Random r = new java.util.Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < n; i++) {
            if (p[i] == anchor) {
                p[i] = p[0];
                p[0] = anchor;
                break;
            }
        }
        lastOrder = p;
        lastKey = key;
        return p;
    }

    private static List<Entry> fetch(String list, @Nullable String video, String prefix, boolean music) throws Exception {
        List<Entry> out = new ArrayList<>();
        // its page, a page at a time - YouTube Music's own for its links (albums, its playlists), YouTube's - then, for
        // mixes and the like (no page), the list beside a video playing from it
        for (boolean remix : music ? new boolean[] {true, false} : new boolean[] {false, true}) {
            String ctx = context(remix);
            JsonObject page = post(remix, "browse", "{\"context\":" + ctx + ",\"browseId\":\"VL" + list + "\"}");
            for (int pages = 0; page != null && pages < 30; pages++) {
                List<String> next = new ArrayList<>();
                walk(page, remix ? "musicResponsiveListItemRenderer" : "playlistVideoRenderer", out, next, prefix);
                if (next.isEmpty()) break;
                page = post(remix, "browse", "{\"context\":" + ctx + ",\"continuation\":\"" + next.get(0) + "\"}");
            }
            if (!out.isEmpty()) break;
        }
        for (boolean remix : music ? new boolean[] {true, false} : new boolean[] {false, true}) {
            if (!out.isEmpty()) break;
            JsonObject watch = post(remix, "next", "{\"context\":" + context(remix) + ",\"playlistId\":\"" + list + "\""
                    + (video != null ? ",\"videoId\":\"" + video + "\"" : "") + "}");
            if (watch != null) {
                walk(watch, "playlistPanelVideoRenderer", out, new ArrayList<>(), prefix);
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
    private static void walk(JsonElement el, String kind, List<Entry> out, List<String> continuations, String prefix) {
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                walk(e, kind, out, continuations, prefix);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has(kind) && o.get(kind).isJsonObject()) {
            JsonObject v = o.getAsJsonObject(kind);
            if (kind.equals("musicResponsiveListItemRenderer")) {
                // (YouTube Music's: its video in playlistItemData, its name the first column's words)
                try {
                    String id = v.getAsJsonObject("playlistItemData").get("videoId").getAsString();
                    String name = text(v.getAsJsonArray("flexColumns").get(0).getAsJsonObject()
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer").get("text"));
                    out.add(new Entry(prefix + id, name));
                } catch (RuntimeException ignored) {
                    // (not a song: a header or the like)
                }
                return;
            }
            if (v.has("videoId")) {
                String id = v.get("videoId").getAsString();
                out.add(new Entry(prefix + id, text(v.get("title"))));
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
            walk(e.getValue(), kind, out, continuations, prefix);
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
    /** Who's asking: YouTube's web page, or YouTube Music's - with the visitor id YouTube gave (it wants one now). */
    private static String context(boolean remix) {
        String visitor = "";
        try {
            visitor = YouTube.visitorData(false);
        } catch (RuntimeException ignored) {
            // (none: asked without)
        }
        return "{\"client\":{\"clientName\":\"" + (remix ? "WEB_REMIX" : "WEB") + "\",\"clientVersion\":\""
                + (remix ? "1.20250310.01.00" : "2.20250312.04.00") + "\",\"hl\":\"en\",\"gl\":\"US\""
                + (visitor == null || visitor.isEmpty() ? "" : ",\"visitorData\":\"" + visitor + "\"") + "}}";
    }

    private static JsonObject post(boolean remix, String what, String body) {
        try {
            String host = remix ? "https://music.youtube.com" : "https://www.youtube.com";
            String visitor = "";
            try {
                visitor = YouTube.visitorData(false);
            } catch (RuntimeException ignored) {
                // (none)
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(host + "/youtubei/v1/" + what + "?prettyPrint=false"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36")
                    .header("Origin", host)
                    .header("Referer", host + "/")
                    .header("Cookie", "SOCS=CAISAiAD");
            if (visitor != null && !visitor.isEmpty()) b.header("X-Goog-Visitor-Id", visitor);
            HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? JsonParser.parseString(res.body()).getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Playlist() {}
}
