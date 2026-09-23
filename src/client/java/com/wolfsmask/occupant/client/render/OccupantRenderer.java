package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws the Occupant. Only the haunted player is ever sent the entity, so only they see this. */
public class OccupantRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantRenderState, OccupantModel> {
	private static final Identifier TEXTURE = Occupant.id("textures/entity/occupant.png");
	private static final Identifier EYES = Occupant.id("textures/entity/occupant_eyes.png");
	/** The model is ~3 blocks tall as built; this brings it to just over 2, so it fits where a player does. */
	private static final float SCALE = 0.7f;

	public OccupantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new OccupantModel(OccupantModel.createLayer().bakeRoot()), 0.4f);
		this.addLayer(new GlowingEyes(this));
	}

	@Override
	public OccupantRenderState createRenderState() {
		return new OccupantRenderState();
	}

	@Override
	public void extractRenderState(OccupantEntity entity, OccupantRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.mode = entity.getMode();
		state.form = entity.getForm();
		state.seed = entity.getId();
	}

	@Override
	public Identifier getTextureLocation(OccupantRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(OccupantRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected boolean shouldShowName(OccupantEntity entity, double distanceSquared) {
		return false;
	}

	/** Two pinpricks of light under the brim. Drawn full-bright, so they are the last thing you lose in the dark. */
	private static final class GlowingEyes extends EyesLayer<OccupantRenderState, OccupantModel> {
		private static final RenderType TYPE = RenderTypes.eyes(EYES);

		GlowingEyes(RenderLayerParent<OccupantRenderState, OccupantModel> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return TYPE;
		}
	}
}
