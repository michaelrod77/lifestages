package dev.lifestages.test;

import dev.lifestages.client.screen.ChoiceScreen;
import dev.lifestages.client.screen.JournalScreen;
import dev.lifestages.entity.MomEntity;
import dev.lifestages.life.Life;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Payloads;
import dev.lifestages.registry.ModItems;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Joins a real dedicated server over the network and checks that choices, synced life data, the Mom, emotes,
 * growth, death, Elderly and Ghost respawns all work between a separate client and server.
 */
public class LifeStagesMultiplayerTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("time set noon");
			try (TestDedicatedServerConnection conn = server.connect()) {
				conn.waitForChunksRender();

				// First join over the network: the server opens the choice screen on the client.
				ctx.waitForScreen(ChoiceScreen.class);
				ctx.takeScreenshot("mp_01_first_join");
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(2, Optional.empty())));
				ctx.runOnClient(mc -> mc.setScreenAndShow(null));
				ctx.waitTicks(60);
				check(server.computeOnServer(s -> Life.stage(player(s))) == Stage.NEWBORN, "server: not a Newborn");
				check(ctx.computeOnClient(mc -> Life.stage(mc.player)) == Stage.NEWBORN, "client: life data did not sync");
				check(ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof MomEntity), "client: not held by a Mom");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(10);
				ctx.takeScreenshot("mp_02_newborn_held_by_mom");

				// Emotes travel client -> server; the dirty diaper travels back.
				for (int k = 0; k < 4; k++) {
					final int kind = k;
					ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(kind)));
					ctx.waitTicks(15);
				}
				ctx.waitTicks(20);
				check(server.computeOnServer(s -> Life.of(player(s)).diaperDirty), "server: poop emote did not dirty the diaper");
				check(ctx.computeOnClient(mc -> Life.of(mc.player).diaperDirty), "client: dirty diaper did not sync");
				ctx.takeScreenshot("mp_03_after_emotes");

				// Growth on the server shows up on the client.
				server.runCommand("lifestages goals @a complete");
				ctx.waitTicks(60);
				check(ctx.computeOnClient(mc -> Life.stage(mc.player)) == Stage.INFANT, "client: stage-up did not sync");
				ctx.runOnClient(mc -> mc.setScreenAndShow(new JournalScreen()));
				ctx.waitTicks(5);
				ctx.takeScreenshot("mp_04_journal");
				ctx.runOnClient(mc -> mc.setScreenAndShow(null));

				// Death in combat -> respawn choice -> Elderly.
				server.runCommand("kill @a");
				ctx.waitForScreen(DeathScreen.class);
				ctx.waitTicks(30);
				ctx.runOnClient(mc -> mc.player.respawn());
				ctx.waitForScreen(ChoiceScreen.class);
				ctx.takeScreenshot("mp_05_respawn_choice");
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(3, Optional.empty())));
				ctx.runOnClient(mc -> mc.setScreenAndShow(null));
				ctx.waitTicks(40);
				check(server.computeOnServer(s -> Life.stage(player(s))) == Stage.ELDERLY, "respawn as Elderly failed");
				check(server.computeOnServer(s -> player(s).getInventory().contains(new net.minecraft.world.item.ItemStack(ModItems.WALKING_CANE))),
						"Elderly did not get a Walking Cane");
				ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				ctx.waitTicks(10);
				ctx.takeScreenshot("mp_06_elderly");
				check(server.computeOnServer(s -> !Life.of(player(s)).momAssigned && !Life.of(player(s)).momDead), "the previous life's Mom carried over");

				// Death -> Ghost -> reborn early as an Adult.
				server.runCommand("kill @a");
				ctx.waitForScreen(DeathScreen.class);
				ctx.waitTicks(30);
				ctx.runOnClient(mc -> mc.player.respawn());
				ctx.waitForScreen(ChoiceScreen.class);
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(4, Optional.empty())));
				ctx.runOnClient(mc -> mc.setScreenAndShow(null));
				ctx.waitTicks(40);
				check(server.computeOnServer(s -> player(s).gameMode() == GameType.SPECTATOR), "Ghost is not a spectator");
				ctx.takeScreenshot("mp_07_ghost");
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Act(Payloads.Action.BOO, 0)));
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Act(Payloads.Action.REBORN, 0)));
				ctx.waitForScreen(ChoiceScreen.class);
				ctx.takeScreenshot("mp_08_reborn_choice_no_ghost");
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(1, Optional.empty())));
				ctx.runOnClient(mc -> mc.setScreenAndShow(null));
				ctx.waitTicks(40);
				check(server.computeOnServer(s -> Life.stage(player(s)) == Stage.YOUNG_ADULT && player(s).gameMode() == GameType.SURVIVAL),
						"reborn as a survival Young Adult failed");
				ctx.takeScreenshot("mp_09_reborn_adult");
			}
		}
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
