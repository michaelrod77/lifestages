package dev.lifestages.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An Easter egg, hidden around home on Easter morning: six pastel designs. Mine it and a sweet treat lands
 * straight in your inventory (a cookie, a cake or a Chocolate Bunny). Anyone can find one, babies too.
 */
public class EasterEggBlock extends Block implements BabySafe {
	public static final int DESIGNS = 6;
	public static final IntegerProperty DESIGN = IntegerProperty.create("design", 0, DESIGNS - 1);
	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 9, 11);

	public EasterEggBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(DESIGN, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DESIGN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return PICK_UP_PROGRESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (player instanceof ServerPlayer sp) {
			dev.lifestages.life.Holidays.eggFound(sp, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
