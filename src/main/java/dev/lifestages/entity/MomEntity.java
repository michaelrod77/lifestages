package dev.lifestages.entity;

import dev.lifestages.LifeConfig;
import dev.lifestages.block.MusicBoxBlockEntity;
import dev.lifestages.block.Nursery;
import dev.lifestages.life.Bodily;
import dev.lifestages.life.Carrying;
import dev.lifestages.life.Family;
import dev.lifestages.life.HouseBuilder;
import dev.lifestages.life.Feeding;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Moms;
import dev.lifestages.life.Stage;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModItems;
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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The NPC Mom. She looks after one player for their whole life: carries, feeds, changes and protects them
 * as a baby, stands by when a player parent is near, and moves into a home of her own once they are a teenager.
 */
public class MomEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(MomEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ELDERLY = SynchedEntityData.defineId(MomEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID childId;
	private BlockPos home;
	private int recoverTicks;
	private int freePlayTicks;
	private int diaperTimer = -1;
	private int feedCooldown;
	private boolean pendingCake;
	// ---- daily routine for babies ----
	private int ticksSinceFeed = -1;
	private int ticksSinceNap;
	private boolean napping;
	private int napsAtStart;
	private int napTicks;
	private boolean cryRequested;
	private int offhandTicks;
	private boolean cribChecked;
	private boolean musicOn;
	private BlockPos cribPos;
	private int buildCooldown;
	// ---- crying, changing table and playtime ----
	/** Ticks left to answer the baby's cry; while above 0 she drops everything else. */
	private int cryTicks;
	private static final int CRY_TICKS = 30 * 20;
	private BlockPos tablePos;
	private int changeTicks;
	/** -1 until the baby is lying on the changing table, then counts up. */
	private int tableTicks = -1;
	private int playTicks;
	private int playCooldown = 60 * 20;
	private BlockPos playSpot;
	private boolean playWithTeddy;
	private boolean playStarted;
	// ---- tidying: dirty diapers go in the trash can ----
	private net.minecraft.world.entity.item.ItemEntity trashTarget;
	private int trashCarried;
	private int tidyTicks;
	/** Diapers she couldn't get to: left alone, so she doesn't try forever. */
	private final java.util.Set<UUID> trashIgnore = new java.util.HashSet<>();
	// ---- safety ----
	private int patTicks;
	private int rescueTicks;
	private int lastRefusal = -1000;
	private boolean saidNight;
	private Vec3 safeSpot;
	private int safeSpotAge;
	private int wetTicks;
	// ---- fixing drops around the house ----
	private dev.lifestages.life.DropFixer.Fix dropFix;
	private int dropScan;
	private int dropCooldown;
	private int dropTicks;
	private boolean saidDrops;
	private final java.util.Set<Long> dropSkip = new java.util.HashSet<>();
	private int playTravel;

	public MomEntity(EntityType<? extends MomEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		getNavigation().setCanOpenDoors(true);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.FIRE, -1.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.FIRE_IN_NEIGHBOR, 16.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGING, -1.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DAMAGING_IN_NEIGHBOR, 16.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, -1.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.POWDER_SNOW, -1.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 16.0f);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 4.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 80.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.5)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LOOK, 1);
		builder.define(ELDERLY, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creeper.class, 6.0f, 1.0, 1.35));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
		goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	public void setup(ServerPlayer child, int look, BlockPos home) {
		this.childId = child.getUUID();
		this.home = home;
		entityData.set(LOOK, Mth.clamp(look, 1, 6));
		setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.FRYING_PAN));
		setCustomName(Component.literal(child.getName().getString() + "'s Mom"));
		setCustomNameVisible(true);
	}

	public int look() {
		return entityData.get(LOOK);
	}

	public boolean isElderly() {
		return entityData.get(ELDERLY);
	}

	public UUID childId() {
		return childId;
	}

	public void giveCakeOnVisit() {
		pendingCake = true;
	}

	public void scheduleDiaper() {
		if (diaperTimer < 0) {
			diaperTimer = 10 * 20;
		}
	}

	/** The carried child asked to be put down. Allowed unless danger is near. */
	public boolean allowDown() {
		ServerPlayer child = child();
		if (child == null) {
			return true;
		}
		LifeData d = Life.of(child);
		String reason = threat(child) != null ? "Not now, sweetie. There's a monster nearby!"
				: nightOutside(child, d) ? "It's dark out. I'll put you down once we're inside."
				: isInWater() || isInLava() || hazardAround(child.blockPosition()) ? "Not here, it isn't safe."
				: null;
		if (reason != null) {
			if (tickCount - lastRefusal > 60) {
				lastRefusal = tickCount;
				child.sendOverlayMessage(Component.literal("Mom: " + reason).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return false;
		}
		freePlayTicks = 60 * 20;
		stopPlay();
		return true;
	}

	private ServerPlayer child() {
		return childId == null || !(level() instanceof ServerLevel server) ? null : server.getServer().getPlayerList().getPlayer(childId);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		// 0.2 forward keeps the baby's head inside her own width, so turning in a doorway never pushes it into a wall
		return new Vec3(0.0, dims.height() * 0.5, 0.2).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) {
			return false;
		}
		if (source.getEntity() instanceof Player player && !player.isCreative()) {
			if (player instanceof ServerPlayer sp && tickCount % 10 == 0) {
				sp.sendSystemMessage(Component.literal(getName().getString() + ": Hey! Not in front of the baby.").withStyle(ChatFormatting.YELLOW));
			}
			return false;
		}
		if (recoverTicks > 0) {
			return false;
		}
		if (amount >= getHealth()) {
			// She never dies in a fight: she grabs the baby and gets away to recover.
			setHealth(1.0f);
			recoverTicks = 10 * 20;
			setTarget(null);
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	/**
	 * Mom with her frying pan is the strongest fighter in the game: one bonk finishes any mob. (In a player's hand
	 * the pan is an ordinary weapon.)
	 */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (!getMainHandItem().is(ModItems.FRYING_PAN) || !(target instanceof LivingEntity living) || target instanceof Player) {
			return super.doHurtTarget(level, target);
		}
		living.hurtServer(level, damageSources().mobAttack(this), Math.max(1000.0f, living.getMaxHealth() * 4 + living.getAbsorptionAmount()));
		if (living.isAlive()) {
			living.kill(level);
		}
		Vec3 push = living.position().subtract(position()).normalize();
		living.push(push.x * 0.8, 0.35, push.z * 0.8);
		level.playSound(null, living.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 1.9f);
		level.playSound(null, living.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.6f);
		level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.7, living.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
		setLastHurtMob(living);
		return true;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer child = child();
		if (child == null) {
			if (childId == null) {
				Moms.adoptNearest(this);
			} else {
				discard();
			}
			return;
		}
		LifeData d = Life.of(child);
		entityData.set(ELDERLY, Moms.momAge(child) >= 65);
		if (tickCount % 100 == 0 && getHealth() < getMaxHealth()) {
			heal(2.0f);
		}
		tickBuild(level, child);
		if (recoverTicks > 0) {
			recoverTicks--;
			heal(0.5f);
			Vec3 away = position().subtract(child.position()).normalize();
			if (!isVehicle() && distanceToSqr(child) < 4) {
				Carrying.pickUp(this, child);
			}
			getNavigation().moveTo(getX() + away.x * 6, getY(), getZ() + away.z * 6, 1.4);
			return;
		}
		if (selfRescue(level, child)) {
			return;
		}
		if (d.stage().atLeast(Stage.TEENAGER)) {
			atHome(level, child);
			return;
		}
		boolean parentNear = Family.caregivers(child).stream().anyMatch(p -> p.level() == level() && p.distanceToSqr(child) < 16 * 16);
		if (parentNear) {
			cryTicks = 0; // a parent is here to answer
			stopPlay();
			if (child.getVehicle() == this) {
				Carrying.drop(child);
			}
			follow(child, 5.0);
			return;
		}
		Monster danger = threat(child);
		if (danger == null && getTarget() != null
				&& (!getTarget().isAlive() || getTarget().distanceToSqr(child) > 16 * 16 || safelyInside(child, d))) {
			setTarget(null); // threat gone, or locked outside
		}
		Stage s = d.stage();
		if (ticksSinceFeed < 0) {
			ticksSinceFeed = Math.max(0, feedInterval(s) - 30 * 20); // first bottle about 30 seconds after she arrives
		}
		ticksSinceFeed++;
		ticksSinceNap++;
		if (offhandTicks > 0 && --offhandTicks == 0) {
			setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		if (!cribChecked && home != null) {
			cribChecked = true;
			cribPos = Moms.planHouse(level, child, home);
			home = Moms.homeOf(child);
		}
		boolean carryable = Moms.carryable(child);
		double dist = distanceTo(child);
		if (freePlayTicks > 0) {
			freePlayTicks--;
		}
		if (rescue(level, child, d, danger)) {
			return;
		}
		keepDoorShut(level, d);

		// A cry comes first, then a diaper change in progress.
		if (cryTicks > 0 && danger == null) {
			stopPlay();
			if (tablePos == null) {
				answerCry(level, child, d);
				return;
			}
			cryTicks = 0; // already on the way to the changing table
		}
		if (tablePos != null) {
			if (danger == null) {
				changeStep(level, child);
				return;
			}
			tablePos = null;
			tableTicks = -1;
			scheduleDiaper();
		}

		// Nap time: rock the baby in her arms, or lay them in the crib, until a nap is done.
		if (napping) {
			napTicks++;
			if (d.naps > napsAtStart || danger != null || napTicks > 90 * 20 || freePlayTicks > 0) {
				endNap(child, danger == null && d.naps > napsAtStart);
			} else {
				napStep(level, child);
				careFor(level, child, d, dist, danger);
				return;
			}
		}
		careFor(level, child, d, dist, danger);
		if (tablePos != null) {
			return;
		}
		if (danger == null && carryable && s.naps > d.naps && ticksSinceNap >= napInterval(s) && ticksSinceFeed > 15 * 20 && freePlayTicks == 0) {
			stopPlay();
			startNap(child, d);
			return;
		}
		// Tidying: dirty diapers lying about go in the trash can (with the baby in her arms).
		if (danger == null && playSpot == null && (child.getVehicle() == this || trashCarried > 0) && tidyStep(level, child)) {
			return;
		}
		// Steep drops around the house get filled in or fenced off.
		if (danger == null && playSpot == null && landscapeStep(level, child, d)) {
			return;
		}
		// Playtime when nothing else needs doing.
		if (danger != null || freePlayTicks > 0 || !carryable) {
			stopPlay();
		} else if (playSpot != null || tryStartPlay(level, child, d)) {
			playStep(level, child);
			return;
		}

		boolean wantCarry = carryable && (freePlayTicks == 0 || danger != null || dist > 10);
		if (wantCarry && child.getVehicle() != this && child.getVehicle() == null) {
			if (dist < 2.0) {
				Carrying.pickUp(this, child);
				freePlayTicks = 0;
			} else {
				getNavigation().moveTo(child, 1.2);
			}
		} else if (child.getVehicle() != this) {
			follow(child, s.atMost(Stage.PRESCHOOLER) ? 6.0 : 10.0);
		}
		// Where to be: home at night, strolling near home by day.
		if (child.getVehicle() == this && getTarget() == null && home != null) {
			double fromHome = blockPosition().distSqr(home);
			if (nightTime(level) ? fromHome > 9 : fromHome > 20 * 20) {
				getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
			} else if (!nightTime(level) && getNavigation().isDone() && random.nextInt(240) == 0) {
				getNavigation().moveTo(home.getX() + random.nextInt(13) - 6 + 0.5, home.getY(), home.getZ() + random.nextInt(13) - 6 + 0.5, 0.6);
			}
		}
	}

	/** Ticks between feeds so a baby can meet its milk or food goal in its stage's minimum time. */
	private static int feedInterval(Stage s) {
		int min = LifeConfig.get().ticks(s.minMinutes / 2.0); // pace for a baby whose goals are all met
		int feeds = s.milk > 0 ? s.milk : (int) Math.ceil(s.food / 4.0);
		return feeds > 0 ? Math.max(30 * 20, min / (feeds + 1)) : 3 * 60 * 20;
	}

	private static int napInterval(Stage s) {
		return Math.max(45 * 20, LifeConfig.get().ticks(s.minMinutes / 2.0) / (s.naps + 1));
	}

	private void careFor(ServerLevel level, ServerPlayer child, LifeData d, double dist, Monster danger) {
		Stage s = d.stage();
		boolean close = child.getVehicle() == this || dist < 3.0;
		boolean needsMilk = s.milk > d.milk;
		boolean needsFood = s.food > d.food;
		boolean hungry = child.getFoodData().getFoodLevel() < (s.atMost(Stage.TODDLER) ? 16 : 14);
		boolean due = ((needsMilk || needsFood) && ticksSinceFeed >= feedInterval(s))
				|| (hungry && ticksSinceFeed > 20 * 20);
		if (due && close && danger == null) {
			feed(level, child, d, s, needsMilk);
			ticksSinceFeed = 0;
		}
		if (d.diaperDirty) {
			scheduleDiaper();
		}
		if (diaperTimer > 0) {
			diaperTimer--;
		} else if (diaperTimer == 0 && close && danger == null && freePlayTicks == 0) {
			diaperTimer = -1;
			if (Life.of(child).diaperDirty) {
				startChange(level, child);
			}
		}
	}

	/** Hungry enough that a crying baby gets fed right away. */
	private boolean hungry(ServerPlayer child, LifeData d) {
		Stage s = d.stage();
		boolean goal = (s.milk > d.milk || s.food > d.food) && ticksSinceFeed >= feedInterval(s) / 3;
		return ticksSinceFeed > 10 * 20 && (goal || child.getFoodData().getFoodLevel() < 18);
	}

	/** Tired enough for a nap: naps still needed and the last one was a while ago, or it's night. */
	private boolean tired(ServerLevel level, LifeData d) {
		Stage s = d.stage();
		return s.naps > d.naps && (ticksSinceNap >= napInterval(s) / 3 || level.isDarkOutside());
	}

	// ---- answering a cry ----

	/**
	 * The baby cried: drop everything, pick them up if they're on the ground, then fix whatever is wrong - a dirty
	 * diaper first, then hunger, then tiredness. If nothing is wrong, a cuddle and a little playtime.
	 */
	private void answerCry(ServerLevel level, ServerPlayer child, LifeData d) {
		cryTicks--;
		if (napping) {
			endNap(child, false);
		}
		if (child.getVehicle() != this) {
			if (distanceTo(child) >= 2.2 && cryTicks < CRY_TICKS - 10 * 20) {
				reachChild(child); // stuck behind something: a mother always finds a way
			}
			if (distanceTo(child) < 2.2) {
				Carrying.pickUp(this, child);
			} else {
				getNavigation().moveTo(child, 1.35);
				return;
			}
		}
		getNavigation().stop();
		getLookControl().setLookAt(child);
		cryTicks = 0;
		cryRequested = false;
		freePlayTicks = 0;
		if (d.diaperDirty) {
			say(child, "Oh! Somebody needs a fresh diaper.");
			startChange(level, child);
		} else if (hungry(child, d)) {
			say(child, "Shh, shh... you're hungry, aren't you?");
			feed(level, child, d, d.stage(), d.stage().milk > d.milk);
			ticksSinceFeed = 0;
		} else if (tired(level, d)) {
			startNap(child, d);
		} else {
			say(child, random.nextBoolean() ? "Shh, shh. Mommy's here." : "There, there. What's the matter, little one?");
			comfort();
			level.sendParticles(ParticleTypes.HEART, child.getX(), child.getEyeY() + 0.4, child.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			playCooldown = Math.min(playCooldown, 5 * 20); // a little playtime cheers them up
		}
	}

	// ---- the changing table ----

	/** Changes the diaper on the nearest changing table within 40 blocks, or right where she is if there's none. */
	private void startChange(ServerLevel level, ServerPlayer child) {
		diaperTimer = -1;
		tablePos = Nursery.nearestTable(level, child.blockPosition(), 40);
		changeTicks = 0;
		tableTicks = -1;
		if (tablePos == null) {
			finishChange(level, child, false);
			return;
		}
		stopPlay();
		if (napping) {
			endNap(child, false);
		}
	}

	private void changeStep(ServerLevel level, ServerPlayer child) {
		if (!level.getBlockState(tablePos).is(ModBlocks.CHANGING_TABLE) || ++changeTicks > 45 * 20) {
			tablePos = null; // couldn't get there: change right here
			finishChange(level, child, false);
			return;
		}
		if (tableTicks < 0) {
			if (child.getVehicle() != this) {
				if (distanceTo(child) >= 2.2 && changeTicks > 10 * 20) {
					reachChild(child);
				}
				if (distanceTo(child) < 2.2) {
					Carrying.pickUp(this, child);
				} else {
					getNavigation().moveTo(child, 1.3);
				}
				return;
			}
			if (blockPosition().distSqr(tablePos) > 5) {
				if (changeTicks > 30 * 20) {
					teleportTo(tablePos.getX() + 0.5, tablePos.getY() + 1, tablePos.getZ() + 0.5); // can't find the way in: hop over
				} else if (getNavigation().isDone()) {
					getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.1);
				}
				return;
			}
			Carrying.drop(child);
			child.teleportTo(tablePos.getX() + 0.5, tablePos.getY() + 0.9, tablePos.getZ() + 0.5);
			say(child, "Up on the changing table you go!");
			hold(new ItemStack(ModItems.CLEAN_DIAPER));
			offhandTicks = 80;
			tableTicks = 0;
			return;
		}
		getNavigation().stop();
		getLookControl().setLookAt(child);
		if (tableTicks++ % 15 == 0) {
			swing(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, tablePos, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.5f, 1.4f);
		}
		if (tableTicks >= 60) {
			finishChange(level, child, true);
			tablePos = null;
			tableTicks = -1;
			if (child.getVehicle() == null && distanceTo(child) < 3.0) {
				Carrying.pickUp(this, child);
			}
		}
	}

	private void finishChange(ServerLevel level, ServerPlayer child, boolean onTable) {
		LifeData fresh = Life.of(child);
		if (!fresh.diaperDirty) {
			return;
		}
		fresh.diaperDirty = false;
		fresh.diaperTicks = 0;
		Life.save(child, fresh);
		say(child, onTable ? "All clean! Doesn't that feel better?" : "Phew! Let's get you a fresh diaper.");
		level.playSound(null, blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.NEUTRAL, 0.6f, 0.6f);
		level.playSound(null, blockPosition(), ModSounds.DIAPER_CHANGE, SoundSource.NEUTRAL, 1.0f, 1.0f);
		if (Nursery.nearestTrash(level, blockPosition(), 40) != null) {
			trashCarried++; // she takes it to the trash can
			tidyTicks = 0;
		} else {
			spawnAtLocation(level, new ItemStack(ModItems.DIRTY_DIAPER));
		}
	}

	// ---- tidying up ----

	/**
	 * Picks up dirty diapers lying within 12 blocks and throws them in the nearest trash can (within 40 blocks),
	 * carrying the baby along. Returns true while she's busy with it.
	 */
	private boolean tidyStep(ServerLevel level, ServerPlayer child) {
		if (trashCarried == 0 && (trashTarget == null || !trashTarget.isAlive())) {
			trashTarget = null;
			if (tickCount % 40 != 0 || Nursery.nearestTrash(level, blockPosition(), 40) == null) {
				return false;
			}
			trashTarget = dirtyDiaperNear(level);
			if (trashTarget == null) {
				return false;
			}
			tidyTicks = 0;
		}
		if (++tidyTicks > 40 * 20) {
			if (trashTarget != null) {
				trashIgnore.add(trashTarget.getUUID()); // can't get to it: leave that one be
			}
			trashTarget = null;
			if (trashCarried > 0) {
				dropTrash(level);
			}
			return false;
		}
		if (trashTarget != null) {
			if (distanceTo(trashTarget) > 2.5) {
				if (tidyTicks % 10 == 1) {
					getNavigation().moveTo(trashTarget, 1.0);
				}
				return true;
			}
			trashCarried += trashTarget.getItem().getCount();
			trashTarget.discard();
			trashTarget = null;
			level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 0.8f);
			if (random.nextInt(3) == 0) {
				say(child, random.nextBoolean() ? "Ew! Who left this here?" : "Into the trash you go.");
			}
		}
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.DIRTY_DIAPER));
		offhandTicks = 0;
		BlockPos can = Nursery.nearestTrash(level, blockPosition(), 40);
		if (can == null) {
			dropTrash(level);
			return false;
		}
		if (blockPosition().distSqr(can) > 5) {
			if (tidyTicks % 10 == 1 || getNavigation().isDone()) {
				getNavigation().moveTo(can.getX() + 0.5, can.getY(), can.getZ() + 0.5, 1.0);
			}
			return true;
		}
		getNavigation().stop();
		getLookControl().setLookAt(can.getX() + 0.5, can.getY() + 0.8, can.getZ() + 0.5);
		swing(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		dev.lifestages.block.TrashCanBlock.dispose(level, can);
		trashCarried = 0;
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		return true;
	}

	private net.minecraft.world.entity.item.ItemEntity dirtyDiaperNear(ServerLevel level) {
		net.minecraft.world.entity.item.ItemEntity best = null;
		for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, getBoundingBox().inflate(12),
				e -> e.getItem().is(ModItems.DIRTY_DIAPER) && e.getAge() > 100 && !outsideAtNight(level, e) && !trashIgnore.contains(e.getUUID()))) {
			if (best == null || item.distanceToSqr(this) < best.distanceToSqr(this)) {
				best = item;
			}
		}
		return best;
	}

	/** No trash can any more: put the diapers back down. */
	private void dropTrash(ServerLevel level) {
		for (int i = 0; i < trashCarried; i++) {
			spawnAtLocation(level, new ItemStack(ModItems.DIRTY_DIAPER));
		}
		trashCarried = 0;
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}

	/**
	 * Looks for drops of more than 3 blocks within 10 blocks of the finished house (every 5 seconds while idle) and
	 * fixes them one block at a time: fills shallow ones with dirt, fences off the top of deep ones. Only by day,
	 * with the baby in her arms. Returns true while she's busy with it.
	 */
	private boolean landscapeStep(ServerLevel level, ServerPlayer child, LifeData d) {
		BlockPos house = finishedHouse(d);
		if (house == null || nightTime(level) || child.getVehicle() != this) {
			dropFix = null;
			return false;
		}
		if (dropFix == null) {
			if (--dropScan > 0) {
				return false;
			}
			dropScan = 100;
			dropFix = dev.lifestages.life.DropFixer.next(level, house, dropSkip);
			dropTicks = 0;
			if (dropFix == null) {
				return false;
			}
			if (!saidDrops) {
				saidDrops = true;
				say(child, "That drop is too steep for little ones. Let me fix it.");
			}
		}
		BlockPos at = dropFix.pos();
		double dx = at.getX() + 0.5 - getX(), dz = at.getZ() + 0.5 - getZ();
		boolean inReach = dx * dx + dz * dz <= 5 * 5 && Math.abs(at.getY() - getY()) <= 6;
		if (++dropTicks > 20 * 20) {
			dropSkip.add(at.asLong()); // can't get to it: leave it for now
			dropFix = null;
			dropScan = 20;
			return false;
		}
		if (!inReach) {
			if (getNavigation().isDone() || dropTicks % 20 == 1) {
				getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.9);
			}
			return true;
		}
		getNavigation().stop();
		getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
		if (--dropCooldown > 0) {
			return true;
		}
		dropCooldown = 10;
		BlockState place = dropFix.fence() ? Block.updateFromNeighbourShapes(Blocks.OAK_FENCE.defaultBlockState(), level, at)
				: Blocks.DIRT.defaultBlockState();
		if (level.getBlockState(at).canBeReplaced() && level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) {
			level.setBlock(at, place, Block.UPDATE_ALL);
			swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, at, place.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 1.0f);
		} else {
			dropSkip.add(at.asLong());
		}
		dropFix = dev.lifestages.life.DropFixer.next(level, house, dropSkip); // usually the same spot, one block up
		dropTicks = 0;
		return dropFix != null;
	}

	/** True while she's fixing a drop around the house. */
	public boolean fixingDrops() {
		return dropFix != null;
	}

	/** True while she's taking dirty diapers to the trash. */
	public boolean tidying() {
		return trashCarried > 0 || trashTarget != null;
	}

	/** Steps out right beside the child, with a puff, when walking there isn't working. */
	private void reachChild(ServerPlayer child) {
		if (child.level() != level() || !(level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		getNavigation().stop();
		teleportTo(child.getX(), child.getY(), child.getZ());
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
	}

	/** True while she's busy at the changing table. */
	public boolean changing() {
		return tablePos != null;
	}

	// ---- playtime ----

	/**
	 * Free time: carry the baby to a safe spot - the front yard on a calm day, otherwise the nursery - set them
	 * down and play with their teddy bear (if they have one) or a rattle.
	 */
	private boolean tryStartPlay(ServerLevel level, ServerPlayer child, LifeData d) {
		if (--playCooldown > 0 || d.diaperDirty || d.stage().atLeast(Stage.PRESCHOOLER)) {
			return false;
		}
		BlockPos house = d.houseState == 2 ? d.houseOrigin() : null;
		BlockPos yard = house != null ? house.offset(HouseBuilder.YARD) : home;
		BlockPos room = house != null ? house.offset(HouseBuilder.NURSERY) : home;
		if (yard == null) {
			playCooldown = 60 * 20;
			return false;
		}
		boolean calm = level.isBrightOutside() && !level.isRaining()
				&& level.getEntitiesOfClass(Monster.class, new AABB(yard).inflate(16)).isEmpty();
		playSpot = calm ? yard : room;
		playWithTeddy = hasTeddy(child);
		playTicks = 50 * 20;
		playStarted = false;
		playTravel = 0;
		say(child, calm ? "It's a lovely day. Let's go play outside!" : house != null ? "Let's go play in your room." : "Playtime!");
		return true;
	}

	private static boolean hasTeddy(ServerPlayer child) {
		var inv = child.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ModBlocks.TEDDY_BEAR.asItem())) {
				return true;
			}
		}
		return false;
	}

	/** True while a play session is going. */
	public boolean playing() {
		return playSpot != null;
	}

	private void playStep(ServerLevel level, ServerPlayer child) {
		if (!playStarted && blockPosition().distSqr(playSpot) > 4) {
			if (++playTravel > 40 * 20) {
				playSpot = blockPosition(); // taking too long: play right here
			} else if (child.getVehicle() != this) {
				if (distanceTo(child) < 2.2) {
					Carrying.pickUp(this, child);
				} else {
					getNavigation().moveTo(child, 1.1);
				}
			} else if (getNavigation().isDone()) {
				if (!getNavigation().moveTo(playSpot.getX() + 0.5, playSpot.getY(), playSpot.getZ() + 0.5, 0.9)) {
					playSpot = blockPosition(); // can't get there: play right here
				}
			}
			return;
		}
		if (!playStarted) {
			playStarted = true;
			if (child.getVehicle() == this) {
				Carrying.drop(child);
				Vec3 front = position().add(getLookAngle().multiply(1.2, 0, 1.2));
				child.teleportTo(front.x, getY() + 0.1, front.z);
			}
			say(child, playWithTeddy ? "Look who came to play... it's Teddy!" : "Who wants the rattle? Shake, shake, shake!");
		}
		playTicks--;
		getNavigation().stop();
		getLookControl().setLookAt(child);
		if (child.getVehicle() == null && distanceTo(child) > 4) {
			getNavigation().moveTo(child, 0.8); // crawled off: follow along
		}
		if (playTicks % 20 == 0) {
			hold(playWithTeddy ? new ItemStack(ModBlocks.TEDDY_BEAR.asItem()) : new ItemStack(ModItems.RATTLE));
			swing(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			if (playWithTeddy) {
				level.playSound(null, blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.8f, 1.5f);
			} else {
				level.playSound(null, blockPosition(), ModSounds.RATTLE, SoundSource.NEUTRAL, 0.8f, 1.4f + random.nextFloat() * 0.3f);
				level.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.2, getZ(), 1, 0.3, 0.1, 0.3, 1.0);
			}
		}
		if (playTicks % 60 == 30) {
			level.sendParticles(ParticleTypes.HEART, child.getX(), child.getEyeY() + 0.3, child.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			if (random.nextInt(3) == 0) {
				level.playSound(null, child.blockPosition(), ModSounds.GIGGLE, SoundSource.PLAYERS, 0.8f, 1.2f);
			}
		}
		if (playTicks <= 0) {
			stopPlay();
			say(child, "That was fun! Up you come.");
		}
	}

	private void stopPlay() {
		if (playSpot != null) {
			playTicks = 0;
			playSpot = null;
			playCooldown = 3 * 60 * 20;
		}
	}

	/** The nearest music box within 8 blocks of the crib, or null. */
	private static BlockPos musicBoxNear(ServerLevel level, BlockPos crib) {
		for (BlockPos p : BlockPos.betweenClosed(crib.offset(-8, -3, -8), crib.offset(8, 3, 8))) {
			if (level.getBlockState(p).is(ModBlocks.MUSIC_BOX)) {
				return p.immutable();
			}
		}
		return null;
	}

	private void feed(ServerLevel level, ServerPlayer child, LifeData d, Stage s, boolean needsMilk) {
		if (s.atMost(Stage.INFANT) || (needsMilk && s == Stage.TODDLER)) {
			hold(new ItemStack(ModItems.MILK_BOTTLE));
			say(child, "Bottle time!");
			Feeding.feedBottle(this, child, null);
			return;
		}
		if (s.getsSugarRush() && random.nextInt(4) == 0) {
			hold(new ItemStack(Items.COOKIE));
			say(child, "A cookie, because you've been so good!");
			child.getFoodData().eat(2, 0.1f);
			d.food += 2;
			Feeding.sugarRush(child, d);
		} else {
			hold(new ItemStack(s == Stage.TODDLER ? Items.APPLE : Items.BREAD));
			say(child, s == Stage.TODDLER ? "Here, some mashed apple." : "Snack time!");
			child.getFoodData().eat(4, 0.5f);
			d.food += 4;
		}
		Bodily.fed(d);
		Life.save(child, d);
		level.playSound(null, child.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
	}

	private void hold(ItemStack stack) {
		setItemSlot(EquipmentSlot.OFFHAND, stack);
		offhandTicks = 40;
	}

	private void say(ServerPlayer child, String text) {
		child.sendSystemMessage(Component.literal("Mom: " + text).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private void startNap(ServerPlayer child, LifeData d) {
		napping = true;
		napsAtStart = d.naps;
		napTicks = 0;
		boolean crib = cribPos != null && level().getBlockState(cribPos).is(ModBlocks.CRIB) && cribPos.distSqr(blockPosition()) < 24 * 24;
		say(child, crib ? "Shh... nap time. Into the crib you go." : "Shh... nap time. Rest in my arms.");
		musicOn = false;
	}

	private void napStep(ServerLevel level, ServerPlayer child) {
		boolean crib = cribPos != null && level.getBlockState(cribPos).is(ModBlocks.CRIB) && cribPos.distSqr(blockPosition()) < 24 * 24;
		if (crib && child.getVehicle() == this) {
			if (blockPosition().distSqr(cribPos) > 4) {
				getNavigation().moveTo(cribPos.getX() + 0.5, cribPos.getY(), cribPos.getZ() + 0.5, 0.9);
				return;
			}
			Carrying.drop(child);
			child.teleportTo(cribPos.getX() + 0.5, cribPos.getY() + 0.3, cribPos.getZ() + 0.5);
			BlockPos box = musicBoxNear(level, cribPos);
			if (box != null && !musicOn) {
				musicOn = true;
				MusicBoxBlockEntity.setPlaying(level, box, true);
				say(child, "Let's wind up your music box...");
			}
		} else if (!crib && child.getVehicle() == null && distanceTo(child) < 2.0) {
			Carrying.pickUp(this, child);
		}
		getNavigation().stop();
		getLookControl().setLookAt(child);
		if (napTicks % 60 == 0) {
			level.sendParticles(ParticleTypes.NOTE, child.getX(), child.getEyeY() + 0.4, child.getZ(), 1, 0.2, 0.1, 0.2, 1.0);
		}
		if (napTicks % 200 == 0) {
			level.playSound(null, blockPosition(), ModSounds.MOM_HUM, SoundSource.NEUTRAL, 0.5f, 0.9f);
		}
	}

	private void endNap(ServerPlayer child, boolean slept) {
		napping = false;
		ticksSinceNap = 0;
		if (slept) {
			say(child, "Did you have a nice nap?");
		}
	}

	/** The baby cried: come sooner with a bottle or a fresh diaper. */
	public void onCry() {
		if (cryTicks <= 0 && child() != null && child().getVehicle() != this) {
			say(child(), "Coming, sweetie! Mommy's coming!");
		}
		cryRequested = true;
		cryTicks = CRY_TICKS;
		comfort();
	}

	/** Builds the next block of the family cottage whenever she is close enough and not fighting. */
	private void tickBuild(ServerLevel level, ServerPlayer child) {
		LifeData d = Life.of(child);
		BlockPos origin = d.houseOrigin();
		if (d.houseState != 1 || origin == null || getTarget() != null || recoverTicks > 0 || cryTicks > 0 || tablePos != null || playSpot != null || tidying()) {
			return;
		}
		if (--buildCooldown > 0) {
			return;
		}
		buildCooldown = 10; // a block every half second
		if (threat(child) != null) {
			return;
		}
		BlockPos center = origin.offset(HouseBuilder.CENTER);
		if (blockPosition().distSqr(center) > 12 * 12) {
			if (child.getVehicle() == this || child.getVehicle() == null && !napping) {
				getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.9);
			}
			return;
		}
		int next = HouseBuilder.step(level, origin, d.houseStep);
		swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		LifeData fresh = Life.of(child);
		fresh.houseStep = next;
		if (next >= HouseBuilder.size()) {
			fresh.houseState = 2;
			say(child, "There! Our new home is finished.");
			ModAdvancements.award(child, "home_sweet_home");
		} else if (next * 4 / HouseBuilder.size() != d.houseStep * 4 / HouseBuilder.size()) {
			say(child, "The house is coming along: " + (next * 100 / HouseBuilder.size()) + "% done.");
		}
		Life.save(child, fresh);
	}

	/** Humming and rocking when the baby cries. */
	public void comfort() {
		if (level() instanceof ServerLevel level) {
			level.playSound(null, blockPosition(), ModSounds.MOM_HUM, SoundSource.NEUTRAL, 0.8f, 0.9f + random.nextFloat() * 0.3f);
			level.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.5, getZ(), 3, 0.4, 0.2, 0.4, 1.0);
		}
	}

	private void atHome(ServerLevel level, ServerPlayer child) {
		if (child.getVehicle() == this) {
			Carrying.drop(child);
		}
		if (home != null && blockPosition().distSqr(home) > 36) {
			getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
		}
		if (child.level() == level && distanceToSqr(child) < 8 * 8) {
			if (tickCount % 100 == 0) {
				child.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 6 * 20));
			}
			if (pendingCake && distanceToSqr(child) < 4 * 4) {
				pendingCake = false;
				spawnAtLocation(level, new ItemStack(Items.CAKE));
				child.sendSystemMessage(Component.literal(getName().getString() + ": I baked you a cake. I'm so proud of you!").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			if (tickCount % 2400 == 0 && child.getFoodData().getFoodLevel() < 10) {
				child.sendSystemMessage(Component.literal(getName().getString() + ": Did you eat?").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			getLookControl().setLookAt(child);
		}
	}

	private void follow(ServerPlayer child, double range) {
		if (child.level() == level() && distanceTo(child) > range) {
			getNavigation().moveTo(child, 1.1);
		} else {
			getNavigation().stop();
			getLookControl().setLookAt(child);
		}
	}

	// ---- safety ----

	/**
	 * A monster that is a real threat: hunting the baby or her, or close by with a clear view. Monsters behind
	 * walls or in caves below don't count, nor calm neutral ones, nor anything outside while they're locked in.
	 */
	private Monster threat(ServerPlayer child) {
		if (child == null || child.level() != level()) {
			return null;
		}
		LifeData d = Life.of(child);
		BlockPos house = finishedHouse(d);
		boolean lockedIn = safelyInside(child, d);
		Monster best = null;
		for (Monster m : level().getEntitiesOfClass(Monster.class, child.getBoundingBox().inflate(12), LivingEntity::isAlive)) {
			if (lockedIn && !inHouse(house, m.position())) {
				continue;
			}
			boolean hunting = m.getTarget() == child || m.getTarget() == this;
			if (!hunting && m instanceof net.minecraft.world.entity.NeutralMob neutral && !neutral.isAngry()) {
				continue;
			}
			boolean close = m.distanceToSqr(child) < 8 * 8 && (m.hasLineOfSight(child) || m.hasLineOfSight(this));
			if ((hunting || close) && (best == null || m.distanceToSqr(child) < best.distanceToSqr(child))) {
				best = m;
			}
		}
		return best;
	}

	private static boolean ranged(Monster m) {
		return m instanceof net.minecraft.world.entity.monster.RangedAttackMob || m instanceof net.minecraft.world.entity.monster.Blaze;
	}

	private static boolean nightTime(Level level) {
		return level.isDarkOutside() || level.isThundering();
	}

	/** The finished house's origin, or null. */
	private static BlockPos finishedHouse(LifeData d) {
		return d.houseState == 2 ? d.houseOrigin() : null;
	}

	private static boolean inHouse(BlockPos origin, Vec3 pos) {
		return origin != null && pos.x >= origin.getX() + 0.5 && pos.x <= origin.getX() + HouseBuilder.W - 0.5
				&& pos.z >= origin.getZ() + 0.5 && pos.z <= origin.getZ() + HouseBuilder.D - 0.5
				&& pos.y >= origin.getY() && pos.y <= origin.getY() + 6;
	}

	private BlockPos door(LifeData d) {
		BlockPos house = finishedHouse(d);
		return house == null ? null : house.offset(4, 1, HouseBuilder.D - 1);
	}

	/** Both inside the finished house with the front door shut. */
	private boolean safelyInside(ServerPlayer child, LifeData d) {
		BlockPos house = finishedHouse(d);
		BlockPos door = door(d);
		if (house == null || !inHouse(house, position()) || !inHouse(house, child.position())) {
			return false;
		}
		BlockState state = level().getBlockState(door);
		return !(state.getBlock() instanceof DoorBlock db) || !db.isOpen(state);
	}

	/** Night (or a thunderstorm) and the baby is outside a house they should be in. */
	private boolean nightOutside(ServerPlayer child, LifeData d) {
		BlockPos house = finishedHouse(d);
		return house != null && nightTime(level()) && !inHouse(house, child.position());
	}

	private boolean outsideAtNight(ServerLevel level, Entity e) {
		ServerPlayer child = child();
		BlockPos house = child == null ? null : finishedHouse(Life.of(child));
		return house != null && nightTime(level) && !inHouse(house, e.position());
	}

	/** Fire, lava, magma, a lit campfire or powder snow at or right around a spot. */
	private boolean hazardAround(BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			BlockState s = level().getBlockState(p);
			if (s.is(net.minecraft.tags.BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.POWDER_SNOW)
					|| !level().getFluidState(p).isEmpty() && level().getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)
					|| s.is(net.minecraft.tags.BlockTags.CAMPFIRES) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	/** Burning, in lava, freezing, in water (babies can't swim) or right next to fire or lava. */
	private boolean inTrouble(Entity e) {
		return e.isOnFire() || e.isInLava() || e.isInPowderSnow || e.getTicksFrozen() > 0 || e.isInWater() || hazardAround(e.blockPosition());
	}

	/**
	 * Her own safety: pats out flames (hers and the baby's in her arms) and gets out of lava, fire, powder snow, or
	 * water while holding the baby. Returns true while she is busy getting somewhere safe.
	 */
	private boolean selfRescue(ServerLevel level, ServerPlayer child) {
		boolean holding = child.getVehicle() == this;
		if (isOnFire() || holding && child.isOnFire()) {
			if (++patTicks >= 15) {
				patTicks = 0;
				clearFire();
				if (holding) {
					child.clearFire();
				}
				level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 8, 0.3, 0.4, 0.3, 0.01);
				level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6f, 1.2f);
				if (holding) {
					say(child, "Hot, hot! Mommy put it out.");
				}
			}
		} else {
			patTicks = 0;
		}
		// Bobbing at the surface flickers in and out of the water, so keep at it until she stands on dry ground.
		boolean wet = holding && (isInWater() || wetTicks > 0 && !onGround());
		wetTicks = wet ? wetTicks + 1 : 0;
		if (wet) {
			escapeWater(level, child);
			return true;
		}
		boolean stuck = isInLava() || isInPowderSnow || getTicksFrozen() > 0 || level.getBlockState(blockPosition()).is(net.minecraft.tags.BlockTags.FIRE);
		if (!stuck) {
			return false;
		}
		goSafe(level, 1.4);
		return true;
	}

	/**
	 * In water with the baby: swim for the nearest dry bank (searching wide, since deep water can be far from
	 * shore), kicking upward to climb out at the edge. If she still hasn't made it after 12 seconds, she wades out
	 * onto the bank with the baby.
	 */
	private void escapeWater(ServerLevel level, ServerPlayer child) {
		if (safeSpot == null || --safeSpotAge <= 0 || wetTicks == 1) {
			safeSpot = safeSpotNear(level, blockPosition(), 16, -3, 6);
			safeSpotAge = 40;
		}
		Vec3 target = safeSpot != null ? safeSpot : home != null ? Vec3.atBottomCenterOf(home) : null;
		if (target == null) {
			setDeltaMovement(getDeltaMovement().add(0, 0.04, 0)); // at least keep the baby's head up
			return;
		}
		if (getNavigation().isDone() || tickCount % 10 == 0) {
			getNavigation().moveTo(target.x, target.y, target.z, 1.4);
		}
		Vec3 to = new Vec3(target.x - getX(), 0, target.z - getZ());
		if (to.lengthSqr() > 0.01) {
			to = to.normalize().scale(0.045);
		}
		double up = horizontalCollision ? 0.36 : isUnderWater() ? 0.08 : 0.0; // climb the bank, or surface
		Vec3 v = getDeltaMovement();
		setDeltaMovement(v.x + to.x, Math.max(v.y, up), v.z + to.z);
		getLookControl().setLookAt(target.x, target.y + 1, target.z);
		if (safeSpot == null) {
			return;
		}
		// Against the bank with dry ground right there: climb out (a bank more than a block above the water is too
		// high to swim up onto). Still in the water after 8 seconds: wade out wherever the nearest bank is.
		double reach = Math.hypot(safeSpot.x - getX(), safeSpot.z - getZ());
		boolean atBank = horizontalCollision && reach < 2.6 && safeSpot.y - getY() < 2.5;
		if (atBank || wetTicks > 8 * 20) {
			Carrying.drop(child);
			teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
			child.teleportTo(level, safeSpot.x, safeSpot.y, safeSpot.z, java.util.Set.of(), child.getYRot(), child.getXRot(), true);
			Carrying.pickUp(this, child);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 20, 0.4, 0.2, 0.4, 0.1);
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 0.5f, 1.2f);
			wetTicks = 0;
			safeSpot = null;
		}
	}

	/**
	 * The baby's safety. Picks the baby up when they're in trouble on the ground, a monster is about, or it's
	 * night and they're outside; then fights a lone melee monster with the baby in her arms, or runs from creepers,
	 * archers and anything when she's hurt, heading indoors when there is a house. Returns true when it took over.
	 */
	private boolean rescue(ServerLevel level, ServerPlayer child, LifeData d, Monster danger) {
		boolean holding = child.getVehicle() == this;
		boolean night = nightOutside(child, d);
		boolean trouble = inTrouble(child);
		if (!holding) {
			if (child.getVehicle() != null || !Moms.carryable(child) || !(trouble || danger != null || night)) {
				rescueTicks = 0;
				return false;
			}
			freePlayTicks = 0;
			stopPlay();
			if (napping) {
				endNap(child, false);
			}
			if (night && !saidNight && danger == null && !trouble) {
				saidNight = true;
				say(child, "It's getting dark. Inside we go, where it's safe.");
			}
			if (++rescueTicks > 8 * 20 && distanceTo(child) >= 2.2) {
				reachChild(child); // can't get there in time: a mother always finds a way
			}
			if (distanceTo(child) < 2.2) {
				Carrying.pickUp(this, child);
				rescueTicks = 0;
			} else if (rescueTicks % 5 == 1 || getNavigation().isDone()) {
				getNavigation().moveTo(child, trouble || danger != null ? 1.5 : 1.1);
			}
			return true;
		}
		rescueTicks = 0;
		if (!nightTime(level)) {
			saidNight = false;
		}
		if (danger != null) {
			boolean flee = danger instanceof Creeper || ranged(danger) || getHealth() < getMaxHealth() * 0.5f;
			if (!flee) {
				setTarget(danger); // a lone zombie or spider: she fights it, baby safe in her arms
				return false;
			}
			setTarget(null);
			retreat(level, d, danger);
			return true;
		}
		if (trouble || hazardAround(blockPosition())) {
			goSafe(level, 1.3);
			return true;
		}
		return false;
	}

	/** Runs indoors when there is a house (or deeper in when already inside), otherwise away from the threat. */
	private void retreat(ServerLevel level, LifeData d, Monster from) {
		BlockPos house = finishedHouse(d);
		if (house != null) {
			BlockPos room = house.offset(inHouse(house, position()) ? HouseBuilder.NURSERY : HouseBuilder.INSIDE);
			if (getNavigation().isDone() || tickCount % 10 == 0) {
				getNavigation().moveTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 1.5);
			}
			if (inHouse(house, position())) {
				keepDoorShut(level, d);
			}
			return;
		}
		if (getNavigation().isDone() || tickCount % 20 == 0) {
			Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(this, 16, 7, from.position());
			if (away != null) {
				getNavigation().moveTo(away.x, away.y, away.z, 1.5);
			}
		}
	}

	/** Heads for the nearest safe, dry, solid spot within 8 blocks. */
	private void goSafe(ServerLevel level, double speed) {
		if (safeSpot == null || --safeSpotAge <= 0) {
			safeSpot = safeSpotNear(level, blockPosition(), 8, -2, 2);
			safeSpotAge = 40;
		}
		if (safeSpot != null && (getNavigation().isDone() || tickCount % 10 == 0)) {
			getNavigation().moveTo(safeSpot.x, safeSpot.y, safeSpot.z, speed);
		}
	}

	private Vec3 safeSpotNear(ServerLevel level, BlockPos center, int r, int down, int up) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, down, -r), center.offset(r, up, r))) {
			BlockState floor = level.getBlockState(p.below());
			boolean ok = floor.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP) && !floor.is(Blocks.MAGMA_BLOCK)
					&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty() && !hazardAround(p);
			if (ok && (best == null || p.distSqr(center) < best.distSqr(center))) {
				best = p.immutable();
			}
		}
		return best == null ? null : Vec3.atBottomCenterOf(best);
	}

	/** At night (or when hiding from monsters) she shuts the front door once she and the baby are inside. */
	private void keepDoorShut(ServerLevel level, LifeData d) {
		BlockPos house = finishedHouse(d);
		BlockPos door = door(d);
		if (house == null || tickCount % 10 != 0 || !inHouse(house, position()) || !(nightTime(level) || getTarget() == null && threat(child()) != null)) {
			return;
		}
		BlockState state = level.getBlockState(door);
		if (state.getBlock() instanceof DoorBlock db && db.isOpen(state) && door.distSqr(blockPosition()) < 10 * 10) {
			db.setOpen(this, level, state, door, false);
		}
	}
}
