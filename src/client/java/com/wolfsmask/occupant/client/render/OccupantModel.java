package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Moves the body built by {@link OccupantGeometry}.
 * <p>
 * Almost all of the fear in this thing is in how little it does. It does not lunge, gesture or
 * posture; it stands, at the wrong height, for too long, and every so often its head turns a
 * few degrees further than it did before. So this class is mostly restraint:
 * <ul>
 *     <li>Standing still means <em>still</em>: no idle sway, no breathing, no weight shifting.
 *     The only motion is a slow drift you cannot quite be sure you saw.</li>
 *     <li>What movement there is arrives between frames. It holds a pose, then it is in the next
 *     one, the way a thing looks in photographs taken a second apart.</li>
 *     <li>It only opens its mouth when it is already too late to matter.</li>
 * </ul>
 * VEILED is early in the story, when it keeps its head down and is harder to make out at a
 * distance; REVEALED is when it looks at you.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	private static final String[] SIDE = {"right", "left"};
	private static final int FINGERS = 4;

	private final ModelPart hips;
	private final ModelPart spine;
	private final ModelPart yoke;
	private final ModelPart neck;

	private final ModelPart skull;
	private final ModelPart[] upper = new ModelPart[2];
	private final ModelPart[] fore = new ModelPart[2];
	private final ModelPart[][] finger = new ModelPart[2][FINGERS];
	private final ModelPart[][] tip = new ModelPart[2][FINGERS];
	private final ModelPart[] thigh = new ModelPart[2];
	private final ModelPart[] shin = new ModelPart[2];

	public OccupantModel(ModelPart root) {
		super(root);
		this.hips = root.getChild("hips");
		this.spine = hips.getChild("spine");
		this.yoke = spine.getChild("yoke");
		this.neck = yoke.getChild("neck");
		this.skull = neck.getChild("skull");
		for (int s = 0; s < 2; s++) {
			upper[s] = yoke.getChild(SIDE[s] + "_upper");
			fore[s] = upper[s].getChild(SIDE[s] + "_fore");
			ModelPart hand = fore[s].getChild(SIDE[s] + "_hand");
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i] = hand.getChild(SIDE[s] + "_finger" + i);
				tip[s][i] = finger[s][i].getChild(SIDE[s] + "_tip" + i);
			}
			thigh[s] = hips.getChild(SIDE[s] + "_thigh");
			shin[s] = thigh[s].getChild(SIDE[s] + "_shin");
		}
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state); // resets every part, and aims the (invisible) head anchor
		float lookX = head.xRot;
		float lookY = head.yRot;
		boolean veiled = state.form == OccupantEntity.Form.VEILED;
		int seed = state.seed;

		// Poses hold and then change. Nothing eases: easing is what living things do.
		float step = state.mode == OccupantEntity.Mode.CHASE ? 2.0f : 8.0f;
		float t = Mth.floor(state.ageInTicks / step) * step;

		// A drift so slow you cannot tell whether it moved or you did.
		float drift = Mth.sin(t * 0.013f);
		spine.xRot = 0.03f + 0.012f * drift;
		hips.zRot = 0.008f * drift;

		switch (state.mode) {
			case CHASE -> chase(t, lookX, lookY);
			case AMBUSH -> loom(lookX, lookY);
			default -> stand(seed, t, lookX, lookY, veiled);
		}
	}

	/**
	 * Standing. The head follows you a beat late and a little too far, and the hands hang open
	 * with the fingers slightly apart, which reads as waiting rather than resting.
	 */
	private void stand(int seed, float t, float lookX, float lookY, boolean veiled) {
		// The neck carries most of the turn, so the body stays squarely facing wherever it was.
		neck.yRot = lookY * 0.45f;
		skull.yRot = lookY * 0.55f;
		neck.xRot = lookX * 0.3f - 0.05f;
		skull.xRot = lookX * 0.6f;

		// Every so often the head is simply somewhere else, tilted, and stays there a while.
		float tilt = hold(seed, t, 240, 3);
		if (tilt > 0.45f) {
			skull.zRot = 0.5f * (tilt - 0.45f) / 0.55f;
		} else if (tilt < -0.75f) {
			skull.zRot = -0.7f;                     // right over onto its shoulder
		}

		for (int s = 0; s < 2; s++) {
			// Arms hanging dead straight, turned very slightly out.
			upper[s].xRot = 0.02f;
			upper[s].zRot = s == 0 ? 0.045f : -0.045f;
			fore[s].xRot = 0.05f;
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i].xRot = -0.05f;
				finger[s][i].zRot = (i - 1.5f) * 0.09f;
				tip[s][i].xRot = -0.08f;
			}
			thigh[s].xRot = 0.0f;
			shin[s].xRot = 0.0f;
		}
		if (veiled) {
			// Early on it keeps its head down, which makes the shape shorter and harder to read.
			spine.xRot += 0.12f;
			neck.xRot += 0.35f;
		}
	}

	/** Close enough to touch you. It bends down to your height, and the mouth opens. */
	private void loom(float lookX, float lookY) {
		spine.xRot = 0.55f;
		neck.xRot = -0.35f + lookX * 0.3f;
		skull.xRot = 0.45f + lookX * 0.4f;
		skull.yRot = lookY * 0.5f;
		for (int s = 0; s < 2; s++) {
			upper[s].xRot = -0.55f;
			upper[s].zRot = s == 0 ? 0.18f : -0.18f;
			fore[s].xRot = -0.7f;
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i].xRot = -0.25f;
				finger[s][i].zRot = (i - 1.5f) * 0.22f;
				tip[s][i].xRot = -0.35f;
			}
		}
	}

	/** Running. Far too long in the stride, and it does not swing its arms; they trail. */
	private void chase(float t, float lookX, float lookY) {
		float gait = t * 0.62f;
		spine.xRot = 0.5f;
		neck.xRot = -0.55f;
		skull.xRot = 0.4f + lookX * 0.3f;
		skull.yRot = lookY * 0.3f;
		hips.y -= 1.4f * Math.abs(Mth.sin(gait));   // relative: the hips rest high up, not at 0

		for (int s = 0; s < 2; s++) {
			float swing = Mth.sin(gait + s * Mth.PI);
			thigh[s].xRot = swing * 1.25f;
			shin[s].xRot = Math.max(0.0f, -swing) * 1.5f;
			// The arms are dragged along by the body rather than driven.
			upper[s].xRot = -0.35f + swing * 0.25f;
			upper[s].zRot = s == 0 ? 0.25f : -0.25f;
			fore[s].xRot = -0.15f;
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i].xRot = -0.15f;
				finger[s][i].zRot = (i - 1.5f) * 0.18f;
				tip[s][i].xRot = -0.2f;
			}
		}
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
