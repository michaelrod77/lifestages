package dev.lifestages.client.screen;

import dev.lifestages.LifeStages;
import dev.lifestages.life.Holidays;
import dev.lifestages.net.Payloads;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Halloween morning: pick a costume to wear over your skin for the day, or go without. */
public class CostumeScreen extends Screen {
	private static final int COUNT = Holidays.COSTUMES.length - 1, SCALE = 3, CARD_W = 64, CARD_H = 124;
	private int hovered = -1;
	private boolean sent;

	public CostumeScreen() {
		super(Component.literal("Happy Halloween!"));
	}

	private int left() {
		return width / 2 - (COUNT * CARD_W + (COUNT - 1) * 8) / 2;
	}

	private int top() {
		return Math.max(40, height / 2 - CARD_H / 2 - 10);
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("No costume"), b -> pick(0))
				.bounds(width / 2 - 60, top() + CARD_H + 10, 120, 20).build());
	}

	/** costume: 1-4, or 0 for none. */
	public void pick(int costume) {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new Payloads.PickCostume(costume));
		}
		onClose();
	}

	@Override
	public void onClose() {
		sent = true; // Esc means no costume
		super.onClose();
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
		for (int i = 0; i < COUNT; i++) {
			int x = left() + i * (CARD_W + 8), y = top();
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
		g.centeredText(font, "Happy Halloween!", width / 2, top() - 26, 0xFFF2A04A);
		g.centeredText(font, "Pick a costume to wear today.", width / 2, top() - 14, 0xFFB8B8C8);
		for (int i = 0; i < COUNT; i++) {
			int x = left() + i * (CARD_W + 8), y = top();
			boolean on = i == hovered;
			g.fill(x, y, x + CARD_W, y + CARD_H, on ? 0xC0F2A04A : 0x90000000);
			g.fill(x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 2, on ? 0xF0302418 : 0xC0181820);
			String name = Holidays.COSTUMES[i + 1];
			SkinDoll.draw(g, LifeStages.id("textures/entity/player_layer/costume_" + name.toLowerCase(Locale.ROOT) + "_slim.png"),
					x + CARD_W / 2, y + 8, SCALE);
			g.centeredText(font, name, x + CARD_W / 2, y + CARD_H - 13, on ? 0xFFFFE8C8 : 0xFFE0E0E0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
