package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.registry.ModAdvancements;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ghosts: spectators for up to 10 minutes who can Boo, flicker lamps, and then choose to be reborn. */
public final class Ghosts {
	private static final Map<UUID, Long> BOO_COOLDOWN = new HashMap<>();

	private Ghosts() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		BOO_COOLDOWN.clear();
	}

	public static void become(ServerPlayer player, LifeData d) {
		d.ghostTicks = LifeConfig.get().ghostMaxMinutes * 60 * 20;
		d.lastWasGhost = true;
		d.pendingChoice = 0;
		d.started = true;
		player.setGameMode(GameType.SPECTATOR);
		player.sendSystemMessage(Component.literal("You are a ghost. Press B to Boo, K to flicker a lamp, R to be reborn.").withStyle(ChatFormatting.AQUA));
	}

	static void tick(ServerPlayer player, LifeData d) {
		if (!player.isSpectator()) {
			player.setGameMode(GameType.SPECTATOR);
		}
		d.ghostTicks--;
		if (d.ghostTicks % (60 * 20) == 0 && d.ghostTicks > 0) {
			player.sendOverlayMessage(Component.literal((d.ghostTicks / 1200) + " minutes of haunting left").withStyle(ChatFormatting.AQUA));
		}
		if (d.ghostTicks <= 0) {
			reborn(player, d);
		} else if (player.tickCount % 20 == 0) {
			Life.save(player, d);
		}
	}

	/** Time is up or the ghost pressed R: open the choice screen without the Ghost option. */
	public static void reborn(ServerPlayer player, LifeData d) {
		d.ghostTicks = 0;
		d.pendingChoice = 2;
		Life.save(player, d);
	}

	public static void boo(ServerPlayer ghost) {
		if (!Life.of(ghost).isGhost()) {
			return;
		}
		long now = ghost.level().getGameTime();
		if (BOO_COOLDOWN.getOrDefault(ghost.getUUID(), 0L) > now) {
			return;
		}
		BOO_COOLDOWN.put(ghost.getUUID(), now + 30 * 20);
		ServerLevel level = (ServerLevel) ghost.level();
		Vec3 at = ghost.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 20, 0.5, 0.5, 0.5, 0.02);
		AABB area = new AABB(at, at).inflate(6);
		for (Player p : level.getEntitiesOfClass(Player.class, area)) {
			if (p != ghost && !p.isSpectator()) {
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 2 * 20));
			}
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
			Vec3 away = mob.position().subtract(at).normalize().scale(4);
			mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.4);
			mob.setTarget(null);
		}
		ModAdvancements.award(ghost, "boo");
	}

	/** Turns a nearby redstone lamp off and on again. */
	public static void flicker(ServerPlayer ghost) {
		if (!Life.of(ghost).isGhost()) {
			return;
		}
		ServerLevel level = (ServerLevel) ghost.level();
		for (BlockPos pos : BlockPos.betweenClosed(ghost.blockPosition().offset(-4, -3, -4), ghost.blockPosition().offset(4, 3, 4))) {
			BlockState state = level.getBlockState(pos);
			if (state.hasProperty(RedstoneLampBlock.LIT) && state.getValue(RedstoneLampBlock.LIT)) {
				BlockPos fixed = pos.immutable();
				level.setBlock(fixed, state.setValue(RedstoneLampBlock.LIT, false), 2);
				level.scheduleTick(fixed, state.getBlock(), 10);
				return;
			}
		}
	}
}
