package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Early in the story the Occupant wears the viewer's own skin: from a distance, in the dark,
 * it looks like you. Only the haunted player ever sees it, so "the viewer" is always its target.
 */
public class OccupantRenderer extends MobEntityRenderer<OccupantEntity, OccupantModel> {
	private static final Identifier HOLLOW = Identifier.of(Occupant.MOD_ID, "textures/entity/occupant_hollow.png");
	private static final float PLAYER_SCALE = 0.9375f;

	private final OccupantModel wide;
	private final OccupantModel slim;

	public OccupantRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new OccupantModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
		this.wide = this.model;
		this.slim = new OccupantModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
		this.addFeature(new OccupantFaceFeature(this));
	}

	@Override
	public void render(OccupantEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		this.model = usesSlimArms(entity) ? slim : wide;
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(OccupantEntity entity) {
		if (entity.getForm() == OccupantEntity.Form.MIRROR) {
			ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
			if (viewer != null) return viewer.getSkinTextures().texture();
		}
		return HOLLOW;
	}

	@Override
	protected void scale(OccupantEntity entity, MatrixStack matrices, float amount) {
		if (entity.getForm() == OccupantEntity.Form.HOLLOW) {
			matrices.scale(PLAYER_SCALE * 0.86f, PLAYER_SCALE * 1.07f, PLAYER_SCALE * 0.86f);
		} else {
			matrices.scale(PLAYER_SCALE * 0.95f, PLAYER_SCALE, PLAYER_SCALE * 0.95f);
		}
	}

	@Override
	protected boolean hasLabel(OccupantEntity entity) {
		return false;
	}

	private static boolean usesSlimArms(OccupantEntity entity) {
		if (entity.getForm() != OccupantEntity.Form.MIRROR) return false;
		ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
		return viewer != null && viewer.getSkinTextures().model() == SkinTextures.Model.SLIM;
	}
}
