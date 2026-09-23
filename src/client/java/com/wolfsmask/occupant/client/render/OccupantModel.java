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
import org.jetbrains.annotations.Nullable;

/**
 * Two bodies share this class.
 * <ul>
 *     <li>MIRROR: the vanilla player model, so the viewer's own skin maps onto it exactly.
 *     Just slightly wrong: arms a little long, head tilted.</li>
 *     <li>HOLLOW: what is underneath. Far too tall and thin, arms hanging past its knees with
 *     claws, a stretched neck, and a jaw that hangs open. Built by {@link #createHollowLayer()}.</li>
 * </ul>
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	@Nullable
	private final ModelPart jaw;
	private final boolean hollowBody;

	public OccupantModel(ModelPart root, boolean hollowBody) {
		super(root);
		this.hollowBody = hollowBody;
		this.jaw = hollowBody ? root.getChild("head").getChild("jaw") : null;
	}

	/** The Hollow. Units are pixels; the ground is at y = 24. About 2.7 blocks tall before scaling. */
	public static LayerDefinition createHollowLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -7.0f, -3.0f, 6.0f, 7.0f, 6.0f),
				PartPose.offset(0.0f, -12.0f, 0.0f));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("jaw",
				CubeListBuilder.create().texOffs(24, 0).addBox(-2.0f, 0.0f, -1.0f, 4.0f, 3.0f, 1.0f),
				PartPose.offset(0.0f, -1.0f, -2.0f));

		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 16).addBox(-3.0f, 0.0f, -1.5f, 6.0f, 14.0f, 3.0f),
				PartPose.offset(0.0f, -8.0f, 0.0f));
		body.addOrReplaceChild("neck",
				CubeListBuilder.create().texOffs(24, 8).addBox(-1.0f, -4.0f, -1.0f, 2.0f, 4.0f, 2.0f),
				PartPose.ZERO);

		PartDefinition rightArm = root.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(40, 0).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 24.0f, 2.0f),
				PartPose.offset(-4.0f, -7.0f, 0.0f));
		rightArm.addOrReplaceChild("right_claws", claws(), PartPose.ZERO);

		PartDefinition leftArm = root.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(48, 0).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 24.0f, 2.0f),
				PartPose.offset(4.0f, -7.0f, 0.0f));
		leftArm.addOrReplaceChild("left_claws", claws(), PartPose.ZERO);

		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(40, 28).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 18.0f, 2.0f),
				PartPose.offset(-1.7f, 6.0f, 0.0f));
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(48, 28).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 18.0f, 2.0f),
				PartPose.offset(1.7f, 6.0f, 0.0f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	/** Three long, thin fingers hanging from the end of an arm. */
	private static CubeListBuilder claws() {
		return CubeListBuilder.create().texOffs(56, 0)
				.addBox(-1.0f, 23.0f, -1.0f, 1.0f, 5.0f, 1.0f)
				.addBox(0.2f, 23.0f, -1.0f, 1.0f, 4.0f, 1.0f)
				.addBox(-0.4f, 23.0f, 0.2f, 1.0f, 4.0f, 1.0f);
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;

		switch (state.mode) {
			case STARE, AMBUSH -> {
				// Perfectly still, arms hanging, head tilted too far. Every few seconds: a twitch.
				rightArm.xRot = 0.0f;
				leftArm.xRot = 0.0f;
				rightArm.yRot = 0.0f;
				leftArm.yRot = 0.0f;
				rightArm.zRot = 0.03f;
				leftArm.zRot = -0.03f;
				rightLeg.xRot = 0.0f;
				leftLeg.xRot = 0.0f;
				boolean twitch = ((int) t) % 53 < 2;
				head.zRot = (hollowBody ? 0.38f : 0.08f) + (hollowBody && twitch ? 0.45f : 0.0f);
				if (jaw != null) jaw.xRot = 0.22f + 0.06f * Mth.sin(t * 0.08f);
			}
			case CHASE -> {
				if (hollowBody) {
					// Arms reaching straight at you, fingers first, swinging wildly.
					rightArm.xRot = -1.45f + 0.35f * Mth.sin(t * 0.9f);
					leftArm.xRot = -1.45f + 0.35f * Mth.sin(t * 0.9f + Mth.PI);
					head.zRot = 0.15f * Mth.sin(t * 0.7f);
					if (jaw != null) jaw.xRot = 0.75f;
				} else {
					rightArm.xRot *= 1.6f;
					leftArm.xRot *= 1.6f;
					head.zRot = 0.0f;
				}
			}
			default -> {
				head.zRot = hollowBody ? 0.2f : 0.0f;
				if (jaw != null) jaw.xRot = 0.15f;
			}
		}

		// The borrowed body is only slightly wrong; the Hollow is already built wrong.
		float armLength = hollowBody ? 1.0f : 1.08f;
		rightArm.yScale = armLength;
		leftArm.yScale = armLength;
	}
}
