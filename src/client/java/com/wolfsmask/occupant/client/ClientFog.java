package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.util.FogLine;

/**
 * The fog the story lays over the world. The server says where it should be thick; here it is
 * eased there, so it rolls in and drifts out instead of snapping. The fog renderer asks for it
 * every frame and keeps whichever fog is nearer, the game's or this.
 */
public final class ClientFog {
	/** Further than anything is ever drawn: no fog. */
	private static final float NONE = 1024.0f;
	/** Where a fog that is coming in starts from, rather than from nowhere. */
	private static final float ROLLS_FROM = 320.0f;

	private static float target = NONE;
	private static float current = NONE;
	private static float step;

	private ClientFog() {
	}

	/** Thick at {@code end} blocks (0 for none), getting there over {@code ticks}. */
	public static void set(float end, int ticks) {
		target = end <= 0 ? NONE : Math.max(FogLine.NEAREST, end);
		if (current >= NONE && target < NONE) current = Math.max(target, ROLLS_FROM);
		step = Math.abs(target - current) / Math.max(1, ticks);
	}

	static void tick() {
		if (current < target) current = Math.min(target, current + step);
		else if (current > target) current = Math.max(target, current - step);
		if (current >= ROLLS_FROM && target >= NONE) current = NONE;     // gone, once it is past seeing
	}

	static void reset() {
		target = NONE;
		current = NONE;
	}

	/** Is there any fog to draw? */
	public static boolean active() {
		return current < NONE && ClientConfig.get().fog;
	}

	/** Where nothing can be seen through it, in blocks. */
	public static float end() {
		return current;
	}

	/** Where it begins. */
	public static float start() {
		return FogLine.start(current);
	}

	/**
	 * How heavy it is, 0 (none, or far off) to 1 (close round you): how far the sky is drawn into
	 * it, how grey it is, and how little of a sunset gets through.
	 */
	public static float strength() {
		if (!active()) return 0.0f;
		return Math.max(0.0f, Math.min(1.0f, (240.0f - current) / (240.0f - FogLine.NEAREST)));
	}

	/** Where the sky is lost in it: a little further than the land, so overhead stays sky. */
	public static float skyEnd() {
		return current * 1.6f;
	}

	/**
	 * A fog colour made into fog: the game's own colour for the sky at this moment, drained
	 * towards a cold grey as the fog gets heavier. Daylight fog is no longer blue haze, and at
	 * sunset it is not a band of orange over grey land. In place, on {@code rgb} (0 to 1).
	 */
	public static void grey(float[] rgb) {
		float s = strength() * 0.7f;
		if (s <= 0) return;
		float lum = 0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2];
		float[] cold = {lum * 0.94f, lum * 0.97f, lum * 1.02f};
		for (int i = 0; i < 3; i++) rgb[i] = Math.min(1.0f, rgb[i] + (cold[i] - rgb[i]) * s);
	}

	/** How much of the sunrise and sunset glow comes through, 0 to 1. */
	public static float sunsetLeft() {
		return 1.0f - 0.85f * strength();
	}
}
