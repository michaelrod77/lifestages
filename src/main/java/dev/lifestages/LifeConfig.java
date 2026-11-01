package dev.lifestages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Server settings, read from config/lifestages.json. Missing keys keep their defaults. */
public final class LifeConfig {
	public double lifeLengthMultiplier = 1.0;
	/** full, mild or off */
	public String grossness = "full";
	public int maxParentsPerChild = 2;
	public int maxChildrenPerParent = 4;
	public int carryAgeLimit = 10;
	public boolean npcMomEnabled = true;
	/** The NPC Mom builds a cottage near spawn on a baby's first life. */
	public boolean momBuildsHouse = true;
	public int npcMomArrivalSeconds = 60;
	public int ghostMaxMinutes = 10;
	public int willChestLockMinutes = 30;
	public int elderlyLifespanMinMinutes = 20;
	public int elderlyLifespanMaxMinutes = 25;
	public double sugarRushSpeed = 2.5;
	public int sugarRushSeconds = 30;
	/** Easter, Halloween or Christmas every few days (this many days apart, chosen at random in the range). */
	public boolean holidaysEnabled = true;
	public int holidayMinDays = 3;
	public int holidayMaxDays = 5;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LifeConfig instance = new LifeConfig();

	public static LifeConfig get() {
		return instance;
	}

	public boolean gross() {
		return "full".equalsIgnoreCase(grossness);
	}

	public boolean mild() {
		return "mild".equalsIgnoreCase(grossness);
	}

	/** Minutes scaled by the life length multiplier, in ticks. */
	public int ticks(double minutes) {
		return (int) Math.round(minutes * 60 * 20 * lifeLengthMultiplier);
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("lifestages.json");
		try {
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					LifeConfig read = GSON.fromJson(r, LifeConfig.class);
					if (read != null) {
						instance = read;
					}
				}
			}
			try (Writer w = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, w);
			}
		} catch (IOException | RuntimeException e) {
			LifeStages.LOGGER.error("Could not read {}; using defaults", path, e);
		}
	}
}
