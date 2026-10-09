package dev.lifestages.client;

import dev.lifestages.LifeStages;
import dev.lifestages.client.render.MomRenderer;
import dev.lifestages.client.render.PlayerOverlayLayer;
import dev.lifestages.client.screen.ChoiceScreen;
import dev.lifestages.net.Payloads;
import dev.lifestages.registry.ModEntities;
import dev.lifestages.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;

public class LifeStagesClient implements ClientModInitializer {
	@Override
	@SuppressWarnings("unchecked")
	public void onInitializeClient() {
		Keys.init();
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, LifeStages.id("life_hud"), Hud::draw);

		EntityRendererRegistry.register(ModEntities.THROWN_DIAPER, ThrownItemRenderer::new);
		net.minecraft.client.gui.screens.MenuScreens.register(dev.lifestages.registry.ModMenus.PRESENT, dev.lifestages.client.screen.PresentScreen::new);
		MomRenderer.registerLayer();
		EntityRendererRegistry.register(ModEntities.MOM, MomRenderer::new);
		dev.lifestages.client.render.BabyRenderer.registerLayer();
		EntityRendererRegistry.register(ModEntities.BABY, dev.lifestages.client.render.BabyRenderer::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof AvatarRenderer<?>) {
				helper.register(new PlayerOverlayLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) renderer));
			}
		});

		particle(ModParticles.TEAR, LifeParticle.Kind.TEAR);
		particle(ModParticles.FART, LifeParticle.Kind.FART);
		particle(ModParticles.FLY, LifeParticle.Kind.FLY);
		particle(ModParticles.STINK, LifeParticle.Kind.STINK);
		particle(ModParticles.SPLAT, LifeParticle.Kind.SPLAT);
		particle(ModParticles.SPARKLE, LifeParticle.Kind.SPARKLE);

		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenChoice.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof ChoiceScreen open) {
				open.updateParents(p.parents()); // keep the parent list live while choosing
			} else if (!(mc.gui.screen() instanceof dev.lifestages.client.screen.MomPickerScreen)) {
				mc.setScreenAndShow(new ChoiceScreen(p.mode(), p.ghostLocked(), p.parents()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CryArrow.TYPE, (p, ctx) -> {
			ClientLife.cryingEntityId = p.entityId();
			ClientLife.cryingUntil = ctx.client().level == null ? 0 : ctx.client().level.getGameTime() + 100;
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.PairPrompt.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("(Press T and click [Accept] or [Decline] above.)").withStyle(ChatFormatting.GRAY));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Payloads.ConfigSync.TYPE, (p, ctx) -> {
			ClientLife.lifeLengthMultiplier = p.lifeLengthMultiplier();
			ClientLife.carryAgeLimit = p.carryAgeLimit();
		});
	}

	private static void particle(SimpleParticleType type, LifeParticle.Kind kind) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> LifeParticle.provider(kind, sprites));
	}
}
