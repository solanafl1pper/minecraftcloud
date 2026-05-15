package solana.flipper.api;

/**
 * Represents a SoundCloud track.
 * {@code snipped} is true when only a 30-second preview is available
 * (policy = SNIP, typically for Go+ tracks).
 */
public class SoundCloudTrack {

    private final long   id;
    private final String title;
    private final String artist;
    private final String artworkUrl;
    private final String streamUrl;   // resolved lazily by AudioPlayer
    private final String permalinkUrl;
    private final int    durationMs;
    private final boolean snipped;

    public SoundCloudTrack(long id, String title, String artist,
                           String artworkUrl, String streamUrl,
                           String permalinkUrl, int durationMs,
                           boolean snipped) {
        this.id           = id;
        this.title        = title;
        this.artist       = artist;
        this.artworkUrl   = artworkUrl;
        this.streamUrl    = streamUrl;
        this.permalinkUrl = permalinkUrl;
        this.durationMs   = durationMs;
        this.snipped      = snipped;
    }

    /** Convenience constructor without snipped flag (defaults to false). */
    public SoundCloudTrack(long id, String title, String artist,
                           String artworkUrl, String streamUrl,
                           String permalinkUrl, int durationMs) {
        this(id, title, artist, artworkUrl, streamUrl, permalinkUrl, durationMs, false);
    }

    public long    getId()           { return id; }
    public String  getTitle()        { return title; }
    public String  getArtist()       { return artist; }
    public String  getArtworkUrl()   { return artworkUrl; }
    public String  getStreamUrl()    { return streamUrl; }
    public String  getPermalinkUrl() { return permalinkUrl; }
    public int     getDurationMs()   { return durationMs; }
    public boolean isSnipped()       { return snipped; }

    public String getFormattedDuration() {
        int s = durationMs / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public String toString() { return artist + " — " + title; }
}
