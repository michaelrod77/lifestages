package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ModDamage {
	/** Defined in data/lifestages/damage_type/old_age.json. */
	public static final ResourceKey<DamageType> OLD_AGE = ResourceKey.create(Registries.DAMAGE_TYPE, LifeStages.id("old_age"));

	private ModDamage() {
	}

	public static DamageSource oldAge(Level level) {
		return level.damageSources().source(OLD_AGE);
	}

	/** Old age with the age in the death message: "Name passed away peacefully at age 87." */
	public static DamageSource oldAge(Level level, int age) {
		DamageSource base = oldAge(level);
		return new DamageSource(base.typeHolder()) {
			@Override
			public Component getLocalizedDeathMessage(LivingEntity victim) {
				return Component.literal(victim.getName().getString() + " passed away peacefully at age " + age + ".");
			}
		};
	}
}
