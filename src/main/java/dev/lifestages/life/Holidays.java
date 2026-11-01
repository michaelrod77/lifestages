package dev.lifestages.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lifestages.LifeConfig;
import dev.lifestages.LifeStages;
import dev.lifestages.block.ChristmasTreeBlock;
import dev.lifestages.block.EasterEggBlock;
import dev.lifestages.block.PresentBlockEntity;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModItems;
import dev.lifestages.util.Give;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Holidays: every 3 to 5 days, one of Easter, Halloween or Christmas, starting at sunrise (or when everyone wakes
 * up) and lasting until the next sunrise. Everything a holiday brings is cleared away the next morning.
 * <ul>
 * <li>Easter: pastel eggs hidden around each player's home, 5 each (10 when playing alone). Mining one gives a sweet.</li>
 * <li>Halloween: everyone picks a costume, and 10-15 trick-or-treaters gather near home. Right-click one for a
 * treat, or a (harmless) trick.</li>
 * <li>Christmas: a tree in the house (outside if there's no room) and a present under it for each player.</li>
 * </ul>
 * Holiday decorations only appear in the Overworld; a player elsewhere gets theirs when they come back.
 */
public final class Holidays {
	public static final int NONE = 0, EASTER = 1, HALLOWEEN = 2, CHRISTMAS = 3;
	public static final String[] NAMES = {"", "Easter", "Halloween", "Christmas"};
	public static final String TRICK_OR_TREATER = "lifestages_trick_or_treater";
	public static final String[] COSTUMES = {"", "Pumpkin", "Skeleton", "Ghost", "Vampire"};
	private static final SavedDataType<State> TYPE = new SavedDataType<>(LifeStages.id("holidays"), State::new, State.CODEC, null);
	/** Trick-or-treaters each visited once per player: villager id -> players. */
	private static final Map<UUID, Set<UUID>> VISITED = new HashMap<>();

	private Holidays() {
	}

	/** The world's holiday calendar and what today's holiday has put out (so it can be cleared away). */
	public static final class State extends SavedData {
		long nextDay = -1;
		int nextType = EASTER;
		long activeDay = -1;
		int activeType = NONE;
		long announcedDay = -1;
		final List<Long> eggs = new ArrayList<>();
		final List<Long> trees = new ArrayList<>();
		/** Homes that already got their trick-or-treaters or tree. */
		final List<Long> homes = new ArrayList<>();
		final Set<UUID> served = new HashSet<>();
		final Map<UUID, Integer> found = new HashMap<>();

		private record Saved(long nextDay, int nextType, long activeDay, int activeType, long announcedDay, List<Long> eggs, List<Long> trees,
				List<Long> homes, List<UUID> served, Map<String, Integer> found) {
			static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.LONG.fieldOf("next_day").forGetter(Saved::nextDay),
					Codec.INT.fieldOf("next_type").forGetter(Saved::nextType),
					Codec.LONG.fieldOf("active_day").forGetter(Saved::activeDay),
					Codec.INT.fieldOf("active_type").forGetter(Saved::activeType),
					Codec.LONG.optionalFieldOf("announced_day", -1L).forGetter(Saved::announcedDay),
					Codec.LONG.listOf().optionalFieldOf("eggs", List.of()).forGetter(Saved::eggs),
					Codec.LONG.listOf().optionalFieldOf("trees", List.of()).forGetter(Saved::trees),
					Codec.LONG.listOf().optionalFieldOf("homes", List.of()).forGetter(Saved::homes),
					UUIDUtil.CODEC.listOf().optionalFieldOf("served", List.of()).forGetter(Saved::served),
					Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("found", Map.of()).forGetter(Saved::found)
			).apply(i, Saved::new));
		}

		static final Codec<State> CODEC = Saved.CODEC.xmap(s -> {
			State st = new State();
			st.nextDay = s.nextDay;
			st.nextType = s.nextType;
			st.activeDay = s.activeDay;
			st.activeType = s.activeType;
			st.announcedDay = s.announcedDay;
			st.eggs.addAll(s.eggs);
			st.trees.addAll(s.trees);
			st.homes.addAll(s.homes);
			st.served.addAll(s.served);
			s.found.forEach((k, v) -> st.found.put(UUID.fromString(k), v));
			return st;
		}, st -> {
			Map<String, Integer> found = new HashMap<>();
			st.found.forEach((k, v) -> found.put(k.toString(), v));
			return new Saved(st.nextDay, st.nextType, st.activeDay, st.activeType, st.announcedDay, List.copyOf(st.eggs),
					List.copyOf(st.trees), List.copyOf(st.homes), List.copyOf(st.served), found);
		});

		public int activeType() {
			return activeType;
		}

		public long nextDay() {
			return nextDay;
		}

		public int nextType() {
			return nextType;
		}
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		VISITED.clear();
	}

	public static long day(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getDefaultClockTime(), 24000L);
	}

	/** Today's holiday, or NONE. */
	public static int today(MinecraftServer server) {
		return state(server).activeType;
	}

	// ---- the calendar ----

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		State st = state(server);
		LifeConfig cfg = LifeConfig.get();
		long day = day(server);
		RandomSource r = server.overworld().getRandom();
		if (!cfg.holidaysEnabled) {
			if (st.activeType != NONE) {
				end(server, st);
			}
			return;
		}
		if (st.nextDay < 0 || st.nextDay > day + cfg.holidayMaxDays) {
			// first run, or the clock was set back: the next holiday is a few days from today
			st.nextDay = day + gap(r, cfg);
			st.nextType = pick(r, NONE);
			st.setDirty();
		}
		if (st.activeType != NONE && day != st.activeDay) {
			end(server, st);
		}
		if (st.activeType == NONE && day >= st.nextDay) {
			start(server, st, st.nextType);
		}
		long timeOfDay = Math.floorMod(server.overworld().getDefaultClockTime(), 24000L);
		if (st.activeType == NONE && day == st.nextDay - 1 && timeOfDay >= 12000 && st.announcedDay != day) {
			st.announcedDay = day;
			st.setDirty();
			broadcast(server, Component.literal("Tomorrow is " + NAMES[st.nextType] + "!").withStyle(ChatFormatting.GOLD));
		}
		if (st.activeType == NONE) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!st.served.contains(p.getUUID()) && ready(p)) {
				serve(p, st);
			}
			if (server.getTickCount() % 40 == 0) {
				Net.holidayInfo(p, st.activeType, hudLine(p, st));
			}
		}
		if (st.activeType == HALLOWEEN && server.getTickCount() % 100 == 0) {
			keepTrickOrTreatersNear(server.overworld(), st);
		}
	}

	private static int gap(RandomSource r, LifeConfig cfg) {
		int lo = Math.max(1, cfg.holidayMinDays), hi = Math.max(lo, cfg.holidayMaxDays);
		return lo + r.nextInt(hi - lo + 1);
	}

	/** A random holiday, not the same one twice in a row. */
	private static int pick(RandomSource r, int not) {
		int t;
		do {
			t = 1 + r.nextInt(3);
		} while (t == not);
		return t;
	}

	/** Ready for today's holiday: living a life (not choosing, not a ghost) in the Overworld. */
	private static boolean ready(ServerPlayer p) {
		LifeData d = Life.of(p);
		return d.started && d.pendingChoice == 0 && !d.isGhost() && !p.isSpectator() && p.level() == p.level().getServer().overworld();
	}

	/** Starts a holiday today (also /lifestages holiday start). */
	public static void start(MinecraftServer server, State st, int type) {
		if (st.activeType != NONE) {
			end(server, st);
		}
		long day = day(server);
		st.activeType = type;
		st.activeDay = day;
		st.eggs.clear();
		st.trees.clear();
		st.homes.clear();
		st.served.clear();
		st.found.clear();
		VISITED.clear();
		RandomSource r = server.overworld().getRandom();
		st.nextDay = day + gap(r, LifeConfig.get());
		st.nextType = pick(r, type);
		st.setDirty();
		String sub = switch (type) {
			case EASTER -> "Eggs are hidden around home. Go find them!";
			case HALLOWEEN -> "Pick a costume. Trick-or-treaters are about!";
			default -> "There's a tree, and a present for everyone!";
		};
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String hello = (type == CHRISTMAS ? "Merry " : "Happy ") + NAMES[type] + "!";
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(hello).withStyle(ChatFormatting.GOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
			p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
		}
		LifeStages.LOGGER.info("Holiday: {} on day {}", NAMES[type], day);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (ready(p)) {
				serve(p, st);
			}
		}
	}

	/** Clears away today's holiday: eggs, trick-or-treaters, the tree and costumes. Unopened presents stay. */
	public static void end(MinecraftServer server, State st) {
		ServerLevel level = server.overworld();
		for (long at : st.eggs) {
			BlockPos pos = BlockPos.of(at);
			if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.EASTER_EGG)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (long at : st.trees) {
			BlockPos pos = BlockPos.of(at);
			if (level.isLoaded(pos)) {
				ChristmasTreeBlock.remove(level, pos);
			}
		}
		for (ServerLevel l : server.getAllLevels()) {
			for (Entity e : l.getEntities(EntityTypes.VILLAGER, v -> v.entityTags().contains(TRICK_OR_TREATER))) {
				e.discard();
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			LifeData d = Life.of(p);
			if (d.costume != 0) {
				d.costume = 0;
				Life.save(p, d);
			}
			Net.holidayInfo(p, NONE, "");
		}
		if (st.activeType != NONE) {
			broadcast(server, Component.literal(NAMES[st.activeType] + " is over. The next holiday is in a few days.").withStyle(ChatFormatting.GRAY));
		}
		st.activeType = NONE;
		st.activeDay = -1;
		st.eggs.clear();
		st.trees.clear();
		st.homes.clear();
		st.served.clear();
		st.found.clear();
		VISITED.clear();
		st.setDirty();
	}

	/** Gives one player their part of today's holiday. */
	private static void serve(ServerPlayer p, State st) {
		st.served.add(p.getUUID());
		st.setDirty();
		ServerLevel level = (ServerLevel) p.level();
		BlockPos home = homeOf(p);
		switch (st.activeType) {
			case EASTER -> {
				int n = level.getServer().getPlayerList().getPlayerCount() <= 1 ? 10 : 5;
				int hid = hideEggs(level, home, n, st);
				p.sendSystemMessage(Component.literal("Happy Easter! " + hid + " eggs are hidden around home. Mine them to see what's inside.")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			case HALLOWEEN -> {
				if (nearest(st.homes, home, 32) == null) {
					int n = trickOrTreaters(level, home, 10 + level.getRandom().nextInt(6));
					st.homes.add(home.asLong());
					LifeStages.LOGGER.info("Holiday: {} trick-or-treaters near {}", n, home);
				}
				if (!Life.stage(p).isPet()) {
					Net.openCostumes(p);
				}
				p.sendSystemMessage(Component.literal("Happy Halloween! Trick-or-treaters are gathering near home. Right-click one: "
						+ "you'll get a treat... or a trick!").withStyle(ChatFormatting.GOLD));
			}
			case CHRISTMAS -> {
				BlockPos tree = nearest(st.trees, home, 32);
				if (tree == null) {
					tree = placeTree(level, p, home);
					if (tree != null) {
						st.trees.add(tree.asLong());
						st.homes.add(home.asLong());
					}
				}
				boolean underTree = tree != null && placeGift(level, tree, p);
				p.sendSystemMessage(Component.literal(underTree ? "Merry Christmas! There's a present with your name on it under the tree."
						: "Merry Christmas! A present appeared in your inventory.").withStyle(ChatFormatting.GREEN));
			}
			default -> {
			}
		}
	}

	private static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(message);
		}
	}

	/** One line for the top of the screen while a holiday is on. */
	private static String hudLine(ServerPlayer p, State st) {
		return switch (st.activeType) {
			case EASTER -> "Easter egg hunt · found " + st.found.getOrDefault(p.getUUID(), 0) + " · " + st.eggs.size() + " still hidden";
			case HALLOWEEN -> "Halloween · right-click a Trick-or-Treater";
			case CHRISTMAS -> "Christmas · your present is under the tree";
			default -> "";
		};
	}

	// ---- where home is ----

	/** The middle of the family house, else their bed if it's close, else where they stand. */
	public static BlockPos homeOf(ServerPlayer p) {
		BlockPos house = houseOf(p);
		if (house != null) {
			return house.offset(HouseBuilder.CENTER);
		}
		BlockPos bed = Moms.spawnOf(p);
		return bed.distSqr(p.blockPosition()) < 48 * 48 ? bed : p.blockPosition();
	}

	/** The origin of the family cottage the NPC Mom built (theirs, or an online family member's) if it's nearby, or null. */
	private static BlockPos houseOf(ServerPlayer p) {
		LifeData d = Life.of(p);
		BlockPos house = d.houseOrigin();
		if (house == null) {
			List<UUID> family = new ArrayList<>(d.parents);
			family.addAll(d.children);
			for (ServerPlayer f : Family.online(p.level().getServer(), family)) {
				if (Life.of(f).houseOrigin() != null) {
					house = Life.of(f).houseOrigin();
					break;
				}
			}
		}
		return house != null && house.distSqr(p.blockPosition()) < 160 * 160 ? house : null;
	}

	private static BlockPos nearest(List<Long> spots, BlockPos to, int within) {
		BlockPos best = null;
		for (long at : spots) {
			BlockPos pos = BlockPos.of(at);
			if (pos.distSqr(to) < within * within && (best == null || pos.distSqr(to) < best.distSqr(to))) {
				best = pos;
			}
		}
		return best;
	}

	/** A spot on the ground near the center with `height` blocks of air (no water), between rMin and rMax away. */
	private static BlockPos groundSpot(ServerLevel level, BlockPos center, int rMin, int rMax, int height, RandomSource r) {
		for (int attempt = 0; attempt < 40; attempt++) {
			int dx = r.nextInt(2 * rMax + 1) - rMax, dz = r.nextInt(2 * rMax + 1) - rMax;
			if (Math.max(Math.abs(dx), Math.abs(dz)) < rMin) {
				continue;
			}
			for (int dy = 3; dy >= -5; dy--) {
				BlockPos pos = center.offset(dx, dy, dz);
				if (!level.isLoaded(pos)) {
					break;
				}
				if (free(level, pos, height)) {
					return pos;
				}
			}
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos pos, int height) {
		for (int h = 0; h < height; h++) {
			BlockPos p = pos.above(h);
			if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty() || level.getBlockState(p).getBlock() instanceof dev.lifestages.block.PresentBlock) {
				return false;
			}
		}
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	// ---- Easter ----

	private static int hideEggs(ServerLevel level, BlockPos home, int n, State st) {
		RandomSource r = level.getRandom();
		int hid = 0;
		for (int i = 0; i < n * 3 && hid < n; i++) {
			BlockPos pos = groundSpot(level, home, 2, 14, 1, r);
			if (pos == null || st.eggs.contains(pos.asLong())) {
				continue;
			}
			level.setBlock(pos, ModBlocks.EASTER_EGG.defaultBlockState().setValue(EasterEggBlock.DESIGN, r.nextInt(EasterEggBlock.DESIGNS)), 3);
			st.eggs.add(pos.asLong());
			hid++;
		}
		st.setDirty();
		return hid;
	}

	/** An egg was mined: a sweet goes straight into the finder's inventory. */
	public static void eggFound(ServerPlayer p, BlockPos pos) {
		State st = state(p.level().getServer());
		st.eggs.remove(pos.asLong());
		int found = st.found.merge(p.getUUID(), 1, Integer::sum);
		st.setDirty();
		RandomSource r = p.getRandom();
		int roll = r.nextInt(10);
		ItemStack sweet = roll < 4 ? new ItemStack(ModItems.CHOCOLATE_BUNNY) : roll < 9 ? new ItemStack(Items.COOKIE, 2 + r.nextInt(3)) : new ItemStack(Items.CAKE);
		String what = (sweet.getCount() > 1 ? sweet.getCount() + " " : "") + sweet.getHoverName().getString(); // before Give empties the stack
		Give.item(p, sweet);
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.sendParticles(dev.lifestages.registry.ModParticles.SPARKLE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
		p.sendSystemMessage(Component.literal("You found an Easter egg! Inside: " + what + ". (" + found + " found)").withStyle(ChatFormatting.LIGHT_PURPLE));
		if (found >= 5) {
			ModAdvancements.award(p, "egg_hunter");
		}
		Net.holidayInfo(p, st.activeType, hudLine(p, st));
	}

	// ---- Halloween ----

	private static int trickOrTreaters(ServerLevel level, BlockPos home, int n) {
		RandomSource r = level.getRandom();
		ItemStack[] masks = {new ItemStack(Items.CARVED_PUMPKIN), new ItemStack(Items.JACK_O_LANTERN), new ItemStack(Items.SKELETON_SKULL),
				new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.CREEPER_HEAD), new ItemStack(Items.WITHER_SKELETON_SKULL)};
		int made = 0;
		for (int i = 0; i < n; i++) {
			BlockPos pos = groundSpot(level, home, 4, 12, 2, r);
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (pos == null || v == null) {
				continue;
			}
			v.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360f, 0);
			if (r.nextInt(5) < 3) {
				v.setAge(-24000); // most trick-or-treaters are kids
			}
			v.setItemSlot(EquipmentSlot.HEAD, masks[r.nextInt(masks.length)].copy());
			v.setDropChance(EquipmentSlot.HEAD, 0.0f);
			v.setCustomName(Component.literal("Trick-or-Treater"));
			v.addTag(TRICK_OR_TREATER);
			v.setPersistenceRequired();
			v.setPermanentlyInvulnerable(true);
			v.setHomeTo(home, 12);
			level.addFreshEntity(v);
			made++;
		}
		return made;
	}

	private static void keepTrickOrTreatersNear(ServerLevel level, State st) {
		for (Villager v : level.getEntities(EntityTypes.VILLAGER, e -> e.entityTags().contains(TRICK_OR_TREATER))) {
			BlockPos home = nearest(st.homes, v.blockPosition(), 256);
			if (home == null) {
				continue;
			}
			double d = v.blockPosition().distSqr(home);
			if (d > 40 * 40) {
				BlockPos back = groundSpot(level, home, 3, 8, 2, level.getRandom());
				if (back != null) {
					v.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
				}
			} else if (d > 18 * 18) {
				v.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
			}
		}
	}

	/** Trick-or-treaters are gone once Halloween is over, even ones in chunks that weren't loaded at the time. */
	public static void onEntityLoad(Entity e, ServerLevel level) {
		if (e instanceof Villager && e.entityTags().contains(TRICK_OR_TREATER) && state(level.getServer()).activeType != HALLOWEEN) {
			e.discard();
		}
	}

	/** UseEntityCallback on a trick-or-treater: a treat, or a short harmless trick. Once per villager per player. */
	public static InteractionResult trickOrTreat(ServerPlayer p, Villager v) {
		Set<UUID> seen = VISITED.computeIfAbsent(v.getUUID(), k -> new HashSet<>());
		if (!seen.add(p.getUUID())) {
			p.sendOverlayMessage(Component.literal("You've already visited this one. Try another Trick-or-Treater!").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = (ServerLevel) p.level();
		RandomSource r = p.getRandom();
		if (r.nextInt(100) < 65) {
			ItemStack treat = Life.stage(p).isPet() ? new ItemStack(Items.COD) : switch (r.nextInt(6)) {
				case 0 -> new ItemStack(ModItems.CHOCOLATE_BUNNY);
				case 1 -> new ItemStack(Items.PUMPKIN_PIE);
				case 2 -> new ItemStack(Items.HONEY_BOTTLE);
				default -> new ItemStack(Items.COOKIE, 2 + r.nextInt(2));
			};
			String what = (treat.getCount() > 1 ? treat.getCount() + " " : "a ") + treat.getHoverName().getString();
			Give.item(p, treat);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, v.isBaby() ? 1.5f : 1.0f);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getEyeY() + 0.3, v.getZ(), 6, 0.3, 0.2, 0.3, 0);
			p.sendSystemMessage(Component.literal("TREAT! The Trick-or-Treater gives you " + what + ".").withStyle(ChatFormatting.GOLD));
		} else {
			record Trick(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int seconds, int amp, String text) {
			}
			Trick[] tricks = {
					new Trick(MobEffects.SLOWNESS, 15, 1, "Your feet feel like jelly."),
					new Trick(MobEffects.NAUSEA, 10, 0, "Whoa... everything's spinning."),
					new Trick(MobEffects.BLINDNESS, 4, 0, "Boo! The lights go out."),
					new Trick(MobEffects.GLOWING, 30, 0, "You're glowing like a jack-o'-lantern."),
					new Trick(MobEffects.MINING_FATIGUE, 20, 0, "Your arms feel all floppy."),
					new Trick(MobEffects.WEAKNESS, 20, 0, "You feel as weak as a wet noodle.")};
			Trick t = tricks[r.nextInt(tricks.length)];
			p.addEffect(new MobEffectInstance(t.effect, t.seconds * 20, t.amp));
			level.playSound(null, v.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.2f);
			level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getEyeY(), p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
			p.sendSystemMessage(Component.literal("TRICK! " + t.text).withStyle(ChatFormatting.DARK_PURPLE));
		}
		if (VISITED.values().stream().filter(s -> s.contains(p.getUUID())).count() >= 5) {
			ModAdvancements.award(p, "trick_or_treat");
		}
		return InteractionResult.SUCCESS;
	}

	/** The costume chosen on the Halloween screen (0 for none). */
	public static void chooseCostume(ServerPlayer p, int costume) {
		State st = state(p.level().getServer());
		if (st.activeType != HALLOWEEN || costume < 0 || costume >= COSTUMES.length || Life.stage(p).isPet()) {
			return;
		}
		LifeData d = Life.of(p);
		d.costume = costume;
		d.costumeDay = st.activeDay;
		Life.save(p, d);
		if (costume > 0) {
			p.sendSystemMessage(Component.literal("You're dressed as a " + COSTUMES[costume] + " for Halloween!").withStyle(ChatFormatting.GOLD));
		}
	}

	/** A costume from an earlier Halloween comes off when the player rejoins. */
	public static void onJoin(ServerPlayer p) {
		LifeData d = Life.of(p);
		State st = state(p.level().getServer());
		if (d.costume != 0 && (st.activeType != HALLOWEEN || d.costumeDay != st.activeDay)) {
			d.costume = 0;
			Life.save(p, d);
		}
	}

	// ---- Christmas ----

	/** Spots for the tree inside the family cottage (main room, clear of the furniture), relative to its origin. */
	private static final BlockPos[] TREE_SPOTS = {new BlockPos(2, 1, 3), new BlockPos(6, 1, 4), new BlockPos(2, 1, 2)};

	private static BlockPos placeTree(ServerLevel level, ServerPlayer p, BlockPos home) {
		BlockPos house = houseOf(p);
		BlockPos at = null;
		if (house != null) {
			for (BlockPos spot : TREE_SPOTS) {
				BlockPos pos = house.offset(spot);
				if (level.isLoaded(pos) && free(level, pos, 2)) {
					at = pos;
					break;
				}
			}
		}
		if (at == null) {
			at = groundSpot(level, home, 2, 6, 2, level.getRandom()); // no room inside: just outside, near home
		}
		if (at == null) {
			return null;
		}
		ChristmasTreeBlock.place(level, at, ModBlocks.CHRISTMAS_TREE);
		level.sendParticles(dev.lifestages.registry.ModParticles.SPARKLE, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.05);
		return at;
	}

	/** A present next to the tree for this player, holding something valuable. Into their inventory if there's no room. */
	private static boolean placeGift(ServerLevel level, BlockPos tree, ServerPlayer p) {
		ItemStack gift = christmasGift(p.getRandom());
		BlockPos spot = null;
		for (int r = 1; r <= 2 && spot == null; r++) {
			for (BlockPos pos : BlockPos.betweenClosed(tree.offset(-r, -1, -r), tree.offset(r, 1, r))) {
				if (!pos.equals(tree) && free(level, pos, 1) && !level.getBlockState(pos.below()).is(ModBlocks.CHRISTMAS_TREE)) {
					spot = pos.immutable();
					break;
				}
			}
		}
		if (spot == null) {
			ItemStack present = new ItemStack(ModBlocks.PRESENT_LEGENDARY);
			present.set(net.minecraft.core.component.DataComponents.CONTAINER,
					net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(gift)));
			Give.item(p, present);
			return false;
		}
		level.setBlock(spot, ModBlocks.PRESENT_LEGENDARY.defaultBlockState(), 3);
		if (level.getBlockEntity(spot) instanceof PresentBlockEntity present) {
			present.setItem(0, gift);
			present.giftFor(p.getUUID(), p.getName().getString());
		}
		return true;
	}

	/** Something valuable: diamonds, emeralds, a block of gold or iron (and once in a while a block of diamond). */
	private static ItemStack christmasGift(RandomSource r) {
		int roll = r.nextInt(100);
		if (roll < 25) {
			return new ItemStack(Items.DIAMOND, 3);
		} else if (roll < 45) {
			return new ItemStack(Items.EMERALD, 8);
		} else if (roll < 65) {
			return new ItemStack(Items.GOLD_BLOCK);
		} else if (roll < 85) {
			return new ItemStack(Items.IRON_BLOCK, 2);
		} else if (roll < 95) {
			return new ItemStack(Items.LAPIS_BLOCK, 3);
		}
		return new ItemStack(Items.DIAMOND_BLOCK);
	}
}
