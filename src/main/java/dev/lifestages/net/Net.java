package dev.lifestages.net;

import dev.lifestages.LifeConfig;
import dev.lifestages.life.Carrying;
import dev.lifestages.life.Choices;
import dev.lifestages.life.Emotes;
import dev.lifestages.life.Family;
import dev.lifestages.life.Ghosts;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.net.Payloads.Act;
import dev.lifestages.net.Payloads.Action;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class Net {
	private Net() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenChoice.TYPE, Payloads.OpenChoice.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.CryArrow.TYPE, Payloads.CryArrow.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.PairPrompt.TYPE, Payloads.PairPrompt.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.ConfigSync.TYPE, Payloads.ConfigSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.Choose.TYPE, Payloads.Choose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.Emote.TYPE, Payloads.Emote.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Payloads.Choose.TYPE, (p, ctx) ->
				Choices.choose(ctx.player(), p.option(), p.parent().orElse(null), p.momLook()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Emote.TYPE, (p, ctx) -> {
			Emotes.Kind[] kinds = Emotes.Kind.values();
			if (p.kind() >= 0 && p.kind() < kinds.length) {
				Emotes.request(ctx.player(), kinds[p.kind()]);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (p, ctx) -> act(ctx.player(), p.action(), p.target()));
	}

	private static void act(ServerPlayer player, int action, int target) {
		LifeData d = Life.of(player);
		switch (action) {
			case Action.PAIR -> {
				Entity e = player.level().getEntity(target);
				if (e instanceof ServerPlayer other && other.distanceToSqr(player) < 8 * 8) {
					Family.invite(player, other);
				}
			}
			case Action.PAIR_ACCEPT -> Family.answer(player, true);
			case Action.PAIR_DECLINE -> Family.answer(player, false);
			case Action.WRIGGLE -> Carrying.wriggle(player);
			case Action.PUT_DOWN -> {
				if (player.getFirstPassenger() != null) {
					Carrying.drop(player);
				}
			}
			case Action.BOO -> Ghosts.boo(player);
			case Action.FLICKER -> Ghosts.flicker(player);
			case Action.REBORN -> {
				if (d.isGhost()) {
					Ghosts.reborn(player, d);
				}
			}
			case Action.ALLOW_CARRY_ON, Action.ALLOW_CARRY_OFF -> {
				if (!d.started || !d.stage().atMost(dev.lifestages.life.Stage.CHILD)) {
					break;
				}
				d.lookingForParent = action == Action.ALLOW_CARRY_ON;
				d.allowCarry = true;
				Life.save(player, d);
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(d.lookingForParent
						? "The next teenager or grown-up who picks you up will be your parent. Mom steps back while they're with you."
						: "You're no longer looking for a new parent.").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			case Action.SEARCH_PARENTS -> {
				if (!d.started || d.pendingChoice != 0) {
					openChoice(player, d.started ? 2 : 1, d.lastWasGhost); // refreshes the parent list on the open screen
				}
			}
			case Action.LOOKING_ON, Action.LOOKING_OFF -> {
				d.lookingForBaby = action == Action.LOOKING_ON;
				Life.save(player, d);
			}
			default -> {
			}
		}
	}

	public static void openChoice(ServerPlayer player, int mode, boolean ghostLocked) {
		List<Payloads.ParentOption> parents = new ArrayList<>();
		for (Player p : Choices.lookingForBaby(player)) {
			parents.add(new Payloads.ParentOption(p.getUUID(), p.getName().getString()));
		}
		ServerPlayNetworking.send(player, new Payloads.OpenChoice(mode, ghostLocked, parents));
	}

	public static void cryArrow(ServerPlayer parent, net.minecraft.world.entity.Entity baby) {
		ServerPlayNetworking.send(parent, new Payloads.CryArrow(baby.getId()));
	}

	public static void pairPrompt(ServerPlayer to, ServerPlayer from, boolean fromIsParent) {
		ServerPlayNetworking.send(to, new Payloads.PairPrompt(from.getName().getString(), fromIsParent));
	}

	public static void syncConfig(ServerPlayer player) {
		LifeConfig c = LifeConfig.get();
		ServerPlayNetworking.send(player, new Payloads.ConfigSync(c.lifeLengthMultiplier, c.carryAgeLimit));
	}
}
