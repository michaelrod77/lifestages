package dev.lifestages.mixin.client;

import dev.lifestages.client.render.PlayerOverlayLayer;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Stage;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baby and toddler proportions (Newborn to Toddler): a head about as wide as the shoulders, a short slim body
 * and very short legs. Model space has y pointing down, the neck at 0 and the feet at 24; the parts are moved
 * so the feet stay at 24.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	private static final float BODY_Y = 0.75f, LEG_Y = 0.55f, ARM_Y = 0.8f, SLIM = 0.85f, HEAD = 1.15f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void lifestages$babyProportions(AvatarRenderState state, CallbackInfo ci) {
		LifeData d = PlayerOverlayLayer.lifeOf(state);
		if (d == null || !d.stage().atMost(Stage.TODDLER)) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float neck = 24 - 12 * BODY_Y - 12 * LEG_Y;
		place(model.head, HEAD, HEAD, HEAD, model.head.getInitialPose().x(), neck);
		place(model.body, SLIM, BODY_Y, SLIM, model.body.getInitialPose().x(), neck);
		place(model.rightArm, SLIM, ARM_Y, SLIM, model.rightArm.getInitialPose().x() * SLIM, neck + 2 * BODY_Y);
		place(model.leftArm, SLIM, ARM_Y, SLIM, model.leftArm.getInitialPose().x() * SLIM, neck + 2 * BODY_Y);
		place(model.rightLeg, SLIM, LEG_Y, SLIM, model.rightLeg.getInitialPose().x() * SLIM, neck + 12 * BODY_Y);
		place(model.leftLeg, SLIM, LEG_Y, SLIM, model.leftLeg.getInitialPose().x() * SLIM, neck + 12 * BODY_Y);
	}

	private static void place(ModelPart part, float xs, float ys, float zs, float x, float y) {
		part.xScale = xs;
		part.yScale = ys;
		part.zScale = zs;
		part.x = x;
		part.y = y;
	}
}
