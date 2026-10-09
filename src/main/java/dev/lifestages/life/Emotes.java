package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModEffects;
import dev.lifestages.registry.ModParticles;
import dev.lifestages.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server side of the four emotes: what happens in the world when a baby cries, farts, is cute or poops. */
public final class Emotes {
	public enum Kind {
		CRY(2), FART(10), CUTE(20), POOP(45);

		public final int cooldownSeconds;

		Kind(int cooldownSeconds) {
			this.cooldownSeconds = cooldownSeconds;
		}
	}

	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	/** Players within 16 blocks of a crying baby can't sleep until this game time. */
	private static final Map<UUID, Long> CRYING_UNTIL = new HashMap<>();

	private Emotes() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		COOLDOWNS.clear();
		CRYING_UNTIL.clear();
	}

	/** Game time this emote is ready again for the player (0 if never used). */
	public static long readyAt(ServerPlayer player, Kind kind) {
		long[] cd = COOLDOWNS.get(player.getUUID());
		return cd == null ? 0 : cd[kind.ordinal()];
	}

	/** Called when a player picks an emote on the wheel. */
	public static void request(ServerPlayer player, Kind kind) {
		LifeData d = Life.of(player);
		Stage s = d.stage();
		if (!s.hasEmotes() || (kind == Kind.POOP && !s.wearsDiaper())) {
			return;
		}
		long now = player.level().getGameTime();
		long[] cd = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[Kind.values().length]);
		if (now < cd[kind.ordinal()]) {
			long secs = (cd[kind.ordinal()] - now + 19) / 20;
			player.sendOverlayMessage(Component.literal("Wait " + secs + "s before you can do that again.").withStyle(ChatFormatting.GRAY));
			return;
		}
		cd[kind.ordinal()] = now + kind.cooldownSeconds * 20L;
		switch (kind) {
			case CRY -> cry(player);
			case FART -> fart(player, false);
			case CUTE -> cute(player);
			case POOP -> poop(player);
		}
	}

	public static void cry(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.blockPosition(), ModSounds.CRY, SoundSource.PLAYERS, 2.0f, 1.0f + (player.getRandom().nextFloat() - 0.5f) * 0.2f);
		Vec3 eye = player.getEyePosition();
		Vec3 side = player.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(0.25 * Life.stage(player).scale * 2);
		for (int i = 0; i < 6; i++) {
			level.sendParticles(ModParticles.TEAR, eye.x + side.x, eye.y, eye.z + side.z, 2, 0.05, 0.05, 0.05, 0.1);
			level.sendParticles(ModParticles.TEAR, eye.x - side.x, eye.y, eye.z - side.z, 2, 0.05, 0.05, 0.05, 0.1);
		}
		CRYING_UNTIL.put(player.getUUID(), level.getGameTime() + 200);
		Family.cryAlert(player);
		Moms.comfort(player);
	}

	/** True if a crying baby within 16 blocks keeps this player awake (family members can still sleep). */
	public static boolean babyCryingNear(Player sleeper) {
		long now = sleeper.level().getGameTime();
		for (Player other : sleeper.level().players()) {
			Long until = CRYING_UNTIL.get(other.getUUID());
			if (until != null && until > now && other != sleeper && other.distanceToSqr(sleeper) < 16 * 16
					&& !Family.related(other, sleeper)) {
				return true;
			}
		}
		return false;
	}

	public static void fart(ServerPlayer player, boolean turbo) {
		ServerLevel level = (ServerLevel) player.level();
		LifeConfig cfg = LifeConfig.get();
		LifeData d = Life.of(player);
		float pitch = 0.6f + player.getRandom().nextFloat() * 0.8f;
		boolean epic = player.getRandom().nextInt(100) == 0;
		level.playSound(null, player.blockPosition(), epic ? ModSounds.FART_EPIC : ModSounds.FART, SoundSource.PLAYERS, 1.2f, epic ? 1.0f : pitch);
		Vec3 behind = player.position().add(player.getLookAngle().multiply(-0.4, 0, -0.4)).add(0, 0.3 * d.stage().scale * 2, 0);
		{
			AreaEffectCloud cloud = new AreaEffectCloud(level, behind.x, behind.y, behind.z);
			cloud.setRadius(2.0f);
			cloud.setDuration(6 * 20);
			cloud.setRadiusPerTick(-0.01f);
			cloud.setWaitTime(0);
			cloud.setOwner(player);
			if (!"off".equalsIgnoreCase(cfg.grossness)) {
				cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 6 * 20));
			}
			cloud.setCustomParticle(ModParticles.FART);
			level.addFreshEntity(cloud);
		}
		level.sendParticles(ModParticles.FART, behind.x, behind.y, behind.z, 8, 0.3, 0.2, 0.3, 0.01);
		if (!"off".equalsIgnoreCase(cfg.grossness)) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(behind, behind).inflate(2.5))) {
				Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.6);
				mob.push(push.x, 0.15, push.z);
				mob.setTarget(null);
			}
			if (nearFlame(level, player.blockPosition())) {
				level.sendParticles(ParticleTypes.FLAME, behind.x, behind.y, behind.z, 30, 0.6, 0.4, 0.6, 0.05);
				level.sendParticles(ParticleTypes.EXPLOSION, behind.x, behind.y, behind.z, 1, 0, 0, 0, 0);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(behind, behind).inflate(2.0))) {
					e.hurtServer(level, level.damageSources().inFire(), 1.0f);
				}
				ModAdvancements.award(player, "fart_flame");
			}
		}
		if (turbo) {
			player.push(0, 0.55, 0);
			player.needsSync = true;
			ModAdvancements.award(player, "turbo_fart");
		}
		ModAdvancements.award(player, "silent_but_deadly");
	}

	private static boolean nearFlame(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
					|| state.is(Blocks.CAMPFIRE) || state.is(Blocks.LANTERN)) {
				return true;
			}
		}
		return false;
	}

	public static void cute(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		LifeData d = Life.of(player);
		if (d.diaperDirty && !"off".equalsIgnoreCase(LifeConfig.get().grossness)) {
			player.sendOverlayMessage(Component.literal("Nobody thinks you're cute right now.").withStyle(ChatFormatting.GREEN));
			return;
		}
		level.playSound(null, player.blockPosition(), ModSounds.GIGGLE, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY() + 0.2, player.getZ(), 6, 0.4, 0.3, 0.4, 0.02);
		level.sendParticles(ModParticles.SPARKLE, player.getX(), player.getEyeY(), player.getZ(), 8, 0.4, 0.3, 0.4, 0.05);
		AABB area = player.getBoundingBox().inflate(8);
		for (Player other : level.getEntitiesOfClass(Player.class, area)) {
			if (other != player) {
				other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 5 * 20));
			}
		}
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(6))) {
			creeper.setSwellDir(-1);
			creeper.setTarget(null);
		}
	}

	public static void poop(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		LifeConfig cfg = LifeConfig.get();
		LifeData d = Life.of(player);
		level.playSound(null, player.blockPosition(), ModSounds.POOP, SoundSource.PLAYERS, 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
		if (!"off".equalsIgnoreCase(cfg.grossness)) {
			level.sendParticles(ModParticles.SPLAT, player.getX(), player.getY() + 0.3, player.getZ(), cfg.gross() ? 12 : 4, 0.2, 0.1, 0.2, 0.05);
		}
		d.feedsSincePoop = 0;
		d.rumbleTicks = 0;
		if (!d.diaperOn) {
			// Elderly with no diaper: an accident on the floor
			if (cfg.gross()) {
				BlockPos at = player.blockPosition();
				if (level.getBlockState(at).canBeReplaced() && ModBlocks.MESS.defaultBlockState().canSurvive(level, at)) {
					level.setBlockAndUpdate(at, ModBlocks.MESS.defaultBlockState());
				}
			}
			player.addEffect(new MobEffectInstance(ModEffects.STINKY, 60 * 20));
			player.sendOverlayMessage(Component.literal("Uh oh... you weren't wearing a diaper.").withStyle(ChatFormatting.GOLD));
		} else {
			boolean wasDirty = d.diaperDirty;
			d.diaperDirty = true;
			d.diaperTicks = 0;
			Player carrier = Carrying.carrierOf(player);
			if ((carrier != null || wasDirty) && cfg.gross()) {
				LivingEntity victim = carrier != null ? carrier : player;
				victim.addEffect(new MobEffectInstance(ModEffects.STINKY, 60 * 20));
				level.sendParticles(ModParticles.SPLAT, victim.getX(), victim.getY() + 1.0, victim.getZ(), 20, 0.3, 0.4, 0.3, 0.1);
				ModAdvancements.award(player, "blowout");
			}
			Moms.diaperNeedsChange(player);
		}
		Life.save(player, d);
	}
}
