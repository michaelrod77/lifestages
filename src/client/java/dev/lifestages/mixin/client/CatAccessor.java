package dev.lifestages.mixin.client;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets up the stand-in cats that draw pet players. */
@Mixin(Cat.class)
public interface CatAccessor {
	@Invoker("setVariant")
	void lifestages$setVariant(Holder<CatVariant> variant);

	@Invoker("setCollarColor")
	void lifestages$setCollarColor(DyeColor color);
}
