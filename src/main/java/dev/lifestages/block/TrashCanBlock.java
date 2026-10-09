package dev.lifestages.block;

import dev.lifestages.registry.ModItems;
import dev.lifestages.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lidded trash can for dirty diapers. Right-click with a dirty diaper to throw it away (sneak to throw away
 * anything else), or hit it with a thrown diaper. The NPC Mom brings dirty diapers here too.
 */
public class TrashCanBlock extends BaseEntityBlock {
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 13.5, 12.5);

	public TrashCanBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(OPEN, false));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrashCanBlockEntity(pos, state);
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
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean diaper = stack.is(ModItems.DIRTY_DIAPER);
		if (!diaper && !player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.literal("Sneak and right-click to throw this away.").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			player.setItemInHand(hand, ItemStack.EMPTY);
			dispose(server, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** Lid up, a squelch and a puff of stink, lid down a second later. */
	public static void dispose(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TrashCanBlock block)) {
			return;
		}
		level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, block, 20);
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.4f);
		level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.8f, 0.8f);
		level.sendParticles(ModParticles.STINK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.01);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(OPEN)) {
			level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN);
	}
}
