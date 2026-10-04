package com.wolfsmask.occupant.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the Occupant. Only the haunted player is ever sent the entity, so only they see this. */
public class OccupantRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantRenderState, OccupantModel> {
	private static final Identifier TEXTURE = Occupant.id("textures/entity/occupant.png");
	private static final Identifier GLOW = Occupant.id("textures/entity/occupant_glow.png");
	/**
	 * Its height in blocks, standing up, close to. Seventeen feet, near enough. It does not fit
	 * in most of the places the story puts it, so it folds down into them rather than shrinking.
	 */
	private static final float NEAR_BLOCKS = 4.4f;
	/**
	 * Far away there is nothing beside it to measure it against, and it reads as much larger:
	 * a shape standing above the treeline. Nobody ever sees both at once, which is the point.
	 */
	private static final float FAR_BLOCKS = 5.6f;
	/** However cramped the room, it is never allowed to look like a person. */
	private static final float MIN_BLOCKS = 2.6f;
	private static final float NEAR_DISTANCE = 28.0f;
	private static final float FAR_DISTANCE = 64.0f;
	/** How far the hips drop, in model pixels, and how far the body bends, when fully folded. */
	static final float CROUCH_DROP = 26.0f;
	static final float CROUCH_BEND = 0.95f;

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
		state.headroom = headroomAbove(entity);
		fit(state);
		LegGait.of(entity).update(entity, state, state.occupantScale, CROUCH_DROP * state.crouch);
	}

	/**
	 * Works out how big to draw it and how far it has to fold to fit where it is. It keeps its
	 * full size if it can by folding down, hips low and body bent over, legs braced out to the
	 * walls; only if that is still too tall does it get smaller.
	 */
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

	/** Its height when folded by {@code crouch}, as a fraction of its full height. */
	private static float foldedHeight(float crouch) {
		float hips = OccupantGeometry.HIPS_HEIGHT - CROUCH_DROP * crouch;
		float above = OccupantGeometry.HEIGHT - OccupantGeometry.HIPS_HEIGHT;
		return (hips + above * Mth.cos(CROUCH_BEND * crouch)) / OccupantGeometry.HEIGHT;
	}

	/**
	 * The game only draws an entity while its box is on screen, and its hitbox is a person-sized
	 * sliver of what is actually drawn. Without this, looking up at its face from close by, or at
	 * a leg braced on a wall beside you, would make the whole thing blink out of existence. The
	 * box covers its full height, how far its legs reach, and how far the body trails behind.
	 */
	/** Not drawn while it is concealed: it is never seen arriving. */
	@Override
	public boolean shouldRender(OccupantEntity entity, Frustum frustum, double x, double y, double z) {
		return !entity.isConcealed() && super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	protected AABB getBoundingBoxForCulling(OccupantEntity entity) {
		return entity.getBoundingBox().inflate(6.5, 0.0, 6.5).expandTowards(0.0, FAR_BLOCKS + 1.5, 0.0);
	}

	@Override
	public Vec3 getRenderOffset(OccupantRenderState state) {
		return new Vec3(state.offsetX, 0.0, state.offsetZ);
	}

	/**
	 * How many blocks of clear space it has to stand up in. It is drawn taller than its hitbox,
	 * which looks right in the open and would put its head through the ceiling of somebody's
	 * house, so indoors it simply does not stand to its full height.
	 */
	private static float headroomAbove(OccupantEntity entity) {
		Level level = entity.level();
		BlockPos feet = entity.blockPosition();
		for (int i = 0; i < Mth.ceil(FAR_BLOCKS); i++) {
			BlockPos p = feet.above(i);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return i;
		}
		return 64.0f;                                         // open sky
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
