package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent CRY = register("baby.cry");
	public static final SoundEvent FART = register("baby.fart");
	public static final SoundEvent FART_EPIC = register("baby.fart_epic");
	public static final SoundEvent GIGGLE = register("baby.giggle");
	public static final SoundEvent POOP = register("baby.poop");
	public static final SoundEvent RUMBLE = register("baby.rumble");
	public static final SoundEvent DIAPER_CHANGE = register("baby.diaper_change");
	public static final SoundEvent MOM_HUM = register("mom.hum");
	public static final SoundEvent RATTLE = register("item.rattle");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		var id = LifeStages.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
