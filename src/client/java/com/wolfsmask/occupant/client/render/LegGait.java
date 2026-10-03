package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where its legs are, and how its body gets from one place to the next.
 * <p>
 * It does not walk. Each of its legs reaches out to the nearest thing it can push against: a wall,
 * a tree, a ceiling, or the ground, and stays planted there. The body hangs between them and does
 * not follow where the entity actually is; it lags behind, then is shoved most of the way there all
 * at once, then hangs again. Legs that are left stretched too far let go and snap to a new hold.
 * Nothing about it is rhythmic, and the body never drops low and scuttles, so it never reads as
 * a spider. It reads as something pushing itself through the world against the grain.
 * <p>
 * All of this is client-side and cosmetic: the entity itself moves exactly as the server says,
 * and nothing here touches the world beyond reading which blocks are solid.
 */
final class LegGait {
	private static final Map<OccupantEntity, LegGait> GAITS = new WeakHashMap<>();
	private static final int LEGS = OccupantGeometry.LEGS;
	/** How far the drawn body may lag behind the real one, in blocks. */
	private static final double MAX_LAG = 1.4;
	/** Further than this from the drawn body and it has moved by other means: start over. */
	private static final double SNAP = 4.0;

	/** Where the body is drawn: behind the entity, catching up in shoves. */
	private Vec3 body;
	private final Vec3[] foot = new Vec3[LEGS];
	/** For a leg that is moving to a new hold: where it let go, and when. */
	private final Vec3[] from = new Vec3[LEGS];
	private final float[] swingStart = new float[LEGS];
	private final float[] swingTime = new float[LEGS];
	private float lastTime = Float.NaN;
	private float shoveUntil;
	private float nextShove;
	private float nextFidget;
	private int lastCheck;
	/** Which way it was last shoved, in the entity's own frame: forward and to its right. */
	private float leanForward;
	private float leanSide;
	private int salt;

	static LegGait of(OccupantEntity entity) {
		return GAITS.computeIfAbsent(entity, e -> new LegGait(e.getId()));
	}

	private LegGait(int seed) {
		this.salt = seed * 0x9E3779B1;
		for (int i = 0; i < LEGS; i++) swingStart[i] = -1.0f;
	}

