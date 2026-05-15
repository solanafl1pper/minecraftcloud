package solana.flipper.api;

import com.google.gson.*;
import solana.flipper.Soundcloud;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * SoundCloud API v2 client.
 *
 * client_id is obtained automatically by reading the __sc_hydration JSON blob
 * that SoundCloud embeds in every page response:
 *   {"hydratable":"apiClient","data":{"id":"<CLIENT_ID>"}}
 *
 * No registration, no OAuth, no manual configuration needed.
 */
public class SoundCloudAPI {

    private static final String API   = "https://api-v2.soundcloud.com";
    private static final String HOME  = "https://soundcloud.com";

    // In-memory cache — re-fetched if null (e.g. after expiry)
    private static volatile String cachedClientId = null;

    // Regex: find client_id inside __sc_hydration
    private static final Pattern HYDRATION_ID = Pattern.compile(
            "\"hydratable\"\\s*:\\s*\"apiClient\"[^}]{0,200}?\"id\"\\s*:\\s*\"([A-Za-z0-9_-]{10,})\"",
            Pattern.DOTALL
    );
    // Fallback: client_id inside JS bundles
    private static final Pattern BUNDLE_ID = Pattern.compile(
            "client_id[=:][\"']([A-Za-z0-9_-]{10,})[\"']"
    );
    // sndcdn asset script tags
    private static final Pattern SCRIPT_SRC = Pattern.compile(
            "<script[^>]+src=[\"'](https://a-v2\\.sndcdn\\.com/assets/[^\"']+)[\"']"
    );

    // ---------------------------------------------------------------- client_id

    /** Returns a valid client_id, auto-fetching from soundcloud.com if needed. */
    public static String clientId() throws IOException {
        if (cachedClientId != null) return cachedClientId;
        cachedClientId = fetchClientId();
        if (cachedClientId == null)
            throw new IOException("Could not obtain SoundCloud client_id from soundcloud.com");
        Soundcloud.LOGGER.info("[SoundCloud] client_id ready: {}", cachedClientId);
        return cachedClientId;
    }

    /** Force re-fetch (call if you get 401/403). */
    public static void invalidateClientId() {
        cachedClientId = null;
    }

