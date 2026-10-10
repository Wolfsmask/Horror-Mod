package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Moves the body built by {@link OccupantGeometry}.
 * <p>
 * Almost all of the fear in this thing is in how little it does, and in how wrongly it does the
 * rest:
 * <ul>
 *     <li>Its legs are planted on real things, worked out by {@link LegGait}, and every joint
 *     here is solved so that each point lands exactly there. The legs do not swing; they hold,
 *     and push.</li>
 *     <li>The body is shoved along between them and leans into each shove, then hangs.</li>
 *     <li>What movement there is of its own happens only while you are looking away. Look at it
 *     and it is perfectly still, winding down to nothing rather than stopping dead; look away and
 *     back, and its head is at a different angle, and you never saw it move.</li>
 *     <li>Nothing ever jumps or stutters: what it does, it does smoothly.</li>
 *     <li>Its mouth is always open. When it is close, it opens further than a mouth goes.</li>
 *     <li>Its eyes do not move with its head. In each black eye is one tiny pale point, and
 *     wherever you are, whatever its head is doing, that point is on you.</li>
 * </ul>
 * VEILED is early in the story, when it keeps its head down and is harder to make out at a
 * distance; REVEALED is when it looks at you.
 */
final class OccupantPose {
	private static final int LEGS = OccupantGeometry.LEGS;

	private final ModelPart hips;
	private final ModelPart spine;
	private final ModelPart neck;
	private final ModelPart skull;
	/** Everything below the eyes: the cheeks, the mouth between them, the chin. */
	private final ModelPart jaw;
	private final ModelPart hair;
	private final ModelPart[] pupils;
	private final ModelPart[] upper = new ModelPart[LEGS];
	private final ModelPart[] lower = new ModelPart[LEGS];
	private final float[] upperLength = new float[LEGS];
	/** Knee to point. */
	private final float[] lowerLength = new float[LEGS];
	/**
	 * For each leg, the turn (a row-major 3x3) that takes the line from its knee to its point onto
	 * the shin's own line. The point is hooked off at an angle of its own, so the shin is aimed as
	 * though it carried straight on to its point, and then turned back by this: then the point,
	 * not the line, lands where the leg is planted.
	 */
	private final float[][] unhook = new float[LEGS][];

	OccupantPose(ModelPart root) {
		this.hips = root.getChild("hips");
		this.spine = hips.getChild("spine");
		ModelPart yoke = spine.getChild("yoke");
		this.neck = yoke.getChild("neck");
		this.skull = neck.getChild("skull");
		this.jaw = skull.getChild("jaw");
		this.hair = skull.getChild("hair");
		this.pupils = new ModelPart[]{skull.getChild("left_pupil"), skull.getChild("right_pupil")};
		for (int i = 0; i < LEGS; i++) {
			upper[i] = spine.getChild("leg" + i + "_upper");
			lower[i] = upper[i].getChild("leg" + i + "_lower");
			lower[i].getChild("leg" + i + "_claw");   // it has to be there; it is carried along
			upperLength[i] = lower[i].y;                 // the knee sits at the end of the thigh
			float tx = OccupantGeometry.LEG_TIP[i * 3], ty = OccupantGeometry.LEG_TIP[i * 3 + 1];
			float tz = OccupantGeometry.LEG_TIP[i * 3 + 2];
			lowerLength[i] = Mth.sqrt(tx * tx + ty * ty + tz * tz);
			unhook[i] = ontoY(tx / lowerLength[i], ty / lowerLength[i], tz / lowerLength[i]);
		}
	}

	/** The smallest turn that takes the unit vector (ax, ay, az) onto +y, as a row-major 3x3. */
	private static float[] ontoY(float ax, float ay, float az) {
		float s = Mth.sqrt(ax * ax + az * az);
		if (s < 1.0e-6f) return new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
		// About the axis (a x y) / |a x y| = (-az, 0, ax) / s, by the angle whose sine is s and cosine ay.
		float ux = -az / s, uz = ax / s, k = 1.0f - ay;
		return new float[]{
				1.0f + k * (ux * ux - 1.0f), -s * uz, k * ux * uz,
				s * uz, ay, -s * ux,
				k * ux * uz, s * ux, 1.0f + k * (uz * uz - 1.0f)};
	}

