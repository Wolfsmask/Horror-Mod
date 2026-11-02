package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.client.render.Harness;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.List;

/** The one on the floor after the fall, from the camera where it landed, and from the side. */
public final class SitTest {
	public static void main(String[] a) throws Exception {
		File out = new File(a[0]);
		out.mkdirs();
		String view = a.length > 1 ? a[1] : "lens";
		Vec3 seat = new Vec3(0.0, 64, 0.0);
		Vec3 lens = a.length > 3 ? new Vec3(Double.parseDouble(a[2]), 64.12, Double.parseDouble(a[3])) : new Vec3(-1.9, 64.12, 0.25);
		Vec3 aim = a.length > 5 ? new Vec3(Double.parseDouble(a[4]), 64 + Double.parseDouble(a[5]), a.length > 6 ? Double.parseDouble(a[6]) : 0.1) : new Vec3(0.6, 64.85, 0.0);
		Sitter s = new Sitter(seat, new Vec3(1, 0, 0), new Vec3(2.95, 65.6, 0.1), lens);
		Harness.Scene scene = new Harness.Scene();
		Harness.ROLL = view.equals("lens") ? -12 : 0;
		for (int t = 0; t <= Sitter.COVER + 4; t++) {
			List<Person.Part> parts = s.pose(t, Vec3.ZERO);
			double[][] boxes = new double[parts.size()][];
			String[] blocks = new String[parts.size()];
			for (int i = 0; i < parts.size(); i++) {
				boxes[i] = parts.get(i).m();
				blocks[i] = parts.get(i).block();
			}
			Vec3 cam = view.equals("lens") ? lens : new Vec3(-0.2, 65.0, 3.0);
			Vec3 look = view.equals("lens") ? aim : new Vec3(-0.2, 64.4, 0);
			scene.frame(t, new Vec3(30, 64, 30), 0f, cam, look, 55, boxes, blocks, t % 4 == 0 ? new File(out, String.format("%04d.png", t)) : null);
		}
	}
}
