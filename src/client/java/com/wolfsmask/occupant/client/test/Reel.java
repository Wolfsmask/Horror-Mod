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
 * Footage, not photographs: a tape, recorded a frame every tick, upright, the shape of a phone
 * screen, for a short video of the mod. Only ever what the camcorder saw: someone walking a path in
 * a wood at dusk, the sounds the world makes around them, and at the end the thing that made them.
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
	/**
	 * What its leg goes through: nothing anyone can see, not even a spectator (an invisible armor
	 * stand still shows to one, as a ghost).
	 */
	private static final String BODY = "@e[type=interaction,tag=occupant_reel]";
	/** Held as it is put, standing at its full height, looking at you. */
	private static final Cinematic.Look STANDING = new Cinematic.Look(false, 0.01f, OccupantEntity.Mode.STARE, 0);
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

			clip("found", () -> found(context, game, woods));
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

	/** The tick being recorded, for whatever is posed by hand to follow. */
	private static volatile int now;

	/**
	 * One tape, as it was found: someone with a camcorder walking a path through a wood as night
	 * comes down, filming nothing much. The things a world like this does to you: a pick on stone,
	 * far off and down somewhere; steps behind yours that stop when yours do, and then one more;
	 * the cave sound. They turn round at the steps, and there is nothing. They go on, quicker; at a
	 * rustle they look back, and far back on the path, in the fog, something pale might be standing.
	 * They stop. Something breathes behind them. They turn round, and it is there, and it knocks
	 * them down; the camera in the leaves, still recording, sees them on their back shoving
	 * themselves away from it, a hand up, and it coming after them; and the tape stops. Recorded in
	 * one go; the edit cuts it, the way a tape is cut by being stopped and started.
	 */
	private static void found(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(120, 0, 90)));
		if (floor == null) return;
		BlockPos o = floor.above();
		Way trail = Way.local(o, new double[][]{{0, -6}, {0.6, 4}, {-0.8, 13}, {0.7, 22}, {-0.6, 31}, {0.8, 40},
				{-0.5, 49}, {0.6, 58}, {0, 67}, {0, 84}});
		server.runOnServer(s -> wood(s.overworld(), o, -16, 16, -30, 88, List.of(trail), 63L, new double[0][], null));
		Vec3 start = local(o, 0, Cinematic.EYE, -6);
		Fear f = new Fear(start);
		f.follow(trail);
		f.face(f.onWay(5.0, 0, -0.4));
		// Walking: the path, the trees either side, now and then down at their feet. Nothing much.
		f.walk(90, 0.085, t -> f.onWay(5.5, 1.3 * Math.sin(t * 0.05), -0.5 + 0.25 * Math.sin(t * 0.08) - (t > 55 && t < 70 ? 1.2 : 0)));
		f.markAt(6, "in");
		// Off in the dark, far down somewhere, a pick on stone. They slow, and stop to listen.
		int mine = f.size() - 18;
		f.markAt(mine, "mine");
		f.walk(16, 0.02, t -> f.onWay(5.0, 2.5, -0.3));
		Vec3 sound = f.onWay(3.0, 14.0, -1.8);
		f.stare(44, sound, 0.2);
		f.markAt(f.size() - 2, "cut");
		f.walk(60, 0.09, t -> f.onWay(5.5, -1.1 * Math.sin(t * 0.04), -0.5));
		f.markAt(f.size() - 20, "in");
		// Steps behind theirs, a moment after each, all the way; and when they stop, one more.
		int behindFrom = f.size();
		f.walk(56, 0.095, t -> f.onWay(5.5, 0.6 * Math.sin(t * 0.06), -0.6));
		int stopped = f.size();
		f.stare(18, f.onWay(5.0, 0, -0.4), 0.5);
		f.markAt(stopped + 6, "echo");
		// Round, slowly, back along the path: the dark, the trees, nothing.
		Vec3 back = f.onWay(-9.0, 0.3, -0.2);
		f.turnTo(26, back);
		int nothing = f.size();
		f.stare(34, back, 0.8);
		f.markAt(nothing + 14, "what");
		f.turnTo(16, f.onWay(5.0, 0, -0.3));
		f.markAt(f.size() - 2, "cut");
		// On, quicker, breathing; the cave sound.
		f.walk(48, 0.12, t -> f.onWay(6.0, 0.5 * Math.sin(t * 0.07), -0.5));
		f.markAt(f.size() - 40, "in");
		f.markAt(f.size() - 26, "cave");
		// Something in the leaves behind: a look back over the shoulder, still walking. Far back on the
		// path, at the edge of what the fog lets them see, something pale is standing; they do not see it.
		int glance = f.size();
		f.markAt(glance - 3, "rustle");
		Vec3 farBack = f.onWay(-24.0, 0.6, 0.8);
		BlockPos farSpot = BlockPos.containing(farBack.x, o.getY(), farBack.z);
		f.glanceBack(8, 9, 8, farBack, 0.1);
		f.walk(52, 0.13, t -> f.onWay(6.0, 0.4 * Math.sin(t * 0.08), -0.5));
		// They stop. Breathing, right behind them. They do not move.
		f.stare(8, f.onWay(5.0, 0, -0.4), 0.6);
		int breath = f.size();
		f.markAt(breath + 2, "breath");
		f.stare(32, f.onWay(5.0, 0, -0.4), 1.4);
		// Round, slowly; up. It is there.
		Vec3 here = new Vec3(f.x, o.getY(), f.z);
		Vec3 fwd = f.wayDir();
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 feet = here.subtract(fwd.scale(2.6));
		Vec3 itsFace = feet.add(0, 4.3, 0);
		f.turnTo(28, itsFace);
		int seen = f.size() - 6;
		f.markAt(seen, "seen");
		f.flinchAt(seen, itsFace, 0.7);
		// It only looks at them. They cannot move.
		f.stare(24, itsFace, 1.9);
		f.backAway(6, itsFace, 0.07);
		// It knocks them down, backwards, flat on their back, their feet toward it; the camera out of
		// their hand, over them, into the dirt off to one side beyond their head, still recording,
		// looking back at them from over their shoulder, and past them at it.
		int hit = f.size();
		Vec3 person0 = here.add(fwd.scale(1.3));
		Vec3 lens = here.add(fwd.scale(6.5)).add(right.scale(2.5));
		Vec3 aim = here.add(fwd.scale(2.97)).add(right.scale(0.42)).add(0, 1.32, 0);
		f.thrown(8, lens, aim, 0.25, -14.0);
		int land = f.size();
		// On their back: shoving themselves away from it, a hand up at it; it comes after them; it
		// bends down to them; its leg comes down; the tape stops.
		int[] pushes = new int[8];
		for (int k = 0; k < pushes.length; k++) pushes[k] = hit + 18 + 10 * k;
		int stab = hit + 112, end = hit + 120;
		for (int push : pushes) f.markAt(push, "scoot");
		f.markAt(hit + 26, "no1");
		f.markAt(hit + 56, "no2");
		f.markAt(hit + 100, "no3");
		f.markAt(stab, "stab");
		f.still(end - land, aim, -14.0);
		f.mark("end");
		// The steps behind them: one after each of theirs, a few ticks late.
		for (String m : new ArrayList<>(f.marks())) {
			String[] parts = m.split(" ");
			int t = Integer.parseInt(parts[0]);
			if (parts[1].equals("step") && t >= behindFrom && t < stopped) f.markAt(t + 7, "echo");
		}
		IntToDoubleFunction scooted = t -> {
			double d = 0;
			for (int push : pushes) d += 0.16 * smooth((t - push - 2) / 5.0);
			return d;
		};
		// Where they lie (their feet: a body lying down is drawn from its feet to its head), where their
		// chest is, and where it is, each tick.
		IntFunction<Vec3> person = t -> person0.add(fwd.scale(scooted.applyAsDouble(t)));
		IntFunction<Vec3> chest = t -> person.apply(t).add(fwd.scale(1.15)).add(0, 0.3, 0);
		IntFunction<Vec3> it = t -> {
			if (t < hit) return feet;
			if (t < hit + 5) return feet.add(fwd.scale(0.6 * smooth((t - hit) / 5.0)));
			Vec3 after = feet.add(fwd.scale(0.6 + 1.5 * smooth((t - hit - 20) / 90.0)));
			if (t < stab - 2) return after;
			return after.add(fwd.scale(0.5 * smooth((t - stab + 2) / 4.0)));
		};
		f.watching(t -> t >= glance && t < glance + 30 ? Vec3.atBottomCenterOf(farSpot).add(0, 2.4, 0)
				: t >= breath - 8 ? it.apply(t).add(0, 2.4, 0) : null);
		double[][] poses = f.flat(o.getY());
		// They lie on their back, the head away from it: a body asleep lies with its head a quarter turn round from where it faces.
		float lying = (float) (Fear.yawBetween(Vec3.ZERO, fwd) + 90.0);
		prepare(context, game, start, f.onWay(5.0, 0, -0.4), 13050);
		// A small glade where it happens: nothing low over the path up to its height, and nothing
		// between where the camera will land and them; the trunks there gone, the canopy kept.
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (double a = -4.0; a <= 8.5; a += 0.5) {
				for (double b = -2.5; b <= 4.5; b += 0.5) {
					Vec3 q = here.add(fwd.scale(a)).add(right.scale(b));
					for (int y = 0; y <= 18; y++) {
						BlockPos p = BlockPos.containing(q.x, o.getY() + y, q.z);
						BlockState was = level.getBlockState(p);
						if (was.isAir()) continue;
						if (y <= 7 || was.is(net.minecraft.tags.BlockTags.LOGS)) set(level, p, Blocks.AIR.defaultBlockState());
					}
				}
			}
		});
		Vec3 hidden = person0.add(0, -8, 0);
		// Turned the way it will lie from the start: a body that has never turned is never caught
		// part way round.
		server.runCommand(String.format(Locale.ROOT, "summon mannequin %.3f %.3f %.3f {pose:\"sleeping\",immovable:1b,NoGravity:1b,"
				+ "Invulnerable:1b,Silent:1b,hide_description:1b,Rotation:[%.1ff,0.0f],Tags:[\"occupant_self\"]}", hidden.x, hidden.y, hidden.z, lying));
		context.waitTicks(25);
		int[] puppet = {-1};
		try {
			record(context, server, "found", poses, f, t -> {
				now = t;
				if (t == glance) Cinematic.place(server, farSpot, new Vec3(poses[t][0], poses[t][1], poses[t][2]), STANDING);
				if (t == glance + 30) server.runCommand("kill " + ALL);
				if (t == breath - 8) Cinematic.place(server, BlockPos.containing(feet), here, STANDING);
				if (t >= breath - 8) {
					Vec3 at = it.apply(t);
					Vec3 eye = new Vec3(poses[t][0], poses[t][1], poses[t][2]);
					// Standing over them to look at them before it strikes; down at them once they are down.
					Vec3 them = chest.apply(t);
					Vec3 watch = t < hit ? eye : them;
					OccupantEntity.Mode mode = t < hit ? OccupantEntity.Mode.STARE : OccupantEntity.Mode.AMBUSH;
					look(server, at, watch, 0);
					server.runOnServer(s -> {
						for (OccupantEntity e : s.overworld().getEntitiesOfClass(OccupantEntity.class, AABB.ofSize(at, 16, 16, 16))) {
							if (e.getMode() != mode) e.setMode(mode);
						}
					});
				}
				if (t == hit + 2) {
					Vec3 p = person.apply(t);
					server.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f 0", SELF, p.x, p.y, p.z, lying));
					server.runOnServer(s -> {
						for (Entity e : s.overworld().getEntities((Entity) null, AABB.ofSize(p, 6, 6, 6), x -> Kinds.is(x, "mannequin"))) {
							if (e instanceof net.minecraft.world.entity.LivingEntity le) {
								le.setYBodyRot(lying);
								le.setYHeadRot(lying);
							}
						}
					});
				} else if (t > hit + 2) {
					Vec3 p = person.apply(t);
					server.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f 0", SELF, p.x, p.y, p.z, lying));
					if (puppet[0] >= 0) {
						context.runOnClient(mc -> {
							if (mc.level != null && mc.level.getEntity(puppet[0]) instanceof net.minecraft.world.entity.LivingEntity le) {
								le.setYRot(lying);
								le.setYHeadRot(lying);
								le.setYBodyRot(lying);
							}
						});
					}
				}
				if (t == hit + 3) {
					Vec3 p = person.apply(t);
					context.runOnClient(mc -> {
						if (mc.level == null) return;
						for (Entity e : mc.level.getEntities((Entity) null, AABB.ofSize(p, 6, 6, 6), x -> Kinds.is(x, "mannequin"))) {
							puppet[0] = e.getId();
							com.wolfsmask.occupant.client.render.Ragdoll.puppet(e.getId(), () -> onTheirBack(now - hit, pushes, hit, stab));
						}
					});
				}
				// Its leg at the lens as it knocks them down; its legs into their chest at the end.
				boolean atLens = t >= hit - 6 && t <= hit;
				boolean atChest = t >= stab - 6;
				double[] held = poses[Math.min(t, hit - 1)];
				double yr = Math.toRadians(held[3]);
				Vec3 legAt = new Vec3(held[0], held[1], held[2]).add(-Math.sin(yr) * 0.45, -0.15, Math.cos(yr) * 0.45);
				if (t == hit - 6) {
					server.runCommand(String.format(Locale.ROOT, "summon interaction %.3f %.3f %.3f {width:0.01f,height:0.01f,Tags:[\"occupant_reel\"]}",
							legAt.x, legAt.y, legAt.z));
				} else if (atLens) {
					server.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f", BODY, legAt.x, legAt.y, legAt.z));
				} else if (t == hit + 1) {
					server.runCommand("kill " + BODY);
				}
				Vec3 into0 = chest.apply(t);
				if (t == stab - 6) {
					server.runCommand(String.format(Locale.ROOT, "summon interaction %.3f %.3f %.3f {width:0.01f,height:0.01f,Tags:[\"occupant_reel\"]}",
							into0.x, into0.y, into0.z));
				} else if (atChest) {
					server.runCommand(String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f", BODY, into0.x, into0.y, into0.z));
				}
				Vec3 at = it.apply(t);
				int legs = t >= stab - 5 ? 2 : 0;
				server.runOnServer(s -> {
					ServerLevel level = s.overworld();
					List<OccupantEntity> its = level.getEntitiesOfClass(OccupantEntity.class, AABB.ofSize(at, 16, 16, 16));
					if (its.isEmpty()) return;
					List<Entity> into = new ArrayList<>();
					if (atLens) {
						into.addAll(level.getEntities((Entity) null, AABB.ofSize(legAt, 4, 4, 4), e -> Kinds.is(e, "interaction")));
					} else if (legs > 0) {
						List<Entity> marks = level.getEntities((Entity) null, AABB.ofSize(into0, 4, 4, 4), e -> Kinds.is(e, "interaction"));
						for (int i = 0; i < legs && !marks.isEmpty(); i++) into.add(marks.get(0));
					}
					its.get(0).setHeld(into);
				});
			});
		} finally {
			if (puppet[0] >= 0) com.wolfsmask.occupant.client.render.Ragdoll.puppet(puppet[0], null);
			server.runCommand("kill " + BODY);
			server.runCommand("kill " + SELF);
		}
	}

	/**
	 * Them on their back, {@code tau} ticks after it knocked them down (head, arms and legs, as
	 * Ragdoll lays them out; on their back, forward is up): flat a moment; then the head up to look
	 * at it, one hand held up at it, shaking, the other arm and the legs shoving them away from it,
	 * a push at a time; at the end both arms up over their face and the knees pulled up.
	 */
	private static float[] onTheirBack(int tau, int[] pushes, int hit, int stab) {
		float t = Math.max(0, tau);
		float shake = (float) (Math.sin(t * 1.9) * 0.6 + Math.sin(t * 3.1 + 1.0) * 0.4);
		float[] p = new float[10];
		if (tau < 14) {
			// Down: flat, winded, the head back, the arms out where they fell.
			float u = (float) smooth(tau / 14.0);
			p[0] = 0.6f * u;
			p[2] = 0.15f;
			p[3] = 0.5f - 0.3f * u;
			p[4] = 0.2f;
			p[5] = -0.45f + 0.2f * u;
			p[6] = -0.1f;
			p[8] = 0.05f;
			return p;
		}
		if (hit + tau >= stab - 2) {
			// It is coming down on them: both arms up over the face, the knees up, the head turned away.
			p[0] = 0.4f;
			p[1] = 0.35f;
			p[2] = -1.75f + 0.08f * shake;
			p[3] = -0.25f;
			p[4] = -1.7f + 0.08f * shake;
			p[5] = 0.25f;
			p[6] = -0.9f;
			p[8] = -0.75f;
			return p;
		}
		// Looking at it, over their own chest, never away.
		p[0] = 0.72f + 0.04f * shake;
		p[1] = 0.05f * shake;
		// One hand up at it, shaking; more when they cry out at it.
		boolean crying = (tau >= 26 && tau < 40) || (tau >= 56 && tau < 72) || (tau >= 100);
		p[2] = -1.0f + 0.07f * shake + (crying ? 0.22f * (float) Math.sin(t * 0.9) : 0.0f);
		p[3] = 0.12f;
		// The other arm and the legs: a shove at a time, away from it.
		int k = -1;
		for (int i = 0; i < pushes.length; i++) if (hit + tau >= pushes[i]) k = i;
		float u = k < 0 ? 1.0f : Math.min(1.0f, (hit + tau - pushes[k]) / 10.0f);
		float shove = (float) Math.sin(Math.PI * u);
		p[4] = 0.25f + 0.35f * shove;
		p[5] = -0.4f;
		boolean right = k % 2 == 0;
		p[6] = -0.15f - (right ? 0.65f * shove : 0.15f * shove);
		p[7] = 0.06f;
		p[8] = -0.15f - (right ? 0.15f * shove : 0.65f * shove);
		p[9] = -0.06f;
		return p;
	}


	/**
	 * Puts it at {@code at} this tick, turned to the camera at {@code eye}, its face (about
	 * {@code face} up) on it.
	 */
	private static void look(TestServerContext server, Vec3 at, Vec3 eye, double face) {
		float yaw = (float) Fear.yawBetween(at, eye);
		server.runOnServer(s -> {
			List<OccupantEntity> its = s.overworld().getEntitiesOfClass(OccupantEntity.class, AABB.ofSize(at, 16, 16, 16));
			if (its.isEmpty()) return;
			OccupantEntity thing = its.get(0);
			thing.setPos(at.x, at.y, at.z);
			thing.setYRot(yaw);
			thing.setYHeadRot(yaw);
			thing.setYBodyRot(yaw);
			thing.setXRot((float) -Math.toDegrees(Math.atan2(eye.y - (at.y + thing.getEyeHeight()), Math.hypot(eye.x - at.x, eye.z - at.z))));
		});
	}

	private static Vec3 horizontal(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}

	// ------------------------------------------------------------------ the places

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
		/** Whether they look down at their feet as they run, and how much longer this glance lasts. */
		boolean glancing = true;
		private int glance;
		private Way way;
		private final double eyeY;
		/** Where it is at each tick, the middle of it, or null while it is nowhere: see {@link #calm}. */
		private IntFunction<Vec3> it = t -> null;
		/** When something came at them (tick, where from, how hard). */
		private final List<double[]> frights = new ArrayList<>();

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

		/** Where it is at each tick (the middle of it), or null while it is not there. */
		void watching(IntFunction<Vec3> where) {
			it = where;
		}

		/**
		 * How steady the hands are at each tick, 1 for not at all: while it is in the picture they
		 * shake a fifth less (they are holding still, staring at it), easing in and out over a few
		 * ticks either side, so the shaking never changes all at once.
		 */
		private double[] calm(double groundY) {
			int n = poses.size();
			double[] in = new double[n];
			for (int i = 0; i < n; i++) {
				Vec3 at = it.apply(i);
				if (at == null) continue;
				double[] p = poses.get(i);
				double dx = at.x - p[0], dz = at.z - p[1], dy = at.y - (groundY + Cinematic.EYE + p[4]);
				double across = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - p[2]));
				double up = Math.abs(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) - p[3]);
				// Inside the upright frame (about 25 degrees either side, 40 up and down), and a margin for its size.
				if (across < 30.0 && up < 44.0) in[i] = 1.0;
			}
			double[] out = new double[n];
			for (int i = 0; i < n; i++) {
				double sum = 0;
				int k = 0;
				for (int j = Math.max(0, i - 4); j <= Math.min(n - 1, i + 4); j++, k++) sum += in[j];
				out[i] = 1.0 - 0.18 * sum / k;
			}
			return out;
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

		/** A point on the way {@code by} blocks on from here, {@code side} to the right of it, {@code up} above the eye. */
		Vec3 onWay(double by, double side, double up) {
			double s = way.along(x, z) + by;
			double[] p = way.point(s), q = way.point(s + 0.5);
			double dx = q[0] - p[0], dz = q[1] - p[1], l = Math.max(1.0e-6, Math.hypot(dx, dz));
			return new Vec3(p[0] - dz / l * side, eyeY + up, p[1] + dx / l * side);
		}

		/** Which way the way runs here, level. */
		Vec3 wayDir() {
			double s = way.along(x, z);
			double[] p = way.point(s), q = way.point(s + 1.0);
			Vec3 d = new Vec3(q[0] - p[0], 0, q[1] - p[1]);
			return d.lengthSqr() < 1.0e-9 ? new Vec3(0, 0, 1) : d.normalize();
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

		/**
		 * Looking hard at {@code at}, not moving, leaning in to it by {@code lean} over the whole of
		 * it, the hands shaking up towards {@code shake}: trying to make out what is there.
		 */
		void peer(int n, Vec3 at, double shake, double lean) {
			double r = Math.toRadians(yawTo(at));
			for (int i = 0; i < n; i++) {
				double u = smooth((i + 1.0) / n) - smooth((double) i / n);
				x += -Math.sin(r) * lean * u;
				z += Math.cos(r) * lean * u;
				step(0, 0.03);
				yaw = toward(yaw, yawTo(at), 0.25);
				pitch += (pitchTo(at) - pitch) * 0.25;
				tremble += (shake - tremble) * 0.08;
				lift += (-0.05 - lift) * 0.08;
				roll *= 0.9;
				push();
			}
		}

		/**
		 * A look back over one shoulder without stopping: walking on at {@code pace}, round to
		 * {@code at}, held a moment, and back to the way ahead.
		 */
		void glanceBack(int turn, int hold, int back, Vec3 at, double pace) {
			double side = Math.signum(Mth.wrapDegrees(yawTo(at) - ahead(4.0)));
			if (side == 0) side = 1;
			for (int i = 0; i < turn + hold + back; i++) {
				steer(6);
				step(pace, 0.03);
				double u = i < turn ? smooth((i + 1.0) / turn) : i < turn + hold ? 1.0 : 1.0 - smooth((i - turn - hold + 1.0) / back);
				double forward = ahead(4.0), there = yawTo(at);
				double round = Mth.wrapDegrees(there - forward);
				if (round * side < 0) round += 360.0 * side;
				yaw = forward + round * u;
				pitch = 6.0 * (1 - u) + pitchTo(at) * u;
				roll += (side * 5 * u - roll) * 0.3;
				tremble += ((i >= turn && i < turn + hold ? 0.9 : 0.3) - tremble) * 0.3;
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
			// Now and then a glance down at the ground in front of their feet.
			if (glancing && glance == 0 && speed > 0.2 && uneven.nextDouble() < 0.025) glance = 7;
			double want = glance > 0 ? 24.0 : 5.0 + speed * 10;
			if (glance > 0) glance--;
			pitch += (want - pitch) * 0.3;
			lift *= 0.8;
			tremble *= 0.9;
			roll *= 0.8;
			push();
		}

		/** A foot caught on a root: pitched forward, nearly down, and scrambling on. */
		void trip(int n) {
			mark("trip");
			for (int i = 0; i < n; i++) {
				double u = (double) i / n;
				double dip = Math.sin(Math.min(1, u * 1.6) * Math.PI);
				steer(10);
				step(0.08 + 0.14 * u, 0.12);
				pitch += (5 + 45 * dip - pitch) * 0.55;
				lift = -0.65 * dip;
				roll += (14 * dip - roll) * 0.5;
				yaw += 3 * Math.sin(i * 1.9) * dip;
				tremble += (1.8 - tremble) * 0.4;
				push();
			}
		}

		/** Over the shoulder while still running: round to {@code at}, held there, and back again. */
		void lookBack(int turn, int hold, int back, Vec3 at) {
			int total = turn + hold + back;
			// Over one shoulder, and that shoulder all the way: never round the other way halfway through.
			double side = Math.signum(Mth.wrapDegrees(yawTo(at) - (way != null ? ahead(4.0) : heading)));
			for (int i = 0; i < total; i++) {
				steer(10);
				step(SPRINT * 0.8, 0.04);
				double u = i < turn ? overshoot((i + 1.0) / turn) : i < turn + hold ? 1.0 : 1.0 - smooth((i - turn - hold + 1.0) / back);
				double forward = way != null ? ahead(4.0) : heading, there = yawTo(at);
				double round = Mth.wrapDegrees(there - forward);
				if (round * side < 0) round += 360.0 * side;
				yaw = forward + round * u;
				pitch = (5.0 + speed * 10) * (1 - u) + pitchTo(at) * u;
				roll += (side * 7 * u - roll) * 0.3;
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

		/**
		 * The poses on level ground at {@code groundY}, as a camcorder in someone's hand records them:
		 * where they look followed as a hand turns a camera, quick, a little past and back; the
		 * shake of the hand and the body under it never quite repeating, worse with fear and worse
		 * running; every footfall a jolt of its own, never two the same; setting off and pulling up
		 * felt in the hand; and when something comes at them, a flinch.
		 */
		double[][] flat(double groundY) {
			int n = poses.size();
			double[] calm = calm(groundY);
			// Where they look, turned as a hand turns a camera: quick to get going, a little past, back.
			double[] yawF = new double[n], pitchF = new double[n];
			double y = 0, yv = 0, pv = 0, pp = 0, prev = 0;
			for (int i = 0; i < n; i++) {
				double[] p = poses.get(i);
				double target = i == 0 ? p[2] : prev + Mth.wrapDegrees(p[2] - poses.get(i - 1)[2]);
				prev = target;
				if (i == 0 || p[11] == 0) {
					y = target;
					yv = 0;
					pp = p[3];
					pv = 0;
				} else {
					for (int k = 0; k < 4; k++) {
						yv += (LOOK * LOOK * (target - y) - 2 * 0.62 * LOOK * yv) * 0.25;
						y += yv * 0.25;
						pv += (LOOK * LOOK * (p[3] - pp) - 2 * 0.62 * LOOK * pv) * 0.25;
						pp += pv * 0.25;
					}
				}
				yawF[i] = y;
				pitchF[i] = pp;
			}
			// Setting off, the camera dips with the body leaning into it; pulling up short, it is
			// thrown back; a spring, so it comes back past and settles.
			double[] lean = new double[n];
			double l = 0, lv = 0;
			for (int i = 1; i < n; i++) {
				double[] p = poses.get(i);
				double a = p[11] == 0 || p[9] == 0 ? 0 : p[5] - poses.get(i - 1)[5];
				for (int k = 0; k < 2; k++) {
					lv += (-0.35 * l - 0.8 * lv + 16.0 * a) * 0.5;
					l += lv * 0.5;
				}
				lean[i] = p[11] == 0 ? 0 : l;
			}
			// The hand: slow wander and quick shake, each a noise that never repeats (yaw, pitch, roll).
			// Each clip a hand of its own, from where it starts.
			java.util.Random rng = new java.util.Random(7L + (n == 0 ? 0L : Math.round(poses.get(0)[0] * 13 + poses.get(0)[1] * 7)));
			double[][] wander = {noise(rng, n, 9.0), noise(rng, n, 9.0), noise(rng, n, 12.0)};
			double[][] shake = {noise(rng, n, 1.3), noise(rng, n, 1.3), noise(rng, n, 1.6)};
			// Every footfall a jolt of its own: down, and some way to one side, and over, as hard as the
			// foot came down, a spring through the arm that dies away in a few ticks.
			double[][] jolt = new double[n][4];
			double[] js = new double[4], jv = new double[4];
			for (int i = 0; i < n; i++) {
				double[] p = poses.get(i);
				boolean standing = p[9] > 0 && p[11] > 0;
				double run = standing ? Mth.clamp(p[5] / SPRINT, 0, 1) : 0;
				if (i > 0 && standing && p[5] > 0.03 && Math.floor(poses.get(i - 1)[7] / Math.PI) != Math.floor(p[7] / Math.PI)) {
					double hard = p[10] * (0.35 + 0.65 * run) * calm[i];
					jv[0] += (2.0 + 4.5 * run) * hard * (0.7 + 0.6 * rng.nextDouble());              // pitch, down
					jv[1] += (rng.nextDouble() - 0.5) * (2.5 + 5.0 * run) * hard;                      // yaw
					jv[2] += (rng.nextDouble() - 0.5) * (3.0 + 6.0 * run) * hard;                      // roll
					jv[3] -= (0.03 + 0.07 * run) * hard * (0.7 + 0.6 * rng.nextDouble());              // height
				}
				for (int k = 0; k < 4; k++) {
					jv[k] += -0.45 * js[k] - 0.55 * jv[k];
					js[k] += jv[k];
					jolt[i][k] = js[k];
				}
			}
			double[][] out = new double[n][];
			for (int i = 0; i < n; i++) {
				double[] o = finish(i, groundY + Cinematic.EYE, calm[i], yawF[i], pitchF[i] + lean[i],
						new double[]{wander[0][i], wander[1][i], wander[2][i]}, new double[]{shake[0][i], shake[1][i], shake[2][i]}, jolt[i]);
				// Inertia moves the head with the lean: forward over the feet as it tips down.
				double r = Math.toRadians(o[3]);
				o[0] += -Math.sin(r) * lean[i] * 0.012;
				o[2] += Math.cos(r) * lean[i] * 0.012;
				startle(i, o);
				out[i] = o;
			}
			return out;
		}

		/**
		 * A noise that never repeats, smoothed over about {@code ticks} ticks, from -1 to 1 or so:
		 * white noise put through two smoothings, and scaled back up to the size it started.
		 */
		private static double[] noise(java.util.Random rng, int n, double ticks) {
			double[] v = new double[n];
			double a = 0, b = 0, k = 1.0 / Math.max(1.0, ticks);
			for (int i = -40; i < n; i++) {
				a += (rng.nextGaussian() - a) * k;
				b += (a - b) * k;
				if (i >= 0) v[i] = b;
			}
			double sum = 0;
			for (double d : v) sum += d * d;
			double rms = Math.sqrt(sum / Math.max(1, n));
			if (rms > 1.0e-9) for (int i = 0; i < n; i++) v[i] /= rms;
			return v;
		}

		/** How quickly the camera follows where they mean to look, a tick: about a tick behind. */
		private static final double LOOK = 1.8;

		/**
		 * The startle: for each fright, a jerk of the head away from it and up, the whole body
		 * dropping a little, in a tick or two, and then they have hold of themselves again.
		 */
		private void startle(int i, double[] o) {
			for (double[] fl : frights) {
				double k = i - fl[0];
				if (k < 0 || k > 14) continue;
				double pulse = (k / 1.5) * Math.exp(1 - k / 1.5);
				double rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-(fl[1] - o[0]), fl[3] - o[2])) - o[3]);
				double away = Math.abs(rel) < 4 ? (((int) fl[0]) % 2 == 0 ? 1 : -1) : -Math.signum(rel);
				o[3] += away * 3.2 * fl[4] * pulse;
				o[4] += -2.4 * fl[4] * pulse;
				o[5] += away * 2.6 * fl[4] * pulse;
				o[1] += -0.08 * fl[4] * pulse;
			}
		}

		/** Something comes at them at tick {@code tick}, from {@code from}: they flinch, as hard as {@code strength}. */
		void flinchAt(int tick, Vec3 from, double strength) {
			frights.add(new double[]{tick, from.x, from.y, from.z, strength});
		}

		/**
		 * x, y, z, yaw, pitch, roll for tick {@code i}, looking along {@code yw}, {@code pt}: the body
		 * carrying the camera and the hand holding it put in, the hand's own shake scaled by
		 * {@code calm}; {@code wander}, {@code shake} the hand's noises, {@code jolt} the footfalls'.
		 */
		private double[] finish(int i, double base, double calm, double yw, double pt, double[] wander, double[] shake, double[] jolt) {
			double[] p = poses.get(i);
			double px = p[0], pz = p[1], lf = p[4], sp = p[5], tr = p[6], ph = p[7], rl = p[8];
			// Lying on the ground, the camera is only where it fell.
			if (p[11] == 0) return new double[]{px, base + lf, pz, p[2], p[3], rl};
			boolean standing = p[9] > 0;
			double run = standing ? Mth.clamp(sp / SPRINT, 0, 1) : 0;
			double moving = standing && sp > 0.01 ? 1 : 0;
			// The body under the camera: up and down a little with the stride, side to side over
			// whichever foot is down; never exactly the same twice.
			double rise = (Math.abs(Math.sin(ph)) - 0.55) * (0.02 + 0.06 * run) * moving * (0.75 + 0.25 * wander[1]);
			double over = Math.sin(ph - 0.35) * (0.015 + 0.04 * run) * moving;
			// Breathing: standing, the camera rises and falls with it.
			double breath = Math.sin(i * 0.3 + 0.4 * wander[0]) * (standing ? 0.012 * (1 - run) : 0.03);
			// The hand: it wanders, slowly, more when they are moving and more when they are afraid,
			// and it shakes, quickly, with fear; while it is in the picture they hold it a little stiller.
			double fear = 0.25 + tr;
			double drift = calm * (0.5 + 1.4 * run + 0.3 * tr);
			double quick = calm * (0.12 + 0.55 * fear + 0.35 * run);
			double yawOut = yw + wander[0] * drift * 1.2 + shake[0] * quick + jolt[1] + 1.6 * run * Math.sin(ph) * calm;
			double pitchOut = pt + wander[1] * drift * 0.8 + shake[1] * quick * 0.8 + jolt[0] + breath * 9.0;
			// The roll: the hand never keeps the horizon level, leaning into a turn, rocking as they run.
			double turned = i > 0 ? Mth.wrapDegrees(p[2] - poses.get(i - 1)[2]) : 0;
			double rollOut = rl + wander[2] * calm * (0.9 + 2.6 * run) + shake[2] * quick * 0.9 + jolt[2]
					- Mth.clamp(turned * 0.3, -6, 6);
			double r = Math.toRadians(yw);
			double sx = Math.cos(r) * over, sz = Math.sin(r) * over;
			return new double[]{px + sx, base + rise + lf + breath + jolt[3], pz + sz, yawOut, Mth.clamp(pitchOut, -85, 85), rollOut};
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
