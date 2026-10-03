package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Photographs of it the way a player actually meets it: a real, normally generated world, in a
 * real forest, from the player's own eyes with the HUD up and a few ordinary things on the
 * hotbar. Nothing in the scene is built; the test only finds a spot in the trees from which it
 * could actually be seen, stands the player there, and puts it there.
 * <p>
 * Not a check: if any of this goes wrong it is logged and the run carries on.
 */
final class ForestGallery {
	private static final String ALL = "@e[type=occupant:occupant]";

	private ForestGallery() {
	}

	/** Where the player stands and looks, and where it stands. */
	private record Shot(double px, double py, double pz, float yaw, float pitch, double ex, double ey, double ez, float facing) {
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

			scene(context, game, woods, 1, "forest-day", 6000, 17, "stare", false);
			scene(context, game, woods, 2, "forest-dusk", 12700, 22, "veiled", false);
			scene(context, game, woods, 3, "forest-night", 18000, 11, "stare", false);
			scene(context, game, woods, 4, "forest-night-close", 18000, 5.5, "loom", false);
			behindYou(context, game, woods);
			scene(context, game, woods, 7, "coming-through-the-trees", 13000, 15, "chase", false);
			scene(context, game, woods, 8, "rain", 12900, 16, "stare", true);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] forest gallery stopped early", e);
		}
	}

	private static void scene(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods,
							  int n, String name, int time, double distance, String pose, boolean rain) {
		try {
			TestServerContext server = game.getServer();
			Shot shot = null;
			for (BlockPos forest : woods) {
				shot = server.computeOnServer(s -> frame(s.overworld(), forest, distance, n));
				if (shot != null) break;
			}
			if (shot == null) {
				Occupant.LOGGER.warn("[client-gametest] no clear view for gallery shot {}", name);
				return;
			}
			server.runCommand("weather " + (rain ? "rain" : "clear"));
			server.runCommand("time set " + time);
			stage(context, game, shot, shot.yaw(), shot.pitch(), pose);
			context.waitTicks(pose.equals("chase") ? 8 : 20);
			OccupantClientGameTest.shoot(context, "occupant-photo-" + n + "-" + name);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] gallery shot {} failed", name, e);
		}
	}

	/** Facing away from it in the dark, then turning round to find it right there. */
	private static void behindYou(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		try {
			TestServerContext server = game.getServer();
			Shot shot = null;
			for (BlockPos forest : woods) {
				shot = server.computeOnServer(s -> frame(s.overworld(), forest, 3.5, 5));
				if (shot != null) break;
			}
			if (shot == null) return;
			server.runCommand("weather clear");
			server.runCommand("time set 18500");
			stage(context, game, shot, shot.yaw() + 180.0f, 5.0f, "loom");
			context.waitTicks(10);
			OccupantClientGameTest.shoot(context, "occupant-photo-5-nothing-there");
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f",
					shot.px(), shot.py(), shot.pz(), shot.yaw(), -24.0f));
			context.waitTicks(3);
			OccupantClientGameTest.shoot(context, "occupant-photo-6-turned-around");
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] gallery shot behind-you failed", e);
		}
	}

	/** Puts the player at the shot, looking the given way, and it where the shot says. */
	private static void stage(ClientGameTestContext context, TestSingleplayerContext game, Shot shot,
							  float yaw, float pitch, String pose) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f",
				shot.px(), shot.py(), shot.pz(), yaw, pitch));
		context.waitTicks(10);
		game.getClientLevel().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = s.getPlayerList().getPlayers().get(0);
			OccupantEntity e = ModEntities.OCCUPANT.create(level, EntitySpawnReason.COMMAND);
			if (e == null) return;
			e.standAlone(player);
			e.snapTo(shot.ex(), shot.ey(), shot.ez(), shot.facing(), 0.0f);
			e.setYHeadRot(shot.facing());
			e.setYBodyRot(shot.facing());
			e.setMode(switch (pose) {
				case "loom" -> OccupantEntity.Mode.AMBUSH;
				case "chase" -> OccupantEntity.Mode.CHASE;
				default -> OccupantEntity.Mode.STARE;
			});
			e.setForm("veiled".equals(pose) ? OccupantEntity.Form.VEILED : OccupantEntity.Form.REVEALED);
			level.addFreshEntity(e);
		});
		for (int i = 0; i < 100 && context.computeOnClient(OccupantClientGameTest::seen) == 0; i++) {
			context.waitTick();
		}
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

	/**
	 * Somewhere near the forest to stand, and somewhere {@code distance} away to put it, with a
	 * clear line from the player's eyes to its whole height. Trees all round, just not in the way.
	 */
	private static Shot frame(ServerLevel level, BlockPos forest, double distance, int salt) {
		int columns = 0, standable = 0, pairs = 0, lowLines = 0;
		Shot fallback = null;
		for (int ring = 0; ring <= 64; ring += 4) {
			int steps = Math.max(1, ring * 2);
			for (int k = 0; k < steps; k++) {
				double a = 2.0 * Math.PI * (k + 0.37 * salt) / steps;
				int px = forest.getX() + (int) Math.round(Math.cos(a) * ring) + salt * 9;
				int pz = forest.getZ() + (int) Math.round(Math.sin(a) * ring);
				columns++;
				Integer py = ground(level, px, pz, 3);
				if (py == null) continue;
				standable++;
				for (int d = 0; d < 16; d++) {
					double yaw = Math.toRadians(d * 22.5 + salt * 37.0);
					double ex = px + 0.5 - Math.sin(yaw) * distance;
					double ez = pz + 0.5 + Math.cos(yaw) * distance;
					Integer ey = ground(level, (int) Math.floor(ex), (int) Math.floor(ez), 2);
					if (ey == null || Math.abs(ey - py) > 4) continue;
					pairs++;
					double eye = py + 1.62;
					// Its legs and body have to be in view; its head may be in the leaves.
					if (!clear(level, px + 0.5, eye, pz + 0.5, ex, ey + 0.8, ez)) continue;
					if (!clear(level, px + 0.5, eye, pz + 0.5, ex, ey + 2.0, ez)) continue;
					lowLines++;
					float look = (float) Math.toDegrees(Math.atan2(-(ex - px - 0.5), ez - pz - 0.5));
					float pitch = (float) -Math.toDegrees(Math.atan2(ey + 1.8 - eye, distance));
					float back = (float) Math.toDegrees(Math.atan2(-(px + 0.5 - ex), pz + 0.5 - ez));
					Shot shot = new Shot(px + 0.5, py, pz + 0.5, look, pitch, ex, ey, ez, back);
					if (clear(level, px + 0.5, eye, pz + 0.5, ex, ey + 3.4, ez)) return shot;  // face too
					if (fallback == null) fallback = shot;
				}
			}
		}
		Occupant.LOGGER.info("[client-gametest] framing {} at {}: {} columns, {} standable, {} pairs, {} with a view",
				salt, distance, columns, standable, pairs, lowLines);
		return fallback;
	}

	/** The ground to stand on in this column, under the canopy; null if it is not forest floor. */
	private static Integer ground(ServerLevel level, int x, int z, int headroom) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		BlockState floor = level.getBlockState(feet.below());
		if (!floor.is(BlockTags.DIRT)) return null;                    // a trunk, a rock, water
		for (int h = 0; h < headroom; h++) {
			BlockPos p = feet.above(h);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return null;
			if (!level.getFluidState(p).isEmpty()) return null;
		}
		return y;
	}

	/** Nothing solid (trunks, leaves, ground) between the two points. */
	private static boolean clear(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int steps = (int) Math.ceil(len / 0.25);
		for (int i = 2; i < steps - 1; i++) {
			double t = (double) i / steps;
			BlockPos p = BlockPos.containing(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
		}
		return true;
	}
}
