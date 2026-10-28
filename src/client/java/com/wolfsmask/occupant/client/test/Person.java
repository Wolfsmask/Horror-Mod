package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whoever was holding the camera, once it has knocked them down: a person built of blocks the
 * size and colours of a player's body, but jointed as a body is, at the knees and elbows and the
 * small of the back, so they can do what someone does to get away from something.
 * <p>
 * They try everything, one thing after another, and each fails. Thrown back, they land flat and
 * the breath goes out of them. They come up onto one arm and shove themselves backwards along the
 * ground with their heels. When it is at their feet they kick at it. They roll over onto their
 * front, get up onto their hands and knees and try to stand, one foot under them, and the foot goes
 * out from under them and they go down flat on their face. They claw their way along the ground on
 * their stomach, one arm over the other, looking back at it. It takes them by the ankle and drags
 * them back, their hands raking the dirt; and it comes down on them.
 * <p>
 * Everything is worked out a tick at a time, in order: the body has weight and lags behind
 * where it is going, and swings past and settles. Nothing of it is ever below the ground, and
 * whatever is planted on the ground stays exactly where it was put until it is picked up.
 */
final class Person {
	/** A player's body is 32 of these tall, scaled down as the game scales players. */
	static final double PX = 0.9375 / 16.0;

	/** One block of the body: what it is made of, and where its unit cube goes (row-major 4x4, world). */
	record Part(String name, String block, double[] m) {
	}

	static final String SHIRT = "minecraft:cyan_concrete", PANTS = "minecraft:blue_concrete", SHOES = "minecraft:gray_concrete",
			SKIN = "minecraft:white_terracotta", HAIR = "minecraft:brown_concrete", EYES = "minecraft:black_concrete";
	/** Every part, in the order {@link #pose} gives them, with what it is made of. */
	static final String[][] PARTS = {
			{"pelvis", SHIRT}, {"chest", SHIRT}, {"head", SKIN}, {"hair", HAIR}, {"hair_back", HAIR}, {"eye_l", EYES}, {"eye_r", EYES},
			{"arm_l", SHIRT}, {"forearm_l", SKIN}, {"arm_r", SHIRT}, {"forearm_r", SKIN},
			{"thigh_l", PANTS}, {"shin_l", PANTS}, {"shoe_l", SHOES}, {"thigh_r", PANTS}, {"shin_r", PANTS}, {"shoe_r", SHOES}};

	// What they try, in ticks after the blow. Down until SCRAMBLE; each thing until the next.
	/** On their back, up on one arm, shoving themselves back with their heels at each of SHOVES. */
	static final int SCRAMBLE = 14;
	static final int[] SHOVES = {16, 25, 34};
	/** It is at their feet: kicking at it, one leg and the other, at each of KICKS. */
	static final int KICK = 46;
	static final int[] KICKS = {47, 51, 55, 59};
	/** Over onto their front, up onto hands and knees, one foot under them to stand; it slips. */
	static final int ROLL = 62, KNEEL = 70, RISE = 78, SLIP = 88;
	/** Flat on their front, clawing their way along, one arm over the other at each of PULLS. */
	static final int CRAWL = 94;
	static final int[] PULLS = {96, 104, 112};
	/** Taken by the ankle and dragged back. */
	static final int DRAG = 118;
	/** It comes down on them. */
	static final int STRIKE = 134;

	private static final Vec3 UP = new Vec3(0, 1, 0);
	/** The way along the ground their head points, lying, and (away from it) their left as they lie on their back. */
	private final Vec3 f, left;
	private final double ground;

	// Where the body is and how it is held, each with how fast it is changing.
	private Vec3 pelvis, pelvisV = Vec3.ZERO;
	private double lift, liftV, curl, curlV, roll, rollV, nod, nodV;
	/** Where each foot and hand is: planted, or on its way somewhere. 0 is their left, 1 their right. */
	private final Vec3[] feet = new Vec3[2], hands = new Vec3[2];
	private final Vec3[] footFrom = new Vec3[2], handFrom = new Vec3[2], footTo = new Vec3[2], handTo = new Vec3[2];
	private final int[] footAt = {-99, -99}, handAt = {-99, -99};
	/** Which way each knee and elbow bends this tick, if not the usual way. */
	private final Vec3[] legPole = new Vec3[2], armPole = new Vec3[2];
	/** Where they are looking: at it, or somewhere of their own. */
	private Vec3 look;
	private int tau = -1;
	private Vec3 chest = Vec3.ZERO;
	private final double sx, sz;

