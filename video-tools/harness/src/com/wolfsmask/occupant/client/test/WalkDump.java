package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

public final class WalkDump {
	public static void main(String[] a) {
		double pitch = Double.parseDouble(a[0]), eyeY = Double.parseDouble(a[1]);
		double pr = Math.toRadians(pitch);
		Vec3 eye = new Vec3(0, 64 + eyeY, 0);
		Vec3 f = new Vec3(0, -Math.sin(pr), Math.cos(pr));
		Vec3 right = new Vec3(-1, 0, 0);
		Vec3 up = new Vec3(0, Math.cos(pr), Math.sin(pr));
		double th = Math.tan(Math.toRadians(27.5));
		for (double[] sp : new double[][]{{0, 0}, {0.1, 1.6}, {0.1, 4.7}}) {
			System.out.println("speed " + sp[0] + " phase " + sp[1]);
			for (Person.Part p : Walker.pose(eye, f, 64, 0, sp[1], sp[0], 0, Vec3.ZERO)) {
				if (p.block().isEmpty()) continue;
				double[] m = p.m();
				double umin = 9, umax = -9, vmin = 9, vmax = -9;
				int behind = 0;
				for (int i = 0; i < 8; i++) {
					double bx = (i & 1), by = (i >> 1 & 1), bz = (i >> 2 & 1);
					double x = m[3] + m[0] * bx + m[1] * by + m[2] * bz, y = m[7] + m[4] * bx + m[5] * by + m[6] * bz, z = m[11] + m[8] * bx + m[9] * by + m[10] * bz;
					Vec3 d = new Vec3(x, y, z).subtract(eye);
					double zf = d.dot(f);
					if (zf <= 0.01) { behind++; continue; }
					double u = d.dot(right) / zf / (th * 16 / 9), v = d.dot(up) / zf / th;
					umin = Math.min(umin, u); umax = Math.max(umax, u); vmin = Math.min(vmin, v); vmax = Math.max(vmax, v);
				}
				boolean inFrame = umax > -1 && umin < 1 && vmax > -1 && vmin < 1;
				System.out.printf("  %-10s %s u[%.2f,%.2f] v[%.2f,%.2f] behind=%d%n", p.name(), inFrame ? "IN " : "out", umin, umax, vmin, vmax, behind);
			}
		}
	}
}
