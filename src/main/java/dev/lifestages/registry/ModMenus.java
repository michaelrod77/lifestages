package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import dev.lifestages.inventory.PresentMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<PresentMenu> PRESENT = Registry.register(BuiltInRegistries.MENU, LifeStages.id("present"),
			new MenuType<>(PresentMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
