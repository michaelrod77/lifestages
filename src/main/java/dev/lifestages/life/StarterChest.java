package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * One chest per baby connection, up to the child limit. Adults no longer start with nursery items in their inventory.
 */
public final class StarterChest {
	private StarterChest() {
	}

	/** Places a chest of baby supplies beside the parent. Does nothing once they already have a chest per child slot. */
	public static void give(ServerPlayer parent, String babyName) {
		LifeData d = Life.of(parent);
		int cap = LifeConfig.get().maxChildrenPerParent;
		if (d.starterPacks >= cap) {
			parent.sendSystemMessage(Component.literal("You already have starter chests for " + cap + " babies.").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = (ServerLevel) parent.level();
		BlockPos pos = findSpot(level, parent);
		if (pos == null) {
			parent.sendSystemMessage(Component.literal("A starter chest for " + babyName + " couldn't find an open spot nearby.").withStyle(ChatFormatting.GRAY));
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, parent.getDirection().getOpposite()));
		if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity chest) {
			chest.applyComponents(DataComponentMap.EMPTY, DataComponentPatch.builder()
					.set(DataComponents.CUSTOM_NAME, Component.literal(babyName + "'s starter pack")).build());
			fill(chest);
		}
		d.starterPacks++;
		Life.save(parent, d);
		parent.sendSystemMessage(Component.literal("A starter chest for " + babyName + " appeared at "
				+ pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
				+ ": a crib, a changing table, 32 diapers, a rattle, 64 milk bottles, a trash can and a music box.")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static void fill(Container chest) {
		int[] slot = {0};
		add(chest, slot, ModBlocks.CRIB.asItem(), 1);
		add(chest, slot, ModBlocks.CHANGING_TABLE.asItem(), 1);
		add(chest, slot, ModItems.CLEAN_DIAPER, 32);
		add(chest, slot, ModItems.RATTLE, 1);
		add(chest, slot, ModItems.MILK_BOTTLE, 64);
		add(chest, slot, ModBlocks.TRASH_CAN.asItem(), 1);
		add(chest, slot, ModBlocks.MUSIC_BOX.asItem(), 1);
	}

	/** Milk and diapers stack to 16, so a pack is split across slots. */
	private static void add(Container chest, int[] slot, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		int left = count;
		while (left > 0 && slot[0] < chest.getContainerSize()) {
			int n = Math.min(max, left);
			chest.setItem(slot[0]++, new ItemStack(item, n));
			left -= n;
		}
	}

	/** An open block near the parent that won't join onto another chest. */
	private static BlockPos findSpot(ServerLevel level, ServerPlayer parent) {
		BlockPos feet = parent.blockPosition();
		BlockPos front = feet.relative(parent.getDirection());
		BlockPos[] first = {front, front.above(), feet.relative(Direction.EAST), feet.relative(Direction.WEST)};
		for (BlockPos p : first) {
			if (open(level, feet, p)) {
				return p.immutable();
			}
		}
		for (int r = 1; r <= 3; r++) {
			for (BlockPos p : BlockPos.betweenClosed(feet.offset(-r, 0, -r), feet.offset(r, 1, r))) {
				if (open(level, feet, p)) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	private static boolean open(ServerLevel level, BlockPos feet, BlockPos p) {
		if (p.equals(feet) || p.equals(feet.above()) || !level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()) {
			return false;
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(p.relative(dir)).is(Blocks.CHEST)) {
				return false;
			}
		}
		return true;
	}
}
