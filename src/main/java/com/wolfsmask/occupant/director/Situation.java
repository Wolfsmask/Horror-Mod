package com.wolfsmask.occupant.director;

/**
 * A snapshot of the player's circumstances, taken once per second. Events read this to decide
 * whether they would work right now: a cave noise needs a cave, a knock needs a house at night.
 *
 * @param night       night-time or a thunderstorm
 * @param dark        the player is standing in low light
 * @param underground no sky above and well below the surface
 * @param sheltered   under a roof but not underground (probably indoors)
 * @param alone       no other player nearby
 * @param still       barely moving
 * @param sprinting   sprinting
 * @param inCombat    hit something or got hit in the last few seconds
 * @param inWater     touching water
 * @param busy        in a menu, asleep, riding, or flying with elytra
 * @param idleSeconds seconds without moving or turning (AFK detection)
 */
public record Situation(boolean night, boolean dark, boolean underground, boolean sheltered, boolean alone,
						boolean still, boolean sprinting, boolean inCombat, boolean inWater, boolean busy,
						int light, int idleSeconds) {
	public boolean afk() {
		return idleSeconds >= 120;
	}

	/** Any kind of darkness the Occupant can hide in. */
	public boolean gloomy() {
		return night || dark || underground;
	}
}
