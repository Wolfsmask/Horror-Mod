package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.client.ClientFog;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.GateScreen;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import com.wolfsmask.occupant.world.House;

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
		if (Boolean.getBoolean("occupant.sodium")) {
			sodiumDrawsTheFog(context);
			return;
		}
		// The title screen, as it first opens and then a while later, when it may be standing there.
		// The gate comes first: the wood, the title, and one thing to do.
		// Before it, off camera: playing, or recording? A real click on RECORDING.
		context.waitTicks(60);
		check(context.computeOnClient(mc -> com.wolfsmask.occupant.client.ModeScreen.showing()),
				"the first screen should ask how you are playing");
		shoot(context, "occupant-mode");
		clickMode(context, true);
		context.waitTicks(40);
		check(context.computeOnClient(mc -> GateScreen.showing() && com.wolfsmask.occupant.client.ModeScreen.recording()),
				"after choosing RECORDING, the gate should be on screen, with the Creator Cut chosen");
		shoot(context, "occupant-gate");
		context.waitTicks(280);
		shoot(context, "occupant-gate-later");
		// A real click on "or leave it alone": the game's own menu, with the haunting off.
		clickGate(context, false);
		context.waitTicks(20);
		check(context.computeOnClient(mc -> !GateScreen.showing() && GateScreen.passed() && !GateScreen.accepted()),
				"clicking 'or leave it alone' should leave the gate for the game's own menu");
		shoot(context, "occupant-left-alone");

		// Back to the gate, and a real click on CREATE WORLD: straight into a new world, no
		// settings, and, as RECORDING was chosen before, that world tells the story as the Creator Cut.
		context.runOnClient(mc -> mc.setScreenAndShow(new GateScreen()));
		context.waitTicks(20);
		clickGate(context, true);
		context.waitFor(mc -> mc.level != null, 20 * 60 * 5);
		context.waitTicks(100);
		shoot(context, "occupant-quick-world");
		check(com.wolfsmask.occupant.director.WorldMode.creatorCut(), "a world made after choosing RECORDING should be a Creator Cut");
		// And out again, the way a player leaves.
		context.runOnClient(mc -> mc.pauseGame(false));
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

			// The rules, once the story has started (in survival, past the opening): try creative,
			// and it puts you back.
			server.runCommand("gamemode survival @p");
			boolean introduced = false;
			for (int i = 0; i < 30 && !introduced; i++) {
				context.waitTicks(20);
				introduced = server.computeOnServer(s -> {
					var players = s.getPlayerList().getPlayers();
					return !players.isEmpty() && Director.get() != null && Director.get().data(players.get(0)).introduced;
				});
			}
			Occupant.LOGGER.info("[client-gametest] the story has begun: {}", introduced);
			server.runCommand("gamemode creative @p");
			context.waitTicks(30);
			boolean fair = context.computeOnClient(mc -> mc.player != null && !mc.player.isCreative());
			Occupant.LOGGER.info("[client-gametest] creative refused: {}", fair);
			check(fair, "switching to creative should be refused once the story has started");
			// The photographs below need a free camera.
			server.runOnServer(s -> com.wolfsmask.occupant.OccupantConfig.get().keepToTheRules = false);
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
			// Real trees, crowns and all, high enough that it stands under them.
			for (int[] t : new int[][]{{-2, 6}, {2, 8}, {-1, 9}, {2, 5}}) {
				server.runCommand(String.format("execute at @p run fill ~%d ~6 ~%d ~%d ~7 ~%d minecraft:oak_leaves[persistent=true]",
						t[0] - 2, t[1] - 2, t[0] + 2, t[1] + 2));
				server.runCommand(String.format("execute at @p run fill ~%d ~8 ~%d ~%d ~8 ~%d minecraft:oak_leaves[persistent=true]",
						t[0] - 1, t[1] - 1, t[0] + 1, t[1] + 1));
			}
			for (int[] t : new int[][]{{-2, 6}, {2, 8}, {-1, 9}, {2, 5}}) {
				server.runCommand(String.format("execute at @p run fill ~%d ~ ~%d ~%d ~7 ~%d minecraft:oak_log", t[0], t[1], t[0], t[1]));
			}
			spawn(context, server, 7, "stare");
			shoot(context, "occupant-trees");
			server.runCommand("execute at @p run fill ~-5 ~ ~2 ~5 ~9 ~12 minecraft:air");

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

			// The places it haunts, each built in turn and photographed from above and to one side
			// at midday, so what they look like is a matter of record.
			server.runCommand("time set noon");
			server.runCommand("gamemode spectator @p");         // a camera, not a body: it neither falls nor dies
			BlockPos here = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			String[] kinds = {"village", "ruin", "camp", "graves", "cottage", "watchtower", "chapel", "radio", "lighthouse", "lair"};
			for (int i = 0; i < kinds.length; i++) {
				String kind = kinds[i];
				int cx = here.getX() + 80 + i * 70;
				int cz = here.getZ() + 60;
				server.runCommand("tp @p " + cx + " " + (here.getY() + 40) + " " + (cz - 30));
				context.waitTicks(60);
				int top = server.computeOnServer(s -> {
					ServerLevel lvl = s.overworld();
					int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
					House.buildPlaceForTest(kind, lvl, new BlockPos(cx, y - 1, cz), lvl.getRandom());
					return y;
				});
				int far = kind.equals("village") ? 30 : 16;
				server.runCommand("tp @p " + (cx + far * 2 / 3) + " " + (top + far / 2) + " " + (cz - far)
						+ " facing " + cx + " " + (top + 1) + " " + cz);
				context.waitTicks(40);
				shoot(context, "place-" + kind);
			}
			// The settings, as a player finds them on the pause menu.
			context.runOnClient(mc -> mc.setScreenAndShow(new com.wolfsmask.occupant.client.SettingsScreen(null)));
			context.waitTicks(20);
			shoot(context, "settings");
			context.runOnClient(mc -> mc.setScreenAndShow(null));
			context.waitTicks(10);

			// Back where it all started, on the ground, for what comes next.
			server.runCommand("tp @p " + (here.getX() + 0.5) + " " + here.getY() + " " + (here.getZ() + 0.5) + " 0 0");
			server.runCommand("gamemode survival @p");
			server.runCommand("time set midnight");             // nobody sleeps at noon: the game wakes them
			context.waitTicks(40);

			// At dusk, early in the story: it stands just this side of where the fog begins.
			server.runCommand("time set 12700");
			server.runOnServer(s -> Director.get().data(s.getPlayerList().getPlayers().get(0)).setAct(1));
			context.waitTicks(260);
			int edge = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				return (int) Math.floor(com.wolfsmask.occupant.director.Fog.seenUpTo(p, Director.get().haunt(p))) - 2;
			});
			server.runCommand("kill " + ALL);
			server.runCommand("execute as @p at @s run occupant here " + Math.max(8, Math.min(120, edge)) + " stare");
			context.waitTicks(60);
			boolean drawnFar = context.computeOnClient(mc -> mc.level != null && mc.player != null && mc.level
					.getEntitiesOfClass(OccupantEntity.class, mc.player.getBoundingBox().inflate(128.0))
					.stream().anyMatch(e -> e.shouldRenderAtSqrDistance(100.0 * 100.0)));
			Occupant.LOGGER.info("[client-gametest] fog at dusk begins at {}; it stands at {}; drawn that far off: {}",
					edge + 4, edge, drawnFar);
			check(drawnFar, "it must be drawn as far off as it is put, not only as far as its hitbox says");
			shoot(context, "occupant-fog-edge");
			server.runCommand("kill " + ALL);
			server.runCommand("time set midnight");

			// The fog: the server decides how close it is, the client draws it. Late in the story,
			// at night, it is close.
			server.runOnServer(s -> Director.get().data(s.getPlayerList().getPlayers().get(0)).setAct(HauntData.MAX_ACT));
			context.waitTicks(260);                             // a second to be told, ten to roll in
			boolean fogged = context.computeOnClient(mc -> ClientFog.active());
			float fogEnd = context.computeOnClient(mc -> ClientFog.end());
			Occupant.LOGGER.info("[client-gametest] fog: drawn {}, thick at {} blocks, begins at {}", fogged, fogEnd,
					context.computeOnClient(mc -> ClientFog.start()));
			check(fogged && fogEnd <= 112, "the fog should have come in by the last act (" + fogEnd + ")");
			shoot(context, "occupant-fog");

			// The end of the last night, as the player sees it: black, and the line.
			server.runOnServer(s -> com.wolfsmask.occupant.util.Cues.effect(s.getPlayerList().getPlayers().get(0),
					com.wolfsmask.occupant.network.ScreenEffectPayload.FINALE, 240, 1.0f));
			context.waitTicks(90);
			shoot(context, "occupant-ending");
			context.waitTicks(170);

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
		overTheNetwork(context);
		Occupant.LOGGER.info("[client-gametest] all client checks passed");
		ForestGallery.run(context);
	}

	/**
	 * A friend joining over e4mc or e4all: a real server, joined over the network rather than played
	 * in the same game, and it is there for the one who joined, and nothing went wrong on either side.
	 */
	private static void overTheNetwork(ClientGameTestContext context) {
		if (Boolean.getBoolean("fabric.client.gametest.disableNetworkSynchronizer")) {
			Occupant.LOGGER.info("[client-gametest] over the network: not on this version (no network synchroniser)");
			return;
		}
		// A dedicated server will not start until the EULA is agreed to, wherever it looks for it.
		Path game = FabricLoader.getInstance().getGameDir();
		for (Path eula : new Path[]{Path.of("eula.txt"), game.resolve("eula.txt"), game.resolve("server").resolve("eula.txt")}) {
			try {
				Files.createDirectories(eula.toAbsolutePath().getParent());
				Files.writeString(eula, "eula=true\n");
			} catch (IOException e) {
				Occupant.LOGGER.warn("[client-gametest] could not write {}", eula, e);
			}
		}
		try (net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection = server.connect()) {
				connection.getClientLevel().waitForChunksRender();
				check(context.computeOnClient(mc -> mc.player != null && mc.getSingleplayerServer() == null),
						"joined over the network, not playing in the same game");
				context.waitTicks(40);
				server.runCommand("execute as @a at @s run occupant here 6 stare");
				context.waitFor(mc -> seen(mc) == 1, 200);
				context.waitTicks(30);
				shoot(context, "occupant-over-the-network");
				int errors = server.computeOnServer(s -> Director.get() == null ? -1 : Director.get().totalErrors());
				check(errors == 0, "the server raised " + errors + " error(s) in the Occupant with a player joined over the network");
				Occupant.LOGGER.info("[client-gametest] over the network: it is there for the one who joined");
			}
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

	/** Moves the real mouse onto PLAYING or RECORDING, before the gate, and clicks it. */
	private static void clickMode(ClientGameTestContext context, boolean record) {
		double[] at = context.computeOnClient(mc -> {
			com.wolfsmask.occupant.client.ModeScreen mode = com.wolfsmask.occupant.client.ModeScreen.current();
			check(mode != null, "the mode screen should be on screen to click");
			double[] c = mode.centreOf(record);
			double scale = mc.getWindow().getGuiScale();
			return new double[]{c[0] * scale, c[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(5);
		context.getInput().pressMouse(0);
	}

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

	/**
	 * CI's second client run, with Sodium installed, as most players have it: Sodium draws the
	 * land with its own copy of the fog, taken as the game works the fog out, and that copy must
	 * have this mod's fog in it, not only the game's own.
	 */
	private static void sodiumDrawsTheFog(ClientGameTestContext context) {
		check(FabricLoader.getInstance().isModLoaded("sodium"), "Sodium should be installed for this run");
		// The way in, clicked as a player clicks it: with Sodium too, the first screens answer.
		context.waitTicks(60);
		check(context.computeOnClient(mc -> com.wolfsmask.occupant.client.ModeScreen.showing()),
				"the first screen should ask how you are playing");
		clickMode(context, false);
		context.waitTicks(40);
		check(context.computeOnClient(mc -> GateScreen.showing()), "after choosing PLAYING, the gate should be on screen");
		clickGate(context, true);
		context.waitFor(mc -> mc.level != null, 20 * 60 * 5);
		Occupant.LOGGER.info("[sodium] through the first screens and into a world");
		// The fog, in the copy of it Sodium draws the land with.
		context.waitFor(mc -> ClientFog.active() && ClientFog.end() < 100.0f, 20 * 60);
		context.waitTicks(10);
		float[] fog = context.computeOnClient(mc -> new float[]{ClientFog.end(), sodiumFogEnd(mc)});
		Occupant.LOGGER.info("[sodium] the fog is thick at {}; Sodium draws the land with it thick at {}", fog[0], fog[1]);
		check(fog[1] > 0.0f && fog[1] <= fog[0] + 1.0f,
				"Sodium should draw the land with this fog (thick at " + fog[0] + "), not at " + fog[1]);
		// Out again, and all the way back to the title, with the world saved and its server stopped.
		context.runOnClient(mc -> mc.pauseGame(false));
		context.waitTicks(10);
		context.clickScreenButton("menu.returnToMenu");
		context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 20 * 60 * 2);
		context.waitTicks(20);
		context.runOnClient(GateScreen::passForTest);
		context.waitTicks(20);
	}

	/** The nearest of the fog ends in Sodium's own copy of the fog, found by name. */
	private static float sodiumFogEnd(Minecraft mc) {
		try {
			Object renderer = null;
			for (java.lang.reflect.Field f : mc.gameRenderer.getClass().getDeclaredFields()) {
				if (f.getType().getSimpleName().equals("FogRenderer")) {
					f.setAccessible(true);
					renderer = f.get(mc.gameRenderer);
					break;
				}
			}
			check(renderer != null, "the game renderer should have a fog renderer");
			Object params = renderer.getClass().getMethod("sodium$getFogParameters").invoke(renderer);
			float nearest = Float.MAX_VALUE;
			StringBuilder all = new StringBuilder();
			for (java.lang.reflect.RecordComponent c : params.getClass().getRecordComponents()) {
				Object v = c.getAccessor().invoke(params);
				all.append(c.getName()).append('=').append(v).append(' ');
				if (c.getName().endsWith("End") && v instanceof Float end) nearest = Math.min(nearest, end);
			}
			Occupant.LOGGER.info("[sodium] Sodium's fog: {}", all);
			return nearest;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not read Sodium's fog", e);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
