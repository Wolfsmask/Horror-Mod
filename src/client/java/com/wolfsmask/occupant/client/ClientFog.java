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
}
