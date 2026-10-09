package dev.lifestages.entity;

import dev.lifestages.registry.ModEffects;
import dev.lifestages.registry.ModEntities;
import dev.lifestages.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A thrown Dirty Diaper: splats for Nausea 4 s, Blindness 1 s and Stinky 20 s. */
public class ThrownDiaper extends ThrowableItemProjectile {
	/** Landed in a trash can: no splat. */
	private boolean binned;

	public ThrownDiaper(EntityType<? extends ThrownDiaper> type, Level level) {
		super(type, level);
	}

	public ThrownDiaper(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.THROWN_DIAPER, owner, level, stack);
	}

	public static void throwFrom(ServerLevel level, LivingEntity owner, ItemStack stack) {
		Projectile.spawnProjectileFromRotation(ThrownDiaper::new, level, stack, owner, 0.0f, 1.2f, 1.0f);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.DIRTY_DIAPER;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (hit.getEntity() instanceof LivingEntity target) {
			target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 4 * 20), getOwner());
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20), getOwner());
			target.addEffect(new MobEffectInstance(ModEffects.STINKY, 20 * 20), getOwner());
		}
	}

	@Override
	protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel server && server.getBlockState(hit.getBlockPos()).getBlock() instanceof dev.lifestages.block.TrashCanBlock) {
			dev.lifestages.block.TrashCanBlock.dispose(server, hit.getBlockPos());
			if (getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) {
				thrower.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Swish!").withStyle(net.minecraft.ChatFormatting.GREEN));
				dev.lifestages.registry.ModAdvancements.award(thrower, "nothing_but_net");
			}
			binned = true;
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(getItem())),
					getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.05);
			if (!binned) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			discard();
		}
	}
}
