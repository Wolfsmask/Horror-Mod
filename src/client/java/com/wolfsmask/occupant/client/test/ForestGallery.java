package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.events.HallwayEvent;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.world.HouseFeature;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Photographs of it the way a player actually meets it: a real, normally generated world, in a
 * real forest, from the player's own eyes with the HUD up and a few ordinary things on the
 * hotbar. The test only stands the player somewhere and starts the mod's own events; where it
 * appears, and how much of it can be seen, is the mod's choice, exactly as in a real game. The
 * one thing built is an abandoned house, the same one that generates in the world, in a clearing.
 * <p>
 * Not a check: if any of this goes wrong it is logged and the run carries on.
 */
final class ForestGallery {
	private static final String ALL = "@e[type=occupant:occupant]";

	private ForestGallery() {
	}

	static void run(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed("the occupant"))
				.create()) {
			game.getClientLevel().waitForChunksRender();
			TestServerContext server = game.getServer();
			server.runCommand("gamerule sendCommandFeedback false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("difficulty peaceful");
			server.runCommand("weather clear");
			// What anyone has on them an hour in.
			server.runCommand("item replace entity @p hotbar.0 with minecraft:iron_axe");
			server.runCommand("item replace entity @p hotbar.1 with minecraft:torch 23");
			server.runCommand("item replace entity @p hotbar.2 with minecraft:bread 9");
			server.runCommand("item replace entity @p hotbar.3 with minecraft:dark_oak_log 41");
			server.runCommand("item replace entity @p hotbar.4 with minecraft:cobblestone 64");
			server.runCommand("item replace entity @p hotbar.6 with minecraft:crafting_table");

			BlockPos spawn = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			BlockPos forest = server.computeOnServer(s -> findForest(s.overworld(),
					s.getPlayerList().getPlayers().get(0).blockPosition(), true));
			// An ordinary forest as well, for any shot the dark forest is too thick to get.
			BlockPos other = server.computeOnServer(s -> findForest(s.overworld(),
					s.getPlayerList().getPlayers().get(0).blockPosition(), false));
			if (forest == null) forest = other;
			if (other == null) other = forest;
			if (forest == null) {
				Occupant.LOGGER.warn("[client-gametest] no forest found for the gallery");
				return;
			}
			Occupant.LOGGER.info("[client-gametest] gallery forests at {} and {}", forest, other);
			BlockPos[] woods = {forest, other};

			// What the real events do, from a spot in the trees: the mod chooses where it stands.
			event(context, game, woods, 1, "watcher", "sighting-dusk", 12900, 4);
			event(context, game, woods, 2, "watcher", "sighting-night", 18000, 3);
			event(context, game, woods, 3, "distant", "far-off-dusk", 12600, 2);

			// The abandoned house, in a clearing in the same woods, or failing that the open country
			// where the world began.
			house(context, game, new BlockPos[]{woods[0], woods[1], spawn});
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] forest gallery stopped early", e);
		}
	}

	/**
	 * Stands the player somewhere in the trees and lets the real event decide where it goes. Turns
	 * the player a quarter at a time until the event finds a place, as a player looking about would.
	 */
	private static void event(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods,
							  int n, String event, String name, int time, int act) {
		try {
			TestServerContext server = game.getServer();
			BlockPos stand = null;
			for (BlockPos forest : woods) {
				stand = server.computeOnServer(s -> standSpot(s.overworld(), forest, n));
				if (stand != null) break;
			}
			if (stand == null) return;
			server.runCommand("weather clear");
			server.runCommand("time set " + time);
			BlockPos from = stand;
			int[] yaws = server.computeOnServer(s -> openestYaws(s.overworld(), from));
			for (int yaw : yaws) {
				stop(server);
				server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f %d 0",
						stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw));
				context.waitTicks(10);
				game.getClientLevel().waitForChunksRender();
				boolean started = server.computeOnServer(s -> {
					ServerPlayer player = s.getPlayerList().getPlayers().get(0);
					Director.get().data(player).setAct(act);
					return Director.get().trigger(player, event, true) == Director.TriggerResult.STARTED;
				});
				if (!started) continue;
				// Having caught it out of the corner of an eye, turning towards it, not quite on it.
				turnTowardsIt(server, 9.0f);
				waitForIt(context);
				context.waitTicks(6);
				OccupantClientGameTest.shoot(context, "occupant-photo-" + n + "-" + name);
				stop(server);
				return;
			}
			Occupant.LOGGER.warn("[client-gametest] {} found nowhere to happen for photo {}", event, name);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] photo {} failed", name, e);
		}
	}

	/** The house: from outside, then walking in, by day and at dusk, with the real hallway event. */
	private static void house(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		try {
			TestServerContext server = game.getServer();
			BlockPos floor = null;
			for (BlockPos forest : woods) {
				floor = server.computeOnServer(s -> clearing(s.overworld(), forest));
				if (floor != null) break;
			}
			if (floor == null) {
				Occupant.LOGGER.warn("[client-gametest] no clearing for the house photos");
				return;
			}
			BlockPos at = floor;
			server.runOnServer(s -> HouseFeature.build(s.overworld(), at, Rotation.NONE, s.overworld().getRandom()));
			BlockPos front = HouseFeature.local(floor, Rotation.NONE, 4, 1, -8);
			BlockPos outside = server.computeOnServer(s -> {
				Integer y = ground(s.overworld(), front.getX(), front.getZ(), 2);
				return y == null ? front : new BlockPos(front.getX(), y, front.getZ());
			});
			BlockPos inside = HouseFeature.local(floor, Rotation.NONE, 4, 1, 1);

			server.runCommand("weather clear");
			server.runCommand("time set 12700");
			stop(server);
			teleport(server, outside, 0.0f, -4.0f);
			context.waitTicks(10);
			game.getClientLevel().waitForChunksRender();
			context.waitTicks(10);
			OccupantClientGameTest.shoot(context, "occupant-photo-4-the-house");

			String[][] visits = {{"6000", "5", "house-day"}, {"13000", "6", "house-dusk"}};
			for (String[] v : visits) {
				server.runCommand("time set " + v[0]);
				stop(server);
				teleport(server, inside, 0.0f, 0.0f);
				context.waitTicks(10);
				boolean started = server.computeOnServer(s -> Director.get().trigger(
						s.getPlayerList().getPlayers().get(0), HallwayEvent.ID, true) == Director.TriggerResult.STARTED);
				if (!started) {
					Occupant.LOGGER.warn("[client-gametest] the hallway event found nowhere for {}", v[2]);
					continue;
				}
				waitForIt(context);
				context.waitTicks(4);
				OccupantClientGameTest.shoot(context, "occupant-photo-" + v[1] + "-" + v[2]);
				// And turning towards the hallway, where it is.
				turnTowardsIt(server, 6.0f);
				context.waitTicks(3);
				OccupantClientGameTest.shoot(context, "occupant-photo-" + v[1] + "-" + v[2] + "-turned");
			}
			stop(server);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] house photos failed", e);
		}
	}

	/** Turns the player towards it, as someone who caught it out of the corner of their eye would. */
	private static void turnTowardsIt(TestServerContext server, float off) {
		float[] turn = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().get(0);
			var near = s.overworld().getEntitiesOfClass(OccupantEntity.class, player.getBoundingBox().inflate(120));
			if (near.isEmpty()) return null;
			OccupantEntity e = near.get(0);
			double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz);
			double dy = e.getY() + Math.min(3.0, flat * 0.3) - player.getEyeY();
			return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)) + off,
					(float) -Math.toDegrees(Math.atan2(dy, flat))};
		});
		if (turn != null) server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s ~ ~ ~ %.1f %.1f", turn[0], turn[1]));
	}

	private static void teleport(TestServerContext server, BlockPos feet, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f %.1f %.1f",
				feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, pitch));
	}

	private static void stop(TestServerContext server) {
		server.runOnServer(s -> Director.get().stopCurrent(s.getPlayerList().getPlayers().get(0)));
		server.runCommand("kill " + ALL);
	}

	private static void waitForIt(ClientGameTestContext context) {
		for (int i = 0; i < 60 && context.computeOnClient(OccupantClientGameTest::seen) == 0; i++) {
			context.waitTick();
		}
	}

	/** Directions from here with the longest clear view at eye height, best first. */
	private static int[] openestYaws(ServerLevel level, BlockPos feet) {
		Integer[] yaws = new Integer[24];
		double[] reach = new double[360];
		for (int i = 0; i < 24; i++) {
			int yaw = i * 15;
			yaws[i] = yaw;
			double r = Math.toRadians(yaw);
			double dx = -Math.sin(r), dz = Math.cos(r);
			double d = 0.0;
			while (d < 48.0) {
				BlockPos p = BlockPos.containing(feet.getX() + 0.5 + dx * (d + 1.0), feet.getY() + 1.6, feet.getZ() + 0.5 + dz * (d + 1.0));
				if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) break;
				d += 1.0;
			}
			reach[yaw] = d;
		}
		java.util.Arrays.sort(yaws, (a, b) -> Double.compare(reach[b], reach[a]));
		int[] out = new int[4];
		for (int i = 0; i < 4; i++) out[i] = yaws[i * 2];
		return out;
	}

	/** Forest floor with room to stand, near the middle of the wood. */
	private static BlockPos standSpot(ServerLevel level, BlockPos forest, int salt) {
		for (int ring = 0; ring <= 48; ring += 3) {
			int steps = Math.max(1, ring * 2);
			for (int k = 0; k < steps; k++) {
				double a = 2.0 * Math.PI * (k + 0.37 * salt) / steps;
				int x = forest.getX() + (int) Math.round(Math.cos(a) * ring) + salt * 11;
				int z = forest.getZ() + (int) Math.round(Math.sin(a) * ring);
				Integer y = ground(level, x, z, 3);
				if (y != null) return new BlockPos(x, y, z);
			}
		}
		return null;
	}

	/**
	 * Somewhere flat enough, and clear enough of trunks, for the house: every column of its
	 * footprint (and a little round it) forest floor within a block of the same height.
	 */
	private static BlockPos clearing(ServerLevel level, BlockPos forest) {
		for (int ring = 0; ring <= 160; ring += 8) {
			int steps = Math.max(1, ring / 2);
			for (int k = 0; k < steps; k++) {
				double a = 2.0 * Math.PI * k / steps;
				int cx = forest.getX() + (int) Math.round(Math.cos(a) * ring);
				int cz = forest.getZ() + (int) Math.round(Math.sin(a) * ring);
				Integer cy = ground(level, cx, cz, 3);
				if (cy == null) continue;
				int good = 0, total = 0;
				for (int dx = -7; dx <= 6; dx++) {
					for (int dz = -12; dz <= 6; dz++) {
						total++;
						Integer y = ground(level, cx + dx, cz + dz, 2);
						if (y != null && Math.abs(y - cy) <= 2) good++;
					}
				}
				if (good >= total * 0.85) return new BlockPos(cx, cy - 1, cz);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- finding the spot (server side)

	private static BlockPos findForest(ServerLevel level, BlockPos from, boolean darkOnly) {
		Predicate<Holder<Biome>> dark = h -> h.is(Biomes.DARK_FOREST);
		Predicate<Holder<Biome>> any = h -> h.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
				|| h.is(Biomes.OLD_GROWTH_PINE_TAIGA) || h.is(Biomes.FOREST) || h.is(Biomes.BIRCH_FOREST)
				|| h.is(Biomes.OLD_GROWTH_BIRCH_FOREST);
		var found = darkOnly ? level.findClosestBiome3d(dark, from, 6400, 32, 64) : null;
		Predicate<Holder<Biome>> kind = dark;
		if (found == null) {
			found = level.findClosestBiome3d(any, from, 6400, 32, 64);
			kind = any;
		}
		if (found == null) return null;
		BlockPos edge = found.getFirst();
		// The closest point is the edge of it; walk on in, so it is trees in every direction.
		double dx = edge.getX() - from.getX(), dz = edge.getZ() - from.getZ();
		double len = Math.max(1.0, Math.hypot(dx, dz));
		for (int in = 64; in >= 0; in -= 16) {
			BlockPos p = new BlockPos((int) (edge.getX() + dx / len * in), 80, (int) (edge.getZ() + dz / len * in));
			if (kind.test(level.getBiome(p))) return p;
		}
		return edge;
	}

	/** Forest floor: what you would actually be standing on in a wood. */
	private static boolean isFloor(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL)
				|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.ROOTED_DIRT)
				|| state.is(Blocks.MYCELIUM) || state.is(Blocks.PALE_MOSS_BLOCK) || state.is(Blocks.SNOW_BLOCK);
	}

	private static int rejected;

	/** The ground to stand on in this column, under the canopy; null if it is not forest floor. */
	private static Integer ground(ServerLevel level, int x, int z, int headroom) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		BlockState floor = level.getBlockState(feet.below());
		if (!isFloor(floor)) {                                          // a trunk, a rock, water
			if (rejected++ < 6) {
				Occupant.LOGGER.info("[client-gametest] not floor at {} {} {}: {}", x, y - 1, z, floor);
			}
			return null;
		}
		for (int h = 0; h < headroom; h++) {
			BlockPos p = feet.above(h);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return null;
			if (!level.getFluidState(p).isEmpty()) return null;
		}
		return y;
	}
}
