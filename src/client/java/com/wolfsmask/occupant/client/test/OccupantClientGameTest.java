package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.GateScreen;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.imageio.ImageIO;

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
	private static final String ONE = "@e[type=occupant:occupant,limit=1]";
	/** Where the photographs are gathered, so CI can pick them up by a name it knows. */
	private static final Path SHOTS = FabricLoader.getInstance().getGameDir().resolve("occupant-shots");

	@Override
	public void runTest(ClientGameTestContext context) {
		// The title screen, as it first opens and then a while later, when it may be standing there.
		// The gate comes first: the wood, the title, and one thing to do.
		context.waitTicks(60);
		check(context.computeOnClient(mc -> GateScreen.showing()), "the first screen should be the gate");
		shoot(context, "occupant-gate");
		context.waitTicks(280);
		shoot(context, "occupant-gate-later");
		// A real click on "or leave it alone": the game's own menu, with the haunting off.
		clickGate(context, false);
		context.waitTicks(20);
		check(context.computeOnClient(mc -> !GateScreen.showing() && GateScreen.passed() && !GateScreen.accepted()),
				"clicking 'or leave it alone' should leave the gate for the game's own menu");
		shoot(context, "occupant-left-alone");

		// Back to the gate, and a real click on CREATE WORLD: straight into a new world, no settings.
		context.runOnClient(mc -> mc.setScreenAndShow(new GateScreen()));
		context.waitTicks(20);
		clickGate(context, true);
		context.waitFor(mc -> mc.level != null, 20 * 60 * 5);
		context.waitTicks(100);
		shoot(context, "occupant-quick-world");
		// And out again, the way a player leaves.
		context.getInput().pressKey(KEY_ESCAPE);
		context.waitTicks(10);
		context.clickScreenButton("menu.returnToMenu");
		context.waitFor(mc -> mc.level == null, 20 * 60 * 2);
		context.waitTicks(40);

		// Through the gate, as if CREATE WORLD were chosen, to the title screen behind it.
		context.runOnClient(GateScreen::passForTest);
		context.waitTicks(40);
		shoot(context, "occupant-title");

		TestWorldSave save;
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			TestCompat.waitForWorld(game);
			context.waitTicks(120);   // the way in: a few seconds of black, then the world
			TestServerContext server = game.getServer();
			int errorsBefore = server.computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
			check(errorsBefore >= 0, "the Director should be running once a world is open");

			// A clear day with nothing else in shot, the player standing still and facing south.
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);

			// Every pose, straight on, seven blocks away: it is seventeen feet tall.
			for (String pose : List.of("stare", "veiled", "loom", "chase")) {
				spawn(context, server, 7, pose);
				shoot(context, "occupant-" + pose);
			}

			// Three quarters on, then close enough to see the face. "facing" aims from the feet, so
			// aim at its feet (level) and then tilt up by hand to where its face actually is.
			spawn(context, server, 7, "stare");
			server.runCommand("execute as @p at @s run tp @s ^4 ^ ^2 facing entity " + ONE + " feet");
			context.waitTicks(10);
			shoot(context, "occupant-three-quarter");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			spawn(context, server, 3.5f, "stare");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -35");
			context.waitTicks(10);
			Path face = shoot(context, "occupant-face");
			// The face is the whole design. In daylight it has to come out pale, not shaded or
			// painted over by another layer, which is exactly what once happened.
			int pale = palePixels(face);
			Occupant.LOGGER.info("[client-gametest] pale face pixels in the close-up: {}", pale);
			check(pale >= 800, "the face should be pale in daylight, but only " + pale + " pale pixels were found");

			// Right underneath it, looking straight up at the face. Its hitbox is far below the
			// screen here, so if the game culled it by the hitbox it would simply not be there.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			spawn(context, server, 2.0f, "stare");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -55");
			context.waitTicks(10);
			int above = palePixels(shoot(context, "occupant-looking-up"));
			Occupant.LOGGER.info("[client-gametest] pale pixels looking up at it: {}", above);
			check(above >= 400, "looking up at it from underneath, it was not drawn (" + above + " pale pixels)");

			// Shoved: the entity is moved two blocks at once. The body is drawn trailing behind,
			// then shoved after it, and the legs that are left stretched let go and re-plant.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			spawn(context, server, 7, "stare");
			server.runCommand("execute as " + ALL + " at @s run tp @s ~2 ~ ~");
			context.waitTicks(3);
			shoot(context, "occupant-shove");
			context.waitTicks(40);
			shoot(context, "occupant-after-shove");

			// In a stone corridor three wide and four high: it folds down into it and braces its
			// legs against the walls and the ceiling instead of standing on the floor.
			server.runCommand("execute at @p run fill ~-2 ~-1 ~2 ~2 ~4 ~14 minecraft:stone_bricks hollow");
			server.runCommand("execute at @p run fill ~-1 ~ ~2 ~1 ~3 ~2 minecraft:air");
			server.runCommand("execute at @p run setblock ~ ~4 ~5 minecraft:sea_lantern");
			server.runCommand("execute at @p run setblock ~ ~4 ~10 minecraft:sea_lantern");
			spawn(context, server, 7, "stare");
			shoot(context, "occupant-corridor");
			server.runCommand("execute at @p run fill ~-2 ~ ~2 ~2 ~4 ~14 minecraft:air");

			// Among tree trunks: legs on the trunks, not on the ground.
			for (String log : List.of("~-2 ~ ~6 ~-2 ~4 ~6", "~2 ~ ~8 ~2 ~4 ~8", "~-1 ~ ~9 ~-1 ~4 ~9", "~2 ~ ~5 ~2 ~3 ~5")) {
				server.runCommand("execute at @p run fill " + log + " minecraft:oak_log");
			}
			spawn(context, server, 7, "stare");
			shoot(context, "occupant-trees");
			server.runCommand("execute at @p run fill ~-3 ~ ~4 ~3 ~4 ~10 minecraft:air");

			// Indoors, under a two-block ceiling: it has to stoop rather than stand through the roof.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			server.runCommand("execute at @p run fill ~-4 ~-1 ~-2 ~4 ~2 ~10 minecraft:stone_bricks hollow");
			server.runCommand("execute at @p run setblock ~2 ~ ~-1 minecraft:stone_bricks");
			server.runCommand("execute at @p run setblock ~2 ~1 ~-1 minecraft:lantern[hanging=false]");
			spawn(context, server, 5, "stare");
			shoot(context, "occupant-indoors");
			server.runCommand("execute at @p run fill ~-4 ~ ~-2 ~4 ~2 ~10 minecraft:air");

			// At night, from a long way off: it should still be just about visible.
			server.runCommand("time set midnight");
			spawn(context, server, 30, "stare");
			shoot(context, "occupant-night-distant");
			spawn(context, server, 6, "stare");
			shoot(context, "occupant-night-close");
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
			TestCompat.waitForWorld(again);
			context.waitTicks(120);
			boolean inWorld = context.computeOnClient(mc -> mc.player != null && mc.level != null);
			check(inWorld, "rejoining a world the player left asleep in must work");
			int errors = again.getServer().computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
			check(errors == 0, "rejoining raised " + errors + " error(s) in the Occupant; see the log");
			// And it still works afterwards.
			spawn(context, again.getServer(), 5, "stare");
			shoot(context, "occupant-after-rejoin");
		}
		Occupant.LOGGER.info("[client-gametest] all client checks passed");
		ForestGallery.run(context);
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

	/** Takes a screenshot and files a copy under a fixed name, whatever the game called it. */
	static Path shoot(ClientGameTestContext context, String name) {
		Path taken = context.takeScreenshot(name);
		Path kept = SHOTS.resolve(name + ".png");
		try {
			Files.createDirectories(SHOTS);
			Files.copy(taken, kept, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("could not keep screenshot " + taken, e);
		}
		Occupant.LOGGER.info("[client-gametest] screenshot {} -> {}", name, taken);
		return kept;
	}

	/**
	 * Counts the ivory, faintly pink pixels in a screenshot: the colour of its face. The sky is
	 * blue, the grass green and the hearts red, so none of those count. The bottom of the screen
	 * is left out, where the hotbar and the player's own (skin-coloured) arm are.
	 */
	private static int palePixels(Path shot) {
		BufferedImage img;
		try {
			img = ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException("could not read screenshot " + shot, e);
		}
		check(img != null, "could not decode screenshot " + shot);
		int count = 0;
		int bottom = (int) (img.getHeight() * 0.8f);
		int right = (int) (img.getWidth() * 0.7f);
		for (int y = 0; y < bottom; y++) {
			for (int x = 0; x < right; x++) {
				int rgb = img.getRGB(x, y);
				int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				if (r >= 110 && r >= b + 8 && r - b <= 70 && g >= r - 45 && g <= r) count++;
			}
		}
		return count;
	}

	static int seen(Minecraft mc) {
		if (mc.level == null || mc.player == null) return 0;
		return mc.level.getEntitiesOfClass(OccupantEntity.class, mc.player.getBoundingBox().inflate(48)).size();
	}

	private static final int KEY_ESCAPE = 256;

	/** Moves the real mouse onto one of the gate's two choices and clicks it. */
	private static void clickGate(ClientGameTestContext context, boolean enter) {
		double[] at = context.computeOnClient(mc -> {
			GateScreen gate = GateScreen.current();
			check(gate != null, "the gate should be on screen to click");
			double[] c = gate.centreOf(enter);
			double scale = mc.getWindow().getGuiScale();
			return new double[]{c[0] * scale, c[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(5);
		context.getInput().pressMouse(0);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
