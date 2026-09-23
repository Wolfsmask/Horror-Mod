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
import net.minecraft.util.Mth;

/** Draws the Occupant. Only the haunted player is ever sent the entity, so only they see this. */
public class OccupantRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantRenderState, OccupantModel> {
	private static final Identifier TEXTURE = Occupant.id("textures/entity/occupant.png");
	private static final Identifier GLOW = Occupant.id("textures/entity/occupant_glow.png");
	/**
	 * Its built height in blocks. Up close it is kept near this, so it still fits in the rooms
	 * and caves the story puts it in.
	 */
	private static final float NEAR_BLOCKS = 2.6f;
	/**
	 * Far away there is nothing beside it to measure it against, and it reads as much larger:
	 * a shape standing above the treeline. Nobody ever sees both at once, which is the point.
	 */
	private static final float FAR_BLOCKS = 3.9f;
	private static final float NEAR_DISTANCE = 28.0f;
	private static final float FAR_DISTANCE = 64.0f;

	public OccupantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new OccupantModel(OccupantGeometry.create().bakeRoot()), 0.4f);
		this.addLayer(new PaleSheen(this));
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
		float distance = (float) Math.sqrt(state.distanceToCameraSq);
		float far = Mth.clamp((distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 0.0f, 1.0f);
		float blocks = Mth.lerp(far, NEAR_BLOCKS, FAR_BLOCKS);
		float scale = blocks * 16.0f / OccupantGeometry.HEIGHT;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected boolean shouldShowName(OccupantEntity entity, double distanceSquared) {
		return false;
	}

	/**
	 * A very faint copy of the body, drawn full-bright. Bone does not glow, but without this it
	 * would be a black cutout in a dark forest, and the whole story depends on you being able to
	 * almost see it. The shroud is left out of it, so the covered shape stays unreadable.
	 */
	private static final class PaleSheen extends EyesLayer<OccupantRenderState, OccupantModel> {
		private static final RenderType TYPE = RenderTypes.eyes(GLOW);

		PaleSheen(RenderLayerParent<OccupantRenderState, OccupantModel> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return TYPE;
		}
	}
}
