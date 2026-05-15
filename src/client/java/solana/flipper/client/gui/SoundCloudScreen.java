package solana.flipper.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import solana.flipper.api.*;
import solana.flipper.client.audio.AudioPlayer;
import solana.flipper.client.audio.LocalAudioPlayer;
import solana.flipper.config.FavoritesManager;
import solana.flipper.config.ModConfig;

import java.util.*;
import java.util.concurrent.*;

public class SoundCloudScreen extends Screen {

    // palette
    private static final int C_BG      = 0xFF111827;
    private static final int C_PANEL   = 0xFF1F2937;
    private static final int C_PLAYER  = 0xFF0F172A;
    private static final int C_ACCENT  = 0xFFFF5500;
    private static final int C_ACCENT2 = 0xFFFF7733;
    private static final int C_TEXT    = 0xFFFFFFFF;
    private static final int C_SUB     = 0xFF9CA3AF;
    private static final int C_HOVER   = 0xFF374151;
    private static final int C_SEL     = 0xFF4C1D95;
    private static final int C_PROG    = 0xFF374151;
    private static final int C_GREEN   = 0xFF22C55E;

    // layout constants
    private static final int SEARCH_Y = 14;
    private static final int TAB_Y    = 36;
    private static final int LIST_TOP = 56;
    private static final int ITEM_H   = 20;
    // player bar: title row + progress row + controls row
    private static final int BAR_H    = 58;

    private enum Tab { SEARCH, FAVORITES, QUEUE }
    private Tab activeTab = Tab.SEARCH;

    private EditBox searchBox;
    private final List<SoundCloudTrack> searchResults = new ArrayList<>();
    private volatile boolean searching = false;
    private String searchStatus = "";

    private int  scrollOffset     = 0;
    private int  selectedIndex    = -1;
    private boolean draggingProg  = false;
    private boolean draggingVol   = false;

