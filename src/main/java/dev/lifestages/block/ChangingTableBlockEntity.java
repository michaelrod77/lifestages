package dev.lifestages.block;

import dev.lifestages.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exists so the Mom can find changing tables in loaded chunks. */
public class ChangingTableBlockEntity extends BlockEntity {
	public ChangingTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CHANGING_TABLE_ENTITY, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Nursery.addTable(level, getBlockPos());
		}
	}

	@Override
	public void setRemoved() {
		if (level != null && !level.isClientSide()) {
			Nursery.removeTable(level, getBlockPos());
		}
		super.setRemoved();
	}
}
