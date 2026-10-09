package dev.lifestages.item;

import dev.lifestages.life.Diapers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Sneak-use on a baby or Elderly player to change them; Elderly can use it on themselves. */
public class CleanDiaperItem extends Item {
	public CleanDiaperItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
		if (target instanceof Player other && user instanceof ServerPlayer changer && other instanceof ServerPlayer wearer) {
			return Diapers.change(changer, wearer, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return target instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (user instanceof ServerPlayer self) {
			return Diapers.change(self, self, user.getItemInHand(hand)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}
}
