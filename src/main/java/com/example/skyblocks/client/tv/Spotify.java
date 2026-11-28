package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify links (a song, an album, a playlist): Spotify's own sound is locked to its app, so what's read is only which
 * songs they are (from the page Spotify gives anyone to embed them) - and each is played from YouTube Music, found by
 * its name and artist when its turn comes, with its cover and its words like any YouTube Music song.
 */
final class Spotify {
    private static final Pattern LINK = Pattern.compile("(?:open\\.spotify\\.com/(?:intl-[a-z]+/)?(?:embed/)?|spotify:)(track|album|playlist)[/:]([A-Za-z0-9]{22})");
    /** What a song from Spotify is played as: found on YouTube Music when its turn comes ("artist" \1 "title"). */
    static final String SEARCH = "ytmsearch:";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    static boolean is(String src) {
        return LINK.matcher(src).find();
    }

    /** Its songs, in order - each a search for it on YouTube Music. */
    static List<Playlist.Entry> songs(String src) throws Exception {
        Matcher m = LINK.matcher(src);
        if (!m.find()) {
            throw new Exception("that isn't a Spotify link");
        }
        String type = m.group(1), id = m.group(2);
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create("https://open.spotify.com/embed/" + type + "/" + id))
                .timeout(Duration.ofSeconds(15)).header("User-Agent", UA).header("Accept-Language", "en").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new Exception("Spotify didn't give that " + type + " (" + res.statusCode() + ")");
        }
        String html = res.body();
        int at = html.indexOf("id=\"__NEXT_DATA__\"");
        if (at < 0) {
            throw new Exception("couldn't read that Spotify " + type);
        }
        int start = html.indexOf('>', at) + 1, end = html.indexOf("</script>", start);
        JsonObject data = JsonParser.parseString(html.substring(start, end)).getAsJsonObject();
        JsonObject entity = find(data, "entity");
        if (entity == null) {
            throw new Exception("couldn't read that Spotify " + type);
        }
        List<Playlist.Entry> out = new ArrayList<>();
        if (entity.has("trackList") && entity.get("trackList").isJsonArray()) {
            for (JsonElement e : entity.getAsJsonArray("trackList")) {
                if (!e.isJsonObject()) continue;
                JsonObject t = e.getAsJsonObject();
                String title = str(t, "title"), artist = str(t, "subtitle");
                if (!title.isEmpty()) out.add(entry(artist, title));
            }
        } else {
            String title = str(entity, "title").isEmpty() ? str(entity, "name") : str(entity, "title");
            String artist = str(entity, "subtitle");
            if (artist.isEmpty() && entity.has("artists") && entity.get("artists").isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement a : entity.getAsJsonArray("artists")) {
                    if (a.isJsonObject()) names.add(str(a.getAsJsonObject(), "name"));
                }
                artist = String.join(", ", names);
            }
            if (!title.isEmpty()) out.add(entry(artist, title));
        }
        if (out.isEmpty()) {
            throw new Exception("that Spotify " + type + " has no songs to play");
        }
        SkyBlocksMod.LOGGER.info("Shadered+ television: Spotify {} {} read ({} songs)", type, id, out.size());
        return out;
    }

    private static Playlist.Entry entry(String artist, String title) {
        // (Spotify writes several artists with a non-breaking comma space)
        artist = artist.replace(' ', ' ').trim();
        return new Playlist.Entry(SEARCH + artist + "\u0001" + title, artist.isEmpty() ? title : artist + " - " + title);
    }

    static String artist(String search) {
        String s = search.substring(SEARCH.length());
        int i = s.indexOf('\u0001');
        return i < 0 ? "" : s.substring(0, i);
    }

    static String title(String search) {
        String s = search.substring(SEARCH.length());
        return s.substring(s.indexOf('\u0001') + 1);
    }

    /** The YouTube Music song for one from Spotify: its link. */
    static String find(String search) throws Exception {
        String artist = artist(search), title = title(search);
        String q = (title + " " + artist.replace(",", " ")).trim();
        String id = YouTube.searchSong(q);
        if (id == null) {
            throw new Exception("couldn't find \"" + title + "\" on YouTube Music");
        }
        return "https://music.youtube.com/watch?v=" + id;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    /** The first object under this key anywhere in it. */
    private static JsonObject find(JsonElement el, String key) {
        if (el.isJsonArray()) {
            for (JsonElement e : (JsonArray) el) {
                JsonObject f = find(e, key);
                if (f != null) return f;
            }
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has(key) && o.get(key).isJsonObject()) return o.getAsJsonObject(key);
            for (var e : o.entrySet()) {
                JsonObject f = find(e.getValue(), key);
                if (f != null) return f;
            }
        }
        return null;
    }

    private Spotify() {}
}
