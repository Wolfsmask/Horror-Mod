package com.wolfsmask.occupant.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * What can the player see? Everything the Occupant does is gated on these checks,
 * so that it only ever appears, moves and disappears exactly when it should.
 */
public final class Sight {
	/** Anything further than this from the view direction is safely out of view, even at high FOV. */
	public static final double OUT_OF_VIEW_DEGREES = 80.0;
	/** Comfortably inside the view on any normal FOV setting. */
	public static final double IN_VIEW_DEGREES = 40.0;

	private Sight() {
	}

	/** Angle in degrees between where the player is looking and the given point. */
	public static double angleTo(ServerPlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePos();
		Vec3d to = point.subtract(eye);
		double len = to.length();
		if (len < 1.0E-4) return 0.0;
		Vec3d look = player.getRotationVec(1.0f);
		double dot = MathHelper.clamp(look.dotProduct(to.multiply(1.0 / len)), -1.0, 1.0);
		return Math.toDegrees(Math.acos(dot));
	}

	/** Horizontal-only angle (ignores looking up/down). Used for "behind you" checks. */
	public static double yawAngleTo(ServerPlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePos();
		double dx = point.x - eye.x;
		double dz = point.z - eye.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) return 0.0;
		Vec3d look = player.getRotationVec(1.0f);
		double lx = look.x;
		double lz = look.z;
		double llen = Math.sqrt(lx * lx + lz * lz);
		if (llen < 1.0E-4) return 90.0; // looking straight up or down
		double dot = MathHelper.clamp((dx * lx + dz * lz) / (len * llen), -1.0, 1.0);
		return Math.toDegrees(Math.acos(dot));
	}

	/** True if nothing solid is between the player's eyes and the point. */
	public static boolean hasLineOfSight(ServerPlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePos();
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(
				eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Line of sight to any of the entity's head, chest or knees. */
	public static boolean canSeeAnyPart(ServerPlayerEntity player, Entity entity) {
		double h = entity.getHeight();
		Vec3d base = entity.getPos();
		return hasLineOfSight(player, base.add(0, h * 0.9, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.55, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.2, 0));
	}

	/**
	 * Is the player looking right at this entity? The allowed angle grows as the entity gets
	 * closer (it takes up more of the screen), so this feels right at any distance.
	 */
	public static boolean isLookingAt(ServerPlayerEntity player, Entity entity) {
		Vec3d center = entity.getPos().add(0, entity.getHeight() * 0.6, 0);
		double dist = Math.max(0.5, player.getEyePos().distanceTo(center));
		double tolerance = 5.0 + Math.toDegrees(Math.atan((entity.getHeight() * 0.5) / dist));
		return angleTo(player, center) <= tolerance && canSeeAnyPart(player, entity);
	}

	/** Could this entity be on the player's screen right now (inside the view cone and unobstructed)? */
	public static boolean isOnScreen(ServerPlayerEntity player, Entity entity) {
		Vec3d center = entity.getPos().add(0, entity.getHeight() * 0.5, 0);
		return angleTo(player, center) <= 60.0 && canSeeAnyPart(player, entity);
	}

	/** A block the player definitely cannot see: out of view, or hidden behind a solid block. */
	public static boolean isHidden(ServerPlayerEntity player, BlockPos pos) {
		Vec3d center = Vec3d.ofCenter(pos);
		if (angleTo(player, center) >= OUT_OF_VIEW_DEGREES) return true;
		Vec3d eye = player.getEyePos();
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(
				eye, center, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) return false;
		BlockPos hitPos = hit.getBlockPos();
		if (hitPos.equals(pos)) return false;
		return player.getWorld().getBlockState(hitPos).isOpaqueFullCube(player.getWorld(), hitPos);
	}

	/** Unit vector pointing where the player is looking, flattened onto the ground. */
	public static Vec3d flatLook(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1.0E-6) {
			float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
		}
		return flat.normalize();
	}

	/** Rotate a flat vector around the Y axis. */
	public static Vec3d rotateY(Vec3d v, double degrees) {
		double r = Math.toRadians(degrees);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		return new Vec3d(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/** Yaw (Minecraft convention) that faces from one point toward another. */
	public static float yawBetween(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
	}
}
