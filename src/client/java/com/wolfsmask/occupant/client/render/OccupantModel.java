package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Moves the body built by {@link OccupantGeometry}.
 * <p>
 * The horror is meant to be in the faces, not in the shape: it is a hunched column of people who
 * did not get to stay people, and every one of them is still trying to move. So nothing here is
 * ever in unison. Each face wakes on its own count, turns to look at you at its own moment, and
 * works its jaw at its own speed, while the mass they are part of breathes underneath them.
 * <p>
 * It moves like stop-motion footage: poses hold, then snap.
 * <ul>
 *     <li>VEILED (early in the story): only the faces near the top are uncovered, and they are
 *     asleep. From a distance it reads as a tall, still, hunched figure.</li>
 *     <li>REVEALED: all of them are awake, and they are all looking at you.</li>
 * </ul>
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	private static final String[] SIDE = {"right", "left"};
	private static final int FINGERS = 4;
	/** How far a jaw can drop, in pixels. */
	private static final float JAW_DROP = 3.6f;

	private final ModelPart mass;
	private final ModelPart shroud;
	private final ModelPart crown;
	private final ModelPart[] face = new ModelPart[OccupantGeometry.FACE_COUNT];
	private final ModelPart[] jaw = new ModelPart[OccupantGeometry.FACE_COUNT];
	/** Each face's rest pose, so a rotation can be added to the angle it was built at. */
	private final float[][] rest = new float[OccupantGeometry.FACE_COUNT][3];
	private final ModelPart[] shoulder = new ModelPart[2];
	private final ModelPart[] forearm = new ModelPart[2];
	private final ModelPart[][] finger = new ModelPart[2][FINGERS];
	private final ModelPart[][] tip = new ModelPart[2][FINGERS];

	public OccupantModel(ModelPart root) {
		super(root);
		this.mass = root.getChild("mass");
		this.shroud = mass.getChild("shroud");
		this.crown = mass.getChild("crown");
		for (int i = 0; i < face.length; i++) {
			String name = OccupantGeometry.FACES.get(i);
			ModelPart parent = i < 5 ? crown : mass;
			face[i] = parent.getChild(name);
			jaw[i] = face[i].getChild(name + "_jaw");
			rest[i] = new float[]{face[i].xRot, face[i].yRot, face[i].zRot};
		}
		for (int s = 0; s < 2; s++) {
			shoulder[s] = mass.getChild(SIDE[s] + "_shoulder");
			forearm[s] = shoulder[s].getChild(SIDE[s] + "_forearm");
			ModelPart palm = forearm[s].getChild(SIDE[s] + "_palm");
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i] = palm.getChild(SIDE[s] + "_finger" + i);
				tip[s][i] = finger[s][i].getChild(SIDE[s] + "_tip" + i);
			}
		}
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state); // resets every part, and aims the (invisible) head anchor
		float lookX = head.xRot;
		float lookY = head.yRot;
		boolean veiled = state.form == OccupantEntity.Form.VEILED;
		OccupantEntity.Mode mode = state.mode;
		int seed = state.seed;

		// Stop-motion: the pose only changes every few ticks, so it never moves like something alive.
		float step = mode == OccupantEntity.Mode.CHASE ? 2.0f : 3.0f;
		float t = Mth.floor(state.ageInTicks / step) * step;
		float breath = Mth.sin(t * 0.06f);

		// The whole mass leans and swells. The shroud follows a moment later.
		mass.xRot = 0.06f + 0.015f * breath;
		mass.zRot = 0.02f * Mth.sin(t * 0.04f);
		shroud.xRot = -0.04f + 0.03f * Mth.sin(t * 0.04f - 1.2f);
		shroud.zRot = 0.035f * Mth.sin(t * 0.035f - 0.9f);

		switch (mode) {
			case CHASE -> {
				float gait = t * 0.5f;
				mass.xRot = 0.42f;
				mass.y = 1.1f * Math.abs(Mth.sin(gait));
				mass.zRot = 0.1f * Mth.sin(gait);
				shroud.xRot = 0.22f + 0.07f * Mth.sin(t * 1.1f);
				for (int s = 0; s < 2; s++) {
					float swing = Mth.sin(gait + s * Mth.PI);
					arm(s, -1.4f + 0.3f * swing, 0.14f, -0.3f - 0.25f * (0.5f + 0.5f * swing));
					grasp(s, t, 0.9f);
				}
			}
			case AMBUSH -> {
				mass.xRot = 0.3f;
				for (int s = 0; s < 2; s++) {
					arm(s, -1.15f, 0.28f, -0.45f);
					grasp(s, t, 0.55f);
				}
			}
			default -> {
				for (int s = 0; s < 2; s++) {
					shoulder[s].y -= 0.35f * breath;   // it breathes
					if (veiled) {
						arm(s, -0.16f, -0.12f, -0.3f); // hanging, slightly drawn in
						grasp(s, t, 0.12f);
					} else {
						arm(s, 0.05f, 0.06f, 0.16f);   // hanging straight, elbows very slightly wrong
						grasp(s, t, 0.3f);
					}
				}
			}
		}

		for (int i = 0; i < face.length; i++) {
			animateFace(i, seed, t, lookX, lookY, veiled, mode);
		}
	}

	/**
	 * One face. They are deliberately out of step with each other: each has its own count for
	 * when it wakes, when it turns to you, and how fast it works its jaw.
	 */
	private void animateFace(int i, int seed, float t, float lookX, float lookY, boolean veiled,
							 OccupantEntity.Mode mode) {
		ModelPart f = face[i];
		// Early on, only the faces near the top of the mass are uncovered.
		boolean awake = !veiled || i < 3;
		f.visible = awake;
		jaw[i].visible = awake;
		if (!awake) return;

		float phase = i * 1.7f;
		// How much this face is looking at you right now: it holds, then snaps around.
		float attention = switch (mode) {
			case CHASE, AMBUSH -> 1.0f;
			case STARE, STALK -> i == 0 ? 1.0f : (hold(seed, t, 17 + i * 3, i) > -0.1f ? 1.0f : 0.0f);
			default -> i == 0 ? 0.65f : 0.0f;
		};
		if (veiled && i > 0) attention *= 0.35f;

		// Turning to look pulls the face away from the angle it grew at.
		f.xRot = Mth.lerp(attention, rest[i][0], lookX * 0.9f) + 0.03f * Mth.sin(t * 0.09f + phase);
		f.yRot = Mth.lerp(attention, rest[i][1], lookY * 0.9f) + 0.04f * Mth.sin(t * 0.07f + phase);
		f.zRot = rest[i][2] + 0.05f * Mth.sin(t * 0.05f + phase);

		// A twitch, held for a moment, then gone.
		if (hold(seed, t, 11 + i * 2, 40 + i) > 0.86f) {
			f.zRot += 0.35f;
			f.xRot -= 0.12f;
		}

		float open = switch (mode) {
			// Screaming, all of them, out of time with each other.
			case CHASE -> 0.75f + 0.25f * Mth.sin(t * 1.6f + phase);
			case AMBUSH -> 0.8f + 0.2f * Mth.sin(t * 2.1f + phase);
			// Mouths working slowly, as if trying to say something.
			case STARE, STALK -> veiled ? 0.04f : 0.3f + 0.3f * Mth.sin(t * 0.13f + phase)
					+ (hold(seed, t, 23 + i * 5, 70 + i) > 0.6f ? 0.35f : 0.0f);
			default -> veiled ? 0.0f : 0.12f + 0.1f * Mth.sin(t * 0.1f + phase);
		};
		open = Mth.clamp(open, 0.0f, 1.0f);
		jaw[i].y = open * JAW_DROP;
		jaw[i].xRot = open * 0.45f;
		jaw[i].z = open * 0.6f;
	}

	/**
	 * Pose one arm. {@code out} swings it away from the body (negative brings the hands together),
	 * {@code elbow} bends the forearm (negative is forward, the natural way).
	 */
	private void arm(int side, float forward, float out, float elbow) {
		shoulder[side].xRot = forward;
		shoulder[side].zRot = side == 0 ? out : -out;
		forearm[side].xRot = elbow;
	}

	/** Fingers closing, each one on its own count. */
	private void grasp(int side, float t, float amount) {
		for (int i = 0; i < FINGERS; i++) {
			float a = 0.5f + 0.5f * Mth.sin(t * 0.8f + i * 1.3f + side * 2.0f);
			finger[side][i].xRot = -amount * (0.35f + 0.5f * a);
			tip[side][i].xRot = -amount * (0.5f + 0.5f * a);
			finger[side][i].zRot = (i - 1.5f) * 0.07f;
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
