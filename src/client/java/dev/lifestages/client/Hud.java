package dev.lifestages.client;

import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.LifeManager;
import dev.lifestages.life.Stage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Stage and age at the top of the screen with a growth bar, nap progress, sugar rush, and an arrow toward a crying child. */
public final class Hud {
	private Hud() {
	}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		drawBabies(g, mc);
		LifeData d = Life.of(mc.player);
		if (!d.started) {
			return;
		}
		// Top of the screen, clear of the action-bar messages above the hotbar.
		int cx = g.guiWidth() / 2;
		int y = 4;
		String label;
		if (d.isGhost()) {
			label = "Ghost · " + (d.ghostTicks / 1200 + 1) + " min · B boo · K flicker · R reborn";
		} else {
			Stage s = d.stage();
			double age = d.age(ClientLife.lifeLengthMultiplier);
			label = s.label + " · " + (age < 1 ? (age < 28 / 365.0 ? (int) (age * 365) + " days" : (int) (age * 12) + " months") : "age " + (int) age);
		}
		g.centeredText(mc.font, label, cx, y, 0xFFF2E6C8);
		if (!d.isGhost() && d.stage() != Stage.ELDERLY) {
			drawGrowthBar(g, mc, d, cx, y + 12);
		}
		int line = y + (!d.isGhost() && d.stage() != Stage.ELDERLY ? 22 : 10);
		if (d.napTicks > 0) {
			int pct = d.napTicks * 100 / LifeManager.NAP_TICKS;
			g.centeredText(mc.font, "Zzz… napping " + pct + "%", cx, line, 0xFFB8D8F8);
			line += 10;
		}
		if (d.sugarTicks > 0) {
			g.centeredText(mc.font, "SUGAR RUSH " + (d.sugarTicks / 20 + 1) + "s", cx, line, 0xFFF2A2BE);
			line += 10;
		}
		if (d.rumbleTicks > 0) {
			g.centeredText(mc.font, "*rumble rumble*", cx, line, 0xFFE0B060);
			line += 10;
		}
		drawCryArrow(g, mc, line + 2);
		drawCareTips(g, mc, d, line + 14);
	}

	/**
	 * Below the crosshair: what you can do for the baby you're looking at with what's in your hand, and how to
	 * put down the one you're carrying.
	 */
	private static void drawCareTips(GuiGraphicsExtractor g, Minecraft mc, LifeData me, int carrierLine) {
		if (me.stage().hasEmotes() || mc.gui.screen() != null) {
			return; // little ones don't look after babies
		}
		int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
		net.minecraft.world.entity.Entity rider = mc.player.getFirstPassenger();
		if (rider != null) {
			int count = mc.player.getPassengers().size();
			String who = count > 1 ? "two babies" : rider.getName().getString();
			String extra = rider instanceof dev.lifestages.entity.BabyEntity ? " · crib: lay down · changing table" : " · changing table";
			String text = "Carrying " + who + " · V: put down" + extra;
			int x = Math.max(cx - mc.font.width(text) / 2, 156); // clear of the baby panel on the left
			g.text(mc.font, text, x, carrierLine, 0xFFE8D8F0);
		}
		net.minecraft.world.entity.Entity target = mc.crosshairPickEntity;
		String name;
		boolean diapers;
		boolean newborn;
		boolean npc = target instanceof dev.lifestages.entity.BabyEntity;
		if (npc) {
			var b = (dev.lifestages.entity.BabyEntity) target;
			if (b.grownUp()) {
				return;
			}
			name = b.getName().getString();
			diapers = b.stage().wearsDiaper();
			newborn = b.stage() == Stage.NEWBORN;
		} else if (target instanceof net.minecraft.world.entity.player.Player p && Life.of(p).started && Life.stage(p).atMost(Stage.TODDLER)) {
			name = p.getName().getString();
			diapers = true;
			newborn = Life.stage(p) == Stage.NEWBORN;
		} else {
			return;
		}
		var held = mc.player.getMainHandItem();
		String tip;
		if (held.is(dev.lifestages.registry.ModItems.MILK_BOTTLE)) {
			tip = "Right-click: give " + name + " the bottle";
		} else if (held.has(net.minecraft.core.component.DataComponents.FOOD)) {
			tip = newborn ? name + " is a newborn: milk only" : "Right-click: feed " + name;
		} else if (held.is(dev.lifestages.registry.ModItems.CLEAN_DIAPER)) {
			tip = diapers ? "Right-click: change " + name + "'s diaper" : name + " is out of diapers";
		} else if (npc && (held.is(dev.lifestages.registry.ModItems.RATTLE) || held.is(dev.lifestages.registry.ModBlocks.TEDDY_BEAR.asItem()))) {
			tip = "Right-click: play with " + name;
		} else if (held.isEmpty()) {
			boolean mine = npc ? mc.player.getUUID().equals(((dev.lifestages.entity.BabyEntity) target).ownerId())
					: dev.lifestages.life.Family.isParentOf(mc.player, (net.minecraft.world.entity.player.Player) target);
			tip = mine && me.stage().canCarry() ? "Right-click: pick up " + name
					: "Sneak + right-click: carry " + name + (npc ? " · Right-click: cuddle" : "");
		} else {
			tip = "Hold a milk bottle, food or a clean diaper to look after " + name;
		}
		g.centeredText(mc.font, tip, cx, y, 0xFFF2E6C8);
	}

	/**
	 * A thin bar under the stage label: how far through this stage you are, with the next stage and the time
	 * left beside it. Goals and advancements shorten the time, so the bar jumps ahead when you complete them.
	 */
	private static void drawGrowthBar(GuiGraphicsExtractor g, Minecraft mc, LifeData d, int cx, int y) {
		int required = Math.max(1, d.requiredTicks(ClientLife.lifeLengthMultiplier));
		float done = Mth.clamp(d.stageTicks / (float) required, 0f, 1f);
		int left = Math.max(0, required - d.stageTicks);
		String next = d.stage().next().label + " in " + (left >= 1200 ? (left + 1199) / 1200 + " min" : (left + 19) / 20 + " s");
		int w = 81, x0 = cx - w / 2 - mc.font.width(next) / 2 - 3;
		g.fill(x0 - 1, y - 1, x0 + w + 1, y + 4, 0xA0000000);
		g.fill(x0, y, x0 + w, y + 3, 0xFF3A4A44);
		int filled = Math.round(w * done);
		if (filled > 0) {
			g.fill(x0, y, x0 + filled, y + 3, 0xFF9ED9C0);
			g.fill(x0, y, x0 + filled, y + 1, 0xFFCFF2E2); // highlight
		}
		g.text(mc.font, next, x0 + w + 5, y - 3, 0xFFCFE8DC);
	}

	/**
	 * Top-left: your NPC babies nearby, each with a food bar, a rest bar and their diaper, plus whether they're
	 * crying, asleep or on a sugar rush.
	 */
	private static void drawBabies(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		java.util.UUID me = mc.player.getUUID();
		java.util.List<dev.lifestages.entity.BabyEntity> babies = mc.level.getEntitiesOfClass(dev.lifestages.entity.BabyEntity.class,
				mc.player.getBoundingBox().inflate(48), b -> me.equals(b.ownerId()));
		babies.sort(java.util.Comparator.comparingDouble(b -> b.distanceToSqr(mc.player)));
		int y = 4;
		for (int i = 0; i < Math.min(3, babies.size()); i++) {
			dev.lifestages.entity.BabyEntity b = babies.get(i);
			String name = b.getName().getString() + " · " + b.stage().label;
			if (b.grownUp()) {
				g.fill(2, y - 2, 150, y + 20, 0x70000000);
				g.text(mc.font, name, 5, y, 0xFFF2E6C8);
				g.text(mc.font, "All grown up!", 5, y + 10, 0xFFF2CF63);
				y += 26;
				continue;
			}
			String status = b.crying() ? "  crying!" : b.asleep() ? "  Zzz" : b.sugarRush() ? "  SUGAR RUSH" : "";
			int statusColor = b.crying() ? 0xFFFF8080 : b.asleep() ? 0xFFB8D8F8 : 0xFFF2A2BE;
			g.fill(2, y - 2, 150, y + 40, 0x70000000);
			g.text(mc.font, name, 5, y, 0xFFF2E6C8);
			g.text(mc.font, status, 5 + mc.font.width(name), y, statusColor);
			needBar(g, mc, "Food", b.hunger() / (float) dev.lifestages.entity.BabyEntity.MAX_HUNGER, 5, y + 11, 0xFFF2B05E);
			needBar(g, mc, "Rest", b.energy() / (float) dev.lifestages.entity.BabyEntity.MAX_ENERGY, 66, y + 11, 0xFF8EC3F2);
			// care: how fast they're growing (great care = twice as fast; neglect stops growth)
			int care = b.care();
			String careText = care < 0 ? "Care: needs you!" : care >= 4 ? "Care: great (x2 growth)"
					: care == 3 ? "Care: good" : care == 2 ? "Care: okay" : "Care: poor (slow growth)";
			int careColor = care < 0 ? 0xFFFF8080 : care >= 4 ? 0xFF9ED9C0 : care >= 2 ? 0xFFE0E0C0 : 0xFFE0A060;
			String diaper = b.stage().wearsDiaper() ? (b.dirty() ? "Diaper: dirty!  " : "Diaper: clean  ") : "";
			g.text(mc.font, diaper, 5, y + 20, b.dirty() ? 0xFFC8B060 : 0xFF9ED9C0);
			g.text(mc.font, careText, 5 + mc.font.width(diaper), y + 20, careColor);
			needBar(g, mc, "To " + b.stage().next().label, b.growth(), 5, y + 30, 0xFFC7B5EE, false, 50);
			y += 46;
		}
	}

	private static void needBar(GuiGraphicsExtractor g, Minecraft mc, String label, float value, int x, int y, int color) {
		needBar(g, mc, label, value, x, y, color, true, 30);
	}

	/** A label and a small bar; need bars turn red when low, the growth bar never does. */
	private static void needBar(GuiGraphicsExtractor g, Minecraft mc, String label, float value, int x, int y, int color, boolean warnLow, int w) {
		g.text(mc.font, label, x, y, 0xFFD8D8D8);
		int bx = x + mc.font.width(label) + 3;
		g.fill(bx, y + 2, bx + w, y + 6, 0xFF3A3A44);
		g.fill(bx, y + 2, bx + Math.round(w * Mth.clamp(value, 0f, 1f)), y + 6, warnLow && value < 0.3f ? 0xFFE05050 : color);
	}

	private static void drawCryArrow(GuiGraphicsExtractor g, Minecraft mc, int y) {
		if (mc.level == null || ClientLife.cryingEntityId < 0 || mc.level.getGameTime() > ClientLife.cryingUntil) {
			return;
		}
		Entity baby = mc.level.getEntity(ClientLife.cryingEntityId);
		if (baby == null) {
			return;
		}
		double dx = baby.getX() - mc.player.getX(), dz = baby.getZ() - mc.player.getZ();
		float angle = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f - mc.player.getYRot();
		angle = Mth.wrapDegrees(angle);
		String arrow = Math.abs(angle) < 30 ? "↑" : Math.abs(angle) > 150 ? "↓" : angle > 0 ? "→" : "←";
		int dist = (int) Math.sqrt(dx * dx + dz * dz);
		g.centeredText(mc.font, arrow + " " + baby.getName().getString() + " is crying (" + dist + "m) " + arrow,
				g.guiWidth() / 2, y, 0xFFF2A2BE);
	}
}
