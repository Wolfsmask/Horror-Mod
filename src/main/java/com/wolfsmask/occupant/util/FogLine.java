package com.wolfsmask.occupant.util;

/**
 * The shape of the fog, the same on both sides: given where it is thick enough to hide anything
 * (its end), where it begins. Between the two the world fades out.
 * <p>
 * The server uses this to put the Occupant just this side of where the fog begins: seen whole,
 * at the very edge of what can be seen, never half lost in it.
 */
public final class FogLine {
	/** The fog is never closer than this, whatever asks for it. */
	public static final float NEAREST = 40.0f;

	private FogLine() {
	}

	/** Where the fog begins, for a fog that is thick at {@code end} blocks. */
	public static float start(float end) {
		return Math.max(12.0f, end - Math.max(16.0f, end * 0.35f));
	}

	/** Nearest edge of the band just inside the fog, where something can stand and be seen whole. */
	public static double edgeNear(float end) {
		return start(end) - 10.0;
	}

	/** Far edge of that band: still a block short of where the fog begins. */
	public static double edgeFar(float end) {
		return start(end) - 1.0;
	}
}
