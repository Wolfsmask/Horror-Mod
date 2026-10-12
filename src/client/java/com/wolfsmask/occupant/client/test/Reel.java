package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.ScreenEffects;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Kinds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
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
import java.util.function.IntToDoubleFunction;

/**
 * Footage, not photographs: a few seconds of each moment, recorded a frame every tick, upright,
 * the shape of a phone screen, for a short video of the mod. The camera is someone frightened, in
 * a dark wood, and it is one night: they see it between the trees; they run, and find a house;
 * it is at the window; they run again, out into the trees, and it is behind them, and then it is
 * in front of them, round the corner, and it has them. It is never seen moving until then.
 * <p>
 * Only when asked for (a {@code reel.flag} in the game directory, which CI puts there for a commit
 * marked [reel]); the frames go to {@code occupant-reel/<clip>/0000.png}, with what is heard when
 * in {@code marks.txt} ("tick kind" a line: a foot coming down, a knock, it seen) and how far the
 * camera is tipped each frame in {@code roll.txt}, for the edit; CI makes each clip a video.
 * <p>
 * Everything is the real game and the real mod, only arranged, as with the photographs.
 */
final class Reel {
	private static final Path GAME = FabricLoader.getInstance().getGameDir();
	private static final Path DIR = GAME.resolve("occupant-reel");
	private static final String ALL = "@e[type=occupant:occupant]";
	/** What its leg goes through: nothing anyone can see, where the one on the ground is. */
	private static final String BODY = "@e[type=armor_stand,tag=occupant_reel]";
	/** Held as it is put, in the pose it has when it is after you. */
	private static final Cinematic.Look CHASING = new Cinematic.Look(false, 0.01f, OccupantEntity.Mode.CHASE, 0);
	/** Held as it is put, bent right down to you, the mouth open. */
	private static final Cinematic.Look LOOMING = new Cinematic.Look(false, 0.01f, OccupantEntity.Mode.AMBUSH, 0);
	/** A sprint, in blocks a tick. */
	private static final double SPRINT = 0.27;
	/** Their body, on the ground under the camera once it has them. */
	private static final String SELF = "@e[type=mannequin,tag=occupant_self]";
	/** How far the fog lets you see in the wood. */
	private static final float FOG = 38.0f;

	private Reel() {
	}

