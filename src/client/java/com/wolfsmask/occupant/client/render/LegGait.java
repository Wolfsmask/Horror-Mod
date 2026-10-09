package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where its legs are, and how its body gets from one place to the next.
 * <p>
 * Standing, each of its legs reaches out to the nearest thing it can push against: a wall, a tree,
 * a ceiling, or the ground, and stays planted there, and now and then one lets go and slowly takes
 * a new grip. Moving, it goes in a wave: its legs in two sets, every other one round the body,
 * one set reaching ahead to where the body is going while the other holds, then the other, each
 * leg put down in front of it so the body is carried over its legs, never dragged after them. The
 * body follows where it really is closely and smoothly, leaning into the way it goes; it never
 * falls behind and is never jerked after itself.
 * <p>
 * Nothing it does jumps: every leg's point and every knee is eased to where it is going. And
 * nothing it does by itself is ever seen: while it is being looked at, it is perfectly still.
 * <p>
 * All of this is client-side and cosmetic: the entity itself moves exactly as the server says,
 * and nothing here touches the world beyond reading which blocks are solid.
 */
public final class LegGait {
	private static final Map<OccupantEntity, LegGait> GAITS = new WeakHashMap<>();
	private static final int LEGS = OccupantGeometry.LEGS;
	/** How far the drawn body may lag behind the real one, in blocks. */
	private static final double MAX_LAG = 0.5;
	/** Which of the two sets each leg steps with: every other one round the body. */
	private static final int[] SET = new int[OccupantGeometry.LEGS];
	/** Moving: how far a planted leg may fall behind where it should be before it steps, as a share of its length. */
	private static final double STRIDE = 0.28;
	/** Slower than this, in blocks a tick, it is standing. */
	private static final double MOVING = 0.012;

	static {
		// The legs are listed in order round the body, so alternate ones are on alternate sides of each other.
		for (int i = 0; i < OccupantGeometry.LEGS; i++) SET[i] = i % 2;
	}
	/** Further than this from the drawn body and it has moved by other means: start over. */
	private static final double SNAP = 4.0;

	/** How long a leg takes to go into what it takes hold of, in ticks: a thrust, not a reach. */
	private static final float REACH_IN = 3.5f;
	/** How many of its legs it will put through things: it has ten, and stands on two. */
	private static final int LEGS_FREE = LEGS - 2;

	/** Where the body is drawn: behind the entity, catching up in shoves. */
	private Vec3 body;
	private final Vec3[] foot = new Vec3[LEGS];
	/** For a leg that is moving to a new hold: where it let go, and when. */
	private final Vec3[] from = new Vec3[LEGS];
	private final float[] swingStart = new float[LEGS];
	private final float[] swingTime = new float[LEGS];
	private float lastTime = Float.NaN;
	/** Where it really was last frame, and how fast it is going, in blocks a tick (eased). */
	@Nullable
	private Vec3 lastReal;
	private Vec3 velocity = Vec3.ZERO;
	/** Which set of legs is stepping, and until when. */
	private int stepping;
	private float setUntil;
	private float nextFidget;
	private int lastCheck;
	/** Which way it was last shoved, in the entity's own frame: forward and to its right. */
	private float leanForward;
	private float leanSide;
	private int salt;
	/** Where each leg was last drawn, so a leg that changes what it is doing never jumps. */
	private final Vec3[] drawn = new Vec3[LEGS];
	/** Which way each knee was last bent, in the world, eased towards where it should go. */
	private final Vec3[] bendNow = new Vec3[LEGS];
	/**
	 * Its own clock: it runs while they are looking away and stops while they look at it, so
	 * everything it does by itself (the slow sway, the hair, a leg feeling about) is done unseen,
	 * and what they see is a thing standing perfectly still.
	 */
	private float clock;
	private float clockRate = 1.0f;
	/** How long they have been looking away, in ticks. */
	private float unseenFor;
	/** The angle its head is held at; it only ever changes while they are looking away. */
	private float tilt;
	private float nextTilt;
	/** Its size and how far it is folded, eased, so it never changes shape from one frame to the next. */
	private float fitScale = Float.NaN;
	private float fitCrouch;
	/**
	 * For each leg, what it has through it (by entity id; -1 for nothing). A leg goes in fast from
	 * wherever it was drawn, then stays exactly in it, and when what it held is gone the leg stays
	 * out where it was a moment, holding nothing.
	 */
	private final int[] holds = new int[LEGS];
	private final float[] holdSince = new float[LEGS];
	private final Vec3[] holdFrom = new Vec3[LEGS];
	private final float[] lingerUntil = new float[LEGS];
	/** Where each holding leg's point is this frame, or null. */
	private final Vec3[] holdAt = new Vec3[LEGS];

