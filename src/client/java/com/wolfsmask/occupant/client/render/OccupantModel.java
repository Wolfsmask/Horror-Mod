package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The player model, baked from the vanilla player layer (so any skin maps onto it exactly),
 * posed to be slightly wrong: arms a little too long, perfectly still when it watches you,
 * head tilted just enough to notice. The skin's outer layers are children of each part, so
 * they follow every change automatically.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	public OccupantModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state);

		boolean hollow = state.form == OccupantEntity.Form.HOLLOW;
		float armLength = hollow ? 1.3f : 1.08f;

		switch (state.mode) {
			case STARE, AMBUSH -> {
				rightArm.xRot = 0.0f;
				leftArm.xRot = 0.0f;
				rightArm.yRot = 0.0f;
				leftArm.yRot = 0.0f;
				rightArm.zRot = 0.03f;
				leftArm.zRot = -0.03f;
				rightLeg.xRot = 0.0f;
				leftLeg.xRot = 0.0f;
				head.zRot = hollow ? 0.22f : 0.08f;
			}
			case CHASE -> {
				rightArm.xRot *= 1.6f;
				leftArm.xRot *= 1.6f;
				head.zRot = 0.0f;
			}
			default -> head.zRot = hollow ? 0.1f : 0.0f;
		}

		rightArm.yScale = armLength;
		leftArm.yScale = armLength;
	}
}
