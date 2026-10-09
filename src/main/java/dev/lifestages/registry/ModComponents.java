package dev.lifestages.registry;

import com.mojang.serialization.Codec;
import dev.lifestages.LifeStages;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
	/** XP points held by a Memory Orb. */
	public static final DataComponentType<Integer> STORED_XP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			LifeStages.id("stored_xp"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** A present that holds the Teddy Bear (the first birthday present of a life). */
	public static final DataComponentType<Boolean> TEDDY_INSIDE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			LifeStages.id("teddy_inside"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	private ModComponents() {
	}

	public static void init() {
	}
}