	/**
	 * @param stood  where they were standing when it hit them (their feet), facing it
	 * @param toward the way they were knocked: along the ground, away from it
	 */
	Person(Vec3 stood, Vec3 toward) {
		this.f = new Vec3(toward.x, 0, toward.z).normalize();
		this.left = f.cross(UP).normalize();
		this.ground = stood.y;
		this.pelvis = stood.add(f.scale(0.2)).add(0, 0.7, 0);
		this.sx = stood.x;
		this.sz = stood.z;
		this.lift = 1.4;
	}

	/** Where their chest is, as last posed: what its legs go into. */
	Vec3 chest() {
		return chest;
	}

	/** Their right foot, as last posed: what it takes them by. */
	Vec3 ankle() {
		return feet[1] == null ? pelvis : feet[1];
	}

	/**
	 * The body this tick ({@code t} ticks after the blow, called once a tick in order), with its
	 * head turned to {@code it} (where its face is) when they are looking at it.
	 */
	List<Part> pose(int t, Vec3 it, Vec3 anchor) {
		while (tau < t) step(++tau, it);
		return build(anchor);
	}

	// ---------------------------------------------------------------- what they are doing

	private static double smooth(double x) {
		double c = Math.max(0.0, Math.min(1.0, x));
		return c * c * (3 - 2 * c);
	}

	private static double bump(double x) {
		return x <= 0 || x >= 1 ? 0.0 : Math.sin(Math.PI * x);
	}

	/** Which of {@code at} they are in at t (-1 none), each lasting {@code len} ticks. */
	private static int which(int[] at, int t, int len) {
		for (int k = at.length - 1; k >= 0; k--) if (t >= at[k] && t < at[k] + len) return k;
		return -1;
	}

	/** Their own shaking, never quite regular. */
	private static double shake(int t, double seed) {
		return Math.sin(t * 1.31 + seed) * 0.5 + Math.sin(t * 2.17 + seed * 2.3) * 0.3 + Math.sin(t * 0.53 + seed * 0.7) * 0.2;
	}

	/** A spring a tick on: towards {@code want}, with {@code w} its quickness and {@code z} its damping. */
	private static double[] spring(double x, double v, double want, double w, double z) {
		v += (w * w * (want - x) - 2 * z * w * v);
		return new double[]{x + v, v};
	}

	/**
	 * How far along the ground (along {@code f}) their hips mean to be at t: knocked back, shoved
	 * further at each shove, pushed back by their own kicks, a little on as they get up, thrown
	 * forward as they fall, pulled on by each pull of their arms, and dragged back.
	 */
	private double along(double t) {
		double d = 1.3;
		for (int s : SHOVES) d += 0.16 * smooth((t - s) / 5.0);
		for (int k : KICKS) d += 0.035 * smooth((t - k - 1) / 3.0);
		d += 0.12 * smooth((t - RISE) / 8.0);
		d += 0.42 * smooth((t - SLIP - 1) / 5.0);
		for (int p : PULLS) d += 0.18 * smooth((t - p - 4) / 6.0);
		d -= 0.45 * smooth((t - DRAG - 1) / 4.0) + 0.42 * smooth((t - DRAG - 8) / 5.0);
		return d;
	}

	/** How far to their left (as they lay first) their hips have gone: rolling over carries them that way. */
	private double aside(double t) {
		return 0.3 * smooth((t - ROLL) / 8.0);
	}

	private Vec3 origin() {
		return new Vec3(sx, ground, sz);
	}

