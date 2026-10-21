package solana.flipper.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import solana.flipper.config.ModConfig;

/**
 * Screen to choose HUD screen anchor position from a 3x3 grid (1.18.2).
 */
public class HudPositionScreen extends Screen {

    private final Screen parent;
    private int selRow;
    private int selCol;

    public HudPositionScreen(Screen parent) {
        super(new TextComponent("HUD Position"));
        this.parent = parent;
        this.selRow = ModConfig.get().hudAnchorRow;
        this.selCol = ModConfig.get().hudAnchorCol;
    }

    @Override
    protected void init() {
        super.init();

        int doneW = 100, doneH = 20;
        addButton(new Button(width / 2 - doneW / 2, height / 2 + 75, doneW, doneH, new TextComponent("§aDone"), b -> {
            minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(PoseStack poseStack, int mx, int my, float delta) {
        renderBackground(poseStack);
        GuiGraphics g = new GuiGraphics(poseStack);

        Font font = minecraft.font;

        // Title
        g.drawCenteredString(font, "§6§lChoose HUD Screen Anchor", width / 2, height / 2 - 95, GuiHelper.C_TEXT_MAIN);
        g.drawCenteredString(font, "§7Click any of the 9 anchor points below", width / 2, height / 2 - 80, GuiHelper.C_TEXT_SUB);

        // 3x3 Grid
        int cellW = 80, cellH = 30, gap = 6;
        int gridW = cellW * 3 + gap * 2;
        int gridH = cellH * 3 + gap * 2;
        int gx = (width - gridW) / 2;
        int gy = height / 2 - gridH / 2 - 14;

        String[][] labels = {
                {"↖ Top-Left", "↑ Top-Center", "↗ Top-Right"},
                {"← Center-L", "• Center",     "→ Center-R"},
                {"↙ Bot-Left", "↓ Bot-Center", "↘ Bot-Right"},
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

        super.render(poseStack, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
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
                    cfg.hudY = -1;
                    cfg.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
