package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.block.WillChestBlock;
import dev.lifestages.block.WillChestBlockEntity;
import dev.lifestages.item.MemoryOrbItem;
import dev.lifestages.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** On death of old age, everything the player carried goes into a Will Chest for their heirs. */
public final class WillChest {
	private WillChest() {
	}

	public static void place(ServerPlayer player, int age) {
		ServerLevel level = (ServerLevel) player.level();
		List<ItemStack> stacks = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		int xp = totalXp(player);
		if (xp > 0) {
			stacks.add(MemoryOrbItem.of(xp, player.getName().getString()));
		}
		if (stacks.isEmpty()) {
			return;
		}
		BlockPos pos = findSpot(level, player.blockPosition());
		if (pos == null) {
			return; // nowhere to put it: items drop as normal
		}
		inv.clearContent();
		player.giveExperienceLevels(-player.experienceLevel);
		player.experienceProgress = 0;

		List<UUID> heirs = heirsOf(player);
		long unlock = level.getGameTime() + LifeConfig.get().willChestLockMinutes * 1200L;
		level.setBlockAndUpdate(pos, ModBlocks.WILL_CHEST.defaultBlockState().setValue(WillChestBlock.FACING, player.getDirection().getOpposite()));
		if (level.getBlockEntity(pos) instanceof WillChestBlockEntity chest) {
			chest.fill(player, heirs, unlock, age, stacks);
		}
		Component where = Component.literal(player.getName().getString() + " left a Will Chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".");
		for (ServerPlayer heir : Family.online(level.getServer(), heirs)) {
			heir.sendSystemMessage(where.copy().withStyle(ChatFormatting.GOLD));
		}
	}

	/** Children first, then their children. */
	static List<UUID> heirsOf(ServerPlayer player) {
		List<UUID> heirs = new ArrayList<>(Life.of(player).children);
		for (ServerPlayer child : Family.online(player.level().getServer(), Life.of(player).children)) {
			for (UUID grandchild : Life.of(child).children) {
				if (!heirs.contains(grandchild)) {
					heirs.add(grandchild);
				}
			}
		}
		return heirs;
	}

	private static BlockPos findSpot(ServerLevel level, BlockPos start) {
		for (int dy = 0; dy <= 3; dy++) {
			for (int r = 0; r <= 2; r++) {
				for (BlockPos p : BlockPos.betweenClosed(start.offset(-r, dy, -r), start.offset(r, dy, r))) {
					if (level.getBlockState(p).canBeReplaced() && level.isInWorldBounds(p)) {
						return p.immutable();
					}
				}
			}
		}
		return null;
	}

	private static int totalXp(ServerPlayer player) {
		int level = player.experienceLevel;
		int points;
		if (level <= 16) {
			points = level * level + 6 * level;
		} else if (level <= 31) {
			points = (int) (2.5 * level * level - 40.5 * level + 360);
		} else {
			points = (int) (4.5 * level * level - 162.5 * level + 2220);
		}
		return points + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
	}
}
