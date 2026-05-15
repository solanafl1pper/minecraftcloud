package solana.flipper.api;

import java.util.List;

/**
 * Represents a SoundCloud playlist or album.
 */
public class SoundCloudPlaylist {

    private final long   id;
    private final String title;
    private final String artist;
    private final String artworkUrl;
    private final String permalinkUrl;
    private final List<SoundCloudTrack> tracks;

    public SoundCloudPlaylist(long id, String title, String artist,
                              String artworkUrl, String permalinkUrl,
                              List<SoundCloudTrack> tracks) {
        this.id           = id;
        this.title        = title;
        this.artist       = artist;
        this.artworkUrl   = artworkUrl;
        this.permalinkUrl = permalinkUrl;
        this.tracks       = tracks;
    }

    public long   getId()           { return id; }
    public String getTitle()        { return title; }
    public String getArtist()       { return artist; }
    public String getArtworkUrl()   { return artworkUrl; }
    public String getPermalinkUrl() { return permalinkUrl; }
    public List<SoundCloudTrack> getTracks() { return tracks; }

    @Override
    public String toString() { return artist + " — " + title + " (" + tracks.size() + " tracks)"; }
}
