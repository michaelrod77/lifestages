package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import dev.lifestages.entity.MomEntity;
import dev.lifestages.entity.ThrownDiaper;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModEntities {
	public static final EntityType<ThrownDiaper> THROWN_DIAPER = register("thrown_diaper",
			EntityType.Builder.<ThrownDiaper>of(ThrownDiaper::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10));
	/** Not saved with the world: Moms are summoned beside (or at the home of) their online child. */
	public static final EntityType<MomEntity> MOM = register("mom",
			EntityType.Builder.<MomEntity>of(MomEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).noSave());

	/** An NPC baby an adult player raises (saved with the world). */
	public static final EntityType<dev.lifestages.entity.BabyEntity> BABY = register("baby",
			EntityType.Builder.<dev.lifestages.entity.BabyEntity>of(dev.lifestages.entity.BabyEntity::new, MobCategory.CREATURE)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10));

	public static final Item MOM_SPAWN_EGG = ModItems.register("mom_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(MOM));

	private ModEntities() {
	}

	static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, LifeStages.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MOM, MomEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BABY, dev.lifestages.entity.BabyEntity.createAttributes());
	}
}
