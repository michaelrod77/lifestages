package dev.lifestages;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lifestages.life.Family;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.LifeManager;
import dev.lifestages.life.Moms;
import dev.lifestages.life.Stage;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /lifestages for operators, /family for everyone. */
public final class LifeCommands {
	private LifeCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("lifestages")
				.then(Commands.literal("info")
						.executes(c -> info(c, c.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("stage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.players())
								.then(Commands.argument("stage", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												java.util.Arrays.stream(Stage.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), b))
										.executes(LifeCommands::setStage))))
				.then(Commands.literal("age").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.players())
								.then(Commands.literal("add")
										.then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0))
												.executes(LifeCommands::addAge)))))
				.then(Commands.literal("goals").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.players())
								.then(Commands.literal("complete").executes(LifeCommands::completeGoals))))
				.then(Commands.literal("mom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("summon").then(Commands.argument("player", EntityArgument.players())
								.executes(c -> {
									var players = EntityArgument.getPlayers(c, "player");
									players.forEach(Moms::assign);
									return players.size();
								})))
						.then(Commands.literal("dismiss").then(Commands.argument("player", EntityArgument.players())
								.executes(c -> {
									var players = EntityArgument.getPlayers(c, "player");
									for (ServerPlayer p : players) {
										Moms.onLeave(p);
										LifeData data = Life.of(p);
										data.momAssigned = false;
										Life.save(p, data);
									}
									return players.size();
								}))))
				.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
						.executes(c -> {
							LifeConfig.load();
							c.getSource().sendSuccess(() -> Component.literal("Life Stages config reloaded."), true);
							return 1;
						})));

		d.register(Commands.literal("family")
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
						.executes(c -> {
							Family.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
							return 1;
						})))
				.then(Commands.literal("accept").executes(c -> {
					Family.answer(c.getSource().getPlayerOrException(), true);
					return 1;
				}))
				.then(Commands.literal("decline").executes(c -> {
					Family.answer(c.getSource().getPlayerOrException(), false);
					return 1;
				}))
				.then(Commands.literal("leave").then(Commands.argument("player", EntityArgument.player())
						.executes(c -> {
							ServerPlayer me = c.getSource().getPlayerOrException(), other = EntityArgument.getPlayer(c, "player");
							Family.leave(me, other);
							c.getSource().sendSuccess(() -> Component.literal("You and " + other.getName().getString() + " are no longer family."), false);
							return 1;
						})))
				.then(Commands.literal("list").executes(c -> {
					ServerPlayer me = c.getSource().getPlayerOrException();
					LifeData data = Life.of(me);
					c.getSource().sendSuccess(() -> Component.literal("Parents: " + names(me, data.parents) + "\nChildren: " + names(me, data.children)), false);
					return 1;
				})));
	}

	private static String names(ServerPlayer me, java.util.List<java.util.UUID> ids) {
		if (ids.isEmpty()) {
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (java.util.UUID id : ids) {
			ServerPlayer p = me.level().getServer().getPlayerList().getPlayer(id);
			sb.append(sb.length() > 0 ? ", " : "").append(p != null ? p.getName().getString() : "(offline)");
		}
		return sb.toString();
	}

	private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		LifeData d = Life.of(p);
		Stage s = d.stage();
		LifeConfig cfg = LifeConfig.get();
		int minLeft = Math.max(0, d.requiredTicks(cfg.lifeLengthMultiplier) - d.stageTicks) / 1200;
		String text = p.getName().getString() + ": " + s.label + ", age " + String.format(Locale.ROOT, "%.1f", Life.age(p))
				+ "\nMilk " + d.milk + "/" + s.milk + "  Food " + d.food + "/" + s.food + "  Naps " + d.naps + "/" + s.naps
				+ "  Nights " + d.nights + "/" + s.nights + "  Time left " + minLeft + " min"
				+ (d.diaperDirty ? "\nDiaper: dirty" : "");
		c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int setStage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		var players = EntityArgument.getPlayers(c, "player");
		String name = StringArgumentType.getString(c, "stage").toUpperCase(Locale.ROOT);
		Stage stage;
		try {
			stage = Stage.valueOf(name);
		} catch (IllegalArgumentException e) {
			c.getSource().sendFailure(Component.literal("Unknown stage: " + name));
			return 0;
		}
		for (ServerPlayer p : players) {
			LifeData d = Life.of(p);
			if (!d.started) {
				d.newLife(stage);
				Life.save(p, d);
			}
			LifeManager.setStage(p, stage);
			c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " is now " + stage.label + "."), true);
		}
		return players.size();
	}

	private static int addAge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		var players = EntityArgument.getPlayers(c, "player");
		double minutes = DoubleArgumentType.getDouble(c, "minutes");
		for (ServerPlayer p : players) {
			LifeData d = Life.of(p);
			d.stageTicks += (int) (minutes * 1200);
			Life.save(p, d);
			c.getSource().sendSuccess(() -> Component.literal("Added " + minutes + " minutes to " + p.getName().getString() + "'s stage time."), true);
		}
		return players.size();
	}

	private static int completeGoals(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		var players = EntityArgument.getPlayers(c, "player");
		for (ServerPlayer p : players) {
			LifeData d = Life.of(p);
			Stage s = d.stage();
			d.milk = Math.max(d.milk, s.milk);
			d.food = Math.max(d.food, s.food);
			d.naps = Math.max(d.naps, s.naps);
			d.nights = Math.max(d.nights, s.nights);
			d.stageTicks = Math.max(d.stageTicks, d.requiredTicks(LifeConfig.get().lifeLengthMultiplier));
			Life.save(p, d);
			c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + "'s goals are complete; they will grow up in a moment."), true);
		}
		return players.size();
	}
}
