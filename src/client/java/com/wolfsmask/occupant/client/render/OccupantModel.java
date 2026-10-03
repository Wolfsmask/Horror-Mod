package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
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
 *     <li>What movement there is of its own arrives between frames. It holds a pose, then it is
 *     in the next one, the way a thing looks in photographs taken a second apart.</li>
 *     <li>Its mouth is always open. When it is close, it opens further than a mouth goes.</li>
 * </ul>
 * VEILED is early in the story, when it keeps its head down and is harder to make out at a
 * distance; REVEALED is when it looks at you.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	private static final int LEGS = OccupantGeometry.LEGS;

	private final ModelPart hips;
	private final ModelPart spine;
	private final ModelPart neck;
	private final ModelPart skull;
	/** Everything below the eyes: the cheeks, the mouth between them, the chin. */
	private final ModelPart jaw;
	private final ModelPart hair;
	private final ModelPart[] upper = new ModelPart[LEGS];
	private final ModelPart[] lower = new ModelPart[LEGS];
	private final float[] upperLength = new float[LEGS];
	/** Shin and point together: the point carries straight on from the shin. */
	private final float[] lowerLength = new float[LEGS];

	public OccupantModel(ModelPart root) {
		super(root);
		this.hips = root.getChild("hips");
		this.spine = hips.getChild("spine");
		ModelPart yoke = spine.getChild("yoke");
		this.neck = yoke.getChild("neck");
		this.skull = neck.getChild("skull");
		this.jaw = skull.getChild("jaw");
		this.hair = skull.getChild("hair");
		for (int i = 0; i < LEGS; i++) {
			upper[i] = spine.getChild("leg" + i + "_upper");
			lower[i] = upper[i].getChild("leg" + i + "_lower");
			lower[i].getChild("leg" + i + "_claw");   // it has to be there; it is carried along
			upperLength[i] = lower[i].y;                 // the knee sits at the end of the thigh
			lowerLength[i] = OccupantGeometry.LEG_LENGTH[i] - upperLength[i];
		}
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state); // resets every part, and aims the (invisible) head anchor
		float lookX = head.xRot;
		float lookY = head.yRot;
		boolean veiled = state.form == OccupantEntity.Form.VEILED;

		// Poses hold and then change. Nothing eases: easing is what living things do.
		float step = state.mode == OccupantEntity.Mode.CHASE ? 2.0f : 8.0f;
		float t = Mth.floor(state.ageInTicks / step) * step;

		// A drift so slow you cannot tell whether it moved or you did.
		float drift = Mth.sin(t * 0.013f);
		spine.xRot = 0.03f + 0.012f * drift;
		// The hair lags behind the head and settles slowly, as if it were in water.
		hair.xRot = 0.025f * Mth.sin(t * 0.021f + 1.3f);
		hair.zRot = 0.03f * Mth.sin(t * 0.017f);
		jaw.yScale = 1.0f;

		// Folded down into a space too small for it: hips low, body bent over, head held up.
		float crouch = state.crouch;
		hips.y += OccupantRenderer.CROUCH_DROP * crouch;
		spine.xRot += OccupantRenderer.CROUCH_BEND * crouch;
		neck.xRot -= OccupantRenderer.CROUCH_BEND * 0.75f * crouch;

		// Each shove carries it, and the body goes with it and then comes back upright.
		spine.xRot += state.leanForward * 0.6f;
		spine.zRot -= state.leanSide * 0.5f;

		switch (state.mode) {
			case CHASE -> chase(t, lookX, lookY);
			case AMBUSH -> loom(lookX, lookY);
			default -> stand(state.seed, t, lookX, lookY, veiled);
		}
		legs(state, t);
	}

	/** Standing. The head follows you a beat late and a little too far. */
	private void stand(int seed, float t, float lookX, float lookY, boolean veiled) {
		// The neck carries most of the turn, so the body stays squarely facing wherever it was.
		neck.yRot = lookY * 0.45f;
		skull.yRot = lookY * 0.55f;
		neck.xRot += lookX * 0.3f - 0.05f;
		skull.xRot = lookX * 0.6f;

		// Every so often the head is simply somewhere else, tilted, and stays there a while.
		float tilt = hold(seed, t, 240, 3);
		if (tilt > 0.45f) {
			skull.zRot = 0.5f * (tilt - 0.45f) / 0.55f;
		} else if (tilt < -0.75f) {
			skull.zRot = -0.7f;                     // right over onto its shoulder
		}
		if (veiled) {
			// Early on it keeps its head down, which makes the shape harder to read.
			spine.xRot += 0.12f;
			neck.xRot += 0.35f;
		}
	}

	/** Close enough to touch you. It bends down to your height, and the mouth opens. */
	private void loom(float lookX, float lookY) {
		jaw.yScale = 1.3f;                          // the face pulls longer, around the mouth
		spine.xRot += 0.55f;
		neck.xRot += -0.35f + lookX * 0.3f;
		skull.xRot = 0.45f + lookX * 0.4f;
		skull.yRot = lookY * 0.5f;
	}

	/** Coming for you. Bent forward into it, face first, mouth working. */
	private void chase(float t, float lookX, float lookY) {
		jaw.yScale = 1.25f + 0.08f * Mth.sin(t * 0.9f);
		spine.xRot += 0.35f;
		neck.xRot += -0.4f;
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
				// Free: out to the side and down, folded, slowly feeling about.
				float feel = Mth.sin(t * 0.031f + i * 1.7f);
				float reach = (upperLength[i] + lowerLength[i]) * (0.55f + 0.1f * feel);
				tx = px + ox * reach * 0.55f;
				ty = py + reach * (0.45f + 0.12f * Mth.sin(t * 0.023f + i));
				tz = pz + oz * reach * 0.55f;
			}
			// The joint goes out the way the leg points, and only a little up.
			if (!solve(i, px, py, pz, tx, ty, tz, ox, -0.3f, oz)) {
				upper[i].xRot = 0.0f;
				upper[i].yRot = 0.0f;
				lower[i].xRot = 0.0f;
				lower[i].yRot = 0.0f;
			}
		}
	}

	/**
	 * Two-bone reach from the hip (px, py, pz) to (tx, ty, tz), all in the hips' frame, with the
	 * knee bent towards (kx, ky, kz). Each bone hangs along +y at rest and is turned by X then Y,
	 * which is the order ModelPart applies them in (Z then Y then X, with Z left at zero).
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

		if (!Float.isFinite(x1 + y1 + x2 + y2)) return false;
		upper[i].xRot = x1;
		upper[i].yRot = y1;
		upper[i].zRot = 0.0f;
		lower[i].xRot = x2;
		lower[i].yRot = y2;
		lower[i].zRot = 0.0f;
		return true;
	}

	/** A value in [-1, 1] that holds still for {@code period} ticks, then jumps somewhere else. */
	private static float hold(int seed, float t, int period, int salt) {
		int h = seed * 0x9E3779B1 ^ Mth.floor(t / period) * 0x85EBCA6B ^ salt * 0xC2B2AE35;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 32767.5f - 1.0f;
	}
}
