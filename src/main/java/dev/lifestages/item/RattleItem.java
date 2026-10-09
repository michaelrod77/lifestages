package dev.lifestages.item;

import dev.lifestages.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A baby's rattle. Shake it (right-click) for a jingle and a few musical notes. */
public class RattleItem extends Item {
	public RattleItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (level instanceof ServerLevel server) {
			server.playSound(null, user.blockPosition(), ModSounds.RATTLE, SoundSource.PLAYERS, 0.8f, 1.4f + user.getRandom().nextFloat() * 0.3f);
			server.sendParticles(ParticleTypes.NOTE, user.getX(), user.getEyeY() + 0.3, user.getZ(), 2, 0.3, 0.2, 0.3, 1.0);
		}
		user.getCooldowns().addCooldown(user.getItemInHand(hand), 10);
		return InteractionResult.SUCCESS;
	}
}
