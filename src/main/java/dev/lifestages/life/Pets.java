package dev.lifestages.life;

import dev.lifestages.LifeStages;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.util.Give;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.CatSoundVariant;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.feline.CatVariants;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pet cats: a player who chose to be the family's cat. Kittens drink milk bottles and Cats eat fish, always from
 * someone else. They jump twice as high, dash in short bursts, never take fall damage, see in the dark at night,
 * meow, hiss and purr, and anyone in the family can walk them on a lead.
 */
public final class Pets {
	/** A sprint burst lasts 2.5 seconds, then refills while not sprinting (twice as slowly). */
	public static final int DASH_TICKS = 50;
	public static final String[] NAMES = {"Tabby", "Tuxedo", "Ginger", "Siamese", "British Shorthair", "Calico", "Persian", "Ragdoll",
			"White", "Jellie", "Black"};
	@SuppressWarnings("unchecked")
	public static final ResourceKey<CatVariant>[] VARIANTS = new ResourceKey[] {CatVariants.TABBY, CatVariants.BLACK, CatVariants.RED,
			CatVariants.SIAMESE, CatVariants.BRITISH_SHORTHAIR, CatVariants.CALICO, CatVariants.PERSIAN, CatVariants.RAGDOLL,
			CatVariants.WHITE, CatVariants.JELLIE, CatVariants.ALL_BLACK};
	private static final Identifier DASH = LifeStages.id("pet_dash");
	/** Leads longer than this pull the cat along; much longer and the cat is brought to the walker's side. */
	private static final double LEAD_LENGTH = 4.0, LEAD_SNAP = 14.0;

	private Pets() {
	}

	public static boolean isPet(Player player) {
		LifeData d = Life.of(player);
		return d.started && !d.isGhost() && d.stage().isPet();
	}

	// ---- every tick ----

