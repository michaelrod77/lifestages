package dev.lifestages.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * A flat front view of a 128 x 128 (HD) slim player-layout skin: head, body, arms and legs with their outer layers.
 * Used by the Mom picker and the "Meet your baby" screen.
 */
final class SkinDoll {
	private SkinDoll() {
	}

	/** Width of the doll on screen. */
	static int width(int scale) {
		return 14 * scale;
	}

	/** Height of the doll on screen (head, body and legs). */
	static int height(int scale) {
		return 32 * scale;
	}

	/** Draws the doll with its top centred at (cx, y), each skin pixel `scale` screen pixels. */
	static void draw(GuiGraphicsExtractor g, Identifier tex, int cx, int y, int scale) {
		int s = scale;
		int bodyX = cx - 4 * s, headY = y, bodyY = y + 8 * s, legY = bodyY + 12 * s;
		// base layer, then the outer layer on top
		part(g, tex, bodyX, headY, 8, 8, 8, 8, s);           // head front
		part(g, tex, bodyX, headY, 40, 8, 8, 8, s);          // hat
		part(g, tex, bodyX, bodyY, 20, 20, 8, 12, s);        // body
		part(g, tex, bodyX, bodyY, 20, 36, 8, 12, s);        // jacket
		part(g, tex, bodyX - 3 * s, bodyY, 44, 20, 3, 12, s); // right arm (viewer's left)
		part(g, tex, bodyX - 3 * s, bodyY, 44, 36, 3, 12, s);
		part(g, tex, bodyX + 8 * s, bodyY, 36, 52, 3, 12, s); // left arm
		part(g, tex, bodyX + 8 * s, bodyY, 52, 52, 3, 12, s);
		part(g, tex, bodyX, legY, 4, 20, 4, 12, s);           // right leg
		part(g, tex, bodyX, legY, 4, 36, 4, 12, s);
		part(g, tex, bodyX + 4 * s, legY, 20, 52, 4, 12, s);  // left leg
		part(g, tex, bodyX + 4 * s, legY, 4, 52, 4, 12, s);
	}

	/** One rectangle of the skin, given in 64 x 64 skin pixels. */
	private static void part(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h, int s) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u * 2, v * 2, w * s, h * s, w * 2, h * 2, 128, 128);
	}
}
