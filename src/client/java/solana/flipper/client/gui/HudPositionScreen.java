package solana.flipper.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import solana.flipper.config.ModConfig;

/**
 * 3x3 grid to pick HUD anchor + size/buttons toggles.
 */
public class HudPositionScreen extends Screen {

    private static final int C_BG    = 0xFF111827;
    private static final int C_ACC   = 0xFFFF5500;
    private static final int C_TEXT  = 0xFFFFFFFF;
    private static final int C_SUB   = 0xFF9CA3AF;
    private static final int C_CELL  = 0xFF1F2937;
    private static final int C_HOV   = 0xFF374151;
    private static final int C_SEL   = 0xFFFF5500;

    private final Screen parent;
    private int selRow, selCol;

    public HudPositionScreen(Screen parent) {
        super(Component.literal("HUD Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ModConfig cfg = ModConfig.get();
        selRow = cfg.hudAnchorRow;
        selCol = cfg.hudAnchorCol;

        // Buttons below the grid — we'll place them after knowing grid bounds
        // Grid: 3x3, cellW=70, cellH=30, gap=4 → gridW=218, gridH=98
        int cellW = 70, cellH = 30, gap = 4;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 10;

        int btnY = gy + gridH + 14;
        int btnW = 90, btnH = 20;
        int cx   = width / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Size: " + sizeName()),
                btn -> {
                    PlayerHud.cycleSize();
                    btn.setMessage(Component.literal("Size: " + sizeName()));
                }).bounds(cx - btnW - 4, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal(ModConfig.get().hudButtons ? "Buttons: ON" : "Buttons: OFF"),
                btn -> {
                    PlayerHud.toggleButtons();
                    btn.setMessage(Component.literal(ModConfig.get().hudButtons ? "Buttons: ON" : "Buttons: OFF"));
                }).bounds(cx + 4, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("Done"),
                btn -> minecraft.setScreen(parent))
                .bounds(cx - 40, btnY + 26, 80, 20).build());
    }

    private String sizeName() {
        return switch (ModConfig.get().hudSize) { case 0 -> "S"; case 2 -> "L"; default -> "M"; };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // Background
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, width, 2, C_ACC);

        // Title
        g.drawCenteredString(font, "HUD Position", width / 2, 10, C_TEXT);
        g.drawCenteredString(font, "Click a cell to set HUD anchor", width / 2, 22, C_SUB);

        // Grid
        int cellW = 70, cellH = 30, gap = 4;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 10;

        String[][] labels = {
            {"Top-Left",  "Top",    "Top-Right"},
            {"Mid-Left",  "Centre", "Mid-Right"},
            {"Bot-Left",  "Bottom", "Bot-Right"},
        };

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx2 = gx + col * (cellW + gap);
                int cy2 = gy + row * (cellH + gap);
                boolean sel   = row == selRow && col == selCol;
                boolean hover = mx >= cx2 && mx <= cx2 + cellW && my >= cy2 && my <= cy2 + cellH;

                // Cell background
                g.fill(cx2, cy2, cx2 + cellW, cy2 + cellH, sel ? C_SEL : (hover ? C_HOV : C_CELL));
                // Top border on selected
                if (sel) g.fill(cx2, cy2, cx2 + cellW, cy2 + 2, 0xFFFFFFFF);

                // Label centred in cell
                String lbl = labels[row][col];
                int lw = font.width(lbl);
                g.drawString(font, lbl, cx2 + (cellW - lw) / 2, cy2 + (cellH - 8) / 2, sel ? 0xFFFFFFFF : C_SUB);
            }
        }

        // Section label above buttons
        int btnY = gy + gridH + 6;
        g.drawCenteredString(font, "HUD Options", width / 2, btnY, C_SUB);

        // Widgets (buttons)
        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();

        int cellW = 70, cellH = 30, gap = 4;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 10;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = gx + col * (cellW + gap);
                int cy = gy + row * (cellH + gap);
                if (mx >= cx && mx <= cx + cellW && my >= cy && my <= cy + cellH) {
                    selRow = row; selCol = col;
                    ModConfig cfg = ModConfig.get();
                    cfg.hudAnchorRow = row;
                    cfg.hudAnchorCol = col;
                    cfg.hudX = -1; cfg.hudY = -1; // reset manual drag
                    cfg.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
