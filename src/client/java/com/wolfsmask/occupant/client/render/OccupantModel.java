package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Occupant: a parson far too tall to be a man. A wide-brimmed hat, a long black cassock that
 * hides whatever it has instead of legs (it does not walk, it glides), grey skin stretched over a
 * long skull, a smile sewn shut, and fingers that hang down to its knees. Its jaw is not attached
 * properly, and when it opens there is a red mouth full of teeth behind it.
 *
 * <p>VEILED (early in the story): head bowed, hands folded, the face hidden under the brim so only
 * the eyes show. REVEALED: it looks at you.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	/** How far the jaw can drop, in pixels. */
	private static final float JAW_DROP = 4.0f;

	private final ModelPart jaw;
	private final ModelPart robe;

	public OccupantModel(ModelPart root) {
		super(root);
		this.jaw = root.getChild("head").getChild("jaw");
		this.robe = root.getChild("robe");
	}

	/**
	 * Units are pixels; the ground is at y = 24. About 2.9 blocks tall with the hat, before scaling.
	 * The texture layout must match {@code tools/generate_assets.py} (BOXES).
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -9.0f, -3.0f, 6.0f, 6.0f, 6.0f),
				PartPose.offset(0.0f, -9.0f, 0.0f));
		head.addOrReplaceChild("hat", CubeListBuilder.create()
						.texOffs(0, 13).addBox(-6.0f, -9.5f, -6.0f, 12.0f, 1.0f, 12.0f)
						.texOffs(48, 26).addBox(-3.5f, -13.5f, -3.5f, 7.0f, 4.0f, 7.0f),
				PartPose.ZERO);
		// Hidden inside the jaw until the jaw drops: teeth, then a long grey throat.
		head.addOrReplaceChild("maw",
				CubeListBuilder.create().texOffs(48, 0).addBox(-2.5f, -3.0f, -2.5f, 5.0f, 6.0f, 5.0f),
				PartPose.ZERO);
		head.addOrReplaceChild("jaw",
				CubeListBuilder.create().texOffs(24, 0).addBox(-3.0f, 0.0f, -3.0f, 6.0f, 3.0f, 6.0f),
				PartPose.offset(0.0f, -3.0f, 0.0f));

		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 27).addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f),
				PartPose.offset(0.0f, -8.0f, 0.0f));
		body.addOrReplaceChild("collar",
				CubeListBuilder.create().texOffs(24, 27).addBox(-2.5f, -2.0f, -2.5f, 5.0f, 2.0f, 5.0f),
				PartPose.ZERO);

		root.addOrReplaceChild("robe",
				CubeListBuilder.create().texOffs(72, 0).addBox(-5.0f, 0.0f, -3.0f, 10.0f, 19.0f, 6.0f),
				PartPose.offset(0.0f, 4.0f, 0.0f));

		PartDefinition rightArm = root.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(80, 26).addBox(-1.5f, -1.5f, -1.5f, 3.0f, 18.0f, 3.0f),
				PartPose.offset(-5.5f, -6.5f, 0.0f));
		rightArm.addOrReplaceChild("right_hand", fingers(), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(92, 26).addBox(-1.5f, -1.5f, -1.5f, 3.0f, 18.0f, 3.0f),
				PartPose.offset(5.5f, -6.5f, 0.0f));
		leftArm.addOrReplaceChild("left_hand", fingers(), PartPose.ZERO);

		// Nothing under the cassock.
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-2.0f, 12.0f, 0.0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(2.0f, 12.0f, 0.0f));

		return LayerDefinition.create(mesh, 128, 64);
	}

	/** Four fingers, thinner than a pixel and far too long, hanging out of a sleeve. */
	private static CubeListBuilder fingers() {
		CubeListBuilder b = CubeListBuilder.create().texOffs(104, 26);
		for (float x : new float[]{-1.35f, -0.55f, 0.25f, 1.0f}) {
			float length = x < -1.0f || x > 0.9f ? 6.0f : 7.5f;
			b.addBox(x, 16.5f, -0.25f, 0.5f, length, 0.5f);
		}
		return b;
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		boolean veiled = state.form == OccupantEntity.Form.VEILED;

		// It never swings its arms like something walking. Whatever it does, it does on purpose.
		rightArm.yRot = 0.0f;
		leftArm.yRot = 0.0f;
		robe.xRot = 0.0f;
		robe.zRot = 0.02f * Mth.sin(t * 0.05f);
		float jawOpen;

		if (state.mode == OccupantEntity.Mode.CHASE) {
			// Arms out, reaching, mouth wide open, the hem dragging behind it.
			rightArm.xRot = -1.5f + 0.3f * Mth.sin(t * 0.8f);
			leftArm.xRot = -1.5f + 0.3f * Mth.sin(t * 0.8f + Mth.PI);
			rightArm.zRot = 0.1f;
			leftArm.zRot = -0.1f;
			head.zRot = 0.2f * Mth.sin(t * 0.6f);
			robe.xRot = 0.28f + 0.06f * Mth.sin(t * 1.3f);
			jawOpen = JAW_DROP + 0.6f * Mth.sin(t * 1.7f);
		} else if (state.mode == OccupantEntity.Mode.AMBUSH) {
			// Right behind you: looking straight at you, hands coming up, jaw hanging open.
			rightArm.xRot = -0.45f;
			leftArm.xRot = -0.45f;
			rightArm.zRot = 0.05f;
			leftArm.zRot = -0.05f;
			head.zRot = 0.25f;
			jawOpen = JAW_DROP;
		} else if (veiled) {
			// Head bowed so the brim hides its face, hands folded as if it were praying.
			// Every few seconds it lifts its head, just slightly, to check that you are still there.
			rightArm.xRot = -0.62f;
			leftArm.xRot = -0.62f;
			rightArm.zRot = -0.3f;
			leftArm.zRot = 0.3f;
			boolean looks = ((int) t) % 140 < 12;
			head.xRot = looks ? 0.25f : 0.62f;
			head.zRot = 0.1f;
			jawOpen = 0.0f;
		} else {
			// Arms hanging straight, head tilted much too far. Now and then it twitches.
			rightArm.xRot = 0.0f;
			leftArm.xRot = 0.0f;
			rightArm.zRot = 0.04f;
			leftArm.zRot = -0.04f;
			boolean twitch = ((int) t) % 53 < 2;
			head.zRot = 0.42f + (twitch ? 0.35f : 0.0f);
			jawOpen = 1.2f + 0.5f * Mth.sin(t * 0.08f);
		}

		jaw.y = -3.0f + jawOpen;
		jaw.xRot = 0.05f * jawOpen;
	}
}