    private final ExecutorService async = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SC-GUI"); t.setDaemon(true); return t;
    });
    private final AudioPlayer player = AudioPlayer.getInstance();

    public SoundCloudScreen() {
        super(Component.literal("SoundCloud Player"));
    }

    // ── init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        // Search bar
        searchBox = new EditBox(font, 8, SEARCH_Y, width - 62, 16,
                Component.literal("Search..."));
        searchBox.setMaxLength(512);
        searchBox.setHint(Component.literal("Search or paste SoundCloud URL / playlist..."));
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Go"), btn -> doSearch())
                .bounds(width - 52, SEARCH_Y, 44, 16).build());

        // Tabs — equal thirds
        int tw = (width - 4) / 3;
        addRenderableWidget(Button.builder(Component.literal("Search"),
                btn -> switchTab(Tab.SEARCH)).bounds(2, TAB_Y, tw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Favorites"),
                btn -> switchTab(Tab.FAVORITES)).bounds(2 + tw + 1, TAB_Y, tw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Queue"),
                btn -> switchTab(Tab.QUEUE)).bounds(2 + (tw + 1) * 2, TAB_Y, tw, 16).build());

        // ── Player controls ───────────────────────────────────────────────────
        // Bar starts at height - BAR_H. Controls row at very bottom.
        int ctrlY = height - 20;
        int cx    = width / 2;
        int bh    = 16;
        int bw    = 28;
        int gap   = 3;

        // Centre group: |<  ||  >|  Shuf  Rep
        // 5 buttons × 28px + 4 gaps × 3px = 152px total, centred
        int startX = cx - (5 * bw + 4 * gap) / 2;

        addRenderableWidget(Button.builder(Component.literal("|<"),
                btn -> player.previous()).bounds(startX,                    ctrlY, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("||"),
                btn -> player.pause()).bounds(startX + (bw + gap),         ctrlY, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal(">|"),
                btn -> player.next()).bounds(startX + (bw + gap) * 2,      ctrlY, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Shuf"),
                btn -> player.setShuffle(!player.isShuffle()))
                .bounds(startX + (bw + gap) * 3, ctrlY, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Rep"),
                btn -> player.cycleRepeat())
                .bounds(startX + (bw + gap) * 4, ctrlY, bw, bh).build());

        // Right side: Fav  HUD
        int rightStart = startX + (bw + gap) * 5 + 6;
        addRenderableWidget(Button.builder(Component.literal("Fav"),
                btn -> favCurrent()).bounds(rightStart, ctrlY, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("HUD"),
                btn -> openHudPositionPicker()).bounds(rightStart + bw + gap, ctrlY, bw, bh).build());
    }

    private void switchTab(Tab t) { activeTab = t; scrollOffset = 0; selectedIndex = -1; }

    // ── search ────────────────────────────────────────────────────────────────

    private void doSearch() {
        String q = searchBox.getValue().trim();
        if (q.isEmpty()) return;

        if (LocalAudioPlayer.isLocalPath(q)) {
            FavoritesManager.addLocalFavorite(q);
            LocalAudioPlayer.playLocalFile(q);
            searchStatus = "Playing local file";
            return;
        }

        searching = true; searchStatus = "Loading...";
        searchResults.clear(); scrollOffset = 0; selectedIndex = -1;
        activeTab = Tab.SEARCH;

        async.submit(() -> {
            try {
                if (q.startsWith("http://") || q.startsWith("https://")) {
                    SoundCloudAPI.ResolveResult r = SoundCloudAPI.resolve(q);
                    if (r == null) { searchStatus = "Could not resolve URL"; }
                    else if (r.isTrack()) { searchResults.add(r.track()); searchStatus = "1 track"; }
                    else if (r.isPlaylist()) {
                        SoundCloudPlaylist pl = r.playlist();
                        searchResults.addAll(pl.getTracks());
                        searchStatus = "Playlist: " + pl.getTitle() + " · " + pl.getTracks().size() + " tracks";
                    }
                } else {
                    List<SoundCloudTrack> res = SoundCloudAPI.searchTracks(q, 20);
                    searchResults.addAll(res);
                    searchStatus = res.isEmpty() ? "No results" : res.size() + " tracks found";
                }
            } catch (Exception e) { searchStatus = "Error: " + e.getMessage(); }
            finally { searching = false; }
        });
    }

    private void playSelected() {
        List<SoundCloudTrack> list = getActiveList();
        if (selectedIndex < 0 || selectedIndex >= list.size()) return;
        SoundCloudTrack t = list.get(selectedIndex);
        if (LocalAudioPlayer.isLocalPath(t.getStreamUrl())) LocalAudioPlayer.playLocalFile(t.getStreamUrl());
        else player.setQueue(new ArrayList<>(list), selectedIndex);
    }

    private void favCurrent() {
        SoundCloudTrack t = player.getCurrentTrack();
        if (t != null && t.getId() != 0) FavoritesManager.addFavorite(t);
    }

    /** Opens a simple overlay to pick HUD anchor position (9 positions). */
    private void openHudPositionPicker() {
        minecraft.setScreen(new HudPositionScreen(this));
    }

    private List<SoundCloudTrack> getActiveList() {
        return switch (activeTab) {
            case SEARCH    -> searchResults;
            case FAVORITES -> {
                List<SoundCloudTrack> out = new ArrayList<>();
                for (FavoritesManager.FavoriteEntry e : FavoritesManager.getFavorites()) out.add(e.toTrack());
                yield out;
            }
            case QUEUE -> player.getQueue();
        };
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // 1. Backgrounds
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, width, 2, C_ACCENT);

        // Player bar background (drawn BEFORE super.render so buttons appear on top)
        int barY = height - BAR_H;
        g.fill(0, barY, width, height, C_PLAYER);
        g.fill(0, barY, width, barY + 1, C_ACCENT);

        // Tab underline
        int tw = (width - 4) / 3;
        int tx = switch (activeTab) {
            case SEARCH    -> 2;
            case FAVORITES -> 2 + tw + 1;
            case QUEUE     -> 2 + (tw + 1) * 2;
        };
        g.fill(tx, TAB_Y + 14, tx + tw, TAB_Y + 16, C_ACCENT);

        // 2. Track list
        renderList(g, mx, my);

        // 3. Player bar content (text, progress, volume)
        renderBarContent(g, mx, my);

        // 4. Widgets on top (buttons, search box)
        super.render(g, mx, my, delta);

        // 5. Search status (above list, below tabs)
        if (activeTab == Tab.SEARCH && !searchStatus.isEmpty()) {
            g.drawString(font, "§7" + (searching ? "Searching..." : searchStatus), 8, LIST_TOP - 10, C_SUB);
        }
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        List<SoundCloudTrack> list = getActiveList();
        int top = LIST_TOP;
        int bot = height - BAR_H - 1;
        int vis = Math.max(0, (bot - top) / ITEM_H);

        g.fill(0, top, width, bot, C_PANEL);

        for (int i = 0; i < vis; i++) {
            int idx = i + scrollOffset;
            if (idx >= list.size()) break;
            SoundCloudTrack t = list.get(idx);
            int iy = top + i * ITEM_H;

            boolean hov = my >= iy && my <= iy + ITEM_H;
            boolean sel = idx == selectedIndex;
            boolean now = player.getCurrentTrack() != null
                    && player.getCurrentTrack().getId() == t.getId() && t.getId() != 0;

            if (sel)      g.fill(0, iy, width, iy + ITEM_H - 1, C_SEL);
            else if (hov) g.fill(0, iy, width, iy + ITEM_H - 1, C_HOVER);
            if (now)      g.fill(0, iy, 3, iy + ITEM_H - 1, C_ACCENT);

            String num   = now ? "§a>" : "§8" + (idx + 1);
            String snip  = t.isSnipped() ? " §8[prev]" : "";
            String title = "§f" + trunc(t.getTitle(), 36) + " §7· " + trunc(t.getArtist(), 22) + snip;
            String dur   = t.getFormattedDuration();

            g.drawString(font, num,   6,                          iy + 5, C_TEXT);
            g.drawString(font, title, 20,                         iy + 5, C_TEXT);
            g.drawString(font, "§8" + dur, width - font.width(dur) - 8, iy + 5, C_SUB);
            if (FavoritesManager.isFavorite(t))
                g.drawString(font, "§cF", width - font.width(dur) - 20, iy + 5, C_TEXT);
        }

        // Scrollbar
        if (list.size() > vis && vis > 0) {
            int sbH = bot - top;
            int th  = Math.max(12, sbH * vis / list.size());
            int ty  = top + (sbH - th) * scrollOffset / Math.max(1, list.size() - vis);
            g.fill(width - 3, top, width, bot, 0xFF1F2937);
            g.fill(width - 3, ty, width, ty + th, C_ACCENT);
        }

        if (list.isEmpty() && !searching) {
            String msg = switch (activeTab) {
                case SEARCH    -> "Search tracks or paste a SoundCloud URL / playlist link";
                case FAVORITES -> "No favorites yet — press Fav while a track is playing";
                case QUEUE     -> "Queue is empty";
            };
            g.drawCenteredString(font, "§7" + msg, width / 2, top + 24, C_SUB);
        }
    }

    private void renderBarContent(GuiGraphics g, int mx, int my) {
        int barY = height - BAR_H;
        SoundCloudTrack t = player.getCurrentTrack();

        if (t == null) {
            g.drawCenteredString(font, "§7Double-click a track to play", width / 2, barY + 6, C_SUB);
            return;
        }

        // ── Title row ─────────────────────────────────────────────────────────
        String state = switch (player.getState()) {
            case PLAYING -> "§a>";
            case PAUSED  -> "§e||";
            case LOADING -> "§e..";
            default      -> "§7[]";
        };
        String snip  = t.isSnipped() ? " §8[preview]" : "";
        String title = state + " §f" + trunc(t.getTitle(), 38) + " §7· §e" + trunc(t.getArtist(), 22) + snip;
        g.drawCenteredString(font, title, width / 2, barY + 5, C_TEXT);

        // ── Progress bar ──────────────────────────────────────────────────────
        long posMs = player.getPositionMs();
        int  durMs = t.getDurationMs();
        int  pbX   = 8, pbW = width - 16, pbY = barY + 18, pbH = 4;

        g.fill(pbX, pbY, pbX + pbW, pbY + pbH, C_PROG);
        if (durMs > 0) {
            int filled = Math.max(0, Math.min(pbW, (int)((float) posMs / durMs * pbW)));
            g.fill(pbX, pbY, pbX + filled, pbY + pbH, C_ACCENT);
            g.fill(pbX + filled - 2, pbY - 1, pbX + filled + 2, pbY + pbH + 1, C_ACCENT2);
        }

        // Time labels
        int ps = (int)(posMs / 1000);
        String posStr = String.format("%d:%02d", ps / 60, ps % 60);
        String durStr = t.getFormattedDuration();
        g.drawString(font, "§8" + posStr, pbX, pbY + pbH + 2, C_SUB);
        g.drawString(font, "§8" + durStr, pbX + pbW - font.width(durStr), pbY + pbH + 2, C_SUB);

        // Seek hover tooltip
        if (my >= pbY - 3 && my <= pbY + pbH + 3 && mx >= pbX && mx <= pbX + pbW && durMs > 0) {
            int sec = (int)((float)(mx - pbX) / pbW * durMs / 1000);
            String tip = String.format("%d:%02d", sec / 60, sec % 60);
            g.fill(mx - 1, pbY - 4, mx + 1, pbY + pbH + 2, 0xAAFFFFFF);
            g.drawString(font, "§f" + tip, mx - font.width(tip) / 2, pbY - 12, C_TEXT);
        }

        // ── Volume slider (left side of controls row) ─────────────────────────
        int ctrlY  = height - 20;
        int volX   = 8;
        int volW   = 64;
        int volY   = ctrlY + 6;
        int volH   = 4;

        g.drawString(font, "§8Vol", volX, ctrlY + 2, C_SUB);
        int volBarX = volX + font.width("Vol") + 3;
        g.fill(volBarX, volY, volBarX + volW, volY + volH, C_PROG);
        int volFill = Math.max(0, Math.min(volW, (int)(player.getVolume() * volW)));
        g.fill(volBarX, volY, volBarX + volFill, volY + volH, C_ACCENT2);
        g.fill(volBarX + volFill - 2, volY - 1, volBarX + volFill + 2, volY + volH + 1, C_TEXT);
        g.drawString(font, "§8" + (int)(player.getVolume() * 100) + "%",
                volBarX + volW + 3, ctrlY + 2, C_SUB);

        // ── Shuffle/Repeat indicators (far right) ─────────────────────────────
        int indX = width - 4;
        if (player.getRepeat() != AudioPlayer.RepeatMode.OFF) {
            String rs = player.getRepeat() == AudioPlayer.RepeatMode.ONE ? "§aR:1" : "§aR:A";
            indX -= font.width(rs.substring(2)) + 2;
            g.drawString(font, rs, indX, ctrlY + 2, C_TEXT);
        }
        if (player.isShuffle()) {
            String ss = "§aShuf";
            indX -= font.width(ss.substring(2)) + 4;
            g.drawString(font, ss, indX, ctrlY + 2, C_TEXT);
        }
    }

    // ── input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        SoundCloudTrack t = player.getCurrentTrack();
        int barY = height - BAR_H;

        // Volume slider click
        int ctrlY = height - 20;
        int volBarX = 8 + font.width("Vol") + 3;
        int volW = 64;
        int volY = ctrlY + 6, volH = 4;
        if (btn == 0 && my >= volY - 4 && my <= volY + volH + 4 && mx >= volBarX && mx <= volBarX + volW) {
            player.setVolume((float) Math.max(0, Math.min(1, (mx - volBarX) / volW)));
            draggingVol = true;
            return true;
        }

        // Progress bar click
        if (t != null && t.getDurationMs() > 0 && btn == 0) {
            int pbX = 8, pbW = width - 16, pbY = barY + 18, pbH = 4;
            if (my >= pbY - 4 && my <= pbY + pbH + 4 && mx >= pbX && mx <= pbX + pbW) {
                player.seekToSeconds((int)((float)(mx - pbX) / pbW * t.getDurationMs() / 1000));
                draggingProg = true;
                return true;
            }
        }

        // Track list click
        List<SoundCloudTrack> list = getActiveList();
        int top = LIST_TOP, bot = height - BAR_H - 1;
        if (my >= top && my <= bot) {
            int idx = (int)((my - top) / ITEM_H) + scrollOffset;
            if (idx >= 0 && idx < list.size()) {
                if (btn == 0) {
                    if (selectedIndex == idx) playSelected();
                    else selectedIndex = idx;
                } else if (btn == 1) {
                    SoundCloudTrack tr = list.get(idx);
                    if (activeTab == Tab.FAVORITES) {
                        List<FavoritesManager.FavoriteEntry> favs = FavoritesManager.getFavorites();
                        if (idx < favs.size()) FavoritesManager.removeFavorite(favs.get(idx));
                    } else {
                        if (FavoritesManager.isFavorite(tr)) {
                            FavoritesManager.getFavorites().removeIf(e ->
                                    e.trackId == tr.getId() || e.source.equals(tr.getPermalinkUrl()));
                            FavoritesManager.save();
                        } else FavoritesManager.addFavorite(tr);
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingProg = false; draggingVol = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x();
        if (draggingVol) {
            int volBarX2 = 8 + font.width("Vol") + 3;
            int volW2 = 64;
            player.setVolume((float) Math.max(0, Math.min(1, (mx - volBarX2) / volW2)));
            return true;
        }
        if (draggingProg) {
            SoundCloudTrack t = player.getCurrentTrack();
            if (t != null && t.getDurationMs() > 0) {
                int pbX = 8, pbW = width - 16;
                player.seekToSeconds((int)(Math.max(0, Math.min(1, (mx - pbX) / pbW)) * t.getDurationMs() / 1000));
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        List<SoundCloudTrack> list = getActiveList();
        int top = LIST_TOP, bot = height - BAR_H - 1;
        int vis = (bot - top) / ITEM_H;
        if (my >= top && my <= bot) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) vScroll, Math.max(0, list.size() - vis)));
            return true;
        }
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 257 && searchBox.isFocused())  { doSearch();    return true; }
        if (key == 264) { selectedIndex = Math.min(selectedIndex + 1, getActiveList().size() - 1); return true; }
        if (key == 265) { selectedIndex = Math.max(selectedIndex - 1, 0); return true; }
        if (key == 257 && !searchBox.isFocused()) { playSelected(); return true; }
        if (key == 32  && !searchBox.isFocused()) { player.pause(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
