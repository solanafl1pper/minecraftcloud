package solana.flipper.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

/**
 * Backwards compatibility wrapper emulating GuiGraphics for Minecraft 1.19.2 & 1.18.2.
 */
public class GuiGraphics {

    private final PoseStack poseStack;

    public GuiGraphics(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack pose() {
        return poseStack;
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        GuiComponent.fill(poseStack, minX, minY, maxX, maxY, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(poseStack, text, (float) x, (float) y, color);
        } else {
            return font.draw(poseStack, text, (float) x, (float) y, color);
        }
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(poseStack, font, text, x, y, color);
    }
}
