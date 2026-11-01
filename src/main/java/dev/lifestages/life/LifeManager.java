package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Server-side clock for every player's life: aging, growth goals, naps, nights, sugar rush and old age. */
public final class LifeManager {
	private static final Identifier SCALE = LifeStages.id("stage_scale");
	private static final Identifier SPEED = LifeStages.id("stage_speed");
	private static final Identifier SUGAR = LifeStages.id("sugar_rush");
	private static final Identifier HEALTH = LifeStages.id("stage_health");
	private static final Identifier JUMP = LifeStages.id("stage_jump");
	private static final Identifier MINING = LifeStages.id("stage_mining");
	private static final Identifier DAMAGE = LifeStages.id("stage_damage");

	public static final int NAP_TICKS = 30 * 20;

	private LifeManager() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		if (player.isDeadOrDying() || player.isRemoved()) {
			return;
		}
		LifeData d = Life.of(player);
		if (!d.started || d.pendingChoice != 0) {
			if (player.tickCount % 40 == 0) {
				Net.openChoice(player, d.started ? 2 : 1, d.lastWasGhost);
			}
			return;
		}
		if (d.isGhost()) {
			Ghosts.tick(player, d);
			return;
		}
		if (player.isSpectator() || player.isCreative()) {
			return;
		}
		LifeConfig cfg = LifeConfig.get();
		Stage stage = d.stage();
		boolean dirty = false;

		d.stageTicks++;
		if (d.sugarTicks > 0) {
			d.sugarTicks--;
			if (d.sugarTicks == 0) {
				dirty = true;
				sugarCrash(player, d);
			}
		}
		if (d.crashTicks > 0) {
			// The crash lasts until they sleep it off (a nap or a night), or 2 minutes at most.
			if (--d.crashTicks == 0 || player.isSleeping() || d.napTicks > 0) {
				endSugarCrash(player, d);
			}
			dirty = true;
		}
		if (stage.isBaby()) {
			// Babies burn through milk fast: about one hunger point every 15 seconds, with no saturation to spare.
			player.getFoodData().setSaturation(0.0f);
			player.getFoodData().addExhaustion(0.014f);
		} else if (stage == Stage.TODDLER) {
			player.getFoodData().addExhaustion(0.008f);
		} else if (stage == Stage.KITTEN) {
			player.getFoodData().addExhaustion(0.01f);
		}
		if (stage.isPet()) {
			dirty |= Pets.tick(player, d);
		}
		dirty |= tickSleep(player, d, stage);
		dirty |= Bodily.tick(player, d, stage);

