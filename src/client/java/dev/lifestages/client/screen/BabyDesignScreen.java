package dev.lifestages.client.screen;

import dev.lifestages.LifeStages;
import dev.lifestages.entity.BabyEntity;
import dev.lifestages.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * "Meet your baby": shown right after a Baby Spawn Egg hatches. Change the suggested name (or roll a new one) and
 * pick one of the four looks. Done, or simply closing the screen, keeps what's chosen.
 */
public class BabyDesignScreen extends Screen {
	private static final int LOOKS = 4, SCALE = 3, CARD_W = 62, CARD_H = 122;
	private final int entityId;
	private String name;
	private int look;
	private EditBox nameBox;
	private boolean sent;

	public BabyDesignScreen(int entityId, String name, int look) {
		super(Component.literal("Meet your baby"));
		this.entityId = entityId;
		this.name = name;
		this.look = Math.max(1, Math.min(LOOKS, look));
	}

	private int top() {
		return Math.max(62, height / 2 - CARD_H / 2);
	}

	private int left() {
		return width / 2 - (LOOKS * CARD_W + (LOOKS - 1) * 8) / 2;
	}

	@Override
	protected void init() {
		int y = top() - 30;
		nameBox = new EditBox(font, width / 2 - 100, y, 150, 20, Component.literal("Name"));
		nameBox.setMaxLength(24);
		nameBox.setValue(name);
		nameBox.setResponder(v -> name = v);
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
		addRenderableWidget(Button.builder(Component.literal("Random"), b -> {
			String[] names = BabyEntity.NAMES;
			String next = name;
			while (next.equals(name)) {
				next = names[(int) (Math.random() * names.length)];
			}
			nameBox.setValue(next);
		}).bounds(width / 2 + 54, y, 46, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Welcome home!"), b -> onClose())
				.bounds(width / 2 - 60, top() + CARD_H + 10, 120, 20).build());
	}

	/** Chooses one of the looks (1-4). */
	public void pickLook(int which) {
		look = Math.max(1, Math.min(LOOKS, which));
	}

	/** Types a name, as if into the box. */
	public void setName(String value) {
		nameBox.setValue(value);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = cardAt(event.x(), event.y());
		if (i >= 0) {
			look = i + 1;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int cardAt(double mx, double my) {
		int y = top();
		for (int i = 0; i < LOOKS; i++) {
			int x = left() + i * (CARD_W + 8);
			if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractBackground(g, mouseX, mouseY, partial);
		g.centeredText(font, "Meet your baby!", width / 2, top() - 54, 0xFFFFFFFF);
		g.centeredText(font, "Pick a name and how they look.", width / 2, top() - 43, 0xFFB8B8C8);
		int hovered = cardAt(mouseX, mouseY);
		int y = top();
		for (int i = 0; i < LOOKS; i++) {
			int x = left() + i * (CARD_W + 8);
			boolean chosen = look == i + 1;
			g.fill(x, y, x + CARD_W, y + CARD_H, chosen ? 0xFFF2A2BE : i == hovered ? 0xC0F2A2BE : 0x90000000);
			g.fill(x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 2, chosen ? 0xF0302838 : 0xC0181820);
			SkinDoll.draw(g, LifeStages.id("textures/entity/baby/baby_" + (i + 1) + ".png"), x + CARD_W / 2, y + 8, SCALE);
			g.centeredText(font, chosen ? "Chosen" : "Look " + (i + 1), x + CARD_W / 2, y + CARD_H - 13, chosen ? 0xFFFFE0EC : 0xFFE0E0E0);
		}
	}

	@Override
	public void onClose() {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new Payloads.NameBaby(entityId, name, look));
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
