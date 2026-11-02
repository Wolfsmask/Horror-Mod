package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.client.render.Harness;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.List;

/** The body under the camera at the end of a lowering, from the camera, standing and walking. */
public final class WalkTest {
	public static void main(String[] a) throws Exception {
		File out = new File(a[0]);
		out.mkdirs();
		Harness.Scene scene = new Harness.Scene();
		Harness.ROLL = 0;
		double pitch = Double.parseDouble(a[1]), eyeY = Double.parseDouble(a[2]);
		int k = 0;
		for (double speed : new double[]{0.0, 0.1}) {
			for (double phase : new double[]{0.0, 1.6, 3.2}) {
				Vec3 eye = new Vec3(0, 64 + eyeY, 0);
				double pr = Math.toRadians(pitch);
				Vec3 look = new Vec3(0, -Math.sin(pr), Math.cos(pr));
				List<Person.Part> parts = Walker.pose(eye, look, 64, 0, phase, speed, 0, Vec3.ZERO);
				double[][] boxes = new double[parts.size()][];
				String[] blocks = new String[parts.size()];
				for (int i = 0; i < parts.size(); i++) {
					boxes[i] = parts.get(i).m();
					blocks[i] = parts.get(i).block().isEmpty() ? "minecraft:air" : parts.get(i).block();
				}
				scene.frame(k, new Vec3(30, 64, 30), 0f, eye, eye.add(look), 55, boxes, blocks, new File(out, String.format("%04d.png", k)));
				k++;
			}
		}
	}
}