		if (stage.isFinal()) {
			if (d.lifespanTicks <= 0) {
				rollLifespan(player, d);
				dirty = true;
			}
			int left = d.lifespanTicks + d.bonusTicks - d.stageTicks;
			if (left == 5 * 60 * 20) {
				player.sendSystemMessage(Component.literal("You feel tired.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			} else if (left == 60 * 20) {
				player.sendSystemMessage(Component.literal("Time to say goodbye.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				Family.notifyFamily(player, Component.literal(player.getName().getString() + " doesn't have long left. Go see them."));
			}
			if (left <= 0) {
				dieOfOldAge(player, d);
				return;
			}
		} else if (player.tickCount % 20 == 0 && goalsMet(d, stage, cfg)) {
			growUp(player, d);
			return;
		}
		if (dirty || player.tickCount % 20 == 0) {
			applyAttributes(player, d);
			Life.save(player, d);
		}
	}

	// ---- growth ----

	/** Grown up once the stage has run its (goal-shortened) length. */
	public static boolean goalsMet(LifeData d, Stage s, LifeConfig cfg) {
		return d.stageTicks >= d.requiredTicks(cfg.lifeLengthMultiplier);
	}

	public static void growUp(ServerPlayer player, LifeData d) {
		Stage from = d.stage();
		Stage to = from.next();
		d.enterStage(to);
		applyAttributes(player, d);
		Life.save(player, d);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
		player.sendSystemMessage(Component.literal("You are now a " + to.label + "!").withStyle(ChatFormatting.GOLD));
		ModAdvancements.stageReached(player, to);
		if (to.isPet()) {
			player.sendSystemMessage(Component.literal(to == Stage.CAT ? "No more milk: from now on it's fish, please!"
					: "You're getting on a bit. Plenty of naps in sunny spots from now on.").withStyle(ChatFormatting.GRAY));
		} else {
			Birthdays.give(player, d, to);
		}
		Life.save(player, d);
		if (to == Stage.CHILD) {
			Moms.childGrewUp(player);
		}
		if (from.atMost(Stage.CHILD) && to.atLeast(Stage.TEENAGER)) {
			Carrying.drop(player);
		}
	}

	/** Sets a player straight to a stage (commands and respawn choices). */
	public static void setStage(ServerPlayer player, Stage stage) {
		LifeData d = Life.of(player);
		d.enterStage(stage);
		if (stage.isFinal()) {
			rollLifespan(player, d);
		}
		applyAttributes(player, d);
		Life.save(player, d);
	}

	public static void rollLifespan(ServerPlayer player, LifeData d) {
		LifeConfig cfg = LifeConfig.get();
		RandomSource r = player.getRandom();
		int lo = cfg.elderlyLifespanMinMinutes, hi = Math.max(lo, cfg.elderlyLifespanMaxMinutes);
		d.lifespanTicks = cfg.ticks(lo + r.nextDouble() * (hi - lo));
		d.bonusTicks = 0;
	}

	private static void dieOfOldAge(ServerPlayer player, LifeData d) {
		Stage s = d.stage();
		int age = (int) Math.floor(s.startAge + (s.endAge - s.startAge) * d.stageTicks / Math.max(1, d.lifespanTicks + d.bonusTicks));
		ModAdvancements.award(player, "rest_in_peace");
		WillChest.place(player, age);
		var source = ModDamage.oldAge(player.level(), age);
		player.hurtServer((ServerLevel) player.level(), source, Float.MAX_VALUE);
		if (player.isAlive()) {
			// Something blocked the damage (a totem, another mod): old age still wins.
			player.setHealth(0.0f);
			player.die(source);
		}
	}

	// ---- sleep ----

	private static boolean tickSleep(ServerPlayer player, LifeData d, Stage s) {
		boolean changed = false;
		if (player.isSleeping() && player.getSleepTimer() >= 100 && player.level().isDarkOutside()) {
			long day = player.level().getDefaultClockTime() / 24000L;
			if (day != d.lastNightDay) {
				d.lastNightDay = day;
				d.nights++;
				player.sendSystemMessage(Component.literal("You slept through the night.").withStyle(ChatFormatting.AQUA));
				changed = true;
			}
		}
		boolean napStage = s.naps > 0 || s.napOrNight;
		if (napStage && napping(player)) {
			// cuddling a teddy makes naps go twice as fast; a music box playing nearby adds another half
			d.napTicks += (Birthdays.holdingTeddy(player) ? 2 : 1)
					+ (player.tickCount % 2 == 0 && dev.lifestages.block.Nursery.musicNear(player.level(), player.blockPosition()) ? 1 : 0);
			if (player.tickCount % 40 == 0) {
				((ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
						player.getX(), player.getEyeY() + 0.3, player.getZ(), 1, 0.1, 0.1, 0.1, 0);
			}
			if (d.napTicks >= NAP_TICKS) {
				d.napTicks = 0;
				d.naps++;
				player.sendSystemMessage(Component.literal("What a nice nap. (" + d.naps + " today)").withStyle(ChatFormatting.AQUA));
				changed = true;
			}
		} else if (d.napTicks > 0) {
			d.napTicks = 0;
			changed = true;
		}
		return changed;
	}

	/** What goes up comes down: after a sugar rush, a child is slow and dizzy until they sleep (2 minutes at most). */
	public static void sugarCrash(ServerPlayer player, LifeData d) {
		int ticks = SUGAR_CRASH_TICKS;
		d.crashTicks = ticks;
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks, 1, false, false, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, ticks, 0, false, false, true));
		player.sendOverlayMessage(Component.literal("Sugar crash... you feel slow and dizzy. A nap will help.").withStyle(ChatFormatting.GRAY));
	}

	public static void endSugarCrash(ServerPlayer player, LifeData d) {
		d.crashTicks = 0;
		var slow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		if (slow != null && slow.getDuration() <= SUGAR_CRASH_TICKS && !slow.isVisible()) {
			player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		}
		var dizzy = player.getEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
		if (dizzy != null && dizzy.getDuration() <= SUGAR_CRASH_TICKS && !dizzy.isVisible()) {
			player.removeEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
		}
	}

	public static final int SUGAR_CRASH_TICKS = 2 * 60 * 20;

	/** Lying still in a bed or crib, or held still in someone's arms. */
	public static boolean napping(ServerPlayer player) {
		Vec3 v = player.getDeltaMovement();
		boolean still = v.horizontalDistanceSqr() < 1.0e-4 && player.getLastHurtByMobTimestamp() + 100 < player.tickCount;
		if (!still || player.isSprinting()) {
			return false;
		}
		// a cat curls up anywhere (sneak to sit still)
		return player.isSleeping() || Carrying.isCarried(player) || Cribs.isInCrib(player) || player.isShiftKeyDown() && Pets.isPet(player);
	}

	// ---- body ----

	public static void applyAttributes(ServerPlayer player, LifeData d) {
		Stage s = d.stage();
		LifeConfig cfg = LifeConfig.get();
		set(player, Attributes.SCALE, SCALE, s.scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		double speed = s.speed;
		if (s == Stage.CHILD && d.age(cfg.lifeLengthMultiplier) >= cfg.carryAgeLimit) {
			speed = 1.0;
		}
		set(player, Attributes.MOVEMENT_SPEED, SPEED, speed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, SUGAR, d.sugarTicks > 0 ? cfg.sugarRushSpeed - 1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MAX_HEALTH, HEALTH, s.hearts * 2 - 20, AttributeModifier.Operation.ADD_VALUE);
		// Cats jump twice as high as a person (jump height grows with the square of the take-off speed: x1.41).
		double jump = s.isBaby() ? -1.0 : s == Stage.TODDLER ? -0.3 : s == Stage.KITTEN ? 0.2 : s.isPet() ? 0.414 : 0.0;
		set(player, Attributes.JUMP_STRENGTH, JUMP, jump, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.BLOCK_BREAK_SPEED, MINING, s.atMost(Stage.TODDLER) ? -1.0 : s == Stage.PRESCHOOLER ? -0.5 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.ATTACK_DAMAGE, DAMAGE, s.isBaby() ? -1.0 : s == Stage.CHILD ? -0.25 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst == null) {
			return;
		}
		AttributeModifier current = inst.getModifier(id);
		if (amount == 0.0) {
			if (current != null) {
				inst.removeModifier(id);
			}
			return;
		}
		if (current == null || current.amount() != amount) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