	/** Which way the body was last drawn facing, in radians. */
	private double drawnYaw;
	/**
	 * How the body is bent as it is drawn, as {@link OccupantPose} bends it (less its slow drift,
	 * and where its head is looking): the spine forward and to the side, and the neck. The legs
	 * leave the body all the way up the trunk, so where each one starts depends on it.
	 */
	private double spineX = OccupantPose.SPINE_REST;
	private double spineZ;
	private double neckX;
	/** How far the hips are dropped, in model pixels, as last drawn. */
	private float drawnDrop;

	/**
	 * Where the middle of its mouth is drawn, in the world, as of the last frame it was drawn: as
	 * tall, as folded and as bent as it was, wherever its body had got to. Null until it has been
	 * drawn.
	 */
	@Nullable
	public static Vec3 mouth(OccupantEntity entity) {
		LegGait g = GAITS.get(entity);
		if (g == null || g.body == null || Float.isNaN(g.fitScale)) return null;
		// From the base of the neck to the mouth, turned by the neck, then on from the root of the spine.
		float[] n = OccupantGeometry.NECK_TO_MOUTH, s = OccupantGeometry.SPINE_TO_NECK;
		double c = Math.cos(g.neckX), sn = Math.sin(g.neckX);
		Vec3 at = g.fromSpine(s[0] + n[0], s[1] + n[1] * c - n[2] * sn, s[2] + n[1] * sn + n[2] * c,
				g.fitScale / 16.0, g.drawnDrop);
		// Not drawn lately (out of sight), it may have gone on since without its drawn body: shaped
		// as it was last drawn, but where it is now, not left behind in the air where it was.
		if (Float.isNaN(g.lastTime) || entity.tickCount - g.lastTime > 2.0f) at = at.subtract(g.body).add(entity.position());
		return at;
	}

	static LegGait of(OccupantEntity entity) {
		return GAITS.computeIfAbsent(entity, e -> new LegGait(e.getId()));
	}

	private LegGait(int seed) {
		this.salt = seed * 0x9E3779B1;
		for (int i = 0; i < LEGS; i++) {
			swingStart[i] = -1.0f;
			holds[i] = -1;
		}
	}

