package solana.flipper.client.audio;

import solana.flipper.Soundcloud;
import solana.flipper.api.SoundCloudTrack;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Plays local MP3/audio files by wrapping them as SoundCloudTrack with a file:// source.
 * Delegates to AudioPlayer but opens a local stream instead of HTTP.
 */
public class LocalAudioPlayer {

    /**
     * Creates a pseudo-track from a local file path and plays it via AudioPlayer.
     */
    public static void playLocalFile(String filePath) {
        java.io.File f = new java.io.File(filePath);
        if (!f.exists() || !f.isFile()) {
            Soundcloud.LOGGER.warn("[SoundCloud] Local file not found: {}", filePath);
            return;
        }
        String name = f.getName();
        if (name.contains(".")) name = name.substring(0, name.lastIndexOf('.'));

        // Create a track with the file path as streamUrl
        SoundCloudTrack track = new SoundCloudTrack(
                0, name, "Local", "", filePath, filePath, 0
        );

        // Override AudioPlayer to use local stream
        AudioPlayer player = AudioPlayer.getInstance();
        player.play(track);
    }

    /**
     * Opens a local file as InputStream for the player.
     * Called by AudioPlayer when streamUrl starts with "/" or a drive letter.
     */
    public static InputStream openLocalStream(String path) {
        try {
            return new BufferedInputStream(new FileInputStream(path), 65536);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] Cannot open local file: {}", e.getMessage());
            return null;
        }
    }

    public static boolean isLocalPath(String url) {
        if (url == null) return false;
        return url.startsWith("/") || (url.length() > 2 && url.charAt(1) == ':');
    }
}
