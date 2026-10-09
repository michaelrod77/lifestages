package dev.lifestages.registry;

import dev.lifestages.LifeStages;
import dev.lifestages.life.Stage;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Life Stages advancements are data files with an impossible trigger; code grants them. */
public final class ModAdvancements {
	private ModAdvancements() {
	}

	public static void award(ServerPlayer player, String name) {
		var server = player.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(LifeStages.id(name));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}

	public static void stageReached(ServerPlayer player, Stage stage) {
		String name = switch (stage) {
			case INFANT -> "hello_world";
			case TODDLER -> "first_steps";
			case PRESCHOOLER -> "big_kid_pants";
			case CHILD -> "first_day_of_school";
			case TEENAGER -> "teenage_angst";
			case YOUNG_ADULT -> "graduation";
			case ADULT -> "bills_to_pay";
			case MIDDLE_AGE -> "midlife_crisis";
			case SENIOR -> "senior_discount";
			case ELDERLY -> "golden_years";
			default -> null;
		};
		if (name != null) {
			award(player, name);
		}
	}
}
