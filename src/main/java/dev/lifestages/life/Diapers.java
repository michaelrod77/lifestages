package dev.lifestages.life;

import dev.lifestages.registry.ModItems;
import dev.lifestages.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Changing diapers, by a carer or (for Elderly) by yourself. */
public final class Diapers {
	private Diapers() {
	}

	/** Returns true if the Clean Diaper was used. */
	public static boolean change(ServerPlayer changer, ServerPlayer wearer, ItemStack cleanDiaper) {
		LifeData d = Life.of(wearer);
		Stage s = d.stage();
		boolean self = changer == wearer;
		if (!s.wearsDiaper() || (self && s != Stage.ELDERLY) || (!self && !Life.stage(changer).canCarry())) {
			return false;
		}
		if (self && !d.diaperOn) {
			d.diaperOn = true;
			Life.save(wearer, d);
			cleanDiaper.shrink(1);
			changer.sendOverlayMessage(Component.literal("You put on a diaper. Just in case.").withStyle(ChatFormatting.AQUA));
			return true;
		}
		if (!d.diaperDirty) {
			changer.sendOverlayMessage(Component.literal(self ? "Your diaper is still clean." : "Their diaper is still clean.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		d.diaperDirty = false;
		d.diaperTicks = 0;
		d.diaperOn = true;
		Life.save(wearer, d);
		cleanDiaper.shrink(1);
		dev.lifestages.util.Give.item(changer, new ItemStack(ModItems.DIRTY_DIAPER));
		changer.level().playSound(null, wearer.blockPosition(), ModSounds.DIAPER_CHANGE, SoundSource.PLAYERS, 1.0f, 1.0f);
		if (!self) {
			wearer.sendOverlayMessage(Component.literal(changer.getName().getString() + " changed your diaper. Ahh.").withStyle(ChatFormatting.AQUA));
		}
		return true;
	}
}
