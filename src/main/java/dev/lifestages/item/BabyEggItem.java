package dev.lifestages.item;

import dev.lifestages.entity.BabyEntity;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModEntities;
import dev.lifestages.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Have a baby: use on the ground and an NPC baby arrives, with you as its parent. Adults get four in their
 * birthday present; use as many as you like.
 */
public class BabyEggItem extends Item {
	public BabyEggItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer parent)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
		BabyEntity baby = ModEntities.BABY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (baby == null) {
			return InteractionResult.FAIL;
		}
		baby.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, parent.getYRot() + 180, 0);
		baby.hatch(parent, at);
		level.addFreshEntity(baby);
		ctx.getItemInHand().consume(1, parent);
		level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7f, 1.6f);
		level.sendParticles(ModParticles.SPARKLE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.0);
		parent.sendSystemMessage(Component.literal("Congratulations! Meet your baby, " + baby.getName().getString() + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
		parent.sendSystemMessage(Component.literal("Keep them fed (milk bottles), clean (clean diapers) and rested (a crib), and keep them safe. "
				+ "Sneak + right-click with an empty hand to pick them up; right-click a crib while carrying them to lay them down.")
				.withStyle(ChatFormatting.GRAY));
		ModAdvancements.award(parent, "parenthood");
		return InteractionResult.SUCCESS;
	}
}
