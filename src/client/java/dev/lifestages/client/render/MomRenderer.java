package dev.lifestages.client.render;

import dev.lifestages.LifeStages;
import dev.lifestages.entity.MomEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a Mom with the slim player mesh (outer layers included) and one of the six Mom skins or its Elderly version.
 * Uses a plain humanoid render state: a player render state would be redrawn by the game's own player renderer.
 */
public class MomRenderer extends HumanoidMobRenderer<MomEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(LifeStages.id("mom"), "main");
	private static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "lifestages:mom_texture");
	private static final RenderStateDataKey<Boolean> CARRYING = RenderStateDataKey.create(() -> "lifestages:mom_carrying");

	public static void registerLayer() {
		ModelLayerRegistry.registerModelLayer(LAYER, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64));
	}

	public MomRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.5f);
		addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
			@Override
			public void submit(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
					HumanoidRenderState state, float yRot, float xRot) {
				if (Boolean.TRUE.equals(state.getData(CARRYING))) {
					coloredCutoutModelCopyLayerRender(getParentModel(), PlayerOverlayLayer.CARRIER, pose, collector, light, state, -1, 1);
				}
			}
		});
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(MomEntity mom, HumanoidRenderState state, float partialTick) {
		super.extractRenderState(mom, state, partialTick);
		state.setData(TEXTURE, LifeStages.id("textures/entity/mom/mom_" + mom.look() + (mom.isElderly() ? "_elderly" : "") + ".png"));
		state.setData(CARRYING, mom.isVehicle());
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		Identifier tex = state.getData(TEXTURE);
		return tex != null ? tex : LifeStages.id("textures/entity/mom/mom_1.png");
	}
}
