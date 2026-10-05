package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.phys.AABB;

/**
 * When the game draws it at all. Each Minecraft version has its own copy of this file; this one
 * is for 1.21.1, where the box an entity is culled by belongs to the entity, so the whole check
 * is done here.
 */
abstract class CullingRenderer extends HumanoidMobRenderer<OccupantEntity, OccupantModel> {
	/** How tall it can be drawn, in blocks, above its feet. */
	static final float DRAWN_REACH = 7.1f;

	CullingRenderer(EntityRendererProvider.Context ctx, OccupantModel model, float shadow) {
		super(ctx, model, shadow);
	}

	/**
	 * Not drawn while it is concealed: it is never seen arriving. Otherwise drawn while any of
	 * what is drawn could be on screen: its full height, how far its legs reach, and how far the
	 * body trails behind, not just its person-sized hitbox.
	 */
	@Override
	public boolean shouldRender(OccupantEntity entity, Frustum frustum, double x, double y, double z) {
		if (entity.isConcealed() || !entity.shouldRender(x, y, z)) return false;
		if (entity.noCulling) return true;
		AABB box = entity.getBoundingBox().inflate(6.5, 0.0, 6.5).expandTowards(0.0, DRAWN_REACH, 0.0);
		return frustum.isVisible(box);
	}
}
