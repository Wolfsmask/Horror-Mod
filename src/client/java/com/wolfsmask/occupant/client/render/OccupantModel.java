package com.wolfsmask.occupant.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The Occupant's model, as Minecraft sees it. All of how it moves is in {@link OccupantPose};
 * this is the part that differs between Minecraft versions, and each has its own copy.
 */
public class OccupantModel extends HumanoidModel<OccupantRenderState> {
	private final OccupantPose pose;

	public OccupantModel(ModelPart root) {
		super(root);
		this.pose = new OccupantPose(root);
	}

	@Override
	public void setupAnim(OccupantRenderState state) {
		super.setupAnim(state); // resets every part, and aims the (invisible) head anchor
		pose.apply(state, head.xRot, head.yRot);
	}
}