	private void step(int t, Vec3 it) {
		boolean down = t >= 7;
		double wantLift, wantCurl, wantRoll, wantNod = 0, height;
		int k;
		double u;
		look = it;
		if (t < SCRAMBLE) {
			// Knocked over backwards, flat, winded; the head hits the ground.
			wantLift = down ? 0.0 : 1.4 * (1 - smooth((t - 1) / 6.0));
			wantCurl = 0.05;
			wantRoll = 0.0;
			wantNod = t < 7 ? -0.3 : -0.45;
			height = 2 * PX;
		} else if (t < KICK) {
			// Up on one arm, shoving back: each shove throws them back, then they come forward over it.
			k = which(SHOVES, t, 10);
			u = k >= 0 ? (t - SHOVES[k]) / 10.0 : 0;
			wantLift = (t < 20 ? 0.55 * smooth((t - SCRAMBLE) / 6.0) : 0.55) - 0.14 * bump(u / 0.5);
			wantCurl = 0.18 + 0.03 * Math.sin(t * 0.63);
			wantRoll = -0.22 + (k >= 0 ? (k % 2 == 0 ? 0.08 : -0.08) * bump(u) : 0);
			height = 2 * PX + PX * Math.sin(Math.max(0, wantLift));
		} else if (t < ROLL) {
			// Leaning back on both hands, kicking: the whole body jolts back with each one.
			k = which(KICKS, t, 4);
			u = k >= 0 ? (t - KICKS[k]) / 4.0 : 0;
			wantLift = 0.42 - 0.1 * bump(u);
			wantCurl = 0.3;
			wantRoll = k >= 0 ? (k % 2 == 0 ? 0.12 : -0.12) * bump(u) : 0.0;
			height = 2 * PX + PX * Math.sin(wantLift);
		} else if (t < KNEEL) {
			// Over onto their front, the way of their left side, looking back at it over their shoulder.
			wantLift = 0.05;
			wantCurl = 0.2;
			wantRoll = Math.PI;
			height = 2 * PX + 0.06 * bump((t - ROLL) / 8.0);
		} else if (t < RISE) {
			// Up onto hands and knees.
			double g = smooth((t - KNEEL) / 6.0);
			wantLift = 0.1 * g;
			wantCurl = 0.05;
			wantRoll = Math.PI;
			height = 2 * PX + (0.4 - 2 * PX) * g;
		} else if (t < SLIP) {
			// One foot under them, pushing up to stand, the body coming up off the hands.
			double g = smooth((t - RISE) / 9.0);
			wantLift = 0.1 + 1.0 * g;
			wantCurl = 0.1 + 0.35 * g;
			wantRoll = Math.PI;
			height = 0.4 + 0.24 * g;
			look = pelvis.add(f.scale(4)).add(0, 1.2, 0);
		} else if (t < CRAWL) {
			// The foot goes: down forwards, flat on their face, hands out to catch themselves.
			wantLift = 0.02;
			wantCurl = 0.0;
			wantRoll = Math.PI;
			wantNod = t < SLIP + 4 ? 0.3 : 0.0;
			height = 2 * PX;
			look = pelvis.add(f.scale(3)).subtract(0, 0.5, 0);
		} else if (t < DRAG) {
			// On their stomach, chest up off the ground, clawing their way along; a look back at it.
			k = which(PULLS, t, 8);
			u = k >= 0 ? (t - PULLS[k]) / 8.0 : 0;
			wantLift = 0.16 + 0.06 * bump(u);
			wantCurl = -0.12;
			wantRoll = Math.PI + (k >= 0 ? (k % 2 == 0 ? -0.14 : 0.14) * bump(u) : 0);
			height = 2 * PX;
			boolean back = t >= PULLS[1] - 2 && t < PULLS[1] + 6;
			look = back ? it : pelvis.add(f.scale(4)).add(0, 0.6, 0);
		} else if (t < STRIKE) {
			// Dragged back by the ankle, flat, the arms out ahead raking the dirt; looking back at it,
			// then ahead again, at anything.
			wantLift = 0.06;
			wantCurl = -0.08;
			wantRoll = Math.PI + 0.1 * Math.sin(t * 0.7);
			height = 2 * PX;
			look = t < DRAG + 5 ? it : pelvis.add(f.scale(4)).add(0, 0.4, 0);
		} else {
			// Its legs come down into their back: flattened, the head down.
			wantLift = 0.0;
			wantCurl = 0.0;
			wantRoll = Math.PI;
			wantNod = 0.3;
			height = 2 * PX;
			look = null;
		}
		// Where they mean to be.
		Vec3 want = down ? origin().add(f.scale(along(t))).add(left.scale(aside(t))).add(0, height, 0)
				: origin().add(f.scale(0.2 + 1.1 * smooth((t - 1) / 6.0))).add(0, 0.7 * (1 - smooth((t - 2) / 5.0)) + height, 0);
		// The body has weight: it gets there on springs, and goes a little past. Dragged, it does not
		// lag: it is pulled.
		double w = t >= DRAG && t < STRIKE ? 0.7 : down ? 0.45 : 0.6;
		double[] s = spring(pelvis.x, pelvisV.x, want.x, w, 0.55);
		double px = s[0], pvx = s[1];
		s = spring(pelvis.y, pelvisV.y, want.y, w, 0.7);
		double py = s[0], pvy = s[1];
		s = spring(pelvis.z, pelvisV.z, want.z, w, 0.55);
		pelvis = new Vec3(px, Math.max(py, ground + 1.5 * PX), s[0]);
		pelvisV = new Vec3(pvx, pvy, s[1]);
		s = spring(lift, liftV, wantLift, down ? 0.35 : 0.7, 0.6);
		lift = s[0];
		liftV = s[1];
		s = spring(curl, curlV, wantCurl, 0.3, 0.6);
		curl = s[0];
		curlV = s[1];
		s = spring(roll, rollV, wantRoll, t >= ROLL && t < KNEEL ? 0.32 : 0.3, 0.6);
		roll = s[0];
		rollV = s[1];
		s = spring(nod, nodV, wantNod, 0.5, 0.5);
		nod = s[0];
		nodV = s[1];
		// Things that hit the whole body at once.
		if (t == SLIP + 4) {
			liftV -= 0.05;
			pelvisV = pelvisV.add(0, -0.03, 0);
		}
		if (t == STRIKE) {
			liftV -= 0.06;
			rollV += 0.12;
			pelvisV = pelvisV.add(0, -0.03, 0);
		}
		legPole[0] = legPole[1] = armPole[0] = armPole[1] = null;
		feet(t, it);
		hands(t, it);
	}

