package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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
	private static final float NEAR_BLOCKS = 4.4f;
	private static final float FAR_BLOCKS = 5.6f;
	private static final float MIN_BLOCKS = 2.6f;
	private static final float NEAR_DISTANCE = 28.0f;
	private static final float FAR_DISTANCE = 64.0f;
	/** How far the hips drop, in model pixels, and how far the body bends, when fully folded. */
	static final float CROUCH_DROP = 26.0f;
	static final float CROUCH_BEND = 0.95f;

	private final OccupantRenderState state = new OccupantRenderState();
	private int preparedFor = -1;

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
		state.headroom = headroomAbove(entity);
		fit(state);
		LegGait.of(entity).update(entity, state, state.occupantScale, CROUCH_DROP * state.crouch);
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
	}

	/** See the other versions' copy: fold to fit before shrinking, never down to a person's size. */
	private static void fit(OccupantRenderState state) {
		float distance = (float) Math.sqrt(state.distanceToCameraSq);
		float far = Mth.clamp((distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 0.0f, 1.0f);
		float blocks = Mth.lerp(far, NEAR_BLOCKS, FAR_BLOCKS);
		float room = state.headroom - 0.15f;
		float crouch = 0.0f;
		while (crouch < 1.0f && blocks * foldedHeight(crouch) > room) crouch += 0.05f;
		crouch = Math.min(crouch, 1.0f);
		float tall = blocks * foldedHeight(crouch);
		if (tall > room) blocks *= room / tall;
		blocks = Math.max(blocks, MIN_BLOCKS);
		state.crouch = crouch;
		state.occupantScale = blocks * 16.0f / OccupantGeometry.HEIGHT;
	}

	private static float foldedHeight(float crouch) {
		float hips = OccupantGeometry.HIPS_HEIGHT - CROUCH_DROP * crouch;
		float above = OccupantGeometry.HEIGHT - OccupantGeometry.HIPS_HEIGHT;
		return (hips + above * Mth.cos(CROUCH_BEND * crouch)) / OccupantGeometry.HEIGHT;
	}

	private static float headroomAbove(OccupantEntity entity) {
		Level level = entity.level();
		BlockPos feet = entity.blockPosition();
		for (int i = 0; i < Mth.ceil(FAR_BLOCKS); i++) {
			BlockPos p = feet.above(i);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return i;
		}
		return 64.0f;
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
