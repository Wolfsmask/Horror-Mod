package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;

import java.util.Locale;

/**
 * How fast the story moves, all things considered: the config's own numbers, its intensity
 * preset, and whether this world is a Creator Cut.
 */
public final class Pacing {
	private Pacing() {
	}

	/** Multiplies how long each act lasts (bigger is slower). */
	public static double storyPace(OccupantConfig cfg) {
		double preset = switch (intensity(cfg)) {
			case "subtle" -> 1.4;
			case "relentless" -> 0.7;
			default -> 1.0;
		};
		return cfg.storyPace * preset * (WorldMode.creatorCut() ? 0.5 : 1.0);
	}

	/** Multiplies how often things happen (bigger is more often). */
	public static double frequency(OccupantConfig cfg) {
		double preset = switch (intensity(cfg)) {
			case "subtle" -> 0.7;
			case "relentless" -> 1.5;
			default -> 1.0;
		};
		return cfg.eventFrequency * preset * (WorldMode.creatorCut() ? 1.4 : 1.0);
	}

	/** Minutes of nothing at the start. */
	public static double graceMinutes(OccupantConfig cfg) {
		return WorldMode.creatorCut() ? Math.min(cfg.graceMinutes, 1.5) : cfg.graceMinutes;
	}

	/** Minutes between the big moments. */
	public static double minutesBetweenPeaks() {
		return WorldMode.creatorCut() ? 9.0 : 20.0;
	}

	/** How frightened they must be before a big moment is allowed. */
	public static float peakDread() {
		return WorldMode.creatorCut() ? 40f : 55f;
	}

	/**
	 * How long the quiet is felt as: in the Creator Cut a lull counts double, so something real
	 * comes sooner after one.
	 */
	public static int quietSeconds(int seconds) {
		return WorldMode.creatorCut() ? seconds * 2 : seconds;
	}

	private static String intensity(OccupantConfig cfg) {
		return cfg.intensity == null ? "normal" : cfg.intensity.toLowerCase(Locale.ROOT);
	}
}
