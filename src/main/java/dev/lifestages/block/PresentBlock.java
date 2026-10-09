package dev.lifestages.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wrapped birthday present. Place it, then right-click to open it like a small chest with four slots; the gift
 * is inside. Broken, it drops as a present that keeps its contents (still wrapped if nobody opened it), so it
 * can be given to someone else.
 */
public class PresentBlock extends BaseEntityBlock implements BabySafe {
	private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 9.5, 12.5);
	private final String tier;

	public PresentBlock(Properties props, String tier) {
		super(props);
		this.tier = tier;
	}

	public String tier() {
		return tier;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PresentBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
		return PICK_UP_PROGRESS; // babies can pick it back up even though they can't mine
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PresentBlockEntity present) {
			player.openMenu(present);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Creative players get no block drops, so hand them the present with its contents (like a shulker box).
		if (!level.isClientSide() && player.preventsBlockDrops() && level.getBlockEntity(pos) instanceof PresentBlockEntity present
				&& (!present.isEmpty() || present.getLootTable() != null)) {
			ItemStack stack = new ItemStack(this);
			stack.applyComponents(present.collectComponents());
			ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
			entity.setDefaultPickUpDelay();
			level.addFreshEntity(entity);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}
}
