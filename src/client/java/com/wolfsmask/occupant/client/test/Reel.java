package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.ScreenEffects;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.world.House;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Footage, not photographs: a few seconds of each moment, recorded a frame every tick, upright,
 * the shape of a phone screen, for a short video of the mod. The camera is someone frightened:
 * they see it, they freeze, they turn and run, and look back over their shoulder as they go, and
 * every time they look back it is nearer. It is never seen moving.
 * <p>
 * Only when asked for (a {@code reel.flag} in the game directory, which CI puts there for a commit
 * marked [reel]); the frames go to {@code occupant-reel/<clip>/0000.png}, with the ticks the feet
 * land on in {@code steps.txt} for the sound, and CI makes each clip a video.
 * <p>
 * Everything is the real game and the real mod, only arranged, as with the photographs.
 */
final class Reel {
	private static final Path GAME = FabricLoader.getInstance().getGameDir();
	private static final Path DIR = GAME.resolve("occupant-reel");
	private static final String ALL = "@e[type=occupant:occupant]";
	/** Held as it is put, in the pose it has when it is after you. */
	private static final Cinematic.Look CHASING = new Cinematic.Look(false, 0.01f, OccupantEntity.Mode.CHASE, 0);
	/** A sprint, in blocks a tick. */
	private static final double SPRINT = 0.27;

	private Reel() {
	}

