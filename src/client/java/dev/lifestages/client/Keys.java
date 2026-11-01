package dev.lifestages.client;

import dev.lifestages.LifeStages;
import dev.lifestages.client.screen.EmoteWheelScreen;
import dev.lifestages.client.screen.JournalScreen;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.net.Payloads;
import dev.lifestages.net.Payloads.Action;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.platform.InputConstants;

/** Life Stages key bindings (changeable in Controls). */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LifeStages.id("keys"));
	// Z and U: vanilla 26.3 took G (Quick Actions) and O (Friends), and its screens opened instead of ours.
	// New ids so saved options from older versions (G, O) don't carry over.
	public static final KeyMapping EMOTES = key("emotes", InputConstants.KEY_Z);
	public static final KeyMapping JOURNAL = key("journal", InputConstants.KEY_J);
	public static final KeyMapping PAIR = key("family", InputConstants.KEY_U);
	public static final KeyMapping PUT_DOWN = key("put_down", InputConstants.KEY_V);
	public static final KeyMapping BOO = key("boo", InputConstants.KEY_B);
	public static final KeyMapping FLICKER = key("flicker", InputConstants.KEY_K);
	public static final KeyMapping REBORN = key("reborn", InputConstants.KEY_R);
	private static boolean jumpWasDown;

	private Keys() {
	}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lifestages." + name, code, CATEGORY));
	}

	public static void init() {
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		LifeData d = Life.of(mc.player);
		if (d.started && d.stage().isPet() && mc.player.isSprinting()) {
			ClientLife.dash = Math.max(0, ClientLife.dash - 1);
		} else if (ClientLife.dash < dev.lifestages.life.Pets.DASH_TICKS && mc.player.tickCount % 2 == 0) {
			ClientLife.dash++;
		}
		if (ClientLife.costumePending && mc.gui.screen() == null) {
			ClientLife.costumePending = false;
			mc.setScreenAndShow(new dev.lifestages.client.screen.CostumeScreen());
		}
		if (mc.player.tickCount % 100 == 0) {
			dev.lifestages.client.render.PetCats.tidy(mc);
		}
		while (EMOTES.consumeClick()) {
			if (mc.gui.screen() == null && d.started && d.stage().hasEmotes() && !d.isGhost()) {
				mc.setScreenAndShow(new EmoteWheelScreen(EMOTES));
			}
		}
		while (JOURNAL.consumeClick()) {
			if (mc.gui.screen() == null && d.started) {
				mc.setScreenAndShow(new JournalScreen());
			}
		}
		while (PAIR.consumeClick()) {
			if (mc.crosshairPickEntity instanceof Player other) {
				send(Action.PAIR, other.getId());
			}
		}
		while (PUT_DOWN.consumeClick()) {
			send(Action.PUT_DOWN, 0);
		}
		while (BOO.consumeClick()) {
			if (d.isGhost()) {
				send(Action.BOO, 0);
			}
		}
		while (FLICKER.consumeClick()) {
			if (d.isGhost()) {
				send(Action.FLICKER, 0);
			}
		}
		while (REBORN.consumeClick()) {
			if (d.isGhost()) {
				send(Action.REBORN, 0);
			}
		}
		// Carried toddlers and older wriggle free by tapping jump.
		boolean jump = mc.options.keyJump.isDown();
		if (jump && !jumpWasDown && mc.player.getVehicle() != null) {
			send(Action.WRIGGLE, 0);
		}
		jumpWasDown = jump;
	}

	private static void send(int action, int target) {
		ClientPlayNetworking.send(new Payloads.Act(action, target));
	}
}
