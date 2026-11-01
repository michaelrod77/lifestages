package dev.lifestages.client;

/** Client-side copies of server state: settings and the crying-child alert. */
public final class ClientLife {
	/** The local pet's sprint burst, mirrored from the server's rules for the dash meter. */
	public static int dash = dev.lifestages.life.Pets.DASH_TICKS;
	/** Today's holiday (0 none) and its line for the top of the screen. */
	public static int holiday;
	public static String holidayLine = "";
	/** The costume picker waits for any open screen to close. */
	public static boolean costumePending;
	public static double lifeLengthMultiplier = 1.0;
	public static int carryAgeLimit = 10;
	public static int cryingEntityId = -1;
	public static long cryingUntil;

	private ClientLife() {
	}
}
