package dev.lifestages.client.screen;

import dev.lifestages.LifeStages;
import dev.lifestages.net.Payloads;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * After choosing to start as a Baby: pick which of the six Moms will look after you. Each card shows the Mom's
 * skin as a front view. "Surprise me" picks one at random. Can't be closed without choosing.
 */
public class MomPickerScreen extends Screen {
	private static final int COUNT = 6, SCALE = 3, CARD_W = 62, CARD_H = 128;
	private final Optional<UUID> parent;
	private int hovered = -1;

	public MomPickerScreen(Optional<UUID> parent) {
		super(Component.literal("Choose your Mom"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Surprise me"), b -> pick(0))
				.bounds(width / 2 - 60, top() + CARD_H + 12, 120, 20).build());
	}

	private int left() {
		return width / 2 - (COUNT * CARD_W + (COUNT - 1) * 6) / 2;
	}

	private int top() {
		return Math.max(30, height / 2 - CARD_H / 2 - 16);
	}

	private void pick(int look) {
		ClientPlayNetworking.send(new Payloads.Choose(2, parent, look));
		onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = cardAt(event.x(), event.y());
		if (i >= 0) {
			pick(i + 1);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int cardAt(double mx, double my) {
		int y = top();
		for (int i = 0; i < COUNT; i++) {
			int x = left() + i * (CARD_W + 6);
			if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractBackground(g, mouseX, mouseY, partial);
		hovered = cardAt(mouseX, mouseY);
		g.centeredText(font, "Choose your Mom", width / 2, top() - 22, 0xFFFFFFFF);
		g.centeredText(font, "She'll look after you for your whole life.", width / 2, top() - 11, 0xFFB8B8C8);
		int y = top();
		for (int i = 0; i < COUNT; i++) {
			int x = left() + i * (CARD_W + 6);
			g.fill(x, y, x + CARD_W, y + CARD_H, i == hovered ? 0xC0F2A2BE : 0x90000000);
			g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, i == hovered ? 0xE0302838 : 0xC0181820);
			doll(g, LifeStages.id("textures/entity/mom/mom_" + (i + 1) + ".png"), x + CARD_W / 2, y + 8);
			g.centeredText(font, "Mom " + (i + 1), x + CARD_W / 2, y + CARD_H - 12, i == hovered ? 0xFFFFE0EC : 0xFFE0E0E0);
		}
	}

	/**
	 * A flat front view of a 128 x 128 (HD) slim player skin: head, body, arms and legs with their outer layers,
	 * each part drawn SCALE screen pixels per skin pixel (1 skin pixel = 2 texture pixels).
	 */
	private static void doll(GuiGraphicsExtractor g, Identifier tex, int cx, int y) {
		int s = SCALE;
		int bodyX = cx - 4 * s, headY = y, bodyY = y + 8 * s, legY = bodyY + 12 * s;
		// base layer, then the outer layer on top
		part(g, tex, bodyX, headY, 8, 8, 8, 8);           // head front
		part(g, tex, bodyX, headY, 40, 8, 8, 8);          // hat
		part(g, tex, bodyX, bodyY, 20, 20, 8, 12);        // body
		part(g, tex, bodyX, bodyY, 20, 36, 8, 12);        // jacket
		part(g, tex, bodyX - 3 * s, bodyY, 44, 20, 3, 12); // right arm (viewer's left)
		part(g, tex, bodyX - 3 * s, bodyY, 44, 36, 3, 12);
		part(g, tex, bodyX + 8 * s, bodyY, 36, 52, 3, 12); // left arm
		part(g, tex, bodyX + 8 * s, bodyY, 52, 52, 3, 12);
		part(g, tex, bodyX, legY, 4, 20, 4, 12);           // right leg
		part(g, tex, bodyX, legY, 4, 36, 4, 12);
		part(g, tex, bodyX + 4 * s, legY, 20, 52, 4, 12);  // left leg
		part(g, tex, bodyX + 4 * s, legY, 4, 52, 4, 12);
	}

	/** One rectangle of the skin, given in 64 x 64 skin pixels. */
	private static void part(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u * 2, v * 2, w * SCALE, h * SCALE, w * 2, h * 2, 128, 128);
	}
}
