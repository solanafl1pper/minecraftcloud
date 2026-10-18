package solana.flipper.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import solana.flipper.api.SoundCloudAPI;
import solana.flipper.api.SoundCloudPlaylist;
import solana.flipper.api.SoundCloudTrack;
import solana.flipper.client.audio.AudioPlayer;
import solana.flipper.client.audio.LocalAudioPlayer;
import solana.flipper.config.FavoritesManager;
import solana.flipper.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modernized, sleek CloudBeats player GUI.
 */
public class SoundCloudScreen extends Screen {

    // ── Layout Constants ───────────────────────────────────────────────────────
    private static final int HEADER_H  = 44;
    private static final int TABS_Y    = 48;
    private static final int TABS_H    = 22;
    private static final int LIST_TOP  = 74;
    private static final int ITEM_H    = 24;
    private static final int BAR_H     = 58;

    public enum Tab {
        SEARCH("Search"),
        FAVORITES("Favorites"),
        QUEUE("Queue"),
        SETTINGS("HUD & Settings");

        private final String title;
        Tab(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    private Tab activeTab = Tab.SEARCH;

    // Search state
    private EditBox searchBox;
    private final List<SoundCloudTrack> searchResults = new ArrayList<>();
    private volatile boolean searching = false;
    private String searchStatus = "";
    private long lastAnimTime = System.currentTimeMillis();

    // Interaction state
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private boolean draggingProg = false;
    private boolean draggingVol = false;
    private float savedVolumeBeforeMute = 0.5f;
    private String tooltipText = null;

    private final ExecutorService async = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SC-GUI");
        t.setDaemon(true);
        return t;
    });

    private final AudioPlayer player = AudioPlayer.getInstance();

