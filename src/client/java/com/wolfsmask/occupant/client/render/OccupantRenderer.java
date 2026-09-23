package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Early in the story the Occupant wears the viewer's own skin: from a distance, in the dark,
 * it looks like you. Only the haunted player ever sees it, so "the viewer" is always its target.
 */
public class OccupantRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantRenderState, OccupantModel> {
	static final Identifier HOLLOW = Occupant.id("textures/entity/occupant_hollow.png");
	private static final Identifier FACE = Occupant.id("textures/entity/occupant_face.png");
	private static final Identifier EYES = Occupant.id("textures/entity/occupant_eyes.png");
	private static final Identifier HOLLOW_EYES = Occupant.id("textures/entity/occupant_hollow_eyes.png");
	/** The Hollow is ~2.7 blocks tall as built; this brings it to just over 2, so it fits where a player does. */
	private static final float HOLLOW_SCALE = 0.76f;
	private static final float PLAYER_SCALE = 0.9375f;

	private final OccupantModel wide;
	private final OccupantModel slim;
	private final OccupantModel hollow;

	public OccupantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new OccupantModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		this.wide = this.model;
		this.slim = new OccupantModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), false);
		this.hollow = new OccupantModel(OccupantModel.createHollowLayer().bakeRoot(), true);
		this.addLayer(new HollowFace(this));
		this.addLayer(new GlowingEyes(this, OccupantEntity.Form.MIRROR, EYES));
		this.addLayer(new GlowingEyes(this, OccupantEntity.Form.HOLLOW, HOLLOW_EYES));
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
		state.texture = HOLLOW;
		state.slimArms = false;

		LocalPlayer viewer = Minecraft.getInstance().player;
		if (state.form == OccupantEntity.Form.MIRROR && viewer != null) {
			PlayerSkin skin = viewer.getSkin();
			state.texture = skin.body().texturePath();
			state.slimArms = skin.model() == PlayerModelType.SLIM;
		}
	}

	@Override
	public Identifier getTextureLocation(OccupantRenderState state) {
		return state.texture;
	}

	@Override
	public void submit(OccupantRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
					   CameraRenderState camera) {
		this.model = state.form == OccupantEntity.Form.HOLLOW ? hollow : state.slimArms ? slim : wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected void scale(OccupantRenderState state, PoseStack poseStack) {
		if (state.form == OccupantEntity.Form.HOLLOW) {
			poseStack.scale(HOLLOW_SCALE, HOLLOW_SCALE, HOLLOW_SCALE);
		} else {
			poseStack.scale(PLAYER_SCALE * 0.95f, PLAYER_SCALE, PLAYER_SCALE * 0.95f);
		}
	}

	@Override
	protected boolean shouldShowName(OccupantEntity entity, double distanceSquared) {
		return false;
	}

	/** Blots out the face of the borrowed skin. Drawn full-bright, but it is black, so it stays black. */
	private static final class HollowFace extends EyesLayer<OccupantRenderState, OccupantModel> {
		private static final RenderType TYPE = RenderTypes.entityTranslucent(FACE);

		HollowFace(RenderLayerParent<OccupantRenderState, OccupantModel> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return TYPE;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, OccupantRenderState state,
						   float yRot, float xRot) {
			if (state.form == OccupantEntity.Form.MIRROR) super.submit(poseStack, collector, light, state, yRot, xRot);
		}
	}

	/** Pinprick eyes that glow in the dark. Each body has its own eye texture. */
	private static final class GlowingEyes extends EyesLayer<OccupantRenderState, OccupantModel> {
		private final OccupantEntity.Form form;
		private final RenderType type;

		GlowingEyes(RenderLayerParent<OccupantRenderState, OccupantModel> parent, OccupantEntity.Form form, Identifier texture) {
			super(parent);
			this.form = form;
			this.type = RenderTypes.eyes(texture);
		}

		@Override
		public RenderType renderType() {
			return type;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, OccupantRenderState state,
						   float yRot, float xRot) {
			if (state.form == form) super.submit(poseStack, collector, light, state, yRot, xRot);
		}
	}
}
