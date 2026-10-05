package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;

/**
 * The Occupant's model, as 1.20.1 sees it: posed from the entity, so the renderer hands over the
 * frame's state first. All of how it moves is in {@link OccupantPose}.
 */
public class OccupantModel extends HumanoidModel<OccupantEntity> {
	/** Set by the renderer before each draw. */
	public OccupantRenderState state = new OccupantRenderState();
	private final ModelPart root;
	private final List<ModelPart> parts;
	private final OccupantPose pose;

	public OccupantModel(ModelPart root) {
		super(root);
		this.root = root;
		this.parts = root.getAllParts().toList();
		this.pose = new OccupantPose(root);
	}

	@Override
	public void setupAnim(OccupantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
						  float netHeadYaw, float headPitch) {
		// Here nothing puts the parts back between frames, so every frame starts from rest.
		for (ModelPart part : parts) part.resetPose();
		super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		pose.apply(state, head.xRot, head.yRot);
	}

	/** The whole body: here a humanoid model would otherwise draw only its humanoid parts. */
	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
							   float red, float green, float blue, float alpha) {
		root.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
	}
}
