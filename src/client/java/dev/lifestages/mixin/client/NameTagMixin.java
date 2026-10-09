package dev.lifestages.mixin.client;

import dev.lifestages.entity.BabyEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A baby in your arms sits in front of the camera. Their name tag would fill the first-person view. */
@Mixin(LivingEntityRenderer.class)
public class NameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void lifestages$hideCarriedName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
			return;
		}
		if (entity.getVehicle() != mc.player) {
			return;
		}
		if (entity instanceof Player || entity instanceof BabyEntity) {
			cir.setReturnValue(false);
		}
	}
}
