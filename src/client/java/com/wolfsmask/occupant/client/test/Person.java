package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whoever was holding the camera, once it has knocked them down: a person built of blocks the
 * size and colours of a player's body, but jointed as a body is, at the knees and elbows and the
 * small of the back, so they can do what someone does to get away from something.
 * <p>
 * They try everything, one thing after another, each running into the next, and each fails.
 * Thrown back, they land flat and the breath goes out of them. They come up onto one arm and shove
 * themselves backwards with their heels. When it is at their feet they kick at it. They turn over
 * onto their front, the shoulders first and the hips after, over their side; push up onto hands and
 * knees; bring a foot under them and start to stand, and the foot goes out from under them and
 * they go down on their face. They claw their way along on their stomach, one arm over the other.
 * It takes them by the ankle and drags them back, their hands raking the dirt; and it comes down
 * on them.
 * <p>
 * The body is worked out a tick at a time, in order. Nothing holds it up but what it rests on:
 * how high it is comes from the ground under it and the limbs planted on it, and with nothing
 * under it, it falls, as a weight falls. It never goes into the ground and never floats over it.
 * Every part of it gets where it is going smoothly, from where it was, with weight; a planted hand
 * or foot stays exactly where it was put until it is picked up and put down somewhere else.
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

	// What they try, in ticks after the blow; each thing until the next.
	/** Knocked over backwards; flat and winded from LANDED. */
	static final int LANDED = 9;
	/** Up on one arm, shoving themselves back with their heels at each of SHOVES. */
	static final int SCRAMBLE = 16;
	static final int[] SHOVES = {20, 29, 38};
	/** It is at their feet: leaning back on their hands, kicking at it, at each of KICKS. */
	static final int KICK = 48;
	static final int[] KICKS = {50, 55, 60};
	/** Over onto their front, over their left side, shoulders first. */
	static final int ROLL = 66, ROLL_TICKS = 20;
	/** Hands under the shoulders, chest up, hips up and back onto the knees. */
	static final int PUSHUP = 86;
	/** A foot brought up under them, up off the hands, starting to stand. */
	static final int RISE = 100;
	/** The foot goes; down on their face. */
	static final int SLIP = 114;
	/** On their stomach, clawing along, one arm and then the other, at each of PULLS. */
	static final int CRAWL = 124;
	static final int[] PULLS = {126, 135, 144};
	/** Taken by the right ankle and dragged back. */
	static final int DRAG = 154;
	/** It comes down on them. */
	static final int STRIKE = 170;

	private static final Vec3 UP = new Vec3(0, 1, 0);
	/** How fast a falling thing gains speed, in blocks a tick, each tick. */
	private static final double G = 0.03;

	/** The way along the ground their head points, lying, and their left as they lie on their back. */
	private final Vec3 f, left;
	private final double ground;
	private final double sx, sz;

	// The body: where the hips are, and how the trunk is held, each with how fast it is changing.
	private Vec3 pelvis;
	private double vx, vy, vz;
	private double lift, liftV, curl, curlV, roll, rollV, twist, twistV, nod, nodV;
	/** How far over to their left turning over has carried them, rolling on their side. */
	private double aside;
	/** Where the head is turned: tipped (positive, chin down) and turned (positive, to their left). */
	private double headP, headPV, headY, headYV;
	private Vec3 look;
	private final Limb[] feet = {new Limb(false, 1), new Limb(false, -1)};
	private final Limb[] hands = {new Limb(true, 1), new Limb(true, -1)};
	private int tau = -1;
	/** Where the front foot was when it went. */
	private Vec3 skid;
	private Vec3 chest = Vec3.ZERO;

	/** A hand or a foot, and the arm or leg it is on. */
	private static final class Limb {
		final boolean arm;
		/** 1 for their left, -1 for their right. */
		final double side;
		/** Where its end is now. */
		Vec3 at;
		Vec3 vel = Vec3.ZERO;
		/** Where it is planted (or going to be), or null while it is held free. */
		Vec3 plant;
		/** A move to the plant under way: from where and how fast it was going, since when, how long, how high over. */
		Vec3 from, fromVel = Vec3.ZERO;
		/** How far out of the straight way it swings on the way, at the middle: round the shoulder or hip, not through it. */
		Vec3 bow = Vec3.ZERO;
		int since, over;
		double arc;
		/** Taking the body's weight where it is planted. */
		boolean weight;
		/** Which way the elbow or knee points, eased round, never flipped; and the way it is bent now. */
		Vec3 pole, poleWant, bent;

		Limb(boolean arm, double side) {
			this.arm = arm;
			this.side = side;
		}

		double length() {
			return (arm ? 10 : 12) * PX;
		}

		boolean carrying(int t) {
			return plant != null && from != null && t - since < over;
		}
	}

	/**
	 * @param stood  where they were standing when it hit them (their feet), facing it
	 * @param toward the way they were knocked: along the ground, away from it
	 */
	Person(Vec3 stood, Vec3 toward) {
		this.f = new Vec3(toward.x, 0, toward.z).normalize();
		this.left = f.cross(UP).normalize();
		this.ground = stood.y;
		this.sx = stood.x;
		this.sz = stood.z;
		this.pelvis = stood.add(f.scale(0.1)).add(0, 0.9, 0);
		this.lift = Math.PI / 2 - 0.12;
	}

	/** Where their chest is, as last posed: what its legs go into. */
	Vec3 chest() {
		return chest;
	}

	/** Their right ankle, as last posed: what it takes them by. */
	Vec3 ankle() {
		return feet[1].at == null ? pelvis : feet[1].at;
	}

	/**
	 * The body this tick ({@code t} ticks after the blow, called once a tick in order), with its
	 * head turned to {@code it} (where its face is) when they are looking at it.
	 */
	List<Part> pose(int t, Vec3 it, Vec3 anchor) {
		while (tau < t) step(++tau, it);
		List<Part> out = parts(anchor);
		chest = point(chestFrame(), 0, 3, 0);
		return out;
	}

	// ---------------------------------------------------------------- easing

	private static double clamp01(double x) {
		return Math.max(0.0, Math.min(1.0, x));
	}

	/** From 0 to 1 and still at both ends, the way a hand moves from one place to another. */
	private static double ease(double x) {
		double c = clamp01(x);
		return c * c * c * (10 - 15 * c + 6 * c * c);
	}

	private static double bump(double x) {
		return x <= 0 || x >= 1 ? 0.0 : Math.sin(Math.PI * x);
	}

	/** Which of {@code at} they are in at t (-1 none), each lasting {@code len} ticks. */
	private static int which(int[] at, int t, int len) {
		for (int k = at.length - 1; k >= 0; k--) if (t >= at[k] && t < at[k] + len) return k;
		return -1;
	}

	/** A critically damped spring a tick on: towards {@code want}, {@code w} its quickness. */
	private static double[] spring(double x, double v, double want, double w) {
		v += w * w * (want - x) - 2 * w * v;
		return new double[]{x + v, v};
	}

	// ---------------------------------------------------------------- what they mean to do

	/**
	 * How far along the ground (along {@code f}) their hips mean to be at t: knocked back, shoved
	 * further at each shove, pushed back by their own kicks, back onto their knees, on a little as
	 * they start to stand, thrown forward as they fall, pulled on by each pull, and dragged back.
	 */
	private double along(double t) {
		double d = 0.1 + 1.2 * ease(t / 8.0);
		for (int s : SHOVES) d += 0.16 * ease((t - s - 2) / 6.0);
		for (int k : KICKS) d += 0.03 * ease((t - k - 1) / 3.0);
		d -= 0.12 * ease((t - PUSHUP - 6) / 8.0);
		d += 0.1 * ease((t - RISE - 4) / 8.0);
		d += 0.34 * ease((t - SLIP - 1) / 6.0);
		for (int p : PULLS) d += 0.17 * ease((t - p - 4) / 5.0);
		d -= 0.45 * ease((t - DRAG - 1) / 5.0) + 0.4 * ease((t - DRAG - 9) / 6.0);
		return d;
	}

	/** Their hips over onto their front, shoulders a little ahead: how far round each is at t. */
	private static double rollAt(double t) {
		return Math.PI * ease((t - ROLL) / ROLL_TICKS);
	}

	private Vec3 origin() {
		return new Vec3(sx, ground, sz);
	}

	private void step(int t, Vec3 it) {
		double wantLift, wantCurl, wantRoll, wantNod = 0, hip = Double.NaN, quick = 0.3;
		look = it;
		if (t < LANDED) {
			// Going over backwards, faster and faster, the way anything tips over: nothing to do but fall.
			double over = Math.min(Math.PI / 2, 0.12 * Math.cosh(t * 0.33));
			wantLift = Math.PI / 2 - over;
			lift = wantLift;
			liftV = 0;
			wantCurl = 0.1;
			wantRoll = 0;
			look = null;
		} else if (t < SCRAMBLE) {
			// Flat on their back, winded: the head knocked back on the ground, then up to find it.
			wantLift = 0.0;
			wantCurl = 0.05 + 0.08 * bump((t - LANDED) / 5.0);
			wantRoll = 0.0;
			wantNod = t < LANDED + 4 ? -0.3 : 0.0;
		} else if (t < KICK) {
			// Up on their right arm; each shove throws them back a little, and they come forward over it.
			int k = which(SHOVES, t, 10);
			double u = k >= 0 ? (t - SHOVES[k]) / 10.0 : 0;
			wantLift = 0.55 * ease((t - SCRAMBLE) / 6.0) - 0.1 * bump(u);
			wantCurl = 0.2;
			wantRoll = -0.2 * ease((t - SCRAMBLE) / 6.0);
		} else if (t < ROLL) {
			// Back on both hands, kicking; each kick jolts them back.
			int k = which(KICKS, t, 5);
			double u = k >= 0 ? (t - KICKS[k]) / 5.0 : 0;
			wantLift = 0.45 - 0.08 * bump(u);
			wantCurl = 0.3;
			wantRoll = k >= 0 ? (k % 2 == 0 ? 0.08 : -0.08) * bump(u) : 0.0;
		} else if (t < PUSHUP) {
			// Turning over, away from it: down off the hands, over the left side onto the front.
			wantLift = 0.04;
			wantCurl = 0.2 * (1 - ease((t - ROLL) / ROLL_TICKS));
			wantRoll = rollAt(t);
			look = pelvis.add(left.scale(1.5)).add(f.scale(1.2));
		} else if (t < RISE) {
			// Hands under the shoulders: the chest pushed up off the ground, then the hips up and back
			// onto the knees.
			double u = t - PUSHUP;
			wantLift = 0.5 * ease(u / 6.0) - 0.36 * ease((u - 6) / 8.0);
			wantCurl = -0.15 * ease(u / 6.0) + 0.2 * ease((u - 6) / 8.0);
			wantRoll = Math.PI;
			if (u >= 5) hip = 0.4 * ease((u - 5) / 8.0);
			look = pelvis.add(f.scale(2.2));
		} else if (t < SLIP) {
			// A foot up under them, then up off the hands, the body coming up over the front leg.
			double u = t - RISE;
			wantLift = 0.14 + 0.95 * ease((u - 4) / 9.0);
			wantCurl = 0.05 + 0.3 * bump((u - 4) / 10.0);
			wantRoll = Math.PI;
			hip = 0.4 + 0.2 * ease((u - 7) / 7.0);
			look = pelvis.add(f.scale(4)).add(0, 1.0, 0);
		} else if (t < CRAWL) {
			// The foot goes: nothing under them on that side; they pitch forward onto their hands
			// and down on their front.
			wantLift = 0.0;
			wantCurl = 0.0;
			wantRoll = Math.PI;
			wantNod = 0.25 * bump((t - SLIP) / 8.0);
			quick = 0.45;
			look = pelvis.add(f.scale(3)).subtract(0, 0.4, 0);
		} else if (t < DRAG) {
			// On their stomach, the chest up on the forearms, clawing along; a look back at it.
			int k = which(PULLS, t, 9);
			double u = k >= 0 ? (t - PULLS[k]) / 9.0 : 0;
			wantLift = 0.14 + 0.05 * bump(u);
			wantCurl = -0.1;
			wantRoll = Math.PI + (k >= 0 ? (k % 2 == 0 ? -0.1 : 0.1) * bump(u) : 0);
			boolean back = t >= PULLS[1] - 1 && t < PULLS[1] + 7;
			look = back ? it : pelvis.add(f.scale(4)).add(0, 0.6, 0);
		} else if (t < STRIKE) {
			// Dragged back by the ankle, flat; the pull twists them a little onto that side.
			wantLift = 0.06;
			wantCurl = -0.05;
			wantRoll = Math.PI + 0.12 * ease((t - DRAG) / 4.0);
			look = t < DRAG + 6 ? it : pelvis.add(f.scale(4)).add(0, 0.4, 0);
		} else {
			// Its legs come down into their back: flattened, the head forced down.
			wantLift = 0.0;
			wantCurl = 0.15 * bump((t - STRIKE) / 6.0);
			wantRoll = Math.PI;
			wantNod = 0.35;
			quick = 0.5;
			look = null;
		}

		// The trunk: each part of it gets where it is going on a spring, with weight, no faster.
		double[] s;
		if (t >= SLIP && t < CRAWL && lift > 0.0) {
			// Nothing holding them up: they tip forward under their own weight, faster and faster,
			// until the hands take it and the chest comes down.
			liftV -= 0.035 * Math.cos(lift) + (t >= SLIP + 4 ? 0.2 * liftV : 0);
			lift = Math.max(0.0, lift + liftV);
			if (lift <= 0.0) liftV = 0;
		} else if (t >= LANDED) {
			s = spring(lift, liftV, wantLift, quick);
			lift = s[0];
			liftV = s[1];
		}
		s = spring(curl, curlV, wantCurl, 0.3);
		curl = s[0];
		curlV = s[1];
		if (t >= ROLL && t < PUSHUP) {
			// Turning over: the hips round on their way.
			double was = roll;
			roll = wantRoll;
			rollV = roll - was;
		} else {
			s = spring(roll, rollV, wantRoll, 0.3);
			roll = s[0];
			rollV = s[1];
		}
		// The shoulders go round a few ticks ahead of the hips.
		s = spring(twist, twistV, rollAt(t + 5) - rollAt(t), 0.45);
		twist = s[0];
		twistV = s[1];
		s = spring(nod, nodV, wantNod, 0.4);
		nod = s[0];
		nodV = s[1];
		// Turning over a body on the ground carries it sideways: it rolls over its edge, and the
		// middle of it goes along by how high the middle is over where it touches.
		if (t >= ROLL && t <= PUSHUP) {
			double a = 4 * PX, b = 2 * PX;
			double r = roll + twist / 2;
			aside += (a * Math.abs(Math.sin(r)) + b * Math.abs(Math.cos(r))) * Math.max(0, rollV + twistV / 2);
		}
		// Along the ground: where they mean to be, with weight.
		Vec3 flat = origin().add(f.scale(along(t))).add(left.scale(aside));
		double w = t >= DRAG && t < STRIKE ? 0.55 : 0.35;
		s = spring(pelvis.x, vx, flat.x, w);
		double px = s[0];
		vx = s[1];
		s = spring(pelvis.z, vz, flat.z, w);
		vz = s[1];
		pelvis = new Vec3(px, pelvis.y, s[0]);

		limbs(t, it);
		head();
		settle(hip);
	}

	/**
	 * How high the body is: held up only by what it rests on. It rests on its lowest part on the
	 * ground, unless the legs and arms planted under it are holding it up higher, and they can hold
	 * it up only as far as they reach. Above what holds it, it falls.
	 */
	private void settle(double hip) {
		double low = lowest();
		double yMin = pelvis.y + (ground - low);
		double yMax = Double.MAX_VALUE;
		for (Limb l : all()) {
			if (!l.weight || l.plant == null) continue;
			Vec3 root = root(l);
			double hz = Math.hypot(root.x - l.plant.x, root.z - l.plant.z), reach = l.length() * 0.985;
			if (hz >= reach) continue;
			yMax = Math.min(yMax, pelvis.y + (l.plant.y + Math.sqrt(reach * reach - hz * hz) - root.y));
		}
		double want = Double.isNaN(hip) ? yMin : Math.max(yMin, Math.min(yMax, ground + hip));
		double y = pelvis.y;
		if (want < y - 1.0e-3) {
			// Nothing holding it up here: it falls, faster and faster, until it is stopped.
			vy -= G;
			y += vy;
			if (y <= want) {
				y = want;
				vy = 0;
			}
		} else {
			double[] s = spring(y, vy, want, 0.35);
			y = s[0];
			vy = s[1];
		}
		if (y < yMin) {
			y = yMin;
			vy = Math.max(0, vy);
		}
		pelvis = new Vec3(pelvis.x, y, pelvis.z);
		// A bent limb does not rise as far as the hips do; so again, until nothing is in the ground.
		for (int k = 0; k < 3; k++) {
			double under = ground - lowest();
			if (under < 0.002) break;
			pelvis = pelvis.add(0, under, 0);
			vy = Math.max(0, vy);
		}
	}

	/** The lowest point of the body: what it rests on. */
	private double lowest() {
		double low = Double.MAX_VALUE;
		for (Part q : parts(Vec3.ZERO)) {
			for (int k = 0; k < 8; k++) {
				double y = q.m[7] + ((k & 1) * q.m[4]) + (((k >> 1) & 1) * q.m[5]) + (((k >> 2) & 1) * q.m[6]);
				low = Math.min(low, y);
			}
		}
		return low;
	}

	private Limb[] all() {
		return new Limb[]{feet[0], feet[1], hands[0], hands[1]};
	}

	/** Where a limb starts: the hip or the shoulder. */
	private Vec3 root(Limb l) {
		return l.arm ? point(chestFrame(), 6 * l.side, 4, 0) : point(pelvisFrame(), 1.9 * l.side, 0, 0);
	}

	/**
	 * The head turned to look, as near as a neck lets it (tipped back a little, forward more,
	 * turned either way not quite to the shoulder), the nearest way from how it is held; on
	 * springs, never snapped round.
	 */
	private void head() {
		Frame c = chestFrame();
		double wantP = nod, wantY = 0;
		if (look != null) {
			Vec3 d = look.subtract(point(c, 0, 6, 0)).normalize();
			double best = -9;
			for (int i = 0; i <= 22; i++) {
				double hp = -1.0 + 2.2 * i / 22;
				Vec3 z1 = rotate(c.z, c.x, hp), y1 = rotate(c.y, c.x, hp);
				for (int j = 0; j <= 24; j++) {
					double hy = -1.2 + 2.4 * j / 24;
					double score = rotate(z1, y1, hy).dot(d) - 0.03 * (Math.abs(hp - headP) + Math.abs(hy - headY));
					if (score > best) {
						best = score;
						wantP = hp;
						wantY = hy;
					}
				}
			}
			wantP += nod;
		}
		// A head is heavy: it can only be got going so fast.
		double aP = Math.max(-0.05, Math.min(0.05, 0.3 * 0.3 * (wantP - headP) - 2 * 0.3 * headPV));
		headPV += aP;
		headP += headPV;
		double aY = Math.max(-0.05, Math.min(0.05, 0.3 * 0.3 * (wantY - headY) - 2 * 0.3 * headYV));
		headYV += aY;
		headY += headYV;
	}

	// ---------------------------------------------------------------- hands and feet

	/** A point on the ground at p, as high as half a limb is thick, so it rests on the ground. */
	private Vec3 onGround(Vec3 p) {
		return new Vec3(p.x, ground + 2.08 * PX, p.z);
	}

	/** Planted at p (moving there first if it is not there, up over the ground and down). */
	private void plant(Limb l, Vec3 p, int t, double arc, boolean weight, double slack) {
		if (l.at == null) l.at = p;
		if (l.plant == null || l.plant.distanceTo(p) > slack) {
			double d = l.at.distanceTo(p);
			l.from = l.at;
			l.fromVel = l.vel;
			l.plant = p;
			l.since = t;
			l.over = (int) Math.max(4, Math.min(10, Math.round(4 + 12 * d)));
			l.arc = arc * Math.min(1.0, d / 0.2);
			// The straight way would take it in through its own shoulder or hip: round, instead.
			Vec3 r = root(l), mid = l.from.lerp(p, 0.5), way = p.subtract(l.from);
			Vec3 off = mid.subtract(r);
			if (way.lengthSqr() > 1e-6) off = off.subtract(way.scale(off.dot(way) / way.lengthSqr()));
			double clear = l.length() * 0.45;
			l.bow = off.length() < clear && off.lengthSqr() > 1e-8 ? off.normalize().scale(clear - off.length())
					: off.lengthSqr() <= 1e-8 ? UP.scale(clear) : Vec3.ZERO;
		}
		Vec3 was = l.at;
		if (l.carrying(t)) {
			// On its way, going on as fast as it was going at first, and slowing to rest there.
			double g = clamp01((t - l.since + 1.0) / l.over);
			double g3 = g * g * g, g4 = g3 * g, g5 = g4 * g;
			double h01 = 10 * g3 - 15 * g4 + 6 * g5, h00 = 1 - h01, h10 = g - 6 * g3 + 8 * g4 - 3 * g5;
			l.at = l.from.scale(h00).add(l.fromVel.scale(h10 * l.over)).add(l.plant.scale(h01)).add(0, l.arc * Math.sin(Math.PI * g), 0)
					.add(l.bow.scale(Math.sin(Math.PI * g)));
			if (l.at.y < ground + 2.08 * PX) l.at = new Vec3(l.at.x, ground + 2.08 * PX, l.at.z);
			l.weight = false;
		} else {
			l.at = l.plant;
			l.weight = weight;
		}
		l.vel = l.at.subtract(was);
	}

	/** Slid along the ground to p, never lifted: a foot going out from under them, a hand dragged. */
	private void slide(Limb l, Vec3 p) {
		l.vel = l.at == null ? Vec3.ZERO : p.subtract(l.at);
		l.at = p;
		l.plant = p;
		l.from = null;
		l.weight = false;
	}

	/** Held free, following p on a spring (quickness w): a hand up, a foot kicking. */
	private void hold(Limb l, Vec3 p, double w) {
		if (l.at == null) l.at = p;
		l.plant = null;
		l.weight = false;
		Vec3 a = p.subtract(l.at).scale(w * w).subtract(l.vel.scale(2 * w));
		l.vel = l.vel.add(a);
		l.at = l.at.add(l.vel);
		if (l.at.y < ground + 2 * PX) {
			l.at = new Vec3(l.at.x, ground + 2 * PX, l.at.z);
			l.vel = new Vec3(l.vel.x * 0.5, Math.max(0, l.vel.y), l.vel.z * 0.5);
		}
	}

	private void bend(Limb l, Vec3 pole) {
		l.poleWant = pole.normalize();
	}

	private void limbs(int t, Vec3 it) {
		Frame p = pelvisFrame(), c = chestFrame();
		Vec3 face = it == null ? pelvis.subtract(f.scale(2)).add(0, 3, 0) : it;
		for (Limb l : all()) l.poleWant = null;
		for (int i = 0; i < 2; i++) {
			Limb foot = feet[i], hand = hands[i];
			double side = i == 0 ? 1 : -1;
			Vec3 hip = point(p, 1.9 * side, 0, 0), shoulder = point(c, 6 * side, 4, 0);
			Vec3 legOut = p.x.scale(side), armOut = c.x.scale(side);
			if (t < LANDED) {
				// Falling: the feet coming up off the ground, the arms thrown up and out.
				hold(foot, hip.subtract(f.scale(0.35)).add(0, 0.1 + 0.25 * ease(t / 6.0), 0).add(legOut.scale(0.06)), 0.5);
				hold(hand, shoulder.add(armOut.scale(0.3)).add(0, 0.25, 0), 0.45);
				continue;
			}
			if (t < SCRAMBLE) {
				// Down: legs out along the ground, arms flung out; the left hand coming up off it.
				plant(foot, onGround(hip.subtract(f.scale(0.66)).add(legOut.scale(0.07))), t, 0.05, true, 0.15);
				if (i == 0 && t >= LANDED + 4) hold(hand, shoulder.add(armOut.scale(0.2)).add(0, 0.3, 0).subtract(f.scale(0.15)), 0.3);
				else plant(hand, onGround(shoulder.add(armOut.scale(0.38)).subtract(f.scale(0.08))), t, 0.06, false, 0.15);
				continue;
			}
			if (t < KICK) {
				// Heels dug in in front of them, knees up; each shove of that leg straightens it, and
				// the foot is brought in again nearer.
				int k = which(SHOVES, t, 10);
				double u = k >= 0 ? (t - SHOVES[k]) / 10.0 : 0;
				Vec3 spot = onGround(hip.subtract(f.scale(0.42)).add(legOut.scale(0.06)));
				boolean pushing = k >= 0 && u < 0.6;
				plant(foot, pushing ? foot.plant : spot, t, 0.08, true, pushing ? 9 : 0.12);
				if (i == 0) {
					// Up at it, the arm not quite straight, shaking.
					Vec3 to = face.subtract(shoulder).normalize();
					Vec3 across = to.cross(UP).normalize();
					double shake = Math.sin(t * 1.3) * 0.5 + Math.sin(t * 2.1 + 1) * 0.3;
					hold(hand, shoulder.add(to.scale(10 * PX * 0.85)).add(UP.scale(0.04)).add(across.scale(0.02 * shake)), 0.35);
					bend(hand, armOut.add(UP.scale(-0.6)));
				} else {
					// Propped on it behind them; put down again when the body has gone back past it.
					plant(hand, onGround(pelvis.add(f.scale(0.28)).add(armOut.scale(0.26))), t, 0.06, true, 0.15);
				}
				continue;
			}
			if (t < ROLL) {
				// Both hands down behind, leaning back on them; the leg driven out at it and drawn
				// back, one and then the other; the other drawn up ready.
				plant(hand, onGround(shoulder.add(armOut.scale(0.15)).add(f.scale(0.14))), t, 0.08, true, 0.18);
				int k = which(KICKS, t, 5);
				Vec3 drawn = hip.subtract(f.scale(0.24)).add(0, 0.32, 0).add(legOut.scale(0.06));
				Vec3 want = drawn;
				double w = 0.4;
				if (k >= 0 && k % 2 == i) {
					double u = (t - KICKS[k]) / 5.0;
					Vec3 out = hip.subtract(f.scale(0.62)).add(0, 0.26, 0).add(legOut.scale(0.03));
					want = u < 0.45 ? out : drawn;
					w = u < 0.45 ? 0.62 : 0.45;
				} else if (t >= KICKS[KICKS.length - 1] + 5) {
					want = onGround(hip.subtract(f.scale(0.5)).add(legOut.scale(0.08)));
				}
				hold(foot, want, w);
				continue;
			}
			if (t < PUSHUP) {
				// Turning over: the right arm reaches across them to the ground on their left and
				// pulls; the left one goes up past their head, out of the way; the right knee comes up
				// and over, and the legs follow the hips round.
				double u = (t - ROLL) / (double) ROLL_TICKS;
				if (i == 1) {
					if (u < 0.75) {
						plant(hand, onGround(pelvis.add(f.scale(0.55)).add(left.scale(0.5))), t, 0.18, true, 0.25);
					} else {
						plant(hand, onGround(shoulder.add(f.scale(0.06)).add(armOut.scale(0.04))), t, 0.1, true, 0.15);
					}
					if (u > 0.15 && u < 0.7) {
						hold(foot, hip.subtract(f.scale(0.4)).add(left.scale(0.22)).add(0, 0.16, 0), 0.35);
						bend(foot, UP.add(f.scale(0.4)));
					} else {
						plant(foot, onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.07))), t, 0.08, false, 0.15);
					}
				} else {
					if (u < 0.75) plant(hand, onGround(shoulder.add(f.scale(0.4)).add(left.scale(0.12))), t, 0.15, false, 0.2);
					else plant(hand, onGround(shoulder.add(f.scale(0.06)).add(armOut.scale(0.04))), t, 0.1, true, 0.15);
					plant(foot, onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.07))), t, 0.05, false, 0.15);
				}
				continue;
			}
			if (t < RISE) {
				// Hands flat under the shoulders, elbows back, pushing; the knees drawn up under the
				// hips as they come up, the shins back along the ground.
				double u = t - PUSHUP;
				plant(hand, onGround(shoulder.add(f.scale(0.04)).add(armOut.scale(0.04))), t, 0.08, true, 0.16);
				bend(hand, f.scale(-1).add(armOut.scale(0.4)));
				if (u < 5) {
					plant(foot, onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.07))), t, 0.05, false, 0.15);
				} else {
					plant(foot, onGround(hip.subtract(f.scale(0.36)).add(legOut.scale(0.05))), t, 0.06, true, 0.1);
					bend(foot, p.z.add(f.scale(0.5)));
				}
				continue;
			}
			if (t < SLIP) {
				// The right foot brought up under them and set down flat in front; then up off the
				// hands: the right one pushing on that knee, the left out for balance.
				double u = t - RISE;
				if (i == 1) {
					plant(foot, onGround(hip.add(f.scale(0.26)).add(legOut.scale(0.08))), t, 0.14, u >= 5, 0.2);
					bend(foot, f.add(UP.scale(0.7)));
				} else {
					plant(foot, onGround(hip.subtract(f.scale(0.36)).add(legOut.scale(0.05))), t, 0.06, true, 0.14);
					bend(foot, p.z.add(f.scale(0.5)));
				}
				if (u < 6) {
					plant(hand, onGround(shoulder.add(f.scale(0.04)).add(armOut.scale(0.04))), t, 0.08, true, 0.18);
					bend(hand, f.scale(-1).add(armOut.scale(0.4)));
				} else if (i == 1) {
					hold(hand, point(p, -1.9, 0, 0).add(f.scale(0.28)).add(0, 0.12, 0), 0.3);
				} else {
					hold(hand, shoulder.add(f.scale(0.25)).add(armOut.scale(0.25)).subtract(0, 0.22, 0), 0.3);
				}
				continue;
			}
			if (t < CRAWL) {
				// The front foot skids back out from under them; they come down onto their hands,
				// thrown out ahead to catch them, and the back knee; then the legs slide out behind
				// and they go down flat.
				double u = t - SLIP;
				Vec3 trail = onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.07)));
				if (i == 1) {
					if (u == 0) skid = foot.at;
					if (u < 5) slide(foot, skid.lerp(trail, ease((u + 1) / 5.0)));
					else plant(foot, trail, t, 0.03, false, 0.15);
				} else if (u < 6) {
					plant(foot, foot.plant, t, 0.03, true, 9);
				} else {
					slide(foot, foot.at.lerp(trail, 0.35));
				}
				plant(hand, onGround(shoulder.add(f.scale(0.34)).add(armOut.scale(0.1))), t, 0.06, u >= 3, 0.25);
				continue;
			}
			if (t < DRAG) {
				// Clawing along: on its pull, the arm reaches out far ahead, the hand digs in, and the
				// body is pulled up to it; the other hand stays dug in. The knee on the other side
				// draws up and pushes; otherwise the legs trail, toes in the dirt.
				int k = which(PULLS, t, 9);
				Vec3 reach = onGround(shoulder.add(f.scale(0.44)).add(armOut.scale(0.06)));
				if (k >= 0 && k % 2 == i && t == PULLS[k]) plant(hand, reach, t, 0.12, true, 0.0);
				else plant(hand, hand.plant != null ? hand.plant : reach, t, 0.12, true, 9);
				bend(hand, armOut.add(UP.scale(0.8)).subtract(f.scale(0.3)));
				Vec3 trail = onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.07)));
				if (k >= 0 && k % 2 != i) {
					double u = (t - PULLS[k]) / 9.0;
					plant(foot, u < 0.55 ? onGround(hip.subtract(f.scale(0.44)).add(legOut.scale(0.24))) : trail, t, 0.06, u >= 0.55, 0.1);
					bend(foot, legOut.add(p.z.scale(0.5)));
				} else {
					plant(foot, trail, t, 0.03, false, 0.12);
				}
				continue;
			}
			if (t < STRIKE) {
				// The right ankle in its grip, the leg pulled out straight behind, off the ground; the
				// left leg dragged along. The hands rake the dirt: dug in until the arm is pulled
				// straight, then dragged; and reached out ahead again.
				if (i == 1) hold(foot, hip.subtract(f.scale(0.68)).add(0, 0.14, 0), 0.6);
				else slide(foot, onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.08))));
				Vec3 reach = onGround(shoulder.add(f.scale(0.46)).add(armOut.scale(0.08)));
				boolean again = t == DRAG + 4 + 5 * i || t == DRAG + 11 + 2 * i;
				if (hand.plant == null || again) plant(hand, reach, t, 0.1, false, 0.0);
				else plant(hand, hand.plant, t, 0.1, false, 9);
				double longest = 10 * PX * 0.97;
				if (!hand.carrying(t) && hand.at.distanceTo(shoulder) > longest) {
					Vec3 d = hand.at.subtract(shoulder).normalize();
					slide(hand, onGround(shoulder.add(d.scale(longest))));
				}
				bend(hand, armOut.add(UP.scale(0.8)).subtract(f.scale(0.3)));
				continue;
			}
			// Its legs into their back: the hands jerk up off the ground and fall back; the feet too.
			double u = t - STRIKE;
			Vec3 flung = onGround(shoulder.add(f.scale(0.3)).add(armOut.scale(0.35)));
			if (u < 2) hold(hand, flung.add(0, 0.12, 0), 0.6);
			else plant(hand, flung, t, 0.05, false, 0.2);
			if (i == 1 && u < 2) hold(foot, hip.subtract(f.scale(0.66)).add(0, 0.16, 0), 0.6);
			else plant(foot, onGround(hip.subtract(f.scale(0.64)).add(legOut.scale(0.08))), t, 0.04, false, 0.2);
		}
		// Elbows and knees eased round to where they are meant to point; and however the limb is
		// moved, the joint turns about it only so fast, so it never flips over to the other side
		// as the limb goes straight.
		for (Limb l : all()) {
			Vec3 want = l.poleWant != null ? l.poleWant : defaultPole(l);
			l.pole = l.pole == null ? want : l.pole.lerp(want, 0.3).normalize();
			Vec3 line = l.at.subtract(root(l));
			if (line.lengthSqr() < 1e-6) continue;
			Vec3 u = line.normalize();
			Vec3 n = l.pole.subtract(u.scale(l.pole.dot(u)));
			if (n.lengthSqr() < 1e-6) continue;
			n = n.normalize();
			if (l.bent != null) {
				Vec3 prev = l.bent.subtract(u.scale(l.bent.dot(u)));
				if (prev.lengthSqr() > 1e-6) {
					prev = prev.normalize();
					double angle = Math.acos(Math.max(-1, Math.min(1, prev.dot(n))));
					if (angle > 0.25) {
						Vec3 side = n.subtract(prev.scale(prev.dot(n)));
						side = side.lengthSqr() < 1e-6 ? u.cross(prev).normalize() : side.normalize();
						n = prev.scale(Math.cos(0.25)).add(side.scale(Math.sin(0.25)));
					}
				}
			}
			l.bent = n;
		}
	}

	/** Elbows out and to the back of them; knees out of the front of them, and up. */
	private Vec3 defaultPole(Limb l) {
		if (l.arm) {
			Frame c = chestFrame();
			return c.x.scale(l.side * 0.7).subtract(c.z.scale(0.7)).add(f.scale(0.3)).normalize();
		}
		Frame p = pelvisFrame();
		return p.z.add(p.x.scale(l.side * 0.25)).add(UP.scale(0.3)).normalize();
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

	/** Their chest, bent forward from the small of their back by {@code curl}, and turned ahead of the hips by {@code twist}. */
	private Frame chestFrame() {
		Frame p = pelvisFrame();
		Vec3 o = point(p, 0, 6, 0);
		Vec3 y = rotate(p.y, p.x, curl);
		Vec3 z = rotate(p.z, p.x, curl);
		Vec3 x = rotate(p.x, y, twist);
		z = rotate(z, y, twist);
		return new Frame(o, x, y, z);
	}

	/** Their head, on their neck: tipped forward or back, then turned. */
	private Frame headFrame() {
		Frame c = chestFrame();
		Vec3 o = point(c, 0, 6, 0);
		Vec3 z1 = rotate(c.z, c.x, headP), y1 = rotate(c.y, c.x, headP);
		Vec3 z2 = rotate(z1, y1, headY), x2 = rotate(c.x, y1, headY);
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
		return level(new Frame(from, y.cross(z), y, z));
	}

	/**
	 * The same bone with its box turned about it (by an eighth of a turn at most) so that one side
	 * is face down: a limb is square through, so this changes nothing to look at, but a limb lying
	 * on the ground lies flat on it instead of on an edge sunk into it.
	 */
	private static Frame level(Frame fr) {
		Vec3 up = UP.subtract(fr.y.scale(fr.y.dot(UP)));
		if (up.lengthSqr() < 0.04) return fr;
		up = up.normalize();
		double a = Math.atan2(fr.x.dot(up), fr.z.dot(up));
		double turn = a - (Math.PI / 2) * Math.round(a / (Math.PI / 2));
		return new Frame(fr.o, rotate(fr.x, fr.y, turn), fr.y, rotate(fr.z, fr.y, turn));
	}

	private static Part box(String name, String block, Frame fr, double x0, double x1, double y0, double y1, double z0, double z1, Vec3 anchor) {
		Vec3 ex = fr.x.scale((x1 - x0) * PX), ey = fr.y.scale((y1 - y0) * PX), ez = fr.z.scale((z1 - z0) * PX);
		Vec3 o = point(fr, x0, y0, z0).subtract(anchor);
		return new Part(name, block, new double[]{ex.x, ey.x, ez.x, o.x, ex.y, ey.y, ez.y, o.y, ex.z, ey.z, ez.z, o.z, 0, 0, 0, 1});
	}

	private List<Part> parts(Vec3 anchor) {
		Frame p = pelvisFrame(), c = chestFrame(), h = headFrame();
		List<Part> out = new ArrayList<>();
		out.add(box("pelvis", SHIRT, p, -4, 4, -0.5, 6, -2, 2, anchor));
		out.add(box("chest", SHIRT, c, -4, 4, 0, 6, -2, 2, anchor));
		out.add(box("head", SKIN, h, -4, 4, 0, 8, -4, 4, anchor));
		out.add(box("hair", HAIR, h, -4.2, 4.2, 6, 8.25, -4.2, 4.2, anchor));
		out.add(box("hair_back", HAIR, h, -4.2, 4.2, 2.5, 8.25, -4.25, -3.2, anchor));
		out.add(box("eye_l", EYES, h, 1.2, 2.7, 3.2, 4.2, 3.95, 4.15, anchor));
		out.add(box("eye_r", EYES, h, -2.7, -1.2, 3.2, 4.2, 3.95, 4.15, anchor));
		// Arms: from the shoulder, four pixels to the elbow, six on to the end of the hand.
		for (int i = 0; i < 2; i++) {
			Limb l = hands[i];
			Vec3 shoulder = point(c, 6 * l.side, 4, 0);
			Vec3 hand = l.at == null ? point(c, 6 * l.side, -6, 0) : l.at;
			Frame[] arm = limb(shoulder, hand, 4, 6, l.bent != null ? l.bent : l.pole != null ? l.pole : defaultPole(l));
			out.add(box("arm_" + (i == 0 ? "l" : "r"), SHIRT, arm[0], -2, 2, -4.3, 2, -2, 2, anchor));
			out.add(box("forearm_" + (i == 0 ? "l" : "r"), SKIN, arm[1], -2, 2, -6, 0.3, -2, 2, anchor));
		}
		// Legs: hip to knee six pixels, knee to sole six.
		for (int i = 0; i < 2; i++) {
			Limb l = feet[i];
			Vec3 hip = point(p, 1.9 * l.side, 0, 0);
			Vec3 foot = l.at == null ? point(p, 1.9 * l.side, -12, 0) : l.at;
			Frame[] leg = limb(hip, foot, 6, 6, l.bent != null ? l.bent : l.pole != null ? l.pole : defaultPole(l));
			String s = i == 0 ? "l" : "r";
			out.add(box("thigh_" + s, PANTS, leg[0], -2, 2, -6, 0.3, -2, 2, anchor));
			out.add(box("shin_" + s, PANTS, leg[1], -2, 2, -4.4, 0.3, -2, 2, anchor));
			out.add(box("shoe_" + s, SHOES, leg[1], -2.05, 2.05, -6, -4.4, -2.05, 2.05, anchor));
		}
		return out;
	}
}
