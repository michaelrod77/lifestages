package dev.lifestages.life;

/**
 * The 11 life stages. Ages are in years; times are minutes at lifeLengthMultiplier 1.0.
 * minMinutes is the default stage length; completing the goals (milk bottles, food points, naps, nights) cuts it
 * by up to half. A nap may stand in for a night where noted. Kitten, Cat and Old Cat are a pet cat's own life, after the
 * human stages in the list; comparing a pet's stage with a person's ({@link #atMost}, {@link #atLeast}) is always false.
 */
public enum Stage {
	NEWBORN("Newborn", 0.0, 28 / 365.0, 6, 0.35, 0.10, 3, 3, 0, 4, 0, false),
	INFANT("Infant", 28 / 365.0, 1, 10, 0.40, 0.30, 4, 5, 0, 2, 0, false),
	TODDLER("Toddler", 1, 3, 14, 0.50, 0.55, 5, 4, 10, 2, 0, false),
	PRESCHOOLER("Preschooler", 3, 6, 15, 0.60, 0.75, 6, 0, 20, 1, 1, false),
	CHILD("Child", 6, 13, 24, 0.75, 0.90, 8, 0, 30, 0, 1, false),
	TEENAGER("Teenager", 13, 18, 25, 0.92, 1.0, 10, 0, 40, 0, 1, false),
	YOUNG_ADULT("Young Adult", 18, 25, 30, 1.0, 1.0, 10, 0, 40, 0, 1, false),
	ADULT("Adult", 25, 40, 40, 1.0, 1.0, 10, 0, 50, 0, 1, false),
	MIDDLE_AGE("Middle Age", 40, 60, 40, 1.0, 1.0, 10, 0, 50, 0, 1, false),
	SENIOR("Senior Citizen", 60, 65, 16, 1.0, 0.95, 9, 0, 30, 0, 1, true),
	ELDERLY("Elderly", 65, 85, 30, 0.95, 0.85, 8, 0, 0, 0, 0, false),
	// A pet cat's life (cat years): fed milk bottles as a Kitten, fish as a Cat; the Old Cat's time is rolled like an Elder's.
	KITTEN("Kitten", 0, 1, 15, 0.3, 1.0, 4, 4, 0, 2, 0, false),
	CAT("Cat", 1, 12, 45, 0.4, 1.1, 5, 0, 30, 1, 1, false),
	OLD_CAT("Old Cat", 12, 18, 30, 0.4, 0.95, 5, 0, 0, 0, 0, false);

	public final String label;
	public final double startAge, endAge;
	public final int minMinutes;
	public final double scale, speed;
	public final int hearts;
	public final int milk, food, naps, nights;
	/** Senior Citizen: one nap or one night satisfies the sleep goal. */
	public final boolean napOrNight;

	Stage(String label, double startAge, double endAge, int minMinutes, double scale, double speed, int hearts,
			int milk, int food, int naps, int nights, boolean napOrNight) {
		this.label = label;
		this.startAge = startAge;
		this.endAge = endAge;
		this.minMinutes = minMinutes;
		this.scale = scale;
		this.speed = speed;
		this.hearts = hearts;
		this.milk = milk;
		this.food = food;
		this.naps = naps;
		this.nights = nights;
		this.napOrNight = napOrNight;
	}

	public static Stage of(int ordinal) {
		Stage[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))];
	}

	public Stage next() {
		return isFinal() ? this : values()[ordinal() + 1];
	}

	/** The last stage of a life: Elderly, or an Old Cat. Its length is a rolled lifespan, and then the life ends. */
	public boolean isFinal() {
		return this == ELDERLY || this == OLD_CAT;
	}

	/** A pet cat: Kitten, Cat or Old Cat. */
	public boolean isPet() {
		return ordinal() >= KITTEN.ordinal();
	}

	public boolean atMost(Stage other) {
		return ordinal() <= other.ordinal() && isPet() == other.isPet();
	}

	public boolean atLeast(Stage other) {
		return ordinal() >= other.ordinal() && isPet() == other.isPet();
	}

	/** Can only lie, wriggle and roll over. */
	public boolean isNewborn() {
		return this == NEWBORN;
	}

	/** Newborn and Infant: no solid food, no chat, crawling pose. */
	public boolean isBaby() {
		return this == NEWBORN || this == INFANT;
	}

	/** Uses diapers: Newborn to Toddler, and Elderly. */
	public boolean wearsDiaper() {
		return atMost(TODDLER) || this == ELDERLY;
	}

	/** Can use the emote wheel: Newborn to Preschooler, and pets (meow, hiss, purr). */
	public boolean hasEmotes() {
		return atMost(PRESCHOOLER) || isPet();
	}

	/** Can pick up children: Teenager and older. */
	public boolean canCarry() {
		return atLeast(TEENAGER);
	}

	/** Can become a parent: Young Adult to Senior Citizen. */
	public boolean canParent() {
		return atLeast(YOUNG_ADULT) && atMost(SENIOR);
	}

	/** Gets a sugar rush from sweets: Child and younger. */
	public boolean getsSugarRush() {
		return atMost(CHILD);
	}
}
