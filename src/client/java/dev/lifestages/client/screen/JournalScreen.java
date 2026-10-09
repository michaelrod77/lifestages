package dev.lifestages.client.screen;

import dev.lifestages.client.ClientLife;
import dev.lifestages.life.Life;
import dev.lifestages.life.LifeData;
import dev.lifestages.life.Stage;
import dev.lifestages.net.Payloads;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** The Growth Journal: stage, age, goal progress, family and settings. */
public class JournalScreen extends Screen {
	public JournalScreen() {
		super(Component.literal("Growth Journal"));
	}

	private LifeData data() {
		return Life.of(minecraft.player);
	}

	@Override
	protected void init() {
		LifeData d = data();
		int y = height / 2 + 70;
		if (d.stage().atMost(Stage.CHILD)) {
			addRenderableWidget(Button.builder(carryLabel(d.allowCarry), b -> {
				boolean on = !data().allowCarry;
				ClientPlayNetworking.send(new Payloads.Act(on ? Payloads.Action.ALLOW_CARRY_ON : Payloads.Action.ALLOW_CARRY_OFF, 0));
				b.setMessage(carryLabel(on));
			}).bounds(width / 2 - 100, y, 200, 20).build());
		}
		if (d.stage().canParent()) {
			addRenderableWidget(Button.builder(lookingLabel(d.lookingForBaby), b -> {
				boolean on = !data().lookingForBaby;
				ClientPlayNetworking.send(new Payloads.Act(on ? Payloads.Action.LOOKING_ON : Payloads.Action.LOOKING_OFF, 0));
				b.setMessage(lookingLabel(on));
			}).bounds(width / 2 - 100, y, 200, 20).build());
		}
	}

	private static Component carryLabel(boolean on) {
		return Component.literal("Others can carry me: " + (on ? "On" : "Off"));
	}

	private static Component lookingLabel(boolean on) {
		return Component.literal("Looking for a baby: " + (on ? "On" : "Off"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		LifeData d = data();
		Stage s = d.stage();
		int x = width / 2 - 110, y = height / 2 - 100;
		g.fill(x - 10, y - 10, x + 230, height / 2 + 62, 0xC0101018);
		double age = d.age(ClientLife.lifeLengthMultiplier);
		g.text(font, Component.literal(s.label).withStyle(net.minecraft.ChatFormatting.GOLD), x, y, 0xFFFFFFFF);
		g.text(font, ageText(age), x + 110, y, 0xFFD0D0DA);
		y += 16;
		if (s == Stage.ELDERLY) {
			g.text(font, "Your time is running out. Golden foods add a little.", x, y, 0xFFD0D0DA);
		} else {
			int minTicks = d.requiredTicks(ClientLife.lifeLengthMultiplier);
			bar(g, x, y, "Grow up", d.stageTicks, minTicks, (Math.max(0, minTicks - d.stageTicks) / 1200) + " min left");
			y += 12;
			g.text(font, "Goals done: " + (int) Math.round(d.goalProgress() * 100) + "% (each one makes you grow faster)", x, y, 0xFF9AA0B0);
			y += 14;
			if (s.milk > 0) {
				bar(g, x, y, "Milk", d.milk, s.milk, d.milk + " / " + s.milk + " bottles");
				y += 14;
			}
			if (s.food > 0) {
				bar(g, x, y, "Food", d.food, s.food, d.food + " / " + s.food + " points");
				y += 14;
			}
			if (s.naps > 0) {
				bar(g, x, y, "Naps", d.naps, s.naps, d.naps + " / " + s.naps);
				y += 14;
			}
			if (s.nights > 0 || s.napOrNight) {
				int need = s.napOrNight ? 1 : s.nights;
				int have = s.napOrNight ? d.naps + d.nights : d.nights;
				bar(g, x, y, "Nights", have, need, have + " / " + need + (s.napOrNight ? " (a nap counts)" : ""));
				y += 14;
			}
		}
		y += 6;
		if (s.wearsDiaper()) {
			g.text(font, "Diaper: " + (!d.diaperOn ? "none" : d.diaperDirty ? "dirty!" : "clean"), x, y, d.diaperDirty ? 0xFFE0B060 : 0xFFD0D0DA);
			y += 12;
		}
		if (d.sugarTicks > 0) {
			g.text(font, "Sugar rush: " + d.sugarTicks / 20 + " s", x, y, 0xFFF2A2BE);
			y += 12;
		}
		g.text(font, "Parents: " + names(d.parents), x, y, 0xFFD0D0DA);
		y += 12;
		g.text(font, "Children: " + names(d.children), x, y, 0xFFD0D0DA);
		y += 16;
		String tip = tip(d);
		if (tip != null) {
			g.textWithWordWrap(font, Component.literal(tip), x, y, 220, 0xFFB8D8F8);
		}
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, String label, int have, int need, String text) {
		g.text(font, label, x, y, 0xFFB8B8C8);
		int bx = x + 50, bw = 80;
		float t = need <= 0 ? 1 : Math.min(1f, (float) have / need);
		g.fill(bx, y + 1, bx + bw, y + 8, 0xFF5A6070);
		g.fill(bx, y + 1, bx + (int) (bw * t), y + 8, t >= 1 ? 0xFF7FD08A : 0xFF7FB8E6);
		g.text(font, text, bx + bw + 6, y, 0xFFD0D0DA);
	}

	private String names(List<UUID> ids) {
		if (ids.isEmpty()) {
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (UUID id : ids) {
			PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
			sb.append(sb.length() > 0 ? ", " : "").append(info != null ? info.getProfile().name() : "(offline)");
		}
		return sb.toString();
	}

	private static String tip(LifeData d) {
		Stage s = d.stage();
		if (s.isNewborn()) {
			return "Tip: Mom feeds you a bottle about every 1-2 minutes and rocks you to sleep. Stay still while napping. Cry (Z) if you need her sooner.";
		}
		if (s == Stage.INFANT) {
			return "Tip: You can crawl now and eat real food. Bottles still count for your milk goal; nap in Mom's arms or a crib.";
		}
		if (s == Stage.TODDLER) {
			return "Tip: Toddlers eat just about anything. Sweets give a sugar rush!";
		}
		if (s == Stage.PRESCHOOLER) {
			return "Tip: Eat anything now, take a nap, and sleep in a bed one night.";
		}
		if (s.atLeast(Stage.CHILD) && s.atMost(Stage.SENIOR)) {
			return "Tip: Eat well and sleep through a night in a bed to grow up.";
		}
		return null;
	}

	static String ageText(double age) {
		if (age < 1.0) {
			int months = (int) (age * 12);
			return months < 1 ? (int) (age * 365) + " days old" : months + " months old";
		}
		return "Age " + (int) age;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
