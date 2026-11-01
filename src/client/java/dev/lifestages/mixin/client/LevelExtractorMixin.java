package dev.lifestages.mixin.client;

import dev.lifestages.client.render.PetCats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A pet player is drawn in the world as their cat: the stand-in cat's render state replaces the player's. Only the
 * world's entity pass is swapped; the local player's own first-person state stays a player's (vanilla requires it).
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Redirect(method = "extractVisibleEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
	private EntityRenderState lifestages$drawPetAsCat(LevelExtractor self, Entity entity, float partialTicks) {
		Cat cat = PetCats.standIn(entity);
		return Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(cat != null ? cat : entity, partialTicks);
	}
}
