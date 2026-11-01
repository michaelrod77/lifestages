package dev.lifestages.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lifestages.LifeStages;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Stage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;

/** Draws the swaddle, onesie, diaper, elderly hair and the baby carrier on top of each player's own skin. */
public class PlayerOverlayLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier SWADDLE = tex("newborn_swaddle");
	private static final Identifier SWADDLE_SLIM = tex("newborn_swaddle_slim");
	private static final Identifier ONESIE = tex("onesie");
	private static final Identifier ONESIE_SLIM = tex("onesie_slim");
	private static final Identifier DIAPER = tex("diaper");
	private static final Identifier DIAPER_DIRTY = tex("diaper_dirty");
	private static final Identifier GREY_HAIR = tex("elderly_hair");
	public static final Identifier CARRIER = tex("baby_carrier");

	public PlayerOverlayLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	private static Identifier tex(String name) {
		return LifeStages.id("textures/entity/player_layer/" + name + ".png");
	}

	/** The life data of the player a render state belongs to, or null. */
	public static LifeData lifeOf(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(state.id);
		if (!(e instanceof Player p)) {
			return null;
		}
		LifeData d = Life.of(p);
		return d.started && !d.isGhost() ? d : null;
	}

	/** Carrying a baby (a player or an NPC baby) in the baby carrier. */
	private static boolean carrying(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		Entity e = mc.level == null ? null : mc.level.getEntity(state.id);
		Entity rider = e == null ? null : e.getFirstPassenger();
		return rider instanceof Player || rider instanceof dev.lifestages.entity.BabyEntity;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (carrying(state)) {
			coloredCutoutModelCopyLayerRender(getParentModel(), CARRIER, pose, collector, light, state, -1, 4);
		}
		LifeData d = lifeOf(state);
		if (d == null) {
			return;
		}
		Stage s = d.stage();
		boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
		if (d.costume > 0 && d.costume < dev.lifestages.life.Holidays.COSTUMES.length) {
			// Halloween: the costume goes over everything else they're wearing
			String name = dev.lifestages.life.Holidays.COSTUMES[d.costume].toLowerCase(java.util.Locale.ROOT);
			coloredCutoutModelCopyLayerRender(getParentModel(), tex("costume_" + name + (slim ? "_slim" : "")), pose, collector, light, state, -1, 3);
		}
		int order = 1;
		if (s == Stage.NEWBORN) {
			coloredCutoutModelCopyLayerRender(getParentModel(), slim ? SWADDLE_SLIM : SWADDLE, pose, collector, light, state, -1, order);
			return;
		}
		if (s == Stage.INFANT || s == Stage.TODDLER) {
			coloredCutoutModelCopyLayerRender(getParentModel(), slim ? ONESIE_SLIM : ONESIE, pose, collector, light, state, -1, order++);
			coloredCutoutModelCopyLayerRender(getParentModel(), d.diaperDirty ? DIAPER_DIRTY : DIAPER, pose, collector, light, state, -1, order);
			return;
		}
		if (s == Stage.SENIOR || s == Stage.ELDERLY) {
			coloredCutoutModelCopyLayerRender(getParentModel(), GREY_HAIR, pose, collector, light, state, -1, order);
		}
	}
}
