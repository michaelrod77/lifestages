package dev.lifestages.life;

import dev.lifestages.registry.ModBlocks;
import net.minecraft.world.entity.player.Player;

public final class Cribs {
	private Cribs() {
	}

	/** Standing or lying inside a crib. */
	public static boolean isInCrib(Player player) {
		return player.level().getBlockState(player.blockPosition()).is(ModBlocks.CRIB)
				|| player.level().getBlockState(player.blockPosition().below()).is(ModBlocks.CRIB);
	}
}
