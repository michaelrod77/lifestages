package dev.lifestages.client.render;

import dev.lifestages.LifeStages;
import dev.lifestages.entity.BabyEntity;
import dev.lifestages.life.Stage;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws an NPC child: the slim player mesh, with the same baby proportions players get up to Toddler (bigger
 * head, slim body, short legs). Babies wear one of four onesies (with a dirty diaper when it needs changing),
 * preschoolers and children a t-shirt and shorts, teenagers and young adults a hoodie and jeans.
 */
public class BabyRenderer extends HumanoidMobRenderer<BabyEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(LifeStages.id("baby"), "main");
	private static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "lifestages:baby_texture");
	private static final RenderStateDataKey<Boolean> BABY_SHAPE = RenderStateDataKey.create(() -> "lifestages:baby_shape");

	public static void registerLayer() {
		ModelLayerRegistry.registerModelLayer(LAYER, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64));
	}

	public BabyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new BabyModel(ctx.bakeLayer(LAYER)), 0.25f);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(BabyEntity baby, HumanoidRenderState state, float partialTick) {
		super.extractRenderState(baby, state, partialTick);
		Stage s = baby.stage();
		String outfit = s.atMost(Stage.TODDLER) ? "baby_" + baby.look() + (baby.dirty() ? "_dirty" : "")
				: s.atMost(Stage.CHILD) ? "kid_" + baby.look() : "teen_" + baby.look();
		state.setData(TEXTURE, LifeStages.id("textures/entity/baby/" + outfit + ".png"));
		state.setData(BABY_SHAPE, s.atMost(Stage.TODDLER));
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		Identifier tex = state.getData(TEXTURE);
		return tex != null ? tex : LifeStages.id("textures/entity/baby/baby_1.png");
	}

	/** Baby proportions, matching the player babies. */
	static class BabyModel extends HumanoidModel<HumanoidRenderState> {
		private static final float BODY_Y = 0.75f, LEG_Y = 0.55f, ARM_Y = 0.8f, SLIM = 0.85f, HEAD = 1.15f;

		BabyModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state); // resets every part, so older children get normal proportions
			if (!Boolean.TRUE.equals(state.getData(BABY_SHAPE))) {
				return;
			}
			float neck = 24 - 12 * BODY_Y - 12 * LEG_Y;
			place(head, HEAD, HEAD, HEAD, head.getInitialPose().x(), neck);
			place(body, SLIM, BODY_Y, SLIM, body.getInitialPose().x(), neck);
			place(rightArm, SLIM, ARM_Y, SLIM, rightArm.getInitialPose().x() * SLIM, neck + 2 * BODY_Y);
			place(leftArm, SLIM, ARM_Y, SLIM, leftArm.getInitialPose().x() * SLIM, neck + 2 * BODY_Y);
			place(rightLeg, SLIM, LEG_Y, SLIM, rightLeg.getInitialPose().x() * SLIM, neck + 12 * BODY_Y);
			place(leftLeg, SLIM, LEG_Y, SLIM, leftLeg.getInitialPose().x() * SLIM, neck + 12 * BODY_Y);
		}

		private static void place(ModelPart part, float xs, float ys, float zs, float x, float y) {
			part.xScale = xs;
			part.yScale = ys;
			part.zScale = zs;
			part.x = x;
			part.y = y;
		}
	}
}
