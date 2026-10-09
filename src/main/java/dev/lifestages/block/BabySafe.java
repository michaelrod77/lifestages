package dev.lifestages.block;

/**
 * Blocks babies may place, open and pick back up (presents and the teddy bear), even though they can't use or
 * break anything else yet. These blocks break in a moment for anyone, whatever their mining speed.
 */
public interface BabySafe {
	/** Fraction of the block broken per tick: about half a second to pick it up. */
	float PICK_UP_PROGRESS = 1.0f / 10.0f;
}
