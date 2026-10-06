package com.wolfsmask.occupant.util;

/**
 * The shape of the fog, the same on both sides. It is a real fog: it begins a few blocks from
 * you and thickens all the way out to its end, where nothing at all can be seen. Halfway out, a
 * thing is a grey shape; further, it is gone.
 * <p>
 * The server uses this to put the Occupant where it can still just be made out: in the fog, a
 * shape, never so far in that it is lost.
 */
public final class FogLine {
	/** The fog is never closer than this, whatever asks for it. */
	public static final float NEAREST = 24.0f;

	private FogLine() {
	}

	/** Where the fog begins, for a fog that is thick at {@code end} blocks: almost at your feet. */
	public static float start(float end) {
		return Math.max(3.0f, end * 0.06f);
	}

	/** Nearest edge of the band where something far off stands: in the fog, a shape. */
	public static double edgeNear(float end) {
		return end * 0.40;
	}

	/** Far edge of that band: past this, more than half lost; it is never put further. */
	public static double edgeFar(float end) {
		return end * 0.58;
	}
}