	/**
	 * Advances everything to this frame and writes the results into the render state: where each
	 * planted leg's point should be (in model pixels), and how far the drawn body is from the real
	 * one.
	 */
	void update(OccupantEntity entity, OccupantRenderState state, float scale, float dropPx) {
		Level level = entity.level();
		Vec3 real = new Vec3(state.x, state.y, state.z);
		float now = state.ageInTicks;
		float dt = Float.isNaN(lastTime) ? 0.0f : Mth.clamp(now - lastTime, 0.0f, 5.0f);
		lastTime = now;

		// Folding down into somewhere low happens quickly, so it is never drawn through the ceiling;
		// getting up again, and growing back to its full height, slowly.
		boolean fresh = body == null || body.distanceTo(real) > SNAP || Math.abs(body.y - real.y) > 1.5;
		if (fresh || Float.isNaN(fitScale)) {
			fitScale = state.occupantScale;
			fitCrouch = state.crouch;
		} else {
			float down = (float) (1.0 - Math.exp(-0.6 * dt));
			float up = (float) (1.0 - Math.exp(-0.06 * dt));
			fitCrouch += (state.crouch - fitCrouch) * (state.crouch > fitCrouch ? down : up);
			fitScale += (state.occupantScale - fitScale) * (state.occupantScale < fitScale ? down : up);
		}
		state.crouch = fitCrouch;
		state.occupantScale = fitScale;
		scale = fitScale;
		dropPx = OccupantFit.CROUCH_DROP * fitCrouch;

		double px = scale / 16.0;                             // model pixels to blocks
		double yaw = Math.toRadians(state.bodyRot);
		drawnYaw = yaw;
		drawnDrop = dropPx;
		bend(state);
		boolean arrived = false;
		if (fresh) {
			// Arrived from nowhere: already standing, every leg already braced.
			arrived = true;
			body = real;
			for (int i = 0; i < LEGS; i++) {
				foot[i] = findHold(i, level, yaw, OccupantGeometry.LEG_LENGTH[i] * px, root(i, px, dropPx), Vec3.ZERO);
				swingStart[i] = -1.0f;
				drawn[i] = null;
				bendNow[i] = null;
			}
			nextFidget = now + 60.0f + rand(3, (int) now) * 120.0f;
			tilt = rand(21, (int) now) * 2.0f - 1.0f;
			nextTilt = now + 80.0f + rand(23, (int) now) * 200.0f;
		}

		// Whether they are looking at it. Its own movements wind down to nothing while they do,
		// and come back only once they look away; its head is never seen changing its angle.
		boolean watched = watched(real, state.occupantScale);
		unseenFor = watched ? 0.0f : unseenFor + dt;
		float wantRate = watched ? 0.0f : 1.0f;
		clockRate += (wantRate - clockRate) * (float) (1.0 - Math.exp(-0.12 * dt));
		clock += dt * clockRate;
		if (unseenFor > 8.0f && now >= nextTilt) {
			tilt = rand(21, (int) now) * 2.0f - 1.0f;
			nextTilt = now + 80.0f + rand(23, (int) now) * 200.0f;
		}
		state.clock = clock;
		state.tilt = tilt;

		boolean chasing = state.mode == OccupantEntity.Mode.CHASE;
		// How fast it is really going, eased so one late update from the server is not a lurch.
		if (fresh || lastReal == null) {
			velocity = Vec3.ZERO;
		} else if (dt > 1.0e-3) {
			Vec3 moved = new Vec3((real.x - lastReal.x) / dt, 0.0, (real.z - lastReal.z) / dt);
			velocity = velocity.lerp(moved, 1.0 - Math.exp(-0.35 * dt));
		}
		lastReal = real;
		double speed = Math.hypot(velocity.x, velocity.z);
		boolean moving = speed > MOVING;
		// The body: close behind where it really is, smoothly. Height always follows exactly, so it never sinks.
		Vec3 gap = new Vec3(real.x - body.x, 0.0, real.z - body.z);
		double lag = gap.length();
		double k = 1.0 - Math.exp(-0.8 * dt);
		double bx = body.x + gap.x * k;
		double bz = body.z + gap.z * k;
		if (lag > MAX_LAG) {
			double pull = (lag - MAX_LAG) / lag;
			bx += gap.x * (1.0 - k) * pull;
			bz += gap.z * (1.0 - k) * pull;
		}
		// Never drawn inside anything: if the trailing body would be in a wall, it is brought only as
		// far towards the real one as it takes to be clear, not all the way at once.
		if (!columnClear(level, bx, real.y, bz)) {
			double cx = real.x, cz = real.z;
			for (double f = 0.25; f < 1.0; f += 0.25) {
				double tx = Mth.lerp(f, bx, real.x), tz = Mth.lerp(f, bz, real.z);
				if (columnClear(level, tx, real.y, tz)) {
					cx = tx;
					cz = tz;
					break;
				}
			}
			bx = cx;
			bz = cz;
		}
		body = new Vec3(bx, real.y, bz);

		// Lean into the way it is going, in its own frame: hardest when it runs.
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // the way it faces
		double rx = -fz, rz = fx;                             // to its right
		float wantF = (float) Mth.clamp((velocity.x * fx + velocity.z * fz) * 1.6 + (gap.x * fx + gap.z * fz) * 0.3, -0.5, 0.5);
		float wantS = (float) Mth.clamp((velocity.x * rx + velocity.z * rz) * 1.2 + (gap.x * rx + gap.z * rz) * 0.3, -0.5, 0.5);
		float ease = (float) (1.0 - Math.exp(-0.2 * dt));
		leanForward += (wantF - leanForward) * ease;
		leanSide += (wantS - leanSide) * ease;
		bend(state);

		// Legs.
		int swinging = 0;
		for (int i = 0; i < LEGS; i++) if (swingStart[i] >= 0.0f) swinging++;
		boolean recheck = (int) now / 20 != lastCheck;
		lastCheck = (int) now / 20;

		// Moving, one set of legs steps while the other holds, then the other: quicker the faster it goes.
		float stepTicks = (float) Mth.clamp(1.1 / Math.max(speed, 1.0e-3) * 0.5, chasing ? 2.5 : 3.5, 7.0);
		if (moving && now >= setUntil) {
			stepping ^= 1;
			setUntil = now + stepTicks + 0.5f;
		}
		Vec3 heading = moving ? velocity.scale(1.0 / speed) : Vec3.ZERO;

		// Which legs are worst off, and so are first to let go.
		double[] score = new double[LEGS];
		boolean anyHeld = held(entity, level, now, yaw, px, dropPx);
		for (int i = 0; i < LEGS; i++) {
			score[i] = 0.0;
			if (holdAt[i] != null || now < lingerUntil[i]) continue;
			if (swingStart[i] >= 0.0f) {
				if (now - swingStart[i] >= swingTime[i]) swingStart[i] = -1.0f;
				else continue;
			}
			double reach = OccupantGeometry.LEG_LENGTH[i] * px;
			Vec3 hip = root(i, px, dropPx);
			if (foot[i] == null) {
				score[i] = recheck || moving && SET[i] == stepping ? 0.4 : 0.0;   // a free leg feels about now and then
				continue;
			}
			double stretch = foot[i].distanceTo(hip) / reach;
			Vec3 ideal = ideal(i, yaw, reach, hip);
			if (moving && !high(i) && ideal != null) {
				// Where it should be by the time it is down again: ahead, the way the body is going.
				Vec3 want = ideal.add(heading.scale(lead(i, reach, speed, stepTicks)));
				double drift = Math.hypot(foot[i].x - want.x, foot[i].z - want.z) / reach;
				score[i] = drift > STRIDE ? drift : 0.0;
				if (stretch > 0.95) score[i] = 10.0;          // about to be torn off it: now, whichever set
			} else {
				double drift = ideal == null ? 0.0 : Math.hypot(foot[i].x - ideal.x, foot[i].z - ideal.z) / reach;
				score[i] = Math.max((stretch - 0.9) * 10.0, drift - 0.55);
			}
			if (recheck && !heldBy(level, foot[i])) score[i] = 2.0;   // what it held is gone
		}
		boolean stepped = false;
		if (moving) {
			// Every leg of the set that is stepping and wants to, and any leg that has to, worst first.
			int maxSwinging = LEGS / 2 + 1;
			for (int n = 0; n < LEGS && swinging < maxSwinging; n++) {
				int best = -1;
				for (int i = 0; i < LEGS; i++) {
					if (score[i] <= 0.0 || (SET[i] != stepping && score[i] < 10.0)) continue;
					if (best < 0 || score[i] > score[best]) best = i;
				}
				if (best < 0) break;
				double reach = OccupantGeometry.LEG_LENGTH[best] * px;
				replant(best, level, yaw, px, root(best, px, dropPx), stepTicks,
						heading.scale(lead(best, reach, speed, stepTicks)));
				score[best] = 0.0;
				swinging++;
				stepped = true;
			}
		} else {
			int worst = -1;
			for (int i = 0; i < LEGS; i++) if (score[i] > 0.0 && (worst < 0 || score[i] > score[worst])) worst = i;
			if (worst >= 0 && swinging < 2) {
				replant(worst, level, yaw, px, root(worst, px, dropPx), 3.0f, Vec3.ZERO);
				stepped = true;
			}
		}
		if (!stepped && now >= nextFidget && swinging == 0 && !moving && lag < 0.1 && !watched && !anyHeld) {
			// Standing still, every so often one leg lets go and takes a new grip, slowly.
			int i = (int) (rand(5, (int) now) * LEGS) % LEGS;
			replant(i, level, yaw, px, root(i, px, dropPx), 9.0f, Vec3.ZERO);
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
			double reach = OccupantGeometry.LEG_LENGTH[i] * px;
			Vec3 hip = root(i, px, dropPx);
			boolean holding = holdAt[i] != null;
			if (holding) {
				// Into it, fast, harder at the end than at the start, and from then on exactly in it,
				// wherever it is.
				float f = Mth.clamp((now - holdSince[i]) / REACH_IN, 0.0f, 1.0f);
				float e = f * f;
				at = holdFrom[i] == null ? holdAt[i] : holdFrom[i].lerp(holdAt[i], e);
			}
			if (at == null) at = dangle(i, level, yaw, reach, hip, clock);
			if (at == null) {
				state.legPlanted[i] = false;
				state.legBendSet[i] = false;
				drawn[i] = null;
				continue;
			}
			// Whatever the leg's point is doing, it gets there smoothly: a hold lost, a free leg
			// finding one, the floor under a hanging leg stepping down. Never a jump.
			if (drawn[i] == null || arrived || holding) {
				drawn[i] = at;
			} else {
				// A step is already eased along its arc: followed closely, so the foot comes down where it lands.
				double follow = 1.0 - Math.exp(-(swingStart[i] >= 0.0f ? 2.5 : 0.7) * dt);
				drawn[i] = drawn[i].lerp(at, follow);
			}
			at = drawn[i];
			// Which way the knee goes: the first way that keeps the whole leg out of the blocks,
			// eased round to it, so a knee never flips from one side to the other in a frame.
			Vec3 want = kneeBend(i, level, yaw, px, hip, at);
			if (want == null) want = bendNow[i] != null ? bendNow[i] : outward(i, yaw).add(0.0, 0.3, 0.0);
			if (bendNow[i] == null || arrived) {
				bendNow[i] = want;
			} else {
				Vec3 eased = bendNow[i].lerp(want, 1.0 - Math.exp(-0.5 * dt));
				bendNow[i] = eased.lengthSqr() > 1.0e-4 ? eased : want;
			}
			Vec3 bend = bendNow[i];
			state.legBendSet[i] = true;
			double bx2 = bend.x * c - bend.z * sn;
			double bz2 = bend.x * sn + bend.z * c;
			state.legBend[i * 3] = (float) -bx2;
			state.legBend[i * 3 + 1] = (float) -bend.y;
			state.legBend[i * 3 + 2] = (float) bz2;
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

		// How far down it looks. The game aims its head from a person's eye height, at whoever it is
		// looking at; its face is far above that, so close to it would stare out over their head.
		// Which way round it looks stays the game's; how far down is worked out again from its eyes:
		// at what the game's aim points at, as far off as whoever is watching.
		state.facePitch = Float.NaN;
		Entity viewer = net.minecraft.client.Minecraft.getInstance().player;
		if (viewer != null) {
			float[] n = OccupantGeometry.NECK_TO_EYES, sp = OccupantGeometry.SPINE_TO_NECK;
			double nc = Math.cos(neckX), ns = Math.sin(neckX);
			Vec3 eyes = fromSpine(sp[0] + n[0], sp[1] + n[1] * nc - n[2] * ns, sp[2] + n[1] * ns + n[2] * nc, px, dropPx);
			double away = Math.max(0.5, Math.hypot(viewer.getX() - real.x, viewer.getZ() - real.z));
			double aimedAt = real.y + entity.getEyeHeight() - Math.tan(Math.toRadians(entity.getXRot())) * away;
			double fromEyes = Math.max(0.5, Math.hypot(viewer.getX() - eyes.x, viewer.getZ() - eyes.z));
			state.facePitch = (float) Mth.clamp(Math.atan2(eyes.y - aimedAt, fromEyes), -1.05, 1.05);
		}
	}

	/**
	 * Where each leg that is through something should be this frame (into {@link #holdAt}): in at
	 * the back and out through the front, from the hip it comes out of. A leg is picked for each
	 * new thing the first time (the free one pointing most its way that can reach it; never more
	 * than eight, so it still stands), and lets go when the server says so or the thing is gone.
	 * True if any leg holds something.
	 */
	private boolean held(OccupantEntity entity, Level level, float now, double yaw, double px, float dropPx) {
		int[] ids = entity.getHeld();
		float partial = Mth.clamp(now - entity.tickCount, 0.0f, 1.0f);
		// How many legs each thing is to have through it (the same one more than once: more legs).
		Map<Integer, Integer> wanted = new java.util.HashMap<>();
		for (int id : ids) wanted.merge(id, 1, Integer::sum);
		// Let go of what is no longer held (or not by so many legs), or no longer there: the leg stays out a moment.
		Map<Integer, Integer> kept = new java.util.HashMap<>();
		for (int i = 0; i < LEGS; i++) {
			holdAt[i] = null;
			if (holds[i] < 0) continue;
			Entity held = level.getEntity(holds[i]);
			int already = kept.getOrDefault(holds[i], 0);
			boolean still = held != null && !held.isRemoved() && already < wanted.getOrDefault(holds[i], 0);
			if (!still) {
				if (drawn[i] != null) foot[i] = drawn[i];
				swingStart[i] = -1.0f;
				lingerUntil[i] = now + 40.0f;
				holds[i] = -1;
			} else {
				kept.put(holds[i], already + 1);
			}
		}
		// New legs for what is to have more through it than it has.
		for (Map.Entry<Integer, Integer> w : wanted.entrySet()) {
			int id = w.getKey();
			Entity held = level.getEntity(id);
			if (held == null || held.isRemoved()) continue;
			Vec3 centre = held.getPosition(partial).add(0.0, held.getBbHeight() * 0.55, 0.0);
			for (int n = kept.getOrDefault(id, 0); n < w.getValue(); n++) {
				int used = 0;
				for (int i = 0; i < LEGS; i++) if (holds[i] >= 0) used++;
				if (used >= LEGS_FREE) break;
				int leg = -1;
				double best = Double.NEGATIVE_INFINITY;
				for (int i = 0; i < LEGS; i++) {
					if (holds[i] >= 0) continue;
					Vec3 hip = root(i, px, dropPx);
					Vec3 to = centre.subtract(hip);
					Vec3 flat = new Vec3(to.x, 0.0, to.z);
					double score = flat.lengthSqr() < 1.0e-6 ? 0.0 : outward(i, yaw).dot(flat.normalize());
					if (to.length() > OccupantGeometry.LEG_LENGTH[i] * px * 0.95) score -= 10.0;   // cannot reach it
					if (score > best) {
						best = score;
						leg = i;
					}
				}
				if (leg < 0) break;
				holds[leg] = id;
				holdSince[leg] = now;
				holdFrom[leg] = drawn[leg] != null ? drawn[leg] : foot[leg];
				swingStart[leg] = -1.0f;
				lingerUntil[leg] = 0.0f;
			}
		}
		boolean any = false;
		for (int i = 0; i < LEGS; i++) {
			if (holds[i] < 0) continue;
			Entity held = level.getEntity(holds[i]);
			if (held == null || held.isRemoved()) continue;
			// Each leg a little higher or lower through it than the next, so they do not all come out
			// of the same place.
			double at = 0.55 + (rand(i, 31) - 0.5) * 0.3;
			Vec3 centre = held.getPosition(partial).add(0.0, held.getBbHeight() * at, 0.0);
			// In at the back and out through the front, well past it, so the point is seen.
			Vec3 hip = root(i, px, dropPx);
			Vec3 through = centre.subtract(hip);
			holdAt[i] = through.lengthSqr() < 1.0e-4 ? centre
					: centre.add(through.normalize().scale(held.getBbWidth() * 0.5 + 0.55));
			any = true;
		}
		return any;
	}

	/**
	 * Whether the player is looking its way: anywhere on their screen, worked out from their own
	 * field of view and window shape, with a margin for its size.
	 */
	private static boolean watched(Vec3 at, float scale) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) return false;
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getViewVector(1.0f);
		double aspect = Math.max(1.0, (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
		double halfV = Math.toRadians(mc.options.fov().get() * 0.5);
		double halfDiagonal = Math.atan(Math.tan(halfV) * Math.sqrt(1.0 + aspect * aspect));
		// All the way up it, as tall as it is drawn: from close by, looking up at its face, its feet
		// are off the bottom of the screen; each with a margin for how far its legs reach out.
		double tall = scale * OccupantGeometry.HEIGHT / 16.0;
		for (double up : new double[]{0.15, 0.5, 0.9}) {
			Vec3 to = at.add(0.0, tall * up, 0.0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0e-3) return true;
			double margin = Math.atan(tall * 0.55 / dist) + Math.toRadians(8.0);
			if (to.scale(1.0 / dist).dot(look) > Math.cos(Math.min(Math.PI, halfDiagonal + margin))) return true;
		}
		return false;
	}

	/** This frame's bend of the body, as {@link OccupantPose} will draw it. */
	private void bend(OccupantRenderState state) {
		boolean veiled = state.form == OccupantEntity.Form.VEILED;
		spineX = OccupantPose.SPINE_REST + OccupantFit.CROUCH_BEND * fitCrouch + leanForward * OccupantPose.LEAN_FORWARD
				+ OccupantPose.spineBend(state.mode, veiled);
		spineZ = -leanSide * OccupantPose.LEAN_SIDE;
		neckX = -OccupantFit.CROUCH_BEND * 0.75f * fitCrouch + OccupantPose.neckBend(state.mode, veiled);
	}

	/** Where leg i leaves the body, in the world: up the trunk, wherever the bent body has taken it. */
	private Vec3 root(int i, double px, float dropPx) {
		double up = OccupantGeometry.LEG_ROOT_HEIGHT[i] - OccupantGeometry.SPINE_ROOT_HEIGHT;
		return fromSpine(0.0, -up, 0.0, px, dropPx);
	}

	/**
	 * A point given in the spine's own frame (model pixels from its root: y down, -z in front), in
	 * the world: turned by the spine's bend, from the root of the spine over the dropped hips, and
	 * then as the renderer draws the model (scaled, flipped and turned to face its way).
	 */
	private Vec3 fromSpine(double x, double y, double z, double px, float dropPx) {
		// Turned as ModelPart turns it: X first, then Z (the spine is never turned about Y).
		double cx = Math.cos(spineX), sx = Math.sin(spineX);
		double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
		double cz = Math.cos(spineZ), sz = Math.sin(spineZ);
		double mx = x * cz - y1 * sz, my = x * sz + y1 * cz;
		// Model to world: up from the ground, and the flip and the turn undone.
		double up = (OccupantGeometry.SPINE_ROOT_HEIGHT - dropPx - my) * px;
		double ax = -mx * px, az = z1 * px;
		double theta = Math.PI - drawnYaw;
		double c = Math.cos(theta), sn = Math.sin(theta);
		return body.add(ax * c + az * sn, up, -ax * sn + az * c);
	}

	/** Whether leg i is one of those high up the body, that reach for walls and ceilings, not the floor. */
	private static boolean high(int i) {
		return OccupantGeometry.LEG_ROOT_HEIGHT[i] > OccupantGeometry.HIPS_HEIGHT + 17.0f;
	}

	/** Moving: how far ahead of where it rests leg i is put down, so the body is carried over it. */
	private static double lead(int i, double reach, double speed, float stepTicks) {
		return Math.min(reach * 0.3, speed * (stepTicks + 4.0));
	}

	/**
	 * Sends leg i off to a new hold, {@code lead} ahead of where it would rest if the body stood
	 * still (as near that as it can reach), if it can find one; otherwise it hangs free.
	 */
	private void replant(int i, Level level, double yaw, double px, Vec3 hip, float duration, Vec3 lead) {
		double reach = OccupantGeometry.LEG_LENGTH[i] * px;
		Vec3 hold = null;
		for (double f = 1.0; f >= 0.0 && hold == null; f -= 0.5) hold = findHold(i, level, yaw, reach, hip, lead.scale(f));
		if (hold == null) {
			foot[i] = null;
			return;
		}
		// From wherever the leg is drawn now, so it never starts its reach from somewhere else.
		from[i] = drawn[i] != null ? drawn[i] : foot[i] != null ? foot[i] : hip.add(0.0, -reach * 0.5, 0.0);
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
	private Vec3 ideal(int i, double yaw, double reach, Vec3 hip) {
		double hipHeight = hip.y - body.y;
		double across = Math.sqrt(Math.max(0.0, reach * reach - hipHeight * hipHeight));
		if (across < 0.2) return null;
		Vec3 d = outward(i, yaw);
		double r = across * (0.35 + 0.5 * rand(i, 7));
		return new Vec3(hip.x + d.x * r, body.y, hip.z + d.z * r);
	}

	/**
	 * The nearest thing leg i can push against. First anything solid out to its side at the height
	 * this leg reaches out at: a wall, a trunk, a step. Failing that, the ground. Some of the legs
	 * only ever reach high; with nothing beside it to brace on, those hang free rather than join
	 * the others on the ground, so out in the open it does not stand on a ring of legs.
	 */
	private Vec3 findHold(int i, Level level, double yaw, double reach, Vec3 hip, Vec3 lead) {
		Vec3 d = outward(i, yaw);
		// The legs highest up the body reach for walls and ceilings, not the floor.
		boolean high = high(i);
		double height = (high ? 0.9 + 0.9 * rand(i, 11) : 0.15 + 0.6 * rand(i, 13)) * (hip.y - body.y);

		// Something beside it.
		for (double t = 0.3; t <= reach * 0.95; t += 0.2) {
			Vec3 p = new Vec3(hip.x + d.x * t, body.y + height, hip.z + d.z * t);
			if (solidAt(level, p)) {
				Vec3 face = new Vec3(p.x - d.x * 0.06, p.y, p.z - d.z * 0.06);
				if (face.distanceTo(hip) <= reach * 0.97 && lineClear(level, hip, face)) return face;
				break;
			}
		}
		if (high) {
			// Overhead, in a cramped space: brace against the ceiling.
			for (double up = 0.5; up <= reach * 0.9; up += 0.25) {
				Vec3 p = new Vec3(hip.x + d.x * 0.6, hip.y + up, hip.z + d.z * 0.6);
				if (solidAt(level, p)) {
					Vec3 face = new Vec3(p.x, Math.floor(p.y) - 0.04, p.z);
					return face.distanceTo(hip) <= reach * 0.97 && lineClear(level, hip, face) ? face : null;
				}
			}
			return null;
		}

		// The ground.
		Vec3 g = ideal(i, yaw, reach, hip);
		if (g == null) return null;
		g = g.add(lead.x, 0.0, lead.z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(body.y + 1.0);
		for (int y = top; y >= top - 3; y--) {
			pos.set(Mth.floor(g.x), y, Mth.floor(g.z));
			BlockState bs = level.getBlockState(pos);
			VoxelShape shape = bs.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				// Solid right at the top of the search means the spot is inside a wall or a hill,
				// not on top of anything: no hold there.
				if (y == top) return null;
				Vec3 at = new Vec3(g.x, y + shape.max(Direction.Axis.Y), g.z);
				return at.distanceTo(hip) <= reach * 0.97 && lineClear(level, hip, at) ? at : null;
			}
		}
		return null;
	}

	/**
	 * Whether a planted point is still against something. Holds sit exactly on a surface (the top
	 * of a block, or just off the face of a wall), so look a little way off it in every direction
	 * rather than at the point itself, which is always in the air.
	 */
	private static boolean heldBy(Level level, Vec3 p) {
		for (Direction d : Direction.values()) {
			if (solidAt(level, p.add(d.getStepX() * 0.12, d.getStepY() * 0.12, d.getStepZ() * 0.12))) return true;
		}
		return false;
	}

	/**
	 * Where a leg with nothing to hold hangs: down beside the body, long and limp, slowly feeling
	 * about, but never into the ground or a wall. Null only if there is no room at all.
	 */
	private Vec3 dangle(int i, Level level, double yaw, double reach, Vec3 hip, float now) {
		Vec3 d = outward(i, yaw);
		double feel = Math.sin(now * 0.031 + i * 1.7);
		double out = reach * (0.3 + 0.06 * Math.sin(now * 0.023 + i));
		double down = reach * (0.8 + 0.08 * feel) * 0.88;
		for (int tries = 0; tries < 6; tries++) {
			Vec3 p = new Vec3(hip.x + d.x * out, hip.y - down, hip.z + d.z * out);
			// Not into the floor: stop just above whatever is under it.
			for (double y = hip.y; y > p.y; y -= 0.25) {
				if (solidAt(level, new Vec3(p.x, y, p.z))) {
					p = new Vec3(p.x, Math.floor(y) + 1.15, p.z);
					break;
				}
			}
			if (!solidAt(level, p) && lineClear(level, hip, p)) return p;
			out *= 0.6;                                   // tuck it in closer to the body
			down *= 0.8;
		}
		return null;
	}

	/**
	 * The way leg i's knee bends, in the world, or null if none keeps it clear (then it bends the
	 * usual way). The usual way is out from the body and a little up, like an elbow braced against
	 * a wall; but bracing against a wall close by, that would put the knee into the wall, so up,
	 * back, and either side are tried in turn.
	 */
	private Vec3 kneeBend(int i, Level level, double yaw, double px, Vec3 hip, Vec3 foot) {
		double l1 = OccupantGeometry.LEG_UPPER[i] * px;
		double l2 = (OccupantGeometry.LEG_LENGTH[i] - OccupantGeometry.LEG_UPPER[i]) * px;
		Vec3 d = outward(i, yaw);
		Vec3 side = new Vec3(-d.z, 0.0, d.x);
		Vec3[] tries = {
				d.add(0.0, 0.3, 0.0), new Vec3(d.x * 0.25, 1.0, d.z * 0.25), d.scale(-1.0).add(0.0, 0.6, 0.0),
				side.add(0.0, 0.3, 0.0), side.scale(-1.0).add(0.0, 0.3, 0.0), new Vec3(d.x * 0.3, -1.0, d.z * 0.3)};
		Vec3 best = null;
		int bestHits = Integer.MAX_VALUE;
		for (Vec3 k : tries) {
			Vec3 knee = knee(hip, foot, l1, l2, k);
			if (knee == null) return null;
			int hits = 0;
			for (double t = 0.1; t < 0.95; t += 0.1) {
				if (solidAt(level, hip.lerp(knee, t))) hits++;
				if (t < 0.85 && solidAt(level, knee.lerp(foot, t))) hits++;
			}
			if (hits == 0) return k;
			if (hits < bestHits) {
				bestHits = hits;
				best = k;
			}
		}
		return best;
	}

	/** Where the knee of a two-bone leg from {@code hip} to {@code foot} sits, bent towards {@code k}. */
	private static Vec3 knee(Vec3 hip, Vec3 foot, double l1, double l2, Vec3 k) {
		Vec3 line = foot.subtract(hip);
		double dist = line.length();
		if (dist < 1.0e-3) return null;
		Vec3 u = line.scale(1.0 / dist);
		dist = Mth.clamp(dist, Math.abs(l1 - l2) + 0.01, l1 + l2 - 0.01);
		double along = (l1 * l1 - l2 * l2 + dist * dist) / (2.0 * dist);
		double off = Math.sqrt(Math.max(0.0, l1 * l1 - along * along));
		Vec3 n = k.subtract(u.scale(k.dot(u)));
		if (n.lengthSqr() < 1.0e-6) n = new Vec3(-u.z, 0.0, u.x);
		if (n.lengthSqr() < 1.0e-6) n = new Vec3(1.0, 0.0, 0.0);
		n = n.normalize();
		return hip.add(u.scale(along)).add(n.scale(off));
	}

	/** Nothing solid on the straight line between two points, short of the last little bit. */
	private static boolean lineClear(Level level, Vec3 from, Vec3 to) {
		for (double t = 0.08; t < 0.9; t += 0.08) {
			if (solidAt(level, from.lerp(to, t))) return false;
		}
		return true;
	}

	/** Room for the body where it is drawn, from its feet to above its hips. */
	private static boolean columnClear(Level level, double x, double y, double z) {
		for (double h = 0.3; h <= 2.7; h += 0.6) {
			if (solidAt(level, new Vec3(x, y + h, z))) return false;
		}
		return true;
	}

	static boolean solidAt(Level level, Vec3 p) {
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
