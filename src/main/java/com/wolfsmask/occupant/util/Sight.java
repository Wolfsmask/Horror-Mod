package com.wolfsmask.occupant.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What can the player see? Everything the Occupant does is gated on these checks,
 * so that it only ever appears, moves and disappears exactly when it should.
 */
public final class Sight {
	/** Anything further than this from the view direction is safely out of view, even at high FOV. */
	public static final double OUT_OF_VIEW_DEGREES = 80.0;

	private Sight() {
	}

	/** Angle in degrees between where the player is looking and the given point. */
	public static double angleTo(ServerPlayer player, Vec3 point) {
		Vec3 to = point.subtract(player.getEyePosition());
		double len = to.length();
		if (len < 1.0E-4) return 0.0;
		Vec3 look = player.getViewVector(1.0f);
		double dot = Mth.clamp(look.dot(to.scale(1.0 / len)), -1.0, 1.0);
		return Math.toDegrees(Math.acos(dot));
	}

	/** Horizontal-only angle (ignores looking up/down). Used for "behind you" checks. */
	public static double yawAngleTo(ServerPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		double dx = point.x - eye.x;
		double dz = point.z - eye.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) return 0.0;
		Vec3 look = player.getViewVector(1.0f);
		double llen = Math.sqrt(look.x * look.x + look.z * look.z);
		if (llen < 1.0E-4) return 90.0; // looking straight up or down
		double dot = Mth.clamp((dx * look.x + dz * look.z) / (len * llen), -1.0, 1.0);
		return Math.toDegrees(Math.acos(dot));
	}

	/** True if nothing solid is between the player's eyes and the point. */
	public static boolean hasLineOfSight(ServerPlayer player, Vec3 point) {
		BlockHitResult hit = player.level().clip(new ClipContext(
				player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Line of sight to any of the entity's head, chest or knees. */
	public static boolean canSeeAnyPart(ServerPlayer player, Entity entity) {
		double h = entity.getBbHeight();
		Vec3 base = entity.position();
		return hasLineOfSight(player, base.add(0, h * 0.9, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.55, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.2, 0));
	}

	/**
	 * Is the player looking right at this entity? The allowed angle grows as the entity gets
	 * closer (it takes up more of the screen), so this feels right at any distance.
	 */
	public static boolean isLookingAt(ServerPlayer player, Entity entity) {
		Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
		double dist = Math.max(0.5, player.getEyePosition().distanceTo(center));
		double tolerance = 5.0 + Math.toDegrees(Math.atan((entity.getBbHeight() * 0.5) / dist));
		return angleTo(player, center) <= tolerance && canSeeAnyPart(player, entity);
	}

	/** Could this entity be on the player's screen right now (inside the view cone and unobstructed)? */
	public static boolean isOnScreen(ServerPlayer player, Entity entity) {
		Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
		return angleTo(player, center) <= 60.0 && canSeeAnyPart(player, entity);
	}

	/** A block the player definitely cannot see: out of view, or hidden behind a solid block. */
	public static boolean isHidden(ServerPlayer player, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		if (angleTo(player, center) >= OUT_OF_VIEW_DEGREES) return true;
		BlockHitResult hit = player.level().clip(new ClipContext(
				player.getEyePosition(), center, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) return false;
		BlockPos hitPos = hit.getBlockPos();
		if (hitPos.equals(pos)) return false;
		return player.level().getBlockState(hitPos).isSolidRender();
	}

	/** Unit vector pointing where the player is looking, flattened onto the ground. */
	public static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-6) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		}
		return flat.normalize();
	}

	/** Rotate a flat vector around the Y axis. */
	public static Vec3 rotateY(Vec3 v, double degrees) {
		double r = Math.toRadians(degrees);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/** Yaw (Minecraft convention) that faces from one point toward another. */
	public static float yawBetween(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}
}
