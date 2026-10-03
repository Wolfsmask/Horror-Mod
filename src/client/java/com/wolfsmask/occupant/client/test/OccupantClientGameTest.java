package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
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
			again.getClientLevel().waitForChunksRender();
			context.waitTicks(40);
			boolean inWorld = context.computeOnClient(mc -> mc.player != null && mc.level != null);
			check(inWorld, "rejoining a world the player left asleep in must work");
			int errors = again.getServer().computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
			check(errors == 0, "rejoining raised " + errors + " error(s) in the Occupant; see the log");
			// And it still works afterwards.
			spawn(context, again.getServer(), 5, "stare");
			shoot(context, "occupant-after-rejoin");
		}
		Occupant.LOGGER.info("[client-gametest] all client checks passed");
		gallery(context);
	}

	/**
	 * Not a check: photographs of it the way a player actually meets it, from the player's own
	 * eyes with the HUD up, in the places the story puts it. Each scene is built fresh, a long way
	 * from the last, so nothing from one shows up in another.
	 */
	private static void gallery(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			game.getClientLevel().waitForChunksRender();
			TestServerContext server = game.getServer();
			// No "It is standing behind you..." in the chat: that is only there for /occupant here.
			server.runCommand("gamerule sendCommandFeedback false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("weather clear");

			// Watching from the treeline at dusk, too far to make out.
			scene(context, game, 1);
			trees(server, 16, 34, 14);
			server.runCommand("time set 12900");
			look(context, server, 26, "stare");
			context.waitTicks(20);
			shoot(context, "occupant-gallery-1-treeline-dusk");

			// The same trees after dark, closer.
			server.runCommand("time set 18000");
			look(context, server, 13, "veiled");
			shoot(context, "occupant-gallery-2-forest-night");
			look(context, server, 8, "stare");
			shoot(context, "occupant-gallery-3-forest-night-close");

			// On the ridge across the valley, standing above the trees.
			scene(context, game, 2);
			for (int k = 0; k < 7; k++) {
				server.runCommand("execute at @p run fill ~-40 ~" + (k - 1) + " ~" + (28 + 2 * k) + " ~40 ~" + (k - 1) + " ~" + (62 - 2 * k) + " minecraft:grass_block");
			}
			trees(server, 30, 44, 26);
			server.runCommand("time set 13300");
			look(context, server, 36, "stare");
			context.waitTicks(20);
			shoot(context, "occupant-gallery-4-on-the-ridge");

			// Outside the window, at night, from inside a lit room.
			scene(context, game, 3);
			server.runCommand("execute at @p run fill ~-4 ~-1 ~-4 ~4 ~3 ~3 minecraft:spruce_planks hollow");
			server.runCommand("execute at @p run fill ~-1 ~1 ~3 ~1 ~2 ~3 minecraft:glass_pane");
			server.runCommand("execute at @p run setblock ~3 ~ ~-3 minecraft:lantern");
			server.runCommand("execute at @p run setblock ~-3 ~ ~2 minecraft:crafting_table");
			server.runCommand("execute at @p run setblock ~3 ~ ~2 minecraft:red_bed[facing=west,part=head]");
			server.runCommand("execute at @p run setblock ~2 ~ ~2 minecraft:red_bed[facing=west,part=foot]");
			trees(server, 14, 26, 12);
			server.runCommand("time set 18000");
			look(context, server, 9, "stare");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -8");
			context.waitTicks(10);
			shoot(context, "occupant-gallery-5-at-the-window");

			// Facing away from it in the dark, then turning round.
			scene(context, game, 4);
			trees(server, -24, -10, 14);
			server.runCommand("time set 18500");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 0");
			look(context, server, 3.5f, "loom");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);
			shoot(context, "occupant-gallery-6-nothing-there");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 -12");
			context.waitTicks(4);
			shoot(context, "occupant-gallery-7-turned-around");

			// Down a tunnel lit by torches, braced against the walls.
			scene(context, game, 5);
			server.runCommand("execute at @p run fill ~-3 ~-1 ~-2 ~3 ~4 ~30 minecraft:deepslate_bricks hollow");
			server.runCommand("execute at @p run fill ~-2 ~-1 ~-1 ~2 ~-1 ~29 minecraft:cobbled_deepslate");
			for (int z = 3; z <= 27; z += 6) {
				server.runCommand("execute at @p run setblock ~-2 ~2 ~" + z + " minecraft:wall_torch[facing=east]");
				server.runCommand("execute at @p run setblock ~2 ~2 ~" + (z + 3) + " minecraft:wall_torch[facing=west]");
			}
			look(context, server, 13, "stare");
			shoot(context, "occupant-gallery-8-tunnel");
			look(context, server, 6, "loom");
			shoot(context, "occupant-gallery-9-tunnel-close");

			// Out in the rain, in the last of the light.
			scene(context, game, 6);
			trees(server, 18, 40, 22);
			server.runCommand("weather rain");
			server.runCommand("time set 13000");
			look(context, server, 24, "stare");
			context.waitTicks(40);
			shoot(context, "occupant-gallery-10-rain");

			// Coming for you through the trees.
			server.runCommand("weather clear");
			server.runCommand("time set 12800");
			look(context, server, 16, "chase");
			context.waitTicks(10);
			shoot(context, "occupant-gallery-11-coming");
			server.runCommand("kill " + ALL);
		} catch (RuntimeException | AssertionError e) {
			// Photographs only: never let them fail the run that proved everything else.
			Occupant.LOGGER.warn("[client-gametest] gallery stopped early", e);
		}
	}

	/** Like {@link #spawn}, for photographs: if it does not arrive, take the picture anyway. */
	private static void look(ClientGameTestContext context, TestServerContext server, float distance, String pose) {
		server.runCommand("kill " + ALL);
		context.waitTicks(2);
		server.runCommand("execute as @p at @s run occupant here " + distance + " " + pose);
		for (int i = 0; i < 100 && context.computeOnClient(OccupantClientGameTest::seen) == 0; i++) {
			context.waitTick();
		}
		context.waitTicks(30);
	}

	/** Moves the player somewhere new and untouched, facing south, and waits for it to load. */
	private static void scene(ClientGameTestContext context, TestSingleplayerContext game, int n) {
		TestServerContext server = game.getServer();
		server.runCommand("kill " + ALL);
		server.runCommand("execute as @p at @s run tp @s ~" + 400 + " ~ ~ 0 0");
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(20);
		game.getClientLevel().waitForChunksRender();
		Occupant.LOGGER.info("[client-gametest] gallery scene {}", n);
	}

	/** A scattering of trees across the view, from {@code near} to {@code far} blocks ahead. */
	private static void trees(TestServerContext server, int near, int far, int count) {
		String[] kinds = {"minecraft:oak", "minecraft:dark_oak", "minecraft:birch", "minecraft:spruce", "minecraft:fancy_oak"};
		for (int i = 0; i < count; i++) {
			int x = (int) Math.round(Math.sin(i * 2.399) * (8 + (i * 7) % 23));
			int z = near + (int) Math.abs(Math.round(((i * 0.618) % 1.0) * (far - near)));
			if (Math.abs(x) < 3 && Math.abs(z) < 30) x += x < 0 ? -4 : 4;   // keep the middle clear
			String kind = kinds[i % kinds.length];
			server.runCommand("execute at @p run place feature " + kind + " ~" + x + " ~ ~" + z);
		}
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
	private static Path shoot(ClientGameTestContext context, String name) {
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

	private static int seen(Minecraft mc) {
		if (mc.level == null || mc.player == null) return 0;
		return mc.level.getEntitiesOfClass(OccupantEntity.class, mc.player.getBoundingBox().inflate(48)).size();
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
