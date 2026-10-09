package com.wolfsmask.occupant.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * How big to draw it, and how far it folds, so that none of it goes into a wall or a ceiling.
 * Shared by every Minecraft version's renderer.
 * <p>
 * It keeps its full size if it can by folding down, hips low and body bent over, legs braced out
 * to the walls. But bent over, its top half reaches forward, so that has to be clear too; only if
 * folding cannot make it fit does it get smaller, and never down to a person's size.
 */
final class OccupantFit {
	/**
	 * Its height in blocks, standing up, close to. Seventeen feet, near enough. It does not fit
	 * in most of the places the story puts it, so it folds down into them rather than shrinking.
	 */
	static final float NEAR_BLOCKS = 4.4f;
	/**
	 * Far away there is nothing beside it to measure it against, and it reads as much larger:
	 * a shape standing above the treeline. Nobody ever sees both at once, which is the point.
	 */
	static final float FAR_BLOCKS = 5.6f;
	/** However cramped the room, it is never allowed to look like a person. */
	static final float MIN_BLOCKS = 2.6f;
	private static final float NEAR_DISTANCE = 28.0f;
	private static final float FAR_DISTANCE = 64.0f;
	/** How far the hips drop, in model pixels, and how far the body bends, when fully folded. */
	static final float CROUCH_DROP = 26.0f;
	static final float CROUCH_BEND = 0.95f;
	/** Half the width of what is drawn above the hips, in blocks, with a little to spare. */
	private static final double HALF_WIDTH = 0.3;

	private OccupantFit() {
	}

	/** Works out {@code state.crouch} and {@code state.occupantScale} for it standing at (x, y, z). */
	static void fit(Level level, OccupantRenderState state, double x, double y, double z) {
		state.headroom = headroom(level, x, y, z);
		float distance = (float) Math.sqrt(state.distanceToCameraSq);
		float far = Mth.clamp((distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 0.0f, 1.0f);
		float blocks = Mth.lerp(far, NEAR_BLOCKS, FAR_BLOCKS);
		// It grows with every act: by the end it is a quarter again as tall as when it was first seen.
		blocks *= 1.0f + 0.08f * Mth.clamp(com.wolfsmask.occupant.client.PauseLines.act() - 1, 0, 3);
		float room = state.headroom - 0.15f;
		double yaw = Math.toRadians(state.bodyRot);

		float crouch = 0.0f;
		for (int tries = 0; ; tries++) {
			crouch = crouchFor(blocks, room);
			float tall = blocks * foldedHeight(crouch);
			if (tall > room) blocks *= room / tall;
			blocks = Math.max(blocks, MIN_BLOCKS);
			if (tries >= 10 || blocks <= MIN_BLOCKS || clear(level, x, y, z, yaw, blocks, crouch)) break;
			blocks = Math.max(MIN_BLOCKS, blocks * 0.9f);
		}
		state.crouch = crouch;
		state.occupantScale = blocks * 16.0f / OccupantGeometry.HEIGHT;
	}

	/** The least it has to fold, at this size, to fit under {@code room}. */
	private static float crouchFor(float blocks, float room) {
		float crouch = 0.0f;
		while (crouch < 1.0f && blocks * foldedHeight(crouch) > room) crouch += 0.05f;
		return Math.min(crouch, 1.0f);
	}

	/** Its height when folded by {@code crouch}, as a fraction of its full height. */
	static float foldedHeight(float crouch) {
		float hips = OccupantGeometry.HIPS_HEIGHT - CROUCH_DROP * crouch;
		float above = OccupantGeometry.HEIGHT - OccupantGeometry.HIPS_HEIGHT;
		return (hips + above * Mth.cos(CROUCH_BEND * crouch)) / OccupantGeometry.HEIGHT;
	}

	/**
	 * Whether everything from its hips to the top of its head is in open air, bent over as it is,
	 * leaning a little further than it ever does (its shoves lean it, and it looks round).
	 */
	private static boolean clear(Level level, double x, double y, double z, double yaw, float blocks, float crouch) {
		double perPixel = blocks / OccupantGeometry.HEIGHT;
		double hips = (OccupantGeometry.HIPS_HEIGHT - CROUCH_DROP * crouch) * perPixel;
		double length = (OccupantGeometry.HEIGHT - OccupantGeometry.HIPS_HEIGHT) * perPixel;
		double bend = CROUCH_BEND * crouch + 0.15;
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // the way it faces
		double rx = -fz, rz = fx;                             // to its right
		for (double s = 0.0; s <= 1.0001; s += 0.125) {
			double up = hips + s * length * Math.cos(bend);
			double ahead = s * length * Math.sin(bend);
			for (int side = -1; side <= 1; side++) {
				Vec3 p = new Vec3(x + fx * ahead + rx * side * HALF_WIDTH, y + up, z + fz * ahead + rz * side * HALF_WIDTH);
				if (LegGait.solidAt(level, p)) return false;
			}
		}
		return true;
	}

	/**
	 * How many blocks of clear space it has to stand up in, across its whole footprint, not just
	 * straight above its feet: its head is not exactly over them.
	 */
	private static float headroom(Level level, double x, double y, double z) {
		float least = 64.0f;
		for (double[] o : new double[][]{{0, 0}, {HALF_WIDTH, 0}, {-HALF_WIDTH, 0}, {0, HALF_WIDTH}, {0, -HALF_WIDTH}}) {
			BlockPos feet = BlockPos.containing(x + o[0], y + 0.01, z + o[1]);
			for (int i = 0; i < Mth.ceil(FAR_BLOCKS); i++) {
				BlockPos p = feet.above(i);
				if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
					least = Math.min(least, i);
					break;
				}
			}
		}
		return least;
	}
}
