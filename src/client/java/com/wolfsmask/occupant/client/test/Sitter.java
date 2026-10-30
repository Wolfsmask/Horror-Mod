package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whoever had the camera, down on the floor where they fell, seen from where it landed behind
 * them: sitting, leaning back on both hands, legs out in front, staring at where it was, breathing
 * hard. Then round over the shoulder at the camera, and a hand back for it, over the lens.
 * <p>
 * Built of the same blocks as {@link Person}, jointed the same way; posed from what they are doing
 * at each tick rather than worked out, since all they do is sit there and reach.
 */
final class Sitter {
	private static final double PX = Person.PX;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	/** Breathing hard from when they land until they turn; turning; reaching; the hand at the lens. */
	static final int TURN = 46, REACH = 58, COVER = 76;

	private final Vec3 seat, f, r, stare, lens;

	private record Frame(Vec3 o, Vec3 x, Vec3 y, Vec3 z) {
	}

	/**
	 * Sitting at {@code seat} (on the floor), facing along {@code facing}, staring at {@code stare};
	 * the camera on the floor behind them at {@code lens}, to their right.
	 */
	Sitter(Vec3 seat, Vec3 facing, Vec3 stare, Vec3 lens) {
		this.seat = seat;
		this.f = new Vec3(facing.x, 0, facing.z).normalize();
		this.r = new Vec3(-f.z, 0, f.x);
		this.stare = stare;
		this.lens = lens;
	}

	private static double clamp01(double x) {
		return Math.max(0.0, Math.min(1.0, x));
	}

	private static double ease(double x) {
		double c = clamp01(x);
		return c * c * c * (10 - 15 * c + 6 * c * c);
	}

	private static Vec3 point(Frame fr, double x, double y, double z) {
		return fr.o.add(fr.x.scale(x * PX)).add(fr.y.scale(y * PX)).add(fr.z.scale(z * PX));
	}

	/** A frame with {@code up} its y and {@code front} (made square to it) its z. */
	private static Frame frame(Vec3 o, Vec3 up, Vec3 front) {
		Vec3 y = up.normalize();
		Vec3 z = front.subtract(y.scale(front.dot(y)));
		z = z.lengthSqr() < 1e-9 ? new Vec3(0, 0, 1) : z.normalize();
		return new Frame(o, y.cross(z), y, z);
	}

