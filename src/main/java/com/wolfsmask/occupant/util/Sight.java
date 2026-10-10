package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.entity.OccupantEntity;
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

	/**
	 * True if nothing the player cannot see through is between their eyes and the point. Glass,
	 * panes and bars are seen through, as a player sees through them; leaves and walls are not.
	 */
	public static boolean hasLineOfSight(ServerPlayer player, Vec3 point) {
		if (!loadedAlong(player, player.getEyePosition(), point)) return false;
		BlockHitResult hit = player.level().clip(new ClipContext(
				player.getEyePosition(), point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	/**
	 * How much of it the player would see if it stood with its feet at {@code feet}: fifteen points
	 * over its drawn shape (five heights up a body {@code height} blocks tall, at its middle and a
	 * little to either side, across the player's line of view), each tested for line of sight.
	 * Returns a bit mask: bit {@code row * 3 + column}, row 0 at the feet, row 4 at the head.
	 */
	public static int visibleParts(ServerPlayer player, Vec3 feet, double height) {
		Vec3 eye = player.getEyePosition();
		double dx = feet.x - eye.x, dz = feet.z - eye.z;
		double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
		double sx = -dz / len * 0.6, sz = dx / len * 0.6;               // sideways, across the view
		int mask = 0;
		for (int row = 0; row < 5; row++) {
			double y = height * (0.1 + 0.2 * row);
			for (int col = 0; col < 3; col++) {
				Vec3 point = feet.add(sx * (col - 1), y, sz * (col - 1));
				if (hasLineOfSight(player, point)) mask |= 1 << (row * 3 + col);
			}
		}
		return mask;
	}

	/**
	 * Only just there: part of its head can be seen, but most of it is behind something, a trunk,
	 * a wall, a doorframe, the brow of a hill. This is how it should nearly always be met.
	 */
	public static boolean onlyJustVisible(ServerPlayer player, Vec3 feet, double height) {
		int mask = visibleParts(player, feet, height);
		boolean head = (mask & (0b111 << 12 | 0b111 << 9)) != 0;      // the top two rows
		int seen = Integer.bitCount(mask);
		return head && seen <= 6;
	}

	/**
	 * How tall the Occupant is drawn, in blocks, standing upright, close to: seventeen feet, near
	 * enough. Its hitbox is a person's; what is drawn is far taller, and its head is up there.
	 */
	public static final float NEAR_BLOCKS = 4.4f;
	/**
	 * Far away there is nothing beside it to measure it against, and it is drawn larger: a shape
	 * standing above the treeline. Nobody ever sees both at once, which is the point.
	 */
	public static final float FAR_BLOCKS = 5.6f;
	/** Between these distances it is drawn between the two. */
	public static final float NEAR_DISTANCE = 28.0f;
	public static final float FAR_DISTANCE = 64.0f;
	/** And it grows with every act: by the last it is a quarter again as tall. */
	public static final float GROWTH_PER_ACT = 0.08f;

	/**
	 * How tall the Occupant is drawn, in blocks, standing upright, seen from {@code distance}, so far
	 * into the story: what the renderer draws it at before folding it into anywhere too low for it.
	 */
	public static float drawnBlocks(double distance, int act) {
		float far = (float) Mth.clamp((distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 0.0, 1.0);
		return Mth.lerp(far, NEAR_BLOCKS, FAR_BLOCKS) * (1.0f + GROWTH_PER_ACT * Mth.clamp(act - 1, 0, 3));
	}

	/**
	 * How tall {@code entity} is as {@code player} sees it, in blocks: the Occupant as it is drawn
	 * for them (its head may be all there is to see over a hill or a wall), anything else as it is.
	 */
	public static double drawnHeight(ServerPlayer player, Entity entity) {
		if (!(entity instanceof OccupantEntity)) return entity.getBbHeight();
		Director director = Director.get();
		return drawnBlocks(player.distanceTo(entity), director == null ? 0 : director.actOf(player));
	}

	/** Line of sight to any of its head, chest, middle or knees, as it is drawn. */
	public static boolean canSeeAnyPart(ServerPlayer player, Entity entity) {
		double h = drawnHeight(player, entity);
		Vec3 base = entity.position();
		return hasLineOfSight(player, base.add(0, h * 0.9, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.7, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.45, 0))
				|| hasLineOfSight(player, base.add(0, h * 0.2, 0));
	}

	/**
	 * Is the player looking right at this entity? The allowed angle grows as the entity gets
	 * closer (it takes up more of the screen), so this feels right at any distance.
	 */
	public static boolean isLookingAt(ServerPlayer player, Entity entity) {
		double h = drawnHeight(player, entity);
		Vec3 center = entity.position().add(0, h * 0.55, 0);
		Vec3 head = entity.position().add(0, h * 0.9, 0);
		double dist = Math.max(0.5, player.getEyePosition().distanceTo(center));
		double tolerance = 5.0 + Math.toDegrees(Math.atan((h * 0.5) / dist));
		boolean aimed = angleTo(player, center) <= tolerance || angleTo(player, head) <= 5.0 + Math.toDegrees(Math.atan(0.6 / dist));
		return aimed && canSeeAnyPart(player, entity);
	}

	/**
	 * Could the player possibly see this right now, at any field of view anyone plays with?
	 * Deliberately generous: this decides whether it is safe for something to appear.
	 */
	public static boolean couldBeSeen(ServerPlayer player, Entity entity) {
		return couldBeSeen(player, entity, OUT_OF_VIEW_DEGREES);
	}

	/** As {@link #couldBeSeen(ServerPlayer, Entity)}, with a wider cone: {@code degrees} either side. */
	public static boolean couldBeSeen(ServerPlayer player, Entity entity, double degrees) {
		double h = drawnHeight(player, entity);
		Vec3 base = entity.position();
		boolean inCone = angleTo(player, base.add(0, h * 0.5, 0)) < degrees
				|| angleTo(player, base.add(0, h * 0.9, 0)) < degrees
				|| angleTo(player, base.add(0, h * 0.1, 0)) < degrees;
		if (inCone && canSeeAnyPart(player, entity)) return true;
		if (!(entity instanceof OccupantEntity)) return false;
		// Its legs reach out all round it, braced on whatever is there: with its body just out of
		// view beside them, one braced forward along their side can be at the edge of the screen.
		Vec3 eye = player.getEyePosition();
		Vec3 to = new Vec3(base.x - eye.x, 0.0, base.z - eye.z);
		if (to.lengthSqr() < 1.0e-4) return false;
		to = to.normalize();
		Vec3 side = new Vec3(-to.z, 0.0, to.x);
		double reach = h * LEG_REACH;
		for (Vec3 out : new Vec3[]{side, side.scale(-1.0), to, to.scale(-1.0)}) {
			Vec3 leg = base.add(out.scale(reach)).add(0.0, h * 0.35, 0.0);
			if (leg.distanceTo(eye) < 1.0) continue;                 // that one is beside them, not in view
			if (angleTo(player, leg) < degrees && hasLineOfSight(player, leg)) return true;
		}
		return false;
	}

	/** How far out from its body its legs reach, braced, as a share of how tall it is drawn. */
	private static final double LEG_REACH = 0.55;

	/** Could this entity be on the player's screen right now (inside the view cone and unobstructed)? */
	public static boolean isOnScreen(ServerPlayer player, Entity entity) {
		Vec3 center = entity.position().add(0, drawnHeight(player, entity) * 0.5, 0);
		return angleTo(player, center) <= 60.0 && canSeeAnyPart(player, entity);
	}

	/** A block the player definitely cannot see: out of view, or hidden behind a solid block. */
	public static boolean isHidden(ServerPlayer player, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		if (angleTo(player, center) >= OUT_OF_VIEW_DEGREES) return true;
		if (!loadedAlong(player, player.getEyePosition(), center)) return true;
		BlockHitResult hit = player.level().clip(new ClipContext(
				player.getEyePosition(), center, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) return false;
		BlockPos hitPos = hit.getBlockPos();
		if (hitPos.equals(pos)) return false;
		return player.level().getBlockState(hitPos).isSolidRender();
	}

	/**
	 * Whether every chunk a straight line from {@code from} to {@code to} passes through is loaded.
	 * Looking along a line reads every block on it, and reading a block in a chunk that is not
	 * loaded makes the server load it, or make it, there and then: a look across the world must
	 * never do that. Nobody can see through land that is not there for them anyway.
	 */
	public static boolean loadedAlong(ServerPlayer player, Vec3 from, Vec3 to) {
		net.minecraft.world.level.Level level = player.level();
		double x0 = from.x / 16.0, z0 = from.z / 16.0, x1 = to.x / 16.0, z1 = to.z / 16.0;
		int cx = Mth.floor(x0), cz = Mth.floor(z0);
		int ex = Mth.floor(x1), ez = Mth.floor(z1);
		double dx = x1 - x0, dz = z1 - z0;
		int stepX = dx > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? cx + 1 - x0 : x0 - cx) * tDeltaX;
		double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? cz + 1 - z0 : z0 - cz) * tDeltaZ;
		// Chunk by chunk along it (and the ones either side where it passes a corner).
		for (int n = 0; n < 256; n++) {
			if (!level.hasChunk(cx, cz)) return false;
			if (cx == ex && cz == ez) return true;
			if (Math.abs(tMaxX - tMaxZ) < 1.0e-9) {
				if (!level.hasChunk(cx + stepX, cz) || !level.hasChunk(cx, cz + stepZ)) return false;
				tMaxX += tDeltaX;
				tMaxZ += tDeltaZ;
				cx += stepX;
				cz += stepZ;
			} else if (tMaxX < tMaxZ) {
				tMaxX += tDeltaX;
				cx += stepX;
			} else {
				tMaxZ += tDeltaZ;
				cz += stepZ;
			}
		}
		return false;
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
