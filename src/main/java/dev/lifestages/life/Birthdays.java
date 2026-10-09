package dev.lifestages.life;

import dev.lifestages.registry.ModBlocks;
import dev.lifestages.block.PresentBlock;
import dev.lifestages.block.PresentBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.Block;
import dev.lifestages.util.Give;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A present on every birthday (stage-up). The first is a Teddy Bear; after that, rarer the older you are. */
public final class Birthdays {
	private Birthdays() {
	}

	public static void give(ServerPlayer player, LifeData d, Stage reached) {
		// The gift is inside the present: the first one holds a Teddy Bear, later ones roll their tier's loot table.
		Block block = d.presents == 0 ? ModBlocks.PRESENT_COMMON : ModBlocks.presentFor(reached);
		String table = d.presents == 0 ? "teddy" : ((PresentBlock) block).tier();
		ItemStack present = new ItemStack(block);
		if (reached == Stage.ADULT) {
			// Time to start a family: four Baby Spawn Eggs, one per slot. Use as many as you like.
			java.util.List<ItemStack> eggs = new java.util.ArrayList<>();
			for (int i = 0; i < 4; i++) {
				eggs.add(new ItemStack(dev.lifestages.registry.ModItems.BABY_SPAWN_EGG));
			}
			present.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(eggs));
			d.presents++;
			Give.item(player, present);
			player.sendSystemMessage(Component.literal("Happy birthday! This present is special: inside are four Baby Spawn Eggs. Have as many babies as you like!")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		present.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(PresentBlockEntity.lootTable(table), player.getRandom().nextLong()));
		d.presents++;
		Give.item(player, present);
		player.sendSystemMessage(Component.literal("Happy birthday! You got a present. Place it and open it to see what's inside.")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Holding a Teddy Bear in either hand. */
	public static boolean holdingTeddy(ServerPlayer player) {
		Item teddy = ModBlocks.TEDDY_BEAR.asItem();
		return player.getMainHandItem().is(teddy) || player.getOffhandItem().is(teddy);
	}
}
