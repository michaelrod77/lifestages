package dev.lifestages.mixin;

import dev.lifestages.life.Growth;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	/** Finishing a vanilla advancement helps you grow up. */
	@Inject(method = "award", at = @At("RETURN"))
	private void lifestages$growthBoost(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && player != null) {
			Growth.onAdvancement(player, holder, (PlayerAdvancements) (Object) this);
		}
	}
}
