package dev.lifestages.block;

import dev.lifestages.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exists so the Mom can find trash cans in loaded chunks. */
public class TrashCanBlockEntity extends BlockEntity {
	public TrashCanBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TRASH_CAN_ENTITY, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Nursery.addTrash(level, getBlockPos());
		}
	}

	@Override
	public void setRemoved() {
		if (level != null && !level.isClientSide()) {
			Nursery.removeTrash(level, getBlockPos());
		}
		super.setRemoved();
	}
}
