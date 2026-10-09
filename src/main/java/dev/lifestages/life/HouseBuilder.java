package dev.lifestages.life;

import dev.lifestages.block.ChangingTableBlock;
import dev.lifestages.block.CribBlock;
import dev.lifestages.block.MusicBoxBlock;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The cozy oak cottage the NPC Mom builds, one block at a time, on a baby's first life. It is 13 x 7 with a
 * peaked roof running east-west and the front door on the south wall. The main room is x 0-8; a partition wall
 * at x 8 with a doorway leads east into the nursery (x 9-11). Relative coordinates: x 0-12 (west to east),
 * z 0-6 (north to south), y 0 = floor.
 */
public final class HouseBuilder {
	/** A block to place. Fill-only placements (foundations) only go where nothing solid is. */
	public record Placement(int x, int y, int z, BlockState state, boolean fillOnly) {
		Placement(int x, int y, int z, BlockState state) {
			this(x, y, z, state, false);
		}
	}

	/** Bumped when the blueprint changes; older houses get remodeled. 2 added the nursery, 3 the trash can. */
	public static final int VERSION = 3;
	public static final int W = 13, D = 7;
	/** The nursery crib, against the north wall. */
	public static final BlockPos CRIB = new BlockPos(10, 1, 1);
	public static final BlockPos CHANGING_TABLE = new BlockPos(11, 1, 4);
	public static final BlockPos BARREL = new BlockPos(9, 1, 1);
	public static final BlockPos TRASH_CAN = new BlockPos(11, 1, 5);
	public static final BlockPos MUSIC_BOX = new BlockPos(9, 2, 1);
	/** Middle of the nursery floor, where the Mom plays with the baby at night. */
	public static final BlockPos NURSERY = new BlockPos(10, 1, 3);
	/** A spot inside, in front of the hearth, that the Mom treats as home. */
	public static final BlockPos INSIDE = new BlockPos(4, 1, 3);
	/** The front yard, two steps past the end of the path. */
	public static final BlockPos YARD = new BlockPos(4, 1, 10);
	/** Center of the footprint, where the Mom stands to build. */
	public static final BlockPos CENTER = new BlockPos(6, 1, 3);
	private static final List<Placement> PLAN = blueprint();

	private HouseBuilder() {
	}

	public static int size() {
		return PLAN.size();
	}

	// ---- the blueprint ----

