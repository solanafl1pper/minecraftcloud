package solana.flipper.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import solana.flipper.api.SoundCloudTrack;
import solana.flipper.client.audio.AudioPlayer;
import solana.flipper.config.ModConfig;

/**
 * In-game mini player HUD.
 *
 * Two modes (toggle with H):
 *   COMPACT  — just title + progress bar, no buttons
 *   FULL     — title + progress + ⏮ ⏯ ⏭ 🔀 🔁 Vol
 *
 * Three sizes: S (180px), M (260px), L (340px)
 * Draggable: hold left-click on the title area and drag.
 * Position saved to config.
 *
 * Keybind H  → toggle visible
 * Keybind H (long / double) → not needed; use HudEditScreen (open via screen)
 */
public class PlayerHud implements HudElement {

    // ── singleton ─────────────────────────────────────────────────────────────
    private static PlayerHud INSTANCE;

    public static PlayerHud getInstance() { return INSTANCE; }

    public static void register() {
        INSTANCE = new PlayerHud();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("soundcloud", "player_hud"),
                INSTANCE);
    }

    // ── palette ───────────────────────────────────────────────────────────────
    private static final int C_BG      = 0xD00F172A;
    private static final int C_BORDER  = 0xFF1E293B;
    private static final int C_ACCENT  = 0xFFFF5500;
    private static final int C_ACCENT2 = 0xFFFF7733;
    private static final int C_TEXT    = 0xFFFFFFFF;
    private static final int C_SUB     = 0xFF94A3B8;
    private static final int C_PROG    = 0xFF334155;
    private static final int C_BTN     = 0xFF1E293B;
    private static final int C_BTN_HOV = 0xFF334155;
    private static final int C_BTN_ACT = 0xFF7C3AED;
    private static final int C_GREEN   = 0xFF22C55E;

    // ── size presets ──────────────────────────────────────────────────────────
    // { width, height-compact, height-full }
    private static final int[][] SIZES = {
            {180, 22, 42},   // S
            {260, 24, 48},   // M
            {340, 26, 54},   // L
    };
    private static final int PAD = 5;

    // ── drag state ────────────────────────────────────────────────────────────
    private boolean dragging     = false;
    private double  dragOffX     = 0;
    private double  dragOffY     = 0;
    private boolean draggingProg = false;

    // ── last rendered bounds (for hit-testing) ────────────────────────────────
    private int lastX, lastY, lastW, lastH;

    // ── mouse position (updated each tick from SoundcloudClient) ─────────────
    private double mouseX = 0, mouseY = 0;

    public void updateMouse(double mx, double my) { mouseX = mx; mouseY = my; }

    // ── public API ────────────────────────────────────────────────────────────

    public static boolean isVisible()  { return ModConfig.get().hudVisible; }
    public static void toggle()        { ModConfig.get().hudVisible = !ModConfig.get().hudVisible; ModConfig.get().save(); }

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

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        ModConfig cfg = ModConfig.get();
        if (!cfg.hudVisible) return;

        AudioPlayer player = AudioPlayer.getInstance();
        SoundCloudTrack track = player.getCurrentTrack();
        if (track == null
                || player.getState() == AudioPlayer.State.IDLE
                || player.getState() == AudioPlayer.State.STOPPED) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int[] sz  = SIZES[Math.max(0, Math.min(2, cfg.hudSize))];
        int   w   = sz[0];
        int   h   = cfg.hudButtons ? sz[2] : sz[1];

        // Resolve position from anchor (or manual drag override)
        int x, y;
        if (cfg.hudX >= 0 && cfg.hudY >= 0) {
            // Manual drag position
            x = Math.max(0, Math.min(sw - w, cfg.hudX));
            y = Math.max(0, Math.min(sh - h, cfg.hudY));
        } else {
            // Anchor-based position
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

        var font = mc.font;
        long posMs = player.getPositionMs();
        int  durMs = track.getDurationMs();

        // ── Background ───────────────────────────────────────────────────────
        // Outer border
        g.fill(x,     y,     x + w,     y + h,     C_BORDER);
        // Inner bg
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_BG);
        // Top accent line
        g.fill(x + 1, y + 1, x + w - 1, y + 2, C_ACCENT);

        // ── Title row ────────────────────────────────────────────────────────
        int titleY = y + 4;
        int scale  = cfg.hudSize;   // 0/1/2 → font scale stays 1, just more chars

        // State icon (left)
        String icon = switch (player.getState()) {
            case PLAYING -> "§a>";
            case PAUSED  -> "§e||";
            case LOADING -> "§e..";
            default      -> "§7[]";
        };
        g.drawString(font, icon, x + PAD, titleY, C_TEXT, false);

        // Time (right)
        int ps = (int)(posMs / 1000);
        String timeStr = String.format("%d:%02d/%s", ps / 60, ps % 60, track.getFormattedDuration());
        int timeW = font.width(timeStr);
        g.drawString(font, "§8" + timeStr, x + w - timeW - PAD, titleY, C_SUB, false);

        // Title + artist (centre, clipped)
        int titleMaxW = w - PAD * 2 - 14 - timeW - 4;
        int maxChars  = Math.max(8, titleMaxW / 6);
        String titleStr = "§f" + trunc(track.getTitle(), maxChars / 2)
                + " §7· §e" + trunc(track.getArtist(), maxChars / 2);
        g.drawCenteredString(font, titleStr, x + w / 2, titleY, C_TEXT);

        // ── Progress bar ─────────────────────────────────────────────────────
        int pbY = y + h - (cfg.hudButtons ? sz[2] - sz[1] + 5 : 6);
        int pbX = x + PAD;
        int pbW = w - PAD * 2;
        int pbH = 3;

        g.fill(pbX, pbY, pbX + pbW, pbY + pbH, C_PROG);
        if (durMs > 0) {
            int filled = (int)((float) posMs / durMs * pbW);
            filled = Math.max(0, Math.min(pbW, filled));
            g.fill(pbX, pbY, pbX + filled, pbY + pbH, C_ACCENT);
            // Thumb
            boolean hovPb = mouseY >= pbY - 3 && mouseY <= pbY + pbH + 3
                    && mouseX >= pbX && mouseX <= pbX + pbW;
            int thumbR = hovPb ? 3 : 2;
            g.fill(pbX + filled - thumbR, pbY - 1,
                   pbX + filled + thumbR, pbY + pbH + 1, C_ACCENT2);
        }

        // ── Buttons row (FULL mode only) ──────────────────────────────────────
        if (cfg.hudButtons) {
            int btnY  = y + h - 14;
            int btnH  = 11;
            int btnW  = 20;   // all same width
            int gap   = 2;
            // 5 buttons: |<  ||/>  >|  Shuf  Rep
            // total = 5*20 + 4*2 = 108, centred
            int startX = x + (w - (5 * btnW + 4 * gap)) / 2;

            drawBtn(g, font, "|<",  startX,                    btnY, btnW, btnH, false);
            drawBtn(g, font, player.getState() == AudioPlayer.State.PLAYING ? "||" : ">",
                                    startX + (btnW + gap),     btnY, btnW, btnH, false);
            drawBtn(g, font, ">|",  startX + (btnW + gap) * 2, btnY, btnW, btnH, false);
            drawBtn(g, font, "Shf", startX + (btnW + gap) * 3, btnY, btnW, btnH, player.isShuffle());
            drawBtn(g, font,
                    player.getRepeat() == AudioPlayer.RepeatMode.ONE ? "R:1" :
                    player.getRepeat() == AudioPlayer.RepeatMode.ALL ? "R:A" : "Rep",
                    startX + (btnW + gap) * 4, btnY, btnW, btnH,
                    player.getRepeat() != AudioPlayer.RepeatMode.OFF);

            // Volume slider — left side, same row
            int volX   = x + 4;
            int volW2  = startX - x - 8;
            if (volW2 > 10) {
                int volBarY = btnY + 4;
                g.fill(volX, volBarY, volX + volW2, volBarY + 3, C_PROG);
                g.fill(volX, volBarY, volX + (int)(player.getVolume() * volW2), volBarY + 3, C_ACCENT2);
            }
        }

        // ── Drag handle indicator (top-right corner) ──────────────────────────
        boolean hovHud = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (hovHud) {
            // Show resize/drag hint dots
            g.fill(x + w - 5, y + 3, x + w - 3, y + 5, 0x88FFFFFF);
            g.fill(x + w - 5, y + 6, x + w - 3, y + 8, 0x88FFFFFF);
        }

        // ── Shuffle/Repeat dots ───────────────────────────────────────────────
        if (player.isShuffle())
            g.fill(x + 2, y + h - 3, x + 4, y + h - 1, C_GREEN);
        if (player.getRepeat() != AudioPlayer.RepeatMode.OFF)
            g.fill(x + w - 4, y + h - 3, x + w - 2, y + h - 1, C_GREEN);
    }

    private void drawBtn(GuiGraphics g, net.minecraft.client.gui.Font font,
                         String label, int bx, int by, int bw, int bh, boolean active) {
        boolean hov = mouseX >= bx && mouseX <= bx + bw
                && mouseY >= by && mouseY <= by + bh;
        int bg = active ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN);
        g.fill(bx, by, bx + bw, by + bh, bg);
        g.fill(bx, by, bx + bw, by + 1, active ? C_ACCENT : 0xFF334155);
        int lw = font.width(label);
        g.drawString(font, "§f" + label, bx + (bw - lw) / 2, by + 2, C_TEXT, false);
    }

    // ── Mouse event handlers (called from SoundcloudClient) ───────────────────

    public boolean onMousePress(double mx, double my, int btn) {
        if (!ModConfig.get().hudVisible) return false;
        if (mx < lastX || mx > lastX + lastW || my < lastY || my > lastY + lastH) return false;

        ModConfig cfg = ModConfig.get();
        AudioPlayer player = AudioPlayer.getInstance();
        SoundCloudTrack track = player.getCurrentTrack();

        // Progress bar seek
        int[] sz  = SIZES[Math.max(0, Math.min(2, cfg.hudSize))];
        int   pbY = lastY + lastH - (cfg.hudButtons ? sz[2] - sz[1] + 5 : 6);
        int   pbX = lastX + PAD;
        int   pbW = lastW - PAD * 2;

        if (my >= pbY - 3 && my <= pbY + 6 && track != null && track.getDurationMs() > 0) {
            float frac = (float) Math.max(0, Math.min(1, (mx - pbX) / pbW));
            player.seekToSeconds((int)(frac * track.getDurationMs() / 1000));
            draggingProg = true;
            return true;
        }

        // Buttons row
        if (cfg.hudButtons) {
            int btnY = lastY + lastH - 16;
            int btnH = 12;
            int btnW = 18;
            int cx   = lastX + lastW / 2;

            if (my >= btnY && my <= btnY + btnH) {
                // |<  prev
                if (mx >= cx - btnW * 3 - 4 && mx <= cx - btnW * 2 - 4) { player.previous(); return true; }
                // ||/>  pause
                if (mx >= cx - btnW - 2     && mx <= cx + 6)             { player.pause();    return true; }
                // >|  next
                if (mx >= cx + 6            && mx <= cx + btnW + 6)      { player.next();     return true; }
                // Shuf
                if (mx >= cx + btnW + 10    && mx <= cx + btnW * 2 + 16) { player.setShuffle(!player.isShuffle()); return true; }
                // Rep
                if (mx >= cx + btnW * 2 + 20 && mx <= cx + btnW * 3 + 30) { player.cycleRepeat(); return true; }
            }
        }

        // Drag: title area (top part)
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
        if (draggingProg) { draggingProg = false; return true; }
        return false;
    }

    public boolean onMouseDrag(double mx, double my, int btn) {
        if (draggingProg) {
            AudioPlayer player = AudioPlayer.getInstance();
            SoundCloudTrack track = player.getCurrentTrack();
            if (track != null && track.getDurationMs() > 0) {
                int pbX = lastX + PAD, pbW = lastW - PAD * 2;
                float frac = (float) Math.max(0, Math.min(1, (mx - pbX) / pbW));
                player.seekToSeconds((int)(frac * track.getDurationMs() / 1000));
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

    // ── util ──────────────────────────────────────────────────────────────────

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
