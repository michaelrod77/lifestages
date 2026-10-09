package dev.lifestages.block;

import dev.lifestages.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Plays "Twinkle, Twinkle, Little Star" (public domain) three times on note-block bells, then stops. */
public class MusicBoxBlockEntity extends BlockEntity {
	// Note-block semitones above F#3: C4=6, D4=8, E4=10, F4=11, G4=13, A4=15. 0 = rest. Each entry is one beat.
	private static final int[] TUNE = {
			6, 6, 13, 13, 15, 15, 13, 0, 11, 11, 10, 10, 8, 8, 6, 0,
			13, 13, 11, 11, 10, 10, 8, 0, 13, 13, 11, 11, 10, 10, 8, 0,
			6, 6, 13, 13, 15, 15, 13, 0, 11, 11, 10, 10, 8, 8, 6, 0, 0, 0};
	private static final int BEAT_TICKS = 9;
	private static final int LOOPS = 3;
	private int beat;

	public MusicBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MUSIC_BOX_ENTITY, pos, state);
	}

	public static void setPlaying(Level level, BlockPos pos, boolean playing) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.MUSIC_BOX)) {
			return;
		}
		level.setBlock(pos, state.setValue(MusicBoxBlock.PLAYING, playing), 3);
		Nursery.setPlaying(level, pos, playing);
		if (level.getBlockEntity(pos) instanceof MusicBoxBlockEntity box) {
			box.beat = 0;
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MusicBoxBlockEntity box) {
		if (!state.getValue(MusicBoxBlock.PLAYING) || level.getGameTime() % BEAT_TICKS != 0) {
			return;
		}
		Nursery.setPlaying(level, pos, true);
		int note = TUNE[box.beat % TUNE.length];
		if (note > 0) {
			float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS, 0.45f, pitch);
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.RECORDS, 0.15f, pitch);
			if (level instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 1, 0, 0, 0, note / 24.0);
			}
		}
		box.beat++;
		if (box.beat >= TUNE.length * LOOPS) {
			setPlaying(level, pos, false);
		}
	}
}
