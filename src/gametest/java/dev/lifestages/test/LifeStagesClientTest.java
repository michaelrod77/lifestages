package dev.lifestages.test;

import dev.lifestages.client.screen.ChoiceScreen;
import dev.lifestages.client.screen.JournalScreen;
import dev.lifestages.block.PresentBlockEntity;
import dev.lifestages.entity.MomEntity;
import dev.lifestages.life.HouseBuilder;
import dev.lifestages.life.Life;
import dev.lifestages.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Payloads;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;

/**
 * Walks one player through a life in singleplayer and saves a screenshot at each step
 * (run/screenshots). Fails if a stage, the Mom or the Will Chest doesn't behave.
 */
public class LifeStagesClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("time set noon");
			server.runCommand("weather clear");

			// 1. First join: the choice screen opens and can't be closed.
			ctx.waitForScreen(ChoiceScreen.class);
			ctx.takeScreenshot("01_first_join_choice");
			ctx.runOnClient(mc -> mc.setScreenAndShow(new dev.lifestages.client.screen.MomPickerScreen(Optional.empty())));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01b_mom_picker");
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(2, Optional.empty(), 4)));
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> Life.stage(s.getPlayerList().getPlayers().get(0))) == Stage.NEWBORN, "baby choice did not start a Newborn");

			// 2. Third-person look at the newborn, then the NPC Mom picks the baby up.
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("02_newborn");
			server.runCommand("lifestages mom summon @p");
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return p.getVehicle() instanceof MomEntity m && m.look() == 4;
			}), "the Mom who arrived is not the one picked (Mom 4)");
			// The emote key really opens the wheel (vanilla 26.3 put Quick Actions on G, which used to swallow it).
			ctx.getInput().holdKey(dev.lifestages.client.Keys.EMOTES);
			ctx.waitForScreen(dev.lifestages.client.screen.EmoteWheelScreen.class);
			ctx.takeScreenshot("02b_emote_wheel");
			ctx.runOnClient(mc -> mc.setScreenAndShow(null)); // close first: releasing over the wheel would fire an emote
			ctx.getInput().releaseKey(dev.lifestages.client.Keys.EMOTES);
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getVehicle() != null), "Mom did not pick up the baby");
			ctx.takeScreenshot("03_mom_holding_baby");

			// 2b. Mom's routine on a sped-up clock: a bottle, a crib at home, and a nap.
			server.runOnServer(s -> dev.lifestages.LifeConfig.get().lifeLengthMultiplier = 0.15);
			server.waitFor(s -> Life.of(s.getPlayerList().getPlayers().get(0)).milk >= 1, 60 * 20);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				for (var pos : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-16, -6, -16), p.blockPosition().offset(16, 6, 16))) {
					if (p.level().getBlockState(pos).is(dev.lifestages.registry.ModBlocks.CRIB)) {
						return true;
					}
				}
				return false;
			}), "Mom did not set up a crib");
			server.waitFor(s -> Life.of(s.getPlayerList().getPlayers().get(0)).napTicks > 100, 90 * 20);
			ctx.takeScreenshot("03b_nap_in_crib");
			server.waitFor(s -> Life.of(s.getPlayerList().getPlayers().get(0)).naps >= 1, 60 * 20);
			server.runOnServer(s -> dev.lifestages.LifeConfig.get().lifeLengthMultiplier = 1.0);
			check(server.computeOnServer(s -> {
				var d = Life.of(s.getPlayerList().getPlayers().get(0));
				return d.houseState == 1 && d.houseStep > 1;
			}), "Mom did not start building the house");

			// 3. Emotes.
			for (int kind = 0; kind < 4; kind++) {
				final int k = kind;
				ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(k)));
				ctx.waitTicks(15);
			}
			ctx.takeScreenshot("04_after_emotes");
			check(server.computeOnServer(s -> Life.of(s.getPlayerList().getPlayers().get(0)).diaperDirty), "poop emote did not dirty the diaper");

			// 3a. Crying twice in a row both register (2 second cooldown).
			long firstCry = server.computeOnServer(s -> dev.lifestages.life.Emotes.readyAt(s.getPlayerList().getPlayers().get(0), dev.lifestages.life.Emotes.Kind.CRY));
			ctx.waitTicks(45);
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(0)));
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> dev.lifestages.life.Emotes.readyAt(s.getPlayerList().getPlayers().get(0), dev.lifestages.life.Emotes.Kind.CRY)) > firstCry,
					"second cry 2.25 s later did not register");

			// 3b. Finish the house now and check the nursery.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				while (d.houseStep < HouseBuilder.size()) {
					d.houseStep = HouseBuilder.step((ServerLevel) p.level(), d.houseOrigin(), d.houseStep);
				}
				d.houseState = 2;
				Life.save(p, d);
			});
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				var lv = p.level();
				boolean barrelStocked = lv.getBlockEntity(o.offset(HouseBuilder.BARREL)) instanceof net.minecraft.world.Container c
						&& c.countItem(dev.lifestages.registry.ModItems.CLEAN_DIAPER) > 0 && c.countItem(dev.lifestages.registry.ModItems.MILK_BOTTLE) > 0;
				return lv.getBlockState(o.offset(HouseBuilder.CRIB)).is(ModBlocks.CRIB)
						&& lv.getBlockState(o.offset(HouseBuilder.CHANGING_TABLE)).is(ModBlocks.CHANGING_TABLE)
						&& lv.getBlockState(o.offset(HouseBuilder.MUSIC_BOX)).is(ModBlocks.MUSIC_BOX)
						&& lv.getBlockState(o.offset(10, 1, 3)).is(net.minecraft.world.level.block.Blocks.CARPET.lightBlue())
						&& barrelStocked
						&& dev.lifestages.block.Nursery.nearestTable(lv, o, 40) != null;
			}), "the nursery is missing its crib, changing table, music box, carpet or stocked barrel");

			// 3c. A dirty baby on the ground cries: Mom picks them up and changes them on the changing table.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				dev.lifestages.life.Carrying.drop(p);
				var o = Life.of(p).houseOrigin();
				p.teleportTo((ServerLevel) p.level(), o.getX() + 4.5, o.getY() + 1, o.getZ() + 10.5, java.util.Set.of(), 0f, 0f, true);
				var d = Life.of(p);
				d.diaperDirty = true;
				Life.save(p, d);
			});
			ctx.waitTicks(50); // past the cry cooldown from 3a
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(0)));
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var t = Life.of(p).houseOrigin().offset(HouseBuilder.CHANGING_TABLE);
				return p.getVehicle() == null && p.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(t.above())) < 1.0;
			}, 45 * 20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03c_changing_table");
			server.waitFor(s -> !Life.of(s.getPlayerList().getPlayers().get(0)).diaperDirty, 20 * 20);
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getVehicle() instanceof MomEntity), "Mom did not pick the baby back up after the change");

			// 3d. A hungry baby cries and gets fed right away.
			ctx.waitTicks(220);
			int milkBefore = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				p.getFoodData().setFoodLevel(4);
				var d = Life.of(p);
				d.diaperDirty = false;
				d.feedsSincePoop = 0;
				Life.save(p, d);
				return d.milk;
			});
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(0)));
			server.waitFor(s -> Life.of(s.getPlayerList().getPlayers().get(0)).milk > milkBefore, 20 * 20);

			// 3e. Music box: plays, speeds naps nearby, and stops by itself.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				dev.lifestages.block.MusicBoxBlockEntity.setPlaying(p.level(), Life.of(p).houseOrigin().offset(HouseBuilder.MUSIC_BOX), true);
			});
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				return dev.lifestages.block.Nursery.musicNear(p.level(), o.offset(HouseBuilder.CRIB))
						&& p.level().getBlockState(o.offset(HouseBuilder.MUSIC_BOX)).getValue(dev.lifestages.block.MusicBoxBlock.PLAYING);
			}), "music box did not start playing");

			// 3f. Nothing wrong: a cuddle, then playtime with the rattle.
			ctx.waitTicks(50); // past the cry cooldown from 3d
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.naps = d.stage().naps;
				d.diaperDirty = false;
				Life.save(p, d);
				p.getFoodData().setFoodLevel(20);
			});
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Emote(0)));
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return p.level().getEntitiesOfClass(MomEntity.class, p.getBoundingBox().inflate(8)).stream()
						.anyMatch(m -> m.playing() && m.getOffhandItem().is(dev.lifestages.registry.ModItems.RATTLE));
			}, 90 * 20); // she may finish another chore (a diaper to the trash) first
			ctx.waitTicks(20);
			ctx.takeScreenshot("03d_playtime");

			// 3g. A dirty diaper left lying around: Mom picks it up and throws it in the nursery trash can.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return p.level().getBlockState(Life.of(p).houseOrigin().offset(HouseBuilder.TRASH_CAN)).is(ModBlocks.TRASH_CAN);
			}), "the nursery has no trash can");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var mom = p.level().getEntitiesOfClass(MomEntity.class, p.getBoundingBox().inflate(16)).get(0);
				var diaper = new net.minecraft.world.entity.item.ItemEntity(p.level(), mom.getX() + 2, mom.getY() + 0.5, mom.getZ(),
						new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.DIRTY_DIAPER));
				diaper.setNeverPickUp(); // only Mom may take it
				diaper.addTag("test_diaper");
				p.level().addFreshEntity(diaper);
			});
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				boolean gone = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(40),
						e -> e.entityTags().contains("test_diaper")).isEmpty();
				return gone && level.getEntitiesOfClass(MomEntity.class, p.getBoundingBox().inflate(40)).stream().noneMatch(MomEntity::tidying);
			}, 150 * 20);

			// 3h. Safety. A baby in Mom's arms is never squeezed into a wall.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var mom = level.getEntitiesOfClass(MomEntity.class, p.getBoundingBox().inflate(40)).get(0);
				if (p.getVehicle() != mom) {
					dev.lifestages.life.Carrying.drop(p);
					p.teleportTo(level, mom.getX(), mom.getY(), mom.getZ(), java.util.Set.of(), 0f, 0f, true);
					dev.lifestages.life.Carrying.pickUp(mom, p);
				}
				float before = p.getHealth();
				p.hurtServer(level, level.damageSources().inWall(), 2.0f);
				return p.getVehicle() == mom && p.getHealth() == before;
			}), "a baby in Mom's arms was hurt by a wall");

			// A zombie in plain view: Mom won't put the baby down (and says why); once it's gone, she will.
			java.util.UUID zombieId = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var mom = (MomEntity) p.getVehicle();
				// out in the open yard, so the zombie is in plain view
				var o = Life.of(p).houseOrigin();
				dev.lifestages.life.Carrying.drop(p);
				mom.teleportTo(o.getX() + 4.5, o.getY() + 1, o.getZ() + 11.5);
				p.teleportTo(level, o.getX() + 4.5, o.getY() + 1, o.getZ() + 11.5, java.util.Set.of(), 0f, 0f, true);
				dev.lifestages.life.Carrying.pickUp(mom, p);
				var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				zombie.snapTo(mom.getX() + 4, mom.getY(), mom.getZ(), 0f, 0f);
				zombie.setNoAi(true);
				zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 600));
				level.addFreshEntity(zombie);
				return zombie.getUUID();
			});
			ctx.waitTicks(2);
			check(!server.computeOnServer(s -> dev.lifestages.life.Moms.requestDown(s.getPlayerList().getPlayers().get(0))),
					"Mom put the baby down with a zombie in plain view");
			server.runOnServer(s -> ((ServerLevel) s.getPlayerList().getPlayers().get(0).level()).getEntity(zombieId).discard());
			ctx.waitTicks(2);
			check(server.computeOnServer(s -> dev.lifestages.life.Moms.requestDown(s.getPlayerList().getPlayers().get(0))),
					"Mom would not put the baby down once the zombie was gone");

			// The baby catches fire on the ground: Mom scoops them up and pats the flames out.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				dev.lifestages.life.Carrying.drop(p);
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60 * 20));
				var o = Life.of(p).houseOrigin();
				p.teleportTo((ServerLevel) p.level(), o.getX() + 4.5, o.getY() + 1, o.getZ() + 11.5, java.util.Set.of(), 0f, 0f, true);
				p.setRemainingFireTicks(30 * 20);
			});
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return p.getVehicle() instanceof MomEntity && !p.isOnFire();
			}, 30 * 20);

			// Night: a baby left outside is brought in, and Mom shuts the front door.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				dev.lifestages.life.Carrying.drop(p);
				var o = Life.of(p).houseOrigin();
				p.teleportTo((ServerLevel) p.level(), o.getX() + 4.5, o.getY() + 1, o.getZ() + 11.5, java.util.Set.of(), 0f, 0f, true);
				var door = o.offset(4, 1, 6);
				var state = p.level().getBlockState(door);
				((net.minecraft.world.level.block.DoorBlock) state.getBlock()).setOpen(null, p.level(), state, door, true);
			});
			server.runCommand("time set midnight");
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				var door = p.level().getBlockState(o.offset(4, 1, 6));
				boolean inside = p.getX() > o.getX() + 0.5 && p.getX() < o.getX() + 12.5 && p.getZ() > o.getZ() + 0.5 && p.getZ() < o.getZ() + 6.5;
				return p.getVehicle() instanceof MomEntity && inside && !door.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
			}, 60 * 20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03e_inside_at_night");
			server.runCommand("time set noon");
			ctx.waitTicks(20);

			// 3i. Deep water: Mom holding the baby in a 3-deep walled pool swims to the edge and climbs out.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var o = Life.of(p).houseOrigin();
				var base = o.offset(26, 0, 0);
				for (int x = -1; x <= 5; x++) {
					for (int z = -1; z <= 5; z++) {
						boolean edge = x == -1 || z == -1 || x == 5 || z == 5;
						for (int y = -2; y <= 1; y++) {
							level.setBlockAndUpdate(base.offset(x, y, z), edge || y == -2
									? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
									: y == 1 ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
									: net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
						}
						for (int y = 2; y <= 4; y++) {
							level.setBlockAndUpdate(base.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
				var mom = level.getEntitiesOfClass(MomEntity.class, p.getBoundingBox().inflate(60)).get(0);
				dev.lifestages.life.Carrying.drop(p);
				mom.teleportTo(base.getX() + 2.5, base.getY() - 1, base.getZ() + 2.5);
				p.teleportTo(level, base.getX() + 2.5, base.getY() - 1, base.getZ() + 2.5, java.util.Set.of(), 0f, 0f, true);
				dev.lifestages.life.Carrying.pickUp(mom, p);
			});
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getVehicle() instanceof MomEntity m && m.isInWater()),
					"test setup: Mom and the baby are not in the pool");
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return p.getVehicle() instanceof MomEntity m && !m.isInWater() && m.onGround();
			}, 30 * 20);

			// 3j. Mom's frying pan: one bonk finishes any mob, even a ravager.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var mom = (MomEntity) p.getVehicle();
				boolean all = true;
				for (var type : java.util.List.of(net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.RAVAGER)) {
					var mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
					mob.snapTo(mom.getX() + 1.5, mom.getY(), mom.getZ(), 0f, 0f);
					mob.setNoAi(true);
					level.addFreshEntity(mob);
					mom.doHurtTarget(level, mob);
					all &= !mob.isAlive();
				}
				return all;
			}), "Mom's frying pan did not finish a mob in one hit");

			// 3k. Steep drops near the house: a 5-high dirt mound gets its sides filled in (no step over 3)...
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var o = Life.of(p).houseOrigin();
				for (int x = -6; x <= -4; x++) {
					for (int z = 1; z <= 3; z++) {
						for (int y = 1; y <= 5; y++) {
							level.setBlockAndUpdate(o.offset(x, y, z), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
						}
					}
				}
				var mom = (MomEntity) p.getVehicle();
				dev.lifestages.life.Carrying.drop(p);
				mom.teleportTo(o.getX() + 4.5, o.getY() + 1, o.getZ() + 9.5);
				p.teleportTo(level, o.getX() + 4.5, o.getY() + 1, o.getZ() + 9.5, java.util.Set.of(), 0f, 0f, true);
				dev.lifestages.life.Carrying.pickUp(mom, p);
			});
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return dev.lifestages.life.DropFixer.next((ServerLevel) p.level(), Life.of(p).houseOrigin(), java.util.Set.of()) == null;
			}, 180 * 20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03f_drop_filled");
			// ...while a 10-high pillar (too deep to fill) gets a fence on its edge instead.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var o = Life.of(p).houseOrigin();
				for (int y = 1; y <= 10; y++) {
					level.setBlockAndUpdate(o.offset(16, y, 3), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
				}
				var fix = dev.lifestages.life.DropFixer.next(level, o, java.util.Set.of());
				for (int y = 1; y <= 10; y++) {
					level.setBlockAndUpdate(o.offset(16, y, 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
				return fix != null && fix.fence() && fix.pos().equals(o.offset(16, 11, 3));
			}), "a drop too deep to fill was not given a fence");

			// 4. Growth: complete the goals and grow up.
			Stage beforeGoals = server.computeOnServer(s -> Life.stage(s.getPlayerList().getPlayers().get(0)));
			server.runCommand("lifestages goals @p complete");
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> Life.stage(s.getPlayerList().getPlayers().get(0))) == beforeGoals.next(), "goals complete but no stage-up");

			// 4b. First birthday: a present with the Teddy Bear inside. Place it, open it (4 slots), then break it:
			// the dropped present keeps what's inside.
			server.runCommand("lifestages mom dismiss @p");
			server.runCommand("lifestages stage @p infant");
			ctx.waitTicks(5);
			net.minecraft.core.BlockPos presentPos = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					var st = inv.getItem(i);
					var loot = st.get(net.minecraft.core.component.DataComponents.CONTAINER_LOOT);
					if (st.is(ModBlocks.PRESENT_COMMON.asItem()) && loot != null && loot.lootTable().equals(PresentBlockEntity.lootTable("teddy"))) {
						// Place it the way a right-click does, as an Infant (babies can place and open presents).
						inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
						p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
						var pos = p.blockPosition().relative(p.getDirection(), 2);
						var ground = pos.below();
						p.level().setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
						p.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0),
								net.minecraft.core.Direction.UP, ground, false);
						p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
						return p.level().getBlockState(pos).is(ModBlocks.PRESENT_COMMON) ? pos : null;
					}
				}
				return null;
			});
			check(presentPos != null, "an Infant could not place the first birthday present (or it had no Teddy Bear inside)");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				p.openMenu((PresentBlockEntity) p.level().getBlockEntity(presentPos));
			});
			ctx.waitForScreen(dev.lifestages.client.screen.PresentScreen.class);
			ctx.waitTicks(5);
			ctx.takeScreenshot("04a_present_opened");
			check(ctx.computeOnClient(mc -> mc.player.containerMenu.slots.size()) == 4 + 36, "the present menu does not have four slots");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				return ((PresentBlockEntity) p.level().getBlockEntity(presentPos)).countItem(ModBlocks.TEDDY_BEAR.asItem()) == 1;
			}), "the opened present did not hold the Teddy Bear");
			ctx.runOnClient(mc -> mc.player.closeContainer());
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				boolean broke = p.gameMode.destroyBlock(presentPos); // as an Infant: presents are baby-safe
				if (!broke) {
					return false;
				}
				return p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(presentPos).inflate(2))
						.stream().anyMatch(e -> e.getItem().is(ModBlocks.PRESENT_COMMON.asItem())
								&& e.getItem().getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
										net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItemCopyStream().anyMatch(t -> t.is(ModBlocks.TEDDY_BEAR.asItem())));
			}), "an Infant could not break the present, or it did not keep the Teddy Bear inside");

			// 4c. A vanilla advancement makes you grow.
			int before = server.computeOnServer(s -> Life.of(s.getPlayerList().getPlayers().get(0)).stageTicks);
			server.runCommand("advancement grant @p only minecraft:story/mine_stone");
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> Life.of(s.getPlayerList().getPlayers().get(0)).stageTicks) >= before + 1200, "advancement did not boost growth");

			// 4d. Look at the finished house (built in 3b).
			server.runCommand("lifestages mom dismiss @p");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), o.getX() + 6.5, o.getY() + 1, o.getZ() + 18.5, java.util.Set.of(), 180f, 10f, true);
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			ctx.takeScreenshot("04b_house");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), o.getX() + 4.5, o.getY() + 1, o.getZ() + 4.5, java.util.Set.of(), 0f, 25f, true);
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("04c_inside_house");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var o = Life.of(p).houseOrigin();
				p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), o.getX() + 10.5, o.getY() + 1, o.getZ() + 5.5, java.util.Set.of(), 180f, 25f, true);
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("04d_nursery");

			// 5. Toddler look (onesie, diaper, booties, proportions), put down by Mom.
			server.runCommand("lifestages mom dismiss @p");
			server.runCommand("lifestages stage @p toddler");
			server.runCommand("tp @p ~ ~0.5 ~ 0 15");
			ctx.waitTicks(30);
			ctx.takeScreenshot("05_toddler");

			// 5b. Growth bar: half-way through the stage, once the chat has faded.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.stageTicks = d.requiredTicks(dev.lifestages.LifeConfig.get().lifeLengthMultiplier) / 2;
				Life.save(p, d);
			});
			ctx.waitTicks(220);
			ctx.takeScreenshot("05b_growth_bar");

			// 5c. Goals speed the stage up: no arrows at the normal pace, then one and three as it accelerates.
			check(server.computeOnServer(s -> {
				var d = Life.of(s.getPlayerList().getPlayers().get(0));
				int milk = d.milk, food = d.food, naps = d.naps, nights = d.nights;
				d.milk = d.food = d.naps = d.nights = 0;
				boolean none = d.speedArrows() == 0;
				d.milk = 1;
				boolean one = d.speedArrows() == 1;
				d.milk = d.stage().milk;
				d.food = d.stage().food;
				d.naps = d.stage().naps;
				d.nights = d.stage().nights;
				boolean three = d.speedArrows() == 3;
				d.milk = milk;
				d.food = food;
				d.naps = naps;
				d.nights = nights;
				return none && one && three;
			}), "the growth bar arrows did not follow how fast the stage is speeding up");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.milk = d.stage().milk;
				d.food = d.stage().food;
				d.naps = d.stage().naps;
				d.nights = d.stage().nights;
				d.stageTicks = d.requiredTicks(dev.lifestages.LifeConfig.get().lifeLengthMultiplier) / 2;
				Life.save(p, d);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("05c_speed_arrows");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.milk = d.food = d.naps = d.nights = 0;
				d.stageTicks = d.requiredTicks(dev.lifestages.LifeConfig.get().lifeLengthMultiplier) / 2;
				Life.save(p, d);
			});

			// 6. Sugar rush.
			server.runCommand("give @p minecraft:cookie 4");
			server.runCommand("effect give @p minecraft:hunger 3 255 true");
			ctx.waitTicks(80);
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				LifeData d = Life.of(p);
				dev.lifestages.life.Feeding.sugarRush(p, d);
				Life.save(p, d);
			});
			ctx.waitTicks(10);
			check(server.computeOnServer(s -> Life.of(s.getPlayerList().getPlayers().get(0)).sugarTicks > 0), "sugar rush did not start");
			ctx.takeScreenshot("06_sugar_rush");

			// 6b. The sugar crash: when the rush wears off, a child is slow and dizzy until they sleep.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.sugarTicks = 2;
				Life.save(p, d);
			});
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				boolean crashed = d.crashTicks > 0 && p.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA)
						&& p.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
				dev.lifestages.life.LifeManager.endSugarCrash(p, d); // keep later screenshots steady
				Life.save(p, d);
				return crashed && !p.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
			}), "a player's sugar rush did not end in a sugar crash (slow and dizzy)");

			// 7. Growth Journal.
			ctx.runOnClient(mc -> mc.setScreenAndShow(new JournalScreen()));
			ctx.waitTicks(5);
			ctx.takeScreenshot("07_journal");
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.lookingForParent = true;
				Life.save(p, d);
				dev.lifestages.life.Family.claimIfLooking(p, p);
				return Life.of(p).lookingForParent && !Life.of(p).parents.contains(p.getUUID());
			}), "a child looking for a parent was able to adopt themselves");
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> Life.of(mc.player).lookingForParent), "looking for a new parent did not sync to the client");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.lookingForParent = false;
				Life.save(p, d);
			});

			// 8. Blocks and items.
			server.runCommand("setblock ~2 ~ ~ lifestages:crib");
			server.runCommand("setblock ~-2 ~ ~ lifestages:will_chest");
			server.runCommand("setblock ~ ~ ~2 lifestages:gravestone");
			server.runCommand("setblock ~ ~ ~-2 lifestages:mess");
			server.runCommand("give @p lifestages:milk_bottle 3");
			server.runCommand("give @p lifestages:clean_diaper 4");
			server.runCommand("give @p lifestages:frying_pan");
			server.runCommand("give @p lifestages:walking_cane");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(20);
			ctx.takeScreenshot("08_blocks_and_items");

			// 8a. Close-up: teddy bear, present and trash can side by side; then a diaper goes in the can.
			net.minecraft.core.BlockPos canPos = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var row = p.blockPosition().north(2);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -1; dz <= 3; dz++) {
						level.setBlockAndUpdate(row.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
						for (int dy = 0; dy <= 2; dy++) {
							level.setBlockAndUpdate(row.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
				level.setBlockAndUpdate(row, ModBlocks.TEDDY_BEAR.defaultBlockState()
						.setValue(dev.lifestages.block.TeddyBearBlock.FACING, net.minecraft.core.Direction.SOUTH));
				level.setBlockAndUpdate(row.west(), ModBlocks.PRESENT_RARE.defaultBlockState());
				level.setBlockAndUpdate(row.east(), ModBlocks.TRASH_CAN.defaultBlockState());
				p.teleportTo(level, row.getX() + 0.5, row.getY(), row.getZ() + 2.5, java.util.Set.of(), 180f, 30f, true);
				return row.east();
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(20);
			ctx.takeScreenshot("08a_teddy_present_trash_can");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.DIRTY_DIAPER));
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(canPos), net.minecraft.core.Direction.UP, canPos, false);
				p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				return p.getMainHandItem().isEmpty() && p.level().getBlockState(canPos).getValue(dev.lifestages.block.TrashCanBlock.OPEN);
			}), "right-clicking the trash can with a dirty diaper did not throw it away");
			ctx.waitTicks(5);
			ctx.takeScreenshot("08b_trash_can_open");

			// 8c. Reaching Adult: the birthday present holds four Baby Spawn Eggs.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				dev.lifestages.life.Birthdays.give(p, d, Stage.ADULT);
				Life.save(p, d);
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					var c = inv.getItem(i).get(net.minecraft.core.component.DataComponents.CONTAINER);
					if (inv.getItem(i).is(ModBlocks.PRESENT_EPIC.asItem()) && c != null
							&& c.nonEmptyItemCopyStream().filter(e -> e.is(dev.lifestages.registry.ModItems.BABY_SPAWN_EGG)).count() == 4) {
						return true;
					}
				}
				return false;
			}), "the Adult birthday present did not hold four Baby Spawn Eggs");

			// 8d. Hatch an NPC baby and look after it: feed, change, carry to a crib, sugar rush, rescue, monsters.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.BABY_SPAWN_EGG));
				var ground = p.blockPosition().north(2).below();
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0),
						net.minecraft.core.Direction.UP, ground, false);
				level.setBlockAndUpdate(ground.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			});
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var babies = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8));
				if (babies.size() != 1 || !p.getUUID().equals(babies.get(0).ownerId()) || babies.get(0).getCustomName() == null) {
					return false;
				}
				int milk = 0, diapers = 0, cribs = 0, rattles = 0, trash = 0, music = 0;
				var area = net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-4, -1, -4), p.blockPosition().offset(4, 2, 4));
				for (var pos : area) {
					if (!p.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CHEST)) {
						continue;
					}
					if (!(p.level().getBlockEntity(pos) instanceof net.minecraft.world.Container chest)) {
						continue;
					}
					for (int i = 0; i < chest.getContainerSize(); i++) {
						var st = chest.getItem(i);
						if (st.is(dev.lifestages.registry.ModItems.MILK_BOTTLE)) milk += st.getCount();
						else if (st.is(dev.lifestages.registry.ModItems.CLEAN_DIAPER)) diapers += st.getCount();
						else if (st.is(ModBlocks.CRIB.asItem())) cribs += st.getCount();
						else if (st.is(dev.lifestages.registry.ModItems.RATTLE)) rattles += st.getCount();
						else if (st.is(ModBlocks.TRASH_CAN.asItem())) trash += st.getCount();
						else if (st.is(ModBlocks.MUSIC_BOX.asItem())) music += st.getCount();
					}
				}
				return milk == 64 && diapers == 32 && cribs == 1 && rattles == 1 && trash == 1 && music == 1;
			}), "the Baby Spawn Egg did not hatch a baby with a starter chest");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var baby = level.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8)).get(0);
				var main = net.minecraft.world.InteractionHand.MAIN_HAND;
				// feed
				baby.setHunger(4);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.MILK_BOTTLE));
				baby.interact(p, main, baby.position());
				boolean fed = baby.hunger() == 10;
				// change
				baby.setDirty(true);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.CLEAN_DIAPER));
				baby.interact(p, main, baby.position());
				boolean changed = !baby.dirty() && p.getInventory().contains(new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.DIRTY_DIAPER));
				// This stretch of the test is still a toddler. Pickup without sneaking is for an adult.
				var life = Life.of(p);
				int previousStage = life.stage;
				life.stage = Stage.YOUNG_ADULT.ordinal();
				Life.save(p, life);
				p.setItemInHand(main, net.minecraft.world.item.ItemStack.EMPTY);
				p.setShiftKeyDown(false);
				baby.interact(p, main, baby.position());
				boolean carried = baby.getVehicle() == p;
				var table = p.blockPosition().west(2);
				level.setBlockAndUpdate(table, ModBlocks.CHANGING_TABLE.defaultBlockState());
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), main,
						new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table), net.minecraft.core.Direction.UP, table, false));
				boolean onTable = baby.getVehicle() == null && baby.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table)) < 2.5;
				baby.interact(p, main, baby.position());
				var crib = p.blockPosition().east(2);
				level.setBlockAndUpdate(crib, ModBlocks.CRIB.defaultBlockState());
				baby.setEnergy(40);
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), main,
						new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(crib), net.minecraft.core.Direction.UP, crib, false));
				boolean inCrib = baby.getVehicle() == null && baby.blockPosition().equals(crib) && baby.asleep();
				life = Life.of(p);
				life.stage = previousStage;
				Life.save(p, life);
				return fed && changed && carried && onTable && inCrib;
			}), "caring for the NPC baby (feed, change, carry, changing table, crib) did not work");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(10);
			ctx.takeScreenshot("08c_npc_baby_in_crib");

			// 8c2. A baby asleep in a crib can be picked up by right-clicking the crib; babies can't hop out of cribs;
			// a diaper changed on the changing table stays clean longer; a sugar rush ends in a crash.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var baby = level.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8)).get(0);
				var main = net.minecraft.world.InteractionHand.MAIN_HAND;
				var life = Life.of(p);
				int previousStage = life.stage;
				life.stage = Stage.YOUNG_ADULT.ordinal();
				Life.save(p, life);
				p.setItemInHand(main, net.minecraft.world.item.ItemStack.EMPTY);
				var crib = baby.blockPosition();
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), main,
						new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(crib), net.minecraft.core.Direction.UP, crib, false));
				boolean fromCrib = baby.getVehicle() == p;
				// jumping: newborns and infants can't; toddlers clear well under the crib rail (3/4 block)
				baby.setStage(Stage.NEWBORN);
				double newbornJump = baby.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
				baby.setStage(Stage.TODDLER);
				double toddlerJump = baby.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
				baby.setStage(Stage.NEWBORN);
				// onto the changing table, changed there, then three bottles
				var table = p.blockPosition().west(3);
				level.setBlockAndUpdate(table, ModBlocks.CHANGING_TABLE.defaultBlockState());
				p.gameMode.useItemOn(p, level, p.getMainHandItem(), main,
						new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table), net.minecraft.core.Direction.UP, table, false));
				baby.setDirty(true);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.CLEAN_DIAPER));
				baby.interact(p, main, baby.position());
				boolean fresh = baby.freshFromTable() && !baby.dirty();
				for (int i = 0; i < 3; i++) {
					baby.setHunger(2);
					p.setItemInHand(main, new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.MILK_BOTTLE));
					baby.interact(p, main, baby.position());
				}
				p.setItemInHand(main, net.minecraft.world.item.ItemStack.EMPTY);
				// a sugar rush about to wear off
				baby.setStage(Stage.TODDLER);
				baby.startSugarRush(2);
				life = Life.of(p);
				life.stage = previousStage;
				Life.save(p, life);
				return fromCrib && newbornJump == 0.0 && toddlerJump < 0.3 && fresh;
			}), "crib pickup, crib jumping limits or the changing-table diaper did not work");
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8)).get(0);
				boolean stillClean = !baby.dirty() && baby.feedsSincePoop() >= 3; // three feeds would normally dirty it
				boolean crash = baby.sugarCrash() && baby.energy() <= 14
						&& baby.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < 0.1;
				return stillClean && crash;
			}), "the changing-table diaper did not stay clean, or the sugar rush did not end in a crash");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var baby = level.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8)).get(0);
				var main = net.minecraft.world.InteractionHand.MAIN_HAND;
				// a toddler on a cookie: sugar rush
				baby.setStage(Stage.TODDLER);
				baby.setHunger(10);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKIE));
				baby.interact(p, main, baby.position());
				boolean rush = baby.sugarRush();
				// a terrible fall: rescued and brought home instead of lost
				baby.hurtServer(level, level.damageSources().fall(), 1000.0f);
				boolean rescued = baby.isAlive() && baby.closeCalls() == 1 && baby.blockPosition().closerThan(baby.home(), 2.0);
				return rush && rescued;
			}), "the NPC baby's sugar rush or rescue did not work");
			// Monsters go after NPC babies (the player is in creative so the zombie ignores them).
			java.util.UUID babyZombie = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				var baby = level.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(8)).get(0);
				var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				zombie.snapTo(baby.getX() + 3, baby.getY(), baby.getZ(), 0f, 0f);
				zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 600));
				level.addFreshEntity(zombie);
				return zombie.getUUID();
			});
			server.waitFor(s -> ((ServerLevel) s.getPlayerList().getPlayers().get(0).level()).getEntity(babyZombie)
					instanceof net.minecraft.world.entity.Mob z && z.getTarget() instanceof dev.lifestages.entity.BabyEntity, 10 * 20);
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				((ServerLevel) p.level()).getEntity(babyZombie).discard();
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			});

			// 8e. Better care, faster growth: all four needs covered is twice the normal pace; neglect stops growth.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(16)).get(0);
				baby.setStage(Stage.CHILD);
				baby.setHunger(20);
				baby.setEnergy(100);
				baby.setDirty(false);
				baby.love();
				return true;
			}), "setup");
			ctx.waitTicks(3);
			float great = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).level()
					.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, s.getPlayerList().getPlayers().get(0).getBoundingBox().inflate(16)).get(0).growthSpeed());
			server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).level()
					.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, s.getPlayerList().getPlayers().get(0).getBoundingBox().inflate(16)).get(0).setHunger(3));
			ctx.waitTicks(3);
			float neglected = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).level()
					.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, s.getPlayerList().getPlayers().get(0).getBoundingBox().inflate(16)).get(0).growthSpeed());
			check(great == 2.0f && neglected == 0f, "care did not set the growth speed (great " + great + ", neglected " + neglected + ")");

			// A child (t-shirt and shorts) standing in front of the camera.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(16)).get(0);
				baby.setHunger(20);
				baby.setNoAi(true);
				var front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
				baby.teleportTo(front.x, p.getY(), front.z);
				baby.setYRot(p.getYRot() + 180);
				baby.setYHeadRot(p.getYRot() + 180);
				baby.setYBodyRot(p.getYRot() + 180);
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(10);
			ctx.takeScreenshot("08e_npc_child");

			// Grows all the way up with great care (on a sped-up clock): a Young Adult, with a thank-you present.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(16)).get(0);
				baby.setNoAi(false); // a frozen mob doesn't tick, so it couldn't grow
				baby.setStage(Stage.NEWBORN);
				baby.setHunger(20);
				baby.setEnergy(100);
				baby.setDirty(false);
				baby.love();
				dev.lifestages.LifeConfig.get().lifeLengthMultiplier = 0.002;
			});
			server.waitFor(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(40)).get(0);
				baby.setHunger(20); // keep the care great while the clock races
				baby.setEnergy(100);
				baby.setDirty(false);
				baby.love();
				return baby.grownUp();
			}, 60 * 20);
			server.runOnServer(s -> dev.lifestages.LifeConfig.get().lifeLengthMultiplier = 1.0);
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(ModBlocks.PRESENT_RARE.asItem())) {
						return true;
					}
				}
				return false;
			}), "a grown-up NPC child did not give the parent a present");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var baby = p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(40)).get(0);
				baby.setNoAi(true);
				var front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
				baby.teleportTo(front.x, p.getY(), front.z);
				baby.setYRot(p.getYRot() + 180);
				baby.setYHeadRot(p.getYRot() + 180);
				baby.setYBodyRot(p.getYRot() + 180);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("08f_npc_grown_up");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(40)).get(0).setNoAi(false);
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			// 8b. An ordinary death respawns through the choice screen.
			server.runCommand("kill @p");
			ctx.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
			ctx.waitTicks(25);
			ctx.runOnClient(mc -> mc.player.respawn());
			ctx.waitTicks(60);
			ctx.waitForScreen(ChoiceScreen.class);
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(1, Optional.empty())));
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
			ctx.waitTicks(20);

			// 9. Elderly look, then death of old age leaves a Will Chest.
			server.runCommand("lifestages stage @p elderly");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(30);
			ctx.takeScreenshot("09_elderly");
			server.runCommand("give @p minecraft:diamond 5");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				LifeData d = Life.of(p);
				d.stageTicks = d.lifespanTicks + d.bonusTicks - 1;
				Life.save(p, d);
			});
			ctx.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
			ctx.waitTicks(5);
			ctx.takeScreenshot("10_died_of_old_age");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				for (var pos : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-3, -2, -3), p.blockPosition().offset(3, 4, 3))) {
					if (p.level().getBlockState(pos).is(dev.lifestages.registry.ModBlocks.WILL_CHEST)) {
						return true;
					}
				}
				return false;
			}), "no Will Chest after death of old age");
			ctx.runOnClient(mc -> mc.player.respawn());
			ctx.waitTicks(60);
			ctx.waitForScreen(ChoiceScreen.class);
			ctx.takeScreenshot("11_respawn_choice");
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new Payloads.Choose(1, Optional.empty())));
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> Life.stage(s.getPlayerList().getPlayers().get(0))) == Stage.YOUNG_ADULT, "respawn as Adult failed");

			// 12. Adults start with an empty inventory (the nursery arrives as a chest per baby), and ordinary items work.
			check(server.computeOnServer(s -> {
				var inv = s.getPlayerList().getPlayers().get(0).getInventory();
				return !inv.contains(new net.minecraft.world.item.ItemStack(ModBlocks.CRIB))
						&& !inv.contains(new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.MILK_BOTTLE))
						&& !inv.contains(new net.minecraft.world.item.ItemStack(dev.lifestages.registry.ModItems.CLEAN_DIAPER));
			}), "an Adult started with nursery items in their inventory");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var rod = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD);
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, rod);
				p.gameMode.useItem(p, p.level(), rod, net.minecraft.world.InteractionHand.MAIN_HAND);
				return p.fishing != null;
			}), "a fishing rod did not cast for an Adult");

			// 13. Feeding: a Toddler player eats ordinary food; an NPC infant eats bread; an NPC newborn takes only milk.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var main = net.minecraft.world.InteractionHand.MAIN_HAND;
				var baby = dev.lifestages.registry.ModEntities.BABY.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				baby.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0f, 0f);
				baby.hatch(p, p.blockPosition());
				level.addFreshEntity(baby);
				baby.setStage(Stage.INFANT);
				baby.setHunger(4);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
				baby.interact(p, main, baby.position());
				boolean infantAte = baby.hunger() > 4;
				baby.setStage(Stage.NEWBORN);
				baby.setHunger(4);
				p.setItemInHand(main, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
				baby.interact(p, main, baby.position());
				boolean newbornRefused = baby.hunger() == 4;
				baby.discard();
				// the player as a toddler: steak is no longer "too hard to chew"
				var d = Life.of(p);
				d.newLife(Stage.TODDLER);
				Life.save(p, d);
				p.getFoodData().setFoodLevel(6);
				var steak = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF);
				p.setItemInHand(main, steak);
				p.gameMode.useItem(p, level, steak, main);
				boolean toddlerEats = p.isUsingItem();
				p.stopUsingItem();
				return infantAte && newbornRefused && toddlerEats;
			}), "feeding rules: an NPC infant should eat bread, a newborn only milk, and a toddler player anything");

			// 14. Adults are looking for a baby by default.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var d = Life.of(p);
				d.pendingChoice = 2;
				Life.save(p, d);
				dev.lifestages.life.Choices.choose(p, dev.lifestages.life.Choices.ADULT, null);
				return Life.of(p).lookingForBaby;
			}), "a new Adult is not looking for a baby by default");

			// 15. The baby carrier: two newborns fit, a third doesn't, and a toddler rides alone. V puts one down.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var main = net.minecraft.world.InteractionHand.MAIN_HAND;
				p.setItemInHand(main, net.minecraft.world.item.ItemStack.EMPTY);
				java.util.List<dev.lifestages.entity.BabyEntity> babies = new java.util.ArrayList<>();
				for (int i = 0; i < 3; i++) {
					var b = dev.lifestages.registry.ModEntities.BABY.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
					b.snapTo(p.getX() + 1 + i, p.getY(), p.getZ(), 0f, 0f);
					b.hatch(p, p.blockPosition());
					level.addFreshEntity(b);
					babies.add(b);
				}
				p.setShiftKeyDown(false);
				babies.get(0).interact(p, main, babies.get(0).position());
				babies.get(1).interact(p, main, babies.get(1).position());
				babies.get(2).interact(p, main, babies.get(2).position());
				boolean twoNewborns = p.getPassengers().size() == 2 && babies.get(2).getVehicle() == null;
				babies.get(1).stopRiding();
				babies.get(2).setStage(Stage.TODDLER);
				babies.get(2).interact(p, main, babies.get(2).position());
				boolean toddlerAlone = babies.get(2).getVehicle() == null;
				babies.get(1).interact(p, main, babies.get(1).position());
				p.setShiftKeyDown(false);
				babies.get(2).discard();
				return twoNewborns && toddlerAlone && p.getPassengers().size() == 2;
			}), "carrier capacity: two newborns, not a third, and no toddler alongside a baby");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(220); // let the chat fade so the carrier is visible
			ctx.takeScreenshot("15_baby_carrier_twins");
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				dev.lifestages.life.Carrying.drop(p);
				dev.lifestages.life.Carrying.drop(p);
				boolean down = p.getPassengers().isEmpty();
				p.level().getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class, p.getBoundingBox().inflate(16)).forEach(net.minecraft.world.entity.Entity::discard);
				return down;
			}), "V (put down) did not put the NPC babies down");

			// 16. A second house is planned clear of the first.
			check(server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().get(0);
				var level = (ServerLevel) p.level();
				var first = p.blockPosition().offset(-6, -1, -3);
				var second = HouseBuilder.plan(level, p.blockPosition(), java.util.List.of(first));
				return second != null && (Math.abs(second.getX() - first.getX()) > HouseBuilder.W + 4
						|| Math.abs(second.getZ() - first.getZ()) > HouseBuilder.D + 7);
			}), "a second house was planned on top of the first");
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