	static boolean wanted() {
		return Files.exists(GAME.resolve("reel.flag"));
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods, BlockPos spawn) {
		TestServerContext server = game.getServer();
		double gamma = context.computeOnClient(mc -> mc.options.gamma().get());
		try {
			server.runCommand("gamemode spectator @p");
			server.runCommand("weather clear");
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = true);
			context.runOnClient(mc -> {
				com.wolfsmask.occupant.client.ClientScares.breath = false;
				ScreenEffects.trigger(new ScreenEffectPayload(ScreenEffectPayload.ACT, 0, 3), mc);
				com.wolfsmask.occupant.client.ClientFog.set(FOG, 1);
				mc.options.fov().set(80);
				// The night as a camera sees it, which the grading then takes down again.
				mc.options.gamma().set(1.0);
			});
			Cinematic.hud(context, false);
			// Upright, the shape of a phone.
			context.getInput().resizeWindow(1080, 1920);
			context.waitTicks(20);

			clip("woods", () -> woods(context, game, woods));
			// The wood with the house in it, built once: the same house all night.
			BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[0].offset(60, 0, -60)));
			if (floor != null) {
				Cabin cabin = Cabin.build(server, floor.above());
				clip("calib", () -> calib(context, game, cabin));
				clip("house", () -> house(context, game, cabin));
				clip("window", () -> window(context, game, cabin));
				clip("taken", () -> taken(context, game, cabin));
			}
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] reel stopped early", e);
		} finally {
			server.runCommand("kill " + ALL);
			server.runCommand("kill " + BODY);
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = false);
			context.runOnClient(mc -> {
				com.wolfsmask.occupant.client.ClientScares.breath = true;
				mc.options.gamma().set(gamma);
			});
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
	 * Dusk, deep in a dark wood, walking, looking about. Off to one side, between the trunks, it is
	 * standing, facing you. You freeze; you back off, turn, and run back the way you came; over
	 * your shoulder, there is nothing there any more.
	 */
	private static void woods(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(120, 0, 90)));
		if (floor == null) return;
		BlockPos o = floor.above();
		Way in = Way.local(o, new double[][]{{0, -3}, {0.4, 4}, {-0.4, 10}, {0.3, 16}, {0, 23}});
		Way back = Way.local(o, new double[][]{{0, 6}, {0, -2}, {1.5, -7}, {-1.5, -12}, {1, -17}, {-1, -22}, {0, -30}});
		// Where it stands: a gap in the trees, nothing over it, so its head is against what is left of the sky.
		BlockPos spot = o.offset(-4, 0, 8);
		server.runOnServer(s -> wood(s.overworld(), o, -16, 16, -46, 26, List.of(in, back), 41L, new double[][]{{-4, 8, 3.6}}, null));
		Vec3 start = local(o, 0, Cinematic.EYE, -3);
		Vec3 face = head(spot, 4.4);
		Fear f = new Fear(start);
		f.follow(in);
		// Walking, looking about: off to the right, ahead; then, off to the left, it is there.
		Vec3 right = local(o, 5, 1.9, 6), ahead = local(o, 0.5, 1.5, 12);
		f.face(right);
		f.walk(18, 0.075, t -> right);
		f.walk(12, 0.075, t -> ahead);
		f.swingTo(6, face);                          // the head snapped round to it
		f.mark("seen");
		f.stare(24, face, 1.3);                      // frozen
		f.backAway(10, face, 0.08);
		f.swing(9, 180.0, 6, 0.15);                  // round, and run
		int gone = f.size();                          // out of sight: and then simply not there
		f.follow(back.from(f.x, f.z));
		f.run(28, SPRINT, 0);
		f.mark("look");
		f.lookBack(7, 11, 7, face);
		f.run(10, SPRINT, 0);
		prepare(context, game, start, right, 13000);
		Cinematic.place(server, spot, start, Cinematic.Look.STARING);
		context.waitTicks(25);
		record(context, server, "woods", f.flat(o.getY()), f, t -> {
			if (t == gone) server.runCommand("kill " + ALL);
		});
	}

	/**
	 * Dusk, later, running through the wood, and a house in the trees ahead. Over your shoulder it
	 * is on the way behind you. To the door, in, round, and it is shut.
	 */
	private static void house(ClientGameTestContext context, TestSingleplayerContext game, Cabin cabin) {
		TestServerContext server = game.getServer();
		BlockPos o = cabin.o;
		cabin.door(server, cabin.front, true);
		cabin.door(server, cabin.back, false);
		Vec3 start = local(o, 1, Cinematic.EYE, -17);
		Fear f = new Fear(start);
		f.follow(cabin.approach.from(start.x, start.z));
		f.face(local(o, 0, 2.2, -1));
		f.speed = 0.18;
		f.run(20, SPRINT, 0);
		int look = f.size();
		// On the way behind, where they were a few seconds ago.
		double[] b = cabin.approach.point(cabin.approach.along(f.at(look)) - 6.5);
		BlockPos behind = BlockPos.containing(b[0], o.getY(), b[1]);
		f.lookBack(7, 12, 7, head(behind, 2.8));
		f.markAt(look + 6, "seen");
		f.runTo(1.2, SPRINT);
		Vec3 doorway = local(o, 0, 1.4, -1);
		f.stopAndTurn(10, doorway);                  // in, and round to the door
		int shut = f.size();
		f.mark("door");
		f.backAway(12, doorway, 0.05);
		f.mark("pant");
		f.stare(16, doorway, 1.0);
		prepare(context, game, start, local(o, 0, 2.2, -1), 13050);
		context.waitTicks(25);
		record(context, server, "house", f.flat(o.getY()), f, t -> {
			if (t == look) Cinematic.place(server, behind, f.at(look + 9), CHASING);
			if (t == shut) cabin.door(server, cabin.front, false);
		});
	}

	/**
	 * Night, in the house, by the lamp. You go to the window and look out: the trees, nothing. A
	 * knock at the door. You look round at it; when you look back at the window, its face is at
	 * the glass.
	 */
	private static void window(ClientGameTestContext context, TestSingleplayerContext game, Cabin cabin) {
		TestServerContext server = game.getServer();
		BlockPos o = cabin.o;
		cabin.door(server, cabin.front, false);
		cabin.door(server, cabin.back, false);
		Vec3 start = local(o, 1.6, Cinematic.EYE, 4.6);
		Vec3 door = local(o, 0, 1.2, -1);
		Vec3 glass = local(o, -4, 2.4, 3);
		Vec3 trees = local(o, -12, 1.2, 3);
		// Far enough out that, stooping under the lean-to, its face comes to the glass and not into the wall.
		BlockPos outside = o.offset(-8, 0, 3);
		Fear f = new Fear(start).face(door);
		f.stare(18, door, 0.6);
		f.turnTo(16, glass);
		f.heading = Fear.yawBetween(start, glass);
		f.walk(34, 0.08, t -> glass);
		// Looking out: the dark, the trees, one way and the other.
		f.walk(22, 0.0, t -> trees.add(0, Math.sin(t * 0.12) * 0.3, Math.sin(t * 0.17) * 5.0));
		f.mark("knock");
		f.stare(6, trees, 0.9);
		f.swingTo(10, door);
		int there = f.size() + 3;                     // the window out of sight behind them
		f.stare(22, door, 1.1);
		f.turnTo(13, glass);
		f.markAt(f.size() - 4, "seen");
		f.backAway(9, glass, 0.15);
		f.stare(22, glass, 1.8);
		prepare(context, game, start, door, 15500);
		context.waitTicks(25);
		record(context, server, "window", f.flat(o.getY()), f, t -> {
			if (t == there) Cinematic.place(server, outside, glass.add(3.0, -0.4, 0), Cinematic.Look.looming(0));
		});
	}

	/**
	 * Night: out of the back of the house and into the trees, running. Over your shoulder, it is
	 * there, behind you. You turn back and run on, round the rock, and it is standing there in
	 * front of you instead. It strikes: you are down, its leg through you, and it drags you off into
	 * the trees.
	 */
	private static void taken(ClientGameTestContext context, TestSingleplayerContext game, Cabin cabin) {
		TestServerContext server = game.getServer();
		BlockPos o = cabin.o;
		cabin.door(server, cabin.front, false);
		cabin.door(server, cabin.back, true);
		Vec3 start = local(o, 0, Cinematic.EYE, 4.6);
		Way way = cabin.out.from(start.x, start.z);
		Fear f = new Fear(start);
		f.follow(way);
		f.face(local(o, 0, 1.4, 9));
		f.speed = 0.12;
		f.run(40, SPRINT, 0);
		int look = f.size();
		// Behind, by the house they ran from.
		double[] b = way.point(way.along(f.at(look)) - 7.0);
		BlockPos behind = BlockPos.containing(b[0], o.getY(), b[1]);
		f.lookBack(7, 12, 7, head(behind, 2.8));
		f.markAt(look + 6, "seen");
		f.run(8, SPRINT, 0);
		// Round the rock, until it is there in front of them, just past it; a moment to take it in.
		BlockPos spot = o.offset(-4, 0, 27);
		Vec3 feet = Vec3.atBottomCenterOf(spot);
		Vec3 face = feet.add(0, 2.4, 0);
		for (int i = 0; i < 80 && !f.sees(feet, o, Cabin.ROCK); i++) f.run(1, SPRINT, 0);
		int seen = f.size();
		f.mark("seen");
		// Round the corner, and it is there; before they can even stop, it has them.
		f.run(2, SPRINT, 0);
		f.halt(3, face);
		int hit = f.size();
		// The blow knocks the camera out of their hands: it lands in the dirt a few steps off, on its
		// side, still recording, looking back at them. Everything after is seen from there.
		double ground = o.getY();
		Vec3 struck = f.at(hit);
		Vec3 lunge = feet.add(horizontal(struck.subtract(feet)).normalize().scale(0.9));
		Vec3 away = horizontal(struck.subtract(feet)).normalize();
		Vec3 person = new Vec3(struck.x + away.x, ground, struck.z + away.z);
		// It comes round to stand over them on the open side, away from the rock, under the shelf of it.
		Vec3 toIt = new Vec3(-1.2, 0, 2.4).normalize();
		Vec3 stands = person.add(toIt.scale(2.4));
		// They will try to crawl back the way they came; the camera lands off to one side of that,
		// so it sees the whole length of them, not just the top of their head.
		Vec3 flee = horizontal(local(o, 0.3, 0, 21.5).subtract(person)).normalize();
		double side = Math.toRadians(25);
		Vec3 offAngle = new Vec3(flee.x * Math.cos(side) - flee.z * Math.sin(side), 0, flee.x * Math.sin(side) + flee.z * Math.cos(side));
		Vec3 lens = person.add(offAngle.scale(2.5));
		// Looking between where they will crawl to and where it stands over them.
		Vec3 aim = person.add(flee.scale(0.7)).add(stands).scale(0.5).add(0, 0.8, 0);
		f.thrown(8, lens, aim, 0.18, 16.0);
		int land = f.size();
		// What happens, in ticks from the blow: its legs going in; them crawling for the camera;
		// pulled back; dragged off into the dark; it looking at the camera.
		int[][] stabs = {{hit + 14, 2}, {hit + 17, 1}, {hit + 20, 2}, {hit + 23, 1}, {hit + 26, 3}, {hit + 30, 1}};
		int crawl = hit + 32, yank = hit + 59, drag = hit + 68, stop = hit + 108, end = hit + 120;
		for (int st : new int[]{hit + 14, hit + 20, hit + 26}) f.markAt(st, "stab");
		for (int k = 0; k < 3; k++) f.markAt(crawl + 9 * k, "crawl");
		f.markAt(yank, "yank");
		for (int t = drag; t < stop; t += 13) f.markAt(t, "tug");
		f.markAt(stop + 2, "stare");
		f.still(end - land, aim, 16.0);
		f.mark("end");
		double[][] poses = f.flat(ground);
		IntToDoubleFunction crawled = t -> {
			if (t < crawl) return 0;
			double k = Math.min(3.0, (t - crawl) / 9.0);
			int whole = (int) Math.min(2, Math.floor(k));
			return 0.45 * (whole + smooth(Math.min(1.0, (k - whole) / 0.45)));
		};
		IntToDoubleFunction dragged = t -> {
			if (t < drag) return 0;
			int u = Math.min(t, stop) - drag;
			int whole = u / 13;
			double c = (u % 13) / 13.0;
			return 1.1 * (whole + smooth(Math.min(1.0, c / 0.4)));
		};
		// Where they are each tick (their body, face down, head towards the camera).
		IntFunction<Vec3> body = t -> {
			// Writhing under it as its legs go in, twisting against what holds them.
			Vec3 writhe = new Vec3(toIt.z, 0, -toIt.x).scale((t >= hit + 12 && t < crawl ? 0.07 : 0.04) * Math.sin(t * 1.3));
			if (t < yank) return person.add(flee.scale(crawled.applyAsDouble(t))).add(writhe);
			Vec3 near = person.add(flee.scale(1.35));
			Vec3 back = stands.subtract(toIt.scale(1.9));
			if (t < yank + 5) return near.add(back.subtract(near).scale(1 - Math.pow(1 - (t - yank) / 5.0, 2)));
			return back.add(toIt.scale(dragged.applyAsDouble(t))).add(writhe);
		};
		// Where it is each tick: round the corner; lunging; over them; after them; dragging them off.
		IntFunction<Vec3> it = t -> {
			if (t < hit) return feet;
			if (t < hit + 4) return feet.add(lunge.subtract(feet).scale(smooth((t - hit) / 4.0)));
			if (t < hit + 12) return lunge.add(horizontal(stands.subtract(lunge)).scale(smooth((t - hit - 4) / 8.0)));
			Vec3 after = stands;
			if (t < yank) return after.add(flee.scale(0.4 * crawled.applyAsDouble(t)));
			return stands.add(toIt.scale(0.5 * Math.min(1.0, (t - yank) / 5.0) + dragged.applyAsDouble(t)));
		};
		prepare(context, game, start, local(o, 0, 1.4, 9), 14200);
		// Their body, out of sight under the ground until it is needed, lying as it will lie.
		Vec3 hidden = person.add(0, -8, 0);
		server.runCommand(String.format(Locale.ROOT, "summon mannequin %.3f %.3f %.3f {pose:\"swimming\",immovable:1b,NoGravity:1b,"
				+ "Invulnerable:1b,Silent:1b,hide_description:1b,Tags:[\"occupant_self\"]}", hidden.x, hidden.y, hidden.z));
		context.waitTicks(25);
		try {
			record(context, server, "taken", poses, f, t -> {
				if (t == look) Cinematic.place(server, behind, f.at(look + 9), CHASING);
				// Not there until the moment before it is seen: nothing of it showing round the rock.
				if (t == seen - 2) Cinematic.place(server, spot, f.at(hit), LOOMING);
				if (t < seen - 2) return;
				Vec3 at = it.apply(t);
				Vec3 them = body.apply(t);
				Vec3 eye = new Vec3(poses[t][0], poses[t][1], poses[t][2]);
				// Its eyes on them while it has them; at the end, on the camera.
				Vec3 watch = t < hit ? eye : t >= stop ? eye : them.add(0, 0.3, 0);
				float yaw = (float) Fear.yawBetween(at, watch);
				float pitch = (float) -Math.toDegrees(Math.atan2(watch.y - (at.y + 2.4), Math.hypot(watch.x - at.x, watch.z - at.z)));
				// Their head the way they are trying to go; once it has them back, away from it; and
				// jerking about as the legs go in.
				double twitch = t >= hit + 12 && t < crawl ? 14 * Math.sin(t * 1.7) : 0;
				float bodyYaw = (float) (t < yank ? Fear.yawBetween(them, them.add(flee)) + twitch
						: Fear.yawBetween(at, them) + 6 * Math.sin(t * 0.8));
				if (t >= hit + 2) {
					server.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f 0", SELF, them.x, them.y, them.z, bodyYaw));
				}
				int legs = 0;
				if (t >= hit + 3) {
					legs = 1;
					for (int[] st : stabs) if (t >= st[0]) legs = st[1];
					if (t >= yank && t < yank + 8) legs = 2;
				}
				int count = legs;
				server.runOnServer(s -> {
					ServerLevel level = s.overworld();
					List<Entity> self = level.getEntities((Entity) null, AABB.ofSize(them, 6, 20, 6), e -> Kinds.is(e, "mannequin"));
					for (Entity e : self) {
						if (e instanceof net.minecraft.world.entity.LivingEntity le) {
							le.setYBodyRot(bodyYaw);
							le.setYHeadRot(bodyYaw);
						}
					}
					List<OccupantEntity> its = level.getEntitiesOfClass(OccupantEntity.class, AABB.ofSize(at, 16, 16, 16));
					if (its.isEmpty()) return;
					OccupantEntity thing = its.get(0);
					thing.setPos(at.x, at.y, at.z);
					thing.setYRot(yaw);
					thing.setYHeadRot(yaw);
					thing.setYBodyRot(yaw);
					thing.setXRot(pitch);
					List<Entity> into = new ArrayList<>();
					if (!self.isEmpty()) for (int i = 0; i < count; i++) into.add(self.get(0));
					thing.setHeld(into);
				});
			});
		} finally {
			server.runCommand("kill " + BODY);
			server.runCommand("kill " + SELF);
		}
	}

	private static Vec3 horizontal(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}

	/** Where the camera is at tick {@code t}, on the ground. */
	private static Vec3 cam(double[][] poses, int t) {
		double[] p = poses[Mth.clamp(t, 0, poses.length - 1)];
		return new Vec3(p[0], 0, p[2]);
	}

	/**
	 * Not part of the film: their body lying each way it can lie, from the side and from above, a
	 * gold block on its +z side, so where to put it under the camera can be worked out.
	 */
	private static void calib(ClientGameTestContext context, TestSingleplayerContext game, Cabin cabin) {
		TestServerContext server = game.getServer();
		Vec3 at = local(cabin.o, 0, 0, -6);
		BlockPos mark = BlockPos.containing(at.x, at.y, at.z + 2);
		prepare(context, game, at.add(3.5, 0.6, 0), at, 6000);
		server.runOnServer(s -> set(s.overworld(), mark, Blocks.GOLD_BLOCK.defaultBlockState()));
		Path dir = DIR.resolve("calib");
		int k = 0;
		try {
			Files.createDirectories(dir);
			for (String pose : new String[]{"swimming", "sleeping"}) {
				server.runCommand("kill " + SELF);
				server.runCommand(String.format(Locale.ROOT, "summon mannequin %.3f %.3f %.3f {pose:\"%s\",Rotation:[0f,0f],immovable:1b,"
						+ "NoGravity:1b,Invulnerable:1b,hide_description:1b,Tags:[\"occupant_self\"]}", at.x, at.y, at.z, pose));
				context.waitTicks(30);
				for (double[] view : new double[][]{{3.5, 0.6, 0, 90, 5}, {0, 4.0, 0, 0, 90}}) {
					server.runCommand(tp(at.add(view[0], view[1], view[2]), view[3], view[4]));
					context.waitTicks(4);
					Path shot = context.takeScreenshot("reel-calib-" + k);
					Files.move(shot, dir.resolve(String.format(Locale.ROOT, "%04d.png", k++)), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			server.runCommand("kill " + SELF);
			server.runOnServer(s -> set(s.overworld(), mark, Blocks.AIR.defaultBlockState()));
		}
	}

	// ------------------------------------------------------------------ the places

	/** The house in the wood, and the ways to it and away from it. */
	private static final class Cabin {
		/** The rock the way goes round (x0, x1, z0, z1 from the house): big enough to hide what is on the other side of it. */
		static final int[] ROCK = {-9, -2, 14, 25};
		/** The shelf of the rock over the way round it (x0, x1, z0, z1), its underside three blocks up. */
		static final int[] LEDGE = {-11, 2, 26, 32};
		final BlockPos o;
		final BlockPos front, back;
		/** From the south, up to the door and in. */
		final Way approach;
		/** Out of the back door, along the rock, and round it. */
		final Way out;

		private Cabin(BlockPos o) {
			this.o = o;
			this.front = o.offset(0, 0, -1);
			this.back = o.offset(0, 0, 7);
			this.approach = Way.local(o, new double[][]{{1, -24}, {1, -17}, {-1, -13}, {1.5, -8}, {0, -4}, {0, -1}, {0, 3}});
			this.out = Way.local(o, new double[][]{{0, 4}, {0, 9}, {-0.5, 13.5}, {0.6, 18}, {0.2, 22.5}, {-0.3, 25.6},
					{-2.2, 27.6}, {-6, 28.5}, {-11, 29.3}, {-17, 30}});
		}

		static Cabin build(TestServerContext server, BlockPos o) {
			Cabin c = new Cabin(o);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				wood(level, o, -17, 17, -28, 38, List.of(c.approach, c.out), 7L, new double[][]{{0, 3, 7.5}, {-8, 3, 4.0}, {-4, 27, 2.2}, {0.3, 27.5, 3.2}, {1.0, 23.3, 2.2}, {1.4, 23.3, 2.2}, {-2, 30.5, 2.6}}, ROCK);
				RandomSource r = RandomSource.create(9L);
				for (int x = LEDGE[0]; x <= LEDGE[1]; x++) {
					for (int z = LEDGE[2]; z <= LEDGE[3]; z++) {
						// Ragged at its edges, thinner the further it reaches.
						boolean edge = x == LEDGE[0] || x == LEDGE[1] || z == LEDGE[3];
						if (edge && r.nextInt(3) == 0) continue;
						set(level, o.offset(x, 3, z), (r.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE).defaultBlockState());
						if (z < LEDGE[3] - 1) set(level, o.offset(x, 4, z), Blocks.STONE.defaultBlockState());
						if (r.nextInt(3) == 0) set(level, o.offset(x, 5, z), Blocks.MOSS_BLOCK.defaultBlockState());
					}
				}
				c.house(level);
			});
			return c;
		}

		/** One room: plank walls on a stone footing, a stepped roof, a door each end, a window each side, a lamp. */
		private void house(ServerLevel level) {
			BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
			BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = -4; x <= 4; x++) {
				for (int z = -1; z <= 7; z++) {
					boolean wall = Math.abs(x) == 4 || z == -1 || z == 7;
					boolean corner = Math.abs(x) == 4 && (z == -1 || z == 7);
					set(level, o.offset(x, -1, z), wall ? Blocks.COBBLESTONE.defaultBlockState() : planks);
					for (int y = 0; y <= 3; y++) {
						set(level, o.offset(x, y, z), !wall ? air : corner ? Blocks.DARK_OAK_LOG.defaultBlockState() : planks);
					}
				}
			}
			for (int x = -5; x <= 5; x++) {
				for (int z = -2; z <= 8; z++) {
					set(level, o.offset(x, 4, z), roof);
					for (int k = 1; k <= 4; k++) if (Math.abs(x) <= 5 - k) set(level, o.offset(x, 4 + k, z), roof);
				}
			}
			// The windows: on the west side tall, nearly to the eaves, so that whatever is outside it
			// is seen whole; on the east an ordinary one.
			for (int z = 2; z <= 4; z++) {
				for (int y = 1; y <= 3; y++) set(level, o.offset(-4, y, z), Blocks.GLASS.defaultBlockState());
				for (int y = 1; y <= 2; y++) set(level, o.offset(4, y, z), Blocks.GLASS.defaultBlockState());
			}
				// A lean-to along the west wall, for wood: low enough that whatever stands under it stoops.
			for (int x = -10; x <= -5; x++) {
				for (int z = -1; z <= 7; z++) set(level, o.offset(x, 4, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
			for (int z : new int[]{-1, 7}) {
				for (int y = 0; y <= 3; y++) set(level, o.offset(-10, y, z), Blocks.SPRUCE_LOG.defaultBlockState());
			}
			// A barrel for a table with the lamp on it, a bench, shelves, cobwebs in the corners.
			set(level, o.offset(-2, 0, 5), Blocks.BARREL.defaultBlockState());
			set(level, o.offset(-2, 1, 5), Blocks.LANTERN.defaultBlockState());
			set(level, o.offset(-3, 0, 5), Blocks.CRAFTING_TABLE.defaultBlockState());
			set(level, o.offset(-3, 0, 6), Blocks.SPRUCE_STAIRS.defaultBlockState());
			set(level, o.offset(3, 0, 6), Blocks.BOOKSHELF.defaultBlockState());
			set(level, o.offset(3, 1, 6), Blocks.BOOKSHELF.defaultBlockState());
			set(level, o.offset(3, 0, 0), Blocks.CHEST.defaultBlockState());
			set(level, o.offset(-3, 3, 6), Blocks.COBWEB.defaultBlockState());
			set(level, o.offset(3, 3, 0), Blocks.COBWEB.defaultBlockState());
		}

		/** A door, open or shut: the way out of here, each end. */
		void door(TestServerContext server, BlockPos at, boolean open) {
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.OPEN, open);
				set(level, at, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				set(level, at.above(), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			});
		}
	}

	/**
	 * A dark wood, built for a clip: level ground, the trunks close together and a roof of leaves
	 * over them, ferns and stones between, and worn ways through it ({@code ways}, kept clear).
	 * {@code open} are places kept clear of trees (x, z, how far); {@code rock} (x0, x1, z0, z1) a
	 * great mossy rock.
	 */
	private static void wood(ServerLevel level, BlockPos o, int x0, int x1, int z0, int z1, List<Way> ways, long seed,
							 double[][] open, int[] rock) {
		RandomSource r = RandomSource.create(seed);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double d = nearest(ways, o, x, z);
				int n = r.nextInt(10);
				BlockState top = d < 0.8 ? (n < 6 ? Blocks.COARSE_DIRT : Blocks.PODZOL).defaultBlockState()
						: n == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 3 ? Blocks.PODZOL.defaultBlockState()
						: Blocks.GRASS_BLOCK.defaultBlockState();
				set(level, o.offset(x, -1, z), top);
				// Filled under, where the ground fell away.
				for (int y = -2; y >= -12; y--) {
					BlockPos p = o.offset(x, y, z);
					BlockState s = level.getBlockState(p);
					if (!s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty()) break;
					set(level, p, Blocks.DIRT.defaultBlockState());
				}
				for (int y = 0; y <= 18; y++) set(level, o.offset(x, y, z), air);
			}
		}
		if (rock != null) {
			for (int x = rock[0]; x <= rock[1]; x++) {
				for (int z = rock[2]; z <= rock[3]; z++) {
					int in = Math.min(Math.min(x - rock[0], rock[1] - x), Math.min(z - rock[2], rock[3] - z));
					int h = 4 + Math.min(2, in) + r.nextInt(2);
					for (int y = 0; y < h; y++) {
						int n = r.nextInt(10);
						set(level, o.offset(x, y, z), (n < 4 ? Blocks.MOSSY_COBBLESTONE : n < 7 ? Blocks.STONE : n < 9 ? Blocks.ANDESITE
								: Blocks.COBBLESTONE).defaultBlockState());
					}
					set(level, o.offset(x, h, z), r.nextInt(3) == 0 ? Blocks.DARK_OAK_LEAVES.defaultBlockState()
							.setValue(LeavesBlock.PERSISTENT, true) : Blocks.MOSS_BLOCK.defaultBlockState());
				}
			}
		}
		// The trees: close, a step or two apart, never on the way.
		List<int[]> trunks = new ArrayList<>();
		for (int gx = x0; gx <= x1; gx += 3) {
			for (int gz = z0; gz <= z1; gz += 3) {
				int tx = gx + r.nextInt(2), tz = gz + r.nextInt(2);
				int kind = r.nextInt(10);
				if (tx > x1 || tz > z1 || kind == 9) continue;
				if (nearest(ways, o, tx, tz) < 1.8 || inside(open, tx, tz) || inRock(rock, tx, tz)) continue;
				boolean thick = kind == 8 && tx + 1 <= x1 && tz + 1 <= z1 && nearest(ways, o, tx + 1, tz + 1) >= 1.8
						&& nearest(ways, o, tx + 1, tz) >= 1.8 && nearest(ways, o, tx, tz + 1) >= 1.8
						&& !inside(open, tx + 1, tz + 1) && !inRock(rock, tx + 1, tz + 1);
				trunks.add(new int[]{tx, tz, 7 + r.nextInt(5), kind < 2 ? 1 : 0, thick ? 1 : 0});
			}
		}
		for (int[] t : trunks) {
			BlockState leaves = (t[3] == 1 ? Blocks.SPRUCE_LEAVES : Blocks.DARK_OAK_LEAVES).defaultBlockState()
					.setValue(LeavesBlock.PERSISTENT, true);
			int h = t[2];
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					for (int y = h - 3; y <= h + 1; y++) {
						int reach = y >= h ? 1 : y == h - 3 ? 2 : 3;
						if (Math.abs(dx) + Math.abs(dz) > reach + r.nextInt(2)) continue;
						BlockPos p = o.offset(t[0] + dx, y, t[1] + dz);
						if (level.getBlockState(p).isAir()) set(level, p, leaves);
					}
				}
			}
		}
		for (int[] t : trunks) {
			BlockState log = (t[3] == 1 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG).defaultBlockState();
			for (int y = 0; y <= t[2]; y++) {
				set(level, o.offset(t[0], y, t[1]), log);
				if (t[4] == 1) {
					set(level, o.offset(t[0] + 1, y, t[1]), log);
					set(level, o.offset(t[0], y, t[1] + 1), log);
					set(level, o.offset(t[0] + 1, y, t[1] + 1), log);
				}
			}
		}
		// Between them: ferns, dead bushes, a few mushrooms and stones.
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double d = nearest(ways, o, x, z);
				if (d < 1.3 || inside(open, x, z) || inRock(rock, x, z)) continue;
				BlockPos p = o.offset(x, 0, z);
				if (!level.getBlockState(p).isAir()) continue;
				int n = r.nextInt(100);
				if (n < 18) set(level, p, Blocks.FERN.defaultBlockState());
				else if (n < 21) set(level, p, Blocks.DEAD_BUSH.defaultBlockState());
				else if (n < 23) set(level, p, Blocks.BROWN_MUSHROOM.defaultBlockState());
				else if (n < 25 && d > 2.4) {
					set(level, p, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
					if (r.nextBoolean()) set(level, p.above(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
			}
		}
	}

	private static double nearest(List<Way> ways, BlockPos o, int x, int z) {
		double best = Double.MAX_VALUE;
		for (Way w : ways) best = Math.min(best, w.distance(o.getX() + x + 0.5, o.getZ() + z + 0.5));
		return best;
	}

	private static boolean inside(double[][] open, int x, int z) {
		if (open == null) return false;
		for (double[] c : open) if (Math.hypot(x - c[0], z - c[1]) < c[2]) return true;
		return false;
	}

	private static boolean inRock(int[] rock, int x, int z) {
		return rock != null && x >= rock[0] - 1 && x <= rock[1] + 1 && z >= rock[2] - 1 && z <= rock[3] + 1;
	}

	/** A way through the trees: points in order, in the world; where along it a place is, and how far off it. */
	static final class Way {
		private final double[] xs, zs, at;

		Way(double[] xs, double[] zs) {
			this.xs = xs;
			this.zs = zs;
			this.at = new double[xs.length];
			for (int i = 1; i < xs.length; i++) at[i] = at[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
		}

		/** Points as (x, z) from the middle of {@code o}'s block. */
		static Way local(BlockPos o, double[][] pts) {
			double[] xs = new double[pts.length], zs = new double[pts.length];
			for (int i = 0; i < pts.length; i++) {
				xs[i] = o.getX() + 0.5 + pts[i][0];
				zs[i] = o.getZ() + 0.5 + pts[i][1];
			}
			return new Way(xs, zs);
		}

		/** The way on from {@code (x, z)}: from there to the next point along, and on. */
		Way from(double x, double z) {
			double s = along(x, z);
			int k = 1;
			while (k < at.length - 1 && at[k] <= s + 0.5) k++;
			double[] nx = new double[xs.length - k + 1], nz = new double[xs.length - k + 1];
			nx[0] = x;
			nz[0] = z;
			System.arraycopy(xs, k, nx, 1, xs.length - k);
			System.arraycopy(zs, k, nz, 1, zs.length - k);
			return new Way(nx, nz);
		}

		double length() {
			return at[at.length - 1];
		}

		/** The point {@code s} along it (past the end, on the way it was going). */
		double[] point(double s) {
			int i = 1;
			while (i < at.length - 1 && at[i] < s) i++;
			double u = s < 0 ? 0 : (s - at[i - 1]) / Math.max(1e-6, at[i] - at[i - 1]);
			return new double[]{xs[i - 1] + (xs[i] - xs[i - 1]) * u, zs[i - 1] + (zs[i] - zs[i - 1]) * u};
		}

		/** How far along it the nearest point to {@code (x, z)} is. */
		double along(double x, double z) {
			double best = Double.MAX_VALUE, s = 0;
			for (int i = 1; i < xs.length; i++) {
				double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1], len2 = dx * dx + dz * dz;
				double u = len2 == 0 ? 0 : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz) / len2, 0, 1);
				double d = Math.hypot(xs[i - 1] + dx * u - x, zs[i - 1] + dz * u - z);
				if (d < best) {
					best = d;
					s = at[i - 1] + u * (at[i] - at[i - 1]);
				}
			}
			return s;
		}

		double along(Vec3 p) {
			return along(p.x, p.z);
		}

		double distance(double x, double z) {
			double[] p = point(along(x, z));
			return Math.hypot(p[0] - x, p[1] - z);
		}
	}

	// ------------------------------------------------------------------ a frightened person

	/**
	 * How a frightened person carries a camera, a tick at a time: where they are, which way they
	 * are going and which way they look, how fast, how hard their running shakes it, and how far
	 * it tips. Turns are a head's (fast, a little past and back); a run has a stride, three steps a
	 * second, the eye rising and dropping with each, the shoulders rocking, leaning into the bends
	 * of the way, the head looking round them before the body gets there; standing, they breathe
	 * and their hands shake.
	 */
	static final class Fear {
		/** x, z, yaw, pitch, lift (the eye dropped or raised), speed, tremble, stride phase, roll, on their feet. */
		private final List<double[]> poses = new ArrayList<>();
		private final List<String> marks = new ArrayList<>();
		double x, z, heading, yaw, pitch, speed, phase, lift, tremble, roll;
		private boolean feet = true;
		/** Still in their hand (shaking, breathing with them), or lying where it fell. */
		private boolean hand = true;
		/** No two strides alike: this one's length, and how hard it lands. */
		private double stride = 1.0, landing = 1.0;
		private final java.util.Random uneven = new java.util.Random(5L);
		private Way way;
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
			heading = way != null ? ahead(2.4) : yaw;
			return this;
		}

		/** From now on, going along {@code w}. */
		void follow(Way w) {
			way = w;
			heading = ahead(2.4);
		}

		/** Something heard here, for the edit. */
		void mark(String kind) {
			markAt(poses.size(), kind);
		}

		void markAt(int tick, String kind) {
			marks.add(tick + " " + kind);
		}

		/** Where they are at tick {@code i} (at the height they started at). */
		Vec3 at(int i) {
			double[] p = poses.get(Mth.clamp(i, 0, poses.size() - 1));
			return new Vec3(p[0], eyeY, p[1]);
		}

		static double yawBetween(Vec3 from, Vec3 to) {
			return Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
		}

		private double yawTo(Vec3 p) {
			return Math.toDegrees(Math.atan2(-(p.x - x), p.z - z));
		}

		private double pitchTo(Vec3 p) {
			return -Math.toDegrees(Math.atan2(p.y - (eyeY + lift), Math.hypot(p.x - x, p.z - z)));
		}

		private static double toward(double from, double to, double rate) {
			return from + Mth.wrapDegrees(to - from) * rate;
		}

		/**
		 * Whether {@code at} is in front of them now (inside what the upright frame shows across)
		 * and not behind {@code rock} (x0, x1, z0, z1 from {@code o}).
		 */
		boolean sees(Vec3 at, BlockPos o, int[] rock) {
			if (Math.abs(Mth.wrapDegrees(yawTo(at) - yaw)) > 24.0) return false;
			for (int i = 1; i < 40; i++) {
				double u = i / 40.0;
				int bx = Mth.floor(x + (at.x - x) * u) - o.getX(), bz = Mth.floor(z + (at.z - z) * u) - o.getZ();
				if (bx >= rock[0] && bx <= rock[1] && bz >= rock[2] && bz <= rock[3]) return false;
			}
			return true;
		}

		/** Which way the way goes from here, {@code by} blocks on. */
		private double ahead(double by) {
			double[] p = way.point(way.along(x, z) + by);
			return Math.toDegrees(Math.atan2(-(p[0] - x), p[1] - z));
		}

		/** Bent along the way, as a runner cuts a bend: no faster than {@code rate} degrees a tick. */
		private void steer(double rate) {
			if (way == null) return;
			heading += Mth.clamp(Mth.wrapDegrees(ahead(2.4) - heading), -rate, rate);
		}

		/** One tick on: speed eased towards {@code to}, a step taken whenever a foot comes down. */
		private void step(double to, double accel) {
			speed += Mth.clamp(to - speed, -accel, accel);
			double r = Math.toRadians(heading);
			x += -Math.sin(r) * speed;
			z += Math.cos(r) * speed;
			double before = phase;
			// A step is longer the faster they go: under a block walking, nearly two at a sprint; give or take.
			phase += speed * Math.PI / ((0.55 + 4.6 * speed) * stride);
			if (feet && speed > 0.03 && Math.floor(before / Math.PI) != Math.floor(phase / Math.PI)) {
				mark("step");
				stride = 0.9 + uneven.nextDouble() * 0.2;
				landing = 0.75 + uneven.nextDouble() * 0.5;
			}
		}

		private void push() {
			poses.add(new double[]{x, z, yaw, pitch, lift, speed, tremble, phase, roll, feet ? 1 : 0, landing, hand ? 1 : 0});
		}

		/** Stood (whatever way they were going slowing to nothing), looking at {@code at}, shaking as much as {@code shake}. */
		void stare(int n, Vec3 at, double shake) {
			for (int i = 0; i < n; i++) {
				step(0, 0.03);
				yaw = toward(yaw, yawTo(at), 0.3);
				pitch += (pitchTo(at) - pitch) * 0.3;
				tremble += (shake - tremble) * 0.2;
				lift *= 0.85;
				roll *= 0.9;
				push();
			}
		}

		/** Walking on as they were going (along the way, if there is one), looking about. */
		void walk(int n, double pace, IntFunction<Vec3> look) {
			for (int i = 0; i < n; i++) {
				steer(6);
				step(pace, 0.02);
				Vec3 at = look.apply(i);
				yaw = toward(yaw, yawTo(at), 0.18);
				pitch += (pitchTo(at) - pitch) * 0.18;
				tremble *= 0.9;
				roll *= 0.9;
				push();
			}
		}

		/** The head turned slowly to {@code at}: not wanting to look, looking. */
		void turnTo(int n, Vec3 at) {
			double y0 = yaw, p0 = pitch;
			for (int i = 1; i <= n; i++) {
				double u = smooth((double) i / n);
				step(0, 0.03);
				yaw = y0 + Mth.wrapDegrees(yawTo(at) - y0) * u;
				pitch = p0 + (pitchTo(at) - p0) * u;
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
				roll += (-Math.signum(d) * 9 * Math.sin(u * Math.PI) - roll) * 0.5;
				tremble *= 0.9;
				push();
			}
			heading = to;
		}

		/** Running, along the way if there is one (else bending {@code curve} degrees a tick), leaning into it. */
		void run(int n, double pace, double curve) {
			for (int i = 0; i < n; i++) runTick(pace, curve);
		}

		/** Running on until {@code left} blocks from the end of the way. */
		void runTo(double left, double pace) {
			runUntil(way.length() - left, pace);
		}

		/** Running on until {@code s} along the way. */
		void runUntil(double s, double pace) {
			for (int i = 0; i < 120 && way.along(x, z) < s; i++) runTick(pace, 0);
		}

		private void runTick(double pace, double curve) {
			if (way != null) steer(10);
			else heading += curve;
			step(pace, 0.05);
			yaw = toward(yaw, way != null ? ahead(5.0) : heading, 0.4);
			pitch += (5.0 + speed * 10 - pitch) * 0.3;
			lift *= 0.8;
			tremble *= 0.9;
			roll *= 0.8;
			push();
		}

		/** Over the shoulder while still running: round to {@code at}, held there, and back again. */
		void lookBack(int turn, int hold, int back, Vec3 at) {
			int total = turn + hold + back;
			for (int i = 0; i < total; i++) {
				steer(10);
				step(SPRINT * 0.8, 0.04);
				double u = i < turn ? overshoot((i + 1.0) / turn) : i < turn + hold ? 1.0 : 1.0 - smooth((i - turn - hold + 1.0) / back);
				double forward = way != null ? ahead(4.0) : heading, there = yawTo(at);
				yaw = forward + Mth.wrapDegrees(there - forward) * u;
				pitch = (5.0 + speed * 10) * (1 - u) + pitchTo(at) * u;
				roll += (Math.signum(Mth.wrapDegrees(there - forward)) * 7 * u - roll) * 0.3;
				tremble += ((i >= turn && i < turn + hold ? 1.3 : 0.4) - tremble) * 0.3;
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

		/** Running, and in through somewhere, and round: slowing hard, the head round to {@code at} first. */
		void stopAndTurn(int n, Vec3 at) {
			double y0 = yaw, p0 = pitch;
			for (int i = 1; i <= n; i++) {
				double u = overshoot((double) i / n);
				step(0, 0.06);
				yaw = y0 + Mth.wrapDegrees(yawTo(at) - y0) * u;
				pitch = p0 + (pitchTo(at) - p0) * u;
				roll += (8 * Math.sin(u * Math.PI) - roll) * 0.5;
				tremble += (0.9 - tremble) * 0.2;
				push();
			}
		}

		/** Stopped dead, as if they had run into a wall, the eyes going up to {@code at}. */
		void halt(int n, Vec3 at) {
			for (int i = 0; i < n; i++) {
				step(0, 0.09);
				yaw = toward(yaw, yawTo(at), 0.45);
				pitch += (pitchTo(at) - pitch) * 0.45;
				tremble += (2.0 - tremble) * 0.5;
				lift += (-0.1 - lift) * 0.4;
				roll *= 0.7;
				push();
			}
		}

		/**
		 * Struck out of their hands: the camera thrown, tumbling, to land on the ground at {@code to},
		 * its lens {@code height} up, on its side by {@code tilt}, looking back at {@code at}.
		 */
		void thrown(int n, Vec3 to, Vec3 at, double height, double tilt) {
			mark("hit");
			feet = false;
			hand = false;
			double x0 = x, z0 = z, y0 = yaw, p0 = pitch, l0 = lift, r0 = roll;
			double landYaw = yawBetween(to, at);
			double landPitch = -Math.toDegrees(Math.atan2(at.y - (eyeY + height - Cinematic.EYE), Math.hypot(at.x - to.x, at.z - to.z)));
			double spin = Mth.wrapDegrees(landYaw - y0) + 360.0;
			for (int i = 1; i <= n; i++) {
				double u = (double) i / n;
				double e = 1 - (1 - u) * (1 - u);
				x = x0 + (to.x - x0) * e;
				z = z0 + (to.z - z0) * e;
				lift = l0 + (height - Cinematic.EYE - l0) * u * u + 0.45 * Math.sin(u * Math.PI);
				yaw = y0 + spin * e;
				pitch = p0 * (1 - u) + landPitch * u + 75 * Math.sin(u * Math.PI * 1.5) * (1 - u);
				roll = r0 * (1 - u) + 150 * Math.sin(u * Math.PI * 1.2) * (1 - u) + tilt * u;
				speed = 0;
				tremble = 0;
				push();
			}
			mark("land");
		}

		/** The camera lying where it landed: a bounce, and then nothing moves it. */
		void still(int n, Vec3 at, double tilt) {
			double y1 = yaw, p1 = pitch, l1 = lift;
			for (int i = 0; i < n; i++) {
				double b = i < 5 ? Math.sin(i / 5.0 * Math.PI) * (1 - i / 5.0) : 0;
				yaw = y1 + 4 * b;
				pitch = p1 - 5 * b;
				lift = l1 + 0.08 * b;
				roll = tilt + 7 * b;
				push();
			}
		}

		List<String> marks() {
			return marks;
		}

		/** The poses on level ground at {@code groundY}: the eye at a person's height over it, rising and falling with each step. */
		double[][] flat(double groundY) {
			int n = poses.size();
			double[][] out = new double[n][];
			for (int i = 0; i < n; i++) out[i] = finish(i, groundY + Cinematic.EYE);
			return out;
		}

		/** x, y, z, yaw, pitch, roll for tick {@code i}: the stride and the shaking hands put in. */
		private double[] finish(int i, double base) {
			double[] p = poses.get(i);
			double px = p[0], pz = p[1], yw = p[2], pt = p[3], lf = p[4], sp = p[5], tr = p[6], ph = p[7], rl = p[8];
			// Lying on the ground, the camera is only where it fell.
			if (p[11] == 0) return new double[]{px, base + lf, pz, yw, pt, rl};
			boolean standing = p[9] > 0;
			double hard = p[10];
			double run = standing ? Mth.clamp(sp / SPRINT, 0, 1) : 0;
			double moving = standing && sp > 0.01 ? 1 : 0;
			double bob = (0.03 + 0.11 * run) * (Math.abs(Math.sin(ph)) - 0.55) * moving * (0.6 + 0.4 * hard);
			// Breathing: standing, the camera rises and falls with it; on the ground, heaving.
			double breath = Math.sin(i * 0.3) * (standing ? 0.012 * (1 - run) : 0.03);
			double sway = (0.02 + 0.06 * run) * Math.sin(ph) * moving;
			// Hands that will not keep still: slow drift, faster shaking, both worse with fear.
			double drift = 0.6 * Math.sin(i * 0.29 + 0.3) + 0.4 * Math.sin(i * 0.71 + 1.1) + 0.25 * Math.sin(i * 1.93 + 2.0);
			double shake = Math.sin(i * 2.7) * 0.5 + Math.sin(i * 3.9 + 1.0) * 0.4;
			double hands = 0.7 + 1.6 * run;
			double yawOut = yw + drift * hands * 0.9 + shake * tr * 0.9 + 2.4 * run * Math.sin(ph);
			// Each footfall jolts the view down a touch, harder for a heavier step.
			double fall = Math.pow(1 - Math.abs(Math.sin(ph)), 6) * 3.0 * run * hard;
			double pitchOut = pt + drift * hands * 0.5 + shake * tr * 0.7 + fall + breath * 9.0;
			// The tilt: the shoulders rocking with the stride, leaning into a turn, the hands.
			double turned = i > 0 ? Mth.wrapDegrees(yw - poses.get(i - 1)[2]) : 0;
			double rollOut = rl + 1.8 * run * Math.sin(ph) - Mth.clamp(turned * 0.3, -6, 6) + drift * 0.8 + shake * tr * 0.7;
			// And under everything, the faint unsteadiness of a hand holding a camera: never still.
			yawOut += 0.2 * (0.6 * Math.sin(i * 1.71 + 0.4) + 0.4 * Math.sin(i * 2.37 + 1.9));
			pitchOut += 0.16 * (0.6 * Math.sin(i * 1.37 + 2.2) + 0.4 * Math.sin(i * 2.53 + 0.7));
			rollOut += 0.25 * (0.6 * Math.sin(i * 1.19 + 1.3) + 0.4 * Math.sin(i * 2.11 + 2.6));
			double r = Math.toRadians(yw);
			double sx = Math.cos(r) * sway, sz = Math.sin(r) * sway;
			return new double[]{px + sx, base + bob + lf + breath, pz + sz, yawOut, Mth.clamp(pitchOut, -85, 85), rollOut};
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

	/** A point from the middle of {@code o}'s block, at {@code y} above its floor. */
	private static Vec3 local(BlockPos o, double x, double y, double z) {
		return new Vec3(o.getX() + 0.5 + x, o.getY() + y, o.getZ() + 0.5 + z);
	}

	/** Time of day, the camera put where the clip begins, the world round it drawn. */
	private static void prepare(ClientGameTestContext context, TestSingleplayerContext game, Vec3 eye, Vec3 look, int time) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand("kill " + BODY);
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
							   Fear f, IntConsumer cue) {
		Path dir = DIR.resolve(name);
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("marks.txt"), String.join("\n", f.marks()) + "\n");
			StringBuilder roll = new StringBuilder();
			for (double[] p : poses) roll.append(String.format(Locale.ROOT, "%.2f %.2f %.2f%n", p[3], p[4], p[5]));
			Files.writeString(dir.resolve("roll.txt"), roll.toString());
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

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
