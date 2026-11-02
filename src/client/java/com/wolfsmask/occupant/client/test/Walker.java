package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whoever is holding the camera, under it: seen only when they point it down at themselves, or put
 * a hand over it. The hips under the camera, the legs walking as they walk, the right arm up to the
 * camera at their face, the left hanging and swinging, or coming up over the lens. No head: the
 * camera is where it would be.
 * <p>
 * Built of the same blocks as {@link Person}, jointed the same way (see {@link Sitter}).
 */
final class Walker {
	private static final double PX = Person.PX;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private record Frame(Vec3 o, Vec3 x, Vec3 y, Vec3 z) {
	}

	private Walker() {
	}

	private static Vec3 point(Frame fr, double x, double y, double z) {
		return fr.o.add(fr.x.scale(x * PX)).add(fr.y.scale(y * PX)).add(fr.z.scale(z * PX));
	}

	private static Frame frame(Vec3 o, Vec3 up, Vec3 front) {
		Vec3 y = up.normalize();
		Vec3 z = front.subtract(y.scale(front.dot(y)));
		z = z.lengthSqr() < 1e-9 ? new Vec3(0, 0, 1) : z.normalize();
		return new Frame(o, y.cross(z), y, z);
	}

	/**
	 * The body under a camera at {@code eye} looking along {@code look} (a unit vector), on ground at
	 * {@code ground}, the body turned to {@code yaw}, a stride {@code phase} in, going
	 * {@code speed} blocks a tick; {@code cover} how far the left hand has come up over the lens (0
	 * to 1). Relative to {@code anchor}. The head and its parts are there but nothing to see.
	 */
	static List<Person.Part> pose(Vec3 eye, Vec3 look, double ground, double yaw, double phase, double speed, double cover, Vec3 anchor) {
		double r_ = Math.toRadians(yaw);
		Vec3 f = new Vec3(-Math.sin(r_), 0, Math.cos(r_));
		Vec3 r = new Vec3(-f.z, 0, f.x);
		double walking = Math.min(1.0, speed / 0.06);
		// Pointed down, the camera is held out in front of them, at the end of the arm: the body is
		// behind it and under it, and what it sees is their legs and feet going.
		double down = Math.max(0, Math.min(1, (Math.toDegrees(Math.asin(-look.y)) - 20) / 50));
		double bob = 0.025 * walking * Math.abs(Math.sin(phase));
		Vec3 hips = new Vec3(eye.x, ground + 12 * PX - 0.02 + bob, eye.z).subtract(f.scale(0.12 + 0.1 * down));
		Vec3 pUp = UP.add(f.scale(0.06 * walking + 0.1 * down)).normalize();
		Frame p = frame(hips, pUp, f);
		Frame c = frame(point(p, 0, 6, 0), pUp.add(f.scale(0.08 * down)).normalize(), f);
		List<Person.Part> out = new ArrayList<>();
		out.add(box("pelvis", Person.SHIRT, p, -4, 4, -0.5, 6, -2, 2, anchor));
		out.add(box("chest", Person.SHIRT, c, -4, 4, 0, 6, -2, 2, anchor));
		for (String hidden : new String[]{"head", "hair", "hair_back", "eye_l", "eye_r"}) out.add(nothing(hidden, eye, anchor));
		// The right hand at the camera, just behind and under the lens; the left swinging, or over the lens.
		Vec3 side = look.cross(UP);
		side = side.lengthSqr() < 1e-6 ? r : side.normalize();
		Vec3 camUp = side.cross(look).normalize();
		for (int i = 0; i < 2; i++) {
			double s = i == 0 ? 1 : -1;
			Vec3 shoulder = point(c, 6 * s, 4, 0);
			Vec3 hand;
			Vec3 pole;
			if (s < 0) {
				// Behind the camera, whichever way it points: never in front of the lens.
				hand = eye.subtract(look.scale(0.12)).add(side.scale(0.07)).subtract(camUp.scale(0.05));
				pole = r.scale(0.6).subtract(UP.scale(0.8));
			} else {
				double swing = Math.sin(phase) * 0.18 * walking;
				Vec3 hanging = shoulder.subtract(UP.scale(0.56)).add(f.scale(swing + 0.04)).subtract(r.scale(0.04));
				Vec3 over = eye.add(look.scale(0.07)).add(side.scale(-0.02)).subtract(UP.scale(0.01));
				double u = Reel.smooth(cover);
				hand = hanging.scale(1 - u).add(over.scale(u)).add(f.scale(0.12 * Math.sin(Math.PI * u)));
				pole = r.scale(-0.6).subtract(UP.scale(0.5)).add(f.scale(0.2 * u));
			}
			Frame[] arm = limb(shoulder, hand, 4, 6, pole);
			String n = i == 0 ? "l" : "r";
			out.add(box("arm_" + n, Person.SHIRT, arm[0], -2, 2, -4.3, 2, -2, 2, anchor));
			out.add(box("forearm_" + n, Person.SKIN, arm[1], -2, 2, -6, 0.3, -2, 2, anchor));
		}
		// The legs: each foot to and fro under the hips as they walk, lifted as it comes through.
		for (int i = 0; i < 2; i++) {
			double s = i == 0 ? 1 : -1;
			double ph = phase + (i == 0 ? 0 : Math.PI);
			Vec3 hip = point(p, 1.9 * s, 0, 0);
			Vec3 foot = new Vec3(hips.x, ground + 0.07 + 0.09 * walking * Math.max(0, Math.cos(ph)), hips.z)
					.add(f.scale(0.08 + 0.28 * walking * Math.sin(ph))).add(r.scale(-s * 0.12));
			Frame[] leg = limb(hip, foot, 6, 6, f.add(UP.scale(0.2)));
			String n = i == 0 ? "l" : "r";
			out.add(box("thigh_" + n, Person.PANTS, leg[0], -2, 2, -6, 0.3, -2, 2, anchor));
			out.add(box("shin_" + n, Person.PANTS, leg[1], -2, 2, -4.4, 0.3, -2, 2, anchor));
			out.add(box("shoe_" + n, Person.SHOES, leg[1], -2.05, 2.05, -6, -4.4, -2.05, 2.05, anchor));
		}
		return out;
	}

	/** Every part, too small to see, out of the way (while they are not on their feet). */
	static List<Person.Part> none(Vec3 at, Vec3 anchor) {
		List<Person.Part> out = new ArrayList<>();
		for (String[] part : Person.PARTS) out.add(nothing(part[0], at, anchor));
		return out;
	}

	private static Person.Part nothing(String name, Vec3 at, Vec3 anchor) {
		Vec3 o = at.subtract(anchor);
		return new Person.Part(name, "", new double[]{0.001, 0, 0, o.x, 0, 0.001, 0, o.y, 0, 0, 0.001, o.z, 0, 0, 0, 1});
	}

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
