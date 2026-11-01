package dev.lifestages.block;

import dev.lifestages.LifeStages;
import dev.lifestages.inventory.PresentMenu;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModComponents;
import dev.lifestages.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/** Four slots. The gift inside is rolled from the present's loot table the first time someone opens it. */
public class PresentBlockEntity extends RandomizableContainerBlockEntity {
	public static final int SIZE = 4;
	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** A Christmas present under the tree is for one person: only they (or someone in creative) can open it. */
	private java.util.UUID owner;
	private String ownerName;

	public PresentBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.PRESENT_ENTITY, pos, state);
	}

	/** The loot table for a present: {@code teddy} for a first birthday, otherwise the present's tier. */
	public static ResourceKey<LootTable> lootTable(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, LifeStages.id("present/" + name));
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return ownerName != null ? Component.literal("Gift for " + ownerName) : Component.translatable("container.lifestages.present");
	}

	/** Labels this present for one person. */
	public void giftFor(java.util.UUID id, String name) {
		owner = id;
		ownerName = name;
		setChanged();
	}

	/** Who this present is for, or null if anyone may open it. */
	public String ownerFor(Player player) {
		return owner == null || owner.equals(player.getUUID()) || player.isCreative() ? null : ownerName;
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new PresentMenu(id, inventory, this);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		boolean wrapped = lootTable != null;
		AbstractContainerMenu menu = super.createMenu(id, inventory, player);
		if (wrapped && menu != null && level instanceof ServerLevel server) {
			unwrapped(server, player);
		}
		return menu;
	}

	/** Sparkles, a fanfare and a chat line naming what was inside. */
	private void unwrapped(ServerLevel server, Player player) {
		BlockPos pos = getBlockPos();
		MutableComponent list = Component.empty();
		boolean first = true;
		for (ItemStack gift : items) {
			if (gift.isEmpty()) {
				continue;
			}
			list.append(first ? Component.empty() : Component.literal(", "))
					.append(Component.literal(gift.getCount() > 1 ? gift.getCount() + " " : "")).append(gift.getHoverName());
			first = false;
		}
		if (!first) {
			player.sendSystemMessage(Component.literal("You unwrapped: ").withStyle(ChatFormatting.GOLD).append(list));
		}
		server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7f, 1.4f);
		server.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 0.8f, 1.0f);
		server.sendParticles(ModParticles.SPARKLE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.05);
		server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
	}

	@Override
	public void startOpen(net.minecraft.world.entity.ContainerUser user) {
		super.startOpen(user);
		if (level != null && !remove) {
			level.playSound(null, getBlockPos(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		ownerName = input.getStringOr("GiftFor", "");
		String id = input.getStringOr("GiftForId", "");
		if (ownerName.isEmpty() || id.isEmpty()) {
			ownerName = null;
			owner = null;
		} else {
			owner = java.util.UUID.fromString(id);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items, false);
		}
		if (owner != null) {
			output.putString("GiftFor", ownerName);
			output.putString("GiftForId", owner.toString());
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		// A present with nothing recorded inside (from an older version, or the creative tab) gets its gift now.
		if (components.get(DataComponents.CONTAINER) == null && components.get(DataComponents.CONTAINER_LOOT) == null
				&& getBlockState().getBlock() instanceof PresentBlock present) {
			boolean teddy = Boolean.TRUE.equals(components.get(ModComponents.TEDDY_INSIDE));
			setLootTable(lootTable(teddy ? "teddy" : present.tier()), level == null ? 0 : level.getRandom().nextLong());
		}
	}

	/** Contents travel with the dropped present instead of spilling out. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
	}
}
