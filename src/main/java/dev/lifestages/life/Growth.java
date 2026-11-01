package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/** Extra ways to grow up faster than the default stage length. */
public final class Growth {
	/** Share of a stage's default length that one vanilla advancement skips. */
	static final double ADVANCEMENT_BOOST = 0.10;

	private Growth() {
	}

	/** A finished advancement (shown in the advancement screen, not a recipe, not one of ours) skips part of the stage. */
	public static void onAdvancement(ServerPlayer player, AdvancementHolder holder, PlayerAdvancements progress) {
		if (holder.value().display().isEmpty() || holder.id().getNamespace().equals(LifeStages.MOD_ID)
				|| !progress.getOrStartProgress(holder).isDone()) {
			return;
		}
		LifeData d = Life.of(player);
		if (!d.started || d.isGhost() || d.stage().isFinal()) {
			return;
		}
		int boost = LifeConfig.get().ticks(d.stage().minMinutes * ADVANCEMENT_BOOST);
		d.stageTicks += boost;
		Life.save(player, d);
		player.sendSystemMessage(Component.literal("You learned something new and grew a little! (+" + Math.max(1, boost / 1200) + " min)")
				.withStyle(ChatFormatting.GREEN));
	}
}
