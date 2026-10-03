package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Boots the real game client, with a real renderer, and does what a player does.
 * <p>
 * The server-side game tests prove the logic. They cannot prove that the model draws without
 * throwing, that the entity actually reaches a client, that it stands on the ground, that it
 * fits indoors, or that a player can get into a world with the mod installed at all. This can,
 * and it photographs every pose while it is at it, so what the model looks like in game is a
 * matter of record rather than of a preview renderer.
 * <p>
 * Any exception anywhere in the client or the integrated server fails the run.
 */
public final class OccupantClientGameTest implements FabricClientGameTest {
	private static final String ALL = "@e[type=occupant:occupant]";

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			game.getClientLevel().waitForChunksRender();
			TestServerContext server = game.getServer();
			int errorsBefore = server.computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
			check(errorsBefore >= 0, "the Director should be running once a world is open");

			// A clear day with nothing else in shot, the player standing still and facing south.
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);

			// Every pose, straight on, five blocks away.
			for (String pose : List.of("stare", "veiled", "loom", "chase")) {
				spawn(context, server, 5, pose);
				context.takeScreenshot("occupant-" + pose);
			}

			// Three quarters on, then close enough to see the face.
			spawn(context, server, 5, "stare");
			server.runCommand("execute as @p at @s run tp @s ^3 ^ ^1 facing entity " + ALL + "[limit=1] eyes");
			context.waitTicks(10);
			context.takeScreenshot("occupant-three-quarter");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			spawn(context, server, 2.5f, "stare");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ facing entity " + ALL + "[limit=1] eyes");
			context.waitTicks(10);
			context.takeScreenshot("occupant-face");

			// Indoors, under a two-block ceiling: it has to stoop rather than stand through the roof.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			server.runCommand("execute at @p run fill ~-4 ~-1 ~-2 ~4 ~3 ~10 minecraft:stone_bricks hollow");
			server.runCommand("execute at @p run fill ~-3 ~ ~-1 ~3 ~1 ~9 minecraft:air");
			server.runCommand("execute at @p run setblock ~2 ~1 ~-1 minecraft:lantern[hanging=false]");
			server.runCommand("execute at @p run setblock ~2 ~ ~-1 minecraft:stone_bricks");
			spawn(context, server, 5, "stare");
			context.takeScreenshot("occupant-indoors");
			server.runCommand("execute at @p run fill ~-4 ~-1 ~-2 ~4 ~3 ~10 minecraft:air");

			// At night, from a long way off: it should still be just about visible.
			server.runCommand("time set midnight");
			spawn(context, server, 30, "stare");
			context.takeScreenshot("occupant-night-distant");
			spawn(context, server, 6, "stare");
			context.takeScreenshot("occupant-night-close");
			server.runCommand("kill " + ALL);

			// The way the user was locked out: going to sleep, quitting, and coming back. The game
			// wakes a sleeper while it is placing them into the world, which once threw out of the
			// mod and ended the join with "Invalid player data".
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				BlockPos foot = p.blockPosition();
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
						"setblock " + foot.getX() + " " + foot.getY() + " " + foot.getZ() + " minecraft:red_bed[facing=south,part=foot]");
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
						"setblock " + foot.getX() + " " + foot.getY() + " " + (foot.getZ() + 1) + " minecraft:red_bed[facing=south,part=head]");
				p.startSleeping(foot.south());
			});
			context.waitTicks(20);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).isSleeping()),
					"the player should be asleep before quitting");

			int errorsAfter = server.computeOnServer(s -> Director.get().totalErrors());
			check(errorsAfter == errorsBefore, "the Occupant raised " + (errorsAfter - errorsBefore) + " error(s); see the log");
			save = game.getWorldSave();
		}

		// Back into the same world, which still has the player asleep in it.
		try (TestSingleplayerContext again = save.open()) {
			again.getClientLevel().waitForChunksRender();
			context.waitTicks(40);
			boolean inWorld = context.computeOnClient(mc -> mc.player != null && mc.level != null);
			check(inWorld, "rejoining a world the player left asleep in must work");
			int errors = again.getServer().computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
			check(errors == 0, "rejoining raised " + errors + " error(s) in the Occupant; see the log");
			// And it still works afterwards.
			spawn(context, again.getServer(), 5, "stare");
			context.takeScreenshot("occupant-after-rejoin");
		}
		Occupant.LOGGER.info("[client-gametest] all client checks passed");
	}

	/** Clears away any Occupant, puts a new one in front of the player, and waits for it to arrive. */
	private static void spawn(ClientGameTestContext context, TestServerContext server, float distance, String pose) {
		server.runCommand("kill " + ALL);
		context.waitTicks(2);
		server.runCommand("execute as @p at @s run occupant here " + distance + " " + pose);
		// It is only ever sent to the player it is haunting, so this proves that path works too.
		context.waitFor(mc -> seen(mc) == 1, 100);
		context.waitTicks(30);   // let its pose settle; it moves in held steps
	}

	private static int seen(Minecraft mc) {
		if (mc.level == null || mc.player == null) return 0;
		return mc.level.getEntitiesOfClass(OccupantEntity.class, mc.player.getBoundingBox().inflate(48)).size();
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
