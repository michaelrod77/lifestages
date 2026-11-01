package dev.lifestages.client.render;

import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Pets;
import dev.lifestages.life.Stage;
import dev.lifestages.mixin.client.CatAccessor;
import dev.lifestages.mixin.client.WalkAnimationAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

/**
 * Pet players are drawn as a real cat: each one has a stand-in cat (never added to the world) that copies their
 * position, turning, walk cycle and pose every frame, and wears a red collar and, on a walk, the lead.
 */
public final class PetCats {
	private static final Map<Integer, Cat> CATS = new HashMap<>();
	private static final Map<Integer, Integer> LOOKS = new HashMap<>();
	private static Level lastLevel;
	/** Stand-ins need an entity id to be drawn; negative ones never clash with the world's. */
	private static int nextId = -1_000_000;

	private PetCats() {
	}

	/** The cat to draw in place of this entity, or null to draw it normally. */
	public static Cat standIn(Entity entity) {
		if (!(entity instanceof Player p)) {
			return null;
		}
		LifeData d = Life.of(p);
		Minecraft mc = Minecraft.getInstance();
		if (!d.started || d.isGhost() || !d.stage().isPet() || mc.level == null || p.level() != mc.level) {
			return null;
		}
		if (lastLevel != mc.level) {
			CATS.clear();
			LOOKS.clear();
			lastLevel = mc.level;
		}
		int look = Math.max(0, Math.min(Pets.VARIANTS.length - 1, d.petLook));
		Cat cat = CATS.get(p.getId());
		if (cat == null || LOOKS.getOrDefault(p.getId(), -1) != look) {
			cat = make(mc.level, look);
			if (cat == null) {
				return null;
			}
			CATS.put(p.getId(), cat);
			LOOKS.put(p.getId(), look);
		}
		copy(cat, p, d, mc);
		return cat;
	}

	/** A cat of one of the eleven looks, tame with a red collar. Also used by the cat picker. */
	public static Cat make(Level level, int look) {
		Cat cat = EntityTypes.CAT.create(level, EntitySpawnReason.LOAD);
		if (cat == null) {
			return null;
		}
		cat.setId(nextId--);
		level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT).get(Pets.VARIANTS[look])
				.ifPresent(v -> ((CatAccessor) cat).lifestages$setVariant(v));
		cat.setTame(true, false);
		((CatAccessor) cat).lifestages$setCollarColor(DyeColor.RED);
		return cat;
	}

	private static void copy(Cat cat, Player p, LifeData d, Minecraft mc) {
		cat.setPos(p.getX(), p.getY(), p.getZ());
		cat.xo = p.xo;
		cat.yo = p.yo;
		cat.zo = p.zo;
		cat.xOld = p.xOld;
		cat.yOld = p.yOld;
		cat.zOld = p.zOld;
		cat.setYRot(p.getYRot());
		cat.yRotO = p.yRotO;
		cat.setXRot(p.getXRot());
		cat.xRotO = p.xRotO;
		cat.yBodyRot = p.yBodyRot;
		cat.yBodyRotO = p.yBodyRotO;
		cat.yHeadRot = p.yHeadRot;
		cat.yHeadRotO = p.yHeadRotO;
		cat.tickCount = p.tickCount;
		cat.hurtTime = p.hurtTime;
		cat.deathTime = p.deathTime;
		cat.setOnGround(p.onGround());
		cat.setInvisible(p.isInvisible());
		cat.setSprinting(p.isSprinting());
		WalkAnimationAccessor from = (WalkAnimationAccessor) p.walkAnimation, to = (WalkAnimationAccessor) cat.walkAnimation;
		to.lifestages$setSpeedOld(from.lifestages$speedOld());
		to.lifestages$setSpeed(from.lifestages$speed());
		to.lifestages$setPosition(from.lifestages$position());
		// Sneaking: crouch along while moving, sit down when still (or asleep).
		boolean moving = from.lifestages$speed() > 0.05f;
		boolean crouch = p.isCrouching() || p.isShiftKeyDown();
		cat.setInSittingPose(p.isSleeping() || crouch && !moving || p.isPassenger());
		cat.setPose(crouch && moving ? Pose.CROUCHING : Pose.STANDING);
		boolean kitten = d.stage() == Stage.KITTEN;
		if (cat.isBaby() != kitten) {
			cat.setBaby(kitten);
		}
		if (!p.getName().equals(cat.getCustomName())) {
			cat.setCustomName(p.getName());
		}
		cat.setCustomNameVisible(p != mc.player && !p.isDiscrete());
		Player holder = d.leashHolder == null ? null : mc.level.getPlayerByUUID(d.leashHolder);
		if (holder != null && cat.getLeashHolder() != holder) {
			cat.setLeashedTo(holder, false);
		} else if (holder == null && cat.getLeashData() != null) {
			cat.setLeashData(null);
		}
	}

	/** Drops stand-ins for players who have gone. */
	public static void tidy(Minecraft mc) {
		if (mc.level == null) {
			CATS.clear();
			LOOKS.clear();
			return;
		}
		CATS.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
		LOOKS.keySet().retainAll(CATS.keySet());
	}
}
