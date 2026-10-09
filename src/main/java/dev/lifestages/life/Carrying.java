package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import dev.lifestages.registry.ModAdvancements;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Picking up children (players riding players), putting them down, and wriggling free. */
public final class Carrying {
	private static final Identifier CARRY_SLOW = LifeStages.id("carrying");
	private static final Map<UUID, long[]> WRIGGLES = new HashMap<>();
	/** Children allowed to get down on their next dismount check. */
	private static final Set<UUID> RELEASED = new HashSet<>();

	private Carrying() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		WRIGGLES.clear();
		RELEASED.clear();
	}

	/** Newborns and infants are small enough to carry two at a time. */
	public static boolean tiny(Entity e) {
		if (e instanceof Player p) {
			return Life.of(p).started && Life.stage(p).atMost(Stage.INFANT);
		}
		return e instanceof dev.lifestages.entity.BabyEntity b && b.stage().atMost(Stage.INFANT);
	}

	/**
	 * Whether a carrier has room for one more: empty arms, or one tiny baby already in the carrier and another tiny
	 * one coming. Older children ride alone.
	 */
	public static boolean canHold(Entity carrier, Entity passenger) {
		var riders = carrier.getPassengers();
		if (riders.isEmpty()) {
			return true;
		}
		return riders.size() == 1 && tiny(riders.get(0)) && tiny(passenger);
	}

	/** Held by a player or by the NPC Mom. */
	public static boolean isCarried(Player child) {
		return child.getVehicle() instanceof Player || Moms.isMom(child.getVehicle());
	}

	public static Player carrierOf(Player child) {
		return child.getVehicle() instanceof Player p ? p : null;
	}

	public static boolean isCarrying(Player carrier) {
		return carrier.getFirstPassenger() instanceof Player;
	}

	/** Puts the baby this player is carrying onto a changing table. */
	public static void putOnChangingTable(Player carrier, net.minecraft.core.BlockPos table) {
		Entity passenger = carrier.getFirstPassenger();
		if (!(passenger instanceof Player) && !(passenger instanceof dev.lifestages.entity.BabyEntity)) {
			return;
		}
		drop(carrier);
		passenger.teleportTo(table.getX() + 0.5, table.getY() + 0.9, table.getZ() + 0.5);
		if (passenger instanceof dev.lifestages.entity.BabyEntity baby) {
			baby.getNavigation().stop();
		}
	}

	/**
	 * UseEntityCallback: right-click your own child with an empty hand to pick them up. Sneak is still required
	 * for a child who isn't yours.
	 */
	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || !(target instanceof Player child)) {
			return InteractionResult.PASS;
		}
		boolean grown = Life.of(player).started && Life.stage(player).canCarry();
		boolean mine = Family.isParentOf(player, child) && grown;
		boolean adopting = Life.of(child).lookingForParent && grown;
		if (!mine && !adopting && !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerPlayer carrier = (ServerPlayer) player, kid = (ServerPlayer) child;
		String problem = problem(carrier, kid);
		if (problem != null) {
			carrier.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		pickUp(carrier, kid);
		return InteractionResult.SUCCESS;
	}

	static String problem(ServerPlayer carrier, ServerPlayer child) {
		LifeData cd = Life.of(child);
		if (!Life.stage(carrier).canCarry()) {
			return "You're too young to carry anyone.";
		}
		if (!cd.stage().atMost(Stage.CHILD) || Life.age(child) >= LifeConfig.get().carryAgeLimit) {
			return child.getName().getString() + " is too big to carry.";
		}
		if (carrier.isPassenger()) {
			return "Your arms are full.";
		}
		// A child looking for a parent can be taken out of Mom's arms. Anyone else already being carried stays put.
		if (child.isPassenger() && !(cd.lookingForParent && Moms.isMom(child.getVehicle()))) {
			return "Your arms are full.";
		}
		if (!canHold(carrier, child)) {
			return carrier.getPassengers().size() >= 2 ? "Your carrier is full." : "Only two newborns or infants fit in the carrier at once.";
		}
		return null;
	}

	public static void pickUp(Entity carrier, ServerPlayer child) {
		boolean running = child.isSprinting() || child.getDeltaMovement().horizontalDistanceSqr() > 0.02;
		if (Moms.isMom(child.getVehicle())) {
			child.stopRiding();
		}
		if (child.startRiding(carrier, true, true)) {
			if (carrier instanceof ServerPlayer parent) {
				Family.claimIfLooking(parent, child);
			}
			if (carrier instanceof Player p) {
				setSlow(p, true);
			}
			if (running && carrier instanceof ServerPlayer sc) {
				ModAdvancements.award(sc, "gotcha");
			}
		}
	}

	/** Puts down the child this player is carrying, or (called on the child) gets them down. */
	public static void drop(Player player) {
		Entity passenger = player.getFirstPassenger();
		if (passenger instanceof Player child) {
			RELEASED.add(child.getUUID());
			child.stopRiding();
			setSlow(player, false);
		} else if (passenger instanceof dev.lifestages.entity.BabyEntity baby) {
			baby.stopRiding();
		} else if (player.getVehicle() != null) {
			Entity vehicle = player.getVehicle();
			RELEASED.add(player.getUUID());
			player.stopRiding();
			if (vehicle instanceof Player carrier) {
				setSlow(carrier, false);
			}
		}
	}

	/** Called by the Player mixin: a carried child can't just sneak off, they have to wriggle free. */
	public static boolean blocksDismount(Player child) {
		if (RELEASED.remove(child.getUUID())) {
			return false;
		}
		if (Moms.isMom(child.getVehicle())) {
			return !(child instanceof ServerPlayer sp && Moms.requestDown(sp));
		}
		return child.getVehicle() instanceof Player;
	}

	/** Toddlers and older wriggle free by tapping jump 6 times within 3 seconds. */
	public static void wriggle(ServerPlayer child) {
		if (child.getVehicle() == null || Life.stage(child).isBaby()) {
			return;
		}
		long now = child.level().getGameTime();
		long[] taps = WRIGGLES.computeIfAbsent(child.getUUID(), k -> new long[6]);
		System.arraycopy(taps, 1, taps, 0, 5);
		taps[5] = now;
		if (taps[0] != 0 && now - taps[0] <= 60) {
			drop(child);
			WRIGGLES.remove(child.getUUID());
			child.sendOverlayMessage(Component.literal("You wriggled free!").withStyle(ChatFormatting.GOLD));
		}
	}

	/** Each tick: keep the carrier slow, drop children who grew too big. */
	public static void tick(ServerPlayer player) {
		if (isCarried(player) && Life.age(player) >= LifeConfig.get().carryAgeLimit) {
			drop(player);
			player.sendSystemMessage(Component.literal("You're too big to carry now!").withStyle(ChatFormatting.GOLD));
		}
		if (!isCarrying(player)) {
			setSlow(player, false);
		}
	}

	private static void setSlow(Player carrier, boolean on) {
		AttributeInstance speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on && !speed.hasModifier(CARRY_SLOW)) {
			speed.addTransientModifier(new AttributeModifier(CARRY_SLOW, -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!on && speed.hasModifier(CARRY_SLOW)) {
			speed.removeModifier(CARRY_SLOW);
		}
	}
}
