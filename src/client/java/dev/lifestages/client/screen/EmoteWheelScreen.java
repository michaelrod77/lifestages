package dev.lifestages.client.screen;

import dev.lifestages.LifeStages;
import dev.lifestages.life.Emotes;
import dev.lifestages.life.Life;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Hold the emote key, point at an emote, release (or click) to do it. Releasing or clicking in the middle repeats
 * the last emote, so crying again and again is just G, G, G. Emotes still cooling down are greyed out with the
 * seconds left.
 */
public class EmoteWheelScreen extends Screen {
	private static final String[] NAMES = {"Cry", "Fart", "Be cute", "Poop"};
	private static final String[] CAT_NAMES = {"Meow", "Hiss", "Purr", ""};
	private static final Identifier[] CAT_ICONS = {
			LifeStages.id("textures/gui/emote/meow.png"), LifeStages.id("textures/gui/emote/hiss.png"),
			LifeStages.id("textures/gui/emote/purr.png"), LifeStages.id("textures/gui/emote/purr.png")};
	private static final Identifier[] ICONS = {
			LifeStages.id("textures/gui/emote/cry.png"), LifeStages.id("textures/gui/emote/fart.png"),
			LifeStages.id("textures/gui/emote/cute.png"), LifeStages.id("textures/gui/emote/poop.png")};
	/** top, right, left, bottom */
	private static final int[][] DIRS = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}};
	/** Game time each emote is ready again, mirrored from the server's cooldowns. */
	private static final long[] READY_AT = new long[4];
	private static int last = 0;
	private final KeyMapping holdKey;
	private int hovered = -1;

	public EmoteWheelScreen(KeyMapping holdKey) {
		super(Component.literal("Emotes"));
		this.holdKey = holdKey;
	}

	@Override
	protected void init() {
		hovered = available(last) ? last : -1; // the pointer starts in the middle
	}

	private boolean available(int i) {
		Stage s = minecraft.player == null ? Stage.ADULT : Life.stage(minecraft.player);
		return i != 3 || s.wearsDiaper();
	}

	private boolean cat() {
		return minecraft.player != null && Life.stage(minecraft.player).isPet();
	}

	private String name(int i) {
		return cat() ? CAT_NAMES[i] : NAMES[i];
	}

	private long now() {
		return minecraft.level == null ? 0 : minecraft.level.getGameTime();
	}

	private int secondsLeft(int i) {
		return (int) Math.max(0, (READY_AT[i] - now() + 19) / 20);
	}

	/** Which emote the pointer is on: -1 for none, or the last emote when it's in the middle. */
	private int pick(double mouseX, double mouseY) {
		double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
		if (dx * dx + dy * dy <= 18 * 18) {
			return available(last) ? last : -1;
		}
		int i = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 2) : (dy < 0 ? 0 : 3);
		return available(i) ? i : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		int cx = width / 2, cy = height / 2, r = 56;
		hovered = pick(mouseX, mouseY);
		g.fill(cx - 90, cy - 90, cx + 90, cy + 90, 0x90000000);
		for (int i = 0; i < 4; i++) {
			int x = cx + DIRS[i][0] * r - 16, y = cy + DIRS[i][1] * r - 16;
			int wait = secondsLeft(i);
			boolean on = available(i) && wait == 0;
			if (i == hovered) {
				g.fill(x - 4, y - 4, x + 36, y + 36, 0x60FFFFFF);
			}
			if (!available(i) && cat()) {
				continue; // cats have three sounds, not four
			}
			g.blit(RenderPipelines.GUI_TEXTURED, (cat() ? CAT_ICONS : ICONS)[i], x, y, 0, 0, 32, 32, 32, 32, on ? 0xFFFFFFFF : 0x60FFFFFF);
			g.centeredText(font, name(i), x + 16, y + 36, on ? 0xFFFFFFFF : 0xFF808080);
			if (available(i) && wait > 0) {
				g.centeredText(font, wait + "s", x + 16, y + 12, 0xFFFFD060);
			}
		}
		g.centeredText(font, available(last) ? "Again: " + name(last) : "Release to emote", cx, cy - 4, 0xFFC0C0C0);
	}

	private void fire(int choice) {
		if (choice >= 0) {
			ClientPlayNetworking.send(new Payloads.Emote(choice));
			last = choice;
			if (secondsLeft(choice) == 0) {
				READY_AT[choice] = now() + Emotes.Kind.values()[choice].cooldownSeconds * 20L;
			}
		}
		onClose();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (holdKey.matches(event)) {
			fire(hovered);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		fire(pick(event.x(), event.y()));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
