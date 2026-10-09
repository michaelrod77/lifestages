package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import dev.lifestages.item.CleanDiaperItem;
import dev.lifestages.item.DirtyDiaperItem;
import dev.lifestages.item.MemoryOrbItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

public final class ModItems {
	/** 3 hunger, counts toward a baby's milk goal, and grows baby animals. */
	public static final Item MILK_BOTTLE = register("milk_bottle", Item::new, new Item.Properties()
			.food(new FoodProperties(3, 0.6f, true), Consumables.DEFAULT_DRINK)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.craftRemainder(Items.GLASS_BOTTLE)
			.stacksTo(16));
	public static final Item CLEAN_DIAPER = register("clean_diaper", CleanDiaperItem::new, new Item.Properties().stacksTo(16));
	/** Works as bone meal three times; can also be thrown. */
	public static final Item DIRTY_DIAPER = register("dirty_diaper", DirtyDiaperItem::new, new Item.Properties().durability(3));
	/** Elderly move 10% faster holding it. Swings for 3 damage. */
	public static final Item WALKING_CANE = register("walking_cane", Item::new, new Item.Properties()
			.sword(ToolMaterial.WOOD, 2.0f, -2.0f)
			.component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
	/** Mom's heirloom: 6 damage, strong knockback, never breaks. */
	public static final Item FRYING_PAN = register("frying_pan", Item::new, new Item.Properties()
			.sword(ToolMaterial.IRON, 3.0f, -2.6f)
			.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
			.rarity(Rarity.UNCOMMON));
	/** XP from a Will Chest. Using it gives the stored XP to whoever holds it. */
	public static final Item MEMORY_ORB = register("memory_orb", MemoryOrbItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Shake it for a jingle. The NPC Mom plays with it too. */
	public static final Item RATTLE = register("rattle", dev.lifestages.item.RattleItem::new, new Item.Properties().stacksTo(1));


	/** Have a baby: hatches an NPC baby with you as its parent. Adults find four in a birthday present. */
	public static final Item BABY_SPAWN_EGG = register("baby_spawn_egg", dev.lifestages.item.BabyEggItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, LifeStages.id("life_stages"));

	private ModItems() {
	}

	static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LifeStages.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.lifestages"))
				.icon(() -> new ItemStack(MILK_BOTTLE))
				.displayItems((params, out) -> {
					out.accept(MILK_BOTTLE);
					out.accept(CLEAN_DIAPER);
					out.accept(DIRTY_DIAPER);
					out.accept(WALKING_CANE);
					out.accept(FRYING_PAN);
					out.accept(MEMORY_ORB);
					out.accept(RATTLE);
					out.accept(BABY_SPAWN_EGG);
					ModBlocks.addToTab(out);
				})
				.build());
	}
}
