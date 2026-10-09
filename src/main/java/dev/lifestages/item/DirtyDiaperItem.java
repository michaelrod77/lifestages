package dev.lifestages.item;

import dev.lifestages.entity.ThrownDiaper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Bone meal with three uses. Right-click the air to throw it. */
public class DirtyDiaperItem extends Item {
	public DirtyDiaperItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		ItemStack probe = new ItemStack(net.minecraft.world.item.Items.BONE_MEAL);
		if (BoneMealItem.growCrop(probe, level, pos)) {
			if (!level.isClientSide()) {
				level.levelEvent(1505, pos, 15);
				Player player = ctx.getPlayer();
				ItemStack stack = ctx.getItemInHand();
				EquipmentSlot slot = ctx.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
				if (player != null) {
					stack.hurtAndBreak(1, player, slot);
				} else {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.7f);
		if (level instanceof ServerLevel server) {
			ThrownDiaper.throwFrom(server, user, stack.copyWithCount(1));
		}
		stack.consume(1, user);
		return InteractionResult.SUCCESS;
	}
}
