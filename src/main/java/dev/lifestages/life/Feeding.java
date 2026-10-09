package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Who may eat what, credit for food and milk goals, sugar rushes, and feeding babies and baby animals. */
public final class Feeding {
	public static final TagKey<Item> SOFT_FOODS = TagKey.create(Registries.ITEM, LifeStages.id("soft_foods"));
	public static final TagKey<Item> SUGARY = TagKey.create(Registries.ITEM, LifeStages.id("sugary"));
	private static final Map<UUID, Integer> MILK_STREAK = new HashMap<>();

	private Feeding() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		MILK_STREAK.clear();
	}

	/**
	 * ItemEvents.USE. Newborns can't use items at all (they're fed by others); everyone else uses items normally,
	 * and children can eat plain sugar. Returns null for "no opinion": anything else (even PASS) replaces the
	 * item's own use, which once broke fishing rods, bows and the like for everyone.
	 */
	public static InteractionResult onUse(Level level, Player player, InteractionHand hand) {
		if (player.isCreative() || player.isSpectator()) {
			return null;
		}
		LifeData d = Life.of(player);
		if (!d.started) {
			return null;
		}
		Stage s = d.stage();
		ItemStack stack = player.getItemInHand(hand);
		if (s.isNewborn()) {
			return InteractionResult.FAIL;
		}
		if (stack.is(Items.SUGAR) && s.getsSugarRush() && s.atLeast(Stage.TODDLER)) {
			if (player instanceof ServerPlayer sp) {
				player.getFoodData().eat(1, 0.1f);
				level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
				d.food += 1;
				sugarRush(sp, d);
				Life.save(sp, d);
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	/** Called from the FoodProperties mixin when anything edible is finished. */
	public static void onEaten(ServerPlayer player, ItemStack stack, FoodProperties food) {
		LifeData d = Life.of(player);
		if (!d.started || d.isGhost()) {
			return;
		}
		Stage s = d.stage();
		if (stack.is(ModItems.MILK_BOTTLE)) {
			if (s.milk > 0) {
				d.milk++;
			} else {
				d.food += food.nutrition();
			}
			int streak = MILK_STREAK.merge(player.getUUID(), 1, Integer::sum);
			if (streak >= 3) {
				ModAdvancements.award(player, "milk_drunk");
			}
		} else {
			d.food += food.nutrition();
			MILK_STREAK.remove(player.getUUID());
		}
		if (s.wearsDiaper() && s != Stage.ELDERLY) {
			Bodily.fed(d);
		}
		if (stack.is(SUGARY) && s.getsSugarRush()) {
			sugarRush(player, d);
		}
		if (s == Stage.ELDERLY) {
			goldenBonus(player, d, stack);
		}
		Life.save(player, d);
	}

	/** Eating a slice of cake (a block, so it bypasses the food item hook). */
	public static void ateCake(ServerPlayer player) {
		LifeData d = Life.of(player);
		d.food += 2;
		if (d.stage().getsSugarRush()) {
			sugarRush(player, d);
		}
		Life.save(player, d);
	}

	public static void sugarRush(ServerPlayer player, LifeData d) {
		boolean first = d.sugarTicks == 0;
		d.sugarTicks = LifeConfig.get().sugarRushSeconds * 20;
		LifeManager.applyAttributes(player, d);
		if (first) {
			player.sendOverlayMessage(Component.literal("SUGAR RUSH!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
			ModAdvancements.award(player, "sugar_rush");
		}
	}

	private static void goldenBonus(ServerPlayer player, LifeData d, ItemStack stack) {
		int minutes = stack.is(Items.ENCHANTED_GOLDEN_APPLE) ? 10 : stack.is(Items.GOLDEN_APPLE) ? 3 : stack.is(Items.GOLDEN_CARROT) ? 1 : 0;
		if (minutes == 0) {
			return;
		}
		int cap = LifeConfig.get().ticks(15);
		int before = d.bonusTicks;
		d.bonusTicks = Math.min(cap, d.bonusTicks + LifeConfig.get().ticks(minutes));
		if (d.bonusTicks > before) {
			player.sendOverlayMessage(Component.literal("You feel a little younger.").withStyle(ChatFormatting.GOLD));
		}
	}

	/** UseEntityCallback: feeding a baby player a milk bottle or (past Newborn) any food, or a baby animal. */
	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target) {
		ItemStack stack = player.getItemInHand(hand);
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (!stack.is(ModItems.MILK_BOTTLE) && food != null && target instanceof ServerPlayer baby && player instanceof ServerPlayer feeder
				&& !player.isShiftKeyDown()) {
			LifeData d = Life.of(baby);
			if (!d.stage().atMost(Stage.TODDLER)) {
				return InteractionResult.PASS;
			}
			if (d.stage().isNewborn()) {
				feeder.sendOverlayMessage(Component.literal("Newborns can only drink milk.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (!baby.getFoodData().needsFood()) {
				feeder.sendOverlayMessage(Component.literal(baby.getName().getString() + " is full.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			feedFood(feeder, baby, stack, food);
			return InteractionResult.SUCCESS;
		}
		if (!stack.is(ModItems.MILK_BOTTLE)) {
			return InteractionResult.PASS;
		}
		if (target instanceof ServerPlayer baby && player instanceof ServerPlayer feeder) {
			LifeData d = Life.of(baby);
			if (!d.stage().atMost(Stage.TODDLER) || !baby.getFoodData().needsFood() && d.milk >= d.stage().milk) {
				return InteractionResult.PASS;
			}
			feedBottle(feeder, baby, stack);
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return target instanceof AgeableMob || target instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (target instanceof AgeableMob mob && mob.isBaby()) {
			mob.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-mob.getAge()), true);
			((ServerLevel) level).sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
			useUp(player, hand, stack);
			return InteractionResult.SUCCESS;
		}
		if (target instanceof TamableAnimal pet && pet.isTame() && pet.getHealth() < pet.getMaxHealth()) {
			pet.heal(4.0f);
			((ServerLevel) level).sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight(), pet.getZ(), 3, 0.3, 0.2, 0.3, 0);
			useUp(player, hand, stack);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** A grown-up feeds a baby player a bite of real food. */
	public static void feedFood(ServerPlayer feeder, ServerPlayer baby, ItemStack stack, FoodProperties food) {
		LifeData d = Life.of(baby);
		baby.getFoodData().eat(food.nutrition(), food.saturation());
		d.food += food.nutrition();
		Bodily.fed(d);
		if (stack.is(SUGARY) && d.stage().getsSugarRush()) {
			sugarRush(baby, d);
		}
		Life.save(baby, d);
		baby.level().playSound(null, baby.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
		((ServerLevel) baby.level()).sendParticles(ParticleTypes.HEART, baby.getX(), baby.getEyeY() + 0.3, baby.getZ(), 2, 0.2, 0.1, 0.2, 0);
		if (!feeder.isCreative()) {
			stack.shrink(1);
		}
	}

	/** Gives a baby a bottle: hunger, milk credit, and the bottle comes back empty. Used by players and Moms. */
	public static void feedBottle(Entity feeder, ServerPlayer baby, ItemStack stack) {
		LifeData d = Life.of(baby);
		baby.getFoodData().eat(3, 0.6f);
		if (d.stage().milk > 0) {
			d.milk++;
		} else {
			d.food += 3;
		}
		Bodily.fed(d);
		Life.save(baby, d);
		baby.level().playSound(null, baby.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
		if (feeder instanceof Player p && stack != null) {
			useUp(p, InteractionHand.MAIN_HAND, stack);
		}
	}

	private static void useUp(Player player, InteractionHand hand, ItemStack stack) {
		if (player.isCreative()) {
			return;
		}
		stack.shrink(1);
		ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
		if (!player.getInventory().add(empty) && player instanceof ServerPlayer sp) {
			sp.spawnAtLocation((ServerLevel) sp.level(), empty);
		}
	}
}