	/**
	 * Advances everything to this frame and writes the results into the render state: where each
	 * planted leg's point should be (in model pixels), and how far the drawn body is from the real
	 * one.
	 */
	void update(OccupantEntity entity, OccupantRenderState state, float scale, float hipsHeightPx) {
		Level level = entity.level();
		Vec3 real = new Vec3(state.x, state.y, state.z);
		float now = state.ageInTicks;
		float dt = Float.isNaN(lastTime) ? 0.0f : Mth.clamp(now - lastTime, 0.0f, 5.0f);
		lastTime = now;

		double px = scale / 16.0;                             // model pixels to blocks
		double yaw = Math.toRadians(state.bodyRot);
		if (body == null || body.distanceTo(real) > SNAP || Math.abs(body.y - real.y) > 1.5) {
			// Arrived from nowhere: already standing, every leg already braced.
			body = real;
			double hipY = body.y + hipsHeightPx * px;
			for (int i = 0; i < LEGS; i++) {
				foot[i] = findHold(i, level, yaw, OccupantGeometry.LEG_LENGTH[i] * px, hipY);
				swingStart[i] = -1.0f;
			}
			nextFidget = now + 60.0f + rand(3, (int) now) * 120.0f;
		}

		boolean chasing = state.mode == OccupantEntity.Mode.CHASE;
		// The body: dragged slowly, then shoved. Height always follows exactly, so it never sinks.
		Vec3 gap = new Vec3(real.x - body.x, 0.0, real.z - body.z);
		double lag = gap.length();
		if (lag > 0.3 && now >= nextShove && now >= shoveUntil) {
			shoveUntil = now + (chasing ? 3.0f : 5.0f);
			nextShove = now + (chasing ? 3.0f : 8.0f) + rand(17, (int) now) * (chasing ? 4.0f : 14.0f);
		}
		double rate = now < shoveUntil ? 0.75 : 0.03;
		double k = 1.0 - Math.exp(-rate * dt);
		double bx = body.x + gap.x * k;
		double bz = body.z + gap.z * k;
		if (lag > MAX_LAG) {
			double pull = (lag - MAX_LAG) / lag;
			bx += gap.x * (1.0 - k) * pull;
			bz += gap.z * (1.0 - k) * pull;
		}
		body = new Vec3(bx, real.y, bz);

		// Lean into the shove, in its own frame.
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // the way it faces
		double rx = -fz, rz = fx;                             // to its right
		float wantF = (float) Mth.clamp((gap.x * fx + gap.z * fz) * 0.5, -0.5, 0.5);
		float wantS = (float) Mth.clamp((gap.x * rx + gap.z * rz) * 0.5, -0.5, 0.5);
		float ease = (float) (1.0 - Math.exp(-0.2 * dt));
		leanForward += (wantF - leanForward) * ease;
		leanSide += (wantS - leanSide) * ease;

		// Legs.
		double hipY = body.y + hipsHeightPx * px;
		int swinging = 0;
		for (int i = 0; i < LEGS; i++) if (swingStart[i] >= 0.0f) swinging++;
		boolean recheck = (int) now / 20 != lastCheck;
		lastCheck = (int) now / 20;

		// Which leg is worst off, and so is first to let go.
		int worst = -1;
		double worstScore = 0.0;
		for (int i = 0; i < LEGS; i++) {
			if (swingStart[i] >= 0.0f) {
				if (now - swingStart[i] >= swingTime[i]) swingStart[i] = -1.0f;
				continue;
			}
			double reach = OccupantGeometry.LEG_LENGTH[i] * px;
			Vec3 hip = new Vec3(body.x, hipY, body.z);
			double score;
			if (foot[i] == null) {
				score = recheck ? 0.4 : 0.0;                  // a free leg feels about now and then
			} else {
				double stretch = foot[i].distanceTo(hip) / reach;
				Vec3 ideal = ideal(i, yaw, reach, hipsHeightPx * px);
				double drift = ideal == null ? 0.0 : Math.hypot(foot[i].x - ideal.x, foot[i].z - ideal.z) / reach;
				score = Math.max((stretch - 0.9) * 10.0, drift - 0.55);
				if (recheck && !solidAt(level, foot[i])) score = 2.0;
			}
			if (score > worstScore) {
				worstScore = score;
				worst = i;
			}
		}
		int maxSwinging = chasing ? 3 : 2;
		if (worst >= 0 && worstScore > 0.0 && swinging < maxSwinging) {
			replant(worst, level, yaw, px, hipY, chasing ? 2.0f : 3.0f);
		} else if (now >= nextFidget && swinging == 0 && lag < 0.1) {
			// Standing still, every so often one leg lets go and takes a new grip, slowly.
			int i = (int) (rand(5, (int) now) * LEGS) % LEGS;
			replant(i, level, yaw, px, hipY, 9.0f);
			nextFidget = now + 90.0f + rand(9, (int) now) * 220.0f;
		}

		// Write each leg's point into the state, in model space.
		double theta = Math.toRadians(180.0f - state.bodyRot);
		double c = Math.cos(theta), sn = Math.sin(theta);
		state.offsetX = body.x - real.x;
		state.offsetZ = body.z - real.z;
		state.leanForward = leanForward;
		state.leanSide = leanSide;
		for (int i = 0; i < LEGS; i++) {
			Vec3 at = foot[i];
			if (at != null && swingStart[i] >= 0.0f && from[i] != null) {
				float f = Mth.clamp((now - swingStart[i]) / swingTime[i], 0.0f, 1.0f);
				float e = f * f * (3.0f - 2.0f * f);
				double lift = Math.sin(Math.PI * f) * Math.min(0.6, from[i].distanceTo(at) * 0.4);
				at = new Vec3(Mth.lerp(e, from[i].x, at.x), Mth.lerp(e, from[i].y, at.y) + lift, Mth.lerp(e, from[i].z, at.z));
			}
			if (at == null) {
				state.legPlanted[i] = false;
				continue;
			}
			// World to model: undo the renderer's translate, rotation, flip and scale.
			double wx = at.x - body.x, wy = at.y - body.y, wz = at.z - body.z;
			double ax = wx * c - wz * sn;
			double az = wx * sn + wz * c;
			float mx = (float) (-ax / scale * 16.0);
			float my = (float) (-wy / scale * 16.0 + 24.016);
			float mz = (float) (az / scale * 16.0);
			boolean ok = Float.isFinite(mx) && Float.isFinite(my) && Float.isFinite(mz);
			state.legPlanted[i] = ok;
			state.legTarget[i * 3] = mx;
			state.legTarget[i * 3 + 1] = my;
			state.legTarget[i * 3 + 2] = mz;
		}
	}

