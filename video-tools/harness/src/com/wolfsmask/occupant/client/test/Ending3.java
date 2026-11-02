package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.client.render.Harness;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.List;

/** The escape attempts: from the camera on the ground, or from the side. */
public final class Ending3 {
	static double p(String k, double d) { return Double.parseDouble(System.getProperty(k, String.valueOf(d))); }

	public static final double[][] APPROACH = {{0, 0}, {5, 0.6}, {14, 0.6}, {Person.KICK, 1.9}, {Person.ROLL, 2.3}, {Person.SLIP, 2.5},
			{Person.DRAG, 3.0}, {Person.DRAG + 6, 2.85}, {Person.STRIKE - 2, 3.6}, {Person.STRIKE + 6, 3.7}};

	public static double d(double t) {
		if (t <= 0) return 0;
		for (int k = 0; k + 1 < APPROACH.length; k++) {
			if (t >= APPROACH[k][0] && t <= APPROACH[k + 1][0]) {
				double u = (t - APPROACH[k][0]) / (APPROACH[k + 1][0] - APPROACH[k][0]);
				return APPROACH[k][1] + (APPROACH[k + 1][1] - APPROACH[k][1]) * u * u * (3 - 2 * u);
			}
		}
		return APPROACH[APPROACH.length - 1][1];
	}

	public static void main(String[] a) throws Exception {
		File out = new File(a[0]);
		out.mkdirs();
		String view = a.length > 1 ? a[1] : "tape";
		Vec3 here = new Vec3(0.5, 64, 0.5), f = new Vec3(0, 0, 1), r = new Vec3(-1, 0, 0);
		Person person = new Person(here, f);
		Harness.Scene scene = new Harness.Scene();
		OccupantEntity it = scene.creature;
		it.mode = OccupantEntity.Mode.AMBUSH;
		Entity chestMark = new Entity(), ankleMark = new Entity();
		for (Entity m : new Entity[]{chestMark, ankleMark}) {
			m.bbHeight = 0.01f;
			m.bbWidth = 0.01f;
			m.level = it.level;
			it.level.entities.put(m.getId(), m);
		}
		Vec3 lens = here.add(f.scale(p("la", 4.2))).add(r.scale(p("lb", 1.2))).add(0, p("lh", 0.15), 0);
		Vec3 aim = here.add(f.scale(p("aa", -1.2))).add(r.scale(p("ab", 0.2))).add(0, p("ah", 1.2), 0);
		Harness.ROLL = view.equals("tape") ? p("roll", 84) : 0;
		double fov = view.equals("tape") ? p("fov", 82) : p("sfov", 62);
		int end = Person.STRIKE + 2;
		java.io.PrintWriter metrics = System.getProperty("metrics") != null ? new java.io.PrintWriter(System.getProperty("metrics")) : null;
		for (int t = -10; t <= end; t++) {
			Vec3 feet = here.subtract(f.scale(2.6)).add(f.scale(Person.itCame(t)));
			Vec3 face = feet.add(0, 4.6, 0).add(f.scale(0.9));
			List<Person.Part> parts = t >= 2 ? person.pose(t, face, Vec3.ZERO) : List.of();
			if (metrics != null && t >= 2) {
				double lo = 1e9; String low = ""; StringBuilder row = new StringBuilder(String.valueOf(t));
				for (Person.Part q : parts) {
					double[] m = q.m();
					for (int k = 0; k < 8; k++) { double yy = m[7] + ((k & 1) * m[4]) + (((k >> 1) & 1) * m[5]) + (((k >> 2) & 1) * m[6]); if (yy < lo) { lo = yy; low = q.name(); } }
					double cx = m[3] + 0.5 * (m[0] + m[1] + m[2]), cy = m[7] + 0.5 * (m[4] + m[5] + m[6]), cz = m[11] + 0.5 * (m[8] + m[9] + m[10]);
					row.append(String.format(java.util.Locale.ROOT, ",%.4f,%.4f,%.4f", cx, cy, cz));
				}
				metrics.println(String.format(java.util.Locale.ROOT, "%.4f", lo - here.y) + "," + row);
				if (lo - here.y < -0.01) System.out.printf("t=%d low %s %.3f%n", t, low, lo - here.y);
				if (System.getProperty("DBGLOW") != null && lo - here.y > 0.02) {
					StringBuilder sb = new StringBuilder("DBG t=" + t);
					for (Person.Part q : parts) { double[] m = q.m(); double mm = 1e9; for (int k = 0; k < 8; k++) mm = Math.min(mm, m[7] + ((k & 1) * m[4]) + (((k >> 1) & 1) * m[5]) + (((k >> 2) & 1) * m[6])); sb.append(String.format(java.util.Locale.ROOT, " %s=%.3f", q.name(), mm - here.y)); }
					System.out.println(sb);
				}
			}
			List<Vec3> grips = t >= 2 ? person.grips(t) : List.of();
			Entity[] marks = {chestMark, ankleMark};
			int[] held = new int[grips.size()];
			for (int g = 0; g < grips.size(); g++) {
				marks[g].old = marks[g].pos;
				marks[g].pos = grips.get(g);
				held[g] = marks[g].getId();
			}
			it.held = held;
			Vec3 ch = person.chest();
			double mid = t < 2 ? 1.5 : (ch.z - here.z) - 0.35;
			double sideways = p("sd", -3.2);
			Vec3 cam = view.equals("side") ? here.add(r.scale(sideways)).add(f.scale(mid)).add(0, 1.1, 0) : lens;
			Vec3 look = view.equals("side") ? here.add(f.scale(mid)).add(0, 0.35, 0) : aim;
			double[][] boxes = new double[parts.size()][];
			String[] blocks = new String[parts.size()];
			for (int i = 0; i < parts.size(); i++) {
				boxes[i] = parts.get(i).m();
				blocks[i] = parts.get(i).block();
			}
			scene.frame(t + 10, feet, 0.0f, cam, look, fov, boxes, blocks, t >= 10 ? new File(out, String.format("%04d.png", t)) : null);
		}
		if (metrics != null) metrics.close();
	}
}
