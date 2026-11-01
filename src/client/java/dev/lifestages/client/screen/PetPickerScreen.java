package dev.lifestages.client.screen;

import dev.lifestages.client.render.PetCats;
import dev.lifestages.life.Pets;
import dev.lifestages.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.feline.Cat;

/**
 * After choosing to be a Pet Cat: pick one of the eleven cats, shown as real cats that turn to follow the pointer.
 * "Surprise me" picks one at random. Can't be closed without choosing.
 */
public class PetPickerScreen extends Screen {
	private static final int PER_ROW = 6, CARD_W = 58, CARD_H = 62, GAP = 6;
	private final Optional<UUID> family;
	private final List<Cat> cats = new ArrayList<>();
	private int hovered = -1;

	public PetPickerScreen(Optional<UUID> family) {
		super(Component.literal("Choose your cat"));
		this.family = family;
	}

	@Override
	protected void init() {
		if (cats.isEmpty() && minecraft.level != null) {
			for (int i = 0; i < Pets.NAMES.length; i++) {
				cats.add(PetCats.make(minecraft.level, i));
			}
		}
		addRenderableWidget(Button.builder(Component.literal("Surprise me"), b -> pick(0))
				.bounds(width / 2 - 60, top() + 2 * (CARD_H + GAP) + 8, 120, 20).build());
	}

	private int top() {
		return Math.max(36, height / 2 - CARD_H - 20);
	}

	private int cardX(int i) {
		int row = i / PER_ROW, inRow = row == 0 ? PER_ROW : Pets.NAMES.length - PER_ROW;
		int rowW = inRow * CARD_W + (inRow - 1) * GAP;
		return width / 2 - rowW / 2 + (i % PER_ROW) * (CARD_W + GAP);
	}

	private int cardY(int i) {
		return top() + (i / PER_ROW) * (CARD_H + GAP);
	}

	/** look: 1-11, or 0 for a surprise. */
	public void pick(int look) {
		ClientPlayNetworking.send(new Payloads.Choose(5, family, look));
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
		for (int i = 0; i < Pets.NAMES.length; i++) {
			int x = cardX(i), y = cardY(i);
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
		g.centeredText(font, "Choose your cat", width / 2, top() - 22, 0xFFFFFFFF);
		g.centeredText(font, "You'll start as a Kitten and grow into a Cat.", width / 2, top() - 11, 0xFFB8B8C8);
		for (int i = 0; i < Pets.NAMES.length; i++) {
			int x = cardX(i), y = cardY(i);
			boolean on = i == hovered;
			g.fill(x, y, x + CARD_W, y + CARD_H, on ? 0xC0E8C07A : 0x90000000);
			g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, on ? 0xE0302820 : 0xC0181820);
			Cat cat = i < cats.size() ? cats.get(i) : null;
			if (cat != null) {
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 14, 40, 0.0625f,
						mouseX, mouseY, cat);
			}
			g.centeredText(font, Pets.NAMES[i].replace("British ", "Brit. "), x + CARD_W / 2, y + CARD_H - 11, on ? 0xFFFFF0D8 : 0xFFE0E0E0);
		}
	}
}