	/** The point on the ground under p, a hair above it. */
	private Vec3 onGround(Vec3 p) {
		return new Vec3(p.x, ground + 0.5 * PX, p.z);
	}

	private void plantFoot(int i, Vec3 to, int t) {
		footFrom[i] = feet[i] != null ? feet[i] : to;
		footTo[i] = to;
		footAt[i] = t;
	}

	private void plantHand(int i, Vec3 to, int t) {
		handFrom[i] = hands[i] != null ? hands[i] : to;
		handTo[i] = to;
		handAt[i] = t;
	}

	/** Put {@code to} as where foot i is going, unless it is already going about there. */
	private void footTo(int i, Vec3 to, int t, double slack) {
		if (footTo[i] == null || footTo[i].distanceTo(to) > slack) plantFoot(i, to, t);
	}

	private void handTo(int i, Vec3 to, int t, double slack) {
		if (handTo[i] == null || handTo[i].distanceTo(to) > slack) plantHand(i, to, t);
	}

	/** On its way from where it was to where it is going: lifted off the ground in between. */
	private static Vec3 carried(Vec3 from, Vec3 to, int since, int over, double high) {
		double g = Math.min(1.0, Math.max(0.0, since / (double) over));
		double e = g * g * (3 - 2 * g);
		return from.lerp(to, e).add(0, high * Math.sin(Math.PI * g), 0);
	}

