package dev.lifestages.mixin;

import dev.lifestages.life.Feeding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	/** Credits growth goals (food, milk) and starts sugar rushes whenever a player finishes eating. */
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void lifestages$eaten(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (entity instanceof ServerPlayer player) {
			Feeding.onEaten(player, stack, (FoodProperties) (Object) this);
		}
	}
}
