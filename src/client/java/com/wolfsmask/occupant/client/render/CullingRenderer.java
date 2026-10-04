package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.phys.AABB;

/**
 * When the game draws it at all. Kept apart from {@link OccupantRenderer} because these two
 * methods are what Minecraft versions keep changing; each version has its own copy of this file.
 */
abstract class CullingRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantRenderState, OccupantModel> {
	/** How tall it can be drawn, in blocks, above its feet. */
	static final float DRAWN_REACH = 7.1f;

	CullingRenderer(EntityRendererProvider.Context ctx, OccupantModel model, float shadow) {
		super(ctx, model, shadow);
	}

	/** Not drawn while it is concealed: it is never seen arriving. */
	@Override
	public boolean shouldRender(OccupantEntity entity, Frustum frustum, double x, double y, double z) {
		return !entity.isConcealed() && super.shouldRender(entity, frustum, x, y, z);
	}

	/**
	 * The game only draws an entity while its box is on screen, and its hitbox is a person-sized
	 * sliver of what is actually drawn. Without this, looking up at its face from close by, or at
	 * a leg braced on a wall beside you, would make the whole thing blink out of existence. The
	 * box covers its full height, how far its legs reach, and how far the body trails behind.
	 */
	@Override
	protected AABB getBoundingBoxForCulling(OccupantEntity entity) {
		return entity.getBoundingBox().inflate(6.5, 0.0, 6.5).expandTowards(0.0, DRAWN_REACH, 0.0);
	}
}