    public SoundCloudScreen() {
        super(Component.literal("CloudBeats"));
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        int searchW = Math.min(320, width - 180);
        int searchX = 140;

        // Search text box
        searchBox = new EditBox(font, searchX, 12, searchW, 20, Component.literal("Search"));
        searchBox.setMaxLength(512);
        searchBox.setHint(Component.literal("Search track or paste URL / MP3 path..."));
        addRenderableWidget(searchBox);

        // Search Button
        int btnW = 54;
        addRenderableWidget(Button.builder(Component.literal("Search"), btn -> doSearch())
                .bounds(searchX + searchW + 4, 12, btnW, 20).build());

        // Clear Search Button
        addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> {
            searchBox.setValue("");
            searchResults.clear();
            searchStatus = "";
        }).bounds(searchX + searchW + btnW + 8, 12, 46, 20).build());

        // Close Button (top-right)
        addRenderableWidget(Button.builder(Component.literal("✕"), btn -> onClose())
                .bounds(width - 28, 12, 20, 20).build());
    }

    private void switchTab(Tab tab) {
        this.activeTab = tab;
        this.scrollOffset = 0;
        this.selectedIndex = -1;
    }

    // ── Search Action ────────────────────────────────────────────────────────

    private void doSearch() {
        String q = searchBox.getValue().trim();
        if (q.isEmpty()) return;

        if (LocalAudioPlayer.isLocalPath(q)) {
            FavoritesManager.addLocalFavorite(q);
            LocalAudioPlayer.playLocalFile(q);
            searchStatus = "Playing local MP3 file";
            activeTab = Tab.QUEUE;
            return;
        }

        searching = true;
        searchStatus = "Searching SoundCloud...";
        searchResults.clear();
        scrollOffset = 0;
        selectedIndex = -1;
        activeTab = Tab.SEARCH;

        async.submit(() -> {
            try {
                if (q.startsWith("http://") || q.startsWith("https://")) {
                    SoundCloudAPI.ResolveResult r = SoundCloudAPI.resolve(q);
                    if (r == null) {
                        searchStatus = "Could not resolve URL";
                    } else if (r.isTrack()) {
                        searchResults.add(r.track());
                        searchStatus = "Track loaded";
                    } else if (r.isPlaylist()) {
                        SoundCloudPlaylist pl = r.playlist();
                        searchResults.addAll(pl.getTracks());
                        searchStatus = "Playlist: " + pl.getTitle() + " (" + pl.getTracks().size() + " tracks)";
                    }
                } else {
                    List<SoundCloudTrack> res = SoundCloudAPI.searchTracks(q, 25);
                    searchResults.addAll(res);
                    searchStatus = res.isEmpty() ? "No tracks found" : res.size() + " tracks found";
                }
            } catch (Exception e) {
                searchStatus = "Error: " + e.getMessage();
            } finally {
                searching = false;
            }
        });
    }

    private void playTrack(SoundCloudTrack track, List<SoundCloudTrack> list, int index) {
        if (track == null) return;
        if (LocalAudioPlayer.isLocalPath(track.getStreamUrl())) {
            LocalAudioPlayer.playLocalFile(track.getStreamUrl());
        } else if (list != null && index >= 0 && index < list.size()) {
            player.setQueue(new ArrayList<>(list), index);
        } else {
            player.play(track);
        }
    }

    private void toggleFavorite(SoundCloudTrack t) {
        if (t == null) return;
        if (FavoritesManager.isFavorite(t)) {
            FavoritesManager.getFavorites().removeIf(e ->
                    (e.trackId == t.getId() && t.getId() != 0) || e.source.equals(t.getPermalinkUrl()));
            FavoritesManager.save();
        } else {
            FavoritesManager.addFavorite(t);
        }
    }

    private List<SoundCloudTrack> getActiveList() {
        return switch (activeTab) {
            case SEARCH -> searchResults;
            case FAVORITES -> {
                List<SoundCloudTrack> out = new ArrayList<>();
                for (FavoritesManager.FavoriteEntry e : FavoritesManager.getFavorites()) {
                    out.add(e.toTrack());
                }
                yield out;
            }
            case QUEUE -> player.getQueue();
            case SETTINGS -> List.of();
        };
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        tooltipText = null;
        float animSec = (System.currentTimeMillis() - lastAnimTime) / 1000f;

        // 1. Dark ambient background
        g.fill(0, 0, width, height, GuiHelper.C_BG_SCREEN);

        // 2. Header Panel
        renderHeader(g, mx, my);

        // 3. Navigation Tabs
        renderTabs(g, mx, my);

        // 4. Main Body: List or Settings
        if (activeTab == Tab.SETTINGS) {
            renderSettingsTab(g, mx, my);
        } else {
            renderTrackList(g, mx, my, animSec);
        }

        // 5. Bottom Player Dock
        renderPlayerDock(g, mx, my, animSec);

        // 6. Vanilla Widgets (Search box & buttons)
        super.render(g, mx, my, delta);

        // 7. Tooltip on top
        if (tooltipText != null) {
            GuiHelper.drawTooltip(g, font, tooltipText, mx, my, width, height);
        }
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, HEADER_H, GuiHelper.C_HEADER_BG);
        GuiHelper.drawBorder(g, 0, 0, width, HEADER_H, GuiHelper.C_WINDOW_BORDER);

        // Orange brand line at top
        g.fill(0, 0, width, 2, GuiHelper.C_ACCENT);

        // CloudBeats Brand & Logo
        g.drawString(font, "§6☁ §f§lCloudBeats", 14, 16, GuiHelper.C_TEXT_MAIN, false);
        g.drawString(font, "§7v0.6.9", 98, 16, GuiHelper.C_TEXT_MUTED, false);

        // Status text below search if active
        if (activeTab == Tab.SEARCH && !searchStatus.isEmpty()) {
            String statusStr = searching ? "§e⏳ " + searchStatus : "§7" + searchStatus;
            g.drawString(font, statusStr, 140, 34, GuiHelper.C_TEXT_SUB, false);
        }
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int tabW = Math.min(130, (width - 24) / Tab.values().length);
        int totalW = tabW * Tab.values().length;
        int startX = 12;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int tx = startX + i * (tabW + 4);
            boolean active = (tab == activeTab);
            boolean hov = mx >= tx && mx <= tx + tabW && my >= TABS_Y && my <= TABS_Y + TABS_H;

            String label = tab.getTitle();
            if (tab == Tab.FAVORITES) {
                int count = FavoritesManager.getFavorites().size();
                if (count > 0) label += " (" + count + ")";
            } else if (tab == Tab.QUEUE) {
                int count = player.getQueue().size();
                if (count > 0) label += " (" + count + ")";
            }

            GuiHelper.drawPillButton(g, font, label, tx, TABS_Y, tabW, TABS_H, hov, active, 0);

            // Active underline indicator
            if (active) {
                g.fill(tx + 4, TABS_Y + TABS_H - 2, tx + tabW - 4, TABS_Y + TABS_H, GuiHelper.C_ACCENT);
            }
        }
    }

    private void renderTrackList(GuiGraphics g, int mx, int my, float animSec) {
        List<SoundCloudTrack> list = getActiveList();
        int top = LIST_TOP;
        int bot = height - BAR_H - 4;
        int vis = Math.max(0, (bot - top) / ITEM_H);

        // Container frame
        GuiHelper.drawRoundedCard(g, 10, top - 2, width - 20, bot - top + 4,
                GuiHelper.C_WINDOW_BG, GuiHelper.C_WINDOW_BORDER);

        if (list.isEmpty()) {
            String msg = switch (activeTab) {
                case SEARCH -> searching ? "Searching SoundCloud..." : "Type in the search bar above or paste any track/playlist link!";
                case FAVORITES -> "No favorites yet. Click the ♥ icon on any track to save it here!";
                case QUEUE -> "Your queue is empty. Double-click any track or click [+] to add songs!";
                default -> "";
            };
            g.drawCenteredString(font, "§8" + msg, width / 2, top + (bot - top) / 2 - 4, GuiHelper.C_TEXT_SUB);
            return;
        }

        SoundCloudTrack cur = player.getCurrentTrack();
        boolean isPlaying = player.getState() == AudioPlayer.State.PLAYING;

        for (int i = 0; i < vis; i++) {
            int idx = i + scrollOffset;
            if (idx >= list.size()) break;
            SoundCloudTrack t = list.get(idx);
            int iy = top + i * ITEM_H;
            int iw = width - 28;
            int ix = 14;

            boolean isCurrent = cur != null && cur.getId() == t.getId() && t.getId() != 0;
            boolean isSel = (idx == selectedIndex);
            boolean hov = mx >= ix && mx <= ix + iw && my >= iy && my <= iy + ITEM_H - 2;

            int bg = isCurrent ? GuiHelper.C_CARD_PLAYING : (isSel ? GuiHelper.C_CARD_SELECTED : (hov ? GuiHelper.C_CARD_HOVER : GuiHelper.C_CARD_BG));
            int border = isCurrent ? GuiHelper.C_ACCENT : (isSel ? 0xFF6366F1 : (hov ? GuiHelper.C_WINDOW_BORDER : 0));

            GuiHelper.drawRoundedCard(g, ix, iy, iw, ITEM_H - 2, bg, border);

            // Left icon / equalizer
            if (isCurrent && isPlaying) {
                GuiHelper.drawEqualizer(g, ix + 8, iy + 4, 12, animSec, GuiHelper.C_ACCENT);
            } else if (isCurrent) {
                g.drawString(font, "§6▶", ix + 8, iy + 6, GuiHelper.C_ACCENT, false);
            } else {
                g.drawString(font, "§8" + (idx + 1), ix + 8, iy + 6, GuiHelper.C_TEXT_MUTED, false);
            }

            // Music note badge
            g.fill(ix + 26, iy + 3, ix + 40, iy + ITEM_H - 5, 0xFF21293B);
            g.drawString(font, "♫", ix + 30, iy + 6, isCurrent ? GuiHelper.C_ACCENT : GuiHelper.C_TEXT_SUB, false);

            // Track title & artist
            String previewTag = t.isSnipped() ? " §6[Preview]" : "";
            int textLimit = Math.max(10, (iw - 190) / 6);
            String title = "§f" + trunc(t.getTitle(), textLimit) + previewTag;
            String artist = "§7" + trunc(t.getArtist(), textLimit / 2);

            g.drawString(font, title, ix + 46, iy + 6, GuiHelper.C_TEXT_MAIN, false);
            g.drawString(font, artist, ix + 46 + font.width(title) + 8, iy + 6, GuiHelper.C_TEXT_SUB, false);

            // Right side: Actions & Duration
            int rx = ix + iw - 6;

            // Duration
            String dur = t.getFormattedDuration();
            rx -= font.width(dur);
            g.drawString(font, "§8" + dur, rx, iy + 6, GuiHelper.C_TEXT_SUB, false);

            // Action Buttons
            // [Play] [♥] [+] [✕]
            rx -= 22; // Favorite button
            boolean isFav = FavoritesManager.isFavorite(t);
            boolean hovFav = mx >= rx && mx <= rx + 16 && my >= iy + 2 && my <= iy + ITEM_H - 4;
            String favIcon = isFav ? "§c♥" : (hovFav ? "§c♡" : "§8♡");
            g.drawString(font, favIcon, rx + 4, iy + 6, isFav ? GuiHelper.C_RED : GuiHelper.C_TEXT_MUTED, false);
            if (hovFav) tooltipText = isFav ? "Remove from Favorites" : "Add to Favorites";

            rx -= 20; // Enqueue button
            boolean hovAdd = mx >= rx && mx <= rx + 16 && my >= iy + 2 && my <= iy + ITEM_H - 4;
            g.drawString(font, hovAdd ? "§a+" : "§8+", rx + 4, iy + 6, hovAdd ? GuiHelper.C_GREEN : GuiHelper.C_TEXT_MUTED, false);
            if (hovAdd) tooltipText = "Add to Queue";

            if (activeTab == Tab.QUEUE || activeTab == Tab.FAVORITES) {
                rx -= 18; // Remove button
                boolean hovRem = mx >= rx && mx <= rx + 14 && my >= iy + 2 && my <= iy + ITEM_H - 4;
                g.drawString(font, hovRem ? "§c✕" : "§8✕", rx + 3, iy + 6, hovRem ? GuiHelper.C_RED : GuiHelper.C_TEXT_MUTED, false);
                if (hovRem) tooltipText = activeTab == Tab.QUEUE ? "Remove from Queue" : "Remove Favorite";
            }
        }

        // Modern Scrollbar
        if (list.size() > vis && vis > 0) {
            int sbX = width - 12;
            int sbY = top;
            int sbH = bot - top;
            int thumbH = Math.max(16, sbH * vis / list.size());
            int thumbY = sbY + (sbH - thumbH) * scrollOffset / Math.max(1, list.size() - vis);

            g.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF1C2230);
            GuiHelper.drawRoundedCard(g, sbX, thumbY, 4, thumbH, GuiHelper.C_ACCENT, 0);
        }
    }

    private void renderSettingsTab(GuiGraphics g, int mx, int my) {
        int top = LIST_TOP;
        int bot = height - BAR_H - 4;

        GuiHelper.drawRoundedCard(g, 10, top - 2, width - 20, bot - top + 4,
                GuiHelper.C_WINDOW_BG, GuiHelper.C_WINDOW_BORDER);

        int cx = width / 2;
        int cy = top + 10;

        g.drawCenteredString(font, "§f§lHUD Configuration & Controls", cx, cy, GuiHelper.C_TEXT_MAIN);
        g.drawCenteredString(font, "§7Configure in-game music overlay appearance and screen anchor", cx, cy + 12, GuiHelper.C_TEXT_SUB);

        ModConfig cfg = ModConfig.get();

        // 3x3 Anchor Grid
        int cellW = 76, cellH = 26, gap = 4;
        int gridW = cellW * 3 + gap * 2;
        int gx = cx - gridW / 2;
        int gy = cy + 32;

        String[][] gridLabels = {
                {"Top-Left", "Top-Center", "Top-Right"},
                {"Mid-Left", "Center", "Mid-Right"},
                {"Bot-Left", "Bottom", "Bot-Right"}
        };

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int bx = gx + c * (cellW + gap);
                int by = gy + r * (cellH + gap);
                boolean isAnchor = (cfg.hudAnchorRow == r && cfg.hudAnchorCol == c);
                boolean hov = mx >= bx && mx <= bx + cellW && my >= by && my <= by + cellH;

                GuiHelper.drawPillButton(g, font, gridLabels[r][c], bx, by, cellW, cellH,
                        hov, isAnchor, GuiHelper.C_ACCENT);
            }
        }

        // Toggles & Presets row
        int optY = gy + (cellH + gap) * 3 + 12;

        // Visibility Toggle
        int btnW = 92, btnH = 20;
        int b1x = cx - btnW * 2 - 8;
        boolean hovVis = mx >= b1x && mx <= b1x + btnW && my >= optY && my <= optY + btnH;
        GuiHelper.drawPillButton(g, font, cfg.hudVisible ? "HUD: ON" : "HUD: OFF",
                b1x, optY, btnW, btnH, hovVis, cfg.hudVisible, GuiHelper.C_GREEN);

        // Buttons mode Toggle
        int b2x = cx - btnW - 4;
        boolean hovMode = mx >= b2x && mx <= b2x + btnW && my >= optY && my <= optY + btnH;
        GuiHelper.drawPillButton(g, font, cfg.hudButtons ? "Full HUD" : "Compact HUD",
                b2x, optY, btnW, btnH, hovMode, cfg.hudButtons, 0);

        // Size Presets: S / M / L
        int b3x = cx + 4;
        String[] szNames = {"Size: S", "Size: M", "Size: L"};
        boolean hovSz = mx >= b3x && mx <= b3x + btnW && my >= optY && my <= optY + btnH;
        GuiHelper.drawPillButton(g, font, szNames[Math.max(0, Math.min(2, cfg.hudSize))],
                b3x, optY, btnW, btnH, hovSz, true, 0);

        // Reset manual position
        int b4x = cx + btnW + 8;
        boolean hovReset = mx >= b4x && mx <= b4x + btnW && my >= optY && my <= optY + btnH;
        GuiHelper.drawPillButton(g, font, "Reset Pos", b4x, optY, btnW, btnH, hovReset, false, 0);

        // Keybind Shortcuts Guide
        int guideY = optY + 30;
        g.drawCenteredString(font, "§8Shortcuts: [H] Toggle HUD · [Space] Pause · [Enter] Play · [Arrows] Navigate", cx, guideY, GuiHelper.C_TEXT_MUTED);
    }

    private void renderPlayerDock(GuiGraphics g, int mx, int my, float animSec) {
        int barY = height - BAR_H;
        SoundCloudTrack track = player.getCurrentTrack();

        // Dock background with frosted border
        g.fill(0, barY, width, height, GuiHelper.C_BAR_BG);
        g.fill(0, barY, width, barY + 1, GuiHelper.C_WINDOW_BORDER);
        g.fill(0, barY + 1, width, barY + 2, GuiHelper.C_ACCENT_BG);

        // ── Left: Current Track Info ──────────────────────────────────────────
        int leftW = Math.max(160, width / 4);
        if (track != null) {
            // Vinyl icon
            g.fill(12, barY + 12, 44, barY + 44, 0xFF1C2230);
            GuiHelper.drawBorder(g, 12, barY + 12, 32, 32, GuiHelper.C_ACCENT);
            g.drawString(font, "♫", 24, barY + 24, GuiHelper.C_ACCENT, false);

            // Title & Artist
            String title = trunc(track.getTitle(), 22);
            String artist = trunc(track.getArtist(), 20);
            g.drawString(font, "§f§l" + title, 50, barY + 16, GuiHelper.C_TEXT_MAIN, false);
            g.drawString(font, "§7" + artist, 50, barY + 28, GuiHelper.C_TEXT_SUB, false);

            // Heart button
            boolean isFav = FavoritesManager.isFavorite(track);
            boolean hovFav = mx >= 50 + font.width(title) + 6 && mx <= 50 + font.width(title) + 20
                    && my >= barY + 14 && my <= barY + 26;
            String heart = isFav ? "§c♥" : (hovFav ? "§c♡" : "§8♡");
            g.drawString(font, heart, 50 + font.width(title) + 6, barY + 16, isFav ? GuiHelper.C_RED : GuiHelper.C_TEXT_MUTED, false);
            if (hovFav) tooltipText = isFav ? "Favorited" : "Click to Favorite";
        } else {
            g.drawString(font, "§8No track playing", 16, barY + 24, GuiHelper.C_TEXT_MUTED, false);
        }

        // ── Center: Controls & Progress Bar ───────────────────────────────────
        int cx = width / 2;
        int ctrlY = barY + 8;
        int bw = 24, bh = 18, gap = 4;

        // 7 buttons: Shuf |< << Play >> >| Rep
        int startX = cx - (7 * bw + 6 * gap) / 2;

        // Shuffle
        boolean hovShuf = mx >= startX && mx <= startX + bw && my >= ctrlY && my <= ctrlY + bh;
        GuiHelper.drawPillButton(g, font, "🔀", startX, ctrlY, bw, bh, hovShuf, player.isShuffle(), 0);
        if (hovShuf) tooltipText = "Shuffle: " + (player.isShuffle() ? "ON" : "OFF");

        // Previous
        int bPrevX = startX + (bw + gap);
        boolean hovPrev = mx >= bPrevX && mx <= bPrevX + bw && my >= ctrlY && my <= ctrlY + bh;
        GuiHelper.drawPillButton(g, font, "⏮", bPrevX, ctrlY, bw, bh, hovPrev, false, 0);
        if (hovPrev) tooltipText = "Previous Track";

        // Rewind -10s
        int bRewX = startX + (bw + gap) * 2;
        boolean hovRew = mx >= bRewX && mx <= bRewX + bw && my >= ctrlY && my <= ctrlY + bh;
        GuiHelper.drawPillButton(g, font, "-10", bRewX, ctrlY, bw, bh, hovRew, false, 0);
        if (hovRew) tooltipText = "Rewind 10s";

        // Play / Pause (Primary button)
        int bPlayX = startX + (bw + gap) * 3;
        boolean isPlaying = player.getState() == AudioPlayer.State.PLAYING;
        boolean hovPlay = mx >= bPlayX && mx <= bPlayX + bw && my >= ctrlY && my <= ctrlY + bh;
        String playIcon = isPlaying ? "⏸" : "▶";
        GuiHelper.drawPillButton(g, font, playIcon, bPlayX, ctrlY, bw, bh, hovPlay, true, GuiHelper.C_ACCENT);
        if (hovPlay) tooltipText = isPlaying ? "Pause" : "Play";

        // Forward +10s
        int bFwdX = startX + (bw + gap) * 4;
        boolean hovFwd = mx >= bFwdX && mx <= bFwdX + bw && my >= ctrlY && my <= ctrlY + bh;
        GuiHelper.drawPillButton(g, font, "+10", bFwdX, ctrlY, bw, bh, hovFwd, false, 0);
        if (hovFwd) tooltipText = "Forward 10s";

        // Next
        int bNextX = startX + (bw + gap) * 5;
        boolean hovNext = mx >= bNextX && mx <= bNextX + bw && my >= ctrlY && my <= ctrlY + bh;
        GuiHelper.drawPillButton(g, font, "⏭", bNextX, ctrlY, bw, bh, hovNext, false, 0);
        if (hovNext) tooltipText = "Next Track";

        // Repeat
        int bRepX = startX + (bw + gap) * 6;
        boolean hovRep = mx >= bRepX && mx <= bRepX + bw && my >= ctrlY && my <= ctrlY + bh;
        AudioPlayer.RepeatMode rep = player.getRepeat();
        String repIcon = rep == AudioPlayer.RepeatMode.ONE ? "🔂" : "🔁";
        GuiHelper.drawPillButton(g, font, repIcon, bRepX, ctrlY, bw, bh, hovRep, rep != AudioPlayer.RepeatMode.OFF, 0);
        if (hovRep) tooltipText = "Repeat: " + rep.name();

        // Progress Bar
        int pbW = Math.min(360, width - 360);
        int pbX = cx - pbW / 2;
        int pbY = barY + 36;
        int pbH = 5;

        long posMs = player.getPositionMs();
        int durMs = track != null ? track.getDurationMs() : 0;

        // Background track
        GuiHelper.drawRoundedCard(g, pbX, pbY, pbW, pbH, GuiHelper.C_PROG_TRACK, 0);

        if (durMs > 0) {
            float progress = Math.max(0f, Math.min(1f, (float) posMs / durMs));
            int filledW = (int) (progress * pbW);
            g.fill(pbX, pbY, pbX + filledW, pbY + pbH, GuiHelper.C_ACCENT);

            // Glowing thumb knob
            boolean hovPb = mx >= pbX && mx <= pbX + pbW && my >= pbY - 4 && my <= pbY + pbH + 4;
            int thumbR = hovPb ? 4 : 3;
            g.fill(pbX + filledW - thumbR, pbY - 1, pbX + filledW + thumbR, pbY + pbH + 1, GuiHelper.C_TEXT_MAIN);

            // Seek tooltip
            if (hovPb) {
                int seekSec = (int) ((float) (mx - pbX) / pbW * durMs / 1000);
                tooltipText = String.format("%d:%02d", seekSec / 60, seekSec % 60);
            }
        }

        // Time labels
        int ps = (int) (posMs / 1000);
        String posStr = String.format("%d:%02d", ps / 60, ps % 60);
        String durStr = track != null ? track.getFormattedDuration() : "0:00";
        g.drawString(font, "§8" + posStr, pbX - font.width(posStr) - 6, pbY - 2, GuiHelper.C_TEXT_SUB, false);
        g.drawString(font, "§8" + durStr, pbX + pbW + 6, pbY - 2, GuiHelper.C_TEXT_SUB, false);

        // ── Right: Volume & Quick Controls ────────────────────────────────────
        int rx = width - 150;
        int volY = barY + 22;
        int volW = 60;
        int volH = 4;

        // Speaker / Mute icon
        boolean isMuted = player.getVolume() <= 0.001f;
        String spkIcon = isMuted ? "§c🔇" : "§f🔊";
        boolean hovSpk = mx >= rx - 18 && mx <= rx && my >= volY - 4 && my <= volY + 12;
        g.drawString(font, spkIcon, rx - 16, volY - 2, GuiHelper.C_TEXT_MAIN, false);
        if (hovSpk) tooltipText = isMuted ? "Unmute" : "Mute";

        // Volume Bar
        GuiHelper.drawRoundedCard(g, rx, volY, volW, volH, GuiHelper.C_PROG_TRACK, 0);
        int volFill = (int) (player.getVolume() * volW);
        g.fill(rx, volY, rx + volFill, volY + volH, GuiHelper.C_ACCENT);
        g.fill(rx + volFill - 2, volY - 1, rx + volFill + 2, volY + volH + 1, GuiHelper.C_TEXT_MAIN);

        // Volume percent
        int volPct = (int) (player.getVolume() * 100);
        g.drawString(font, "§7" + volPct + "%", rx + volW + 6, volY - 2, GuiHelper.C_TEXT_SUB, false);
    }

    // ── Input Handling ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        // 1. Navigation Tabs click
        if (my >= TABS_Y && my <= TABS_Y + TABS_H) {
            int tabW = Math.min(130, (width - 24) / Tab.values().length);
            int startX = 12;
            for (int i = 0; i < Tab.values().length; i++) {
                int tx = startX + i * (tabW + 4);
                if (mx >= tx && mx <= tx + tabW) {
                    switchTab(Tab.values()[i]);
                    return true;
                }
            }
        }

        // 2. Settings tab interactions
        if (activeTab == Tab.SETTINGS) {
            if (handleSettingsClicks(mx, my)) return true;
        }

        // 3. Track List interactions
        if (activeTab != Tab.SETTINGS) {
            if (handleTrackListClicks(mx, my, btn)) return true;
        }

        // 4. Bottom Player Dock interactions
        if (handlePlayerDockClicks(mx, my, btn)) return true;

        return super.mouseClicked(event, bl);
    }

    private boolean handleTrackListClicks(double mx, double my, int btn) {
        int top = LIST_TOP;
        int bot = height - BAR_H - 4;
        List<SoundCloudTrack> list = getActiveList();

        if (my >= top && my <= bot && mx >= 10 && mx <= width - 10) {
            int idx = (int) ((my - top) / ITEM_H) + scrollOffset;
            if (idx >= 0 && idx < list.size()) {
                SoundCloudTrack t = list.get(idx);
                int iy = top + (idx - scrollOffset) * ITEM_H;
                int iw = width - 28;
                int ix = 14;
                int rx = ix + iw - 6;

                // Duration offset
                String dur = t.getFormattedDuration();
                rx -= font.width(dur);

                // Favorite button click
                rx -= 22;
                if (mx >= rx && mx <= rx + 16 && my >= iy + 2 && my <= iy + ITEM_H - 4) {
                    toggleFavorite(t);
                    return true;
                }

                // Add to queue button click
                rx -= 20;
                if (mx >= rx && mx <= rx + 16 && my >= iy + 2 && my <= iy + ITEM_H - 4) {
                    player.addToQueue(t);
                    return true;
                }

                // Remove button click (in Queue or Favorites)
                if (activeTab == Tab.QUEUE || activeTab == Tab.FAVORITES) {
                    rx -= 18;
                    if (mx >= rx && mx <= rx + 14 && my >= iy + 2 && my <= iy + ITEM_H - 4) {
                        if (activeTab == Tab.QUEUE) {
                            if (idx < player.getQueue().size()) player.getQueue().remove(idx);
                        } else {
                            List<FavoritesManager.FavoriteEntry> favs = FavoritesManager.getFavorites();
                            if (idx < favs.size()) FavoritesManager.removeFavorite(favs.get(idx));
                        }
                        return true;
                    }
                }

                // 1-click Play on left icon area
                if (btn == 0 && mx <= ix + 42) {
                    playTrack(t, list, idx);
                    selectedIndex = idx;
                    return true;
                }

                // Row click / double click
                if (btn == 0) {
                    if (selectedIndex == idx) {
                        playTrack(t, list, idx);
                    } else {
                        selectedIndex = idx;
                    }
                    return true;
                } else if (btn == 1) {
                    toggleFavorite(t);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handlePlayerDockClicks(double mx, double my, int btn) {
        int barY = height - BAR_H;
        if (my < barY) return false;

        SoundCloudTrack track = player.getCurrentTrack();

        // 1. Current track favorite heart click
        if (track != null) {
            String title = trunc(track.getTitle(), 22);
            int heartX = 50 + font.width(title) + 6;
            if (mx >= heartX && mx <= heartX + 16 && my >= barY + 14 && my <= barY + 28) {
                toggleFavorite(track);
                return true;
            }
        }

        // 2. Control buttons
        int cx = width / 2;
        int ctrlY = barY + 8;
        int bw = 24, bh = 18, gap = 4;
        int startX = cx - (7 * bw + 6 * gap) / 2;

        // Shuffle
        if (mx >= startX && mx <= startX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.setShuffle(!player.isShuffle());
            return true;
        }
        // Prev
        int bPrevX = startX + (bw + gap);
        if (mx >= bPrevX && mx <= bPrevX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.previous();
            return true;
        }
        // Rewind -10s
        int bRewX = startX + (bw + gap) * 2;
        if (mx >= bRewX && mx <= bRewX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.seekRelative(-10);
            return true;
        }
        // Play / Pause (Instant, handles all player states)
        int bPlayX = startX + (bw + gap) * 3;
        if (mx >= bPlayX && mx <= bPlayX + bw && my >= ctrlY && my <= ctrlY + bh) {
            if (player.getState() == AudioPlayer.State.PLAYING || player.getState() == AudioPlayer.State.PAUSED) {
                player.pause();
            } else {
                if (selectedIndex >= 0 && selectedIndex < getActiveList().size()) {
                    playTrack(getActiveList().get(selectedIndex), getActiveList(), selectedIndex);
                } else if (!player.getQueue().isEmpty()) {
                    player.play(player.getQueue().get(Math.max(0, player.getQueueIndex())));
                } else if (!getActiveList().isEmpty()) {
                    playTrack(getActiveList().get(0), getActiveList(), 0);
                }
            }
            return true;
        }
        // Forward +10s
        int bFwdX = startX + (bw + gap) * 4;
        if (mx >= bFwdX && mx <= bFwdX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.seekRelative(10);
            return true;
        }
        // Next
        int bNextX = startX + (bw + gap) * 5;
        if (mx >= bNextX && mx <= bNextX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.next();
            return true;
        }
        // Repeat
        int bRepX = startX + (bw + gap) * 6;
        if (mx >= bRepX && mx <= bRepX + bw && my >= ctrlY && my <= ctrlY + bh) {
            player.cycleRepeat();
            return true;
        }

        // 3. Progress bar seek
        int pbW = Math.min(360, width - 360);
        int pbX = cx - pbW / 2;
        int pbY = barY + 36;
        int pbH = 5;

        if (track != null && track.getDurationMs() > 0 && btn == 0) {
            if (mx >= pbX && mx <= pbX + pbW && my >= pbY - 5 && my <= pbY + pbH + 5) {
                float frac = (float) (mx - pbX) / pbW;
                player.seekToSeconds((int) (frac * track.getDurationMs() / 1000));
                draggingProg = true;
                return true;
            }
        }

        // 4. Volume / Mute
        int rx = width - 150;
        int volY = barY + 22;
        int volW = 60;
        int volH = 4;

        // Mute speaker button
        if (mx >= rx - 18 && mx <= rx && my >= volY - 4 && my <= volY + 12) {
            if (player.getVolume() > 0.001f) {
                savedVolumeBeforeMute = player.getVolume();
                player.setVolume(0f);
            } else {
                player.setVolume(savedVolumeBeforeMute > 0 ? savedVolumeBeforeMute : 0.5f);
            }
            return true;
        }

        // Volume bar
        if (btn == 0 && mx >= rx && mx <= rx + volW && my >= volY - 4 && my <= volY + volH + 4) {
            float v = (float) (mx - rx) / volW;
            player.setVolume(v);
            draggingVol = true;
            return true;
        }

        return true;
    }

    private boolean handleSettingsClicks(double mx, double my) {
        int cx = width / 2;
        int top = LIST_TOP;
        int cy = top + 10;
        ModConfig cfg = ModConfig.get();

        // 3x3 Anchor Grid
        int cellW = 76, cellH = 26, gap = 4;
        int gridW = cellW * 3 + gap * 2;
        int gx = cx - gridW / 2;
        int gy = cy + 32;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int bx = gx + c * (cellW + gap);
                int by = gy + r * (cellH + gap);
                if (mx >= bx && mx <= bx + cellW && my >= by && my <= by + cellH) {
                    cfg.hudAnchorRow = r;
                    cfg.hudAnchorCol = c;
                    cfg.hudX = -1;
                    cfg.hudY = -1;
                    cfg.save();
                    return true;
                }
            }
        }

        // Options row
        int optY = gy + (cellH + gap) * 3 + 12;
        int btnW = 92, btnH = 20;

        // Visibility Toggle
        int b1x = cx - btnW * 2 - 8;
        if (mx >= b1x && mx <= b1x + btnW && my >= optY && my <= optY + btnH) {
            PlayerHud.toggle();
            return true;
        }

        // Mode Toggle
        int b2x = cx - btnW - 4;
        if (mx >= b2x && mx <= b2x + btnW && my >= optY && my <= optY + btnH) {
            PlayerHud.toggleButtons();
            return true;
        }

        // Size Presets
        int b3x = cx + 4;
        if (mx >= b3x && mx <= b3x + btnW && my >= optY && my <= optY + btnH) {
            PlayerHud.cycleSize();
            return true;
        }

        // Reset manual position
        int b4x = cx + btnW + 8;
        if (mx >= b4x && mx <= b4x + btnW && my >= optY && my <= optY + btnH) {
            cfg.hudX = -1;
            cfg.hudY = -1;
            cfg.save();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingProg = false;
        draggingVol = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x();

        if (draggingVol) {
            int rx = width - 150;
            int volW = 60;
            float v = (float) Math.max(0, Math.min(1, (mx - rx) / volW));
            player.setVolume(v);
            return true;
        }

        if (draggingProg) {
            SoundCloudTrack t = player.getCurrentTrack();
            if (t != null && t.getDurationMs() > 0) {
                int cx = width / 2;
                int pbW = Math.min(360, width - 360);
                int pbX = cx - pbW / 2;
                float frac = (float) Math.max(0, Math.min(1, (mx - pbX) / pbW));
                player.seekToSeconds((int) (frac * t.getDurationMs() / 1000));
            }
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (activeTab != Tab.SETTINGS) {
            List<SoundCloudTrack> list = getActiveList();
            int top = LIST_TOP;
            int bot = height - BAR_H - 4;
            int vis = (bot - top) / ITEM_H;
            if (my >= top && my <= bot) {
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int) vScroll, Math.max(0, list.size() - vis)));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // Enter key
        if (key == 257) {
            if (searchBox.isFocused()) {
                doSearch();
                return true;
            } else if (selectedIndex >= 0 && selectedIndex < getActiveList().size()) {
                playTrack(getActiveList().get(selectedIndex), getActiveList(), selectedIndex);
                return true;
            }
        }

        // Space key (Pause/Play when search isn't focused)
        if (key == 32 && !searchBox.isFocused()) {
            player.pause();
            return true;
        }

        // Arrows navigation
        if (key == 264 && !searchBox.isFocused()) { // Down arrow
            selectedIndex = Math.min(selectedIndex + 1, getActiveList().size() - 1);
            return true;
        }
        if (key == 265 && !searchBox.isFocused()) { // Up arrow
            selectedIndex = Math.max(selectedIndex - 1, 0);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(0, max - 1)) + "…" : s;
    }
}
