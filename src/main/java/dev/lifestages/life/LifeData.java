package dev.lifestages.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Everything the mod remembers about one player's current life (and family). Mutable on the server;
 * {@link Life#save} stores a copy so changes sync to clients.
 */
public final class LifeData {
	// ---- growth ----
	public int stage;
	public int stageTicks;
	public int milk, food, naps, nights;
	public int napTicks;
	public long lastNightDay = -1;
	// ---- body ----
	public boolean diaperDirty;
	public int diaperTicks;
	public int feedsSincePoop;
	public int sugarTicks;
	public int stinkyTicks;
	/** Babies always wear one; Elderly put one on with a Clean Diaper. */
	public boolean diaperOn = true;
	/** Counts down the belly rumble before an automatic poop. */
	public int rumbleTicks;
	/** After a sugar rush: slow and dizzy until they sleep (or this runs out). */
	public int crashTicks;
	// ---- fate ----
	/** False until the player has made their first-join choice. */
	public boolean started;
	/** 0 none, 1 first-join choice open, 2 respawn choice open. */
	public int pendingChoice;
	public boolean lastWasGhost;
	public int ghostTicks;
	/** Elderly only: ticks of life left in the Elderly stage, rolled on entering it. */
	public int lifespanTicks;
	public int bonusTicks;
	/** Birthday presents received this life (the first one is always a Teddy Bear). */
	public int presents;
	// ---- NPC Mom ----
	public boolean momAssigned;
	public int momLook;
	/** Mom's age (years) when she dies of old age, rolled 80-90. */
	public int momDeathAge;
	public boolean momDead;
	// ---- the house the NPC Mom builds (kept across lives) ----
	/** 0 none, 1 being built, 2 finished. */
	public int houseState;
	public int houseX, houseY, houseZ;
	public int houseStep;
	/** Blueprint version the house was built with. Older houses get remodeled by the Mom. */
	public int houseVersion;
	// ---- family ----
	public List<UUID> parents = new ArrayList<>();
	public List<UUID> children = new ArrayList<>();
	public boolean allowCarry;
	public boolean lookingForBaby;
	/** A child wants the next teenager or grown-up who picks them up to become their parent. */
	public boolean lookingForParent;
	/** Starter chests already placed for this player's babies. Capped at the child limit. */
	public int starterPacks;

	public Stage stage() {
		return Stage.of(stage);
	}

	public boolean isGhost() {
		return ghostTicks > 0;
	}

	/** Share of this stage's goals completed, 0 to 1 (each goal weighted equally). */
	public double goalProgress() {
		Stage s = stage();
		double sum = 0;
		int n = 0;
		if (s.milk > 0) {
			sum += Math.min(1.0, (double) milk / s.milk);
			n++;
		}
		if (s.food > 0) {
			sum += Math.min(1.0, (double) food / s.food);
			n++;
		}
		if (s.napOrNight) {
			sum += Math.min(1.0, naps + nights);
			n++;
		} else {
			if (s.naps > 0) {
				sum += Math.min(1.0, (double) naps / s.naps);
				n++;
			}
			if (s.nights > 0) {
				sum += Math.min(1.0, (double) nights / s.nights);
				n++;
			}
		}
		return n == 0 ? 0 : sum / n;
	}

	/**
	 * How many fast-forward arrows to draw on the growth bar. Goals (milk, food, naps, nights) shorten the stage,
	 * so progress runs faster than the default. None at the normal pace; one, two or three as it speeds up.
	 * A new stage clears the goals, and the arrows with them.
	 */
	public int speedArrows() {
		double rate = 1.0 / (1.0 - 0.5 * goalProgress());
		if (rate < 1.02) {
			return 0;
		}
		if (rate < 1.25) {
			return 1;
		}
		if (rate < 1.6) {
			return 2;
		}
		return 3;
	}

	/** Ticks this stage lasts: the full default length, cut by up to half as goals are completed. */
	public int requiredTicks(double lengthMultiplier) {
		double base = stage().minMinutes * 60 * 20 * lengthMultiplier;
		return (int) Math.round(base * (1.0 - 0.5 * goalProgress()));
	}

	/** Shown age in years. Rises through the stage range as the stage runs its course. */
	public double age(double lengthMultiplier) {
		Stage s = stage();
		if (s == Stage.ELDERLY && lifespanTicks > 0) {
			double t = Math.min(1.0, stageTicks / Math.max(1.0, lifespanTicks + bonusTicks));
			return s.startAge + (s.endAge - s.startAge) * t;
		}
		double t = Math.min(1.0, stageTicks / Math.max(1.0, requiredTicks(lengthMultiplier)));
		return s.startAge + (s.endAge - s.startAge) * t;
	}

	/** Clears growth progress for a new stage. */
	public void enterStage(Stage s) {
		stage = s.ordinal();
		stageTicks = 0;
		milk = food = naps = nights = 0;
		napTicks = 0;
		if (!s.wearsDiaper()) {
			diaperDirty = false;
			diaperTicks = 0;
		}
	}

	/** Starts a new life at the given stage. Family links are kept. */
	public void newLife(Stage s) {
		enterStage(s);
		diaperDirty = false;
		diaperTicks = 0;
		feedsSincePoop = 0;
		sugarTicks = 0;
		stinkyTicks = 0;
		diaperOn = s.wearsDiaper();
		rumbleTicks = 0;
		crashTicks = 0;
		ghostTicks = 0;
		lifespanTicks = 0;
		bonusTicks = 0;
		presents = 0;
		pendingChoice = 0;
		started = true;
		lookingForParent = false;
		// The NPC Mom belongs to one life: a new life gets a new Mom only if it needs one.
		momAssigned = false;
		momDead = false;
		momLook = 0;
		momDeathAge = 0;
	}

	public LifeData copy() {
		LifeData d = new LifeData();
		d.stage = stage;
		d.stageTicks = stageTicks;
		d.milk = milk;
		d.food = food;
		d.naps = naps;
		d.nights = nights;
		d.napTicks = napTicks;
		d.lastNightDay = lastNightDay;
		d.diaperDirty = diaperDirty;
		d.diaperTicks = diaperTicks;
		d.feedsSincePoop = feedsSincePoop;
		d.sugarTicks = sugarTicks;
		d.stinkyTicks = stinkyTicks;
		d.diaperOn = diaperOn;
		d.rumbleTicks = rumbleTicks;
		d.crashTicks = crashTicks;
		d.started = started;
		d.pendingChoice = pendingChoice;
		d.lastWasGhost = lastWasGhost;
		d.ghostTicks = ghostTicks;
		d.lifespanTicks = lifespanTicks;
		d.bonusTicks = bonusTicks;
		d.presents = presents;
		d.parents = new ArrayList<>(parents);
		d.children = new ArrayList<>(children);
		d.allowCarry = allowCarry;
		d.lookingForBaby = lookingForBaby;
		d.lookingForParent = lookingForParent;
		d.starterPacks = starterPacks;
		d.momAssigned = momAssigned;
		d.momLook = momLook;
		d.momDeathAge = momDeathAge;
		d.momDead = momDead;
		d.houseState = houseState;
		d.houseX = houseX;
		d.houseY = houseY;
		d.houseZ = houseZ;
		d.houseStep = houseStep;
		d.houseVersion = houseVersion;
		return d;
	}

	public net.minecraft.core.BlockPos houseOrigin() {
		return houseState == 0 ? null : new net.minecraft.core.BlockPos(houseX, houseY, houseZ);
	}

	// ---- serialization: grouped because a codec group holds at most 16 fields ----

	private record Growth(int stage, int stageTicks, int milk, int food, int naps, int nights, int napTicks, long lastNightDay) {
		static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("stage").forGetter(Growth::stage),
				Codec.INT.fieldOf("stage_ticks").forGetter(Growth::stageTicks),
				Codec.INT.fieldOf("milk").forGetter(Growth::milk),
				Codec.INT.fieldOf("food").forGetter(Growth::food),
				Codec.INT.fieldOf("naps").forGetter(Growth::naps),
				Codec.INT.fieldOf("nights").forGetter(Growth::nights),
				Codec.INT.fieldOf("nap_ticks").forGetter(Growth::napTicks),
				Codec.LONG.fieldOf("last_night_day").forGetter(Growth::lastNightDay)
		).apply(i, Growth::new));
	}

	private record Body(boolean diaperDirty, int diaperTicks, int feedsSincePoop, int sugarTicks, int stinkyTicks, boolean diaperOn, int rumbleTicks,
			int crashTicks) {
		static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("diaper_dirty").forGetter(Body::diaperDirty),
				Codec.INT.fieldOf("diaper_ticks").forGetter(Body::diaperTicks),
				Codec.INT.fieldOf("feeds_since_poop").forGetter(Body::feedsSincePoop),
				Codec.INT.fieldOf("sugar_ticks").forGetter(Body::sugarTicks),
				Codec.INT.fieldOf("stinky_ticks").forGetter(Body::stinkyTicks),
				Codec.BOOL.optionalFieldOf("diaper_on", true).forGetter(Body::diaperOn),
				Codec.INT.optionalFieldOf("rumble_ticks", 0).forGetter(Body::rumbleTicks),
				Codec.INT.optionalFieldOf("crash_ticks", 0).forGetter(Body::crashTicks)
		).apply(i, Body::new));
	}

	private record Fate(boolean started, int pendingChoice, boolean lastWasGhost, int ghostTicks, int lifespanTicks, int bonusTicks, int presents) {
		static final Codec<Fate> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("started").forGetter(Fate::started),
				Codec.INT.fieldOf("pending_choice").forGetter(Fate::pendingChoice),
				Codec.BOOL.fieldOf("last_was_ghost").forGetter(Fate::lastWasGhost),
				Codec.INT.fieldOf("ghost_ticks").forGetter(Fate::ghostTicks),
				Codec.INT.fieldOf("lifespan_ticks").forGetter(Fate::lifespanTicks),
				Codec.INT.fieldOf("bonus_ticks").forGetter(Fate::bonusTicks),
				Codec.INT.optionalFieldOf("presents", 0).forGetter(Fate::presents)
		).apply(i, Fate::new));
	}

	private record Family(List<UUID> parents, List<UUID> children, boolean allowCarry, boolean lookingForBaby, int starterPacks, boolean lookingForParent) {
		static final Codec<Family> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.listOf().fieldOf("parents").forGetter(Family::parents),
				UUIDUtil.CODEC.listOf().fieldOf("children").forGetter(Family::children),
				Codec.BOOL.fieldOf("allow_carry").forGetter(Family::allowCarry),
				Codec.BOOL.fieldOf("looking_for_baby").forGetter(Family::lookingForBaby),
				Codec.INT.optionalFieldOf("starter_packs", 0).forGetter(Family::starterPacks),
				Codec.BOOL.optionalFieldOf("looking_for_parent", false).forGetter(Family::lookingForParent)
		).apply(i, Family::new));
	}

	private record Mom(boolean assigned, int look, int deathAge, boolean dead) {
		static final Codec<Mom> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("assigned").forGetter(Mom::assigned),
				Codec.INT.fieldOf("look").forGetter(Mom::look),
				Codec.INT.fieldOf("death_age").forGetter(Mom::deathAge),
				Codec.BOOL.fieldOf("dead").forGetter(Mom::dead)
		).apply(i, Mom::new));
	}

	private record House(int state, int x, int y, int z, int step, int version) {
		static final Codec<House> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("state").forGetter(House::state),
				Codec.INT.fieldOf("x").forGetter(House::x),
				Codec.INT.fieldOf("y").forGetter(House::y),
				Codec.INT.fieldOf("z").forGetter(House::z),
				Codec.INT.fieldOf("step").forGetter(House::step),
				Codec.INT.optionalFieldOf("version", 1).forGetter(House::version)
		).apply(i, House::new));
	}

	public static final Codec<LifeData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Growth.CODEC.fieldOf("growth").forGetter(d -> new Growth(d.stage, d.stageTicks, d.milk, d.food, d.naps, d.nights, d.napTicks, d.lastNightDay)),
			Body.CODEC.fieldOf("body").forGetter(d -> new Body(d.diaperDirty, d.diaperTicks, d.feedsSincePoop, d.sugarTicks, d.stinkyTicks, d.diaperOn, d.rumbleTicks, d.crashTicks)),
			Fate.CODEC.fieldOf("fate").forGetter(d -> new Fate(d.started, d.pendingChoice, d.lastWasGhost, d.ghostTicks, d.lifespanTicks, d.bonusTicks, d.presents)),
			Family.CODEC.fieldOf("family").forGetter(d -> new Family(d.parents, d.children, d.allowCarry, d.lookingForBaby, d.starterPacks, d.lookingForParent)),
			Mom.CODEC.optionalFieldOf("mom", new Mom(false, 0, 0, false)).forGetter(d -> new Mom(d.momAssigned, d.momLook, d.momDeathAge, d.momDead)),
			House.CODEC.optionalFieldOf("house", new House(0, 0, 0, 0, 0, 1)).forGetter(d -> new House(d.houseState, d.houseX, d.houseY, d.houseZ, d.houseStep, d.houseVersion))
	).apply(i, (g, b, f, fam, mom, house) -> {
		LifeData d = new LifeData();
		d.stage = g.stage;
		d.stageTicks = g.stageTicks;
		d.milk = g.milk;
		d.food = g.food;
		d.naps = g.naps;
		d.nights = g.nights;
		d.napTicks = g.napTicks;
		d.lastNightDay = g.lastNightDay;
		d.diaperDirty = b.diaperDirty;
		d.diaperTicks = b.diaperTicks;
		d.feedsSincePoop = b.feedsSincePoop;
		d.sugarTicks = b.sugarTicks;
		d.stinkyTicks = b.stinkyTicks;
		d.diaperOn = b.diaperOn;
		d.rumbleTicks = b.rumbleTicks;
		d.crashTicks = b.crashTicks;
		d.started = f.started;
		d.pendingChoice = f.pendingChoice;
		d.lastWasGhost = f.lastWasGhost;
		d.ghostTicks = f.ghostTicks;
		d.lifespanTicks = f.lifespanTicks;
		d.bonusTicks = f.bonusTicks;
		d.presents = f.presents;
		d.parents = new ArrayList<>(fam.parents);
		d.children = new ArrayList<>(fam.children);
		d.allowCarry = fam.allowCarry;
		d.lookingForBaby = fam.lookingForBaby;
		d.starterPacks = fam.starterPacks;
		d.lookingForParent = fam.lookingForParent;
		d.momAssigned = mom.assigned;
		d.momLook = mom.look;
		d.momDeathAge = mom.deathAge;
		d.momDead = mom.dead;
		d.houseState = house.state;
		d.houseX = house.x;
		d.houseY = house.y;
		d.houseZ = house.z;
		d.houseStep = house.step;
		d.houseVersion = house.version;
		return d;
	}));
}
