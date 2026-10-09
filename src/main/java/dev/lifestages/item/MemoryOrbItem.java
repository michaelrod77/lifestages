package dev.lifestages.item;

import dev.lifestages.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Holds the XP a player had when they died of old age. Use it to absorb the XP. */
public class MemoryOrbItem extends Item {
	public MemoryOrbItem(Properties props) {
		super(props);
	}

	public static ItemStack of(int xp, String name) {
		ItemStack stack = new ItemStack(dev.lifestages.registry.ModItems.MEMORY_ORB);
		stack.set(ModComponents.STORED_XP, xp);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Memories of " + name));
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		int xp = stack.getOrDefault(ModComponents.STORED_XP, 0);
		if (!level.isClientSide()) {
			user.giveExperiencePoints(xp);
			level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 0.8f);
		}
		stack.consume(1, user);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.literal(stack.getOrDefault(ModComponents.STORED_XP, 0) + " XP"));
	}
}
