package com.wolfsmask.occupant.client.render;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the real gait and pose code against a flat world and draws the result, a frame a tick,
 * from a camera: for seeing how it moves without starting the game.
 *
 *   java Harness <out dir> <walk|creep|chase|stand> [frames] [front|side]
 */
public final class Harness {
	static final int W = Integer.getInteger("W", 540), H = Integer.getInteger("H", 960);
	/** The picture turned this far (degrees), as a camera lying on its side. */
	public static double ROLL = 0;

	/** A flat world with it in it, and a camera. */
	public static final class Scene {
		public final Level level = new Level();
		public final OccupantEntity creature = new OccupantEntity();
		final Entity viewer = new Entity();
		final ModelPart root = OccupantGeometry.create().bakeRoot();
		final OccupantPose pose = new OccupantPose(root);
		OccupantRenderState last;

		public Scene() {
			level.solid = p -> p[1] < 64;
			creature.level = level;
			level.entities.put(creature.getId(), creature);
			viewer.level = level;
			Minecraft.getInstance().player = viewer;
		}

		/** Where its eyes were drawn last frame, or null. */
		public Vec3 face() {
			return last == null ? null : last.eyes;
		}

		/** Moves it to {@code at} facing {@code bodyRot}, poses it, and draws it with the given boxes, if {@code file}. */
		public void frame(int t, Vec3 at, float bodyRot, Vec3 cam, Vec3 look, double fov, double[][] boxes, String[] blocks, File file) throws Exception {
			Entity e = creature;
			e.old = e.tickCount == 0 ? at : e.pos;
			e.pos = at;
			e.tickCount = t;
			viewer.pos = cam.subtract(0, 1.62, 0);
			viewer.old = viewer.pos;
			viewer.view = look.subtract(cam).normalize();
			OccupantRenderState s = new OccupantRenderState();
			s.x = at.x; s.y = at.y; s.z = at.z;
			s.bodyRot = bodyRot;
			s.ageInTicks = t;
			s.distanceToCameraSq = cam.distanceToSqr(at);
			s.mode = creature.mode;
			s.form = OccupantEntity.Form.REVEALED;
			s.seed = e.getId();
			s.act = 4;
			OccupantFit.fit(level, s, s.x, s.y, s.z);
			LegGait.of(creature).update(creature, s, s.occupantScale, OccupantFit.CROUCH_DROP * s.crouch);
			OccupantFit.watch(s, 1.0f);
			if (System.getenv("DEBUG") != null && t % 2 == 0) System.out.printf("t=%d sag=%.2f off=(%.2f,%.2f) lean=%.2f/%.2f upright=%b jolt=%.2f scale=%.2f crouch=%.2f%n", t, s.sag, s.offsetX, s.offsetZ, s.leanForward, s.leanSide, s.upright, s.jolt, s.occupantScale, s.crouch);
			root.resetAll();
			Vec3 to = cam.subtract(at.add(0, e.getEyeHeight(), 0));
			float lookY = (float) Math.toRadians(Math.toDegrees(Math.atan2(-to.x, to.z)) - bodyRot);
			float lookX = (float) -Math.atan2(to.y, Math.hypot(to.x, to.z));
			pose.apply(s, lookX, lookY);
			last = s;
			if (file != null) draw(root, s, cam, look, fov, boxes, blocks, file);
		}
	}

	public static void main(String[] a) throws Exception {
		File out = new File(a[0]);
		out.mkdirs();
		String scenario = a.length > 1 ? a[1] : "walk";
		int frames = a.length > 2 ? Integer.parseInt(a[2]) : 160;
		String view = a.length > 3 ? a[3] : "front";
		Scene scene = new Scene();
		double speed = scenario.equals("walk") ? 0.06 : scenario.equals("creep") ? 0.017 : scenario.equals("chase") ? 0.2 : 0.0;
		scene.creature.mode = scenario.equals("chase") ? OccupantEntity.Mode.CHASE : scenario.equals("creep") ? OccupantEntity.Mode.AMBUSH : OccupantEntity.Mode.STARE;
		Vec3 start = new Vec3(0.5, 64, 0.5);
		for (int t = 0; t < frames; t++) {
			Vec3 at = start.add(0, 0, Math.max(0, t - 20) * speed);
			Vec3 cam = view.equals("side") ? new Vec3(11.0, 66.0, at.z) : new Vec3(7.5, 65.6, 9.0);
			Vec3 look = view.equals("side") ? new Vec3(0.5, 66.8, at.z) : at.add(0, 2.6, 0);
			scene.frame(t, at, 0.0f, cam, look, 70.0, new double[0][], new String[0], new File(out, String.format("%04d.png", t)));
		}
	}

	// ---------------------------------------------------------------- drawing

	record Face(double depth, int[] xs, int[] ys, Color c) {}

