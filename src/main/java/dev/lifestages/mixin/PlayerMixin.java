package dev.lifestages.mixin;

import dev.lifestages.life.Carrying;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/** A carried child can't just sneak off; they wriggle free or are put down. */
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void lifestages$stayCarried(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self.isShiftKeyDown() && Carrying.blocksDismount(self)) {
			cir.setReturnValue(false);
		}
	}

	/** Newborns and infants lie down and crawl. */
	@Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
	private void lifestages$crawl(CallbackInfoReturnable<Pose> cir) {
		Player self = (Player) (Object) this;
		if (self.isSleeping() || self.isPassenger() || self.isSpectator()) {
			return;
		}
		LifeData d = Life.of(self);
		if (d.started && d.stage().isBaby() && !d.isGhost()) {
			cir.setReturnValue(Pose.SWIMMING);
		}
	}
}
