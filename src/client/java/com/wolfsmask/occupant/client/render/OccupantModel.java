package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * A player model that is slightly wrong: arms a little too long, perfectly still when it
 * watches you, head tilted just enough to notice.
 */
public class OccupantModel extends PlayerEntityModel<OccupantEntity> {
	public OccupantModel(ModelPart root, boolean thinArms) {
		super(root, thinArms);
	}

	@Override
	public void setAngles(OccupantEntity entity, float limbAngle, float limbDistance, float animationProgress,
						  float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

		boolean hollow = entity.getForm() == OccupantEntity.Form.HOLLOW;
		float armLength = hollow ? 1.3f : 1.08f;

		switch (entity.getMode()) {
			case STARE, AMBUSH -> {
				rightArm.pitch = 0.0f;
				leftArm.pitch = 0.0f;
				rightArm.yaw = 0.0f;
				leftArm.yaw = 0.0f;
				rightArm.roll = 0.03f;
				leftArm.roll = -0.03f;
				rightLeg.pitch = 0.0f;
				leftLeg.pitch = 0.0f;
				head.roll = hollow ? 0.22f : 0.08f;
			}
			case CHASE -> {
				rightArm.pitch *= 1.6f;
				leftArm.pitch *= 1.6f;
				head.roll = 0.0f;
			}
			default -> head.roll = hollow ? 0.1f : 0.0f;
		}

		rightArm.yScale = armLength;
		leftArm.yScale = armLength;

		// Keep the skin's outer layer glued to the parts we just moved.
		hat.copyTransform(head);
		jacket.copyTransform(body);
		rightSleeve.copyTransform(rightArm);
		leftSleeve.copyTransform(leftArm);
		rightPants.copyTransform(rightLeg);
		leftPants.copyTransform(leftLeg);
		rightSleeve.yScale = armLength;
		leftSleeve.yScale = armLength;
	}
}