	static boolean wanted() {
		return Files.exists(GAME.resolve("reel.flag"));
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods, BlockPos spawn) {
		TestServerContext server = game.getServer();
		try {
			server.runCommand("gamemode spectator @p");
			server.runCommand("weather clear");
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = true);
			context.runOnClient(mc -> {
				com.wolfsmask.occupant.client.ClientScares.breath = false;
				ScreenEffects.trigger(new ScreenEffectPayload(ScreenEffectPayload.ACT, 0, 3), mc);
				com.wolfsmask.occupant.client.ClientFog.set(64.0f, 1);
				mc.options.fov().set(80);
			});
			Cinematic.hud(context, false);
			// Upright, the shape of a phone.
			context.getInput().resizeWindow(1080, 1920);
			context.waitTicks(20);

			clip("shore", () -> shore(context, game, woods));
			clip("trees", () -> trees(context, game, woods));
			clip("behind", () -> behind(context, game, woods));
			clip("graves", () -> graves(context, game, woods));
			clip("grave", () -> grave(context, game, spawn));
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] reel stopped early", e);
		} finally {
			server.runCommand("kill " + ALL);
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = false);
			context.runOnClient(mc -> com.wolfsmask.occupant.client.ClientScares.breath = true);
			Cinematic.hud(context, true);
			server.runCommand("gamemode survival @p");
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void clip(String name, Runnable take) {
		try {
			take.run();
			Occupant.LOGGER.info("[client-gametest] reel: {} recorded", name);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] reel: {} not recorded", name, e);
		}
	}

	// ------------------------------------------------------------------ the clips

	/**
	 * Dusk, in the water off the shore. On the bank, between the trees, it is standing, facing
	 * you. You turn and swim for the far side, ducking under with every stroke; over your shoulder,
	 * it is at the water's edge now. You go under.
	 */
	private static void shore(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(-150, 0, -60)));
		if (floor == null) return;
		// A long pond, dug where you swim; the bank where it stands left as it was.
		double surface = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -9; x <= 9; x++) {
				for (int z = -3; z <= 30; z++) {
					for (int y = -1; y <= 14; y++) {
						boolean edge = Math.abs(x) == 9 || z == 30;
						set(level, floor.offset(x, y, z), y <= 0 ? (edge ? Blocks.DIRT.defaultBlockState() : Blocks.WATER.defaultBlockState())
								: Blocks.AIR.defaultBlockState());
					}
					set(level, floor.offset(x, -2, z), Blocks.MUD.defaultBlockState());
				}
			}
			return floor.getY() + 0.89;
		});
		// The trees behind where it stands felled, so it is a shape against the fog, not the dark.
		server.runOnServer(s -> Cinematic.fellTrees(s.overworld(), floor.offset(0, 0, -13), 8));
		BlockPos far = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, -6));
		BlockPos edge = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 1, -4));
		Vec3 start = new Vec3(floor.getX() + 0.5, surface + 0.1, floor.getZ() + 6.5);
		Vec3 farHead = head(far, 3.2), edgeHead = head(edge, 3.0);
		Fear f = new Fear(start).face(farHead);
		f.stare(44, farHead, 0.5);
		f.swing(10, 2.0, 6, 0.05);                 // round, away, and go
		f.run(54, 0.10, 0.25);
		int closer = f.size() - 30;                 // it moves while your back is turned
		f.lookBack(8, 13, 8, edgeHead);
		f.run(10, 0.10, 0.0);
		f.dive(14);
		prepare(context, game, start, farHead, 12650);
		Cinematic.place(server, far, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		record(context, server, "shore", f.water(surface), f.steps(), t -> {
			if (t == closer) Cinematic.place(server, edge, start, CHASING);
		});
	}

	/**
	 * Dusk, walking along a path through the trees. Ahead, braced across the path between the
	 * trunks, it is standing, facing you. You freeze; you turn and run back the way you came, and
	 * when you look over your shoulder it is on the path behind you, where you were standing.
	 */
	private static void trees(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(120, 0, 90)));
		if (floor == null) return;
		BlockPos o = floor.above();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor.offset(0, 0, -8), 26);
			// Level ground, a worn path down the middle, long enough to run.
			for (int x = -7; x <= 7; x++) {
				for (int z = -40; z <= 18; z++) {
					set(level, floor.offset(x, 0, z), Math.abs(x) <= 1 && (x * 7 + z * 3) % 5 != 0
							? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
					for (int y = 1; y <= 11; y++) set(level, floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
			// Trunks either side of the path all the way, crowns meeting overhead.
			List<int[]> trunks = new ArrayList<>();
			for (int z = -38; z <= 17; z += 3) {
				trunks.add(new int[]{(z / 3) % 2 == 0 ? -3 : 3, z});
				if (z % 9 == 0) trunks.add(new int[]{(z / 3) % 2 == 0 ? 5 : -5, z + 1});
			}
			trunks.add(new int[]{-2, 9});
			trunks.add(new int[]{2, 11});
			for (int[] t : trunks) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int y = 6; y <= 8; y++) {
							if (Math.abs(dx) + Math.abs(dz) + (y == 8 ? 1 : 0) <= 3) {
								set(level, o.offset(t[0] + dx, y, t[1] + dz), Blocks.DARK_OAK_LEAVES.defaultBlockState()
										.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
							}
						}
					}
				}
			}
			for (int[] t : trunks) {
				for (int y = 0; y <= 7; y++) set(level, o.offset(t[0], y, t[1]), Blocks.DARK_OAK_LOG.defaultBlockState());
			}
		});
		BlockPos ahead = o.offset(0, 0, 10);
		BlockPos behind = o.offset(0, 0, -2);
		Vec3 start = Vec3.atBottomCenterOf(o.offset(0, 0, -4)).add(0.2, Cinematic.EYE, 0);
		Vec3 aheadHead = head(ahead, 3.0), behindHead = head(behind, 2.6);
		IntFunction<Vec3> about = t -> aheadHead.add(-4.0 + Math.sin(t * 0.07) * 2.0, -0.8, -3.0 + t * 0.05);
		Fear f = new Fear(start).face(about.apply(0));
		f.heading = 0;
		// Walking, looking about at the trunks; then it is there.
		f.walk(42, 0.045, about);
		f.stare(12, aheadHead, 0.2);
		f.stare(12, aheadHead, 1.2);                 // frozen
		f.swing(9, 180.0, 5, 0.12);                  // round, and run
		int follow = f.size() + 4;
		f.run(30, SPRINT, 0.0);
		f.lookBack(7, 10, 7, behindHead);
		f.run(12, SPRINT, 0.2);
		f.stumble(8);
		f.run(12, 0.22, 0.0);
		prepare(context, game, start, aheadHead, 12900);
		Cinematic.place(server, ahead, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		record(context, server, "trees", f.ground(server), f.steps(), t -> {
			if (t == follow) Cinematic.place(server, behind, start, CHASING);
		});
	}

	/**
	 * Night, out in the open, looking at nothing. You turn round: it has bent right down to you,
	 * its face just above. You stumble back, turn, and run into the dark; over your shoulder it is
	 * still standing there, bent, watching you go.
	 */
	private static void behind(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[0].offset(60, 0, -60)));
		if (floor == null) return;
		server.runOnServer(s -> Cinematic.fellTrees(s.overworld(), floor, 18));
		BlockPos it = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, 0));
		Vec3 eye = server.computeOnServer(s -> {
			BlockPos e = BlockPos.containing(Vec3.atBottomCenterOf(it).add(3.1, 0, 0.6));
			return new Vec3(e.getX() + 0.5, Cinematic.ground(s.overworld(), e.getX(), e.getZ()) + Cinematic.EYE, e.getZ() + 0.5);
		});
		Vec3 face = head(it, 3.0);
		Vec3 out = eye.add(eye.subtract(face).multiply(1, 0, 1).normalize().scale(20)).add(0, 0.2, 0);
		Fear f = new Fear(eye);
		f.yaw = Fear.yawBetween(eye, out);
		f.heading = f.yaw;
		f.stare(26, out, 0.3);
		f.swingTo(9, face);                          // round
		f.stare(16, face, 1.4);                      // frozen
		f.backAway(7, face, 0.13);
		f.swing(8, Fear.yawBetween(eye, out), 6, 0.14);
		f.run(22, SPRINT, -0.3);
		f.lookBack(6, 9, 6, head(it, 2.4));
		f.run(10, SPRINT, 0.0);
		prepare(context, game, eye, out, 18000);
		Cinematic.place(server, it, eye, Cinematic.Look.looming(0));
		context.waitTicks(25);
		record(context, server, "behind", f.ground(server), f.steps(), null);
	}

	/**
	 * Dusk, the old graves, a few candles. It is bent over them, towards you. You back away; you
	 * turn and run; when you look back it is standing where you were.
	 */
	private static void graves(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(-40, 0, 200)));
		if (floor == null) return;
		BlockPos[] it = new BlockPos[2];
		Vec3 eye = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor, 34);
			House.buildPlaceForTest("graves", level, floor, level.getRandom());
			Cinematic.light(level, floor, 7, Blocks.CANDLE.defaultBlockState());
			it[0] = Cinematic.standNear(level, floor, 1, 7);
			Vec3 middle = Cinematic.rel(floor, 0.5, 2.2, -0.5);
			return Cinematic.findCamera(level, Cinematic.player(s), it[0], -0.2, -1.0, new double[]{8, 10, 12}, Cinematic.EYE, 0, middle);
		});
		// Where you were standing, for it to be standing there when you look back.
		it[1] = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), BlockPos.containing(eye).below(2), 0, 0));
		Vec3 head = head(it[0], 2.6), where = head(it[1], 2.7);
		double away = Fear.yawBetween(head, eye);
		Fear f = new Fear(eye).face(head);
		f.heading = f.yaw;
		f.stare(20, head, 0.7);
		f.backAway(18, head, 0.035);
		f.swing(9, away, 6, 0.13);
		int follow = f.size() + 6;
		f.run(26, SPRINT, 0.4);
		f.lookBack(7, 11, 7, where);
		f.run(12, SPRINT, 0.0);
		f.stumble(7);
		clearPath(server, f);
		prepare(context, game, eye, head, 13700);
		Cinematic.place(server, it[0], eye, Cinematic.Look.glancing(-35f, OccupantEntity.Mode.AMBUSH, 0));
		context.waitTicks(25);
		Vec3 runner = f.at(follow + 26);
		record(context, server, "graves", f.ground(server), f.steps(), t -> {
			if (t == follow) Cinematic.place(server, it[1], runner, CHASING);
		});
	}

	/**
	 * Morning, grey. Out of the door: a fresh grave, the earth heaped beside it, a sign at its head
	 * with your name on it. You look up from it: far off in the fog, it is standing there. You run;
	 * over your shoulder, it is at the grave.
	 */
	private static void grave(ClientGameTestContext context, TestSingleplayerContext game, BlockPos spawn) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), spawn.offset(160, 0, 40)));
		if (floor == null) return;
		BlockPos o = floor.above();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor.offset(0, 0, 8), 24);
			// The grave: a body's length, two deep, the earth heaped beside it.
			for (int z = 0; z <= 1; z++) {
				set(level, o.offset(0, 0, z), Blocks.AIR.defaultBlockState());
				set(level, floor.offset(0, 0, z), Blocks.AIR.defaultBlockState());
				set(level, floor.offset(0, -1, z), Blocks.AIR.defaultBlockState());
				set(level, o.offset(1, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
			}
			// The sign at its head, its words towards where you come from.
			BlockPos at = o.offset(0, 0, -1);
			BlockState sign = Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(0.0f));
			set(level, at, sign);
			if (level.getBlockEntity(at) instanceof SignBlockEntity entity) {
				Compat.writeSign(entity, new String[]{"Here lies", "YOU", "", "Day 12"}, true);
				entity.setWaxed(true);
				entity.setChanged();
				level.sendBlockUpdated(at, sign, sign, Block.UPDATE_ALL);
			}
		});
		BlockPos far = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, -22));
		BlockPos atGrave = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, -2, -2));
		Vec3 start = server.computeOnServer(s -> new Vec3(o.getX() + 0.8, Cinematic.ground(s.overworld(), o.getX(), o.getZ() + 9) + Cinematic.EYE, o.getZ() + 9.5));
		Vec3 sign = Vec3.atBottomCenterOf(o.offset(0, 0, -1)).add(0, 0.85, 0);
		Vec3 farHead = head(far, 2.6), graveHead = head(atGrave, 2.8);
		Fear f = new Fear(start).face(sign);
		f.heading = 180;
		f.walk(48, 0.13, t -> sign);
		f.stare(24, sign, 0.2);                      // reading it
		f.swingTo(14, farHead);                      // up, past it
		f.stare(12, farHead, 1.2);
		f.swing(8, 0.0, 6, 0.14);                    // round, and run
		int follow = f.size() + 6;
		f.run(20, SPRINT, -0.3);
		f.lookBack(7, 11, 7, graveHead);
		f.run(10, SPRINT, 0.0);
		prepare(context, game, start, sign, 0);
		Cinematic.place(server, far, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		record(context, server, "grave", f.ground(server), f.steps(), t -> {
			if (t == follow) Cinematic.place(server, atGrave, start, CHASING);
		});
	}

	// ------------------------------------------------------------------ a frightened person

	/**
	 * How a frightened person carries a camera, a tick at a time: where they are, which way they
	 * are going and which way they look, how fast, and how hard their running shakes it. Turns are
	 * a head's (fast, a little past and back); a run has a stride, three steps a second, the eye
	 * rising and dropping with each, leaning into it; standing, they breathe and their hands shake.
	 */
	static final class Fear {
		/** x, z, yaw, pitch, lift (the eye dropped or raised), speed, tremble, stride phase. */
		private final List<double[]> poses = new ArrayList<>();
		private final List<Integer> steps = new ArrayList<>();
		double x, z, heading, yaw, pitch, speed, phase, lift, tremble;
		private final double eyeY;

		Fear(Vec3 eye) {
			this.x = eye.x;
			this.z = eye.z;
			this.eyeY = eye.y;
		}

		int size() {
			return poses.size();
		}

		/** Looking straight at {@code at} from the first frame. */
		Fear face(Vec3 at) {
			yaw = yawTo(at);
			pitch = pitchTo(at);
			return this;
		}

		/** Where they are at tick {@code i} (at the height they started at). */
		Vec3 at(int i) {
			double[] p = poses.get(Math.min(i, poses.size() - 1));
			return new Vec3(p[0], eyeY, p[1]);
		}

		static double yawBetween(Vec3 from, Vec3 to) {
			return Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
		}

		private double yawTo(Vec3 p) {
			return Math.toDegrees(Math.atan2(-(p.x - x), p.z - z));
		}

		private double pitchTo(Vec3 p) {
			return -Math.toDegrees(Math.atan2(p.y - eyeY, Math.hypot(p.x - x, p.z - z)));
		}

		private static double toward(double from, double to, double rate) {
			return from + Mth.wrapDegrees(to - from) * rate;
		}

		/** One tick on: speed eased towards {@code to}, a step taken whenever a foot comes down. */
		private void step(double to, double accel) {
			speed += Mth.clamp(to - speed, -accel, accel);
			double r = Math.toRadians(heading);
			x += -Math.sin(r) * speed;
			z += Math.cos(r) * speed;
			double before = phase;
			// A stride (two steps) is about three and a half blocks at a sprint.
			phase += speed * Math.PI * 2 / 3.6;
			if (speed > 0.03 && Math.floor(before / Math.PI) != Math.floor(phase / Math.PI)) steps.add(poses.size());
		}

		private void push() {
			poses.add(new double[]{x, z, yaw, pitch, lift, speed, tremble, phase});
		}

		/** Stood (whatever way they were going slowing to nothing), looking at {@code at}, shaking as much as {@code shake}. */
		void stare(int n, Vec3 at, double shake) {
			for (int i = 0; i < n; i++) {
				step(0, 0.03);
				yaw = toward(yaw, yawTo(at), 0.3);
				pitch += (pitchTo(at) - pitch) * 0.3;
				tremble += (shake - tremble) * 0.2;
				lift *= 0.85;
				push();
			}
		}

		/** Walking on as they were going, looking about. */
		void walk(int n, double pace, IntFunction<Vec3> look) {
			for (int i = 0; i < n; i++) {
				step(pace, 0.02);
				Vec3 at = look.apply(i);
				yaw = toward(yaw, yawTo(at), 0.18);
				pitch += (pitchTo(at) - pitch) * 0.18;
				tremble *= 0.9;
				push();
			}
		}

		/** The head whipped round to {@code at}, as fast as a startled one turns, a little past and back. */
		void swingTo(int n, Vec3 at) {
			double y0 = yaw, p0 = pitch;
			for (int i = 1; i <= n; i++) {
				double u = overshoot((double) i / n);
				step(0, 0.03);
				yaw = y0 + Mth.wrapDegrees(yawTo(at) - y0) * u;
				pitch = p0 + (pitchTo(at) - p0) * u;
				push();
			}
		}

		/** Round to face {@code to} (a heading) and away: the body turns with the head, and they are moving before it is done. */
		void swing(int n, double to, double lean, double pace) {
			double y0 = yaw, p0 = pitch;
			double d = Mth.wrapDegrees(to - y0);
			for (int i = 1; i <= n; i++) {
				double u = overshoot((double) i / n);
				heading = y0 + d * Math.min(1.0, u);
				step(pace * i / n, 0.05);
				yaw = y0 + d * u;
				pitch = p0 + (lean - p0) * u;
				tremble *= 0.9;
				push();
			}
			heading = to;
		}

		/** Running, bending a little as they go ({@code curve} degrees a tick), leaning into it. */
		void run(int n, double pace, double curve) {
			for (int i = 0; i < n; i++) {
				heading += curve;
				step(pace, 0.05);
				yaw = toward(yaw, heading, 0.45);
				pitch += (5.0 + speed * 10 - pitch) * 0.3;
				lift *= 0.8;
				tremble *= 0.9;
				push();
			}
		}

		/** Over the shoulder while still running: round to {@code at}, held there, and back again. */
		void lookBack(int turn, int hold, int back, Vec3 at) {
			int total = turn + hold + back;
			for (int i = 0; i < total; i++) {
				step(SPRINT * 0.8, 0.04);
				double u = i < turn ? overshoot((i + 1.0) / turn) : i < turn + hold ? 1.0 : 1.0 - smooth((i - turn - hold + 1.0) / back);
				double ahead = heading, there = yawTo(at);
				yaw = ahead + Mth.wrapDegrees(there - ahead) * u;
				pitch = (5.0 + speed * 10) * (1 - u) + pitchTo(at) * u;
				tremble += ((i >= turn && i < turn + hold ? 1.3 : 0.4) - tremble) * 0.3;
				push();
			}
		}

		/** A foot caught: down towards the ground, nearly falling, slowed right down. */
		void stumble(int n) {
			for (int i = 0; i < n; i++) {
				step(0.05, 0.09);
				pitch += (40 - pitch) * 0.4;
				lift += (-0.6 - lift) * 0.4;
				tremble += (1.6 - tremble) * 0.4;
				push();
			}
		}

		/** Backing away from {@code at}, facing it. */
		void backAway(int n, Vec3 at, double pace) {
			for (int i = 0; i < n; i++) {
				heading = yawTo(at) + 180.0;
				step(pace, 0.03);
				yaw = toward(yaw, yawTo(at), 0.4);
				pitch += (pitchTo(at) - pitch) * 0.4;
				tremble += (1.2 - tremble) * 0.3;
				push();
			}
		}

		/** Under, all the way, and stay there. */
		void dive(int n) {
			for (int i = 0; i < n; i++) {
				step(0.04, 0.03);
				lift += (-1.1 - lift) * 0.25;
				pitch += (25 - pitch) * 0.2;
				tremble += (1.0 - tremble) * 0.3;
				push();
			}
		}

		List<Integer> steps() {
			return steps;
		}

		/** The poses on the ground, the eye at a person's height over it, rising and falling with each step. */
		double[][] ground(TestServerContext server) {
			int n = poses.size();
			double[] xs = new double[n], zs = new double[n];
			for (int i = 0; i < n; i++) {
				xs[i] = poses.get(i)[0];
				zs[i] = poses.get(i)[1];
			}
			int[] g = server.computeOnServer(s -> {
				int[] out = new int[n];
				for (int i = 0; i < n; i++) out[i] = Cinematic.ground(s.overworld(), Mth.floor(xs[i]), Mth.floor(zs[i]));
				return out;
			});
			double[][] out = new double[n][];
			double y = g.length > 0 ? g[0] : 0;
			for (int i = 0; i < n; i++) {
				// Up a step quickly, down one a little slower, as legs do.
				y += (g[i] - y) * (g[i] > y ? 0.5 : 0.3);
				out[i] = finish(i, y + Cinematic.EYE, false);
			}
			return out;
		}

		/** The poses in the water: the eye at the surface, ducking under with each stroke. */
		double[][] water(double surface) {
			int n = poses.size();
			double[][] out = new double[n][];
			for (int i = 0; i < n; i++) out[i] = finish(i, surface, true);
			return out;
		}

		/** x, y, z, yaw, pitch for tick {@code i}: the stride and the shaking hands put in. */
		private double[] finish(int i, double base, boolean swimming) {
			double[] p = poses.get(i);
			double px = p[0], pz = p[1], yw = p[2], pt = p[3], lf = p[4], sp = p[5], tr = p[6], ph = p[7];
			double run = Mth.clamp(sp / SPRINT, 0, 1);
			double bob, sway;
			if (swimming) {
				// Treading water, and then the strokes: under a little with every one.
				bob = 0.07 * Math.sin(i * 0.21) + (sp > 0.03 ? 0.13 * Math.sin(ph) : 0.0);
				sway = 0.05 * Math.sin(ph * 0.5);
			} else {
				bob = (0.03 + 0.11 * run) * (Math.abs(Math.sin(ph)) - 0.55) * (sp > 0.01 ? 1 : 0);
				sway = (0.02 + 0.06 * run) * Math.sin(ph) * (sp > 0.01 ? 1 : 0);
			}
			// Hands that will not keep still: slow drift, faster shaking, both worse with fear.
			double drift = 0.6 * Math.sin(i * 0.29 + 0.3) + 0.4 * Math.sin(i * 0.71 + 1.1) + 0.25 * Math.sin(i * 1.93 + 2.0);
			double shake = Math.sin(i * 2.7) * 0.5 + Math.sin(i * 3.9 + 1.0) * 0.4;
			double hands = 0.7 + 1.6 * run;
			double yawOut = yw + drift * hands * 0.9 + shake * tr * 0.9 + 2.4 * run * Math.sin(ph);
			// Each footfall jolts the view down a touch.
			double fall = Math.pow(1 - Math.abs(Math.sin(ph)), 6) * 3.0 * run;
			double pitchOut = Mth.clamp(pt + drift * hands * 0.5 + shake * tr * 0.7 + fall, -80, 85);
			double r = Math.toRadians(yw);
			double sx = Math.cos(r) * sway, sz = Math.sin(r) * sway;
			return new double[]{px + sx, base + bob + lf, pz + sz, yawOut, pitchOut};
		}

		private static double overshoot(double u) {
			double c = Mth.clamp(u, 0, 1);
			return smooth(c) + Math.sin(c * Math.PI) * 0.09 * c;
		}
	}

	// ------------------------------------------------------------------ recording

	private static Vec3 head(BlockPos feet, double up) {
		return Vec3.atBottomCenterOf(feet).add(0, up, 0);
	}

	/** Time of day, the camera put where the clip begins, the world round it drawn. */
	private static void prepare(ClientGameTestContext context, TestSingleplayerContext game, Vec3 eye, Vec3 look, int time) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand("time set " + time);
		Vec3 d = look.subtract(eye);
		server.runCommand(tp(eye, Math.toDegrees(Math.atan2(-d.x, d.z)), -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))));
		context.waitTicks(30);
		TestCompat.waitForWorld(game);
		Cinematic.noBirch(server, eye);
		context.waitTicks(20);
	}

	/** A frame every tick, the camera moved first; {@code cue} for anything that happens at a tick. */
	private static void record(ClientGameTestContext context, TestServerContext server, String name, double[][] poses,
							   List<Integer> steps, IntConsumer cue) {
		Path dir = DIR.resolve(name);
		try {
			Files.createDirectories(dir);
			StringBuilder sb = new StringBuilder();
			for (int s : steps) sb.append(s).append('\n');
			Files.writeString(dir.resolve("steps.txt"), sb.toString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (int t = 0; t < poses.length; t++) {
			double[] p = poses[t];
			server.runCommand(tp(new Vec3(p[0], p[1], p[2]), p[3], p[4]));
			if (cue != null) cue.accept(t);
			context.waitTick();
			Path shot = context.takeScreenshot("reel-" + name + "-" + t);
			try {
				Files.move(shot, dir.resolve(String.format(Locale.ROOT, "%04d.png", t)), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private static String tp(Vec3 eye, double yaw, double pitch) {
		return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - Cinematic.EYE, eye.z,
				(float) Mth.wrapDegrees(yaw), (float) pitch);
	}

	private static double smooth(double x) {
		double c = Mth.clamp(x, 0.0, 1.0);
		return c * c * (3 - 2 * c);
	}

	/**
	 * Nothing in the way where they run: a person goes round a candle, a headstone or a fence, not
	 * through it, so whatever stands within a step of their path is taken away first.
	 */
	private static void clearPath(TestServerContext server, Fear f) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int i = 0; i < f.size(); i++) {
				Vec3 at = f.at(i);
				int feet = Mth.floor(at.y - Cinematic.EYE);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dy = -3; dy <= 3; dy++) {
							BlockPos pos = new BlockPos(Mth.floor(at.x) + dx, feet + dy, Mth.floor(at.z) + dz);
							Block block = level.getBlockState(pos).getBlock();
							if (block instanceof CandleBlock || block instanceof WallBlock || block instanceof FenceBlock
									|| block instanceof SignBlock || block instanceof ChestBlock) {
								set(level, pos, Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
			}
		});
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
