package dev.lifestages.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Copies a pet player's walk cycle onto the cat that draws them. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccessor {
	@Accessor("speedOld")
	float lifestages$speedOld();

	@Accessor("speedOld")
	void lifestages$setSpeedOld(float value);

	@Accessor("speed")
	float lifestages$speed();

	@Accessor("speed")
	void lifestages$setSpeed(float value);

	@Accessor("position")
	float lifestages$position();

	@Accessor("position")
	void lifestages$setPosition(float value);
}
