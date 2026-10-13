package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A startle: when it is suddenly right there, the head jerks back from it and away to one side,
 * fast, and in a moment they have hold of themselves again and are looking where they were. It
 * only ever moves the view by what it takes away again, so whatever they were doing with the mouse
 * is theirs throughout. Off in the comfort settings.
 */
public final class Flinch {
	/** When it started, in nanoseconds; -1 for none. */
	private static long start = -1L;
	private static double strength;
	/** Which way the head goes: away from it, to the right (+1) or the left (-1). */
	private static double side;
	/** How far it has the view turned this frame, and how far it had it turned when last put in. */
	private static double yaw, pitch, appliedYaw, appliedPitch;

	private Flinch() {
	}

	/** They flinch, as hard as {@code intensity}, from the Occupant nearest them. */
	static void start(Minecraft mc, float intensity) {
		LocalPlayer player = mc.player;
		if (player == null || !ClientConfig.get().flinch) return;
		double rel = 0.0;
		Vec3 eye = player.getEyePosition();
		OccupantEntity nearest = null;
		for (OccupantEntity e : player.level().getEntitiesOfClass(OccupantEntity.class, new AABB(eye, eye).inflate(32.0))) {
			if (nearest == null || e.distanceToSqr(player) < nearest.distanceToSqr(player)) nearest = e;
		}
		if (nearest != null) {
			Vec3 to = nearest.position().subtract(eye);
			rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-to.x, to.z)) - player.getYRot());
		}
		side = Math.abs(rel) < 5.0 ? (player.getRandom().nextBoolean() ? 1.0 : -1.0) : -Math.signum(rel);
		strength = Mth.clamp(intensity, 0.0f, 1.5f);
		start = System.nanoTime();
	}

	/** Works out how far the flinch has the view turned now. */
	static void update() {
		if (start < 0L) {
			yaw = 0.0;
			pitch = 0.0;
			return;
		}
		double t = (System.nanoTime() - start) / 1.0e9 * 20.0;    // in ticks
		if (t > 16.0) {
			start = -1L;
			yaw = 0.0;
			pitch = 0.0;
			return;
		}
		// Back in a tick and a half, and then slowly round again.
		double pulse = (t / 1.5) * Math.exp(1.0 - t / 1.5);
		yaw = side * 3.0 * strength * pulse;
		pitch = -2.2 * strength * pulse;
	}

	/** How far the flinch has the view turned now, sideways and up (degrees). */
	static double yaw() {
		return yaw;
	}

	static double pitch() {
		return pitch;
	}

	/** Outside a scene: the view moved by however far the flinch has gone since last frame. */
	static void apply(LocalPlayer player) {
		double dy = yaw - appliedYaw, dp = pitch - appliedPitch;
		appliedYaw = yaw;
		appliedPitch = pitch;
		if (dy == 0.0 && dp == 0.0) return;
		player.setYRot(player.getYRot() + (float) dy);
		player.yRotO += (float) dy;
		float was = player.getXRot();
		float now = Mth.clamp(was + (float) dp, -90.0f, 90.0f);
		player.setXRot(now);
		player.xRotO += now - was;
	}

	/** In a scene, which sets the view outright: nothing is left over to take away afterwards. */
	static void absorbed() {
		appliedYaw = yaw;
		appliedPitch = pitch;
	}
}
