package solana.flipper.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import solana.flipper.Soundcloud;
import solana.flipper.api.SoundCloudTrack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists favorite tracks (both resolved from SoundCloud and local file paths).
 * Saved to config/soundcloud_favorites.json
 */
public class FavoritesManager {

    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("soundcloud_favorites.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<FavoriteEntry> favorites = new ArrayList<>();

    // ------------------------------------------------------------------ types

    public enum FavoriteType { SOUNDCLOUD, LOCAL }

    public static class FavoriteEntry {
        public FavoriteType type;
        /** For SOUNDCLOUD: permalink URL. For LOCAL: absolute file path. */
        public String source;
        public String title;
        public String artist;
        /** SoundCloud track id (0 for local) */
        public long trackId;
        public int durationMs;

        public FavoriteEntry() {}

        public FavoriteEntry(FavoriteType type, String source,
                             String title, String artist,
                             long trackId, int durationMs) {
            this.type = type;
            this.source = source;
            this.title = title;
            this.artist = artist;
            this.trackId = trackId;
            this.durationMs = durationMs;
        }

        /** Convert to a SoundCloudTrack-like object for the player */
        public SoundCloudTrack toTrack() {
            return new SoundCloudTrack(trackId, title, artist, "", source, source, durationMs);
        }

        @Override
        public String toString() {
            return artist + " — " + title;
        }
    }

    // ------------------------------------------------------------------ API

    public static List<FavoriteEntry> getFavorites() {
        return favorites;
    }

    public static boolean isFavorite(SoundCloudTrack track) {
        for (FavoriteEntry e : favorites) {
            if (e.trackId == track.getId() && track.getId() != 0) return true;
            if (e.source.equals(track.getPermalinkUrl())) return true;
        }
        return false;
    }

    public static void addFavorite(SoundCloudTrack track) {
        if (isFavorite(track)) return;
        FavoriteEntry entry = new FavoriteEntry(
                FavoriteType.SOUNDCLOUD,
                track.getPermalinkUrl(),
                track.getTitle(),
                track.getArtist(),
                track.getId(),
                track.getDurationMs()
        );
        favorites.add(entry);
        save();
    }

    public static void addLocalFavorite(String filePath) {
        for (FavoriteEntry e : favorites) {
            if (e.source.equals(filePath)) return;
        }
        File f = new File(filePath);
        String name = f.getName();
        // strip extension
        if (name.contains(".")) name = name.substring(0, name.lastIndexOf('.'));
        FavoriteEntry entry = new FavoriteEntry(
                FavoriteType.LOCAL,
                filePath,
                name,
                "Local",
                0,
                0
        );
        favorites.add(entry);
        save();
    }

    public static void removeFavorite(FavoriteEntry entry) {
        favorites.remove(entry);
        save();
    }

    // ------------------------------------------------------------------ I/O

    public static void load() {
        favorites.clear();
        if (!Files.exists(CONFIG_FILE)) return;
        try (Reader r = new InputStreamReader(
                Files.newInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement el : arr) {
                FavoriteEntry entry = GSON.fromJson(el, FavoriteEntry.class);
                if (entry != null) favorites.add(entry);
            }
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] Failed to load favorites: {}", e.getMessage());
        }
    }

    public static void save() {
        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(favorites, w);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] Failed to save favorites: {}", e.getMessage());
        }
    }
}
