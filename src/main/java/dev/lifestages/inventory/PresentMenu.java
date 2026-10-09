package dev.lifestages.inventory;

import dev.lifestages.block.PresentBlockEntity;
import dev.lifestages.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A present's four slots, centered above the player's inventory (same layout as a hopper). */
public class PresentMenu extends AbstractContainerMenu {
	private final Container present;

	public PresentMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(PresentBlockEntity.SIZE));
	}

	public PresentMenu(int id, Inventory inventory, Container present) {
		super(ModMenus.PRESENT, id);
		this.present = present;
		checkContainerSize(present, PresentBlockEntity.SIZE);
		present.startOpen(inventory.player);
		for (int i = 0; i < PresentBlockEntity.SIZE; i++) {
			addSlot(new Slot(present, i, 53 + i * 18, 20));
		}
		addStandardInventorySlots(inventory, 8, 51);
	}

	@Override
	public boolean stillValid(Player player) {
		return present.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();
			int size = present.getContainerSize();
			if (index < size ? !moveItemStackTo(stack, size, slots.size(), true) : !moveItemStackTo(stack, 0, size, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		present.stopOpen(player);
	}
}
