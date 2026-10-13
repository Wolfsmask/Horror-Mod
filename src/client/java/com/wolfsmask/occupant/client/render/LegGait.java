package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
	/** Which of the two sets each leg steps with: every other leg down each side, the sides crossed over. */
	private static final int[] SET = new int[OccupantGeometry.LEGS];
	/** Moving: how far a planted leg may fall behind where it should be before it steps, as a share of its length. */
	private static final double STRIDE = 0.28;
	/** Slower than this, in blocks a tick, it is standing. */
	private static final double MOVING = 0.012;

	static {
		// As a spider walks: down each side from the front, every other leg in one set, and the other
		// side starting with the other set, so each set stands on both sides at once and the two take
		// turns carrying it.
		for (int i = 0; i < OccupantGeometry.LEGS; i++) {
			double a = OccupantGeometry.LEG_ANGLE[i];
			boolean right = Math.sin(a) >= 0.0;
			int rank = 0;
			for (int j = 0; j < OccupantGeometry.LEGS; j++) {
				double b = OccupantGeometry.LEG_ANGLE[j];
				if (j != i && (Math.sin(b) >= 0.0) == right && Math.cos(b) > Math.cos(a)) rank++;
			}
			SET[i] = (rank + (right ? 1 : 0)) % 2;
		}
	}
	/** Further than this from the drawn body and it has moved by other means: start over. */
	private static final double SNAP = 4.0;

	/** How long a leg takes to go into what it takes hold of, in ticks: a thrust, not a reach. */
	private static final float REACH_IN = 3.5f;
	/** How many of its legs it will put through things: it has ten, and stands on two. */
	private static final int LEGS_FREE = LEGS - 2;
	/**
	 * How far out a leg is put down, at most, as a share of its length: never at full stretch, so
	 * the knee is always bent and the leg looks like it carries something.
	 */
	private static final double COMFORT = 0.86;

	/** Where the body is drawn: behind the entity, catching up in shoves, and carried over its feet. */
	private Vec3 body;
	/** Where the body would be drawn without its weight shifting from side to side: just behind the entity. */
	private Vec3 trail;
	/** How far its weight is carried to its right (blocks; to its left, less than nothing), and how fast. */
	private double shift;
	private double shiftRate;
	/** How far it has drawn itself in, crouched down into its own legs (blocks below its feet), eased. */
	private float coil;
	/** How far it has drawn its hips down to strike, as the server says (blocks), eased. */
	private float coilDrop;
	/** The leg it has lifted and drawn back to strike with, or -1; kept a moment after, for the blow. */
	private int cocked = -1;
	private float cockedUntil;
	/** How far down into its legs it draws itself to strike, at the most, in blocks. */
	private static final float COIL_DEPTH = 0.5f;
	/** Which turn of the gait it is, and in which turn each leg last stepped: once a turn each. */
	private int turn;
	private final int[] steppedIn = new int[LEGS];
	/** Until when the set whose turn it is all goes together, any of them that has far to go. */
	private float turnOpen;
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
	 * Its weight: how far the body has settled down onto its legs, in model pixels, and how fast it
	 * is going down (or coming back up). Each leg that comes down takes the body down onto it a
	 * little, and it rises again; the more of its legs are off the ground at once, the lower it rides.
	 */
	private float sag;
	private float sagRate;
	/** Whether each leg, as last drawn, went into something whichever way its knee bent. */
	private final boolean[] stuck = new boolean[LEGS];
	/** Whether each leg's hold was only the best a tight place had: then it is not stepped off for that. */
	private final boolean[] loose = new boolean[LEGS];
	/** Whether the hold {@link #findHold} last found was only one that would do. */
	private boolean madeDo;
	/** Each leg's knee as last worked out, and the hip and point it was worked out for. */
	private final Vec3[] bendFor = new Vec3[LEGS];
	private final Vec3[] bendHip = new Vec3[LEGS];
	private final Vec3[] bendAt = new Vec3[LEGS];
	private final int[] bendHits = new int[LEGS];
	/** How many points of the leg last tried by {@link #kneeBend} were in something, its knee bent the best way there was. */
	private int lastHits;

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
		boolean fresh = trail == null || trail.distanceTo(real) > SNAP || Math.abs(trail.y - real.y) > 1.5;
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
		if (fresh) {
			sag = 0.0f;
			sagRate = 0.0f;
			shift = 0.0;
			shiftRate = 0.0;
			coil = 0.0f;
		}
		// Drawn down into itself: the entity lower than the ground under its feet is it crouched,
		// coiled to spring; its core curls in with it (see bend()).
		// Or drawn down to strike, as the server says: then the hips go down with it here.
		float coilRate = (float) (1.0 - Math.exp(-0.6 * dt));
		float wantDrop = entity.getCoil() * COIL_DEPTH;
		// Letting go of it is all at once: the spring released.
		float dropEase = wantDrop < coilDrop ? (float) (1.0 - Math.exp(-1.6 * dt)) : coilRate;
		coilDrop = fresh ? wantDrop : coilDrop + (wantDrop - coilDrop) * dropEase;
		float wantCoil = (float) Mth.clamp(groundUnder(level, real) - real.y, 0.0, 0.8);
		coil = fresh ? wantCoil : coil + (wantCoil - coil) * coilRate;
		state.coil = Math.max(coil, coilDrop);
		double px = scale / 16.0;                             // model pixels to blocks
		// Its weight on its legs takes the hips down with the rest of the fold.
		float coilPx = (float) (coilDrop / Math.max(px, 1.0e-3));
		dropPx = OccupantFit.CROUCH_DROP * fitCrouch + sag + coilPx;
		state.sag = sag + coilPx;

		double yaw = Math.toRadians(state.bodyRot);
		drawnYaw = yaw;
		drawnDrop = dropPx;
		bend(state);
		boolean arrived = false;
		if (fresh) {
			// Arrived from nowhere: already standing, every leg already braced.
			arrived = true;
			body = real;
			trail = real;
			for (int i = 0; i < LEGS; i++) {
				foot[i] = findHold(i, level, yaw, OccupantGeometry.LEG_LENGTH[i] * px, root(i, px, dropPx), Vec3.ZERO);
				loose[i] = madeDo;
				swingStart[i] = -1.0f;
				drawn[i] = null;
				bendNow[i] = null;
				bendFor[i] = null;
				stuck[i] = false;
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
		Vec3 gap = new Vec3(real.x - trail.x, 0.0, real.z - trail.z);
		double lag = gap.length();
		double k = 1.0 - Math.exp(-0.8 * dt);
		double bx = trail.x + gap.x * k;
		double bz = trail.z + gap.z * k;
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
		trail = new Vec3(bx, real.y, bz);

		// Lean into the way it is going, in its own frame: hardest when it runs.
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // the way it faces
		double rx = -fz, rz = fx;                             // to its right
		// Its weight over its feet: going along, the body is carried a little towards the side the
		// set of legs holding it stands on, and back across as the other set takes it, the top of it
		// leaning the other way against it; standing, it settles back over the middle.
		double support = 0.0;
		int bearing = 0;
		for (int i = 0; i < LEGS; i++) {
			if (SET[i] == stepping || foot[i] == null || swingStart[i] >= 0.0f || holds[i] >= 0) continue;
			support += (foot[i].x - trail.x) * rx + (foot[i].z - trail.z) * rz;
			bearing++;
		}
		double carry = Math.min(3.2, 1.3 + speed * 14.0) * px;
		double wantShift = moving && bearing > 0 && Math.abs(support / bearing) > 0.03 ? Math.signum(support) * carry : 0.0;
		for (float left = dt; left > 1.0e-4f; left -= 1.0f) {
			double h = Math.min(1.0, left);
			shiftRate += (0.2 * (wantShift - shift) - 0.55 * shiftRate) * h;
			shift += shiftRate * h;
		}
		body = new Vec3(bx + rx * shift, real.y, bz + rz * shift);
		float wantF = (float) Mth.clamp((velocity.x * fx + velocity.z * fz) * 1.6 + (gap.x * fx + gap.z * fz) * 0.3, -0.5, 0.5);
		float wantS = (float) Mth.clamp((velocity.x * rx + velocity.z * rz) * 1.2 + (gap.x * rx + gap.z * rz) * 0.3
				- shift / Math.max(px, 1.0e-3) * 0.06, -0.5, 0.5);
		float ease = (float) (1.0 - Math.exp(-0.2 * dt));
		leanForward += (wantF - leanForward) * ease;
		leanSide += (wantS - leanSide) * ease;
		bend(state);

		// Legs.
		int swinging = 0;
		for (int i = 0; i < LEGS; i++) if (swingStart[i] >= 0.0f) swinging++;
		boolean recheck = (int) now / 20 != lastCheck;
		lastCheck = (int) now / 20;

		// Moving, one set of legs steps while the other holds, then the other: quicker the faster it
		// goes. The turn passes only once every leg of the set that stepped is down again, so it is
		// always standing on one whole set of legs, never on a few.
		float stepTicks = (float) Mth.clamp(1.1 / Math.max(speed, 1.0e-3) * 0.5, chasing ? 2.5 : 3.5, 7.0);
		boolean setDown = true;
		for (int i = 0; i < LEGS; i++) {
			if (SET[i] == stepping && swingStart[i] >= 0.0f && now - swingStart[i] < swingTime[i]) setDown = false;
		}
		if (moving && now >= setUntil && setDown) {
			stepping ^= 1;
			setUntil = now + stepTicks + 0.5f;
			turn++;
			turnOpen = now + stepTicks * 0.4f;
		}
		Vec3 heading = moving ? velocity.scale(1.0 / speed) : Vec3.ZERO;

		// Which legs are worst off, and so are first to let go.
		double[] score = new double[LEGS];
		cock(entity, now, yaw);
		boolean anyHeld = held(entity, level, now, yaw, px, dropPx);
		int landed = 0;
		for (int i = 0; i < LEGS; i++) {
			score[i] = 0.0;
			if (holdAt[i] != null || now < lingerUntil[i] || i == cocked) continue;
			if (swingStart[i] >= 0.0f) {
				if (now - swingStart[i] >= swingTime[i]) {
					swingStart[i] = -1.0f;
					if (foot[i] != null) landed++;
				} else {
					continue;
				}
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
				// At the start of its set's turn, any leg of it with some way to go goes with the rest.
				boolean together = SET[i] == stepping && now < turnOpen && steppedIn[i] != turn;
				score[i] = drift > (together ? STRIDE * 0.45 : STRIDE) && steppedIn[i] != turn ? drift : 0.0;
				if (stretch > 0.95) score[i] = 10.0;          // about to be torn off it: now, whichever set
			} else {
				double drift = ideal == null ? 0.0 : Math.hypot(foot[i].x - ideal.x, foot[i].z - ideal.z) / reach;
				score[i] = Math.max((stretch - 0.9) * 10.0, drift - 0.55);
			}
			if (recheck && !heldBy(level, foot[i])) score[i] = 2.0;   // what it held is gone
			// The body has moved so that the leg, however its knee goes, would be through something:
			// it takes a new hold rather than be drawn through it.
			if (stuck[i]) score[i] = Math.max(score[i], moving ? 10.0 : 3.0);
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
				steppedIn[best] = turn;
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
		settle(landed, moving, speed, dt);

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
				// Picked up quickly, carried over, and put down: over where it lands before it is
				// down, and then straight down onto it, faster and faster, so it lands with weight
				// and does not drift on to its hold.
				float f = Mth.clamp((now - swingStart[i]) / swingTime[i], 0.0f, 1.0f);
				float g = Math.min(1.0f, f / 0.8f);
				float e = g * g * (3.0f - 2.0f * g);
				double high = Math.min(0.9, 0.12 + from[i].distanceTo(at) * 0.45);
				double lift = f < 0.3f ? high * Math.sin(f / 0.3 * Math.PI * 0.5)
						: high * (1.0 - Math.pow((f - 0.3) / 0.7, 2.0));
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
			if (i == cocked && !holding) at = cockedAt(i, yaw, reach, hip);
			if (at == null) at = dangle(i, level, yaw, reach, hip, clock);
			if (at == null) {
				state.legPlanted[i] = false;
				state.legBendSet[i] = false;
				drawn[i] = null;
				continue;
			}
			// A foot that is down is locked where it is, exactly, until it steps; a step is already
			// smooth along its arc. Anything else the leg's point does it gets to smoothly: a free leg
			// feeling about, the floor under a hanging leg stepping down. Never a jump, never a slide.
			if (drawn[i] == null || arrived || holding || foot[i] != null) {
				drawn[i] = at;
			} else {
				// A step is already eased along its arc: followed closely, so the foot comes down where it lands.
				double follow = 1.0 - Math.exp(-(swingStart[i] >= 0.0f ? 2.5 : 0.7) * dt);
				drawn[i] = drawn[i].lerp(at, follow);
			}
			at = drawn[i];
			// Which way the knee goes: the first way that keeps the whole leg out of the blocks,
			// eased round to it, so a knee never flips from one side to the other in a frame.
			Vec3 want = bendOf(i, level, yaw, px, hip, at);
			stuck[i] = !holding && !loose[i] && swingStart[i] < 0.0f && foot[i] != null && bendHits[i] > 0;
			if (want == null) want = bendNow[i] != null ? bendNow[i] : outward(i, yaw).add(0.0, 0.3, 0.0);
			if (bendNow[i] == null || arrived) {
				bendNow[i] = want;
			} else {
				Vec3 eased = bendNow[i].lerp(want, 1.0 - Math.exp(-0.5 * dt));
				bendNow[i] = eased.lengthSqr() > 1.0e-4 ? eased : want;
			}
			// And now and then, by itself, a knee jerks: the foot stays exactly where it is.
			Vec3 bend = bendNow[i].add(twitch(i, yaw, now));
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
		state.eyes = null;
		Entity viewer = net.minecraft.client.Minecraft.getInstance().player;
		if (viewer != null) {
			float[] n = OccupantGeometry.NECK_TO_EYES, sp = OccupantGeometry.SPINE_TO_NECK;
			double nc = Math.cos(neckX), ns = Math.sin(neckX);
			Vec3 eyes = fromSpine(sp[0] + n[0], sp[1] + n[1] * nc - n[2] * ns, sp[2] + n[1] * ns + n[2] * nc, px, dropPx);
			state.eyes = eyes;
			// Whether the game is aiming its head at them or at something else: the angle between the
			// two, both from where the game puts its eyes.
			Vec3 from = new Vec3(real.x, real.y + entity.getEyeHeight(), real.z);
			Vec3 to = viewer.getEyePosition().subtract(from);
			double gy = Math.toRadians(entity.getYHeadRot()), gp = Math.toRadians(entity.getXRot());
			Vec3 aim = new Vec3(-Math.sin(gy) * Math.cos(gp), -Math.sin(gp), Math.cos(gy) * Math.cos(gp));
			double len = to.length();
			state.aimOff = len < 1.0e-4 ? 0.0f : (float) Math.toDegrees(Math.acos(Mth.clamp(aim.dot(to) / len, -1.0, 1.0)));
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
				// The leg it drew back to strike with is the one that goes in.
				if (cocked >= 0 && holds[cocked] < 0) leg = cocked;
				if (leg < 0) break;
				if (leg == cocked) cocked = -1;
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
			// In at the back and out through the front, well past it, so the point is seen; but
			// never on into the ground under it or a wall behind it: the point stops in the first
			// thing past it, pinning it there.
			Vec3 hip = root(i, px, dropPx);
			Vec3 through = centre.subtract(hip);
			Vec3 point = centre;
			if (through.lengthSqr() >= 1.0e-4) {
				Vec3 on = through.normalize();
				double past = held.getBbWidth() * 0.5 + 0.55;
				for (double s = 0.1; s <= past + 1.0e-6; s += 0.1) {
					if (solidAt(level, centre.add(on.scale(s)))) break;
					point = centre.add(on.scale(s));
				}
			}
			holdAt[i] = point;
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
				+ OccupantPose.spineBend(state.mode, veiled) + coil * OccupantPose.COIL_BEND;
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
		loose[i] = madeDo;
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

		double px = reach / OccupantGeometry.LEG_LENGTH[i];
		// In a tight place nothing may fit as well as that: then the first hold that will do at all,
		// a leg nearly straight, rather than a leg hanging in the air with nowhere to go.
		Vec3 makeDo = null;
		madeDo = false;

		// Something beside it, near enough to brace on with the knee still bent.
		for (double t = 0.3; t <= reach * 0.95; t += 0.2) {
			Vec3 p = new Vec3(hip.x + d.x * t, body.y + height, hip.z + d.z * t);
			if (solidAt(level, p)) {
				Vec3 face = new Vec3(p.x - d.x * 0.06, p.y, p.z - d.z * 0.06);
				if (fits(i, level, yaw, px, reach, hip, face)) return face;
				if (doesForNow(level, reach, hip, face)) makeDo = face;
				break;
			}
		}
		if (high) {
			// Overhead, in a cramped space: brace against the ceiling.
			for (double up = 0.5; up <= reach * 0.9; up += 0.25) {
				Vec3 p = new Vec3(hip.x + d.x * 0.6, hip.y + up, hip.z + d.z * 0.6);
				if (solidAt(level, p)) {
					Vec3 face = new Vec3(p.x, Math.floor(p.y) - 0.04, p.z);
					if (fits(i, level, yaw, px, reach, hip, face)) return face;
					if (makeDo == null && doesForNow(level, reach, hip, face)) makeDo = face;
					break;
				}
			}
		}

		// The ground: where it would rest, or failing that a little nearer, further, or round to
		// either side, wherever the whole leg can be put down without going through anything. The
		// legs that reach high, with nothing beside or over them, come down too, wide of the body.
		Vec3 g = ideal(i, yaw, reach, hip);
		madeDo = makeDo != null;
		if (g == null) return makeDo;
		double r = Math.hypot(g.x - hip.x, g.z - hip.z) * (high ? 1.25 : 1.0);
		for (double near : new double[]{1.0, 0.8, 1.15, 0.6}) {
			for (double turn : new double[]{0.0, 0.35, -0.35}) {
				double c = Math.cos(turn), sn = Math.sin(turn);
				double dx = d.x * c - d.z * sn, dz = d.x * sn + d.z * c;
				Vec3 at = ground(level, hip.x + dx * r * near + lead.x, hip.z + dz * r * near + lead.z);
				if (at == null) continue;
				if (fits(i, level, yaw, px, reach, hip, at)) return at;
				if (makeDo == null && doesForNow(level, reach, hip, at)) makeDo = at;
			}
		}
		madeDo = makeDo != null;
		return makeDo;
	}

	/** Whether a hold will do at all: in reach, and nothing in the straight way to it. */
	private static boolean doesForNow(Level level, double reach, Vec3 hip, Vec3 at) {
		return at.distanceTo(hip) <= reach * 0.97 && lineClear(level, hip, at);
	}

	/**
	 * The top of whatever is underfoot at (x, z), from a step up to three down from where it
	 * stands, or null if there is nothing, or the spot is inside a wall or a hill.
	 */
	@Nullable
	private Vec3 ground(Level level, double x, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(body.y + 1.0);
		for (int y = top; y >= top - 3; y--) {
			pos.set(Mth.floor(x), y, Mth.floor(z));
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				if (y == top) return null;
				// Just on it, not in it: the point rests on the surface.
				return new Vec3(x, y + shape.max(Direction.Axis.Y) + 0.02, z);
			}
		}
		return null;
	}

	/**
	 * Whether leg i can be put on {@code at}: near enough that its knee is still bent, not so near
	 * it is folded up, and some way for its knee to go with no part of the leg inside anything.
	 */
	private boolean fits(int i, Level level, double yaw, double px, double reach, Vec3 hip, Vec3 at) {
		double dist = at.distanceTo(hip);
		if (dist > reach * COMFORT || dist < reach * 0.3) return false;
		return kneeBend(i, level, yaw, px, hip, at) != null && lastHits == 0;
	}

	/**
	 * {@link #kneeBend}, but only worked out again once the hip or the point has moved: most of the
	 * time neither has, and every leg is drawn every frame.
	 */
	@Nullable
	private Vec3 bendOf(int i, Level level, double yaw, double px, Vec3 hip, Vec3 at) {
		if (bendFor[i] != null && bendHip[i].distanceToSqr(hip) < 0.0025 && bendAt[i].distanceToSqr(at) < 0.0025) return bendFor[i];
		Vec3 k = kneeBend(i, level, yaw, px, hip, at);
		bendFor[i] = k;
		bendHip[i] = hip;
		bendAt[i] = at;
		bendHits[i] = k == null ? 0 : lastHits;
		return k;
	}

	/**
	 * Its weight, one frame on: a spring that each leg coming down presses down, the body riding
	 * lower the more of its legs are off the ground and the faster it goes, and coming back up,
	 * just past, and settling.
	 */
	private void settle(int landed, boolean moving, double speed, float dt) {
		int up = 0;
		for (int i = 0; i < LEGS; i++) if (swingStart[i] >= 0.0f) up++;
		float rest = (moving ? 0.8f + (float) Math.min(1.2, speed * 6.0) : 0.0f) + 1.6f * up / LEGS;
		sagRate += 0.55f * landed;
		for (float left = dt; left > 1.0e-4f; left -= 1.0f) {
			float h = Math.min(1.0f, left);
			sagRate += (0.25f * (rest - sag) - 0.7f * sagRate) * h;
			sag += sagRate * h;
		}
		sag = Mth.clamp(sag, -1.5f, 4.0f);
	}

	/**
	 * Drawn down to strike, it lifts one leg, the one most in front of it, up off whatever it held
	 * and back; let go, that leg is held ready a moment longer, for whatever it is about to go into.
	 */
	private void cock(OccupantEntity entity, float now, double yaw) {
		if (entity.getCoil() > 0.25f) {
			cockedUntil = now + 10.0f;
			if (cocked >= 0) return;
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double best = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < LEGS; i++) {
				if (holds[i] >= 0 || high(i)) continue;
				Vec3 d = outward(i, yaw);
				double ahead = d.x * fx + d.z * fz;
				if (ahead > best) {
					best = ahead;
					cocked = i;
				}
			}
			if (cocked >= 0) {
				// Off its hold: from here it is drawn to where it is cocked, not locked to the ground.
				foot[cocked] = null;
				swingStart[cocked] = -1.0f;
			}
		} else if (cocked >= 0 && now > cockedUntil) {
			cocked = -1;
		}
	}

	/** Where the cocked leg is held: folded up beside the body, high, and drawn back. */
	private Vec3 cockedAt(int i, double yaw, double reach, Vec3 hip) {
		Vec3 d = outward(i, yaw);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		return hip.add(d.x * reach * 0.25 - fx * reach * 0.2, reach * 0.42, d.z * reach * 0.25 - fz * reach * 0.2);
	}

	/** The top of whatever it stands on, under {@code at}: from a block above its feet down to two below. */
	static double groundUnder(Level level, Vec3 at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(at.y + 0.99);
		for (int y = top; y >= top - 2; y--) {
			pos.set(Mth.floor(at.x), y, Mth.floor(at.z));
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) return y + shape.max(Direction.Axis.Y);
		}
		return at.y;
	}

	/**
	 * Leg i's knee, jerked: now and then, at no time you could guess and on its own, a leg gives a
	 * small quick twitch, up and to one side, and settles; and under that it is never quite still,
	 * as if something were running along it. Added to the way the knee bends, so a planted foot
	 * stays exactly where it is.
	 */
	private Vec3 twitch(int i, double yaw, float now) {
		Vec3 d = outward(i, yaw);
		Vec3 side = new Vec3(-d.z, 0.0, d.x);
		double tremor = 0.05 * Math.sin(now * 0.43 + i * 2.1) * Math.sin(now * 0.13 + i * 0.7);
		float period = 34.0f + 26.0f * rand(i, 41);
		float shifted = now + rand(i, 43) * period;
		int window = (int) Math.floor(shifted / period);
		Vec3 out = new Vec3(0.0, tremor, 0.0);
		if (rand(i * 31 + window, 47) < 0.5f) {
			float u = shifted - window * period - (4.0f + rand(i * 31 + window, 53) * (period - 14.0f));
			if (u >= 0.0f && u < 9.0f) {
				double pulse = u < 1.0f ? u : Math.exp(-(u - 1.0f) / 1.6f);
				double way = rand(i * 31 + window, 59) < 0.5f ? -1.0 : 1.0;
				out = out.add(side.scale(way * 0.45 * pulse)).add(0.0, 0.35 * pulse, 0.0);
			}
		}
		return out;
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
		Vec3 makeDo = null;
		for (int tries = 0; tries < 6; tries++) {
			Vec3 p = new Vec3(hip.x + d.x * out, hip.y - down, hip.z + d.z * out);
			// Not into the floor: stop just above whatever is under it.
			for (double y = hip.y; y > p.y; y -= 0.25) {
				if (solidAt(level, new Vec3(p.x, y, p.z))) {
					p = new Vec3(p.x, Math.floor(y) + 1.15, p.z);
					break;
				}
			}
			if (!solidAt(level, p) && lineClear(level, hip, p)) {
				// Best with the knee clear of everything too; failing that, the first that is clear at all.
				if (kneeBend(i, level, yaw, reach / OccupantGeometry.LEG_LENGTH[i], hip, p) != null && lastHits == 0) return p;
				if (makeDo == null) makeDo = p;
			}
			out *= 0.6;                                   // tuck it in closer to the body
			down *= 0.8;
		}
		return makeDo;
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
				d.add(0.0, 0.3, 0.0), d.add(0.0, 0.9, 0.0), new Vec3(d.x * 0.25, 1.0, d.z * 0.25),
				side.add(d).add(0.0, 0.5, 0.0), side.scale(-1.0).add(d).add(0.0, 0.5, 0.0),
				side.add(0.0, 0.3, 0.0), side.scale(-1.0).add(0.0, 0.3, 0.0),
				d.scale(-1.0).add(0.0, 0.6, 0.0), new Vec3(d.x * 0.3, -1.0, d.z * 0.3)};
		Vec3 best = null;
		int bestHits = Integer.MAX_VALUE;
		lastHits = 0;
		for (Vec3 k : tries) {
			Vec3 knee = knee(hip, foot, l1, l2, k);
			if (knee == null) return null;
			int hits = hits(level, hip, knee, foot);
			if (hits == 0) return k;
			if (hits < bestHits) {
				bestHits = hits;
				best = k;
			}
		}
		lastHits = bestHits;
		return best;
	}

	/**
	 * How many points along a leg from {@code hip} through {@code knee} to {@code foot} are inside
	 * something, a fifth of a block apart, short of the last bit of it, where it rests on its hold.
	 */
	private static int hits(Level level, Vec3 hip, Vec3 knee, Vec3 foot) {
		int hits = solidAt(level, knee) ? 1 : 0;
		double a = hip.distanceTo(knee), b = knee.distanceTo(foot);
		int na = Math.max(3, (int) Math.ceil(a / 0.2)), nb = Math.max(3, (int) Math.ceil(b / 0.2));
		for (int s = 1; s < na; s++) if (solidAt(level, hip.lerp(knee, (double) s / na))) hits++;
		for (int s = 1; s < nb; s++) {
			double t = (double) s / nb;
			if (b * (1.0 - t) < 0.22) break;
			if (solidAt(level, knee.lerp(foot, t))) hits++;
		}
		return hits;
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