    private static String fetchClientId() {
        try {
            Soundcloud.LOGGER.info("[SoundCloud] Fetching client_id...");
            String html = get(HOME);
            if (html == null) return null;

            // Primary: __sc_hydration blob
            Matcher m = HYDRATION_ID.matcher(html);
            if (m.find()) return m.group(1);

            // Fallback: search JS bundles listed in the HTML
            Matcher sm = SCRIPT_SRC.matcher(html);
            while (sm.find()) {
                try {
                    String js = get(sm.group(1));
                    if (js == null) continue;
                    Matcher bm = BUNDLE_ID.matcher(js);
                    if (bm.find()) return bm.group(1);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] fetchClientId failed: {}", e.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------- search

    /** Search tracks. Returns up to {@code limit} results. */
    public static List<SoundCloudTrack> searchTracks(String query, int limit) throws IOException {
        String url = API + "/search/tracks?q=" + enc(query)
                + "&limit=" + limit + "&client_id=" + clientId();
        JsonObject root = getJson(url);
        return parseTracks(root);
    }

    // ---------------------------------------------------------------- resolve

    /**
     * Resolve any SoundCloud URL — track or playlist/album.
     * Returns a {@link ResolveResult} that is either a single track or a playlist.
     */
    public static ResolveResult resolve(String scUrl) throws IOException {
        String url = API + "/resolve?url=" + enc(scUrl) + "&client_id=" + clientId();
        JsonObject root = getJson(url);
        if (root == null) return null;

        String kind = root.has("kind") ? root.get("kind").getAsString() : "";
        return switch (kind) {
            case "track"    -> new ResolveResult(parseTrack(root), null);
            case "playlist" -> new ResolveResult(null, parsePlaylist(root));
            default         -> null;
        };
    }

    // ---------------------------------------------------------------- stream URL

    /**
     * Returns the best streamable URL for a track.
     * Prefers progressive MP3; falls back to HLS.
     * Caller must handle HLS (.m3u8) separately if needed.
     */
    public static StreamInfo getStreamInfo(SoundCloudTrack track) throws IOException {
        String cid = clientId();
        // Fetch full track data (includes media transcodings)
        JsonObject root = getJson(API + "/tracks/" + track.getId() + "?client_id=" + cid);
        if (root == null) return null;

        // Also check track_authorization for auth header
        String auth = root.has("track_authorization")
                ? root.get("track_authorization").getAsString() : null;

        if (!root.has("media")) return null;
        JsonArray transcodings = root.getAsJsonObject("media")
                .getAsJsonArray("transcodings");
        if (transcodings == null || transcodings.isEmpty()) return null;

        // Pass 1: progressive MP3, not snipped
        for (JsonElement el : transcodings) {
            JsonObject tc = el.getAsJsonObject();
            if (isSnipped(tc)) continue;
            if (isProgressiveMp3(tc)) {
                String resolved = resolveTranscoding(tc, cid, auth);
                if (resolved != null) return new StreamInfo(resolved, false);
            }
        }
        // Pass 2: any progressive, not snipped
        for (JsonElement el : transcodings) {
            JsonObject tc = el.getAsJsonObject();
            if (isSnipped(tc)) continue;
            if (isProgressive(tc)) {
                String resolved = resolveTranscoding(tc, cid, auth);
                if (resolved != null) return new StreamInfo(resolved, false);
            }
        }
        // Pass 3: HLS MP3, not snipped
        for (JsonElement el : transcodings) {
            JsonObject tc = el.getAsJsonObject();
            if (isSnipped(tc)) continue;
            if (isHlsMp3(tc)) {
                String resolved = resolveTranscoding(tc, cid, auth);
                if (resolved != null) return new StreamInfo(resolved, true);
            }
        }
        // Pass 4: any HLS, not snipped
        for (JsonElement el : transcodings) {
            JsonObject tc = el.getAsJsonObject();
            if (isSnipped(tc)) continue;
            if (isHls(tc)) {
                String resolved = resolveTranscoding(tc, cid, auth);
                if (resolved != null) return new StreamInfo(resolved, true);
            }
        }
        // Pass 5: snipped fallback (preview only)
        for (JsonElement el : transcodings) {
            JsonObject tc = el.getAsJsonObject();
            if (isProgressiveMp3(tc)) {
                String resolved = resolveTranscoding(tc, cid, auth);
                if (resolved != null) return new StreamInfo(resolved, false);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- data types

    public record StreamInfo(String url, boolean isHls) {}

    public record ResolveResult(SoundCloudTrack track, SoundCloudPlaylist playlist) {
        public boolean isTrack()    { return track != null; }
        public boolean isPlaylist() { return playlist != null; }
    }

    // ---------------------------------------------------------------- parsing

    private static List<SoundCloudTrack> parseTracks(JsonObject root) {
        List<SoundCloudTrack> list = new ArrayList<>();
        if (root == null || !root.has("collection")) return list;
        for (JsonElement el : root.getAsJsonArray("collection")) {
            SoundCloudTrack t = parseTrack(el.getAsJsonObject());
            if (t != null) list.add(t);
        }
        return list;
    }

    static SoundCloudTrack parseTrack(JsonObject obj) {
        if (obj == null) return null;
        try {
            long id = obj.get("id").getAsLong();
            String title  = str(obj, "title", "Unknown");
            String artist = "Unknown";
            if (obj.has("user") && !obj.get("user").isJsonNull()) {
                artist = str(obj.getAsJsonObject("user"), "username", "Unknown");
            }
            String artwork = str(obj, "artwork_url", "")
                    .replace("-large.", "-t200x200.");
            String permalink = str(obj, "permalink_url", "");
            int duration = obj.has("duration") ? obj.get("duration").getAsInt() : 0;
            boolean snipped = "SNIP".equals(str(obj, "policy", ""));
            return new SoundCloudTrack(id, title, artist, artwork, "", permalink, duration, snipped);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] parseTrack: {}", e.getMessage());
            return null;
        }
    }

    private static SoundCloudPlaylist parsePlaylist(JsonObject obj) {
        if (obj == null) return null;
        try {
            long id = obj.get("id").getAsLong();
            String title  = str(obj, "title", "Unknown Playlist");
            String artist = "Unknown";
            if (obj.has("user") && !obj.get("user").isJsonNull()) {
                artist = str(obj.getAsJsonObject("user"), "username", "Unknown");
            }
            String artwork = str(obj, "artwork_url", "")
                    .replace("-large.", "-t200x200.");
            String permalink = str(obj, "permalink_url", "");

            List<SoundCloudTrack> tracks = new ArrayList<>();
            if (obj.has("tracks") && !obj.get("tracks").isJsonNull()) {
                for (JsonElement el : obj.getAsJsonArray("tracks")) {
                    SoundCloudTrack t = parseTrack(el.getAsJsonObject());
                    if (t != null) tracks.add(t);
                }
            }
            return new SoundCloudPlaylist(id, title, artist, artwork, permalink, tracks);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] parsePlaylist: {}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- transcoding helpers

    private static boolean isProgressiveMp3(JsonObject tc) {
        return isProgressive(tc) && mime(tc).contains("mpeg");
    }
    private static boolean isProgressive(JsonObject tc) {
        return "progressive".equals(protocol(tc));
    }
    private static boolean isHlsMp3(JsonObject tc) {
        return isHls(tc) && mime(tc).contains("mpeg");
    }
    private static boolean isHls(JsonObject tc) {
        return "hls".equals(protocol(tc));
    }
    private static boolean isSnipped(JsonObject tc) {
        return tc.has("snipped") && tc.get("snipped").getAsBoolean();
    }
    private static String protocol(JsonObject tc) {
        if (!tc.has("format")) return "";
        return str(tc.getAsJsonObject("format"), "protocol", "");
    }
    private static String mime(JsonObject tc) {
        if (!tc.has("format")) return "";
        return str(tc.getAsJsonObject("format"), "mime_type", "");
    }

    private static String resolveTranscoding(JsonObject tc, String cid, String auth) {
        try {
            String tcUrl = tc.get("url").getAsString() + "?client_id=" + cid;
            if (auth != null) tcUrl += "&track_authorization=" + auth;
            JsonObject resp = getJson(tcUrl);
            if (resp != null && resp.has("url")) return resp.get("url").getAsString();
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] resolveTranscoding: {}", e.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------- HTTP

    static JsonObject getJson(String url) throws IOException {
        String text = get(url);
        if (text == null) return null;
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] JSON parse error: {}", e.getMessage());
            return null;
        }
    }

    public static String get(String urlStr) throws IOException {
        return get(urlStr, 0);
    }

    private static String get(String urlStr, int depth) throws IOException {
        if (depth > 5) throw new IOException("Too many redirects");
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        c.setRequestProperty("Accept", "*/*");
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(false);

        int code = c.getResponseCode();
        if (code == 401 || code == 403) {
            // client_id expired — invalidate so next call re-fetches
            cachedClientId = null;
            Soundcloud.LOGGER.warn("[SoundCloud] {} {} — client_id invalidated", code, urlStr);
            c.disconnect();
            return null;
        }
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            return loc != null ? get(loc, depth + 1) : null;
        }
        if (code != 200) {
            Soundcloud.LOGGER.warn("[SoundCloud] HTTP {} for {}", code, urlStr);
            c.disconnect();
            return null;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8), 65536)) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        } finally {
            c.disconnect();
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- util

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key, String def) {
        if (!o.has(key) || o.get(key).isJsonNull()) return def;
        return o.get(key).getAsString();
    }
}
