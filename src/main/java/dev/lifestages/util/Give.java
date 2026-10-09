package dev.lifestages.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class Give {
	private Give() {
	}

	/** Puts the stack in the inventory, or drops it at the player's feet if full. */
	public static void item(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation((ServerLevel) player.level(), stack);
		}
	}

	/** A sound only this player hears. */
	public static void privateSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}
}
