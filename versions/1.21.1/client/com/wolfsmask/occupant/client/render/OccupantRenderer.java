package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Occupant. Only the haunted player is ever sent the entity, so only they see this.
 * <p>
 * This copy is for 1.21.1, which draws straight from the entity: each frame, the first thing the
 * game asks for (where to draw it) works out the frame's state, and the drawing then uses that.
 * How big it is and how it folds are the same as in every other version.
 */
public class OccupantRenderer extends CullingRenderer {
	private static final ResourceLocation TEXTURE = Occupant.id("textures/entity/occupant.png");
	private static final ResourceLocation GLOW = Occupant.id("textures/entity/occupant_glow.png");
	private final OccupantRenderState state = new OccupantRenderState();
	private int preparedFor = -1;
	private static boolean drawn;

	public OccupantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new OccupantModel(OccupantGeometry.create().bakeRoot()), 0.4f);
		this.addLayer(new PaleSheen(this));
	}

	/** This frame's state for {@code entity}, worked out once however often it is asked for. */
	private OccupantRenderState prepare(OccupantEntity entity, float partialTick) {
		float age = entity.tickCount + partialTick;
		if (preparedFor == entity.getId() && age == state.ageInTicks) return state;
		preparedFor = entity.getId();
		state.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
		state.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
		state.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
		state.ageInTicks = age;
		state.bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		state.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
		state.mode = entity.getMode();
		state.form = entity.getForm();
		state.seed = entity.getId();
		OccupantFit.fit(entity.level(), state, state.x, state.y, state.z);
		LegGait.of(entity).update(entity, state, state.occupantScale, OccupantFit.CROUCH_DROP * state.crouch);
		this.getModel().state = state;
		return state;
	}

	@Override
	public Vec3 getRenderOffset(OccupantEntity entity, float partialTick) {
		OccupantRenderState s = prepare(entity, partialTick);
		return new Vec3(s.offsetX, 0.0, s.offsetZ);
	}

	@Override
	public void render(OccupantEntity entity, float yaw, float partialTick, PoseStack poseStack,
					   MultiBufferSource buffers, int light) {
		prepare(entity, partialTick);
		super.render(entity, yaw, partialTick, poseStack, buffers, light);
		if (!drawn) {
			drawn = true;
			Occupant.LOGGER.info("[client] the Occupant has been drawn");
			if (Boolean.getBoolean("occupant.smoke")) photograph();
		}
	}

	/** In the smoke run only: a photograph, a few seconds on, once the fog has rolled in (screenshots/). */
	private static void photograph() {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(8000);
			} catch (InterruptedException e) {
				return;
			}
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			mc.execute(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
					message -> Occupant.LOGGER.info("[client] photograph: {}", message.getString())));
		}, "occupant-photograph");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public ResourceLocation getTextureLocation(OccupantEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(OccupantEntity entity, PoseStack poseStack, float partialTick) {
		float scale = state.occupantScale;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected boolean shouldShowName(OccupantEntity entity) {
		return false;
	}

	/** The faint full-bright copy of the face and legs; see the other versions' copy. */
	private static final class PaleSheen extends EyesLayer<OccupantEntity, OccupantModel> {
		private static final RenderType TYPE = RenderType.eyes(GLOW);

		PaleSheen(RenderLayerParent<OccupantEntity, OccupantModel> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return TYPE;
		}
	}
}
