package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.block.GravestoneBlock;
import dev.lifestages.block.GravestoneBlockEntity;
import dev.lifestages.entity.MomEntity;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModEntities;
import dev.lifestages.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;

/** Assigns, keeps and finally says goodbye to each player's NPC Mom. She is always 28 years older than her child. */
public final class Moms {
	public static final int AGE_GAP = 28;
	private static final Map<UUID, MomEntity> ACTIVE = new HashMap<>();
	private static final Map<UUID, Integer> WAITING = new HashMap<>();
	private static final Map<UUID, Boolean> WARNED = new HashMap<>();

	private Moms() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		ACTIVE.clear();
		WAITING.clear();
		WARNED.clear();
	}

	public static boolean isMom(Entity e) {
		return e instanceof MomEntity;
	}

	public static double momAge(ServerPlayer child) {
		return Life.age(child) + AGE_GAP;
	}

	/** Moms carry their child until age 10, like player parents. */
	public static boolean carryable(ServerPlayer child) {
		return Life.stage(child).atMost(Stage.CHILD) && Life.age(child) < LifeConfig.get().carryAgeLimit;
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		LifeConfig cfg = LifeConfig.get();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			LifeData d = Life.of(p);
			if (!d.started || d.isGhost() || d.pendingChoice != 0 || p.isSpectator()) {
				continue;
			}
			if (!d.momAssigned && !d.momDead && cfg.npcMomEnabled && d.stage().atMost(Stage.PRESCHOOLER)) {
				if (Family.caregivers(p).isEmpty()) {
					int waited = WAITING.merge(p.getUUID(), 1, Integer::sum);
					if (waited >= cfg.npcMomArrivalSeconds) {
						assign(p);
					}
				} else {
					WAITING.remove(p.getUUID());
				}
			}
			if (d.momAssigned && !d.momDead) {
				if (checkLastDay(p, d)) {
					continue;
				}
				ensurePresent(p, d);
			}
		}
	}

	/** A baby with nobody to look after them gets a Mom, who arrives already holding them. */
	public static void assign(ServerPlayer baby) {
		LifeData d = Life.of(baby);
		if (d.momAssigned || d.momDead || !LifeConfig.get().npcMomEnabled) {
			return;
		}
		d.momAssigned = true;
		if (d.momLook < 1 || d.momLook > 6) {
			d.momLook = 1 + baby.getRandom().nextInt(6);
		}
		d.momDeathAge = 80 + baby.getRandom().nextInt(11);
		Life.save(baby, d);
		WAITING.remove(baby.getUUID());
		MomEntity mom = spawn(baby, d, baby.blockPosition());
		if (mom != null && carryable(baby)) {
			Carrying.pickUp(mom, baby);
		}
		baby.sendSystemMessage(Component.literal("Mom is here to look after you. She'll feed you bottles, change you, and rock you to sleep.").withStyle(ChatFormatting.LIGHT_PURPLE));
		ModAdvancements.award(baby, "it_takes_a_village");
	}

	private static void ensurePresent(ServerPlayer child, LifeData d) {
		MomEntity mom = ACTIVE.get(child.getUUID());
		boolean grownUp = d.stage().atLeast(Stage.TEENAGER);
		BlockPos home = homeOf(child);
		if (mom != null && !mom.isRemoved()) {
			if (grownUp && child.blockPosition().distSqr(home) > 96 * 96) {
				mom.discard(); // nobody visiting: let her rest until the child comes home
				ACTIVE.remove(child.getUUID());
			} else if (!grownUp && (mom.level() != child.level() || mom.distanceToSqr(child) > 48 * 48)) {
				mom.teleportTo(child.getX(), child.getY(), child.getZ());
			}
			return;
		}
		if (grownUp) {
			if (child.blockPosition().distSqr(home) < 48 * 48) {
				spawn(child, d, home);
			}
		} else {
			spawn(child, d, child.blockPosition());
		}
	}

	private static MomEntity spawn(ServerPlayer child, LifeData d, BlockPos at) {
		ServerLevel level = (ServerLevel) child.level();
		MomEntity mom = ModEntities.MOM.create(level, EntitySpawnReason.EVENT);
		if (mom == null) {
			return null;
		}
		mom.setup(child, d.momLook, homeOf(child));
		mom.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, child.getYRot(), 0);
		level.addFreshEntity(mom);
		ACTIVE.put(child.getUUID(), mom);
		return mom;
	}

	public static BlockPos homeOf(ServerPlayer child) {
		BlockPos house = Life.of(child).houseOrigin();
		if (house != null) {
			return house.offset(HouseBuilder.INSIDE);
		}
		return spawnOf(child);
	}

	static BlockPos spawnOf(ServerPlayer child) {
		TeleportTransition t = child.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		return BlockPos.containing(t.position());
	}

	/** Spawn eggs and summons: a Mom with nobody yet adopts the nearest baby without a Mom. */
	public static void adoptNearest(MomEntity mom) {
		ServerPlayer best = null;
		for (ServerPlayer p : ((ServerLevel) mom.level()).players()) {
			LifeData d = Life.of(p);
			if (d.started && !d.momAssigned && !d.momDead && d.stage().atMost(Stage.CHILD) && mom.distanceToSqr(p) < 32 * 32
					&& (best == null || mom.distanceToSqr(p) < mom.distanceToSqr(best))) {
				best = p;
			}
		}
		if (best == null) {
			mom.discard();
			return;
		}
		LifeData d = Life.of(best);
		d.momAssigned = true;
		if (d.momLook < 1 || d.momLook > 6) {
			d.momLook = 1 + best.getRandom().nextInt(6);
		}
		d.momDeathAge = 80 + best.getRandom().nextInt(11);
		Life.save(best, d);
		mom.setup(best, d.momLook, homeOf(best));
		MomEntity old = ACTIVE.put(best.getUUID(), mom);
		if (old != null && old != mom) {
			old.discard();
		}
	}

	// ---- events from other systems ----

	public static void comfort(ServerPlayer child) {
		MomEntity mom = ACTIVE.get(child.getUUID());
		if (mom != null && !mom.isRemoved() && mom.level() == child.level() && mom.distanceToSqr(child) < 64 * 64) {
			mom.onCry();
		}
	}

	public static void diaperNeedsChange(ServerPlayer child) {
		MomEntity mom = ACTIVE.get(child.getUUID());
		if (mom != null && !mom.isRemoved()) {
			mom.scheduleDiaper();
		}
	}

	public static void parentArrived(ServerPlayer child) {
		// Mom notices on her next tick and steps back.
	}

	public static void childGrewUp(ServerPlayer child) {
		MomEntity mom = ACTIVE.get(child.getUUID());
		if (mom != null && Life.stage(child).atLeast(Stage.YOUNG_ADULT)) {
			mom.giveCakeOnVisit();
		}
	}

	/** The child pressed sneak while she holds them. */
	public static boolean requestDown(ServerPlayer child) {
		return child.getVehicle() instanceof MomEntity mom && mom.allowDown();
	}

	public static void onLeave(ServerPlayer child) {
		MomEntity mom = ACTIVE.remove(child.getUUID());
		if (mom != null) {
			if (child.getVehicle() == mom) {
				child.stopRiding();
			}
			mom.discard();
		}
	}

	/**
	 * On a baby's first life the Mom picks a site near spawn and starts the cottage (crib first). Returns the crib
	 * she should use: the house crib, or one near home if there is no house.
	 */
	public static BlockPos planHouse(ServerLevel level, ServerPlayer child, BlockPos home) {
		LifeData d = Life.of(child);
		if (d.houseState == 0 && LifeConfig.get().momBuildsHouse && d.stage().atMost(Stage.TODDLER)) {
			java.util.List<BlockPos> others = new java.util.ArrayList<>();
			for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
				BlockPos o = Life.of(p).houseOrigin();
				if (p != child && o != null) {
					others.add(o);
				}
			}
			BlockPos origin = HouseBuilder.plan(level, spawnOf(child), others);
			if (origin != null) {
				d.houseState = 1;
				d.houseX = origin.getX();
				d.houseY = origin.getY();
				d.houseZ = origin.getZ();
				d.houseVersion = HouseBuilder.VERSION;
				d.houseStep = HouseBuilder.step(level, origin, HouseBuilder.step(level, origin, 0)); // floor, then the crib
				Life.save(child, d);
				child.sendSystemMessage(Component.literal("Mom: I'll build us a little house right here, with a nursery just for you!")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		} else if (d.houseState != 0 && d.houseVersion < HouseBuilder.VERSION && d.stage().atMost(Stage.TODDLER)) {
			// A cottage from an older version: remodel it with the nursery wing.
			boolean finished = d.houseState == 2;
			boolean hadNursery = d.houseVersion >= 2;
			d.houseState = 1;
			d.houseVersion = HouseBuilder.VERSION;
			d.houseStep = HouseBuilder.step(level, d.houseOrigin(), HouseBuilder.step(level, d.houseOrigin(), 0));
			Life.save(child, d);
			child.sendSystemMessage(Component.literal(hadNursery ? "Mom: Your nursery needs a trash can for those diapers!"
					: finished ? "Mom: Let's add a nursery to our house, just for you!"
					: "Mom: I've got a better plan for our house, with a nursery!").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		BlockPos house = d.houseOrigin();
		if (house != null && level.getBlockState(house.offset(HouseBuilder.CRIB)).is(ModBlocks.CRIB)) {
			return house.offset(HouseBuilder.CRIB);
		}
		return ensureCrib(level, home, child);
	}

	/** Finds a crib near home, or sets one up beside the home spawn. Returns its position, or null. */
	public static BlockPos ensureCrib(ServerLevel level, BlockPos home, ServerPlayer child) {
		for (BlockPos p : BlockPos.betweenClosed(home.offset(-12, -4, -12), home.offset(12, 4, 12))) {
			if (level.getBlockState(p).is(ModBlocks.CRIB)) {
				return p.immutable();
			}
		}
		if (!Life.stage(child).atMost(Stage.TODDLER)) {
			return null;
		}
		for (int r = 2; r <= 4; r++) {
			for (BlockPos p : BlockPos.betweenClosed(home.offset(-r, -2, -r), home.offset(r, 2, r))) {
				boolean free = level.getBlockState(p).canBeReplaced() && level.getBlockState(p.above()).isAir()
						&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
						&& level.getFluidState(p).isEmpty();
				if (free) {
					BlockPos at = p.immutable();
					level.setBlockAndUpdate(at, ModBlocks.CRIB.defaultBlockState());
					child.sendSystemMessage(Component.literal("Mom set up a crib for you near home.").withStyle(ChatFormatting.LIGHT_PURPLE));
					return at;
				}
			}
		}
		return null;
	}

	// ---- her last day ----

	private static boolean checkLastDay(ServerPlayer child, LifeData d) {
		LifeConfig cfg = LifeConfig.get();
		Stage s = d.stage();
		double yearsPerMinute = (s.endAge - s.startAge) / Math.max(1.0, s.minMinutes * cfg.lifeLengthMultiplier);
		double left = d.momDeathAge - momAge(child);
		if (left <= yearsPerMinute && !WARNED.containsKey(child.getUUID()) && left > 0) {
			WARNED.put(child.getUUID(), true);
			child.sendSystemMessage(Component.literal("Mom isn't feeling well. Go see her.").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (left > 0) {
			return false;
		}
		MomEntity mom = ACTIVE.remove(child.getUUID());
		d.momDead = true;
		Life.save(child, d);
		WARNED.remove(child.getUUID());
		ServerLevel level = (ServerLevel) child.level();
		BlockPos at = mom != null && !mom.isRemoved() ? mom.blockPosition() : homeOf(child);
		boolean there = mom != null && !mom.isRemoved() && mom.distanceToSqr(child) < 6 * 6;
		if (there) {
			child.sendSystemMessage(Component.literal("Mom hugs you one last time and hands you her Frying Pan.").withStyle(ChatFormatting.LIGHT_PURPLE));
			dev.lifestages.util.Give.item(child, new ItemStack(ModItems.FRYING_PAN));
		} else {
			child.sendSystemMessage(Component.literal("Mom passed away peacefully at home.").withStyle(ChatFormatting.LIGHT_PURPLE));
			level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(ModItems.FRYING_PAN)));
		}
		level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(Items.CAKE)));
		if (level.getBlockState(at).canBeReplaced()) {
			level.setBlockAndUpdate(at, ModBlocks.GRAVESTONE.defaultBlockState().setValue(GravestoneBlock.FACING, child.getDirection()));
			if (level.getBlockEntity(at) instanceof GravestoneBlockEntity grave) {
				grave.set(child.getName().getString() + "'s Mom", d.momDeathAge);
			}
		}
		if (mom != null) {
			mom.discard();
		}
		ModAdvancements.award(child, "thanks_mom");
		return true;
	}
}
