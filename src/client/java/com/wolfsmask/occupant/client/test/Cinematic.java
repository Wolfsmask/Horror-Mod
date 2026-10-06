package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.events.HallwayEvent;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.world.House;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Stills for the mod's page: no HUD, full HD, the light chosen, the camera placed, and it standing
 * where it reads best. Everything in them is the real game and the real mod (the places are the
 * ones that generate, the creature is the real one), only arranged, the way a film is.
 * <p>
 * Not a check: if any of this goes wrong it is logged and the run carries on.
 */
final class Cinematic {
	private static final String ALL = "@e[type=occupant:occupant]";
	private static final int KEY_F1 = 290;
	/** How far above where a player is put their eyes are: the camera. */
	private static final double EYE = 1.62;

	/** A shot, worked out on the server: where it stands, where the camera is, what it looks at. */
	private record Shot(BlockPos it, Vec3 eye, Vec3 look) {
	}

	private Cinematic() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods, BlockPos spawn) {
		TestServerContext server = game.getServer();
		try {
			server.runCommand("gamemode spectator @p");
			server.runCommand("weather clear");
			hud(context, false);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(20);

			treeline(context, game, woods);
			// Fresh ground for each, away from the house the photos before these built.
			BlockPos[] centres = {woods[0].offset(-110, 0, 80), woods[1].offset(90, 0, -70), woods[0].offset(200, 0, 40),
					woods[1].offset(-40, 0, 200), spawn.offset(0, 0, 120), spawn};
			int next = 0;
			BlockPos ruin = null, camp = null, graves = null, village = null;
			for (BlockPos c : centres) {
				BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), c));
				if (floor == null) continue;
				switch (next++) {
					case 0 -> village = floor;
					case 1 -> ruin = floor;
					case 2 -> camp = floor;
					case 3 -> graves = floor;
					default -> {
					}
				}
				if (next > 3) break;
			}
			if (village != null) village(context, game, village);
			if (ruin != null) ruin(context, game, ruin);
			if (camp != null) camp(context, game, camp);
			if (graves != null) graves(context, game, graves);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] cinematic stills stopped early", e);
		} finally {
			server.runCommand("kill " + ALL);
			hud(context, true);
			server.runCommand("gamemode survival @p");
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** Sunset in the wood: it, between the trunks, against the last of the light, looking back. */
	private static void treeline(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos it = server.computeOnServer(s -> ForestGallery.standSpot(s.overworld(), woods[0], 7));
		if (it == null) return;
		// The camera to the east of it, looking west into the sunset; the trees between cleared.
		BlockPos cam = server.computeOnServer(s -> new BlockPos(it.getX() + 13, surface(s.overworld(), it.getX() + 13, it.getZ()), it.getZ()));
		clearView(server, it.offset(-1, 0, -3), cam.offset(1, 0, 3), Math.min(it.getY(), cam.getY()) + 1, 14);
		Vec3 eye = new Vec3(cam.getX() + 0.5, cam.getY() + 1.6 + EYE, cam.getZ() + 0.5);
		camera(context, game, eye, new Vec3(it.getX() + 0.5, it.getY() + 3.0, it.getZ() + 0.5), 12700);
		place(server, it, eye);
		context.waitTicks(20);
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~9 ~");      // it in the left third, as if half-turned to it
		still(context, game, "cinematic-treeline");

		// And close: its face, from a little below, with the last of the sky behind it.
		Vec3 near = server.computeOnServer(s -> inAir(s.overworld(), new Vec3(it.getX() + 4.5, it.getY() + 2.6, it.getZ() + 0.5)));
		take(context, game, new Shot(it, near, new Vec3(it.getX() + 0.5, it.getY() + 3.4, it.getZ() + 0.5)), 12600, "cinematic-face");
	}

	/** The village from above at dusk, and it, small, standing in the path between the houses. */
	private static void village(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		Shot shot = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			fellTrees(level, floor, 34);
			House.buildPlaceForTest("village", level, floor, level.getRandom());
			BlockPos it = standNear(level, floor, -1, -9);
			Vec3 door = rel(floor, -1.5, 2.5, -6.5);
			Vec3 well = rel(floor, -1.5, 5.5, -12.5);
			Vec3 eye = findCamera(level, player(s), it, 0.6, -1.0, new double[]{16, 20, 24}, 2.0, floor.getY() + 10.0, door, well);
			return new Shot(it, eye, door.lerp(chest(it), 0.4));
		});
		take(context, game, shot, 13000, "cinematic-village");

		// And inside the house, at dusk: the real event, looking towards the dark hallway.
		server.runCommand("kill " + ALL);
		server.runCommand("time set 13000");
		server.runCommand("gamemode survival @p");
		BlockPos inside = House.local(floor, Rotation.NONE, 4, 1, 1);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0", inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5));
		context.waitTicks(10);
		boolean started = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Director.get().data(p).setAct(3);
			return Director.get().trigger(p, HallwayEvent.ID, true) == Director.TriggerResult.STARTED;
		});
		if (started) {
			waitForIt(context);
			float[] turn = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				var near = s.overworld().getEntitiesOfClass(OccupantEntity.class, p.getBoundingBox().inflate(40));
				if (near.isEmpty()) return null;
				OccupantEntity e = near.get(0);
				double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
				double dy = e.getY() + 2.6 - p.getEyeY();
				return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)) + 9f, (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
			});
			if (turn != null) server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s ~ ~ ~ %.1f %.1f", turn[0], turn[1]));
			context.waitTicks(6);
			still(context, game, "cinematic-hallway");
		}
		server.runOnServer(s -> Director.get().stopCurrent(s.getPlayerList().getPlayers().get(0)));
		server.runCommand("gamemode spectator @p");
	}

	/** The ruined keep at sunset, and it, standing outside the gate as if it has been waiting. */
	private static void ruin(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		Shot shot = game.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			fellTrees(level, floor, 28);
			House.buildPlaceForTest("ruin", level, floor, level.getRandom());
			BlockPos it = standNear(level, floor, 2, -10);
			Vec3 gate = rel(floor, 0.5, 2.0, -6.6);
			Vec3 yard = rel(floor, 0.5, 6.5, 0.5);
			Vec3 eye = findCamera(level, player(s), it, -0.6, -1.0, new double[]{12, 15, 18}, 2.4, 0, gate, yard);
			return new Shot(it, eye, rel(floor, 0.5, 3.0, -4.0).lerp(chest(it), 0.45));
		});
		take(context, game, shot, 12500, "cinematic-ruin");
	}

	/** A camp at night, its fire lit again, and it just past the fire, where the light still reaches. */
	private static void camp(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		Shot shot = game.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			fellTrees(level, floor, 22);
			House.buildPlaceForTest("camp", level, floor, level.getRandom());
			light(level, floor, 2, Blocks.CAMPFIRE.defaultBlockState());
			BlockPos it = standNear(level, floor, 1, 5);
			Vec3 fire = rel(floor, 0.5, 1.9, 0.5);
			Vec3 chest = rel(floor, -2.5, 1.9, 2.5);
			Vec3 eye = findCamera(level, player(s), it, -0.15, -1.0, new double[]{13, 15, 17}, 2.2, 0, fire, chest);
			return new Shot(it, eye, fire.lerp(chest(it), 0.5));
		});
		take(context, game, shot, 18000, "cinematic-camp");
	}

	/** The graveyard at nightfall, candles lit, and it standing past the last row. */
	private static void graves(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		Shot shot = game.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			fellTrees(level, floor, 24);
			House.buildPlaceForTest("graves", level, floor, level.getRandom());
			light(level, floor, 7, Blocks.CANDLE.defaultBlockState());
			BlockPos it = standNear(level, floor, 1, 7);
			Vec3 middle = rel(floor, 0.5, 2.2, -0.5);
			Vec3 gate = rel(floor, 0.5, 1.8, -3.5);
			Vec3 eye = findCamera(level, player(s), it, -0.2, -1.0, new double[]{17, 19, 22}, 2.8, 0, middle, gate);
			return new Shot(it, eye, rel(floor, 0.5, 1.5, 0.5).lerp(chest(it), 0.5));
		});
		take(context, game, shot, 13800, "cinematic-graves");
	}

	// ---------------------------------------------------------------- the set (server side)

	/** Wood, leaves and the like that grew there: never anything anyone built. */
	private static boolean grown(BlockState state) {
		if (state.is(BlockTags.LEAVES)) return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
		if (state.is(Blocks.VINE) || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST)) return true;
		if (state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)) return true;
		return state.is(BlockTags.LOGS) && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_");
	}

	/** Where something would stand at a column: on the ground, under any tree, out of any water. */
	private static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
		while (p.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(p);
			if (!state.getFluidState().isEmpty()) break;
			if (!grown(state) && !state.getCollisionShape(level, p).isEmpty()) break;
			p.move(0, -1, 0);
		}
		return p.getY() + 1;
	}

	/**
	 * Fells the trees in a circle, whole, so a shot has room and nothing is left floating: every
	 * trunk standing inside it, its branches, and the leaves round it. Done before anything is built.
	 */
	private static void fellTrees(ServerLevel level, BlockPos centre, int radius) {
		int r = radius + 6, size = 2 * r + 1;
		int[] base = new int[size * size];
		boolean[] trunk = new boolean[size * size];
		boolean[] near = new boolean[size * size];
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int i = (dx + r) * size + dz + r;
				base[i] = ground(level, centre.getX() + dx, centre.getZ() + dz);
				trunk[i] = dx * dx + dz * dz <= radius * radius
						&& grown(level.getBlockState(new BlockPos(centre.getX() + dx, base[i], centre.getZ() + dz)));
			}
		}
		for (int i = 0; i < trunk.length; i++) {
			if (!trunk[i]) continue;
			int tx = i / size, tz = i % size;
			for (int x = Math.max(0, tx - 5); x <= Math.min(size - 1, tx + 5); x++) {
				for (int z = Math.max(0, tz - 5); z <= Math.min(size - 1, tz + 5); z++) {
					if ((x - tx) * (x - tx) + (z - tz) * (z - tz) <= 30) near[x * size + z] = true;
				}
			}
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int felled = 0;
		for (int i = 0; i < near.length; i++) {
			if (!near[i]) continue;
			int x = centre.getX() + i / size - r, z = centre.getZ() + i % size - r;
			// A column with another tree's trunk in it keeps its wood; only leaves come off it.
			boolean otherTrunk = !trunk[i] && grown(level.getBlockState(p.set(x, base[i], z)));
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			for (int y = base[i]; y <= top + 1; y++) {
				BlockState state = level.getBlockState(p.set(x, y, z));
				if (!grown(state)) continue;
				if (otherTrunk && state.is(BlockTags.LOGS)) continue;
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				felled++;
			}
		}
		Occupant.LOGGER.info("[client-gametest] felled {} blocks of trees round {}", felled, centre);
	}

	/** Where it can stand nearest to (x, z) from {@code floor}. */
	private static BlockPos standNear(ServerLevel level, BlockPos floor, int x, int z) {
		for (int ring = 0; ring <= 4; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int cx = floor.getX() + x + dx, cz = floor.getZ() + z + dz;
					BlockPos spot = Spots.groundNear(level, cx, ground(level, cx, cz), cz, 3);
					if (spot != null) return spot;
				}
			}
		}
		return new BlockPos(floor.getX() + x, ground(level, floor.getX() + x, floor.getZ() + z), floor.getZ() + z);
	}

	/**
	 * Where the camera goes: first {@code dists[0]} blocks from it towards {@code (dx, dz)}, then
	 * further back, then further round either side, until all of it and every one of {@code see}
	 * can be seen from there with nothing in the way. At least {@code low} above the ground there,
	 * and never below {@code minY}. Failing that, wherever the most of it can be seen.
	 */
	private static Vec3 findCamera(ServerLevel level, Entity viewer, BlockPos it, double dx, double dz, double[] dists,
								   double low, double minY, Vec3... see) {
		double toward = Math.atan2(dz, dx);
		Vec3 feet = Vec3.atBottomCenterOf(it);
		Vec3 best = null;
		int bestScore = Integer.MIN_VALUE, tried = 0;
		for (int turn : new int[]{0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180}) {
			double a = toward + Math.toRadians(turn);
			for (double d : dists) {
				double x = feet.x + Math.cos(a) * d, z = feet.z + Math.sin(a) * d;
				Vec3 eye = new Vec3(x, Math.max(ground(level, Mth.floor(x), Mth.floor(z)) + low, minY), z);
				BlockPos at = BlockPos.containing(eye);
				if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) continue;
				int body = 0, place = 0;
				for (double h : new double[]{0.5, 1.5, 2.5, 3.5}) if (clear(level, viewer, eye, feet.add(0, h, 0))) body++;
				for (Vec3 v : see) if (clear(level, viewer, eye, v)) place++;
				if (body == 4 && place == see.length) {
					Occupant.LOGGER.info("[client-gametest] camera {} blocks out, turned {}: everything in view", d, turn);
					return eye;
				}
				int score = (body >= 3 ? 1000 : 0) + body * 100 + place * 60 - tried++;
				if (score > bestScore) {
					bestScore = score;
					best = eye;
				}
			}
		}
		Occupant.LOGGER.info("[client-gametest] no camera sees it all; the best scores {}", bestScore);
		return best != null ? best : feet.add(Math.cos(toward) * dists[0], low + 2.0, Math.sin(toward) * dists[0]);
	}

	private static boolean clear(ServerLevel level, Entity viewer, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
	}

	/** Up out of the ground, if {@code eye} is in it. */
	private static Vec3 inAir(ServerLevel level, Vec3 eye) {
		for (int up = 0; up < 6; up++) {
			BlockPos at = BlockPos.containing(eye.add(0, up, 0));
			if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return eye.add(0, up, 0);
		}
		return eye;
	}

	private static Vec3 rel(BlockPos floor, double x, double y, double z) {
		return new Vec3(floor.getX() + x, floor.getY() + y, floor.getZ() + z);
	}

	private static Vec3 chest(BlockPos feet) {
		return Vec3.atBottomCenterOf(feet).add(0, 2.6, 0);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/**
	 * Clears the trees out of a shot: leaves, vines and natural trunks only, from {@code fromY} up
	 * {@code height} blocks, so nothing anyone built (planks, stone, stripped logs) is ever touched.
	 */
	private static void clearView(TestServerContext server, BlockPos a, BlockPos b, int fromY, int height) {
		int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
		int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
		for (String what : new String[]{"#minecraft:leaves", "minecraft:vine", "minecraft:oak_log", "minecraft:birch_log",
				"minecraft:spruce_log", "minecraft:dark_oak_log", "minecraft:jungle_log", "minecraft:acacia_log"}) {
			// In slices, to stay inside what one fill may change.
			for (int x = x0; x <= x1; x += 12) {
				server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air replace %s",
						x, fromY, z0, Math.min(x + 11, x1), fromY + height, z1, what));
			}
		}
	}

	/**
	 * The HUD on or off. The option has had more than one name, so it is found by what it is
	 * called; failing that, F1, as a player would.
	 */
	private static void hud(ClientGameTestContext context, boolean shown) {
		boolean set = context.computeOnClient(mc -> {
			for (java.lang.reflect.Field f : mc.options.getClass().getFields()) {
				String n = f.getName().toLowerCase(Locale.ROOT);
				if (f.getType() == boolean.class && n.contains("hide") && n.contains("gui")) {
					try {
						f.setBoolean(mc.options, !shown);
						return true;
					} catch (IllegalAccessException e) {
						return false;
					}
				}
			}
			return false;
		});
		if (!set) context.getInput().pressKey(KEY_F1);
	}

	/** Puts it there, facing the camera, the way /occupant here does. */
	private static void place(TestServerContext server, BlockPos feet, Vec3 facing) {
		server.runCommand("kill " + ALL);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			OccupantEntity e = ModEntities.OCCUPANT.create(level, EntitySpawnReason.COMMAND);
			if (e == null) return;
			e.standAlone(player(s));
			Vec3 at = Vec3.atBottomCenterOf(feet);
			float yaw = Sight.yawBetween(at, facing);
			e.snapTo(at.x, at.y, at.z, yaw, 0.0f);
			e.setYHeadRot(yaw);
			e.setYBodyRot(yaw);
			e.setMode(OccupantEntity.Mode.STARE);
			e.setForm(OccupantEntity.Form.REVEALED);
			level.addFreshEntity(e);
		});
	}

	/**
	 * The camera goes first, then it: put there before the camera arrives, it would be too far from
	 * anyone to stay.
	 */
	private static void take(ClientGameTestContext context, TestSingleplayerContext game, Shot shot, int time, String name) {
		camera(context, game, shot.eye(), shot.look(), time);
		place(game.getServer(), shot.it(), shot.eye());
		context.waitTicks(20);
		Occupant.LOGGER.info("[client-gametest] {}: it at {}, camera at {}, {} in sight", name, shot.it(), shot.eye(),
				context.computeOnClient(OccupantClientGameTest::seen));
		still(context, game, name);
	}

	private static void camera(ClientGameTestContext context, TestSingleplayerContext game, Vec3 eye, Vec3 look, int time) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand("time set " + time);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f facing %.2f %.2f %.2f",
				eye.x, eye.y - EYE, eye.z, look.x, look.y, look.z));
		context.waitTicks(30);
		TestCompat.waitForWorld(game);
		context.waitTicks(30);
	}

	private static void still(ClientGameTestContext context, TestSingleplayerContext game, String name) {
		context.waitTicks(10);
		OccupantClientGameTest.shoot(context, name);
	}

	/** Lights every unlit {@code kind} (a campfire, candles) within {@code reach} of {@code centre}. */
	private static void light(ServerLevel level, BlockPos centre, int reach, BlockState kind) {
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-reach, -3, -reach), centre.offset(reach, 4, reach))) {
			BlockState state = level.getBlockState(p);
			if (state.is(kind.getBlock()) && state.hasProperty(BlockStateProperties.LIT)) {
				level.setBlock(p, state.setValue(BlockStateProperties.LIT, true), 3);
			}
		}
	}

	private static int surface(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static void waitForIt(ClientGameTestContext context) {
		for (int i = 0; i < 60 && context.computeOnClient(OccupantClientGameTest::seen) == 0; i++) context.waitTick();
	}
}