	/**
	 * Poses every part for this frame. The parts must already be back in their resting pose, and
	 * {@code lookX} and {@code lookY} are where the (invisible) head anchor was aimed: at you.
	 */
	void apply(OccupantRenderState state, float lookX, float lookY) {
		boolean veiled = state.form == OccupantEntity.Form.VEILED;

		// Its own clock, which stands still while it is watched. Coming for you, it no longer cares.
		float t = state.mode == OccupantEntity.Mode.CHASE ? state.ageInTicks : state.clock;

		// A drift so slow you cannot tell whether it moved or you did.
		float drift = Mth.sin(t * 0.013f);
		spine.xRot = SPINE_REST + 0.012f * drift;
		// The hair lags behind the head and settles slowly, as if it were in water.
		hair.xRot = 0.025f * Mth.sin(t * 0.021f + 1.3f);
		hair.zRot = 0.03f * Mth.sin(t * 0.017f);
		jaw.yScale = 1.0f;

		// Folded down into a space too small for it: hips low, body bent over, head held up.
		float crouch = state.crouch;
		hips.y += OccupantFit.CROUCH_DROP * crouch;
		spine.xRot += OccupantFit.CROUCH_BEND * crouch;
		neck.xRot -= OccupantFit.CROUCH_BEND * 0.75f * crouch;

		// Each shove carries it, and the body goes with it and then comes back upright.
		spine.xRot += state.leanForward * LEAN_FORWARD;
		spine.zRot -= state.leanSide * LEAN_SIDE;

		// How it is standing bends it further, and its neck back up, or down.
		spine.xRot += spineBend(state.mode, veiled);
		neck.xRot += neckBend(state.mode, veiled);

		switch (state.mode) {
			case CHASE -> chase(t, lookX, lookY);
			case AMBUSH -> loom(lookX, lookY);
			// Standing, it looks at them from its own face (see LegGait); bent to them, or coming for
			// them, its face is already brought down to them, and the game's aim is what it was made for.
			default -> stand(state.tilt, Float.isNaN(state.facePitch) ? lookX : state.facePitch, lookY, state.act);
		}
		legs(state, t);
		stare(state);
	}

	/**
	 * The pupils, on you. Whatever the head has not turned to face you, they make up for, sliding
	 * across the black towards you, so that they are always looking straight out at you, even
	 * while nothing else about it moves at all.
	 */
	private void stare(OccupantRenderState state) {
		float yaw = (float) Math.toRadians(state.watchYaw) - (neck.yRot + skull.yRot);
		float pitch = (float) Math.toRadians(state.watchPitch) - (spine.xRot + neck.xRot + skull.xRot);
		// Turning the head by an angle carries its front over by minus its sine across, and its
		// sine down: the pupils go as far as the front of the head would have, within the black.
		float dx = Mth.clamp(-Mth.sin(Mth.clamp(yaw, -1.5f, 1.5f)) * 1.1f, -0.55f, 0.55f);
		float dy = Mth.clamp(Mth.sin(Mth.clamp(pitch, -1.5f, 1.5f)) * 1.1f, -0.55f, 0.55f);
		for (ModelPart p : pupils) {
			p.x += dx;
			p.y += dy;
		}
	}

	/** How far its body is bent forward when nothing is bending it. */
	static final float SPINE_REST = 0.03f;
	/** How far a shove leans it, forward and to the side, for each block of the shove. */
	static final float LEAN_FORWARD = 0.6f;
	static final float LEAN_SIDE = 0.5f;

	/**
	 * How far the way it is standing bends its body forward, on top of folding down and its shoves:
	 * coming for you, bent into it; close enough to touch you, bent down to your height; early on,
	 * keeping its head down, which makes the shape harder to read.
	 */
	static float spineBend(OccupantEntity.Mode mode, boolean veiled) {
		return switch (mode) {
			case CHASE -> 0.35f;
			case AMBUSH -> 0.55f;
			default -> veiled ? 0.12f : 0.0f;
		};
	}

	/** And how far its neck bends forward with that (back, below nothing), before it looks at you. */
	static float neckBend(OccupantEntity.Mode mode, boolean veiled) {
		return switch (mode) {
			case CHASE -> -0.4f;
			case AMBUSH -> -0.35f;
			default -> veiled ? 0.3f : -0.05f;
		};
	}

