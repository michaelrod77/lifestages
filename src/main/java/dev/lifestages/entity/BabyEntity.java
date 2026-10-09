package dev.lifestages.entity;

import dev.lifestages.life.Feeding;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModItems;
import dev.lifestages.registry.ModParticles;
import dev.lifestages.registry.ModSounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An NPC baby, hatched from a Baby Spawn Egg by an adult player. It has needs - food, a clean diaper and rest - and
 * a curious streak: it crawls (later toddles) around its home, investigating animals, flowers and anything shiny,
 * and on a sugar rush it bolts off, sometimes toward trouble. Its parent's job is to keep it fed, clean, rested
 * and safe. It cries when something is wrong. It never dies: a baby that would have been badly hurt is rescued
 * and brought home, shaken, and the parent hears about it.
 */
public class BabyEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DIRTY = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> SUGAR = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> CRYING = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.STRING);
	/** -1 neglected, 0-4 how many of fed / clean / rested / loved are covered. */
	private static final EntityDataAccessor<Integer> CARE = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);
	/** Progress to the next stage, 0-1000. */
	private static final EntityDataAccessor<Integer> GROWTH = SynchedEntityData.defineId(BabyEntity.class, EntityDataSerializers.INT);

	public static final int MAX_HUNGER = 20, MAX_ENERGY = 100;
	/** Grown up: the last stage an NPC child reaches. */
	public static final Stage GROWN = Stage.YOUNG_ADULT;
	private static final String[] NAMES = {"Lily", "Max", "Ava", "Leo", "Mia", "Theo", "Ruby", "Finn", "Ivy", "Milo", "Rosie", "Sam",
			"Nora", "Ollie", "Zoe", "Jack", "Ellie", "Noah", "Poppy", "Eli", "Luna", "Ben", "Daisy", "Kai", "Willow", "Toby", "Hazel", "Arlo"};

	private BlockPos home;
	private int growTicks;
	private int lovedTicks;
	private int complainCooldown;
	private int hungerTimer;
	private int dirtyTicks;
	private int feedsSincePoop;
	private int energyTimer;
	private int cryCooldown;
	private int calmTicks;
	private int starveTimer;
	private int closeCalls;
	private boolean inCrib;

	public BabyEntity(EntityType<? extends BabyEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FOLLOW_RANGE, 16.0)
				.add(Attributes.TEMPT_RANGE, 10.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(STAGE, Stage.NEWBORN.ordinal());
		builder.define(LOOK, 1);
		builder.define(HUNGER, 16);
		builder.define(ENERGY, 80);
		builder.define(DIRTY, false);
		builder.define(ASLEEP, false);
		builder.define(SUGAR, 0);
		builder.define(CRYING, false);
		builder.define(OWNER, "");
		builder.define(CARE, 2);
		builder.define(GROWTH, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		goalSelector.addGoal(2, new SugarRushGoal(this));
		goalSelector.addGoal(3, new TemptGoal(this, 1.1, s -> s.is(ModItems.MILK_BOTTLE) || s.is(Feeding.SUGARY) || s.is(ModItems.RATTLE)
				|| s.has(net.minecraft.core.component.DataComponents.FOOD), false));
		goalSelector.addGoal(5, new CuriousGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	// ---- setup and accessors ----

	/** Called when a Baby Spawn Egg hatches. */
	public void hatch(ServerPlayer parent, BlockPos at) {
		entityData.set(OWNER, parent.getUUID().toString());
		entityData.set(LOOK, 1 + random.nextInt(4));
		home = at.immutable();
		setHomeTo(home, 16);
		love();
		setCustomName(Component.literal(NAMES[random.nextInt(NAMES.length)]));
		setCustomNameVisible(true);
		applyStage();
	}

	public Stage stage() {
		return Stage.values()[Mth.clamp(entityData.get(STAGE), 0, GROWN.ordinal())];
	}

	public int look() {
		return entityData.get(LOOK);
	}

	public int hunger() {
		return entityData.get(HUNGER);
	}

	public int energy() {
		return entityData.get(ENERGY);
	}

	public boolean dirty() {
		return entityData.get(DIRTY);
	}

	public boolean asleep() {
		return entityData.get(ASLEEP);
	}

	public boolean sugarRush() {
		return entityData.get(SUGAR) > 0;
	}

	public boolean crying() {
		return entityData.get(CRYING);
	}

	public int closeCalls() {
		return closeCalls;
	}

	public BlockPos home() {
		return home;
	}

	/** The parent's UUID, or null. */
	public UUID ownerId() {
		String s = entityData.get(OWNER);
		try {
			return s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private ServerPlayer owner() {
		UUID id = ownerId();
		return id == null || !(level() instanceof ServerLevel server) ? null : server.getServer().getPlayerList().getPlayer(id);
	}

	public void setHunger(int value) {
		entityData.set(HUNGER, Mth.clamp(value, 0, MAX_HUNGER));
	}

	public void setEnergy(int value) {
		entityData.set(ENERGY, Mth.clamp(value, 0, MAX_ENERGY));
	}

	public void setDirty(boolean value) {
		entityData.set(DIRTY, value);
		dirtyTicks = 0;
	}

	/** Any stage from Newborn to Young Adult. */
	public void setStage(Stage stage) {
		entityData.set(STAGE, Mth.clamp(stage.ordinal(), 0, GROWN.ordinal()));
		growTicks = 0;
		applyStage();
	}

	public boolean grownUp() {
		return stage() == GROWN;
	}

	/** -1 neglected, otherwise 0-4: how many of fed, clean, rested and loved are covered right now. */
	public int care() {
		return entityData.get(CARE);
	}

	/** Progress toward the next stage, 0 to 1. */
	public float growth() {
		return entityData.get(GROWTH) / 1000f;
	}

	/** Recently cuddled, played with or carried. */
	public void love() {
		lovedTicks = 3 * 60 * 20;
	}

	private void applyStage() {
		Stage s = stage();
		var scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(s.scale);
		}
		// newborns crawl slowly, infants crawl, toddlers toddle, then they walk and run like anyone
		double speed = switch (s) {
			case NEWBORN -> 0.06;
			case INFANT -> 0.1;
			case TODDLER -> 0.17;
			case PRESCHOOLER -> 0.21;
			case CHILD -> 0.25;
			default -> 0.28;
		};
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(s.atMost(Stage.TODDLER) ? 12.0 : s.atMost(Stage.CHILD) ? 16.0 : 20.0);
		// older children roam further from home
		int radius = s.atMost(Stage.TODDLER) ? 16 : s == Stage.PRESCHOOLER ? 20 : s == Stage.CHILD ? 28 : 36;
		if (home != null) {
			setHomeTo(home, radius);
		}
		refreshDimensions();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- the day: needs, crying, growing ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == null) {
			home = blockPosition();
			setHomeTo(home, 16);
		}
		boolean carried = getVehicle() instanceof Player;
		Stage s = stage();
		if (s == GROWN) {
			// All grown up: no more needs, no more crying.
			setHunger(MAX_HUNGER);
			setEnergy(MAX_ENERGY);
			entityData.set(DIRTY, false);
			entityData.set(ASLEEP, false);
			entityData.set(CRYING, false);
			entityData.set(CARE, 4);
			entityData.set(GROWTH, 1000);
			return;
		}
		if (carried) {
			love();
			// in the carrier: look out the way the grown-up is facing, not up at them
			Player carrier = (Player) getVehicle();
			Vec3 ahead = carrier.position().add(Vec3.directionFromRotation(0, carrier.getYRot()).scale(4));
			getLookControl().setLookAt(ahead.x, getEyeY(), ahead.z);
		}
		// hunger: a full tummy lasts about 5 minutes for a newborn, much longer as they grow
		int hungerSeconds = switch (s) {
			case NEWBORN -> 15;
			case INFANT -> 20;
			case TODDLER -> 25;
			case PRESCHOOLER -> 35;
			case CHILD -> 45;
			default -> 60;
		};
		if (++hungerTimer >= hungerSeconds * 20) {
			hungerTimer = 0;
			setHunger(hunger() - 1);
		}
		if (!s.wearsDiaper() && dirty()) {
			entityData.set(DIRTY, false); // out of diapers
		}
		// diapers: after a few feeds, or every few minutes
		if (s.wearsDiaper() && !dirty() && (feedsSincePoop >= 3 || tickCount % (6 * 60 * 20) == 0 && tickCount > 0)) {
			entityData.set(DIRTY, true);
			dirtyTicks = 0;
			feedsSincePoop = 0;
			level.playSound(null, blockPosition(), ModSounds.POOP, SoundSource.NEUTRAL, 0.7f, 1.3f);
		}
		if (dirty()) {
			dirtyTicks++;
			if (tickCount % 30 == 0) {
				level.sendParticles(ModParticles.STINK, getX(), getY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
			if (dirtyTicks > 2 * 60 * 20 && tickCount % 50 == 0) {
				level.sendParticles(ModParticles.FLY, getX(), getY() + 0.4, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
		// rest: awake babies tire, sleeping ones recover (twice as fast in a crib)
		inCrib = level.getBlockState(blockPosition()).is(ModBlocks.CRIB);
		if (asleep()) {
			if (++energyTimer >= (inCrib ? 5 : 10)) {
				energyTimer = 0;
				setEnergy(energy() + 1);
			}
			getNavigation().stop();
			if (tickCount % 40 == 0) {
				level.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
			boolean napper = s.atMost(Stage.PRESCHOOLER);
			if (energy() >= MAX_ENERGY || hunger() <= 3 || !napper && level.isBrightOutside() && energy() >= 70) {
				entityData.set(ASLEEP, false); // rested, woken by hunger, or morning
			}
		} else if (s.atMost(Stage.PRESCHOOLER)) {
			// little ones nap whenever they're tired
			if (++energyTimer >= (sugarRush() ? 3 : 7) * 20) {
				energyTimer = 0;
				setEnergy(energy() - 1);
			}
			boolean bedtime = energy() <= 12 || energy() < 40 && (level.isDarkOutside() || carried && random.nextInt(600) == 0);
			if (bedtime && !sugarRush() && (carried || inCrib || energy() <= 4 || random.nextInt(200) == 0)) {
				fallAsleep();
			}
		} else {
			// big kids stay up all day and go home to sleep at night
			if (++energyTimer >= (sugarRush() ? 4 : 12) * 20) {
				energyTimer = 0;
				setEnergy(energy() - 1);
			}
			boolean bedtime = energy() <= 8 || level.isDarkOutside() && energy() < 80;
			if (bedtime && !sugarRush() && !carried) {
				if (home != null && blockPosition().distSqr(home) > 9 && energy() > 8) {
					if (getNavigation().isDone()) {
						getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
					}
				} else {
					fallAsleep();
				}
			}
		}
		if (entityData.get(SUGAR) > 0) {
			entityData.set(SUGAR, entityData.get(SUGAR) - 1);
			if (tickCount % 6 == 0) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.5, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
		}
		// starving babies weaken
		if (hunger() == 0 && ++starveTimer >= 10 * 20) {
			starveTimer = 0;
			hurtServer(level, damageSources().starve(), 1.0f);
		}
		// Growing up. Each stage lasts as long as it does for players, at normal care. Better care speeds it up:
		// fed, clean, rested and loved (cuddles, play, being carried) each count, and all four make it twice as
		// fast, like a player completing their goals. Neglect (hungry, a dirty diaper left too long, exhausted)
		// stops growth until things are put right.
		if (lovedTicks > 0) {
			lovedTicks--;
		}
		boolean neglected = hunger() < 6 || dirty() && dirtyTicks > 60 * 20 || energy() <= 10 && !asleep();
		int care = (hunger() >= 12 ? 1 : 0) + (!dirty() ? 1 : 0) + (energy() >= 40 || asleep() ? 1 : 0) + (lovedTicks > 0 ? 1 : 0);
		entityData.set(CARE, neglected ? -1 : care);
		int rate = neglected ? 0 : 4 + 3 * care; // 8 = normal pace, 16 = twice as fast
		int needed = 8 * Math.max(1, dev.lifestages.LifeConfig.get().ticks(s.minMinutes));
		growTicks += rate;
		entityData.set(GROWTH, (int) Math.min(1000L, growTicks * 1000L / needed));
		if (growTicks >= needed) {
			growUp(level);
		}
		cry(level);
	}

	/** Pace of growth: 0 when neglected, 1 at normal care, up to 2 with great care. */
	public float growthSpeed() {
		int care = care();
		return care < 0 ? 0f : (4 + 3 * care) / 8f;
	}

	private void growUp(ServerLevel level) {
		growTicks = 0;
		Stage next = stage().next();
		entityData.set(STAGE, next.ordinal());
		entityData.set(GROWTH, 0);
		applyStage();
		heal(getMaxHealth());
		level.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.6f);
		level.sendParticles(ModParticles.SPARKLE, getX(), getY() + 0.5, getZ(), 16, 0.4, 0.4, 0.4, 0.05);
		String name = getName().getString();
		if (next == GROWN) {
			tell(name + " is all grown up! Thank you for raising them. They hand you a present.", ChatFormatting.GOLD);
			ServerPlayer parent = owner();
			if (parent != null) {
				ItemStack gift = new ItemStack(ModBlocks.PRESENT_RARE);
				gift.set(net.minecraft.core.component.DataComponents.CONTAINER_LOOT, new net.minecraft.world.item.component.SeededContainerLoot(
						dev.lifestages.block.PresentBlockEntity.lootTable("rare"), random.nextLong()));
				dev.lifestages.util.Give.item(parent, gift);
				dev.lifestages.registry.ModAdvancements.award(parent, "raised_right");
			}
			return;
		}
		String article = "aeiouAEIOU".indexOf(next.label.charAt(0)) >= 0 ? "an " : "a ";
		tell(name + " is now " + article + next.label + "!" + (next == Stage.PRESCHOOLER ? " No more diapers!" : ""), ChatFormatting.LIGHT_PURPLE);
	}

	/** Why the baby is upset, or null. */
	public String need() {
		if (hunger() <= 5) {
			return "hungry";
		}
		if (stage().wearsDiaper() && dirty() && dirtyTicks > 30 * 20) {
			return "needs a diaper change";
		}
		if (!asleep() && energy() <= 15) {
			return "tired";
		}
		if (getHealth() < getMaxHealth() * 0.5f) {
			return "hurt";
		}
		return null;
	}

	private void cry(ServerLevel level) {
		if (calmTicks > 0) {
			calmTicks--;
		}
		String need = asleep() ? null : need();
		if (!stage().hasEmotes()) {
			// big kids tell you instead of crying
			entityData.set(CRYING, false);
			ServerPlayer parent = owner();
			if (need != null && calmTicks == 0 && --complainCooldown <= 0 && parent != null && parent.level() == level) {
				complainCooldown = 45 * 20;
				parent.sendOverlayMessage(Component.literal(getName().getString() + ": I'm " + need + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return;
		}
		boolean crying = need != null && calmTicks == 0;
		entityData.set(CRYING, crying);
		if (!crying || --cryCooldown > 0) {
			return;
		}
		cryCooldown = 8 * 20;
		level.playSound(null, blockPosition(), ModSounds.CRY, SoundSource.NEUTRAL, 1.2f, 1.25f + random.nextFloat() * 0.15f);
		level.sendParticles(ModParticles.TEAR, getX(), getEyeY(), getZ(), 4, 0.15, 0.05, 0.15, 0.02);
		ServerPlayer parent = owner();
		if (parent != null && parent.level() == level && parent.distanceToSqr(this) < 96 * 96) {
			Net.cryArrow(parent, this);
			if (parent.distanceToSqr(this) > 6 * 6 || random.nextInt(3) == 0) {
				parent.sendOverlayMessage(Component.literal(getName().getString() + " is crying: " + need + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	private void fallAsleep() {
		entityData.set(ASLEEP, true);
		entityData.set(CRYING, false);
		getNavigation().stop();
	}

	private void tell(String text, ChatFormatting color) {
		ServerPlayer parent = owner();
		if (parent != null) {
			parent.sendSystemMessage(Component.literal(text).withStyle(color));
		}
	}

	// ---- care ----

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Stage s = stage();
		if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			if (player.isShiftKeyDown()) {
				return carry(player);
			}
			// a cuddle (or, for big kids, a hug)
			calmTicks = 30 * 20;
			love();
			level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3, getZ(), 3, 0.2, 0.1, 0.2, 0.0);
			level.playSound(null, blockPosition(), ModSounds.GIGGLE, SoundSource.NEUTRAL, 0.8f, 1.3f);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ModItems.MILK_BOTTLE)) {
			if (hunger() >= MAX_HUNGER) {
				hint(player, getName().getString() + " is full.");
				return InteractionResult.SUCCESS;
			}
			feed(level, 6);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			stack.consume(1, player);
			if (player instanceof ServerPlayer sp) {
				dev.lifestages.util.Give.item(sp, bottle);
			}
			return InteractionResult.SUCCESS;
		}
		var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
		if (stack.is(Feeding.SUGARY) || food != null) {
			if (grownUp()) {
				hint(player, getName().getString() + " can feed themselves now.");
				return InteractionResult.SUCCESS;
			}
			if (s == Stage.NEWBORN) {
				hint(player, "Newborns can only drink milk.");
				return InteractionResult.SUCCESS;
			}
			if (hunger() >= MAX_HUNGER && !stack.is(Feeding.SUGARY)) {
				hint(player, getName().getString() + " is full.");
				return InteractionResult.SUCCESS;
			}
			feed(level, food != null ? Math.max(2, food.nutrition()) : 1);
			if (stack.is(Feeding.SUGARY) && s.getsSugarRush()) {
				entityData.set(SUGAR, 30 * 20);
				entityData.set(ASLEEP, false);
				tell(getName().getString() + " has a SUGAR RUSH! Watch out, they'll run off!", ChatFormatting.GOLD);
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ModItems.CLEAN_DIAPER)) {
			if (!s.wearsDiaper()) {
				hint(player, getName().getString() + " is out of diapers now.");
				return InteractionResult.SUCCESS;
			}
			if (!dirty()) {
				hint(player, getName().getString() + "'s diaper is still clean.");
				return InteractionResult.SUCCESS;
			}
			setDirty(false);
			stack.consume(1, player);
			ItemStack dirtyDiaper = new ItemStack(ModItems.DIRTY_DIAPER);
			if (player instanceof ServerPlayer sp) {
				dev.lifestages.util.Give.item(sp, dirtyDiaper);
			}
			level.playSound(null, blockPosition(), ModSounds.DIAPER_CHANGE, SoundSource.NEUTRAL, 1.0f, 1.0f);
			level.sendParticles(ModParticles.SPARKLE, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.0);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ModItems.RATTLE) || stack.is(ModBlocks.TEDDY_BEAR.asItem())) {
			calmTicks = 60 * 20;
			love();
			level.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.3, getZ(), 3, 0.2, 0.1, 0.2, 1.0);
			level.playSound(null, blockPosition(), stack.is(ModItems.RATTLE) ? ModSounds.RATTLE : SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.8f, 1.4f);
			level.playSound(null, blockPosition(), ModSounds.GIGGLE, SoundSource.NEUTRAL, 0.8f, 1.3f);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private static void hint(Player player, String text) {
		if (player instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
		}
	}

	private void feed(ServerLevel level, int amount) {
		setHunger(hunger() + amount);
		feedsSincePoop++;
		level.playSound(null, blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.6f, 1.4f);
		level.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0.0);
	}

	/** Sneak + right-click with an empty hand: pick the baby up, or put them down. */
	private InteractionResult carry(Player player) {
		if (getVehicle() == player) {
			stopRiding();
			return InteractionResult.SUCCESS;
		}
		if (stage().atLeast(Stage.TEENAGER)) {
			hint(player, getName().getString() + " is too big to carry.");
			return InteractionResult.SUCCESS;
		}
		if (player.isPassenger() || !dev.lifestages.life.Carrying.canHold(player, this)) {
			hint(player, player.getPassengers().size() >= 2 ? "Your carrier is full." : "Only two newborns or infants fit in the carrier at once.");
			return InteractionResult.SUCCESS;
		}
		startRiding(player, true, true);
		entityData.set(SUGAR, 0);
		return InteractionResult.SUCCESS;
	}

	/** Lays the carried baby in a crib, where they settle down for a nap. Big kids don't fit any more. */
	public void putInCrib(BlockPos crib, Player by) {
		if (stage().atLeast(Stage.PRESCHOOLER)) {
			hint(by, getName().getString() + " is too big for the crib now.");
			return;
		}
		stopRiding();
		teleportTo(crib.getX() + 0.5, crib.getY() + 0.3, crib.getZ() + 0.5);
		getNavigation().stop();
		if (energy() < MAX_ENERGY - 10) {
			fallAsleep();
		}
	}

	// ---- danger: rescued, never lost ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player) {
			return false; // nobody hits a baby
		}
		if (amount >= getHealth() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			rescue(level, source);
			return false;
		}
		entityData.set(ASLEEP, false);
		calmTicks = 0;
		cryCooldown = 0;
		return super.hurtServer(level, source, amount);
	}

	/** A guardian angel steps in: the baby is brought home, shaken, and the parent hears about it. */
	private void rescue(ServerLevel level, DamageSource source) {
		closeCalls++;
		stopRiding();
		clearFire();
		setHealth(4.0f);
		setAirSupply(getMaxAirSupply());
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		BlockPos to = home != null ? home : blockPosition();
		teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
		level.playSound(null, blockPosition(), ModSounds.CRY, SoundSource.NEUTRAL, 1.5f, 1.2f);
		calmTicks = 0;
		cryCooldown = 0;
		tell("Oh no! " + getName().getString() + " was nearly badly hurt (" + source.getMsgId() + ") and was rushed home. Keep them safe! (close calls: "
				+ closeCalls + ")", ChatFormatting.RED);
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	// ---- saving ----

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putString("Parent", entityData.get(OWNER));
		out.putInt("Stage", entityData.get(STAGE));
		out.putInt("Look", look());
		out.putInt("Hunger", hunger());
		out.putInt("Energy", energy());
		out.putBoolean("Dirty", dirty());
		out.putBoolean("Asleep", asleep());
		out.putInt("GrowTicks", growTicks);
		out.putInt("Loved", lovedTicks);
		out.putInt("CloseCalls", closeCalls);
		if (home != null) {
			out.putIntArray("Home", new int[] {home.getX(), home.getY(), home.getZ()});
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		entityData.set(OWNER, in.getStringOr("Parent", ""));
		entityData.set(STAGE, in.getIntOr("Stage", 0));
		entityData.set(LOOK, in.getIntOr("Look", 1));
		setHunger(in.getIntOr("Hunger", 16));
		setEnergy(in.getIntOr("Energy", 80));
		entityData.set(DIRTY, in.getBooleanOr("Dirty", false));
		entityData.set(ASLEEP, in.getBooleanOr("Asleep", false));
		growTicks = in.getIntOr("GrowTicks", 0);
		lovedTicks = in.getIntOr("Loved", 0);
		closeCalls = in.getIntOr("CloseCalls", 0);
		in.getIntArray("Home").filter(a -> a.length == 3).ifPresent(a -> {
			home = new BlockPos(a[0], a[1], a[2]);
			setHomeTo(home, 16);
		});
		applyStage();
	}

	// ---- goals ----

	/** Curious: toddles off to investigate an animal, a flower, a dropped item or a player nearby, or just somewhere new. */
	static class CuriousGoal extends Goal {
		private final BabyEntity baby;
		private Vec3 target;

		CuriousGoal(BabyEntity baby) {
			this.baby = baby;
			setFlags(java.util.EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (baby.asleep() || baby.isPassenger() || baby.sugarRush() || baby.getRandom().nextInt(baby.stage() == Stage.NEWBORN ? 160 : baby.grownUp() ? 120 : 80) != 0) {
				return false;
			}
			target = interesting();
			if (target == null) {
				target = DefaultRandomPos.getPos(baby, baby.stage().atMost(Stage.TODDLER) ? 8 : 14, 3);
			}
			return target != null;
		}

		private Vec3 interesting() {
			if (baby.getRandom().nextInt(3) == 0) {
				return null;
			}
			List<Entity> near = baby.level().getEntities(baby, baby.getBoundingBox().inflate(8),
					e -> e instanceof Animal || e instanceof Player || e instanceof net.minecraft.world.entity.item.ItemEntity);
			if (!near.isEmpty()) {
				Entity e = near.get(baby.getRandom().nextInt(near.size()));
				return baby.isWithinHome(e.blockPosition()) ? e.position() : null;
			}
			BlockPos center = baby.blockPosition();
			for (int i = 0; i < 12; i++) {
				BlockPos p = center.offset(baby.getRandom().nextInt(13) - 6, baby.getRandom().nextInt(3) - 1, baby.getRandom().nextInt(13) - 6);
				if (baby.level().getBlockState(p).is(BlockTags.FLOWERS) && baby.isWithinHome(p)) {
					return Vec3.atBottomCenterOf(p);
				}
			}
			return null;
		}

		@Override
		public void start() {
			baby.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
		}

		@Override
		public boolean canContinueToUse() {
			return !baby.getNavigation().isDone() && !baby.asleep() && !baby.isPassenger();
		}
	}

	/**
	 * On a sugar rush the baby bolts: three times as fast, a new direction every few seconds, well past home, and
	 * now and then straight toward trouble - water, a drop, or a monster.
	 */
	static class SugarRushGoal extends Goal {
		private final BabyEntity baby;

		SugarRushGoal(BabyEntity baby) {
			this.baby = baby;
			setFlags(java.util.EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return baby.sugarRush() && !baby.isPassenger() && !baby.asleep();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void stop() {
			baby.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (!baby.getNavigation().isDone() && baby.tickCount % 60 != 0) {
				return;
			}
			Vec3 to = baby.getRandom().nextInt(4) == 0 ? trouble() : null;
			if (to == null) {
				to = DefaultRandomPos.getPos(baby, 14, 4);
			}
			if (to != null) {
				baby.getNavigation().moveTo(to.x, to.y, to.z, 3.0);
			}
		}

		private Vec3 trouble() {
			List<Monster> monsters = baby.level().getEntitiesOfClass(Monster.class, baby.getBoundingBox().inflate(16));
			if (!monsters.isEmpty()) {
				return monsters.get(0).position();
			}
			BlockPos center = baby.blockPosition();
			for (int i = 0; i < 40; i++) {
				BlockPos p = center.offset(baby.getRandom().nextInt(25) - 12, baby.getRandom().nextInt(5) - 2, baby.getRandom().nextInt(25) - 12);
				boolean water = !baby.level().getFluidState(p).isEmpty() && baby.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER);
				boolean edge = baby.level().getBlockState(p).isAir() && baby.level().getBlockState(p.below()).isAir()
						&& baby.level().getBlockState(p.below(2)).isAir() && baby.level().getBlockState(p.below(3)).isAir();
				if (water || edge) {
					return Vec3.atBottomCenterOf(p);
				}
			}
			return null;
		}
	}
}
