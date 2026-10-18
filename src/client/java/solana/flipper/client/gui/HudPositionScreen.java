package solana.flipper.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import solana.flipper.config.ModConfig;

/**
 * Modernized HUD Position and Layout Settings Screen.
 */
public class HudPositionScreen extends Screen {

    private final Screen parent;
    private int selRow, selCol;

    public HudPositionScreen(Screen parent) {
        super(Component.literal("HUD Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ModConfig cfg = ModConfig.get();
        selRow = cfg.hudAnchorRow;
        selCol = cfg.hudAnchorCol;

        int cellW = 80, cellH = 30, gap = 6;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gy = height / 2 - gridH / 2 - 14;

        int btnY = gy + gridH + 18;
        int btnW = 95, btnH = 20;
        int cx = width / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Size: " + sizeName()),
                btn -> {
                    PlayerHud.cycleSize();
                    btn.setMessage(Component.literal("Size: " + sizeName()));
                }).bounds(cx - btnW - 4, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal(ModConfig.get().hudButtons ? "Full HUD" : "Compact HUD"),
                btn -> {
                    PlayerHud.toggleButtons();
                    btn.setMessage(Component.literal(ModConfig.get().hudButtons ? "Full HUD" : "Compact HUD"));
                }).bounds(cx + 4, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("Done"),
                btn -> minecraft.setScreen(parent))
                .bounds(cx - 45, btnY + 26, 90, 20).build());
    }

    private String sizeName() {
        return switch (ModConfig.get().hudSize) {
            case 0 -> "S";
            case 2 -> "L";
            default -> "M";
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // Dark translucent ambient background
        g.fill(0, 0, width, height, GuiHelper.C_BG_SCREEN);
        g.fill(0, 0, width, 2, GuiHelper.C_ACCENT);

        // Header
        g.drawCenteredString(font, "§f§lHUD Position & Style", width / 2, 14, GuiHelper.C_TEXT_MAIN);
        g.drawCenteredString(font, "§7Click any grid anchor to lock the player overlay position", width / 2, 28, GuiHelper.C_TEXT_SUB);

        // Grid Container
        int cellW = 80, cellH = 30, gap = 6;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 14;

        GuiHelper.drawRoundedCard(g, gx - 12, gy - 10, gridW + 24, gridH + 20,
                GuiHelper.C_WINDOW_BG, GuiHelper.C_WINDOW_BORDER);

        String[][] labels = {
                {"Top-Left", "Top", "Top-Right"},
                {"Mid-Left", "Center", "Mid-Right"},
                {"Bot-Left", "Bottom", "Bot-Right"},
        };

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx2 = gx + col * (cellW + gap);
                int cy2 = gy + row * (cellH + gap);
                boolean sel = (row == selRow && col == selCol);
                boolean hover = (mx >= cx2 && mx <= cx2 + cellW && my >= cy2 && my <= cy2 + cellH);

                GuiHelper.drawPillButton(g, font, labels[row][col], cx2, cy2, cellW, cellH,
                        hover, sel, GuiHelper.C_ACCENT);
            }
        }

        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();

        int cellW = 80, cellH = 30, gap = 6;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 14;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = gx + col * (cellW + gap);
                int cy = gy + row * (cellH + gap);
                if (mx >= cx && mx <= cx + cellW && my >= cy && my <= cy + cellH) {
                    selRow = row;
                    selCol = col;
                    ModConfig cfg = ModConfig.get();
                    cfg.hudAnchorRow = row;
                    cfg.hudAnchorCol = col;
                    cfg.hudX = -1;
                    cfg.hudY = -1; // Reset manual drag coordinates
                    cfg.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
