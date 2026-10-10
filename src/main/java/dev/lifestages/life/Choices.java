package dev.lifestages.life;

import dev.lifestages.LifeConfig;
import dev.lifestages.registry.ModAdvancements;
import dev.lifestages.registry.ModItems;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

/** The first-join and respawn choice: 1 Adult, 2 Baby, 3 Elderly, 4 Ghost. */
public final class Choices {
	public static final int ADULT = 1, BABY = 2, ELDERLY = 3, GHOST = 4;

	private Choices() {
	}

	/** parent: the player chosen as parent for a Baby, or null for "any parent / NPC Mom". */
	public static void choose(ServerPlayer player, int option, UUID parent) {
		choose(player, option, parent, 0);
	}

	/** momLook: which of the six Moms a Baby wants (1-6), or 0 for a surprise. */
	public static void choose(ServerPlayer player, int option, UUID parent, int momLook) {
		LifeData d = Life.of(player);
		if (d.started && d.pendingChoice == 0) {
			return;
		}
		boolean firstJoin = !d.started;
		if (option < ADULT || option > GHOST || (firstJoin && option > BABY) || (option == GHOST && d.lastWasGhost)) {
			return;
		}
		if (option == GHOST) {
			Ghosts.become(player, d);
			Life.save(player, d);
			return;
		}
		Moms.onLeave(player); // the previous life's Mom goes with that life
		boolean wasGhost = player.isSpectator();
		d.lastWasGhost = false;
		Stage start = option == ADULT ? Stage.YOUNG_ADULT : option == BABY ? Stage.NEWBORN : Stage.ELDERLY;
		d.newLife(start);
		if (option == BABY && momLook >= 1 && momLook <= 6) {
			d.momLook = momLook;
		}
		if (option == ADULT) {
			d.lookingForBaby = true; // new players almost always want this; it can be turned off in the Journal
		}
		if (start == Stage.ELDERLY) {
			LifeManager.rollLifespan(player, d);
		}
		Life.save(player, d);
		if (wasGhost || player.gameMode() == GameType.SPECTATOR) {
			player.setGameMode(GameType.SURVIVAL);
			TeleportTransition home = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
			player.teleport(home);
		}
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		LifeManager.applyAttributes(player, d);
		player.setHealth(player.getMaxHealth());
		ModAdvancements.award(player, "root");

		switch (option) {
			case ADULT -> {
				dev.lifestages.util.Give.item(player, new ItemStack(net.minecraft.world.item.Items.BED.white())); // somewhere to sleep from night one
				player.sendSystemMessage(Component.literal("You begin life as a Young Adult, age 18, with a bed to sleep in.").withStyle(ChatFormatting.GOLD));
				player.sendSystemMessage(Component.literal("New babies can choose you as their parent (turn this off in the Journal, J).")
						.withStyle(ChatFormatting.GRAY));
				player.sendSystemMessage(Component.literal("When a baby connects with you, a starter chest appears beside you: "
						+ "a crib, 32 diapers, a rattle, 64 milk bottles, a trash can and a music box. You can look after up to "
						+ LifeConfig.get().maxChildrenPerParent + " babies.").withStyle(ChatFormatting.GRAY));
			}
			case ELDERLY -> {
				player.giveExperienceLevels(5);
				dev.lifestages.util.Give.item(player, new ItemStack(ModItems.WALKING_CANE));
				dev.lifestages.util.Give.item(player, new ItemStack(net.minecraft.world.item.Items.BED.white()));
				player.sendSystemMessage(Component.literal("You begin life as an Elder, age 65, with a little wisdom (and a bed).").withStyle(ChatFormatting.GOLD));
			}
			case BABY -> startBaby(player, d, parent);
			default -> {
			}
		}
	}

	private static void startBaby(ServerPlayer baby, LifeData d, UUID parentId) {
		baby.sendSystemMessage(Component.literal("Welcome to the world! You're a Newborn: you can't walk or talk yet.").withStyle(ChatFormatting.LIGHT_PURPLE));
		baby.sendSystemMessage(Component.literal("You grow by drinking milk and napping. Whoever looks after you feeds you bottles and rocks you to sleep.").withStyle(ChatFormatting.GRAY));
		baby.sendSystemMessage(Component.literal("Press J to see your goals. Hold Z to cry (it calls for help), fart, be cute or poop.").withStyle(ChatFormatting.GRAY));
		ServerPlayer parent = parentId == null ? null : baby.level().getServer().getPlayerList().getPlayer(parentId);
		if (parent != null && Life.stage(parent).canParent() && Life.of(parent).lookingForBaby) {
			Family.link(parent, baby);
			baby.teleport(new TeleportTransition(parent.level(), parent.position().add(parent.getLookAngle().multiply(1, 0, 1).normalize()),
					net.minecraft.world.phys.Vec3.ZERO, baby.getYRot(), baby.getXRot(), TeleportTransition.DO_NOTHING));
			parent.sendSystemMessage(Component.literal("Your baby " + baby.getName().getString() + " has arrived!").withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		ServerPlayer existing = Family.caregivers(baby).stream().findFirst().orElse(null);
		if (existing != null) {
			baby.teleport(new TeleportTransition(existing.level(), existing.position().add(1, 0, 0),
					net.minecraft.world.phys.Vec3.ZERO, baby.getYRot(), baby.getXRot(), TeleportTransition.DO_NOTHING));
			return;
		}
		Moms.assign(baby);
	}

	/** Parents who are online and looking for a baby, for the choice screen's parent list. */
	public static java.util.List<Player> lookingForBaby(ServerPlayer asker) {
		java.util.List<Player> out = new java.util.ArrayList<>();
		for (ServerPlayer p : asker.level().getServer().getPlayerList().getPlayers()) {
			LifeData pd = Life.of(p);
			if (p != asker && pd.started && pd.lookingForBaby && pd.stage().canParent()
					&& pd.children.size() < LifeConfig.get().maxChildrenPerParent) {
				out.add(p);
			}
		}
		return out;
	}
}
