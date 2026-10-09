package dev.lifestages.life;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds steep drops around the finished house that a crawling baby could fall down. Within 10 blocks of the house,
 * any spot more than 3 blocks lower than a neighbour is a drop. Drops up to 6 deep get filled with dirt (one block
 * at a time, until no step is more than 3); deeper ones get a fence along the top edge instead.
 */
public final class DropFixer {
	public static final int RANGE = 10;
	/** A baby falls this far without getting hurt. */
	public static final int SAFE_STEP = 3;
	/** Deeper than this is fenced off rather than filled. */
	public static final int FILL_DEPTH = 6;

	/** Where to place the next block, and whether it is a fence (otherwise dirt). */
	public record Fix(BlockPos pos, boolean fence) {
	}

	private DropFixer() {
	}

	/** The nearest drop to fix, or null when the ground around the house is safe. */
	public static Fix next(ServerLevel level, BlockPos house, Set<Long> skip) {
		int x0 = house.getX() - RANGE, x1 = house.getX() + HouseBuilder.W - 1 + RANGE;
		int z0 = house.getZ() - RANGE, z1 = house.getZ() + HouseBuilder.D - 1 + RANGE;
		int w = x1 - x0 + 3, d = z1 - z0 + 3;
		int[][] ground = new int[w][d];
		boolean[][] wet = new boolean[w][d];
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < d; j++) {
				int x = x0 - 1 + i, z = z0 - 1 + j;
				ground[i][j] = groundY(level, x, z);
				wet[i][j] = !level.getFluidState(new BlockPos(x, ground[i][j], z)).isEmpty()
						|| !level.getFluidState(new BlockPos(x, ground[i][j] + 1, z)).isEmpty();
			}
		}
		double cx = house.getX() + HouseBuilder.W / 2.0, cz = house.getZ() + HouseBuilder.D / 2.0;
		Fix best = null;
		double bestDist = Double.MAX_VALUE;
		for (int i = 1; i < w - 1; i++) {
			for (int j = 1; j < d - 1; j++) {
				int x = x0 - 1 + i, z = z0 - 1 + j;
				if (wet[i][j]) {
					continue; // water isn't a fall
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					int ni = i + dir.getStepX(), nj = j + dir.getStepZ();
					if (wet[ni][nj]) {
						continue;
					}
					int drop = ground[ni][nj] - ground[i][j];
					if (drop <= SAFE_STEP) {
						continue;
					}
					Fix fix;
					if (drop <= FILL_DEPTH) {
						fix = inArea(house, x, z) ? new Fix(new BlockPos(x, ground[i][j] + 1, z), false) : null;
					} else {
						int hx = x + dir.getStepX(), hz = z + dir.getStepZ();
						fix = inArea(house, hx, hz) ? new Fix(new BlockPos(hx, ground[ni][nj] + 1, hz), true) : null;
					}
					if (fix == null || skip.contains(fix.pos.asLong()) || !level.getBlockState(fix.pos).canBeReplaced()
							|| !level.getFluidState(fix.pos).isEmpty()) {
						continue;
					}
					double dist = (fix.pos.getX() - cx) * (fix.pos.getX() - cx) + (fix.pos.getZ() - cz) * (fix.pos.getZ() - cz);
					if (dist < bestDist) {
						best = fix;
						bestDist = dist;
					}
				}
			}
		}
		return best;
	}

	/** Within 10 blocks of the house, but not the house itself or the strip right around its walls. */
	private static boolean inArea(BlockPos house, int x, int z) {
		int rx = x - house.getX(), rz = z - house.getZ();
		boolean nearHouse = rx >= -1 && rx <= HouseBuilder.W && rz >= -1 && rz <= HouseBuilder.D + 1;
		return !nearHouse && rx >= -RANGE && rx <= HouseBuilder.W - 1 + RANGE && rz >= -RANGE && rz <= HouseBuilder.D - 1 + RANGE;
	}

	/**
	 * Height of the walkable ground in a column: the top solid natural block, looking through trees, plants and
	 * anything players built (so a tree or a wall never counts as a cliff).
	 */
	static int groundY(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		int min = level.getMinY();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
		while (y > min) {
			p.setY(y);
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && level.getFluidState(p).isEmpty() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)
					&& HouseBuilder.natural(s) && s.isCollisionShapeFullBlock(level, p)) {
				return y;
			}
			if (!level.getFluidState(p).isEmpty()) {
				return y; // the surface of water counts as where you'd land
			}
			y--;
		}
		return y;
	}
}