	private void feet(int t, Vec3 it) {
		Frame pl = pelvisFrame();
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 hip = point(pl, 1.9 * side, 0, 0);
			Vec3 out = pl.x.scale(side);
			if (t < 7) {
				// Going over backwards: the feet come up off the ground.
				feet[i] = hip.subtract(f.scale(0.45)).add(0, 0.25 + 0.1 * side, 0);
				footTo[i] = null;
				continue;
			}
			if (t < SCRAMBLE - 2) {
				// Down flat, legs out straight along the ground.
				feet[i] = onGround(hip.subtract(f.scale(0.68))).add(out.scale(0.08));
				footTo[i] = null;
				continue;
			}
			if (t < KICK) {
				// Heels dug in in front of them, knees up; re-planted after each shove of that leg.
				int k = which(SHOVES, t, 10);
				double u = k >= 0 ? (t - SHOVES[k]) / 10.0 : 0;
				Vec3 spot = onGround(hip.subtract(f.scale(0.44)).add(out.scale(0.06)));
				boolean mine = k >= 0 && k % 2 == i;
				if (footTo[i] == null) {
					plantFoot(i, spot, t);
				} else if (mine && u >= 0.5 && footAt[i] < SHOVES[k] + 5) {
					plantFoot(i, onGround(spot.add(f.scale(0.08))), t);
				} else if (!mine && k >= 0 && u >= 0.7 && footTo[i].distanceTo(spot) > 0.2) {
					plantFoot(i, spot, t);
				}
				feet[i] = carried(footFrom[i], footTo[i], t - footAt[i], 4, 0.07);
				continue;
			}
			if (t < ROLL) {
				// Kicking at it: the leg driven out straight at it and drawn back, fast; the other
				// one drawn up ready, its heel on the ground.
				int k = which(KICKS, t, 4);
				Vec3 drawn = hip.subtract(f.scale(0.22)).add(0, 0.34, 0).add(out.scale(0.05));
				Vec3 want = drawn;
				if (k >= 0 && k % 2 == i) {
					double u = (t - KICKS[k]) / 4.0;
					Vec3 kicked = hip.subtract(f.scale(0.62)).add(0, 0.3 + 0.05 * side, 0).add(out.scale(0.03));
					want = u < 0.4 ? drawn.lerp(kicked, smooth(u / 0.4)) : kicked.lerp(drawn, smooth((u - 0.4) / 0.6));
				} else if (k < 0 && t >= KICKS[KICKS.length - 1] + 4) {
					want = onGround(hip.subtract(f.scale(0.5)));
				}
				feet[i] = feet[i] == null ? want : feet[i].lerp(want, 0.7);
				footTo[i] = null;
				continue;
			}
			if (t < KNEEL) {
				// Turning over: the legs go over with the body, along the ground.
				Vec3 want = onGround(hip.subtract(f.scale(0.62)));
				feet[i] = feet[i].lerp(want, 0.4);
				footTo[i] = null;
				continue;
			}
			if (t < RISE || (t < SLIP && i == 0)) {
				// Kneeling: knee down under the hip, the shin back along the ground.
				legPole[i] = pl.z.add(f.scale(0.4));
				footTo(i, onGround(hip.subtract(f.scale(0.38)).add(out.scale(0.03))), t, 0.12);
				feet[i] = carried(footFrom[i], footTo[i], t - footAt[i], 4, 0.05);
				continue;
			}
			if (t < SLIP) {
				// The right foot brought up under them, flat, the knee up in front.
				legPole[i] = f.add(UP.scale(0.6));
				footTo(i, onGround(hip.add(f.scale(0.2)).add(out.scale(0.05))), t, 0.3);
				feet[i] = carried(footFrom[i], footTo[i], t - footAt[i], 5, 0.12);
				continue;
			}
			if (t < CRAWL) {
				// It slips out backwards from under them; both legs go out straight behind.
				Vec3 want = onGround(hip.subtract(f.scale(0.66)).add(out.scale(0.06)));
				feet[i] = feet[i].lerp(want, i == 1 && t < SLIP + 3 ? 0.65 : 0.35);
				footTo[i] = null;
				continue;
			}
			if (t < DRAG) {
				// Flat behind them, toes dug in; the knee on the side of the pull drawn up to push.
				int k = which(PULLS, t, 8);
				Vec3 trail = onGround(hip.subtract(f.scale(0.66)).add(out.scale(0.07)));
				Vec3 want = trail;
				if (k >= 0 && k % 2 != i) {
					legPole[i] = out.add(pl.z.scale(0.5));
					want = onGround(hip.subtract(f.scale(0.42)).add(out.scale(0.24))).lerp(trail, smooth(((t - PULLS[k]) / 8.0 - 0.5) / 0.5));
				}
				feet[i] = feet[i].lerp(want, 0.45);
				footTo[i] = null;
				continue;
			}
			if (t < STRIKE) {
				// Its leg has the right foot: held up off the ground, the leg out straight behind; the
				// other one dragged along the ground.
				Vec3 want = i == 1 ? hip.subtract(f.scale(0.68)).add(0, 0.14, 0) : onGround(hip.subtract(f.scale(0.64)).add(out.scale(0.08)));
				feet[i] = feet[i].lerp(want, 0.6);
				footTo[i] = null;
				continue;
			}
			// Its legs into their back: the feet jerk up off the ground and drop.
			Vec3 want = onGround(hip.subtract(f.scale(0.64)).add(out.scale(0.08)));
			if (t < STRIKE + 2) want = want.add(0, 0.16, 0);
			feet[i] = feet[i].lerp(want, 0.6);
			footTo[i] = null;
		}
	}

	private void hands(int t, Vec3 it) {
		Frame c = chestFrame();
		Vec3 face = it == null ? pelvis.subtract(f.scale(2)).add(0, 3, 0) : it;
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 shoulder = point(c, 6 * side, 4, 0);
			Vec3 out = c.x.scale(side);
			if (t < 7) {
				// Thrown back: the arms fly up and out.
				hands[i] = shoulder.add(out.scale(0.25)).add(0, 0.35, 0).subtract(f.scale(0.1));
				handTo[i] = null;
				continue;
			}
			if (t < SCRAMBLE) {
				// Flung out where they landed, and the left one coming up off the ground.
				Vec3 flung = onGround(shoulder.add(out.scale(0.42)).subtract(f.scale(0.1)));
				if (i == 0 && t >= 11) flung = flung.add(0, 0.2, 0).subtract(f.scale(0.1));
				hands[i] = hands[i] == null ? flung : hands[i].lerp(flung, 0.4);
				handTo[i] = null;
				continue;
			}
			if (t < KICK) {
				if (i == 0) {
					// Up at it, shaking, the arm not quite straight; waved at it as they cry out.
					Vec3 to = face.subtract(shoulder).normalize();
					Vec3 across = to.cross(UP).normalize();
					Vec3 held = shoulder.add(to.scale(10 * PX * 0.86)).add(UP.scale(0.04));
					if (t >= 26 && t < 40) held = held.add(across.scale(0.1 * Math.sin(t * 0.9))).add(UP.scale(0.04 * Math.sin(t * 1.8)));
					held = held.add(across.scale(shake(t, 1.7) * 0.025)).add(UP.scale(shake(t, 4.1) * 0.02));
					armPole[i] = out.add(UP.scale(-0.6));
					hands[i] = hands[i].lerp(held, 0.55);
					handTo[i] = null;
				} else {
					// Behind them, propped on it; put down again further back when it is left behind.
					int k = which(SHOVES, t, 10);
					double u = k >= 0 ? (t - SHOVES[k]) / 10.0 : 0;
					Vec3 spot = onGround(pelvis.add(f.scale(0.26)).add(out.scale(0.24)));
					if (handTo[i] == null || (handTo[i].distanceTo(spot) > 0.16 && t - handAt[i] > 5 && (k < 0 || u > 0.55))) plantHand(i, spot, t);
					hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 4, 0.06);
				}
				continue;
			}
			if (t < ROLL) {
				// Both hands down behind them, leaning back on them to kick.
				handTo(i, onGround(shoulder.add(out.scale(0.16)).add(f.scale(0.12))), t, 0.18);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 3, 0.08);
				continue;
			}
			if (t < KNEEL) {
				// Over: the right arm reaches across them to the ground on their left and pulls; the
				// left one tucked under as they go over it.
				Vec3 spot = i == 1 ? onGround(chestPoint().add(left.scale(0.45)).add(f.scale(0.08)))
						: onGround(shoulder.add(left.scale(0.12)));
				handTo(i, spot, t, 0.2);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 4, 0.12);
				continue;
			}
			if (t < RISE) {
				// Hands and knees: hands flat under the shoulders, elbows back.
				armPole[i] = f.scale(-1).add(out.scale(0.3));
				handTo(i, onGround(shoulder.add(f.scale(0.04)).add(out.scale(0.03))), t, 0.12);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 4, 0.08);
				continue;
			}
			if (t < SLIP) {
				// Pushing up: the right hand shoves off the raised knee; the left comes up off the
				// ground and out, for balance.
				Vec3 want = i == 1 ? point(pelvisFrame(), -1.9, 0, 0).add(f.scale(0.3)).add(0, 0.12, 0)
						: shoulder.add(f.scale(0.3)).add(out.scale(0.22)).subtract(0, 0.25, 0);
				hands[i] = hands[i].lerp(want, 0.35);
				handTo[i] = null;
				continue;
			}
			if (t < CRAWL) {
				// Falling: both hands thrown out ahead to catch themselves, and slapped down.
				handTo(i, onGround(shoulder.add(f.scale(0.34)).add(out.scale(0.1))), t, 0.25);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 3, 0.06);
				continue;
			}
			if (t < DRAG) {
				// Clawing along: on its pull, the arm reaches out far ahead and the hand digs in;
				// the body is pulled up to it. The other hand stays where it dug in.
				armPole[i] = out.add(UP.scale(0.8)).subtract(f.scale(0.3));
				int k = which(PULLS, t, 8);
				if (k >= 0 && k % 2 == i && t == PULLS[k]) plantHand(i, onGround(shoulder.add(f.scale(0.44)).add(out.scale(0.06))), t);
				if (handTo[i] == null) plantHand(i, onGround(shoulder.add(f.scale(0.2)).add(out.scale(0.08))), t);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 4, 0.12);
				continue;
			}
			if (t < STRIKE) {
				// Raking the dirt as they go: each hand digs in and is dragged out of it when the
				// arm is pulled straight, and reaches out ahead again.
				armPole[i] = out.add(UP.scale(0.8)).subtract(f.scale(0.3));
				boolean reach = t == DRAG + 3 + 5 * i || t == DRAG + 11 + 3 * i;
				if (handTo[i] == null || reach) plantHand(i, onGround(shoulder.add(f.scale(0.46)).add(out.scale(0.08))), t);
				hands[i] = carried(handFrom[i], handTo[i], t - handAt[i], 3, 0.1);
				// Pulled straight: the hand comes with the body, furrowing the ground.
				double reachOf = 10 * PX * 0.97;
				if (hands[i].distanceTo(shoulder) > reachOf) {
					Vec3 d = hands[i].subtract(shoulder).normalize();
					hands[i] = onGround(shoulder.add(d.scale(reachOf)));
					handTo[i] = hands[i];
					handFrom[i] = hands[i];
				}
				continue;
			}
			// Flung out as its legs come down into them.
			Vec3 want = onGround(shoulder.add(f.scale(0.3)).add(out.scale(0.35)));
			if (t < STRIKE + 2) want = want.add(0, 0.12, 0);
			hands[i] = hands[i].lerp(want, 0.5);
			handTo[i] = null;
		}
	}

	private Vec3 chestPoint() {
		return point(chestFrame(), 0, 3, 0);
	}

	// ---------------------------------------------------------------- the body, built

	/** A place and three axes: x to their left, y up their spine (or along a limb), z out of their front. */
	private record Frame(Vec3 o, Vec3 x, Vec3 y, Vec3 z) {
	}

	private static Vec3 rotate(Vec3 v, Vec3 axis, double a) {
		double c = Math.cos(a), s = Math.sin(a);
		return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
	}

	private static Vec3 point(Frame fr, double x, double y, double z) {
		return fr.o.add(fr.x.scale(x * PX)).add(fr.y.scale(y * PX)).add(fr.z.scale(z * PX));
	}

	/**
	 * Their hips: lying along the ground, their head along {@code f}, lifted by {@code lift},
	 * rolled by {@code roll} (a half turn and they are on their front).
	 */
	private Frame pelvisFrame() {
		Vec3 y = f.scale(Math.cos(lift)).add(UP.scale(Math.sin(lift)));
		Vec3 z = UP.scale(Math.cos(lift)).subtract(f.scale(Math.sin(lift)));
		Vec3 x = y.cross(z);
		x = rotate(x, y, roll);
		z = rotate(z, y, roll);
		return new Frame(pelvis, x, y, z);
	}

	/** Their chest, bent forward from the small of their back by {@code curl}. */
	private Frame chestFrame() {
		Frame p = pelvisFrame();
		Vec3 o = point(p, 0, 6, 0);
		Vec3 y = rotate(p.y, p.x, curl);
		Vec3 z = rotate(p.z, p.x, curl);
		return new Frame(o, p.x, y, z);
	}

	/**
	 * Their head, on their neck, turned to look at {@code look} (as far as a neck goes): tipped
	 * forward or back first, chin down to see along their body or head back to see ahead of it,
	 * then turned to one side.
	 */
	private Frame headFrame() {
		Frame c = chestFrame();
		Vec3 o = point(c, 0, 6, 0);
		double ly = 0, lp = 0;
		if (look != null) {
			Vec3 d = look.subtract(o);
			double dx = d.dot(c.x), dy = d.dot(c.y), dz = d.dot(c.z);
			lp = Math.max(-1.0, Math.min(1.2, Math.atan2(-dy, dz)));
			ly = Math.max(-1.3, Math.min(1.3, Math.atan2(dx, Math.hypot(dy, dz))));
		}
		lp += nod;
		Vec3 z1 = rotate(c.z, c.x, lp), y1 = rotate(c.y, c.x, lp);
		Vec3 z2 = rotate(z1, y1, ly), x2 = rotate(c.x, y1, ly);
		return new Frame(o, x2, y1, z2);
	}

	/**
	 * A limb from {@code root} to {@code end}, of two bones {@code a} and {@code b} long (in body
	 * pixels), the joint between them bent towards {@code pole}: the frames of its two bones, each
	 * with y pointing back up the bone.
	 */
	private static Frame[] limb(Vec3 root, Vec3 end, double a, double b, Vec3 pole) {
		double l1 = a * PX, l2 = b * PX;
		Vec3 line = end.subtract(root);
		double d = Math.max(Math.abs(l1 - l2) + 1e-3, Math.min(l1 + l2 - 1e-4, line.length()));
		Vec3 u = line.lengthSqr() < 1e-9 ? new Vec3(0, -1, 0) : line.normalize();
		double along = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
		double off = Math.sqrt(Math.max(0, l1 * l1 - along * along));
		Vec3 n = pole.subtract(u.scale(pole.dot(u)));
		if (n.lengthSqr() < 1e-9) n = u.cross(new Vec3(0.3, 0.1, 1)).normalize();
		n = n.normalize();
		Vec3 joint = root.add(u.scale(along)).add(n.scale(off));
		Vec3 tip = root.add(u.scale(d));
		return new Frame[]{bone(root, joint, n), bone(joint, tip, n)};
	}

	private static Frame bone(Vec3 from, Vec3 to, Vec3 front) {
		Vec3 y = from.subtract(to).normalize();
		Vec3 z = front.subtract(y.scale(front.dot(y)));
		z = z.lengthSqr() < 1e-9 ? y.cross(new Vec3(1, 0, 0)).normalize() : z.normalize();
		return new Frame(from, y.cross(z), y, z);
	}

	private static Part box(String name, String block, Frame fr, double x0, double x1, double y0, double y1, double z0, double z1, Vec3 anchor) {
		Vec3 ex = fr.x.scale((x1 - x0) * PX), ey = fr.y.scale((y1 - y0) * PX), ez = fr.z.scale((z1 - z0) * PX);
		Vec3 o = point(fr, x0, y0, z0).subtract(anchor);
		return new Part(name, block, new double[]{ex.x, ey.x, ez.x, o.x, ex.y, ey.y, ez.y, o.y, ex.z, ey.z, ez.z, o.z, 0, 0, 0, 1});
	}

	private List<Part> build(Vec3 anchor) {
		Frame p = pelvisFrame(), c = chestFrame(), h = headFrame();
		chest = point(c, 0, 3, 0);
		List<Part> out = new ArrayList<>();
		out.add(box("pelvis", SHIRT, p, -4, 4, -0.5, 6, -2, 2, anchor));
		out.add(box("chest", SHIRT, c, -4, 4, 0, 6, -2, 2, anchor));
		out.add(box("head", SKIN, h, -4, 4, 0, 8, -4, 4, anchor));
		out.add(box("hair", HAIR, h, -4.2, 4.2, 6, 8.25, -4.2, 4.2, anchor));
		out.add(box("hair_back", HAIR, h, -4.2, 4.2, 2.5, 8.25, -4.25, -3.2, anchor));
		out.add(box("eye_l", EYES, h, 1.2, 2.7, 3.2, 4.2, 3.95, 4.15, anchor));
		out.add(box("eye_r", EYES, h, -2.7, -1.2, 3.2, 4.2, 3.95, 4.15, anchor));
		// Arms: from the shoulder, four pixels to the elbow, six on to the end of the hand; the
		// elbows out and to the back, unless they are being held some other way.
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 shoulder = point(c, 6 * side, 4, 0);
			Vec3 hand = hands[i] == null ? point(c, 6 * side, -6, 0) : hands[i];
			Vec3 pole = armPole[i] != null ? armPole[i] : c.x.scale(side * 0.7).subtract(c.z.scale(0.7)).add(f.scale(0.3));
			Frame[] arm = limb(shoulder, hand, 4, 6, pole);
			out.add(box("arm_" + (i == 0 ? "l" : "r"), SHIRT, arm[0], -2, 2, -4.3, 2, -2, 2, anchor));
			out.add(box("forearm_" + (i == 0 ? "l" : "r"), SKIN, arm[1], -2, 2, -6, 0.3, -2, 2, anchor));
		}
		// Legs: hip to knee six pixels, knee to sole six; the knees bend out of the front of them.
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 hip = point(p, 1.9 * side, 0, 0);
			Vec3 foot = feet[i] == null ? point(p, 1.9 * side, -12, 0) : feet[i];
			Vec3 pole = legPole[i] != null ? legPole[i] : p.z.add(p.x.scale(side * 0.25)).add(UP.scale(0.3));
			Frame[] leg = limb(hip, foot, 6, 6, pole);
			String s = i == 0 ? "l" : "r";
			out.add(box("thigh_" + s, PANTS, leg[0], -2, 2, -6, 0.3, -2, 2, anchor));
			out.add(box("shin_" + s, PANTS, leg[1], -2, 2, -4.4, 0.3, -2, 2, anchor));
			out.add(box("shoe_" + s, SHOES, leg[1], -2.05, 2.05, -6, -4.4, -2.05, 2.05, anchor));
		}
		// Nothing below the ground: if any corner is, the whole body comes up by as much.
		double low = Double.MAX_VALUE;
		for (Part q : out) for (int n = 0; n < 8; n++) {
			double y = q.m[7] + anchor.y + ((n & 1) * q.m[4]) + (((n >> 1) & 1) * q.m[5]) + (((n >> 2) & 1) * q.m[6]);
			low = Math.min(low, y);
		}
		if (low < ground) {
			double up = ground - low;
			List<Part> lifted = new ArrayList<>();
			for (Part q : out) {
				double[] m = q.m.clone();
				m[7] += up;
				lifted.add(new Part(q.name, q.block, m));
			}
			chest = chest.add(0, up, 0);
			return lifted;
		}
		return out;
	}
}