	/** Sprint bursts, night vision and the lead. Returns true when the life data changed. */
	public static boolean tick(ServerPlayer pet, LifeData d) {
		boolean changed = false;
		if (pet.isSprinting()) {
			if (d.dash > 0) {
				d.dash--;
			}
		} else if (d.dash < DASH_TICKS && pet.tickCount % 2 == 0) {
			d.dash++;
		}
		double boost = pet.isSprinting() && d.dash > 0 ? (d.stage() == Stage.KITTEN ? 0.8 : 1.2) : 0.0;
		AttributeInstance speed = pet.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			AttributeModifier current = speed.getModifier(DASH);
			if (boost == 0.0 && current != null) {
				speed.removeModifier(DASH);
			} else if (boost != 0.0 && current == null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(DASH, boost, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		// Cats see in the dark: night vision from dusk to dawn. Ours is "ambient", which tells it apart from a potion.
		MobEffectInstance nv = pet.getEffect(MobEffects.NIGHT_VISION);
		if (pet.level().isDarkOutside()) {
			if (nv == null || nv.isAmbient() && nv.getDuration() < 260) {
				pet.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
			}
		} else if (nv != null && nv.isAmbient() && nv.getDuration() <= 400) {
			pet.removeEffect(MobEffects.NIGHT_VISION);
		}
		if (d.leashHolder != null) {
			changed |= tickLead(pet, d);
		}
		return changed;
	}

	/** Called when a pet's life ends or they stop being a pet: no lead, no dash, no cat eyes. */
	public static void clear(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(DASH);
		}
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isAmbient()) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	// ---- family ----

	/** Anyone in the pet's family: an owner, or an owner's parent or child. A cat nobody owns lets anyone walk them. */
	public static boolean family(Player person, ServerPlayer pet) {
		LifeData pd = Life.of(pet);
		if (pd.parents.isEmpty() || pd.parents.contains(person.getUUID())) {
			return true;
		}
		LifeData hd = Life.of(person);
		for (UUID owner : pd.parents) {
			if (hd.parents.contains(owner) || hd.children.contains(owner)) {
				return true;
			}
		}
		return false;
	}

	/** A grown-up takes in a pet cat (no starter chest, and pets don't count toward the child limit). */
	public static void adopt(ServerPlayer owner, ServerPlayer pet) {
		LifeData od = Life.of(owner), pd = Life.of(pet);
		if (!od.children.contains(pet.getUUID())) {
			od.children.add(pet.getUUID());
		}
		if (!pd.parents.contains(owner.getUUID())) {
			pd.parents.add(owner.getUUID());
		}
		Life.save(owner, od);
		Life.save(pet, pd);
		String name = pet.getName().getString();
		owner.sendSystemMessage(Component.literal(name + " is now your family's pet cat!").withStyle(ChatFormatting.LIGHT_PURPLE));
		owner.sendSystemMessage(Component.literal("Looking after " + name + ": right-click them holding a milk bottle while they're a Kitten, "
				+ "or any fish once they're a Cat. Right-click them holding a lead to take them for a walk (again to unclip it). "
				+ "When they meow for you, an arrow points to them.").withStyle(ChatFormatting.GRAY));
		pet.sendSystemMessage(Component.literal(owner.getName().getString() + " is your family now!").withStyle(ChatFormatting.LIGHT_PURPLE));
		ModAdvancements.award(owner, "cat_person");
	}

	// ---- feeding ----

	/** A Kitten drinks milk bottles; a Cat (or Old Cat) eats any fish. Anyone may feed a pet. */
	public static InteractionResult feed(ServerPlayer feeder, ServerPlayer pet, ItemStack stack) {
		LifeData d = Life.of(pet);
		boolean kitten = d.stage() == Stage.KITTEN;
		boolean milk = stack.is(dev.lifestages.registry.ModItems.MILK_BOTTLE);
		boolean fish = stack.is(ItemTags.FISHES);
		if (!milk && !fish) {
			return InteractionResult.PASS;
		}
		String name = pet.getName().getString();
		if (kitten && fish) {
			feeder.sendOverlayMessage(Component.literal("Kittens only drink milk. Try a milk bottle.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (!kitten && milk) {
			feeder.sendOverlayMessage(Component.literal(name + " is a grown cat now: fish, please!").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (!pet.getFoodData().needsFood() && (!kitten || d.milk >= d.stage().milk)) {
			feeder.sendOverlayMessage(Component.literal(name + " is full.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (milk) {
			Feeding.feedBottle(feeder, pet, stack);
		} else {
			eatFish(pet, stack);
			if (!feeder.isCreative()) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** A cat eats a fish: hunger, food-goal credit, a happy sound and hearts. Used by players and Moms. */
	public static void eatFish(ServerPlayer pet, ItemStack fish) {
		LifeData d = Life.of(pet);
		var food = fish.get(net.minecraft.core.component.DataComponents.FOOD);
		int nutrition = food == null ? 2 : Math.max(2, food.nutrition());
		pet.getFoodData().eat(nutrition, food == null ? 0.2f : food.saturation());
		d.food += nutrition;
		Life.save(pet, d);
		ServerLevel level = (ServerLevel) pet.level();
		level.playSound(null, pet.blockPosition(), sound(pet, CatSoundVariant.CatSoundSet::eatSound), SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getEyeY() + 0.3, pet.getZ(), 2, 0.2, 0.1, 0.2, 0);
	}

	// ---- the lead ----

	/**
	 * UseEntityCallback. Someone in the family right-clicks the cat holding a lead to clip it on (and right-clicks
	 * again to unclip it), or the cat right-clicks them while holding its own lead.
	 */
	public static InteractionResult onUseEntity(Player user, Level level, InteractionHand hand, Entity target) {
		if (!(target instanceof Player other) || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = user.getItemInHand(hand);
		Player pet = isPet(other) ? other : isPet(user) && held.is(Items.LEAD) ? user : null;
		if (pet == null) {
			return InteractionResult.PASS;
		}
		Player walker = pet == other ? user : other;
		LifeData d = Life.of(pet);
		boolean unclip = walker.getUUID().equals(d.leashHolder) && pet == other;
		if (!unclip && !held.is(Items.LEAD)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerPlayer sPet = (ServerPlayer) pet, sWalker = (ServerPlayer) walker, sUser = (ServerPlayer) user;
		String petName = pet.getName().getString();
		if (unclip) {
			unclip(sPet, d, true);
			return InteractionResult.SUCCESS;
		}
		if (d.leashHolder != null) {
			ServerPlayer holder = level.getServer().getPlayerList().getPlayer(d.leashHolder);
			sUser.sendOverlayMessage(Component.literal(petName + " is already out for a walk" + (holder == null ? "." : " with " + holder.getName().getString() + "."))
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (!family(walker, sPet)) {
			sUser.sendOverlayMessage(Component.literal("Only " + petName + "'s family can take them for a walk.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		d.leashHolder = walker.getUUID();
		d.leashFromPet = pet == user;
		Life.save(pet, d);
		if (!user.isCreative()) {
			held.shrink(1);
		}
		level.playSound(null, pet.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0f, 1.0f);
		sWalker.sendSystemMessage(Component.literal("You're walking " + petName + ". Right-click them again to unclip the lead.").withStyle(ChatFormatting.LIGHT_PURPLE));
		sPet.sendSystemMessage(Component.literal(walker.getName().getString() + " is taking you for a walk!").withStyle(ChatFormatting.LIGHT_PURPLE));
		ModAdvancements.award(sPet, "walkies");
		return InteractionResult.SUCCESS;
	}

	/** Takes the lead off. It goes back to whoever it came from (the cat's own, or the walker's). */
	public static void unclip(ServerPlayer pet, LifeData d, boolean say) {
		UUID holderId = d.leashHolder;
		if (holderId == null) {
			return;
		}
		ServerPlayer holder = pet.level().getServer().getPlayerList().getPlayer(holderId);
		ServerPlayer back = d.leashFromPet || holder == null ? pet : holder;
		d.leashHolder = null;
		d.leashFromPet = false;
		Life.save(pet, d);
		Give.item(back, new ItemStack(Items.LEAD));
		pet.level().playSound(null, pet.blockPosition(), SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0f, 1.0f);
		if (say && holder != null) {
			holder.sendOverlayMessage(Component.literal("Unclipped " + pet.getName().getString() + "'s lead.").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The lead pulls the cat along once it's taut. If the walker leaves, it comes off. */
	private static boolean tickLead(ServerPlayer pet, LifeData d) {
		ServerPlayer holder = pet.level().getServer().getPlayerList().getPlayer(d.leashHolder);
		if (holder == null || holder.level() != pet.level() || !holder.isAlive() || holder.isSpectator() || pet.isPassenger()) {
			unclip(pet, d, false);
			return true;
		}
		double dist = pet.distanceTo(holder);
		if (dist > LEAD_SNAP) {
			Vec3 behind = holder.position().subtract(holder.getLookAngle().multiply(1.5, 0, 1.5));
			pet.teleportTo(behind.x, holder.getY(), behind.z);
		} else if (dist > LEAD_LENGTH) {
			Vec3 dir = holder.position().subtract(pet.position()).normalize();
			double pull = Math.min(0.45, (dist - LEAD_LENGTH) * 0.1);
			Vec3 v = pet.getDeltaMovement();
			pet.setDeltaMovement(dir.x * pull, dir.y > 0.3 && pet.onGround() ? 0.42 : v.y, dir.z * pull);
			pet.needsSync = true;
		}
		return false;
	}

	// ---- emotes ----

	/** The emote wheel for pets: meow (calls the family, like a baby's cry), hiss, and purr. */
	public static void emote(ServerPlayer pet, Emotes.Kind kind) {
		switch (kind) {
			case CRY -> meow(pet);
			case FART -> hiss(pet);
			case CUTE -> purr(pet);
			default -> {
			}
		}
	}

	public static void meow(ServerPlayer pet) {
		ServerLevel level = (ServerLevel) pet.level();
		boolean hungry = pet.getFoodData().getFoodLevel() < 14;
		SoundEvent meow = sound(pet, hungry ? CatSoundVariant.CatSoundSet::begForFoodSound : CatSoundVariant.CatSoundSet::ambientSound);
		level.playSound(null, pet.blockPosition(), meow, SoundSource.PLAYERS, 1.5f, 1.0f);
		level.sendParticles(ParticleTypes.NOTE, pet.getX(), pet.getEyeY() + 0.3, pet.getZ(), 1, 0.1, 0.1, 0.1, 0);
		for (ServerPlayer owner : Family.online(level.getServer(), Life.of(pet).parents)) {
			if (owner.level() == level && owner.distanceToSqr(pet) < 128 * 128) {
				Give.privateSound(owner, meow, 0.8f, 1.0f);
				Net.cryArrow(owner, pet);
			}
		}
		Moms.comfort(pet);
	}

	public static void hiss(ServerPlayer pet) {
		ServerLevel level = (ServerLevel) pet.level();
		level.playSound(null, pet.blockPosition(), sound(pet, CatSoundVariant.CatSoundSet::hissSound), SoundSource.PLAYERS, 1.5f, 1.0f);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pet.getX(), pet.getEyeY() + 0.2, pet.getZ(), 3, 0.2, 0.1, 0.2, 0);
		// Monsters back off from an angry cat, and creepers give up (they're scared of cats anyway).
		for (Monster m : level.getEntitiesOfClass(Monster.class, pet.getBoundingBox().inflate(6))) {
			Vec3 away = m.position().subtract(pet.position()).normalize().scale(0.8);
			m.push(away.x, 0.2, away.z);
			m.setTarget(null);
			if (m instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
		}
	}

	public static void purr(ServerPlayer pet) {
		ServerLevel level = (ServerLevel) pet.level();
		level.playSound(null, pet.blockPosition(), sound(pet, CatSoundVariant.CatSoundSet::purrSound), SoundSource.PLAYERS, 1.2f, 1.0f);
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getEyeY() + 0.2, pet.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
		// Purring is soothing: everyone close by heals a little.
		for (Player other : level.getEntitiesOfClass(Player.class, pet.getBoundingBox().inflate(6))) {
			if (other != pet) {
				other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 5 * 20));
			}
		}
	}

	/** A cat's chat comes out as meows (mews for a kitten): one per word, up to six. */
	public static String meowify(Stage s, String text, net.minecraft.util.RandomSource random) {
		String[] words = text.trim().split("\\s+");
		String[] sounds = s == Stage.KITTEN ? new String[] {"mew", "mew", "mrrp", "mew"} : new String[] {"meow", "mrrow", "meow", "mrrp", "meeow"};
		StringBuilder out = new StringBuilder();
		int n = Math.max(1, Math.min(6, words.length));
		for (int i = 0; i < n; i++) {
			out.append(i == 0 ? "" : " ").append(sounds[random.nextInt(sounds.length)]);
		}
		out.setCharAt(0, Character.toUpperCase(out.charAt(0)));
		return out + (text.trim().endsWith("?") ? "?" : "!");
	}

	/** A cat sound for this pet: the kitten version while a Kitten. */
	public static SoundEvent sound(Player pet, Function<CatSoundVariant.CatSoundSet, Holder<SoundEvent>> which) {
		CatSoundVariant v = SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC);
		CatSoundVariant.CatSoundSet set = Life.stage(pet) == Stage.KITTEN ? v.babySounds() : v.adultSounds();
		return which.apply(set).value();
	}
}
