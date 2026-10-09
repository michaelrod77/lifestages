package dev.lifestages.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lifestages.registry.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Vanilla refuses to let anything ride a player (players aren't saved like other entities). Children may. */
	@WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
	private boolean lifestages$allowCarrying(EntityType<?> type, Operation<Boolean> original) {
		return type == net.minecraft.world.entity.EntityTypes.PLAYER || type == ModEntities.MOM || original.call(type);
	}

	/**
	 * A carried child rides in a baby carrier on the grown-up's chest, facing out. Two newborns or infants sit side
	 * by side.
	 */
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void lifestages$inCarrier(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Player && (passenger instanceof Player || passenger instanceof dev.lifestages.entity.BabyEntity)) {
			var riders = self.getPassengers();
			double side = riders.size() < 2 ? 0.0 : riders.indexOf(passenger) == 0 ? 0.17 : -0.17;
			cir.setReturnValue(new Vec3(side * scale, dims.height() * 0.4, 0.26 * scale).yRot(-self.getYRot() * Mth.DEG_TO_RAD));
		}
	}

	/**
	 * Minecraft only tells players who are watching an entity when its riders change, and a player doesn't watch
	 * themselves. Tell a carrying player too, or their own screen never shows the babies in their carrier.
	 */
	@Inject(method = {"addPassenger", "removePassenger"}, at = @At("TAIL"))
	private void lifestages$tellCarrier(Entity passenger, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if ((Object) this instanceof net.minecraft.server.level.ServerPlayer carrier && carrier.connection != null) {
			carrier.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(carrier));
		}
	}

	/** The baby's body faces the way the carrier faces (they can still turn their head and look around). */
	@Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("TAIL"))
	private void lifestages$faceOut(Entity passenger, Entity.MoveFunction move, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Player && passenger instanceof net.minecraft.world.entity.LivingEntity baby
				&& (passenger instanceof Player || passenger instanceof dev.lifestages.entity.BabyEntity)) {
			baby.setYBodyRot(self.getYRot());
			baby.yBodyRotO = self.yRotO;
		}
	}
}
