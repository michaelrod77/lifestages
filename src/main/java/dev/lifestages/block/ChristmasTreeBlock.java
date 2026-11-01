package dev.lifestages.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Christmas tree, two blocks tall, with lights and a star on top. It appears on Christmas morning (in the
 * house if there's room) with a present under it for everyone, and is gone the next morning.
 */
public class ChristmasTreeBlock extends Block {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	private static final VoxelShape LOWER = Block.box(1, 0, 1, 15, 16, 15);
	private static final VoxelShape UPPER = Block.box(3, 0, 3, 13, 15, 13);

	public ChristmasTreeBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
	}

	/** Places both halves. */
	public static void place(Level level, BlockPos lower, Block tree) {
		level.setBlock(lower, tree.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER), 3);
		level.setBlock(lower.above(), tree.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), 3);
	}

	/** Removes both halves, given either one. */
	public static void remove(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChristmasTreeBlock)) {
			return;
		}
		BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
			BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		boolean partnerSide = half == DoubleBlockHalf.LOWER ? dir == Direction.UP : dir == Direction.DOWN;
		if (partnerSide && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
			return Blocks.AIR.defaultBlockState(); // the other half went: so does this one
		}
		return state;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		if (level.getBlockState(other).is(this)) {
			level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
