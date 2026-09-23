package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Occupant: a parson far too tall to be a man. A wide-brimmed hat, a ragged black cassock with
 * nothing under it (it does not walk, it glides), grey skin, a smile sewn shut over a jaw that is not
 * properly attached, a neck that can get longer, a split in its chest where ribs show through, and
 * arms that end at the elbow in bare grey bone-thin forearms and fingers that hang almost to the ground.
 *
 * <p>It moves like stop-motion footage: poses hold, then snap. Fingers flex one at a time. It breathes.
 *
 * <p>HumanoidModel needs head/hat/body/arms/legs parts; here they are empty anchors (the head anchor
 * still receives the look direction, which is copied onto the real skull). The real skeleton is:
 * hips -> skirt -> hem, and hips -> chest -> collar, neck -> skull -> jaw/maw/hat,
 * chest -> shoulder -> forearm -> palm -> finger -> fingertip.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	/** How far the jaw can drop, in pixels. */
	private static final float JAW_DROP = 4.0f;
	private static final String[] SIDE = {"right", "left"};
	private static final int FINGERS = 4;

	private final ModelPart hips;
	private final ModelPart skirt;
	private final ModelPart hem;
	private final ModelPart chest;
	private final ModelPart neck;
	private final ModelPart skull;
	private final ModelPart jaw;
	private final ModelPart[] shoulder = new ModelPart[2];
	private final ModelPart[] forearm = new ModelPart[2];
	private final ModelPart[] palm = new ModelPart[2];
	private final ModelPart[][] finger = new ModelPart[2][FINGERS];
	private final ModelPart[][] tip = new ModelPart[2][FINGERS];

	public OccupantModel(ModelPart root) {
		super(root);
		this.hips = root.getChild("hips");
		this.skirt = hips.getChild("skirt");
		this.hem = skirt.getChild("hem");
		this.chest = hips.getChild("chest");
		this.neck = chest.getChild("neck");
		this.skull = neck.getChild("skull");
		this.jaw = skull.getChild("jaw");
		for (int s = 0; s < 2; s++) {
			shoulder[s] = chest.getChild(SIDE[s] + "_shoulder");
			forearm[s] = shoulder[s].getChild(SIDE[s] + "_forearm");
			palm[s] = forearm[s].getChild(SIDE[s] + "_palm");
			for (int i = 0; i < FINGERS; i++) {
				finger[s][i] = palm[s].getChild(SIDE[s] + "_finger" + i);
				tip[s][i] = finger[s][i].getChild(SIDE[s] + "_tip" + i);
			}
		}
	}

	/**
	 * Units are pixels; the ground is at y = 24. About 3 blocks tall with the hat, before scaling.
	 * The texture layout must match {@code tools/generate_assets.py} (BOXES).
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Anchors HumanoidModel looks up. Nothing is drawn for them.
		PartDefinition anchor = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0f, -9.0f, 0.0f));
		anchor.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		for (String name : new String[]{"body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
		}

		PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offset(0.0f, 4.0f, 0.0f));

		// The cassock: a straight upper part and a flared, ragged hem that swings after it.
		PartDefinition skirt = hips.addOrReplaceChild("skirt",
				CubeListBuilder.create().texOffs(68, 0).addBox(-5.0f, 0.0f, -3.0f, 10.0f, 10.0f, 6.0f),
				PartPose.ZERO);
		skirt.addOrReplaceChild("hem",
				CubeListBuilder.create().texOffs(68, 16).addBox(-5.5f, 0.0f, -3.5f, 11.0f, 9.0f, 7.0f),
				PartPose.offset(0.0f, 10.0f, 0.0f));

		PartDefinition chest = hips.addOrReplaceChild("chest",
				CubeListBuilder.create().texOffs(0, 27).addBox(-4.0f, -12.0f, -2.0f, 8.0f, 12.0f, 4.0f),
				PartPose.ZERO);
		chest.addOrReplaceChild("collar",
				CubeListBuilder.create().texOffs(24, 27).addBox(-2.5f, -2.0f, -2.5f, 5.0f, 2.0f, 5.0f),
				PartPose.offset(0.0f, -12.0f, 0.0f));

		// Most of the neck is hidden down inside the chest. When it stretches, more of it comes out.
		PartDefinition neck = chest.addOrReplaceChild("neck",
				CubeListBuilder.create().texOffs(48, 27).addBox(-1.0f, -2.5f, -1.0f, 2.0f, 8.0f, 2.0f),
				PartPose.offset(0.0f, -12.0f, 0.0f));
		PartDefinition skull = neck.addOrReplaceChild("skull",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -9.0f, -3.0f, 6.0f, 6.0f, 6.0f),
				PartPose.offset(0.0f, -2.5f, 0.0f));
		skull.addOrReplaceChild("brim",
				CubeListBuilder.create()
						.texOffs(0, 13).addBox(-6.0f, -9.5f, -6.0f, 12.0f, 1.0f, 12.0f)
						.texOffs(100, 0).addBox(-3.5f, -13.5f, -3.5f, 7.0f, 4.0f, 7.0f),
				PartPose.ZERO);
		// Hidden inside the jaw until the jaw drops: teeth, then a long grey throat.
		skull.addOrReplaceChild("maw",
				CubeListBuilder.create().texOffs(48, 0).addBox(-2.5f, -3.0f, -2.5f, 5.0f, 5.0f, 5.0f, new CubeDeformation(-0.2f)),
				PartPose.ZERO);
		skull.addOrReplaceChild("jaw",
				CubeListBuilder.create().texOffs(24, 0).addBox(-3.0f, 0.0f, -3.0f, 6.0f, 3.0f, 6.0f),
				PartPose.offset(0.0f, -3.0f, 0.0f));

		for (int s = 0; s < 2; s++) {
			float x = s == 0 ? -5.5f : 5.5f;
			PartDefinition shoulder = chest.addOrReplaceChild(SIDE[s] + "_shoulder",
					CubeListBuilder.create().texOffs(s == 0 ? 0 : 12, 44).addBox(-1.5f, -1.5f, -1.5f, 3.0f, 10.0f, 3.0f),
					PartPose.offset(x, -10.5f, 0.0f));
			PartDefinition forearm = shoulder.addOrReplaceChild(SIDE[s] + "_forearm",
					CubeListBuilder.create().texOffs(s == 0 ? 24 : 32, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f),
					PartPose.offset(0.0f, 8.5f, 0.0f));
			PartDefinition palm = forearm.addOrReplaceChild(SIDE[s] + "_palm",
					CubeListBuilder.create().texOffs(112, 34).addBox(-1.0f, 0.0f, -0.75f, 2.0f, 2.0f, 1.5f),
					PartPose.offset(0.0f, 9.0f, 0.0f));
			for (int i = 0; i < FINGERS; i++) {
				boolean outer = i == 0 || i == FINGERS - 1;
				float first = outer ? 3.0f : 4.0f;
				float second = outer ? 2.5f : 3.5f;
				PartDefinition f = palm.addOrReplaceChild(SIDE[s] + "_finger" + i,
						CubeListBuilder.create().texOffs(112, 34).addBox(-0.2f, 0.0f, -0.2f, 0.4f, first, 0.4f),
						PartPose.offset(-0.75f + i * 0.5f, 2.0f, 0.0f));
				f.addOrReplaceChild(SIDE[s] + "_tip" + i,
						CubeListBuilder.create().texOffs(112, 34).addBox(-0.18f, 0.0f, -0.18f, 0.36f, second, 0.36f),
						PartPose.offset(0.0f, first, 0.0f));
			}
		}

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state); // resets every part to its rest pose, and aims the head anchor
		float lookX = head.xRot;
		float lookY = head.yRot;
		boolean veiled = state.form == OccupantEntity.Form.VEILED;
		OccupantEntity.Mode mode = state.mode;
		int seed = state.seed;

		// Stop-motion: it only changes pose every few ticks, so it never quite moves like a living thing.
		float step = mode != OccupantEntity.Mode.CHASE && !veiled ? 3.0f : 2.0f;
		float t = Mth.floor(state.ageInTicks / step) * step;
		float breath = Mth.sin(t * 0.07f);

		skull.xRot = lookX;
		skull.yRot = lookY;
		skirt.zRot = 0.015f * Mth.sin(t * 0.05f);
		hem.zRot = 0.025f * Mth.sin(t * 0.05f - 0.9f);
		for (int s = 0; s < 2; s++) {
			for (int i = 0; i < FINGERS; i++) finger[s][i].zRot = (i - 1.5f) * 0.06f;
		}
		float neckStretch = 0.0f;
		float jawOpen = 0.0f;

		switch (mode) {
			case CHASE -> {
				// Hunched, lurching, arms out, fingers grasping. The robe drags behind it.
				float gait = t * 0.55f;
				hips.y += 0.9f * Math.abs(Mth.sin(gait));
				hips.zRot = 0.08f * Mth.sin(gait);
				chest.xRot = 0.5f;
				skull.xRot = lookX - 0.5f + 0.12f * hold(seed, t, 4, 11);
				skull.zRot = 0.3f * hold(seed, t, 6, 12);
				skirt.xRot = 0.15f;
				hem.xRot = 0.2f + 0.08f * Mth.sin(t * 1.1f);
				for (int s = 0; s < 2; s++) {
					float swing = Mth.sin(gait + s * Mth.PI);
					arm(s, -1.35f + 0.35f * swing, 0.12f, -0.25f - 0.25f * (0.5f + 0.5f * swing));
					for (int i = 0; i < FINGERS; i++) {
						curl(s, i, -0.2f - 0.5f * (0.5f + 0.5f * Mth.sin(t * 0.9f + i)), -0.6f);
					}
				}
				neckStretch = 2.5f;
				jawOpen = JAW_DROP + 0.8f * Math.abs(Mth.sin(t * 1.9f));
			}
			case AMBUSH -> {
				// Right behind you: leaning in, neck out, jaw hanging, hands open.
				chest.xRot = 0.3f;
				skull.xRot = lookX - 0.3f;
				skull.zRot = 0.2f;
				for (int s = 0; s < 2; s++) {
					arm(s, -1.1f, 0.25f, -0.5f);
					for (int i = 0; i < FINGERS; i++) {
						finger[s][i].zRot = (i - 1.5f) * 0.25f;
						curl(s, i, 0.25f, -0.4f);
					}
				}
				neckStretch = 4.5f;
				jawOpen = JAW_DROP + 0.6f * Math.abs(Mth.sin(t * 2.5f));
			}
			default -> {
				if (veiled) {
					// Stooped, head bowed so the brim hides its face, hands folded at its chest.
					// Every few seconds it lifts its head, just slightly, to check that you are still there.
					breathe(breath);
					chest.xRot = 0.12f + 0.02f * breath;
					boolean looks = ((int) t) % 140 < 12;
					skull.xRot = looks ? 0.25f : 0.62f;
					skull.zRot = 0.1f;
					// Once in a while: a sudden jerk of the head, held, then gone.
					if (hold(seed, t, 10, 7) > 0.85f) skull.zRot += 0.6f * Math.signum(hold(seed, t, 10, 8));
					for (int s = 0; s < 2; s++) {
						arm(s, -0.3f, -0.28f, -1.05f);
						for (int i = 0; i < FINGERS; i++) {
							curl(s, i, -0.35f - 0.3f * Math.max(0.0f, Mth.sin(t * 0.1f - i * 0.9f)), -0.5f);
						}
					}
				} else {
					// Upright, head tilted much too far, elbows bending the wrong way.
					breathe(breath);
					chest.xRot = 0.05f + 0.02f * breath;
					skull.xRot = lookX - 0.1f;
					boolean twitch = ((int) t) % 53 < 3;
					skull.zRot = 0.42f + (twitch ? 0.35f : 0.0f);
					// Now and then its head lolls over sideways and stays there.
					if (hold(seed, t, 100, 9) > 0.7f) skull.zRot = 1.35f;
					neckStretch = 2.0f + 2.0f * Mth.sin(t * 0.025f);
					jawOpen = 1.2f + 0.5f * Mth.sin(t * 0.08f);
					if (hold(seed, t, 60, 3) > 0.5f && ((int) t) % 60 < 20) {
						jawOpen = 1.0f + 1.2f * Math.abs(Mth.sin(t * 2.2f)); // chattering
					}
					for (int s = 0; s < 2; s++) {
						arm(s, 0.0f, 0.05f, 0.22f);
						for (int i = 0; i < FINGERS; i++) {
							// Each finger twitches on its own, holding each position for a moment.
							float a = Math.max(0.0f, hold(seed, t, 7 + i * 2, 20 + s * 4 + i));
							curl(s, i, -0.15f - 0.45f * a, -0.2f - 0.5f * a);
						}
					}
				}
			}
		}

		neck.y -= neckStretch;
		jaw.y += jawOpen;
		jaw.xRot = 0.05f * jawOpen;
	}

	/** Shoulders rise and fall. */
	private void breathe(float breath) {
		for (int s = 0; s < 2; s++) shoulder[s].y -= 0.3f * breath;
	}

	/**
	 * Pose one arm. {@code out} swings the arm away from the body (negative brings the hands together),
	 * {@code elbow} bends the forearm (negative is forward, the natural way).
	 */
	private void arm(int side, float forward, float out, float elbow) {
		shoulder[side].xRot = forward;
		shoulder[side].zRot = side == 0 ? out : -out;
		forearm[side].xRot = elbow;
	}

	private void curl(int side, int i, float knuckle, float joint) {
		finger[side][i].xRot = knuckle;
		tip[side][i].xRot = joint;
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
