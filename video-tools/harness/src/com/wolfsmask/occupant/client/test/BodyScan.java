package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * For a scene from the camera sim (name.csv, name.raw, name.marks): the body under the camera posed
 * each frame as Tape.Body poses it, and how much of the picture it covers (rays through a grid, each
 * tested against every part's box). Prints frames where it covers a lot, or the lens is inside a part.
 */
public final class BodyScan {
	public static void main(String[] a) throws Exception {
		Path dir = Path.of(a[0]);
		String name = a[1];
		boolean useBacking = a.length < 3 || !a[2].equals("old");
		List<double[]> flat = read(dir.resolve(name + ".csv"));
		List<double[]> raw = read(dir.resolve(name + ".raw"));
		int cover = -1, end = flat.size();
		for (String l : Files.readAllLines(dir.resolve(name + ".marks"))) {
			String[] w = l.trim().split(" ");
			if (w.length < 2) continue;
			if (w[1].equals("cover") && cover < 0) cover = Integer.parseInt(w[0]);
			if (w[1].equals("end")) end = Integer.parseInt(w[0]);
		}
		int n = flat.size();
		double[] yaw = new double[n];
		double by = raw.get(0)[2];
		int backed = -100, cut = -100;
		for (int t = 0; t < n; t++) {
			double[] r = raw.get(t), q = raw.get(Math.max(0, t - 4)), b = raw.get(Math.max(0, t - 1));
			if (useBacking && r.length > 13 && r[13] > 0) backed = t;
			if (t > 0 && Math.hypot(r[0] - b[0], r[1] - b[1]) > 0.6) {
				cut = t;
				by = r[2];
			}
			double dx = r[0] - q[0], dz = r[1] - q[1];
			boolean backing = t - backed < 16;
			if (Math.hypot(dx, dz) > 0.12 && !backing && t - cut >= 4) by += wrap(Math.toDegrees(Math.atan2(-dx, dz)) - by) * 0.2;
			else if (backing) by += wrap(r[2] - by) * 0.3;
			else if (Math.abs(wrap(r[2] - by)) > 35) by += wrap(r[2] - by) * 0.1;
			double off = wrap(by - r[2]);
			if (Math.abs(off) > 60) by = r[2] + Math.signum(off) * 60;
			yaw[t] = by;
		}
		double ground = 64;
		double th = Math.tan(Math.toRadians(27.5)), tw = th * 16 / 9;
		int gx = 48, gy = 27;
		StringBuilder out = new StringBuilder();
		double worst = 0;
		int worstT = -1;
		for (int t = 0; t < n; t++) {
			double[] r = raw.get(t);
			boolean standing = r[9] > 0 && r[11] > 0 && r[4] > -0.25;
			if (!standing) continue;
			double[] p = flat.get(t);
			Vec3 eye = new Vec3(p[0], p[1], p[2]);
			double yr = Math.toRadians(p[3]), pr = Math.toRadians(p[4]);
			Vec3 look = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
			double u = cover >= 0 && t >= cover ? Math.min(1.0, (t - cover + 1.0) / Math.max(1.0, (end - cover) * 0.75)) : 0;
			List<Person.Part> parts = Walker.pose(eye, look, ground, yaw[t], r[7], r[5], u, Vec3.ZERO);
			Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 up = right.cross(look).normalize();
			int hit = 0, inside = 0;
			int bottomHit = 0;
			for (int j = 0; j < gy; j++) {
				for (int i = 0; i < gx; i++) {
					double sx = ((i + 0.5) / gx * 2 - 1) * tw, sy = (1 - (j + 0.5) / gy * 2) * th;
					Vec3 d = look.add(right.scale(sx)).add(up.scale(sy)).normalize();
					double best = 1e9;
					for (Person.Part part : parts) {
						if (part.block().isEmpty() || part.name().startsWith("hand")) continue;
						double h = ray(part.m(), eye, d);
						if (h >= 0) best = Math.min(best, h);
					}
					if (best < 1e8) {
						hit++;
						if (best < 0.06) inside++;
						if (j > gy * 2 / 3) bottomHit++;
					}
				}
			}
			double frac = hit / (double) (gx * gy);
			if (frac > worst) {
				worst = frac;
				worstT = t;
			}
			if (frac > 0.12 || inside > 0) {
				out.append(String.format("  t=%d cover %.0f%% (bottom third %d) inside %d pitch %.0f u %.2f%n", t, frac * 100, bottomHit, inside, p[4], u));
			}
		}
		System.out.printf("%s: worst %.0f%% at %d%n%s", name, worst * 100, worstT, out);
	}

	/** Where along the ray from {@code o} in direction {@code d} it first meets the box {@code m} (unit cube mapped), or -1. */
	private static double ray(double[] m, Vec3 o, Vec3 d) {
		// Columns of m: ex, ey, ez; origin (m[3], m[7], m[11]).
		double[][] A = {{m[0], m[1], m[2]}, {m[4], m[5], m[6]}, {m[8], m[9], m[10]}};
		double[][] inv = invert(A);
		if (inv == null) return -1;
		double[] oo = {o.x - m[3], o.y - m[7], o.z - m[11]};
		double[] lo = mul(inv, oo), ld = mul(inv, new double[]{d.x, d.y, d.z});
		double tmin = -1e9, tmax = 1e9;
		for (int k = 0; k < 3; k++) {
			if (Math.abs(ld[k]) < 1e-12) {
				if (lo[k] < 0 || lo[k] > 1) return -1;
				continue;
			}
			double t1 = (0 - lo[k]) / ld[k], t2 = (1 - lo[k]) / ld[k];
			tmin = Math.max(tmin, Math.min(t1, t2));
			tmax = Math.min(tmax, Math.max(t1, t2));
		}
		// From inside a box nothing of it shows (only its back faces, which are not drawn).
		if (tmin < 0 || tmax < tmin || tmin < 0.05) return -1;
		return tmin;
	}

	private static double[] mul(double[][] a, double[] v) {
		return new double[]{a[0][0] * v[0] + a[0][1] * v[1] + a[0][2] * v[2], a[1][0] * v[0] + a[1][1] * v[1] + a[1][2] * v[2],
				a[2][0] * v[0] + a[2][1] * v[1] + a[2][2] * v[2]};
	}

	private static double[][] invert(double[][] m) {
		double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2], g = m[2][0], h = m[2][1], i = m[2][2];
		double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
		if (Math.abs(det) < 1e-12) return null;
		return new double[][]{{(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
				{(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
				{(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}};
	}

	private static double wrap(double x) {
		x = x % 360;
		if (x >= 180) x -= 360;
		if (x < -180) x += 360;
		return x;
	}

	private static List<double[]> read(Path p) throws Exception {
		List<double[]> out = new ArrayList<>();
		for (String l : Files.readAllLines(p)) {
			if (l.isBlank()) continue;
			String[] w = l.split(",");
			double[] v = new double[w.length];
			for (int k = 0; k < w.length; k++) v[k] = Double.parseDouble(w[k]);
			out.add(v);
		}
		return out;
	}
}