	private static List<Placement> blueprint() {
		List<Placement> solid = new ArrayList<>();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		// floor: cobblestone rim, plank inside
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				boolean rim = x == 0 || z == 0 || x == W - 1 || z == D - 1;
				solid.add(new Placement(x, 0, z, rim ? cobble : planks));
			}
		}
		// outer walls, bottom to top; log posts at the corners and where the partition meets the long walls
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x < W; x++) {
				for (int z = 0; z < D; z++) {
					boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
					if (!edge) {
						continue;
					}
					boolean post = (x == 0 || x == 8 || x == W - 1) && (z == 0 || z == D - 1);
					boolean door = x == 4 && z == D - 1 && y <= 2;
					boolean window = y == 2 && !post && ((z == 0 || z == D - 1) && (x == 2 || x == 6 || x == 10) || (x == 0 || x == W - 1) && z == 3);
					if (door) {
						continue;
					}
					solid.add(new Placement(x, y, z, post ? log : window ? pane : planks));
				}
			}
		}
		// partition wall between the main room and the nursery, with a doorway in the middle
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z < D - 1; z++) {
				if (z == 3 && y <= 2) {
					continue;
				}
				solid.add(new Placement(8, y, z, planks));
			}
		}
		// gable ends above the eaves: west, partition and east
		for (int y = 4; y <= 6; y++) {
			int inset = y - 4;
			for (int z = inset; z < D - inset; z++) {
				for (int x : new int[] {0, 8, W - 1}) {
					solid.add(new Placement(x, y, z, planks));
				}
			}
		}
		// roof: stair rows rising to a plank ridge, overhanging one block all round
		for (int k = 0; k <= 3; k++) {
			int y = 4 + k;
			for (int x = -1; x <= W; x++) {
				solid.add(new Placement(x, y, -1 + k, stairs(Direction.SOUTH)));
				solid.add(new Placement(x, y, D - k, stairs(Direction.NORTH)));
			}
		}
		for (int x = -1; x <= W; x++) {
			solid.add(new Placement(x, 7, 3, planks));
		}
		// front door
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
		solid.add(new Placement(4, 1, D - 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
		solid.add(new Placement(4, 2, D - 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
		// main room
		BlockState bed = Blocks.BED.lightBlue().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		solid.add(new Placement(6, 1, 1, bed.setValue(BedBlock.PART, BedPart.FOOT)));
		solid.add(new Placement(7, 1, 1, bed.setValue(BedBlock.PART, BedPart.HEAD)));
		solid.add(new Placement(1, 1, 1, Blocks.BOOKSHELF.defaultBlockState()));
		solid.add(new Placement(1, 1, 5, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST)));
		solid.add(new Placement(7, 1, 5, Blocks.CRAFTING_TABLE.defaultBlockState()));
		solid.add(new Placement(7, 1, 4, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST)));
		solid.add(new Placement(1, 2, 5, Blocks.LANTERN.defaultBlockState()));
		solid.add(new Placement(7, 2, 5, Blocks.LANTERN.defaultBlockState()));
		for (int x = 3; x <= 5; x++) {
			for (int z = 2; z <= 4; z++) {
				solid.add(new Placement(x, 1, z, Blocks.CARPET.pink().defaultBlockState()));
			}
		}
		// nursery: crib, changing table and trash can, a barrel of diapers and bottles with the music box on top, soft carpet
		solid.add(new Placement(CHANGING_TABLE.getX(), 1, CHANGING_TABLE.getZ(),
				ModBlocks.CHANGING_TABLE.defaultBlockState().setValue(ChangingTableBlock.FACING, Direction.WEST)));
		solid.add(new Placement(BARREL.getX(), 1, BARREL.getZ(), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)));
		solid.add(new Placement(MUSIC_BOX.getX(), 2, MUSIC_BOX.getZ(),
				ModBlocks.MUSIC_BOX.defaultBlockState().setValue(MusicBoxBlock.FACING, Direction.SOUTH)));
		solid.add(new Placement(9, 1, 5, Blocks.LANTERN.defaultBlockState()));
		solid.add(new Placement(TRASH_CAN.getX(), 1, TRASH_CAN.getZ(), ModBlocks.TRASH_CAN.defaultBlockState()));
		Set<Long> furnished = new HashSet<>(Arrays.asList(BlockPos.asLong(CRIB.getX(), 1, CRIB.getZ()),
				BlockPos.asLong(CHANGING_TABLE.getX(), 1, CHANGING_TABLE.getZ()), BlockPos.asLong(BARREL.getX(), 1, BARREL.getZ()),
				BlockPos.asLong(9, 1, 5), BlockPos.asLong(TRASH_CAN.getX(), 1, TRASH_CAN.getZ())));
		for (int x = 9; x <= 11; x++) {
			for (int z = 1; z <= 5; z++) {
				if (!furnished.contains(BlockPos.asLong(x, 1, z))) {
					solid.add(new Placement(x, 1, z, Blocks.CARPET.lightBlue().defaultBlockState()));
				}
			}
		}
		// outside: torches by the door and a short path
		solid.add(new Placement(3, 2, D, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)));
		solid.add(new Placement(5, 2, D, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)));
		solid.add(new Placement(4, 0, D, Blocks.DIRT_PATH.defaultBlockState()));
		solid.add(new Placement(4, 0, D + 1, Blocks.DIRT_PATH.defaultBlockState()));

		// Build order: the crib first (so naps can start), then clear the site, foundations, then everything else.
		Set<Long> filled = new HashSet<>();
		for (Placement p : solid) {
			filled.add(BlockPos.asLong(p.x, p.y, p.z));
		}
		filled.add(BlockPos.asLong(CRIB.getX(), CRIB.getY(), CRIB.getZ()));
		List<Placement> plan = new ArrayList<>();
		plan.add(new Placement(CRIB.getX(), 0, CRIB.getZ(), planks));
		plan.add(new Placement(CRIB.getX(), CRIB.getY(), CRIB.getZ(), ModBlocks.CRIB.defaultBlockState().setValue(CribBlock.FACING, Direction.SOUTH)));
		for (int y = 7; y >= 1; y--) {
			for (int x = -1; x <= W; x++) {
				for (int z = -1; z <= D; z++) {
					if (!filled.contains(BlockPos.asLong(x, y, z))) {
						plan.add(new Placement(x, y, z, Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}
		for (int y = -4; y <= -1; y++) {
			for (int x = 0; x < W; x++) {
				for (int z = 0; z < D; z++) {
					plan.add(new Placement(x, y, z, cobble, true));
				}
			}
		}
		for (int z = D; z <= D + 1; z++) {
			plan.add(new Placement(4, -1, z, Blocks.DIRT.defaultBlockState(), true));
		}
		plan.addAll(solid);
		return plan;
	}

	private static BlockState stairs(Direction facing) {
		return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	// ---- choosing a site ----

	/**
	 * Finds the flattest dry spot near home for the house. The Mom levels the ground herself, so hills and trees
	 * are fine, but never water, other people's chests or unbreakable blocks. Returns the origin (north-west floor
	 * corner) or null.
	 */
	public static BlockPos plan(ServerLevel level, BlockPos home) {
		return plan(level, home, java.util.List.of());
	}

	/** As above, keeping at least 4 blocks clear of other players' houses. */
	public static BlockPos plan(ServerLevel level, BlockPos home, java.util.List<BlockPos> otherHouses) {
		record Site(BlockPos origin, double score) {
		}
		List<Site> sites = new ArrayList<>();
		for (int dx = -24; dx <= 24; dx += 2) {
			for (int dz = -24; dz <= 24; dz += 2) {
				int ox = home.getX() + dx - W / 2, oz = home.getZ() + dz - D / 2;
				int[] heights = new int[W * D];
				int i = 0;
				for (int x = 0; x < W; x++) {
					for (int z = 0; z < D; z++) {
						heights[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox + x, oz + z) - 1;
					}
				}
				int[] sorted = heights.clone();
				Arrays.sort(sorted);
				int floor = sorted[sorted.length / 2];
				double rough = 0;
				for (int h : heights) {
					if (h < floor - 4 || h > floor + 5) {
						rough = Double.MAX_VALUE;
						break;
					}
					rough += Math.abs(h - floor);
				}
				if (rough < Double.MAX_VALUE) {
					sites.add(new Site(new BlockPos(ox, floor, oz), rough + Math.sqrt(dx * dx + dz * dz) * 2));
				}
			}
		}
		sites.sort((a, b) -> Double.compare(a.score, b.score));
		for (Site s : sites) {
			if (overlaps(s.origin, otherHouses)) {
				continue;
			}
			if (suitable(level, s.origin)) {
				return s.origin;
			}
		}
		return null;
	}

	/** Within 4 blocks of another house's footprint (roof overhang and yard included). */
	private static boolean overlaps(BlockPos origin, java.util.List<BlockPos> others) {
		int gap = 4;
		for (BlockPos o : others) {
			boolean xs = origin.getX() - 1 <= o.getX() + W + gap && o.getX() - 1 <= origin.getX() + W + gap;
			boolean zs = origin.getZ() - 1 <= o.getZ() + D + 3 + gap && o.getZ() - 1 <= origin.getZ() + D + 3 + gap;
			if (xs && zs && Math.abs(origin.getY() - o.getY()) < 12) {
				return true;
			}
		}
		return false;
	}

	private static boolean suitable(ServerLevel level, BlockPos origin) {
		int built = 0; // other people's buildings (an offline player's house, say): avoid them
		for (int x = -1; x <= W; x++) {
			for (int z = -1; z <= D + 1; z++) {
				for (int y = 0; y <= 8; y++) {
					BlockPos p = origin.offset(x, y, z);
					BlockState s = level.getBlockState(p);
					if (!level.getFluidState(p).isEmpty() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) {
						return false;
					}
					if (!natural(s) && ++built > 6) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Terrain and plants. Anything else the Mom clears away is dropped as an item so nothing is lost. */
	static boolean natural(BlockState s) {
		return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.DIRT)
				|| s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH) || s.is(BlockTags.SAND) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.STONE)
				|| s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
				|| s.is(Blocks.CLAY) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN)
				|| s.is(Blocks.MELON) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)
				|| s.is(Blocks.DEEPSLATE) || s.is(Blocks.TUFF) || s.is(Blocks.SANDSTONE) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.ICE)
				|| s.is(BlockTags.BASE_STONE_OVERWORLD);
	}

	/** Blocks the cottage itself is made of. Remodeling an older cottage clears these without dropping them. */
	private static boolean houseMaterial(BlockState s) {
		return s.is(Blocks.OAK_PLANKS) || s.is(Blocks.OAK_LOG) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_STAIRS)
				|| s.is(Blocks.GLASS_PANE) || s.is(ModBlocks.CRIB) || s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.LANTERN)
				|| s.is(Blocks.WALL_TORCH);
	}

	// ---- building ----

	/**
	 * Places the next block that needs changing. Steps that are already right are skipped without cost. Block
	 * entities (chests, furnaces...) and unbreakable blocks are never touched. Returns the new step index;
	 * {@link #size()} means the house is finished.
	 */
	public static int step(ServerLevel level, BlockPos origin, int index) {
		int skipped = 0;
		while (index < PLAN.size()) {
			Placement p = PLAN.get(index++);
			BlockPos pos = origin.offset(p.x, p.y, p.z);
			BlockState current = level.getBlockState(pos);
			boolean twoPart = p.state.getBlock() instanceof BedBlock || p.state.getBlock() instanceof DoorBlock;
			BlockState state = twoPart || p.state.isAir() ? p.state : Block.updateFromNeighbourShapes(p.state, level, pos);
			boolean done = current.equals(state) || current.equals(p.state) || (p.state.isAir() && current.isAir())
					|| (p.fillOnly && !current.canBeReplaced() && level.getFluidState(pos).isEmpty())
					|| level.getBlockEntity(pos) != null || current.getDestroySpeed(level, pos) < 0;
			if (done) {
				if (++skipped > 256) {
					return index;
				}
				continue;
			}
			if (!current.isAir() && !current.canBeReplaced() && !natural(current) && !houseMaterial(current)) {
				level.destroyBlock(pos, true); // someone's build: drop it as items
			}
			level.setBlock(pos, state, Block.UPDATE_ALL);
			if (state.is(Blocks.BARREL) && level.getBlockEntity(pos) instanceof Container barrel) {
				barrel.setItem(0, new ItemStack(ModItems.CLEAN_DIAPER, 16));
				barrel.setItem(1, new ItemStack(ModItems.CLEAN_DIAPER, 16));
				barrel.setItem(2, new ItemStack(ModItems.MILK_BOTTLE, 16));
				barrel.setItem(3, new ItemStack(ModItems.MILK_BOTTLE, 8));
				barrel.setItem(4, new ItemStack(ModItems.RATTLE));
			}
			var sound = (state.isAir() ? current : state).getSoundType();
			level.playSound(null, pos, state.isAir() ? sound.getBreakSound() : sound.getPlaceSound(), SoundSource.BLOCKS, 0.6f, 1.0f);
			return index;
		}
		return index;
	}
}
