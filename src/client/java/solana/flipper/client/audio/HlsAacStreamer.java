package solana.flipper.client.audio;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import solana.flipper.Soundcloud;
import solana.flipper.api.SoundCloudAPI;
import solana.flipper.config.ModConfig;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pure-Java HLS AAC Streamer and Decoder using JAAD.
 *
 * Resolves SoundCloud HLS fragmented MP4 (.m4s) audio streams,
 * demuxes samples, decodes AAC into clean 16-bit PCM (little-endian),
 * and plays via SourceDataLine with zero static and instant controls.
 */
public class HlsAacStreamer {

    public record Segment(String url, double durationSec) {}

    private static volatile SourceDataLine activeLine = null;

    public static void stopActiveLine() {
        SourceDataLine l = activeLine;
        if (l != null) {
            try {
                l.stop();
                l.flush();
                l.close();
            } catch (Exception ignored) {}
            activeLine = null;
        }
    }

    public static boolean playHlsAac(String m3u8Url, int seekMs,
                                     AudioPlayer player,
                                     BooleanSupplier isStopRequested,
                                     BooleanSupplier isPaused) {
        SourceDataLine line = null;
        try {
            // Stop any leftover playback
            stopActiveLine();

            // 1. Fetch playlist m3u8
            String playlist = SoundCloudAPI.get(m3u8Url);
            if (playlist == null || playlist.isEmpty()) {
                Soundcloud.LOGGER.warn("[SoundCloud] Empty HLS playlist for: {}", m3u8Url);
                return false;
            }

            String initUrl = null;
            List<Segment> segments = new ArrayList<>();
            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf('/') + 1);

            double currentDuration = 10.0;
            for (String rawLine : playlist.split("\n")) {
                String l = rawLine.trim();
                if (l.startsWith("#EXT-X-MAP:URI=")) {
                    int start = l.indexOf('"') + 1;
                    int end = l.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        String uri = l.substring(start, end);
                        initUrl = uri.startsWith("http") ? uri : baseUrl + uri;
                    }
                } else if (l.startsWith("#EXTINF:")) {
                    try {
                        String durStr = l.substring(8);
                        if (durStr.contains(",")) durStr = durStr.substring(0, durStr.indexOf(','));
                        currentDuration = Double.parseDouble(durStr.trim());
                    } catch (Exception ignored) {}
                } else if (!l.isEmpty() && !l.startsWith("#")) {
                    String segUrl = l.startsWith("http") ? l : baseUrl + l;
                    segments.add(new Segment(segUrl, currentDuration));
                }
            }

            if (initUrl == null || segments.isEmpty()) {
                Soundcloud.LOGGER.warn("[SoundCloud] No valid fMP4 init/segments found in playlist");
                return false;
            }

            // 2. Download init.mp4 to get AAC DecoderSpecificInfo
            byte[] initBytes = downloadBytes(initUrl, isStopRequested);
            if (initBytes == null || isStopRequested.getAsBoolean()) return false;

            MP4Container cont = new MP4Container(new ByteArrayInputStream(initBytes));
            AudioTrack at = (AudioTrack) cont.getMovie().getTracks().get(0);
            byte[] dsi = at.getDecoderSpecificInfo();
            int sampleRate = at.getSampleRate();
            int channels = at.getChannelCount();

            Decoder decoder = new Decoder(dsi);
            SampleBuffer sampleBuffer = new SampleBuffer();
            // CRITICAL: Force Little-Endian so 16-bit PCM matches AudioFormat!
            sampleBuffer.setBigEndian(false);

            // 3. Open JavaSound output line (44100Hz 16-bit stereo signed little-endian)
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            // Low latency ~200ms buffer (sampleRate * channels * 2 * 0.2)
            int bufSize = Math.max(16384, (int)(sampleRate * channels * 2 * 0.25));
            line.open(format, bufSize);
            line.start();
            activeLine = line;

            // 4. Calculate start segment for seek
            int startSeg = 0;
            double accumulatedSec = 0.0;
            double targetSec = seekMs / 1000.0;

            for (int i = 0; i < segments.size(); i++) {
                double segDur = segments.get(i).durationSec();
                if (accumulatedSec + segDur > targetSec) {
                    startSeg = i;
                    break;
                }
                accumulatedSec += segDur;
            }

            player.setPositionMs((long) (accumulatedSec * 1000));

