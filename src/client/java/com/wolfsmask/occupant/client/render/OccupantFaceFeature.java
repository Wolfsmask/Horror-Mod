package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Hollows out the face and adds two pinprick eyes that glow in the dark. */
public class OccupantFaceFeature extends FeatureRenderer<OccupantEntity, OccupantModel> {
	private static final Identifier FACE = Identifier.of(Occupant.MOD_ID, "textures/entity/occupant_face.png");
	private static final Identifier EYES = Identifier.of(Occupant.MOD_ID, "textures/entity/occupant_eyes.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	public OccupantFaceFeature(FeatureRendererContext<OccupantEntity, OccupantModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, OccupantEntity entity,
					   float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
					   float headPitch) {
		if (entity.isInvisible()) return;
		OccupantModel model = this.getContextModel();
		if (entity.getForm() == OccupantEntity.Form.MIRROR) {
			model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(FACE)), light,
					OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		}
		model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEyes(EYES)), FULL_BRIGHT,
				OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
	}
}
