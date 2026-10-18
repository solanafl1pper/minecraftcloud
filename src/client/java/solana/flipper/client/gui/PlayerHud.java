package solana.flipper.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import solana.flipper.api.SoundCloudTrack;
import solana.flipper.client.audio.AudioPlayer;
import solana.flipper.config.ModConfig;

/**
 * Modernized in-game music player HUD.
 *
 * Supports:
 * - COMPACT mode: Minimal title + glowing progress bar
 * - FULL mode: Title + animated equalizer + progress bar + playback controls (⏮ ⏯ ⏭ 🔀 🔁) + volume
 * - 3 Size presets: S (180px), M (250px), L (330px)
 * - 9 Screen anchors + interactive in-game drag positioning
 */
public class PlayerHud implements HudElement {

    private static PlayerHud INSTANCE;

    public static PlayerHud getInstance() {
        return INSTANCE;
    }

    public static void register() {
        INSTANCE = new PlayerHud();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("soundcloud", "player_hud"),
                INSTANCE);
    }

    // ── Size presets ──────────────────────────────────────────────────────────
    // { width, height-compact, height-full }
    private static final int[][] SIZES = {
            {180, 24, 46},   // S
            {250, 26, 52},   // M
            {330, 28, 58},   // L
    };
    private static final int PAD = 6;

    // ── Drag & interaction state ──────────────────────────────────────────────
    private boolean dragging     = false;
    private double  dragOffX     = 0;
    private double  dragOffY     = 0;
    private boolean draggingProg = false;

    private int lastX, lastY, lastW, lastH;
    private double mouseX = 0, mouseY = 0;
    private long startTime = System.currentTimeMillis();

    public void updateMouse(double mx, double my) {
        mouseX = mx;
        mouseY = my;
    }

    public static boolean isVisible() {
        return ModConfig.get().hudVisible;
    }

    public static void toggle() {
        ModConfig.get().hudVisible = !ModConfig.get().hudVisible;
        ModConfig.get().save();
    }

    public static void cycleSize() {
        ModConfig cfg = ModConfig.get();
        cfg.hudSize = (cfg.hudSize + 1) % 3;
        cfg.save();
    }

    public static void toggleButtons() {
        ModConfig cfg = ModConfig.get();
        cfg.hudButtons = !cfg.hudButtons;
        cfg.save();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        ModConfig cfg = ModConfig.get();
        if (!cfg.hudVisible) return;

        AudioPlayer player = AudioPlayer.getInstance();
        SoundCloudTrack track = player.getCurrentTrack();
        if (track == null || player.getState() == AudioPlayer.State.IDLE || player.getState() == AudioPlayer.State.STOPPED) {
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int[] sz = SIZES[Math.max(0, Math.min(2, cfg.hudSize))];
        int w = sz[0];
        int h = cfg.hudButtons ? sz[2] : sz[1];

        // Resolve coordinates
        int x, y;
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            x = Math.max(0, Math.min(sw - w, cfg.hudX));
            y = Math.max(0, Math.min(sh - h, cfg.hudY));
        } else {
            int margin = 8;
            x = switch (cfg.hudAnchorCol) {
                case 0 -> margin;
                case 2 -> sw - w - margin;
                default -> (sw - w) / 2;
            };
            y = switch (cfg.hudAnchorRow) {
                case 0 -> margin;
                case 1 -> (sh - h) / 2;
                default -> sh - h - margin;
            };
        }

        lastX = x; lastY = y; lastW = w; lastH = h;

        Font font = mc.font;
        long posMs = player.getPositionMs();
        int durMs = track.getDurationMs();
        float animSec = (System.currentTimeMillis() - startTime) / 1000f;
        boolean isPlaying = player.getState() == AudioPlayer.State.PLAYING;

        // 1. Sleek Card Container
        GuiHelper.drawRoundedCard(g, x, y, w, h, 0xE80F1522, GuiHelper.C_WINDOW_BORDER);
        g.fill(x + 2, y, x + w - 2, y + 1, GuiHelper.C_ACCENT);

        // 2. Title Row
        int titleY = y + 4;

        // Animated Equalizer or Play/Pause Icon
        if (isPlaying) {
            GuiHelper.drawEqualizer(g, x + PAD, titleY + 1, 10, animSec, GuiHelper.C_ACCENT);
        } else {
            g.drawString(font, "§6⏸", x + PAD, titleY + 1, GuiHelper.C_ACCENT, false);
        }

        // Time display on the right
        int ps = (int) (posMs / 1000);
        String timeStr = String.format("%d:%02d/%s", ps / 60, ps % 60, track.getFormattedDuration());
        int timeW = font.width(timeStr);
        g.drawString(font, "§8" + timeStr, x + w - timeW - PAD, titleY + 1, GuiHelper.C_TEXT_SUB, false);

        // Track title & artist (centred / clipped)
        int maxTitleW = w - PAD * 2 - 20 - timeW;
        int maxChars = Math.max(6, maxTitleW / 6);
        String titleStr = "§f" + trunc(track.getTitle(), maxChars / 2) + " §7" + trunc(track.getArtist(), maxChars / 2);
        g.drawString(font, titleStr, x + PAD + 16, titleY + 1, GuiHelper.C_TEXT_MAIN, false);

        // 3. Progress Bar
        int pbY = y + (cfg.hudButtons ? h - 22 : h - 7);
        int pbX = x + PAD;
        int pbW = w - PAD * 2;
        int pbH = 3;

        GuiHelper.drawRoundedCard(g, pbX, pbY, pbW, pbH, GuiHelper.C_PROG_TRACK, 0);
        if (durMs > 0) {
            int filled = Math.max(0, Math.min(pbW, (int) ((float) posMs / durMs * pbW)));
            g.fill(pbX, pbY, pbX + filled, pbY + pbH, GuiHelper.C_ACCENT);

            boolean hovPb = mouseY >= pbY - 3 && mouseY <= pbY + pbH + 3 && mouseX >= pbX && mouseX <= pbX + pbW;
            int thumbR = hovPb ? 3 : 2;
            g.fill(pbX + filled - thumbR, pbY - 1, pbX + filled + thumbR, pbY + pbH + 1, GuiHelper.C_TEXT_MAIN);
        }

        // 4. Buttons Row (FULL Mode)
        if (cfg.hudButtons) {
            int btnY = y + h - 16;
            int btnH = 12;
            int btnW = 18;
            int gap = 3;

            // 5 buttons: ⏮ ⏯ ⏭ 🔀 🔁
            int totalBtnW = 5 * btnW + 4 * gap;
            int startX = x + (w - totalBtnW) / 2;

            drawHudBtn(g, font, "⏮", startX, btnY, btnW, btnH, false);
            drawHudBtn(g, font, isPlaying ? "⏸" : "▶", startX + (btnW + gap), btnY, btnW, btnH, true);
            drawHudBtn(g, font, "⏭", startX + (btnW + gap) * 2, btnY, btnW, btnH, false);
            drawHudBtn(g, font, "🔀", startX + (btnW + gap) * 3, btnY, btnW, btnH, player.isShuffle());

            String repStr = player.getRepeat() == AudioPlayer.RepeatMode.ONE ? "🔂" : "🔁";
            drawHudBtn(g, font, repStr, startX + (btnW + gap) * 4, btnY, btnW, btnH, player.getRepeat() != AudioPlayer.RepeatMode.OFF);

            // Mini volume bar on the left
            int volW = Math.max(12, startX - x - 12);
            int volX = x + PAD;
            int volBarY = btnY + 4;
            GuiHelper.drawRoundedCard(g, volX, volBarY, volW, 3, GuiHelper.C_PROG_TRACK, 0);
            int volFill = (int) (player.getVolume() * volW);
            g.fill(volX, volBarY, volX + volFill, volBarY + 3, GuiHelper.C_ACCENT);
        }

        // Hover grip dots (visual drag indicator)
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (hov) {
            g.fill(x + w - 6, y + 3, x + w - 4, y + 5, 0x88FFFFFF);
            g.fill(x + w - 6, y + 6, x + w - 4, y + 8, 0x88FFFFFF);
        }
    }

    private void drawHudBtn(GuiGraphics g, Font font, String label, int bx, int by, int bw, int bh, boolean active) {
        boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = active ? GuiHelper.C_ACCENT : (hov ? GuiHelper.C_CARD_HOVER : GuiHelper.C_CARD_BG);
        int border = active ? GuiHelper.C_ACCENT_HOVER : (hov ? GuiHelper.C_WINDOW_BORDER : 0);

        GuiHelper.drawRoundedCard(g, bx, by, bw, bh, bg, border);
        int lw = font.width(label);
        g.drawString(font, label, bx + (bw - lw) / 2, by + 2, active ? 0xFFFFFFFF : GuiHelper.C_TEXT_MAIN, false);
    }

    // ── Mouse Handlers ────────────────────────────────────────────────────────

    public boolean onMousePress(double mx, double my, int btn) {
        if (!ModConfig.get().hudVisible) return false;
        if (mx < lastX || mx > lastX + lastW || my < lastY || my > lastY + lastH) return false;

        ModConfig cfg = ModConfig.get();
        AudioPlayer player = AudioPlayer.getInstance();
        SoundCloudTrack track = player.getCurrentTrack();

        int[] sz = SIZES[Math.max(0, Math.min(2, cfg.hudSize))];
        int pbY = lastY + (cfg.hudButtons ? sz[2] - 22 : sz[1] - 7);
        int pbX = lastX + PAD;
        int pbW = lastW - PAD * 2;

        // Progress bar seek
        if (my >= pbY - 3 && my <= pbY + 7 && track != null && track.getDurationMs() > 0) {
            float frac = (float) Math.max(0, Math.min(1, (mx - pbX) / pbW));
            player.seekToSeconds((int) (frac * track.getDurationMs() / 1000));
            draggingProg = true;
            return true;
        }

        // Buttons row
        if (cfg.hudButtons) {
            int btnY = lastY + lastH - 16;
            int btnH = 12;
            int btnW = 18;
            int gap = 3;
            int totalBtnW = 5 * btnW + 4 * gap;
            int startX = lastX + (lastW - totalBtnW) / 2;

            if (my >= btnY && my <= btnY + btnH) {
                // ⏮
                if (mx >= startX && mx <= startX + btnW) { player.previous(); return true; }
                // ⏯
                if (mx >= startX + (btnW + gap) && mx <= startX + (btnW + gap) + btnW) { player.pause(); return true; }
                // ⏭
                if (mx >= startX + (btnW + gap) * 2 && mx <= startX + (btnW + gap) * 2 + btnW) { player.next(); return true; }
                // 🔀
                if (mx >= startX + (btnW + gap) * 3 && mx <= startX + (btnW + gap) * 3 + btnW) { player.setShuffle(!player.isShuffle()); return true; }
                // 🔁
                if (mx >= startX + (btnW + gap) * 4 && mx <= startX + (btnW + gap) * 4 + btnW) { player.cycleRepeat(); return true; }
            }

            // Volume bar on left
            int volW = Math.max(12, startX - lastX - 12);
            int volX = lastX + PAD;
            if (mx >= volX && mx <= volX + volW && my >= btnY && my <= btnY + btnH) {
                float v = (float) (mx - volX) / volW;
                player.setVolume(v);
                return true;
            }
        }

        // Drag: top title area
        if (my <= lastY + lastH / 2) {
            dragging = true;
            dragOffX = mx - lastX;
            dragOffY = my - lastY;
            return true;
        }

        return true;
    }

    public boolean onMouseRelease(double mx, double my, int btn) {
        if (dragging) {
            dragging = false;
            ModConfig.get().save();
            return true;
        }
        if (draggingProg) {
            draggingProg = false;
            return true;
        }
        return false;
    }

    public boolean onMouseDrag(double mx, double my, int btn) {
        if (draggingProg) {
            AudioPlayer player = AudioPlayer.getInstance();
            SoundCloudTrack track = player.getCurrentTrack();
            if (track != null && track.getDurationMs() > 0) {
                int pbX = lastX + PAD, pbW = lastW - PAD * 2;
                float frac = (float) Math.max(0, Math.min(1, (mx - pbX) / pbW));
                player.seekToSeconds((int) (frac * track.getDurationMs() / 1000));
            }
            return true;
        }
        if (dragging) {
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            ModConfig cfg = ModConfig.get();
            cfg.hudX = (int) Math.max(0, Math.min(sw - lastW, mx - dragOffX));
            cfg.hudY = (int) Math.max(0, Math.min(sh - lastH, my - dragOffY));
            return true;
        }
        return false;
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(0, max - 1)) + "…" : s;
    }
}
