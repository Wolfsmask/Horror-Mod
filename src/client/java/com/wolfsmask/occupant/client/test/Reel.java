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
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Footage, not photographs: a few seconds of each moment, recorded a frame every tick, the camera
 * moving as a hand-held one would (bobbing in the water, walking, turning round), upright, the
 * shape of a phone screen, for a short video of the mod. Only when asked for (a {@code reel.flag}
 * in the game directory, which CI puts there for a commit marked [reel]); the frames go to
 * {@code occupant-reel/<clip>/0000.png}, and CI makes each clip a video.
 * <p>
 * Everything is the real game and the real mod, only arranged, as with the photographs.
 */
final class Reel {
	private static final Path GAME = FabricLoader.getInstance().getGameDir();
	private static final Path DIR = GAME.resolve("occupant-reel");
	private static final String ALL = "@e[type=occupant:occupant]";

	private Reel() {
	}

	static boolean wanted() {
		return Files.exists(GAME.resolve("reel.flag"));
	}

	/** Where the camera's eye is, and what it looks at, at tick {@code t} of a clip. */
	private interface Move {
		Vec3[] at(int t);
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
				mc.options.fov().set(78);
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

	/**
	 * Dusk, in the water off the shore, the camera held just above the surface and rising and
	 * falling with it. On the bank, between the trees, it is standing, facing you. The camera goes
	 * under for a moment; when it comes up, it is closer.
	 */
	private static void shore(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(-150, 0, -60)));
		if (floor == null) return;
		// A pond, dug where the camera floats, the bank where it stands left as it was.
		double surface = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -8; x <= 8; x++) {
				for (int z = -3; z <= 12; z++) {
					for (int y = -1; y <= 14; y++) {
						BlockPos p = floor.offset(x, y, z);
						boolean edge = Math.abs(x) == 8 || z == 12;
						BlockState s0 = y <= 0 ? (edge ? Blocks.DIRT.defaultBlockState() : Blocks.WATER.defaultBlockState())
								: Blocks.AIR.defaultBlockState();
						set(level, p, s0);
					}
					set(level, floor.offset(x, -2, z), Blocks.MUD.defaultBlockState());
				}
			}
			return floor.getY() + 0.89;
		});
		BlockPos far = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, -8));
		BlockPos near = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, -5));
		Vec3 start = new Vec3(floor.getX() + 0.5, surface, floor.getZ() + 7.5);
		Vec3 head = Vec3.atBottomCenterOf(far).add(0, 3.2, 0);
		prepare(context, game, start.add(0, 0.1, 0), head, 12650);
		Cinematic.place(server, far, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		Move move = t -> {
			double bob = Math.sin(t * 0.21) * 0.09 + Math.sin(t * 0.083) * 0.05;
			// Under for a moment, about three quarters of the way through.
			double dip = t >= 98 && t <= 116 ? -Math.sin((t - 98) / 18.0 * Math.PI) * 0.75 : 0.0;
			Vec3 eye = start.add(Math.sin(t * 0.031) * 0.25, 0.08 + bob + dip, -0.032 * t);
			Vec3 look = (t < 107 ? head : Vec3.atBottomCenterOf(near).add(0, 3.1, 0))
					.add(Math.sin(t * 0.047) * 0.9, Math.sin(t * 0.13) * 0.25 - 0.4, 0);
			return new Vec3[]{eye, look};
		};
		record(context, server, "shore", 150, move, t -> {
			// While the camera is under, it comes closer.
			if (t == 107) Cinematic.place(server, near, move.at(t)[0], Cinematic.Look.STARING);
		});
	}

	/**
	 * Dusk, walking along a path through the trees. Ahead, braced between the trunks, its legs on
	 * them and not on the ground, it is standing in the path, facing you. The camera stops.
	 */
	private static void trees(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(120, 0, 90)));
		if (floor == null) return;
		BlockPos o = floor.above();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor, 16);
			// Level ground, a worn path down the middle.
			for (int x = -7; x <= 7; x++) {
				for (int z = -6; z <= 18; z++) {
					set(level, floor.offset(x, 0, z), Math.abs(x) <= 1 && (x * 7 + z * 3) % 5 != 0
							? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
					for (int y = 1; y <= 11; y++) set(level, floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
			// Trunks either side of the path, crowns meeting overhead.
			int[][] trunks = {{-3, 2}, {3, 4}, {-2, 7}, {2, 9}, {-3, 11}, {3, 13}, {-2, 16}, {2, 17}, {-5, 5}, {5, 10}};
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
		BlockPos it = o.offset(0, 0, 10);
		Vec3 start = Vec3.atBottomCenterOf(o.offset(0, 0, -4)).add(0.2, Cinematic.EYE, 0);
		Vec3 head = Vec3.atBottomCenterOf(it).add(0, 3.0, 0);
		prepare(context, game, start, head.add(-2.5, -0.6, 0), 12900);
		Cinematic.place(server, it, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		Move move = t -> {
			double walk = Math.min(t, 90) * 0.05;
			double step = Math.abs(Math.sin(Math.min(t, 90) * 0.33)) * 0.07;
			Vec3 eye = start.add(Math.sin(t * 0.165) * 0.05, step, walk);
			// Looking about as it walks, at the trunks to one side; only at the last, ahead.
			double turn = smooth((t - 60) / 35.0);
			Vec3 about = head.add(-3.5 + Math.sin(t * 0.06) * 1.5, -0.8, -2);
			Vec3 look = about.scale(1 - turn).add(head.scale(turn)).add(0, Math.sin(t * 0.33) * 0.05, 0);
			return new Vec3[]{eye, look};
		};
		record(context, server, "trees", 130, move, null);
	}

	/**
	 * Night, out in the open, looking at nothing. A breath. The camera turns round: it has bent
	 * right down, its face just above, so close it does not fit.
	 */
	private static void behind(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[0].offset(60, 0, -60)));
		if (floor == null) return;
		server.runOnServer(s -> Cinematic.fellTrees(s.overworld(), floor, 10));
		BlockPos it = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, 0));
		Vec3 eye = server.computeOnServer(s -> {
			BlockPos e = BlockPos.containing(Vec3.atBottomCenterOf(it).add(3.1, 0, 0.6));
			return new Vec3(e.getX() + 0.5, Cinematic.ground(s.overworld(), e.getX(), e.getZ()) + Cinematic.EYE, e.getZ() + 0.5);
		});
		Vec3 face = Vec3.atBottomCenterOf(it).add(0, 3.0, 0);
		Vec3 out = eye.add(eye.subtract(face).multiply(1, 0, 1).normalize().scale(20)).add(0, 0.2, 0);
		prepare(context, game, eye, out, 18000);
		Cinematic.place(server, it, eye, Cinematic.Look.looming(0));
		context.waitTicks(25);
		Move move = t -> {
			double sway = Math.sin(t * 0.07) * 0.04;
			double turn = smooth((t - 52) / 9.0);
			// Round, the long way, as a startled head turns: through the side, not straight back.
			Vec3 from = out.subtract(eye);
			Vec3 to = face.subtract(eye);
			double a0 = Math.atan2(from.z, from.x), a1 = Math.atan2(to.z, to.x);
			double da = Mth.wrapDegrees(Math.toDegrees(a1 - a0));
			double a = a0 + Math.toRadians(da * turn);
			double pitch = Mth.lerp(turn, from.y / from.horizontalDistance(), to.y / to.horizontalDistance());
			Vec3 dir = new Vec3(Math.cos(a), pitch, Math.sin(a));
			Vec3 shake = t > 61 ? new Vec3(Math.sin(t * 2.1) * 0.012, Math.cos(t * 1.7) * 0.012, 0) : Vec3.ZERO;
			return new Vec3[]{eye.add(0, sway * 0.3, 0).add(shake), eye.add(dir.scale(8)).add(0, sway, 0)};
		};
		record(context, server, "behind", 92, move, null);
	}

	/** Dusk, the old graves, a few candles. It is bent over them, towards you. The camera edges along the fence. */
	private static void graves(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(-40, 0, 200)));
		if (floor == null) return;
		BlockPos[] it = new BlockPos[1];
		Vec3 eye = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor, 24);
			House.buildPlaceForTest("graves", level, floor, level.getRandom());
			Cinematic.light(level, floor, 7, Blocks.CANDLE.defaultBlockState());
			it[0] = Cinematic.standNear(level, floor, 1, 7);
			Vec3 middle = Cinematic.rel(floor, 0.5, 2.2, -0.5);
			return Cinematic.findCamera(level, Cinematic.player(s), it[0], -0.2, -1.0, new double[]{7, 9, 11}, Cinematic.EYE, 0, middle);
		});
		Vec3 head = Vec3.atBottomCenterOf(it[0]).add(0, 2.6, 0);
		Vec3 toward = head.subtract(eye);
		Vec3 side = new Vec3(-toward.z, 0, toward.x).normalize();
		prepare(context, game, eye, head, 13700);
		Cinematic.place(server, it[0], eye, Cinematic.Look.glancing(-35f, OccupantEntity.Mode.AMBUSH, 0));
		context.waitTicks(25);
		Move move = t -> {
			Vec3 at = eye.add(side.scale(-1.6 + t * 0.026)).add(0, Math.sin(t * 0.3) * 0.03, 0);
			return new Vec3[]{at, head.add(Math.sin(t * 0.05) * 0.4, -0.3, 0)};
		};
		record(context, server, "graves", 120, move, null);
	}

	/**
	 * Morning, grey. Out of the door: a little way off, freshly dug, a grave, the earth heaped
	 * beside it, a sign at its head. The camera goes up to it and looks down at the sign. Far off
	 * behind it, in the fog, it is standing there.
	 */
	private static void grave(ClientGameTestContext context, TestSingleplayerContext game, BlockPos spawn) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), spawn.offset(160, 0, 40)));
		if (floor == null) return;
		BlockPos o = floor.above();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor, 14);
			// The grave: a body's length, two deep, the earth heaped beside it.
			for (int z = 0; z <= 1; z++) {
				set(level, o.offset(0, 0, z), Blocks.AIR.defaultBlockState());
				set(level, floor.offset(0, 0, z), Blocks.AIR.defaultBlockState());
				set(level, floor.offset(0, -1, z), Blocks.AIR.defaultBlockState());
				set(level, o.offset(1, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
			}
			// The sign at its head, its words towards where the camera comes from.
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
		BlockPos it = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, -22));
		Vec3 start = server.computeOnServer(s -> new Vec3(o.getX() + 0.8, Cinematic.ground(s.overworld(), o.getX(), o.getZ() + 8) + Cinematic.EYE, o.getZ() + 8.5));
		Vec3 sign = Vec3.atBottomCenterOf(o.offset(0, 0, -1)).add(0, 0.7, 0);
		Vec3 far = Vec3.atBottomCenterOf(it).add(0, 2.4, 0);
		prepare(context, game, start, sign, 0);
		Cinematic.place(server, it, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		Move move = t -> {
			// Up to the foot of it, then still, reading; then, at the last, up: past it, far off, there.
			double walk = smooth(t / 80.0) * 7.3;
			double step = t < 80 ? Math.abs(Math.sin(t * 0.33)) * 0.06 : 0.0;
			Vec3 eye = start.add(-0.3 * smooth(t / 80.0), step, -walk);
			double up = smooth((t - 112) / 22.0);
			Vec3 look = sign.add(0, 0.15, 0).scale(1 - up).add(far.scale(up));
			return new Vec3[]{eye, look};
		};
		record(context, server, "grave", 150, move, null);
	}

	/** Time of day, the camera put where the clip begins, the world round it drawn. */
	private static void prepare(ClientGameTestContext context, TestSingleplayerContext game, Vec3 eye, Vec3 look, int time) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand("time set " + time);
		server.runCommand(tp(eye, look));
		context.waitTicks(30);
		TestCompat.waitForWorld(game);
		Cinematic.noBirch(server, eye);
		context.waitTicks(20);
	}

	/** A frame every tick, the camera moved first; {@code cue} for anything that happens at a tick. */
	private static void record(ClientGameTestContext context, TestServerContext server, String name, int frames, Move move,
							   IntConsumer cue) {
		Path dir = DIR.resolve(name);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (int t = 0; t < frames; t++) {
			Vec3[] cam = move.at(t);
			server.runCommand(tp(cam[0], cam[1]));
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

	private static String tp(Vec3 eye, Vec3 look) {
		Vec3 d = look.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - Cinematic.EYE, eye.z, yaw, pitch);
	}

	private static double smooth(double x) {
		double c = Mth.clamp(x, 0.0, 1.0);
		return c * c * (3 - 2 * c);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