	/** The body at {@code t} ticks after they hit the floor, its parts relative to {@code anchor}. */
	List<Person.Part> pose(int t, Vec3 anchor) {
		// Landing: thrown back past sitting and up again onto the hands.
		double land = t < 6 ? Math.exp(-t / 2.0) * Math.cos(t * 0.9) : 0;
		double turn = ease((t - TURN) / 12.0);
		double reach = ease((t - REACH) / 16.0);
		// Breathing hard, the whole body with it; quicker at first.
		double pant = Math.sin(t * (0.62 - 0.12 * clamp01(t / 60.0))) * (1 - 0.6 * turn);
		Vec3 toLens = new Vec3(lens.x - seat.x, 0, lens.z - seat.z).normalize();
		// Reaching back for it, they lean back further and over onto that side, and slide a little.
		Vec3 hips = seat.add(UP.scale(0.12)).add(toLens.scale(0.5 * reach));
		double lean = Math.toRadians(40 + 18 * land + 2.5 * pant + 14 * reach);
		Vec3 back = f.scale(-1);
		Vec3 pUp = UP.scale(Math.cos(lean)).add(back.scale(Math.sin(lean))).add(r.scale(0.25 * reach)).normalize();
		Frame p = frame(hips, pUp, f);
		// The chest a little forward of the hips, and turned round with the head as they look back.
		Vec3 cUp = pUp.add(f.scale(0.12)).normalize();
		Vec3 cFront = f.scale(1 - 0.75 * turn).add(toLens.scale(0.75 * turn));
		Frame c = frame(point(p, 0, 6, 0), cUp, cFront);
		// The head: on where it was, jerking about a little; then round at the camera.
		Vec3 headO = point(c, 0, 6, 0);
		double jitter = (1 - turn) * 0.04;
		Vec3 onIt = stare.subtract(headO).normalize().add(new Vec3(Math.sin(t * 1.7) * jitter, Math.sin(t * 2.3) * jitter * 0.6, Math.cos(t * 1.3) * jitter));
		Vec3 atLens = lens.subtract(headO).normalize();
		Vec3 look = onIt.scale(1 - turn).add(atLens.scale(turn)).normalize();
		Frame h = frame(headO, cUp.subtract(look.scale(cUp.dot(look))).add(UP.scale(0.3)), look);
		List<Person.Part> out = new ArrayList<>();
		out.add(box("pelvis", Person.SHIRT, p, -4, 4, -0.5, 6, -2, 2, anchor));
		out.add(box("chest", Person.SHIRT, c, -4, 4, 0, 6, -2, 2, anchor));
		out.add(box("head", Person.SKIN, h, -4, 4, 0, 8, -4, 4, anchor));
		out.add(box("hair", Person.HAIR, h, -4.2, 4.2, 6, 8.25, -4.2, 4.2, anchor));
		out.add(box("hair_back", Person.HAIR, h, -4.2, 4.2, 2.5, 8.25, -4.25, -3.2, anchor));
		out.add(box("eye_l", Person.EYES, h, 1.2, 2.7, 3.2, 4.2, 3.95, 4.15, anchor));
		out.add(box("eye_r", Person.EYES, h, -2.7, -1.2, 3.2, 4.2, 3.95, 4.15, anchor));
		// Arms: both hands planted behind them on the floor; the right one comes up off it and back to the lens.
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 shoulder = point(c, 6 * side, 4, 0);
			Vec3 planted = hips.add(UP.scale(-0.1)).add(back.scale(0.6)).add(r.scale(-side * 0.3));
			Vec3 hand = planted;
			if (side < 0 && reach > 0) {
				Vec3 at = lens.add(seat.subtract(lens).normalize().scale(0.05));
				hand = planted.scale(1 - reach).add(at.scale(reach)).add(UP.scale(0.22 * Math.sin(Math.PI * reach)));
			}
			Vec3 pole = back.scale(0.6).add(r.scale(-side * 0.8)).add(UP.scale(0.2));
			Frame[] arm = limb(shoulder, hand, 4, 6, pole);
			String s = i == 0 ? "l" : "r";
			out.add(box("arm_" + s, Person.SHIRT, arm[0], -2, 2, -4.3, 2, -2, 2, anchor));
			out.add(box("forearm_" + s, Person.SKIN, arm[1], -2, 2, -6, 0.3, -2, 2, anchor));
		}
		// Legs out in front, knees up a little, the heels on the floor; drawn up as they turn.
		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? 1 : -1;
			Vec3 hip = point(p, 1.9 * side, 0, 0);
			Vec3 foot = seat.add(f.scale(0.66 - 0.12 * turn)).add(r.scale(-side * (0.13 + 0.03 * Math.sin(t * 0.3 + i)))).add(UP.scale(0.07));
			Frame[] leg = limb(hip, foot, 6, 6, UP.add(f.scale(0.4)));
			String s = i == 0 ? "l" : "r";
			out.add(box("thigh_" + s, Person.PANTS, leg[0], -2, 2, -6, 0.3, -2, 2, anchor));
			out.add(box("shin_" + s, Person.PANTS, leg[1], -2, 2, -4.4, 0.3, -2, 2, anchor));
			out.add(box("shoe_" + s, Person.SHOES, leg[1], -2.05, 2.05, -6, -4.4, -2.05, 2.05, anchor));
		}
		return out;
	}

	/** Two bones from {@code root} to {@code end}, {@code a} and {@code b} pixels long, bent towards {@code pole}. */
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

	private static Person.Part box(String name, String block, Frame fr, double x0, double x1, double y0, double y1, double z0, double z1, Vec3 anchor) {
		Vec3 ex = fr.x.scale((x1 - x0) * PX), ey = fr.y.scale((y1 - y0) * PX), ez = fr.z.scale((z1 - z0) * PX);
		Vec3 o = point(fr, x0, y0, z0).subtract(anchor);
		return new Person.Part(name, block, new double[]{ex.x, ey.x, ez.x, o.x, ex.y, ey.y, ez.y, o.y, ex.z, ey.z, ez.z, o.z, 0, 0, 0, 1});
	}
}
