package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	public static final SimpleParticleType TEAR = register("tear");
	public static final SimpleParticleType FART = register("fart");
	public static final SimpleParticleType FLY = register("fly");
	public static final SimpleParticleType STINK = register("stink");
	public static final SimpleParticleType SPLAT = register("splat");
	public static final SimpleParticleType SPARKLE = register("sparkle");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, LifeStages.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
	}
}
