package dev.lifestages.client.screen;

import dev.lifestages.LifeStages;
import dev.lifestages.inventory.PresentMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** An opened present: four slots on wrapping paper above the player's inventory. */
public class PresentScreen extends AbstractContainerScreen<PresentMenu> {
	private static final Identifier TEXTURE = LifeStages.id("textures/gui/container/present.png");

	public PresentScreen(PresentMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 133);
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractBackground(g, mouseX, mouseY, partial);
		int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0f, 0.0f, imageWidth, imageHeight, 256, 256);
	}
}