	/** Standing. The head follows you a beat late and a little too far. */
	private void stand(float tilt, float lookX, float lookY, int act) {
		// The neck carries most of the turn, so the body stays squarely facing wherever it was.
		neck.yRot = lookY * 0.45f;
		skull.yRot = lookY * 0.55f;
		neck.xRot += lookX * 0.3f;
		skull.xRot = lookX * 0.6f;

		// Every so often, while you were not looking, the head has gone somewhere else, tilted,
		// and stays there. The neck takes some of it, so the whole face goes over together.
		float lean = 0.0f;
		if (tilt > 0.45f) {
			lean = 0.34f * (tilt - 0.45f) / 0.55f;
		} else if (tilt < -0.75f) {
			lean = -0.42f;                          // well over to one side, and held there
		}
		// The further the story has gone, the further over its head goes: it no longer pretends.
		lean *= 1.0f + 0.3f * Math.max(0, Math.min(3, act - 1));
		neck.zRot = lean * 0.35f;
		skull.zRot = lean * 0.65f;
	}

	/** Close enough to touch you. It bends down to your height, and the mouth opens. */
	private void loom(float lookX, float lookY) {
		jaw.yScale = 1.3f;                          // the face pulls longer, around the mouth
		neck.xRot += lookX * 0.3f;
		skull.xRot = 0.45f + lookX * 0.4f;
		skull.yRot = lookY * 0.5f;
	}

	/** Coming for you. Bent forward into it, face first, mouth working. */
	private void chase(float t, float lookX, float lookY) {
		jaw.yScale = 1.25f + 0.08f * Mth.sin(t * 0.9f);
		skull.xRot = 0.3f + lookX * 0.3f;
		skull.yRot = lookY * 0.3f;
	}

	/**
	 * Every planted leg is solved so its point lands on its hold: thigh and shin as two bones, the
	 * joint pushed out sideways like an elbow, the way an arm braces against a wall to shove off
	 * it, never peaked up over the body like a spider's knee. A leg with nothing to hold hangs
	 * half folded and slowly feels about.
	 * <p>
	 * The legs leave the body all the way up the trunk, so they ride on the spine as it bends and
	 * leans; each hold is brought into the spine's own frame before solving.
	 */
	private void legs(OccupantRenderState state, float t) {
		// The spine's rotation, undone in reverse order: Z, then Y, then X.
		float cxr = Mth.cos(-spine.xRot), sxr = Mth.sin(-spine.xRot);
		float cyr = Mth.cos(-spine.yRot), syr = Mth.sin(-spine.yRot);
		float czr = Mth.cos(-spine.zRot), szr = Mth.sin(-spine.zRot);
		for (int i = 0; i < LEGS; i++) {
			float px = upper[i].x, py = upper[i].y, pz = upper[i].z;
			float a = OccupantGeometry.LEG_ANGLE[i];
			float ox = Mth.sin(a), oz = Mth.cos(a);
			float tx, ty, tz;
			if (state.legPlanted[i]) {
				// Model space, to the hips (which never turn), to the spine.
				float x = state.legTarget[i * 3] - hips.x - spine.x;
				float y = state.legTarget[i * 3 + 1] - hips.y - spine.y;
				float z = state.legTarget[i * 3 + 2] - hips.z - spine.z;
				float x1 = x * czr - y * szr, y1 = x * szr + y * czr;          // undo Z
				float x2 = x1 * cyr + z * syr, z2 = -x1 * syr + z * cyr;      // undo Y
				float y3 = y1 * cxr - z2 * sxr, z3 = y1 * sxr + z2 * cxr;     // undo X
				tx = x2;
				ty = y3;
				tz = z3;
			} else {
				// Free: hanging down beside the body, long and limp, slowly feeling about.
				float feel = Mth.sin(t * 0.031f + i * 1.7f);
				float reach = (upperLength[i] + lowerLength[i]) * (0.8f + 0.08f * feel);
				tx = px + ox * reach * (0.3f + 0.06f * Mth.sin(t * 0.023f + i));
				ty = py + reach * 0.88f;
				tz = pz + oz * reach * 0.3f;
			}
			// The joint goes out the way the leg points, and only a little up, unless that would put it
			// into something: then the way the gait found clear, brought into the spine's frame.
			float kx = ox, ky = -0.3f, kz = oz;
			if (state.legBendSet[i]) {
				float x = state.legBend[i * 3], y = state.legBend[i * 3 + 1], z = state.legBend[i * 3 + 2];
				float x1 = x * czr - y * szr, y1 = x * szr + y * czr;
				float x2 = x1 * cyr + z * syr, z2 = -x1 * syr + z * cyr;
				kx = x2;
				ky = y1 * cxr - z2 * sxr;
				kz = y1 * sxr + z2 * cxr;
			}
			if (!solve(i, px, py, pz, tx, ty, tz, kx, ky, kz)) {
				upper[i].xRot = 0.0f;
				upper[i].yRot = 0.0f;
				lower[i].xRot = 0.0f;
				lower[i].yRot = 0.0f;
				lower[i].zRot = 0.0f;
			}
		}
	}

