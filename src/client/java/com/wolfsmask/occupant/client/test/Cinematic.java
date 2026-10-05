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
			face(context, game, spawn);
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] cinematic stills stopped early", e);
		} finally {
			server.runCommand("kill " + ALL);
			hud(context, true);
			server.runCommand("gamemode survival @p");
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** Dusk in the wood: it, between the trunks, a way off, looking back. */
	private static void treeline(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos stand = server.computeOnServer(s -> ForestGallery.standSpot(s.overworld(), woods[0], 7));
		if (stand == null) return;
		int[] yaws = server.computeOnServer(s -> ForestGallery.openestYaws(s.overworld(), stand));
		double r = Math.toRadians(yaws[0]);
		int ox = stand.getX() + (int) Math.round(-Math.sin(r) * 15);
		int oz = stand.getZ() + (int) Math.round(Math.cos(r) * 15);
		BlockPos it = server.computeOnServer(s -> new BlockPos(ox, surface(s.overworld(), ox, oz), oz));
		place(server, it);
		camera(context, game, stand.getX() + 0.5, stand.getY() + 1.4, stand.getZ() + 0.5, it.getX() + 0.5, it.getY() + 3.2, it.getZ() + 0.5, 12900);
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~11 ~");     // off centre, as if half-turned to it
		still(context, game, "cinematic-treeline");
	}

	/** The village from above at dusk, and it, small, standing in the path between the houses. */
	private static void village(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> House.buildPlaceForTest("village", s.overworld(), floor, s.overworld().getRandom()));
		BlockPos path = floor.offset(-1, 0, -10);
		BlockPos it = server.computeOnServer(s -> new BlockPos(path.getX(), surface(s.overworld(), path.getX(), path.getZ()), path.getZ()));
		place(server, it);
		camera(context, game, floor.getX() + 16.5, floor.getY() + 15, floor.getZ() - 30.5, floor.getX(), floor.getY() + 1, floor.getZ() - 6, 13100);
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

	/** The ruined keep at sunset, from outside the gate: it is standing in the yard. */
	private static void ruin(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> House.buildPlaceForTest("ruin", s.overworld(), floor, s.overworld().getRandom()));
		place(server, floor.offset(1, 1, 2));
		camera(context, game, floor.getX() + 4.5, floor.getY() + 3, floor.getZ() - 19.5, floor.getX() + 0.5, floor.getY() + 3.5, floor.getZ(), 12350);
		still(context, game, "cinematic-ruin");
	}

	/** A camp at night, its fire lit again, and it behind the tent where the light just reaches. */
	private static void camp(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor) {
		TestServerContext server = game.getServer();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			House.buildPlaceForTest("camp", level, floor, level.getRandom());
			light(level, floor, 2, Blocks.CAMPFIRE.defaultBlockState());
		});
		BlockPos back = floor.offset(4, 0, 4);
		BlockPos it = server.computeOnServer(s -> new BlockPos(back.getX(), surface(s.overworld(), back.getX(), back.getZ()), back.getZ()));
		place(server, it);
		camera(context, game, floor.getX() - 6.5, floor.getY() + 2.6, floor.getZ() - 7.5, floor.getX() + 1.5, floor.getY() + 1.8, floor.getZ() + 1.5, 18000);
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
		BlockPos back = floor.offset(1, 0, 8);
		BlockPos it = server.computeOnServer(s -> new BlockPos(back.getX(), surface(s.overworld(), back.getX(), back.getZ()), back.getZ()));
		place(server, it);
		camera(context, game, floor.getX() - 2.5, floor.getY() + 2.4, floor.getZ() - 10.5, floor.getX() + 0.5, floor.getY() + 2.6, floor.getZ() + 5.5, 17800);
		still(context, game, "cinematic-graves");
	}

	/** Its face, close, from below, against the last of the light. */
	private static void face(ClientGameTestContext context, TestSingleplayerContext game, BlockPos spawn) {
		TestServerContext server = game.getServer();
		BlockPos ground = server.computeOnServer(s -> new BlockPos(spawn.getX(), surface(s.overworld(), spawn.getX(), spawn.getZ()), spawn.getZ()));
		BlockPos it = ground.offset(0, 0, 4);
		BlockPos at = server.computeOnServer(s -> new BlockPos(it.getX(), surface(s.overworld(), it.getX(), it.getZ()), it.getZ()));
		place(server, at);
		camera(context, game, ground.getX() + 0.8, ground.getY() + 1.5, ground.getZ() + 0.5, at.getX() + 0.5, at.getY() + 4.6, at.getZ() + 0.5, 12450);
		still(context, game, "cinematic-face");
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
