package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import dev.lifestages.block.CribBlock;
import dev.lifestages.block.GravestoneBlock;
import dev.lifestages.block.GravestoneBlockEntity;
import dev.lifestages.block.MessBlock;
import dev.lifestages.block.WillChestBlock;
import dev.lifestages.block.WillChestBlockEntity;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block CRIB = register("crib", CribBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion(), true);
	public static final Block WILL_CHEST = register("will_chest", WillChestBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3600000.0f).sound(SoundType.WOOD).noLootTable(), true);
	public static final Block GRAVESTONE = register("gravestone", GravestoneBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6.0f).sound(SoundType.STONE).noOcclusion(), true);
	public static final Block MESS = register("mess", MessBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.2f).friction(0.98f).sound(SoundType.SLIME_BLOCK)
					.noOcclusion().noLootTable().replaceable(), false);

	public static final Block TEDDY_BEAR = register("teddy_bear", dev.lifestages.block.TeddyBearBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.3f).sound(SoundType.WOOL).noOcclusion(), true);

	public static final Block CHANGING_TABLE = register("changing_table", dev.lifestages.block.ChangingTableBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion(), true);
	public static final Block MUSIC_BOX = register("music_box", dev.lifestages.block.MusicBoxBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.5f).sound(SoundType.WOOD).noOcclusion(), true);

	public static final Block TRASH_CAN = register("trash_can", dev.lifestages.block.TrashCanBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(1.5f, 6.0f).sound(SoundType.LANTERN).noOcclusion(), true);

	/** Holidays: Easter eggs hidden around home, and the Christmas tree (both gone the next morning). */
	public static final Block EASTER_EGG = register("easter_egg", dev.lifestages.block.EasterEggBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2f).sound(SoundType.DECORATED_POT).noOcclusion().noLootTable(), true);
	public static final Block CHRISTMAS_TREE = register("christmas_tree", dev.lifestages.block.ChristmasTreeBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5f).sound(SoundType.GRASS).noOcclusion().noLootTable()
					.lightLevel(s -> 10), true);

	/** Birthday presents, rarer the older you are. Placeable; open them like a four-slot chest. */
	public static final Block PRESENT_COMMON = present("common", Rarity.COMMON, MapColor.COLOR_PINK);
	public static final Block PRESENT_UNCOMMON = present("uncommon", Rarity.UNCOMMON, MapColor.COLOR_LIGHT_GREEN);
	public static final Block PRESENT_RARE = present("rare", Rarity.RARE, MapColor.COLOR_LIGHT_BLUE);
	public static final Block PRESENT_EPIC = present("epic", Rarity.EPIC, MapColor.COLOR_PURPLE);
	public static final Block PRESENT_LEGENDARY = present("legendary", Rarity.EPIC, MapColor.GOLD);

	public static final BlockEntityType<WillChestBlockEntity> WILL_CHEST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			LifeStages.id("will_chest"), new BlockEntityType<>(WillChestBlockEntity::new, Set.of(WILL_CHEST)));
	public static final BlockEntityType<GravestoneBlockEntity> GRAVESTONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			LifeStages.id("gravestone"), new BlockEntityType<>(GravestoneBlockEntity::new, Set.of(GRAVESTONE)));

	public static final BlockEntityType<dev.lifestages.block.ChangingTableBlockEntity> CHANGING_TABLE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, LifeStages.id("changing_table"),
			new BlockEntityType<>(dev.lifestages.block.ChangingTableBlockEntity::new, Set.of(CHANGING_TABLE)));
	public static final BlockEntityType<dev.lifestages.block.MusicBoxBlockEntity> MUSIC_BOX_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, LifeStages.id("music_box"),
			new BlockEntityType<>(dev.lifestages.block.MusicBoxBlockEntity::new, Set.of(MUSIC_BOX)));

	public static final BlockEntityType<dev.lifestages.block.PresentBlockEntity> PRESENT_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, LifeStages.id("present"),
			new BlockEntityType<>(dev.lifestages.block.PresentBlockEntity::new,
					Set.of(PRESENT_COMMON, PRESENT_UNCOMMON, PRESENT_RARE, PRESENT_EPIC, PRESENT_LEGENDARY)));

	public static final BlockEntityType<dev.lifestages.block.TrashCanBlockEntity> TRASH_CAN_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, LifeStages.id("trash_can"),
			new BlockEntityType<>(dev.lifestages.block.TrashCanBlockEntity::new, Set.of(TRASH_CAN)));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LifeStages.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		if (item) {
			ModItems.register(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		}
		return block;
	}

	private static Block present(String tier, Rarity rarity, MapColor color) {
		String name = "present_" + tier;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LifeStages.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new dev.lifestages.block.PresentBlock(
				BlockBehaviour.Properties.of().mapColor(color).strength(0.6f).sound(SoundType.WOOL).noOcclusion().setId(key), tier));
		ModItems.register(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix().stacksTo(1).rarity(rarity));
		return block;
	}

	/** The present for a stage reached: common for little ones up to legendary for elders. */
	public static Block presentFor(dev.lifestages.life.Stage s) {
		return switch (s) {
			case NEWBORN, INFANT, TODDLER, PRESCHOOLER -> PRESENT_COMMON;
			case CHILD -> PRESENT_UNCOMMON;
			case TEENAGER, YOUNG_ADULT -> PRESENT_RARE;
			case ADULT, MIDDLE_AGE -> PRESENT_EPIC;
			case SENIOR, ELDERLY -> PRESENT_LEGENDARY;
			case KITTEN, CAT, OLD_CAT -> PRESENT_COMMON;
		};
	}

	static void addToTab(CreativeModeTab.Output out) {
		out.accept(CRIB);
		out.accept(TEDDY_BEAR);
		out.accept(CHANGING_TABLE);
		out.accept(MUSIC_BOX);
		out.accept(TRASH_CAN);
		out.accept(PRESENT_COMMON);
		out.accept(PRESENT_UNCOMMON);
		out.accept(PRESENT_RARE);
		out.accept(PRESENT_EPIC);
		out.accept(PRESENT_LEGENDARY);
		out.accept(EASTER_EGG);
		out.accept(CHRISTMAS_TREE);
		out.accept(GRAVESTONE);
		out.accept(WILL_CHEST);
	}

	public static void init() {
	}
}
