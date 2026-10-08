package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A scene they watch: when it saves them from something, their view is drawn round to it, the way
 * a head turns towards a noise (slowly at first, then quicker, then settling, never snapping),
 * the picture narrows to a band, and nothing they press moves them until it is over. The server
 * says when it starts and ends; what to look at comes from the Occupant itself: whatever its
 * focus is set to, or else what it has its legs through, and it.
 */
public final class Cutscene {
	/** How hard the view is pulled towards what it should look at, and how much that is held back. */
	private static final double PULL = 16.0;
	private static final double DAMP = 7.2;
	/** Fastest the view ever turns, in degrees a second. */
	private static final double MAX_TURN = 200.0;
	/** The bands, top and bottom, as a share of the screen's height. */
	private static final float BAND = 0.11f;

	private static boolean active;
	private static int remaining;
	private static double yaw, pitch, yawSpeed, pitchSpeed;
	private static long lastFrame;
	@Nullable
	private static Vec3 target;
	/** How far the bands have come in, 0 to 1. */
	private static float bands;
	private static long lastBands;

	private Cutscene() {
	}

	public static boolean active() {
		return active;
	}

	/** The server's word: {@code ticks} of a scene, or 0 for it to end. */
	static void start(int ticks) {
		Minecraft mc = Minecraft.getInstance();
		if (ticks <= 0 || mc.player == null) {
			active = false;
			return;
		}
		if (!active) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			yawSpeed = pitchSpeed = 0.0;
			lastFrame = System.nanoTime();
			target = null;
		}
		active = true;
		remaining = ticks;
	}

	static void reset() {
		active = false;
		target = null;
		bands = 0.0f;
	}

	/** Every tick: what to look at now, and the keys let go of. */
	static void tick(Minecraft mc) {
		if (!active) return;
		if (mc.player == null || mc.level == null || --remaining <= 0) {
			active = false;
			return;
		}
		release(mc);
		target = lookAt(mc.player);
	}

	/** Nothing they press moves them: before the game reads the keys, and after. */
	static void release(Minecraft mc) {
		if (!active) return;
		var o = mc.options;
		for (KeyMapping key : List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint,
				o.keyAttack, o.keyUse, o.keyInventory, o.keyDrop)) {
			key.setDown(false);
			while (key.consumeClick()) {
				// Thrown away: nothing they press does anything until it is over.
			}
		}
	}

	/**
	 * What the scene is looking at: what the Occupant's focus is on; or what it has its legs
	 * through, and it; or it, about the height of its face. Null while there is nothing to see.
	 */
	@Nullable
	private static Vec3 lookAt(LocalPlayer player) {
		OccupantEntity it = null;
		double best = 64.0 * 64.0;
		for (OccupantEntity e : player.level().getEntitiesOfClass(OccupantEntity.class, player.getBoundingBox().inflate(64.0),
				o -> !o.isRemoved() && !o.isConcealed())) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				it = e;
			}
		}
		if (it == null) return null;
		Vec3 face = it.position().add(0.0, it.getBbHeight() * Sight.DRAWN_HEIGHT_FACTOR * 0.62, 0.0);
		int focus = it.getFocus();
		if (focus >= 0) {
			Entity f = player.level().getEntity(focus);
			if (f != null && !f.isRemoved()) return f.position().add(0.0, f.getBbHeight() * 0.5, 0.0);
		}
		Vec3 sum = Vec3.ZERO;
		int n = 0;
		for (int id : it.getHeld()) {
			Entity h = player.level().getEntity(id);
			if (h == null || h.isRemoved()) continue;
			sum = sum.add(h.position().add(0.0, h.getBbHeight() * 0.5, 0.0));
			n++;
		}
		return n == 0 ? face : sum.scale(1.0 / n).lerp(face, 0.3);
	}

	/** Every frame, after the mouse has had its say: the view, drawn round to the scene. */
	public static void frame(Minecraft mc) {
		if (!active || mc.player == null) return;
		long now = System.nanoTime();
		double dt = Mth.clamp((now - lastFrame) / 1.0e9, 0.0, 0.1);
		lastFrame = now;
		LocalPlayer player = mc.player;
		if (target != null) {
			Vec3 eye = player.getEyePosition();
			Vec3 to = target.subtract(eye);
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			double wantYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
			double wantPitch = Math.toDegrees(-Math.atan2(to.y, Math.max(flat, 1.0e-4)));
			yawSpeed += (PULL * Mth.wrapDegrees(wantYaw - yaw) - DAMP * yawSpeed) * dt;
			pitchSpeed += (PULL * (wantPitch - pitch) - DAMP * pitchSpeed) * dt;
			yawSpeed = Mth.clamp(yawSpeed, -MAX_TURN, MAX_TURN);
			pitchSpeed = Mth.clamp(pitchSpeed, -MAX_TURN, MAX_TURN);
			yaw += yawSpeed * dt;
			pitch = Mth.clamp(pitch + pitchSpeed * dt, -89.0, 89.0);
		}
		player.setYRot((float) yaw);
		player.yRotO = (float) yaw;
		player.setXRot((float) pitch);
		player.xRotO = (float) pitch;
	}

	/** The bands, top and bottom, coming in as it starts and going as it ends. */
	static void render(GuiGraphicsExtractor g, int w, int h) {
		long now = System.nanoTime();
		double dt = lastBands == 0L ? 0.0 : Mth.clamp((now - lastBands) / 1.0e9, 0.0, 0.1);
		lastBands = now;
		bands += (float) (((active ? 1.0f : 0.0f) - bands) * Math.min(1.0, dt * 3.5));
		if (bands < 0.004f) return;
		int band = Math.round(h * BAND * bands);
		g.fill(0, 0, w, band, 0xFF000000);
		g.fill(0, h - band, w, h, 0xFF000000);
	}
}
