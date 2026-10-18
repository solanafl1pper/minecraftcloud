package solana.flipper.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modern visual styling and drawing utilities for CloudBeats GUI.
 */
public class GuiHelper {

    // ── Palette ───────────────────────────────────────────────────────────────
    public static final int C_BG_SCREEN     = 0xD00A0E17; // Dim world overlay
    public static final int C_WINDOW_BG     = 0xF8111622; // Main window background
    public static final int C_WINDOW_BORDER = 0xFF263043; // Window subtle outline
    public static final int C_HEADER_BG     = 0xFF161C2B; // Top header background
    public static final int C_BAR_BG        = 0xF8101420; // Bottom player dock background
    public static final int C_CARD_BG       = 0xFF181E2C; // Item normal card
    public static final int C_CARD_HOVER    = 0xFF222B3D; // Item hover card
    public static final int C_CARD_SELECTED = 0xFF2A2846; // Item selected card
    public static final int C_CARD_PLAYING  = 0xFF2B211D; // Current playing item card
    public static final int C_CARD_BORDER   = 0xFF232B3C; // Card divider/border

    public static final int C_ACCENT        = 0xFFFF5500; // SoundCloud primary orange
    public static final int C_ACCENT_HOVER  = 0xFFFF7324; // Lighter orange
    public static final int C_ACCENT_BG     = 0x33FF5500; // 20% accent tint
    public static final int C_ACCENT_BORDER = 0x88FF5500; // Semi-transparent accent outline

    public static final int C_TEXT_MAIN     = 0xFFF8FAFC; // White high contrast
    public static final int C_TEXT_SUB      = 0xFF94A3B8; // Slate gray
    public static final int C_TEXT_MUTED    = 0xFF64748B; // Muted gray
    public static final int C_GREEN         = 0xFF10B981; // Playing / active indicator
    public static final int C_RED           = 0xFFF43F5E; // Favorite / delete
    public static final int C_PROG_TRACK    = 0xFF252E3E; // Slider track background
    public static final int C_TOOLTIP_BG    = 0xF50D111A;
    public static final int C_TOOLTIP_BORDER= 0xFF334155;

    // ── Drawing primitives ───────────────────────────────────────────────────

    public static void drawBox(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        if (borderColor != 0) {
            drawBorder(g, x, y, w, h, borderColor);
        }
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);             // top
        g.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        g.fill(x, y + 1, x + 1, y + h - 1, color);     // left
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color); // right
    }

    /**
     * Draws a beveled rounded rectangle (smooth appearance in pixel GUIs).
     */
    public static void drawRoundedCard(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor) {
        if (w <= 4 || h <= 4) {
            drawBox(g, x, y, w, h, bgColor, borderColor);
            return;
        }

        // Inner fill: vertical body + side wings
        g.fill(x + 1, y, x + w - 1, y + h, bgColor);
        g.fill(x, y + 1, x + 1, y + h - 1, bgColor);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bgColor);

        if (borderColor != 0) {
            // Horizontal edges
            g.fill(x + 2, y, x + w - 2, y + 1, borderColor);
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, borderColor);
            // Vertical edges
            g.fill(x, y + 2, x + 1, y + h - 2, borderColor);
            g.fill(x + w - 1, y + 2, x + w, y + h - 2, borderColor);
            // Corners (1px dots)
            g.fill(x + 1, y + 1, x + 2, y + 2, borderColor);
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, borderColor);
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, borderColor);
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, borderColor);
        }
    }

    /**
     * Modern animated equalizer bars (3 or 4 vertical bars bouncing to time).
     */
    public static void drawEqualizer(GuiGraphics g, int x, int y, int h, float animSec, int color) {
        int barW = 2;
        int gap = 1;
        float[] phases = {0.0f, 1.8f, 3.4f, 5.1f};
        float[] speeds = {4.5f, 6.2f, 5.0f, 7.1f};

        for (int i = 0; i < 4; i++) {
            float wave = (float) (Math.sin(animSec * speeds[i] + phases[i]) * 0.5 + 0.5);
            int barH = Math.max(2, Math.min(h, (int) (wave * (h - 2) + 2)));
            int bx = x + i * (barW + gap);
            int by = y + h - barH;
            g.fill(bx, by, bx + barW, y + h, color);
        }
    }

    /**
     * Draws a slick pill button with optional icon/text.
     */
    public static void drawPillButton(GuiGraphics g, Font font, String text, int x, int y, int w, int h,
                                      boolean hovered, boolean active, int customAccent) {
        int bg = active ? (customAccent != 0 ? customAccent : C_ACCENT) : (hovered ? C_CARD_HOVER : C_CARD_BG);
        int border = active ? C_ACCENT_HOVER : (hovered ? C_WINDOW_BORDER : C_CARD_BORDER);
        int textColor = active ? 0xFFFFFFFF : (hovered ? C_TEXT_MAIN : C_TEXT_SUB);

        drawRoundedCard(g, x, y, w, h, bg, border);

        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, textColor, false);
    }

    /**
     * Compact clean tooltip renderer.
     */
    public static void drawTooltip(GuiGraphics g, Font font, String text, int mouseX, int mouseY, int screenW, int screenH) {
        if (text == null || text.isEmpty()) return;

        int tw = font.width(text);
        int th = 11;
        int tx = mouseX + 8;
        int ty = mouseY - th - 6;

        if (tx + tw + 6 > screenW) tx = screenW - tw - 8;
        if (tx < 4) tx = 4;
        if (ty < 4) ty = mouseY + 14;

        g.fill(tx - 3, ty - 2, tx + tw + 3, ty + th, C_TOOLTIP_BG);
        drawBorder(g, tx - 3, ty - 2, tw + 6, th + 2, C_TOOLTIP_BORDER);
        g.drawString(font, text, tx, ty + 1, C_TEXT_MAIN, false);
    }
}