	/**
	 * Two-bone reach from the hip (px, py, pz) to (tx, ty, tz), all in the hips' frame, with the
	 * knee bent towards (kx, ky, kz). Each bone hangs along +y at rest and is turned by X then Y,
	 * which is the order ModelPart applies them in (Z then Y then X, with Z left at zero). The
	 * thigh is aimed along its own line at the knee; the shin so that its point lands on the target.
	 */
	private boolean solve(int i, float px, float py, float pz, float tx, float ty, float tz,
						  float kx, float ky, float kz) {
		float l1 = upperLength[i];
		float l2 = lowerLength[i];
		float dx = tx - px, dy = ty - py, dz = tz - pz;
		float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(d > 1.0e-3f) || !Float.isFinite(d)) return false;
		float ux = dx / d, uy = dy / d, uz = dz / d;
		d = Mth.clamp(d, Math.abs(l1 - l2) + 0.05f, l1 + l2 - 0.05f);

		// How far along the line the knee sits, and how far off it.
		float along = (l1 * l1 - l2 * l2 + d * d) / (2.0f * d);
		float off = Mth.sqrt(Math.max(0.0f, l1 * l1 - along * along));
		// The bend direction, made square to the line.
		float dot = kx * ux + ky * uy + kz * uz;
		float nx = kx - ux * dot, ny = ky - uy * dot, nz = kz - uz * dot;
		float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (nl < 1.0e-4f) {
			nx = -uz; ny = 0.0f; nz = ux;           // any square direction will do
			nl = Mth.sqrt(nx * nx + nz * nz);
			if (nl < 1.0e-4f) { nx = 1.0f; nz = 0.0f; nl = 1.0f; }
		}
		nx /= nl; ny /= nl; nz /= nl;

		float kneeX = ux * along + nx * off;
		float kneeY = uy * along + ny * off;
		float kneeZ = uz * along + nz * off;
		// Thigh direction, from the hip.
		float ax = kneeX / l1, ay = kneeY / l1, az = kneeZ / l1;
		// Shin direction, from the knee to the point.
		float bx = (ux * d - kneeX) / l2, by = (uy * d - kneeY) / l2, bz = (uz * d - kneeZ) / l2;

		float x1 = (float) Math.acos(Mth.clamp(ay, -1.0f, 1.0f));
		float y1 = (float) Mth.atan2(ax, az);
		// The shin's direction in the thigh's own frame: undo the thigh's Y, then its X.
		float cy = Mth.cos(y1), sy = Mth.sin(y1);
		float vx = bx * cy - bz * sy;
		float vz = bx * sy + bz * cy;
		float cx = Mth.cos(x1), sx = Mth.sin(x1);
		float wy = by * cx + vz * sx;
		float wz = -by * sx + vz * cx;
		float x2 = (float) Math.acos(Mth.clamp(wy, -1.0f, 1.0f));
		float y2 = (float) Mth.atan2(vx, wz);

		// That aims the shin's own line at the target. Its point is off that line, so the shin is
		// turned back by the hook first: (Y by y2, then X by x2) times unhook, as Z, Y, X angles.
		float cx2 = Mth.cos(x2), sx2 = Mth.sin(x2), cy2 = Mth.cos(y2), sy2 = Mth.sin(y2);
		float[] u = unhook[i];
		float m00 = cy2 * u[0] + sy2 * sx2 * u[3] + sy2 * cx2 * u[6];
		float m10 = cx2 * u[3] - sx2 * u[6];
		float m20 = -sy2 * u[0] + cy2 * sx2 * u[3] + cy2 * cx2 * u[6];
		float m21 = -sy2 * u[1] + cy2 * sx2 * u[4] + cy2 * cx2 * u[7];
		float m22 = -sy2 * u[2] + cy2 * sx2 * u[5] + cy2 * cx2 * u[8];
		float lx = (float) Mth.atan2(m21, m22);
		float ly = (float) Math.asin(Mth.clamp(-m20, -1.0f, 1.0f));
		float lz = (float) Mth.atan2(m10, m00);

		if (!Float.isFinite(x1 + y1 + lx + ly + lz)) return false;
		upper[i].xRot = x1;
		upper[i].yRot = y1;
		upper[i].zRot = 0.0f;
		lower[i].xRot = lx;
		lower[i].yRot = ly;
		lower[i].zRot = lz;
		return true;
	}
}