            // 5. Stream and decode segments
            for (int sIdx = startSeg; sIdx < segments.size(); sIdx++) {
                if (isStopRequested.getAsBoolean()) break;

                // Handle pause immediately
                if (isPaused.getAsBoolean()) {
                    line.stop();
                    while (isPaused.getAsBoolean() && !isStopRequested.getAsBoolean()) {
                        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    }
                    if (isStopRequested.getAsBoolean()) break;
                    line.start();
                }

                Segment seg = segments.get(sIdx);
                byte[] segBytes = downloadBytes(seg.url(), isStopRequested);
                if (segBytes == null || segBytes.length < 32) continue;

                // Parse 'trun' to get sample sizes
                ByteBuffer buf = ByteBuffer.wrap(segBytes).order(ByteOrder.BIG_ENDIAN);
                int trunPos = -1;
                for (int i = 0; i < segBytes.length - 8; i++) {
                    if (segBytes[i] == 't' && segBytes[i+1] == 'r' && segBytes[i+2] == 'u' && segBytes[i+3] == 'n') {
                        trunPos = i - 4;
                        break;
                    }
                }
                if (trunPos == -1) continue;

                buf.position(trunPos);
                buf.getInt(); // size
                buf.getInt(); // 'trun'
                int flags = buf.getInt() & 0xFFFFFF;
                int sampleCount = buf.getInt();

                boolean dataOffsetPresent = (flags & 0x01) != 0;
                boolean firstSampleFlags = (flags & 0x04) != 0;
                boolean sampleDurationPresent = (flags & 0x100) != 0;
                boolean sampleSizePresent = (flags & 0x200) != 0;

                if (dataOffsetPresent) buf.getInt();
                if (firstSampleFlags) buf.getInt();

                int[] sizes = new int[sampleCount];
                for (int i = 0; i < sampleCount; i++) {
                    if (sampleDurationPresent) buf.getInt();
                    if (sampleSizePresent) sizes[i] = buf.getInt();
                }

                // Find 'mdat' payload
                int mdatOffset = -1;
                for (int i = 0; i < segBytes.length - 8; i++) {
                    if (segBytes[i] == 'm' && segBytes[i+1] == 'd' && segBytes[i+2] == 'a' && segBytes[i+3] == 't') {
                        mdatOffset = i + 4;
                        break;
                    }
                }
                if (mdatOffset == -1) continue;

                int offset = mdatOffset;
                double msPerSample = (seg.durationSec() * 1000.0) / Math.max(1, sampleCount);

                for (int i = 0; i < sampleCount; i++) {
                    if (isStopRequested.getAsBoolean()) break;

                    if (isPaused.getAsBoolean()) {
                        line.stop();
                        while (isPaused.getAsBoolean() && !isStopRequested.getAsBoolean()) {
                            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                        }
                        if (isStopRequested.getAsBoolean()) break;
                        line.start();
                    }

                    int sz = sizes[i];
                    if (offset + sz > segBytes.length) break;

                    byte[] frame = new byte[sz];
                    System.arraycopy(segBytes, offset, frame, 0, sz);
                    offset += sz;

                    try {
                        decoder.decodeFrame(frame, sampleBuffer);
                        byte[] pcm = sampleBuffer.getData();

                        // Apply volume cleanly
                        float vol = ModConfig.get().volume;
                        if (vol <= 0.001f) {
                            Arrays.fill(pcm, (byte) 0);
                        } else if (vol < 0.999f) {
                            for (int p = 0; p < pcm.length - 1; p += 2) {
                                int low = pcm[p] & 0xFF;
                                int high = pcm[p + 1];
                                short val = (short) ((high << 8) | low);
                                val = (short) (val * vol);
                                pcm[p] = (byte) (val & 0xFF);
                                pcm[p + 1] = (byte) ((val >> 8) & 0xFF);
                            }
                        }

                        line.write(pcm, 0, pcm.length);
                    } catch (Exception ignored) {}

                    accumulatedSec += (msPerSample / 1000.0);
                    player.setPositionMs((long) (accumulatedSec * 1000));
                }
            }

            if (line != null && !isStopRequested.getAsBoolean()) {
                line.drain();
            }
            return true;

        } catch (Exception e) {
            Soundcloud.LOGGER.warn("[SoundCloud] HLS AAC playback error: {}", e.getMessage());
            return false;
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                    line.close();
                } catch (Exception ignored) {}
            }
            activeLine = null;
        }
    }

    private static byte[] downloadBytes(String urlStr, BooleanSupplier isStopRequested) {
        if (isStopRequested.getAsBoolean()) return null;
        try {
            URL u = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            if (!isStopRequested.getAsBoolean()) {
                Soundcloud.LOGGER.warn("[SoundCloud] downloadBytes error for {}: {}", urlStr, e.getMessage());
            }
            return null;
        }
    }
}
