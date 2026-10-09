package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Parent and child links, pairing requests, and family alerts. */
public final class Family {
	private record Invite(UUID from, UUID to, long expires) {
	}

	private static final Map<UUID, Invite> INVITES = new HashMap<>();

	private Family() {
	}

	/** Forget everything tied to the previous world (called when a server starts or stops). */
	public static void reset() {
		INVITES.clear();
	}

	public static boolean related(Player a, Player b) {
		LifeData da = Life.of(a);
		return da.parents.contains(b.getUUID()) || da.children.contains(b.getUUID());
	}

	public static boolean isParentOf(Player parent, Player child) {
		return Life.of(child).parents.contains(parent.getUUID());
	}

	public static List<ServerPlayer> online(MinecraftServer server, List<UUID> ids) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : ids) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	/** Online parents old enough to have picked this child up: teenager and older, including Mom stepping back. */
	public static List<ServerPlayer> caregivers(ServerPlayer child) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : online(child.level().getServer(), Life.of(child).parents)) {
			LifeData pd = Life.of(p);
			if (pd.started && !pd.isGhost() && pd.stage().canCarry()) {
				out.add(p);
			}
		}
		return out;
	}

	public static void notifyFamily(ServerPlayer player, Component message) {
		LifeData d = Life.of(player);
		List<UUID> all = new ArrayList<>(d.parents);
		all.addAll(d.children);
		for (ServerPlayer p : online(player.level().getServer(), all)) {
			p.sendSystemMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	public static void cryAlert(ServerPlayer baby) {
		for (ServerPlayer parent : online(baby.level().getServer(), Life.of(baby).parents)) {
			if (parent.level() == baby.level() && parent.distanceToSqr(baby) < 128 * 128) {
				dev.lifestages.util.Give.privateSound(parent, ModSounds.CRY, 0.6f, 1.2f);
				Net.cryArrow(parent, baby);
			}
		}
	}

	// ---- pairing ----

	/** P key or /family invite: ask to become family. Either side can ask. */
	public static void invite(ServerPlayer from, ServerPlayer to) {
		String problem = pairingProblem(from, to);
		if (problem != null) {
			from.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
			return;
		}
		INVITES.put(to.getUUID(), new Invite(from.getUUID(), to.getUUID(), from.level().getGameTime() + 30 * 20));
		boolean fromIsParent = Life.stage(from).canParent();
		String ask = fromIsParent ? " wants to be your parent. " : " wants you to be their parent. ";
		MutableComponent msg = Component.literal(from.getName().getString() + ask).withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal("[Accept]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/family accept"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Decline]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/family decline"))));
		to.sendSystemMessage(msg);
		Net.pairPrompt(to, from, fromIsParent);
		from.sendSystemMessage(Component.literal("Asked " + to.getName().getString() + ". They have 30 seconds to answer.").withStyle(ChatFormatting.GRAY));
	}

	public static void answer(ServerPlayer player, boolean accept) {
		Invite inv = INVITES.remove(player.getUUID());
		if (inv == null || inv.expires < player.level().getGameTime()) {
			player.sendSystemMessage(Component.literal("There's no family request waiting.").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerPlayer from = player.level().getServer().getPlayerList().getPlayer(inv.from);
		if (from == null) {
			return;
		}
		if (!accept) {
			from.sendSystemMessage(Component.literal(player.getName().getString() + " said no.").withStyle(ChatFormatting.GRAY));
			return;
		}
		String problem = pairingProblem(from, player);
		if (problem != null) {
			player.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
			return;
		}
		ServerPlayer parent = Life.stage(from).canParent() ? from : player;
		ServerPlayer child = parent == from ? player : from;
		link(parent, child);
	}

	/**
	 * A child who is looking for a new parent is adopted by the teenager or grown-up who just picked them up.
	 * One adoption turns the search off. If they already have as many parents as allowed, the oldest link makes room.
	 */
	public static void claimIfLooking(ServerPlayer parent, ServerPlayer child) {
		LifeData cd = Life.of(child);
		if (!cd.lookingForParent || parent == child || !Life.stage(parent).canCarry() || isParentOf(parent, child)) {
			return;
		}
		long young = online(parent.level().getServer(), Life.of(parent).children).stream()
				.filter(c -> Life.stage(c).atMost(Stage.CHILD)).count();
		if (young >= LifeConfig.get().maxChildrenPerParent) {
			parent.sendSystemMessage(Component.literal("You already look after " + LifeConfig.get().maxChildrenPerParent + " children.")
					.withStyle(ChatFormatting.RED));
			return;
		}
		int max = LifeConfig.get().maxParentsPerChild;
		while (Life.of(child).parents.size() >= max && !Life.of(child).parents.isEmpty()) {
			UUID oldId = Life.of(child).parents.get(0);
			ServerPlayer old = child.level().getServer().getPlayerList().getPlayer(oldId);
			if (old != null) {
				leave(child, old);
				old.sendSystemMessage(Component.literal(child.getName().getString() + " found a new parent.").withStyle(ChatFormatting.GRAY));
			} else {
				LifeData fresh = Life.of(child);
				fresh.parents.remove(oldId);
				Life.save(child, fresh);
			}
		}
		LifeData ready = Life.of(child);
		ready.lookingForParent = false;
		Life.save(child, ready);
		link(parent, child);
	}

	public static void link(ServerPlayer parent, ServerPlayer child) {
		LifeData pd = Life.of(parent), cd = Life.of(child);
		boolean newly = !pd.children.contains(child.getUUID());
		if (newly) {
			pd.children.add(child.getUUID());
		}
		if (!cd.parents.contains(parent.getUUID())) {
			cd.parents.add(parent.getUUID());
		}
		Life.save(parent, pd);
		Life.save(child, cd);
		if (newly) {
			StarterChest.give(parent, child.getName().getString());
		}
		parent.sendSystemMessage(Component.literal("You are now " + child.getName().getString() + "'s parent!").withStyle(ChatFormatting.LIGHT_PURPLE));
		parent.sendSystemMessage(Component.literal("Looking after " + child.getName().getString() + ": right-click them holding a milk bottle "
				+ "(or food, past Newborn) to feed them, or a clean diaper to change them. Right-click with an empty hand to put them "
				+ "in your baby carrier (press V to put them down), and right-click a changing table to put them on it. "
				+ "When they cry, an arrow points to them. Look at them for tips.")
				.withStyle(ChatFormatting.GRAY));
		child.sendSystemMessage(Component.literal(parent.getName().getString() + " is now your parent!").withStyle(ChatFormatting.LIGHT_PURPLE));
		ModAdvancements.award(parent, "proud_parent");
		ModAdvancements.award(child, "welcome_home");
		for (ServerPlayer grand : online(parent.level().getServer(), pd.parents)) {
			ModAdvancements.award(grand, "generations");
		}
		Moms.parentArrived(child);
	}

	public static void leave(ServerPlayer player, ServerPlayer other) {
		LifeData a = Life.of(player), b = Life.of(other);
		a.parents.remove(other.getUUID());
		a.children.remove(other.getUUID());
		b.parents.remove(player.getUUID());
		b.children.remove(player.getUUID());
		Life.save(player, a);
		Life.save(other, b);
	}

	static String pairingProblem(ServerPlayer a, ServerPlayer b) {
		if (a == b) {
			return "You can't be your own family.";
		}
		ServerPlayer parent = Life.stage(a).canParent() ? a : Life.stage(b).canParent() ? b : null;
		if (parent == null) {
			return "One of you must be a Young Adult to Senior Citizen to be the parent.";
		}
		ServerPlayer child = parent == a ? b : a;
		if (!Life.stage(child).atMost(Stage.CHILD)) {
			return child.getName().getString() + " is too old to be adopted.";
		}
		LifeData cd = Life.of(child), pd = Life.of(parent);
		if (cd.parents.contains(parent.getUUID())) {
			return "You're already family.";
		}
		if (cd.parents.size() >= LifeConfig.get().maxParentsPerChild) {
			return child.getName().getString() + " already has " + cd.parents.size() + " parents.";
		}
		long young = online(parent.level().getServer(), pd.children).stream().filter(c -> Life.stage(c).atMost(Stage.CHILD)).count();
		if (young >= LifeConfig.get().maxChildrenPerParent) {
			return parent.getName().getString() + " already has " + young + " young children.";
		}
		return null;
	}
}