	static void draw(ModelPart root, OccupantRenderState s, Vec3 cam, Vec3 target, double fov, double[][] boxes, String[] blocks, File file) throws Exception {
		double[] m = mul(translate(s.x + s.offsetX, s.y, s.z + s.offsetZ), rotY(Math.toRadians(180.0 - s.bodyRot)));
		m = mul(m, scale(-1, -1, 1));
		m = mul(m, scale(s.occupantScale, s.occupantScale, s.occupantScale));
		m = mul(m, translate(0, -1.501, 0));
		List<Face> faces = new ArrayList<>();
		Cam c = new Cam(cam, target, fov);
		walk(root, m, c, faces, "");
		for (int i = 0; i < boxes.length; i++) {
			double[] b = boxes[i];
			Color col = color(blocks[i]);
			Vec3[] v = new Vec3[8];
			for (int k = 0; k < 8; k++) v[k] = apply(b, k & 1, (k >> 1) & 1, (k >> 2) & 1);
			cube(v, col, c, faces);
		}
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(24, 26, 30));
		g.fillRect(0, 0, W, H);
		g.setColor(new Color(60, 56, 50));
		for (int i = -12; i <= 24; i++) {
			line(g, c, new Vec3(-12, 64, i), new Vec3(14, 64, i));
			line(g, c, new Vec3(i - 6, 64, -12), new Vec3(i - 6, 64, 24));
		}
		faces.sort((p, q) -> Double.compare(q.depth, p.depth));
		for (Face f : faces) {
			g.setColor(f.c);
			g.fillPolygon(f.xs, f.ys, 4);
		}
		g.setColor(Color.LIGHT_GRAY);
		g.drawString(file.getName(), 10, 20);
		g.dispose();
		ImageIO.write(img, "png", file);
	}

	static Color color(String block) {
		if (block.contains("cyan")) return new Color(40, 140, 150);
		if (block.contains("blue")) return new Color(52, 56, 150);
		if (block.contains("gray")) return new Color(70, 72, 76);
		if (block.contains("terracotta")) return new Color(212, 178, 160);
		if (block.contains("brown")) return new Color(96, 60, 32);
		if (block.contains("black")) return new Color(12, 12, 14);
		return Color.MAGENTA;
	}

	static void line(Graphics2D g, Cam c, Vec3 a, Vec3 b) {
		double[] p = c.project(a), q = c.project(b);
		if (p == null || q == null) return;
		g.drawLine((int) p[0], (int) p[1], (int) q[0], (int) q[1]);
	}

	static void cube(Vec3[] v, Color base, Cam c, List<Face> faces) {
		int[][] quads = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
		for (int[] f : quads) {
			int[] xs = new int[4], ys = new int[4];
			double depth = 0;
			boolean ok = true;
			for (int k = 0; k < 4; k++) {
				double[] p = c.project(v[f[k]]);
				if (p == null) { ok = false; break; }
				xs[k] = (int) Math.round(p[0]);
				ys[k] = (int) Math.round(p[1]);
				depth += p[2];
			}
			if (!ok) continue;
			Vec3 n = v[f[1]].subtract(v[f[0]]).cross(v[f[3]].subtract(v[f[0]])).normalize();
			double light = 0.45 + 0.55 * Math.abs(n.dot(new Vec3(-0.4, 0.8, 0.45).normalize()));
			Color col = new Color((int) (base.getRed() * light), (int) (base.getGreen() * light), (int) (base.getBlue() * light));
			faces.add(new Face(depth / 4, xs, ys, col));
		}
	}

	static void walk(ModelPart part, double[] parent, Cam c, List<Face> faces, String path) {
		double[] m = mul(parent, translate(part.x / 16.0, part.y / 16.0, part.z / 16.0));
		m = mul(m, mul(rotZ(part.zRot), mul(rotY(part.yRot), rotX(part.xRot))));
		m = mul(m, scale(part.xScale, part.yScale, part.zScale));
		String name = path + "/" + part.name;
		Color base = name.contains("leg") ? new Color(205, 200, 190) : name.contains("skull") || name.contains("jaw") ? new Color(232, 226, 214)
				: name.contains("hair") ? new Color(40, 38, 40) : new Color(70, 66, 72);
		for (float[] q : part.cubes) {
			double x0 = q[0] / 16.0, y0 = q[1] / 16.0, z0 = q[2] / 16.0, x1 = (q[0] + q[3]) / 16.0, y1 = (q[1] + q[4]) / 16.0, z1 = (q[2] + q[5]) / 16.0;
			Vec3[] v = new Vec3[8];
			for (int i = 0; i < 8; i++) v[i] = apply(m, (i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
			cube(v, base, c, faces);
		}
		for (ModelPart child : part.children.values()) walk(child, m, c, faces, name);
	}

	static final class Cam {
		final Vec3 eye, fwd, right, up;
		final double f;
		Cam(Vec3 eye, Vec3 target, double fovDeg) {
			this.eye = eye;
			fwd = target.subtract(eye).normalize();
			Vec3 r0 = fwd.cross(new Vec3(0, 1, 0)).normalize(), u0 = r0.cross(fwd);
			double a = Math.toRadians(ROLL);
			right = r0.scale(Math.cos(a)).add(u0.scale(Math.sin(a)));
			up = u0.scale(Math.cos(a)).subtract(r0.scale(Math.sin(a)));
			f = (H / 2.0) / Math.tan(Math.toRadians(fovDeg / 2));
		}
		double[] project(Vec3 p) {
			Vec3 d = p.subtract(eye);
			double z = d.dot(fwd);
			if (z < 0.05) return null;
			return new double[]{W / 2.0 + d.dot(right) / z * f, H / 2.0 - d.dot(up) / z * f, z};
		}
	}

	// 4x4 row-major matrices.
	static double[] mul(double[] a, double[] b) {
		double[] r = new double[16];
		for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
			double s = 0;
			for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
			r[i * 4 + j] = s;
		}
		return r;
	}
	static Vec3 apply(double[] m, double x, double y, double z) {
		return new Vec3(m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]);
	}
	static double[] translate(double x, double y, double z) { return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1}; }
	static double[] scale(double x, double y, double z) { return new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1}; }
	static double[] rotX(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1}; }
	static double[] rotY(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1}; }
	static double[] rotZ(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
}
