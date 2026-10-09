package dev.lifestages.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Tracks where changing tables and trash cans are, and which music boxes are playing, per dimension. */
public final class Nursery {
	private static final Map<ResourceKey<Level>, Set<BlockPos>> TABLES = new HashMap<>();
	private static final Map<ResourceKey<Level>, Set<BlockPos>> PLAYING = new HashMap<>();
	private static final Map<ResourceKey<Level>, Set<BlockPos>> TRASH = new HashMap<>();

	private Nursery() {
	}

	public static void reset() {
		TABLES.clear();
		PLAYING.clear();
		TRASH.clear();
	}

	static void addTrash(Level level, BlockPos pos) {
		TRASH.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	static void removeTrash(Level level, BlockPos pos) {
		Set<BlockPos> set = TRASH.get(level.dimension());
		if (set != null) {
			set.remove(pos);
		}
	}

	/** The closest trash can within range, or null. */
	public static BlockPos nearestTrash(Level level, BlockPos from, double range) {
		BlockPos best = null;
		for (BlockPos p : TRASH.getOrDefault(level.dimension(), Set.of())) {
			if (p.distSqr(from) <= range * range && (best == null || p.distSqr(from) < best.distSqr(from))
					&& level.getBlockEntity(p) instanceof TrashCanBlockEntity) {
				best = p;
			}
		}
		return best;
	}

	static void addTable(Level level, BlockPos pos) {
		TABLES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	static void removeTable(Level level, BlockPos pos) {
		Set<BlockPos> set = TABLES.get(level.dimension());
		if (set != null) {
			set.remove(pos);
		}
	}

	static void setPlaying(Level level, BlockPos pos, boolean playing) {
		Set<BlockPos> set = PLAYING.computeIfAbsent(level.dimension(), k -> new HashSet<>());
		if (playing) {
			set.add(pos.immutable());
		} else {
			set.remove(pos);
		}
	}

	/** The closest changing table within range, or null. */
	public static BlockPos nearestTable(Level level, BlockPos from, double range) {
		BlockPos best = null;
		for (BlockPos p : TABLES.getOrDefault(level.dimension(), Set.of())) {
			if (p.distSqr(from) <= range * range && (best == null || p.distSqr(from) < best.distSqr(from))
					&& level.getBlockEntity(p) instanceof ChangingTableBlockEntity) {
				best = p;
			}
		}
		return best;
	}

	/** True if a music box is playing within 8 blocks. */
	public static boolean musicNear(Level level, BlockPos from) {
		for (BlockPos p : PLAYING.getOrDefault(level.dimension(), Set.of())) {
			if (p.distSqr(from) <= 64) {
				return true;
			}
		}
		return false;
	}
}
