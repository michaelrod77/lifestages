package dev.lifestages.block;

import dev.lifestages.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class WillChestBlockEntity extends BlockEntity {
	public static final int SIZE = 54;
	private final SimpleContainer items = new SimpleContainer(SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			WillChestBlockEntity.this.setChanged();
		}
	};
	private String ownerName = "Someone";
	private UUID owner;
	private List<UUID> heirs = new ArrayList<>();
	private long unlockAt;
	private int age;
	private boolean opened;

	public WillChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.WILL_CHEST_ENTITY, pos, state);
	}

	public void fill(Player owner, List<UUID> heirs, long unlockAt, int age, List<ItemStack> stacks) {
		this.ownerName = owner.getName().getString();
		this.owner = owner.getUUID();
		this.heirs = new ArrayList<>(heirs);
		this.unlockAt = unlockAt;
		this.age = age;
		for (ItemStack stack : stacks) {
			items.addItem(stack);
		}
		setChanged();
	}

	public SimpleContainer container() {
		return items;
	}

	public String ownerName() {
		return ownerName;
	}

	public boolean canOpen(Player player) {
		return heirs.isEmpty() || heirs.contains(player.getUUID()) || player.getUUID().equals(owner)
				|| (level != null && level.getGameTime() >= unlockAt) || player.isCreative();
	}

	public String lockedMessage() {
		long minutes = level == null ? 0 : Math.max(1, (unlockAt - level.getGameTime()) / 1200);
		return "This will is for " + ownerName + "'s family. Anyone can open it in " + minutes + " min.";
	}

	public void markOpened() {
		opened = true;
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WillChestBlockEntity chest) {
		if (level.getGameTime() % 20 != 0 || !chest.opened || !chest.items.isEmpty()) {
			return;
		}
		BlockState grave = ModBlocks.GRAVESTONE.defaultBlockState().setValue(GravestoneBlock.FACING, state.getValue(WillChestBlock.FACING));
		String name = chest.ownerName;
		int age = chest.age;
		level.setBlockAndUpdate(pos, grave);
		if (level.getBlockEntity(pos) instanceof GravestoneBlockEntity stone) {
			stone.set(name, age);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		for (int i = 0; i < SIZE; i++) {
			list.set(i, items.getItem(i));
		}
		ContainerHelper.saveAllItems(out, list);
		out.putString("owner_name", ownerName);
		if (owner != null) {
			out.store("owner", UUIDUtil.CODEC, owner);
		}
		out.store("heirs", UUIDUtil.CODEC.listOf(), heirs);
		out.putLong("unlock_at", unlockAt);
		out.putInt("age", age);
		out.putBoolean("opened", opened);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(in, list);
		for (int i = 0; i < SIZE; i++) {
			items.setItem(i, list.get(i));
		}
		ownerName = in.getStringOr("owner_name", "Someone");
		owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
		heirs = new ArrayList<>(in.read("heirs", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		unlockAt = in.getLongOr("unlock_at", 0);
		age = in.getIntOr("age", 0);
		opened = in.getBooleanOr("opened", false);
	}
}
