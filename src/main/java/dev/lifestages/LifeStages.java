package dev.lifestages;

import dev.lifestages.life.Carrying;
import dev.lifestages.life.Emotes;
import dev.lifestages.life.Feeding;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.LifeManager;
import dev.lifestages.life.Moms;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Net;
import dev.lifestages.registry.ModBlocks;
import dev.lifestages.registry.ModComponents;
import dev.lifestages.registry.ModEffects;
import dev.lifestages.registry.ModEntities;
import dev.lifestages.registry.ModItems;
import dev.lifestages.registry.ModParticles;
import dev.lifestages.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifeStages implements ModInitializer {
	public static final String MOD_ID = "lifestages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LifeConfig.load();
		ModComponents.init();
		ModSounds.init();
		ModParticles.init();
		ModEffects.init();
		ModItems.init();
		ModBlocks.init();
		dev.lifestages.registry.ModMenus.init();
		ModEntities.init();
		Life.init();
		Net.init();

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> resetState());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> resetState());
		// Monsters go after NPC babies too: keeping them safe is the parent's job.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.monster.Monster monster && !(entity instanceof net.minecraft.world.entity.NeutralMob)) {
				((dev.lifestages.mixin.MobAccessor) monster).lifestages$targetSelector().addGoal(3,
						new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(monster, dev.lifestages.entity.BabyEntity.class, true));
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> LifeCommands.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LifeManager.tick(server);
			Moms.tick(server);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Carrying.tick(p);
			}
		});

		ServerPlayerEvents.JOIN.register(Net::syncConfig);
		ServerPlayerEvents.LEAVE.register(player -> {
			Carrying.drop(player);
			Moms.onLeave(player);
		});
		// Mark the choice on the dying player: attachments are copied to the respawned player after AFTER_RESPAWN runs.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				LifeData d = Life.of(player);
				if (d.started && !d.isGhost()) {
					d.pendingChoice = 2;
					Life.save(player, d);
				}
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				LifeData d = Life.of(newPlayer);
				if (d.started && !d.isGhost()) {
					d.pendingChoice = 2;
					Life.save(newPlayer, d);
				}
			}
		});

		ItemEvents.USE.register(Feeding::onUse);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			InteractionResult r = Carrying.onUseEntity(player, level, hand, entity);
			if (r != InteractionResult.PASS) {
				return r;
			}
			r = Feeding.onUseEntity(player, level, hand, entity);
			if (r != InteractionResult.PASS) {
				return r;
			}
			if (entity instanceof Villager && player.hasEffect(ModEffects.STINKY)) {
				if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.literal("The villager holds their nose and refuses to trade.").withStyle(ChatFormatting.GREEN));
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// Carrying an NPC baby: right-click a crib to lay them down for a nap.
			if (player.getFirstPassenger() instanceof dev.lifestages.entity.BabyEntity baby
					&& level.getBlockState(hit.getBlockPos()).is(dev.lifestages.registry.ModBlocks.CRIB)) {
				if (!level.isClientSide()) {
					baby.putInCrib(hit.getBlockPos(), player);
				}
				return InteractionResult.SUCCESS;
			}
			// Carrying a baby: right-click a changing table to set them down on it.
			var passenger = player.getFirstPassenger();
			if ((passenger instanceof dev.lifestages.entity.BabyEntity || passenger instanceof Player)
					&& level.getBlockState(hit.getBlockPos()).is(dev.lifestages.registry.ModBlocks.CHANGING_TABLE)) {
				if (!level.isClientSide()) {
					Carrying.putOnChangingTable(player, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			if (player.isCreative() || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (Life.of(player).started && Life.stage(player).isBaby()) {
				// Babies can't open doors or chests yet, but they can unwrap presents and put down their teddy.
				var held = player.getItemInHand(hand);
				boolean gift = level.getBlockState(hit.getBlockPos()).getBlock() instanceof dev.lifestages.block.PresentBlock
						|| held.getItem() instanceof BlockItem bi && (bi.getBlock() instanceof dev.lifestages.block.PresentBlock
								|| bi.getBlock() == dev.lifestages.registry.ModBlocks.TEDDY_BEAR);
				return gift ? InteractionResult.PASS : InteractionResult.FAIL;
			}
			if (Carrying.isCarried(player)) {
				return InteractionResult.FAIL; // a baby in a carrier can't reach anything
			}
			var state = level.getBlockState(hit.getBlockPos());
			if (state.getBlock() instanceof CakeBlock && player.getFoodData().needsFood() && player instanceof ServerPlayer sp
					&& player.getItemInHand(hand).isEmpty()) {
				Feeding.ateCake(sp);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player.isCreative()) {
				return InteractionResult.PASS;
			}
			if (Carrying.isCarried(player) || (Life.of(player).started && Life.stage(player).isBaby())) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (player.isCreative() || !Life.of(player).started) {
				return true;
			}
			if (Carrying.isCarried(player)) {
				return false; // the carrier frees the grown-up's hands, not the baby's
			}
			if (state.getBlock() instanceof dev.lifestages.block.BabySafe) {
				return true; // anyone can pick up a present or a teddy bear
			}
			Stage s = Life.stage(player);
			if (s.isBaby()) {
				return false;
			}
			if (s == Stage.TODDLER) {
				return state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE) || state.is(Blocks.SNOW);
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Player player) || !Life.of(player).started) {
				return true;
			}
			Stage s = Life.stage(player);
			if (Carrying.isCarried(player) && source.getEntity() instanceof Mob) {
				return false; // mobs go after the carrier, not the baby
			}
			if (Carrying.isCarried(player) && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING))) {
				return false; // held in someone's arms: never squeezed into a wall
			}
			if (s.atMost(Stage.TODDLER) && source.is(DamageTypes.DROWN)) {
				return false; // babies float on their back instead of drowning
			}
			return !(s.atMost(Stage.TODDLER) && source.is(DamageTypeTags.IS_FALL) && amount < 2.0f);
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
				Emotes.babyCryingNear(player) ? Player.BedSleepingProblem.OTHER_PROBLEM : null);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> babyChat(sender, message.signedContent()));

		LOGGER.info("Life Stages loaded");
	}

	private static void resetState() {
		Carrying.reset();
		Emotes.reset();
		dev.lifestages.life.Family.reset();
		Feeding.reset();
		dev.lifestages.life.Ghosts.reset();
		Moms.reset();
		dev.lifestages.block.Nursery.reset();
	}

	/** Newborns and infants only babble; toddlers manage three words. */
	private static boolean babyChat(ServerPlayer sender, String text) {
		LifeData d = Life.of(sender);
		if (!d.started) {
			return true;
		}
		Stage s = d.stage();
		String name = sender.getName().getString();
		if (s.isBaby()) {
			sender.level().getServer().getPlayerList().broadcastSystemMessage(
					Component.literal("<" + name + "> *babbles*").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
			return false;
		}
		if (s == Stage.TODDLER) {
			String[] words = text.trim().split("\\s+");
			if (words.length > 3) {
				String said = String.join(" ", java.util.Arrays.copyOf(words, 3));
				sender.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("<" + name + "> " + said + "..."), false);
				return false;
			}
		}
		return true;
	}
}
