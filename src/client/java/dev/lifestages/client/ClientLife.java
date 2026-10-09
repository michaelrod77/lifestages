package dev.lifestages.client;

/** Client-side copies of server state: settings and the crying-child alert. */
public final class ClientLife {
	public static double lifeLengthMultiplier = 1.0;
	public static int carryAgeLimit = 10;
	public static int cryingEntityId = -1;
	public static long cryingUntil;

	private ClientLife() {
	}
}