	/** Sends leg i off to a new hold, if it can find one; otherwise it hangs free. */
	private void replant(int i, Level level, double yaw, double px, double hipY, float duration) {
		double reach = OccupantGeometry.LEG_LENGTH[i] * px;
		Vec3 hold = findHold(i, level, yaw, reach, hipY);
		if (hold == null) {
			foot[i] = null;
			return;
		}
		from[i] = foot[i] != null ? foot[i] : new Vec3(body.x, hipY - reach * 0.5, body.z);
		foot[i] = hold;
		swingStart[i] = lastTime;
		swingTime[i] = duration;
	}

	/** The leg's direction out from the body, in the world. */
	private static Vec3 outward(int i, double yaw) {
		double a = OccupantGeometry.LEG_ANGLE[i];
		double mx = Math.sin(a), mz = Math.cos(a);           // model space: -z is its front
		// Model to world: flip x, then turn by (180 - body rotation).
		double theta = Math.PI - yaw;
		double lx = -mx, lz = mz;
		return new Vec3(lx * Math.cos(theta) + lz * Math.sin(theta), 0.0, -lx * Math.sin(theta) + lz * Math.cos(theta));
	}

	/** Where on flat ground leg i would rest, ignoring what is actually there. */
	private Vec3 ideal(int i, double yaw, double reach, double hipHeight) {
		double across = Math.sqrt(Math.max(0.0, reach * reach - hipHeight * hipHeight));
		if (across < 0.2) return null;
		Vec3 d = outward(i, yaw);
		double r = across * (0.55 + 0.25 * rand(i, 7));
		return new Vec3(body.x + d.x * r, body.y, body.z + d.z * r);
	}

	/**
	 * The nearest thing leg i can push against. First anything solid out to its side at the height
	 * this leg reaches out at: a wall, a trunk, a step. Failing that, the ground. Some of the legs
	 * only ever reach high; with nothing beside it to brace on, those hang free rather than join
	 * the others on the ground, so out in the open it does not stand on a ring of legs.
	 */
	private Vec3 findHold(int i, Level level, double yaw, double reach, double hipY) {
		Vec3 d = outward(i, yaw);
		boolean high = i % 3 == 1;
		double height = (high ? 0.9 + 0.9 * rand(i, 11) : 0.15 + 0.6 * rand(i, 13)) * (hipY - body.y);
		Vec3 hip = new Vec3(body.x, hipY, body.z);

		// Something beside it.
		for (double t = 0.3; t <= reach * 0.95; t += 0.2) {
			Vec3 p = new Vec3(body.x + d.x * t, body.y + height, body.z + d.z * t);
			if (solidAt(level, p)) {
				Vec3 face = new Vec3(p.x - d.x * 0.06, p.y, p.z - d.z * 0.06);
				if (face.distanceTo(hip) <= reach * 0.97) return face;
				break;
			}
		}
		if (high) {
			// Overhead, in a cramped space: brace against the ceiling.
			for (double up = 0.5; up <= reach * 0.9; up += 0.25) {
				Vec3 p = new Vec3(body.x + d.x * 0.6, hipY + up, body.z + d.z * 0.6);
				if (solidAt(level, p)) {
					Vec3 face = new Vec3(p.x, Math.floor(p.y) - 0.04, p.z);
					return face.distanceTo(hip) <= reach * 0.97 ? face : null;
				}
			}
			return null;
		}

		// The ground.
		Vec3 g = ideal(i, yaw, reach, hipY - body.y);
		if (g == null) return null;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(body.y + 1.0);
		for (int y = top; y >= top - 3; y--) {
			pos.set(Mth.floor(g.x), y, Mth.floor(g.z));
			BlockState bs = level.getBlockState(pos);
			VoxelShape shape = bs.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				Vec3 at = new Vec3(g.x, y + shape.max(Direction.Axis.Y), g.z);
				return at.distanceTo(hip) <= reach * 0.97 ? at : null;
			}
		}
		return null;
	}

	private static boolean solidAt(Level level, Vec3 p) {
		BlockPos pos = BlockPos.containing(p);
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		if (shape.isEmpty()) return false;
		double lx = p.x - pos.getX(), ly = p.y - pos.getY(), lz = p.z - pos.getZ();
		return lx >= shape.min(Direction.Axis.X) - 0.05 && lx <= shape.max(Direction.Axis.X) + 0.05
				&& ly >= shape.min(Direction.Axis.Y) - 0.05 && ly <= shape.max(Direction.Axis.Y) + 0.05
				&& lz >= shape.min(Direction.Axis.Z) - 0.05 && lz <= shape.max(Direction.Axis.Z) + 0.05;
	}

	/** A stable value in [0, 1) for a leg or a moment, different for every Occupant. */
	private float rand(int a, int b) {
		int h = salt ^ a * 0x85EBCA6B ^ b * 0xC2B2AE35;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 65536.0f;
	}
}
