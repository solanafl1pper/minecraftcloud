package solana.flipper.client.audio;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import solana.flipper.Soundcloud;
import solana.flipper.api.SoundCloudAPI;
import solana.flipper.api.SoundCloudTrack;
import solana.flipper.config.ModConfig;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Streams and plays audio from SoundCloud (progressive MP3 or HLS) or local files.
 *
 * HLS (.m3u8) is handled by fetching the segment list and concatenating
 * the MP3 segments into a single stream — no native HLS library needed.
 */
public class AudioPlayer {

    public enum State { IDLE, LOADING, PLAYING, PAUSED, STOPPED }

    // ---------------------------------------------------------------- singleton

    private static AudioPlayer instance;
    public static AudioPlayer getInstance() {
        if (instance == null) instance = new AudioPlayer();
        return instance;
    }

    // ---------------------------------------------------------------- state

    private final List<SoundCloudTrack> queue = new ArrayList<>();
    private int queueIndex = -1;

    private volatile State   state        = State.IDLE;
    private volatile boolean paused       = false;
    private volatile boolean stopRequested = false;
    private volatile long    positionMs   = 0;

    private SoundCloudTrack currentTrack;

    // JLayer
    private Bitstream    bitstream;
    private Decoder      decoder;
    private AudioDevice  audioDevice;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundCloud-Player");
        t.setDaemon(true);
        return t;
    });
    private Future<?> playFuture;

    // callbacks
    private Consumer<SoundCloudTrack> onTrackStart;
    private Consumer<State>           onStateChange;
    private Runnable                  onTrackEnd;

    // ---------------------------------------------------------------- shuffle / repeat

    public enum RepeatMode { OFF, ALL, ONE }

    private volatile boolean    shuffle    = false;
    private volatile RepeatMode repeatMode = RepeatMode.OFF;
    private final Random        rng        = new Random();

    public boolean     isShuffle()    { return shuffle; }
    public RepeatMode  getRepeat()    { return repeatMode; }
    public void        setShuffle(boolean v) { shuffle = v; }
    public void        setRepeat(RepeatMode m) { repeatMode = m; }
    public void        cycleRepeat()  { repeatMode = RepeatMode.values()[(repeatMode.ordinal() + 1) % 3]; }

    // ---------------------------------------------------------------- queue

    public void setQueue(List<SoundCloudTrack> tracks, int startIndex) {
        queue.clear();
        queue.addAll(tracks);
        queueIndex = startIndex;
        playCurrentIndex();
    }

    public void addToQueue(SoundCloudTrack track) { queue.add(track); }
    public List<SoundCloudTrack> getQueue()       { return queue; }
    public int getQueueIndex()                    { return queueIndex; }

    // ---------------------------------------------------------------- controls

    public void play(SoundCloudTrack track) {
        queue.clear();
        queue.add(track);
        queueIndex = 0;
        playCurrentIndex();
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (shuffle) {
            queueIndex = rng.nextInt(queue.size());
        } else {
            queueIndex = (queueIndex + 1) % queue.size();
        }
        playCurrentIndex();
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (positionMs > 3000) { playCurrentIndex(); return; }
        queueIndex = (queueIndex - 1 + queue.size()) % queue.size();
        playCurrentIndex();
    }

    public void pause() {
        if (state == State.PLAYING) {
            paused = true;
            setState(State.PAUSED);
        } else if (state == State.PAUSED) {
            paused = false;
            setState(State.PLAYING);
            synchronized (this) { notifyAll(); }
        }
    }

    public void stop() {
        stopRequested = true;
        paused = false;
        synchronized (this) { notifyAll(); }
        if (playFuture != null) playFuture.cancel(true);
        closeAudio();
        setState(State.STOPPED);
        positionMs = 0;
    }

    public void seekToSeconds(int seconds) {
        if (currentTrack == null) return;
        int targetMs = seconds * 1000;
        if (currentTrack.getDurationMs() > 0)
            targetMs = Math.min(targetMs, currentTrack.getDurationMs());
        final int seekMs = targetMs;
        stopRequested = true;
        paused = false;
        synchronized (this) { notifyAll(); }
        if (playFuture != null) playFuture.cancel(false);
        closeAudio();
        SoundCloudTrack track = currentTrack;
        playFuture = executor.submit(() -> streamTrack(track, seekMs));
    }

    public void seekRelative(int deltaSeconds) {
        long newPos = Math.max(0, positionMs + (long) deltaSeconds * 1000);
        seekToSeconds((int)(newPos / 1000));
    }

    // ---------------------------------------------------------------- getters

    public State           getState()        { return state; }
    public SoundCloudTrack getCurrentTrack() { return currentTrack; }
    public long            getPositionMs()   { return positionMs; }
    public float           getVolume()       { return ModConfig.get().volume; }

    public void setVolume(float v) {
        ModConfig.get().volume = Math.max(0f, Math.min(1f, v));
        ModConfig.get().save();
    }

    // ---------------------------------------------------------------- callbacks

    public void setOnTrackStart(Consumer<SoundCloudTrack> cb) { onTrackStart = cb; }
    public void setOnStateChange(Consumer<State> cb)          { onStateChange = cb; }
    public void setOnTrackEnd(Runnable cb)                    { onTrackEnd = cb; }

    // ---------------------------------------------------------------- internal

    private void playCurrentIndex() {
        if (queue.isEmpty() || queueIndex < 0 || queueIndex >= queue.size()) return;
        SoundCloudTrack track = queue.get(queueIndex);
        stopRequested = true;
        paused = false;
        synchronized (this) { notifyAll(); }
        if (playFuture != null) playFuture.cancel(false);
        closeAudio();
        playFuture = executor.submit(() -> streamTrack(track, 0));
    }

    private void streamTrack(SoundCloudTrack track, int seekMs) {
        currentTrack = track;
        positionMs   = seekMs;
        setState(State.LOADING);
        stopRequested = false;

        try {
            InputStream raw;
            boolean isHls = false;

            if (LocalAudioPlayer.isLocalPath(track.getStreamUrl())) {
                // Local file
                raw = LocalAudioPlayer.openLocalStream(track.getStreamUrl());
                if (raw == null) { setState(State.IDLE); return; }
            } else {
                // Resolve stream from SoundCloud
                SoundCloudAPI.StreamInfo info = SoundCloudAPI.getStreamInfo(track);
                if (info == null) {
                    Soundcloud.LOGGER.warn("[SoundCloud] No stream for: {}", track.getTitle());
                    setState(State.IDLE);
                    return;
                }
                isHls = info.isHls();
                if (isHls) {
                    raw = openHlsStream(info.url());
                } else {
                    raw = openHttpStream(info.url());
                }
                if (raw == null) { setState(State.IDLE); return; }
            }

            bitstream   = new Bitstream(raw);
            decoder     = new Decoder();
            audioDevice = FactoryRegistry.systemRegistry().createAudioDevice();
            audioDevice.open(decoder);

            if (onTrackStart != null) onTrackStart.accept(track);
            setState(State.PLAYING);

            if (seekMs > 0) skipFrames(seekMs);

            // Decode loop
            while (!stopRequested) {
                while (paused && !stopRequested) {
                    synchronized (AudioPlayer.this) {
                        try { AudioPlayer.this.wait(100); } catch (InterruptedException ignored) {}
                    }
                }
                if (stopRequested) break;

                Header h = bitstream.readFrame();
                if (h == null) break;

                positionMs += (long) h.ms_per_frame();
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                applyVolume(out.getBuffer(), out.getBufferLength());
                audioDevice.write(out.getBuffer(), 0, out.getBufferLength());
                bitstream.closeFrame();
            }

            audioDevice.flush();

        } catch (Exception e) {
            if (!stopRequested)
                Soundcloud.LOGGER.warn("[SoundCloud] Playback error: {}", e.getMessage());
        } finally {
            closeAudio();
            if (!stopRequested) {
                setState(State.IDLE);
                if (onTrackEnd != null) onTrackEnd.run();
                // Auto-advance respecting repeat/shuffle
                if (!queue.isEmpty()) {
                    if (repeatMode == RepeatMode.ONE) {
                        // replay same
                        SoundCloudTrack same = queue.get(queueIndex);
                        playFuture = executor.submit(() -> streamTrack(same, 0));
                    } else {
                        int next;
                        if (shuffle) {
                            next = rng.nextInt(queue.size());
                        } else {
                            next = queueIndex + 1;
                        }
                        if (next < queue.size() || repeatMode == RepeatMode.ALL) {
                            queueIndex = next % queue.size();
                            SoundCloudTrack nextTrack = queue.get(queueIndex);
                            playFuture = executor.submit(() -> streamTrack(nextTrack, 0));
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- HLS

    /**
     * Fetches an HLS .m3u8 playlist, then concatenates all .ts/.mp3 segments
     * into a single PipedInputStream so JLayer can decode them sequentially.
     */
    private InputStream openHlsStream(String m3u8Url) {
        try {
            String playlist = SoundCloudAPI.get(m3u8Url);
            if (playlist == null) return null;

            List<String> segments = new ArrayList<>();
            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf('/') + 1);
            for (String line : playlist.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                segments.add(line.startsWith("http") ? line : baseUrl + line);
            }
            if (segments.isEmpty()) return null;

            // Pipe segments into a stream on a background thread
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream  pis = new PipedInputStream(pos, 131072);

            Thread feeder = new Thread(() -> {
                try {
                    for (String seg : segments) {
                        if (stopRequested) break;
                        InputStream segStream = openHttpStream(seg);
                        if (segStream == null) continue;
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = segStream.read(buf)) != -1) {
                            pos.write(buf, 0, n);
                        }
                        segStream.close();
                    }
                } catch (Exception e) {
                    if (!stopRequested)
                        Soundcloud.LOGGER.warn("[SoundCloud] HLS feeder: {}", e.getMessage());
                } finally {
                    try { pos.close(); } catch (Exception ignored) {}
                }
            }, "SC-HLS-Feeder");
            feeder.setDaemon(true);
            feeder.start();

            return new BufferedInputStream(pis, 65536);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] openHlsStream: {}", e.getMessage());
            return null;
        }
    }

    private InputStream openHttpStream(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            return new BufferedInputStream(conn.getInputStream(), 65536);
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] openHttpStream: {}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private void skipFrames(int targetMs) {
        int elapsed = 0;
        try {
            while (elapsed < targetMs && !stopRequested) {
                Header h = bitstream.readFrame();
                if (h == null) break;
                elapsed += (int) h.ms_per_frame();
                bitstream.closeFrame();
            }
            positionMs = elapsed;
        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] skipFrames: {}", e.getMessage());
        }
    }

    private void applyVolume(short[] buf, int len) {
        float vol = ModConfig.get().volume;
        if (vol >= 1f) return;
        for (int i = 0; i < len; i++) buf[i] = (short)(buf[i] * vol);
    }

    private void closeAudio() {
        try { if (audioDevice != null) { audioDevice.close(); audioDevice = null; } } catch (Exception ignored) {}
        try { if (bitstream  != null) { bitstream.close();   bitstream  = null; } } catch (Exception ignored) {}
        decoder = null;
    }

    private void setState(State s) {
        state = s;
        if (onStateChange != null) onStateChange.accept(s);
    }
}
