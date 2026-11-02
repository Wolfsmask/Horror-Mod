package com.wolfsmask.occupant.client.test;

import net.minecraft.world.phys.Vec3;

public final class SitDump {
	public static void main(String[] a) {
		Sitter s = new Sitter(new Vec3(0.0, 64, 0.0), new Vec3(1, 0, 0), new Vec3(3.4, 66.3, 0.4), new Vec3(-1.9, 64.12, 0.25));
		for (Person.Part p : s.pose(24, Vec3.ZERO)) {
			double[] m = p.m();
			// the box's corner at (0,0,0) and its far corner
			System.out.printf("%-10s o=(%.2f %.2f %.2f) ey=(%.2f %.2f %.2f) far=(%.2f %.2f %.2f)%n", p.name(), m[3], m[7], m[11], m[1], m[5], m[9],
					m[3] + m[0] + m[1] + m[2], m[7] + m[4] + m[5] + m[6], m[11] + m[8] + m[9] + m[10]);
		}
	}
}
