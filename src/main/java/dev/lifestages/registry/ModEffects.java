package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ModEffects {
	/** Green fumes; mobs notice you from twice as far; villagers refuse to trade. Water washes it off. */
	public static final Holder<MobEffect> STINKY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, LifeStages.id("stinky"),
			new StinkyEffect());

	private ModEffects() {
	}

	public static void init() {
	}

	static class StinkyEffect extends MobEffect {
		StinkyEffect() {
			super(MobEffectCategory.HARMFUL, 0x8DBF3A);
		}
	}
}
