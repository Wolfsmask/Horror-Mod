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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
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
			BlockPos[] centres = {woods[0], woods[1], woods[0].offset(200, 0, 40), woods[1].offset(-40, 0, 200), spawn};
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
		BlockPos cam = server.computeOnServer(s -> ground(s.overworld(), it.getX() + 13, it.getZ()));
		clearView(server, it.offset(-1, 0, -3), cam.offset(1, 0, 3), Math.min(it.getY(), cam.getY()) + 1, 14);
		place(server, it);
		camera(context, game, cam.getX() + 0.5, cam.getY() + 1.6, cam.getZ() + 0.5, it.getX() + 0.5, it.getY() + 3.0, it.getZ() + 0.5, 12700);
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~9 ~");      // it in the left third, as if half-turned to it
		still(context, game, "cinematic-treeline");

		// And close, from below: its face against the sky.
		BlockPos near = server.computeOnServer(s -> ground(s.overworld(), it.getX() + 2, it.getZ() + 1));
		clearView(server, it.offset(-4, 0, -4), it.offset(4, 0, 4), it.getY() + 2, 16);
		place(server, it);
		camera(context, game, near.getX() + 0.5, near.getY() + 1.6, near.getZ() + 0.5, it.getX() + 0.5, it.getY() + 4.3, it.getZ() + 0.5, 12600);
		still(context, game, "cinematic-face");
	}

	/** The village from above at dusk, and it, small, standing in the path between the houses. */
	private static void village(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> House.buildPlaceForTest("village", s.overworld(), floor, s.overworld().getRandom()));
		BlockPos it = server.computeOnServer(s -> ground(s.overworld(), floor.getX() - 1, floor.getZ() - 10));
		clearView(server, floor.offset(-20, 0, -30), floor.offset(22, 0, 12), floor.getY() + 2, 20);
		place(server, it);
		camera(context, game, floor.getX() + 16.5, floor.getY() + 11, floor.getZ() - 26.5, floor.getX(), floor.getY() + 1, floor.getZ() - 5, 13000);
		still(context, game, "cinematic-village");

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
		TestServerContext server = game.getServer();
		server.runOnServer(s -> House.buildPlaceForTest("ruin", s.overworld(), floor, s.overworld().getRandom()));
		// Only outside the walls (the keep reaches z - 7): nothing built is ever cleared.
		clearView(server, floor.offset(-11, 0, -25), floor.offset(8, 0, -8), floor.getY() + 1, 16);
		BlockPos it = server.computeOnServer(s -> ground(s.overworld(), floor.getX() + 3, floor.getZ() - 10));
		place(server, it);
		BlockPos cam = server.computeOnServer(s -> ground(s.overworld(), floor.getX() - 7, floor.getZ() - 22));
		camera(context, game, cam.getX() + 0.5, cam.getY() + 2.2, cam.getZ() + 0.5, floor.getX() + 0.5, floor.getY() + 4.0, floor.getZ() - 6.5, 12500);
		still(context, game, "cinematic-ruin");
	}

	/** A camp at night, its fire lit again, and it beside the tent where the light just reaches. */
	private static void camp(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			House.buildPlaceForTest("camp", level, floor, level.getRandom());
			light(level, floor, 2, Blocks.CAMPFIRE.defaultBlockState());
		});
		clearView(server, floor.offset(-9, 0, -10), floor.offset(8, 0, 8), floor.getY() + 2, 16);
		BlockPos it = server.computeOnServer(s -> ground(s.overworld(), floor.getX() + 6, floor.getZ() + 4));
		place(server, it);
		BlockPos cam = server.computeOnServer(s -> ground(s.overworld(), floor.getX() - 6, floor.getZ() - 7));
		camera(context, game, cam.getX() + 0.5, cam.getY() + 2.0, cam.getZ() + 0.5, floor.getX() + 2.5, floor.getY() + 2.0, floor.getZ() + 1.5, 18000);
		still(context, game, "cinematic-camp");
	}

	/** The graveyard under the moon, candles lit, and it standing past the last row. */
	private static void graves(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			House.buildPlaceForTest("graves", level, floor, level.getRandom());
			light(level, floor, 7, Blocks.CANDLE.defaultBlockState());
		});
		clearView(server, floor.offset(-8, 0, -13), floor.offset(8, 0, 11), floor.getY() + 2, 16);
		BlockPos it = server.computeOnServer(s -> ground(s.overworld(), floor.getX() + 1, floor.getZ() + 8));
		place(server, it);
		BlockPos cam = server.computeOnServer(s -> ground(s.overworld(), floor.getX() - 2, floor.getZ() - 11));
		camera(context, game, cam.getX() + 0.5, cam.getY() + 2.2, cam.getZ() + 0.5, floor.getX() + 0.5, floor.getY() + 2.6, floor.getZ() + 5.5, 17800);
		still(context, game, "cinematic-graves");
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

	/** Where to stand at a column: the ground under any canopy. */
	private static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, surface(level, x, z), z);
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

	/** Puts it there, facing the player, the way /occupant here does. */
	private static void place(TestServerContext server, BlockPos feet) {
		server.runCommand("kill " + ALL);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			OccupantEntity e = ModEntities.OCCUPANT.create(level, EntitySpawnReason.COMMAND);
			if (e == null) return;
			e.standAlone(p);
			Vec3 at = Vec3.atBottomCenterOf(feet);
			float yaw = Sight.yawBetween(at, p.position());
			e.snapTo(at.x, at.y, at.z, yaw, 0.0f);
			e.setYHeadRot(yaw);
			e.setYBodyRot(yaw);
			e.setMode(OccupantEntity.Mode.STARE);
			e.setForm(OccupantEntity.Form.REVEALED);
			level.addFreshEntity(e);
		});
	}

	private static void camera(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z,
							   double lx, double ly, double lz, int time) {
		TestServerContext server = game.getServer();
		server.runCommand("time set " + time);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f facing %.2f %.2f %.2f", x, y, z, lx, ly, lz));
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
