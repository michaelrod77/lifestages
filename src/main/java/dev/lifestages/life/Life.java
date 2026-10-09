package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/** The per-player life attachment: saved with the player, copied on death, synced to every client. */
public final class Life {
	public static final AttachmentType<LifeData> DATA = AttachmentRegistry.<LifeData>builder()
			.persistent(LifeData.CODEC)
			.copyOnDeath()
			.initializer(LifeData::new)
			.syncWith(ByteBufCodecs.fromCodecWithRegistries(LifeData.CODEC), AttachmentSyncPredicate.all())
			.buildAndRegister(LifeStages.id("life"));

	private Life() {
	}

	public static void init() {
	}

	/** The live data for a player. On the server, call {@link #save} after changing it. */
	public static LifeData of(Player player) {
		return player.getAttachedOrCreate(DATA);
	}

	public static Stage stage(Player player) {
		return of(player).stage();
	}

	/** Stores a fresh copy so the change is saved and sent to clients. */
	public static void save(Player player, LifeData data) {
		player.setAttached(DATA, data.copy());
	}

	public static double age(Player player) {
		return of(player).age(LifeConfig.get().lifeLengthMultiplier);
	}
}
