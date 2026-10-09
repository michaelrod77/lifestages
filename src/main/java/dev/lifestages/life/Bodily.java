package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.registry.ModEffects;
import dev.lifestages.registry.ModParticles;
import dev.lifestages.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** The body clock: belly rumbles and automatic poops, diaper rash, Elderly accidents, stink and sugar sparkles. */
public final class Bodily {
	static final int FEEDS_PER_POOP = 4;
	static final int RUMBLE_TICKS = 10 * 20;
	static final int RASH_AFTER = 2 * 60 * 20;

	private Bodily() {
	}

	/** Returns true if anything that clients need to see changed. */
	static boolean tick(ServerPlayer player, LifeData d, Stage s) {
		ServerLevel level = (ServerLevel) player.level();
		LifeConfig cfg = LifeConfig.get();
		boolean changed = false;

		if (player.isInWater() && player.hasEffect(ModEffects.STINKY)) {
			player.removeEffect(ModEffects.STINKY);
		}
		if (player.hasEffect(ModEffects.STINKY) && player.tickCount % 10 == 0) {
			level.sendParticles(ModParticles.STINK, player.getX(), player.getY() + player.getBbHeight() * 0.8, player.getZ(), 1, 0.2, 0.2, 0.2, 0);
		}
		if (d.sugarTicks > 0 && player.tickCount % 3 == 0) {
			level.sendParticles(ModParticles.SPARKLE, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.15, 0.1, 0.15, 0);
		}

		if (!s.wearsDiaper()) {
			return false;
		}

		// belly rumble, then an automatic poop
		if (d.rumbleTicks > 0) {
			d.rumbleTicks--;
			if (d.rumbleTicks == 0) {
				Emotes.poop(player);
				changed = true;
			}
		} else if (s != Stage.ELDERLY && d.feedsSincePoop >= FEEDS_PER_POOP) {
			startRumble(player, d);
			changed = true;
		}

		// Elderly accidents
		if (s == Stage.ELDERLY && d.rumbleTicks == 0) {
			if (player.getRandom().nextInt(cfg.ticks(3.5)) == 0) {
				Emotes.fart(player, false);
			}
			if (player.getRandom().nextInt(cfg.ticks(8)) == 0) {
				startRumble(player, d);
				changed = true;
			}
		}

		if (d.diaperDirty) {
			d.diaperTicks++;
			if (d.diaperTicks % 10 == 0 && !cfg.mild() && cfg.gross()) {
				level.sendParticles(ModParticles.FLY, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 1, 0.3, 0.2, 0.3, 0);
			}
			if (d.diaperTicks == 60 * 20 && s != Stage.ELDERLY && !Birthdays.holdingTeddy(player)) {
				Emotes.cry(player);
			}
			if (d.diaperTicks >= RASH_AFTER && d.diaperTicks % (30 * 20) == 0 && player.getHealth() > 2.0f) {
				player.hurtServer(level, level.damageSources().generic(), 1.0f);
				player.sendOverlayMessage(Component.literal("Diaper rash! Somebody change you!").withStyle(ChatFormatting.RED));
				if (s != Stage.ELDERLY && !Birthdays.holdingTeddy(player)) {
					Emotes.cry(player);
				}
			}
		}
		return changed;
	}

	static void startRumble(ServerPlayer player, LifeData d) {
		d.rumbleTicks = RUMBLE_TICKS;
		player.level().playSound(null, player.blockPosition(), ModSounds.RUMBLE, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.sendOverlayMessage(Component.literal(d.stage() == Stage.ELDERLY ? "Uh oh..." : "*rumble rumble*").withStyle(ChatFormatting.GOLD));
	}

	/** Called whenever a baby is fed; every few feeds leads to a poop. */
	public static void fed(LifeData d) {
		d.feedsSincePoop++;
	}
}
