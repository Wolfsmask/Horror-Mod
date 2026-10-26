package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whoever was holding the camera, once it has knocked them down: a person built of blocks the
 * size and colours of a player's body, but jointed as a body is, at the knees and elbows and the
 * small of the back, so they can do what someone on their back does to get away from something.
 * <p>
 * Thrown back, they land flat and the breath goes out of them. Then they come up onto one arm,
 * dig their heels in and shove themselves backwards along the ground, again and again: the
 * planted foot stays where it is while the knee straightens and drives the body back, the torso
 * lurching with it, then the foot and the hand behind them are picked up and put down again
 * further on. The other hand is held up at it the whole time, shaking, and waved at it when they
 * cry out. Their head never leaves it. At the end they go back flat and curl up, arms over their
 * face, knees up, head turned away; and it comes down on them.
 * <p>
 * Everything is worked out a tick at a time, in order: the body has weight and lags behind
 * where it is going, and swings past and settles. Nothing of it is ever below the ground, and
 * whatever is planted on the ground stays exactly where it was put.
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

	private static final Vec3 UP = new Vec3(0, 1, 0);
	/** The way along the ground their head points, lying, and (away from it) to their left. */
	private final Vec3 f, left;
	private final double ground;
	private final int[] pushes;
	private final int strike;
	private final double slide;

	// Where the body is and how it is held, each with how fast it is changing.
	private Vec3 pelvis, pelvisV = Vec3.ZERO;
	private double lift, liftV, curl, curlV, roll, rollV, yaw, yawV, nod, nodV;
	/** Where each foot and hand is: planted, or on its way somewhere. 0 is their left, 1 their right. */
	private final Vec3[] feet = new Vec3[2], hands = new Vec3[2];
	private final Vec3[] footFrom = new Vec3[2], handFrom = new Vec3[2], footTo = new Vec3[2], handTo = new Vec3[2];
	private final int[] footAt = {-99, -99}, handAt = {-99, -99};
	private int tau = -1;
	private Vec3 chest = Vec3.ZERO;

	/**
	 * @param stood  where they were standing when it hit them (their feet), facing it
	 * @param toward the way they were knocked: along the ground, away from it
	 * @param pushes the ticks after the blow at which they shove themselves back
	 * @param strike the tick after the blow at which it comes down on them
	 * @param slide  how far each shove carries them, in blocks
	 */
	Person(Vec3 stood, Vec3 toward, int[] pushes, int strike, double slide) {
		this.f = new Vec3(toward.x, 0, toward.z).normalize();
		this.left = f.cross(UP).normalize();
		this.ground = stood.y;
		this.pushes = pushes;
		this.strike = strike;
		this.slide = slide;
		this.pelvis = stood.add(f.scale(0.2)).add(0, 0.7, 0);
		this.lift = 1.4;
	}

	/** Where their chest is, as last posed: what its legs go into. */
	Vec3 chest() {
		return chest;
	}

	/**
	 * The body this tick ({@code t} ticks after the blow, called once a tick in order), with its
	 * head turned to {@code it} (where its face is).
	 */
	List<Part> pose(int t, Vec3 it, Vec3 anchor) {
		while (tau < t) step(++tau, it);
		return build(it, anchor);
	}

	// ---------------------------------------------------------------- what they are doing

	private static double smooth(double x) {
		double c = Math.max(0.0, Math.min(1.0, x));
		return c * c * (3 - 2 * c);
	}

	private static double bump(double x) {
		return x <= 0 || x >= 1 ? 0.0 : Math.sin(Math.PI * x);
	}

	/** How far they have shoved themselves back by tick t, in blocks, as they mean to. */
	private double shoved(double t) {
		double d = 0;
		for (int p : pushes) d += slide * smooth((t - p) / 5.0);
		return d;
	}

	/** Which shove they are in at t (-1 none), and how far through it (0 to 1 over ten ticks). */
	private int shove(int t) {
		for (int k = pushes.length - 1; k >= 0; k--) if (t >= pushes[k] && t < pushes[k] + 10) return k;
		return -1;
	}

	private boolean crying(int t) {
		return t >= 26 && t < 40 || t >= 56 && t < 72 || t >= 100 && t < strike - 6;
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

	private void step(int t, Vec3 it) {
		boolean down = t >= 7;
		// Where they mean to be: knocked back and down, then shoved further back a push at a time.
		double base = 1.3 + shoved(t);
		double sitUp = t < 14 ? 0.0 : t < 20 ? 0.55 * smooth((t - 14) / 6.0) : t < strike - 6 ? 0.55 : 0.12;
		int k = shove(t);
		double u = k >= 0 ? (t - pushes[k]) / 10.0 : 0;
		// Each shove throws them back, then they come forward over it again.
		sitUp -= 0.14 * bump(u / 0.5);
		double wantLift = down ? sitUp : 1.4 * (1 - smooth((t - 1) / 6.0));
		double wantCurl = t < 14 ? 0.05 : t < strike - 6 ? 0.18 + 0.03 * Math.sin(t * 0.63) : 0.42;   // panting; curled up at the end
		double wantRoll = t < 14 ? 0.0 : t < strike - 6 ? -0.22 + (k >= 0 ? (k % 2 == 0 ? 0.08 : -0.08) * bump(u) : 0) : 0.35;
		// Up off the ground by the thickness of the body, and higher sitting up.
		double height = ground + 2 * PX + 1 * PX * Math.sin(Math.max(0, wantLift));
		Vec3 want = down ? new Vec3(0, 0, 0).add(f.scale(base)).add(origin()).add(0, height - origin().y, 0)
				: origin().add(f.scale(0.2 + 1.1 * smooth((t - 1) / 6.0))).add(0, 0.7 * (1 - smooth((t - 2) / 5.0)) + height - origin().y, 0);
		// The body has weight: it gets there on springs, and goes a little past.
		double[] s;
		double w = down ? 0.45 : 0.6;
		s = spring(pelvis.x, pelvisV.x, want.x, w, 0.55);
		double px = s[0], pvx = s[1];
		s = spring(pelvis.y, pelvisV.y, want.y, w, 0.7);
		double py = s[0], pvy = s[1];
		s = spring(pelvis.z, pelvisV.z, want.z, w, 0.55);
		pelvis = new Vec3(px, Math.max(py, ground + 1.5 * PX), s[0]);
		pelvisV = new Vec3(pvx, pvy, s[1]);
		s = spring(lift, liftV, wantLift, down ? 0.35 : 0.7, 0.6);
		lift = s[0]; liftV = s[1];
		s = spring(curl, curlV, wantCurl, 0.3, 0.6);
		curl = s[0]; curlV = s[1];
		s = spring(roll, rollV, wantRoll, 0.3, 0.6);
		roll = s[0]; rollV = s[1];
		// Landing: the head goes back and hits the ground, and comes up again slowly.
		double wantNod = t < 7 ? -0.3 : t < 11 ? -0.45 : t < strike - 6 ? 0.0 : 0.0;
		s = spring(nod, nodV, wantNod, 0.5, 0.5);
		nod = s[0]; nodV = s[1];
		double wantYaw = t >= strike - 6 ? 0.9 : 0.0;   // turned away at the end
		s = spring(yaw, yawV, wantYaw, 0.35, 0.65);
		yaw = s[0]; yawV = s[1];
		// Its legs going into them: the whole body jolts with it, flattened, twisted, folding up.
		if (t == strike) {
			liftV -= 0.12;
			rollV += 0.16;
			curlV += 0.18;
			pelvisV = pelvisV.add(0, -0.03, 0);
		}
		feet(t, k, u);
		hands(t, k, u, it);
	}

	/** Where they stood, at the height their hips were. */
	private Vec3 origin() {
		return new Vec3(stoodX(), ground, stoodZ());
	}

	private double sx, sz;
	private boolean set;

	private double stoodX() {
		if (!set) init();
		return sx;
	}

	private double stoodZ() {
		if (!set) init();
		return sz;
	}

	private void init() {
		sx = pelvis.x - f.x * 0.2;
		sz = pelvis.z - f.z * 0.2;
		set = true;
	}

	/** The point on the ground at (x, z) of this. */
	private Vec3 onGround(Vec3 p) {
		return new Vec3(p.x, ground, p.z);
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

	/** On its way from where it was to where it is going: lifted off the ground in between. */
	private static Vec3 carried(Vec3 from, Vec3 to, int since, int over, double high) {
		double g = Math.min(1.0, Math.max(0.0, since / (double) over));
		double e = g * g * (3 - 2 * g);
		return from.lerp(to, e).add(0, high * Math.sin(Math.PI * g), 0);
	}

	private void feet(int t, int k, double u) {
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 hip = pelvis.add(left.scale(side * 1.9 * PX));
			if (t < 7) {
				// Going over backwards: the feet come up off the ground.
				feet[i] = hip.subtract(f.scale(0.45)).add(0, 0.25 + 0.1 * side, 0);
				footTo[i] = null;
				continue;
			}
			if (t < 12) {
				// Down flat, legs out straight along the ground.
				feet[i] = onGround(hip.subtract(f.scale(0.68))).add(left.scale(side * 0.08)).add(0, 0.5 * PX, 0);
				footTo[i] = null;
				continue;
			}
			if (t >= strike - 6) {
				// Knees up to the chest, the feet off the ground; at the blow, kicked out and back.
				Vec3 tucked = pelvis.subtract(f.scale(0.2)).add(left.scale(side * 0.1)).add(0, 0.34, 0);
				if (t >= strike && t < strike + 3) tucked = tucked.subtract(f.scale(0.22)).add(0, -0.08, 0);
				feet[i] = feet[i] == null ? tucked : feet[i].lerp(tucked, 0.45);
				continue;
			}
			// Heels dug in in front of them, knees up; re-planted after each shove of that leg.
			Vec3 spot = onGround(pelvis.subtract(f.scale(0.44)).add(left.scale(side * 0.13))).add(0, 0.5 * PX, 0);
			boolean mine = k >= 0 && (k % 2 == 0 ? 1 : 0) == i;
			if (footTo[i] == null) {
				plantFoot(i, spot, t);
			} else if (mine && u >= 0.5 && footAt[i] < pushes[k] + 5) {
				// The shove is done: the foot comes up and goes down again nearer.
				Vec3 next = onGround(pelvis.add(f.scale(slide * 0.5)).subtract(f.scale(0.44)).add(left.scale(side * 0.13))).add(0, 0.5 * PX, 0);
				plantFoot(i, next, t);
			} else if (!mine && k >= 0 && u >= 0.7 && footTo[i].distanceTo(spot) > 0.2) {
				plantFoot(i, spot, t);
			}
			feet[i] = carried(footFrom[i], footTo[i], t - footAt[i], 4, 0.07);
		}
	}

	private void hands(int t, int k, double u, Vec3 it) {
		// Their left hand held up at it; their right one behind them on the ground, holding them up.
		Vec3 shoulderL = point(chestFrame(), 6, 4, 0), shoulderR = point(chestFrame(), -6, 4, 0);
		Vec3 face = it == null ? pelvis.subtract(f.scale(2)).add(0, 3, 0) : it;
		double reach = 10 * PX;
		if (t < 7) {
			// Thrown back: the arms fly up and out.
			hands[0] = shoulderL.add(left.scale(0.25)).add(0, 0.35, 0).subtract(f.scale(0.1));
			hands[1] = shoulderR.subtract(left.scale(0.25)).add(0, 0.35, 0).subtract(f.scale(0.1));
			handTo[0] = handTo[1] = null;
			return;
		}
		if (t < 14) {
			// Flung out where they landed, and the left one coming up off the ground.
			Vec3 outL = onGround(shoulderL.add(left.scale(0.42)).subtract(f.scale(0.1))).add(0, 0.5 * PX, 0);
			Vec3 outR = onGround(shoulderR.subtract(left.scale(0.42)).subtract(f.scale(0.12))).add(0, 0.5 * PX, 0);
			hands[0] = hands[0] == null ? outL : hands[0].lerp(t < 11 ? outL : outL.add(0, 0.2, 0).subtract(f.scale(0.1)), 0.4);
			hands[1] = hands[1] == null ? outR : hands[1].lerp(outR, 0.4);
			handTo[0] = handTo[1] = null;
			return;
		}
		if (t >= strike - 6) {
			// Over their face, crossed, as it comes down; at the blow, knocked back down onto it.
			Vec3 head = point(headFrame(it), 0, 4, 2);
			Vec3 at = face.subtract(head).normalize();
			for (int i = 0; i < 2; i++) {
				double side = i == 0 ? 1 : -1;
				Vec3 guard = head.add(at.scale(0.2)).add(UP.scale(0.1)).add(left.scale(-side * 0.05));
				if (t >= strike && t < strike + 4) guard = guard.subtract(at.scale(0.12));
				hands[i] = hands[i] == null ? guard : hands[i].lerp(guard, 0.5);
			}
			handTo[0] = handTo[1] = null;
			return;
		}
		// Up at it, shaking, the arm not quite straight; waved at it while they cry out.
		Vec3 to = face.subtract(shoulderL).normalize();
		Vec3 across = to.cross(UP).normalize();
		Vec3 held = shoulderL.add(to.scale(reach * 0.86)).add(UP.scale(0.04));
		double sh = shake(t, 1.7) * 0.025;
		if (crying(t)) held = held.add(across.scale(0.1 * Math.sin(t * 0.9))).add(UP.scale(0.04 * Math.sin(t * 1.8)));
		held = held.add(across.scale(sh)).add(UP.scale(shake(t, 4.1) * 0.02));
		hands[0] = hands[0] == null ? held : hands[0].lerp(held, 0.55);
		handTo[0] = null;
		// Behind them, propped on it; put down again further back when it is left too far forward.
		Vec3 spot = onGround(pelvis.add(f.scale(0.26)).subtract(left.scale(0.24))).add(0, 0.5 * PX, 0);
		if (handTo[1] == null || (handTo[1].distanceTo(spot) > 0.16 && t - handAt[1] > 5 && (k < 0 || u > 0.55))) plantHand(1, spot, t);
		hands[1] = carried(handFrom[1], handTo[1], t - handAt[1], 4, 0.06);
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

	/** Their hips: lying along the ground, their head along {@code f}, lifted by {@code lift}, rolled by {@code roll}. */
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

	/** Their head, on their neck, turned to look at {@code it} (as far as a neck turns), or away. */
	private Frame headFrame(Vec3 it) {
		Frame c = chestFrame();
		Vec3 o = point(c, 0, 6, 0);
		double ly = 0, lp = 0;
		if (it != null) {
			Vec3 d = it.subtract(o);
			double dx = d.dot(c.x), dy = d.dot(c.y), dz = d.dot(c.z);
			ly = Math.max(-1.2, Math.min(1.2, Math.atan2(dx, dz)));
			lp = Math.max(-0.7, Math.min(1.2, Math.atan2(-dy, Math.hypot(dx, dz))));
		}
		ly = ly * (1 - Math.min(1, Math.abs(yaw))) + yaw;
		lp += nod;
		Vec3 y = rotate(c.y, c.y, 0), z = rotate(c.z, c.y, ly), x = rotate(c.x, c.y, ly);
		Vec3 z2 = rotate(z, x, lp), y2 = rotate(y, x, lp);
		return new Frame(o, x, y2, z2);
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

	private List<Part> build(Vec3 it, Vec3 anchor) {
		Frame p = pelvisFrame(), c = chestFrame(), h = headFrame(it);
		chest = point(c, 0, 3, 0);
		List<Part> out = new ArrayList<>();
		out.add(box("pelvis", SHIRT, p, -4, 4, -0.5, 6, -2, 2, anchor));
		out.add(box("chest", SHIRT, c, -4, 4, 0, 6, -2, 2, anchor));
		out.add(box("head", SKIN, h, -4, 4, 0, 8, -4, 4, anchor));
		out.add(box("hair", HAIR, h, -4.2, 4.2, 6, 8.25, -4.2, 4.2, anchor));
		out.add(box("hair_back", HAIR, h, -4.2, 4.2, 2.5, 8.25, -4.25, -3.2, anchor));
		out.add(box("eye_l", EYES, h, 1.2, 2.7, 3.2, 4.2, 3.95, 4.15, anchor));
		out.add(box("eye_r", EYES, h, -2.7, -1.2, 3.2, 4.2, 3.95, 4.15, anchor));
		// Arms: from the shoulder, four pixels to the elbow, six on to the end of the hand.
		Frame pl = pelvisFrame();
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 shoulder = point(c, 6 * side, 4, 0);
			Vec3 hand = hands[i] == null ? point(c, 6 * side, -6, 0) : hands[i];
			// Elbows out and back, behind them; held up at it, down and out.
			Vec3 pole = i == 0 && hands[0] != null && hands[0].y > shoulder.y - 0.05 ? c.x.scale(side).add(UP.scale(-0.6))
					: c.x.scale(side * 0.7).subtract(c.z.scale(0.7)).add(f.scale(0.3));
			Frame[] arm = limb(shoulder, hand, 4, 6, pole);
			out.add(box("arm_" + (i == 0 ? "l" : "r"), SHIRT, arm[0], -2, 2, -4.3, 2, -2, 2, anchor));
			out.add(box("forearm_" + (i == 0 ? "l" : "r"), SKIN, arm[1], -2, 2, -6, 0.3, -2, 2, anchor));
		}
		// Legs: hip to knee six pixels, knee to sole six; the knees up.
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 hip = point(pl, 1.9 * side, 0, 0);
			Vec3 foot = feet[i] == null ? point(pl, 1.9 * side, -12, 0) : feet[i];
			Vec3 pole = UP.add(pl.z.scale(0.6)).add(pl.x.scale(side * 0.25));
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
