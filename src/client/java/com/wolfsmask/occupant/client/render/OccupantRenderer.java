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
import net.minecraft.world.phys.Vec3;

/** Draws the Occupant. Only the haunted player is ever sent the entity, so only they see this. */
public class OccupantRenderer extends CullingRenderer {
	private static final Identifier TEXTURE = Occupant.id("textures/entity/occupant.png");
	private static final Identifier GLOW = Occupant.id("textures/entity/occupant_glow.png");
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
		state.act = entity.getAct() > 0 ? entity.getAct() : com.wolfsmask.occupant.client.PauseLines.act();
		OccupantFit.fit(entity.level(), state, state.x, state.y, state.z);
		LegGait.of(entity).update(entity, state, state.occupantScale, OccupantFit.CROUCH_DROP * state.crouch);
		OccupantFit.watch(state, partialTick);
	}

	@Override
	public Vec3 getRenderOffset(OccupantRenderState state) {
		return new Vec3(state.offsetX, 0.0, state.offsetZ);
	}

	@Override
	public Identifier getTextureLocation(OccupantRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(OccupantRenderState state, PoseStack poseStack) {
		float scale = state.occupantScale;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected boolean shouldShowName(OccupantEntity entity, double distanceSquared) {
		return false;
	}

	/**
	 * A very faint copy of the face and legs, drawn full-bright. Skin does not glow, but without
	 * this it would be a black cutout in a dark forest, and the whole story depends on you being
	 * able to almost see it. The hair and the body are left out of it, so the shape stays
	 * unreadable.
	 * <p>
	 * This layer is alpha-blended over the body, not added to it, so the texture carries the face's
	 * real colour at low opacity: in daylight it changes nothing, in the dark it is all you see.
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
