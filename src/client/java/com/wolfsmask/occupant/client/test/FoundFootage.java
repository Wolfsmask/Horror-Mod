package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.ScreenEffects;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.Mercy;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Kinds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The other kind of picture for the mod's page: not film stills but screenshots, the kind a player
 * takes and then looks at again. The game as it is played: in survival, the hotbar and the hearts
 * on screen, the game's own lens, from where a player's eyes are. Nothing graded, nothing framed
 * round it; it is simply there, where you would not want it to be, and you see it second.
 * <p>
 * Everything is the real game and the real mod, only arranged. Not a check: anything that goes
 * wrong is logged, and the rest are still taken.
 */
final class FoundFootage {
	private static final int KEY_F3 = 292;

	private FoundFootage() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods, BlockPos spawn) {
		TestServerContext server = game.getServer();
		try {
			server.runCommand("gamemode survival @p");
			server.runCommand("weather clear");
			// The story held still, so nothing it does on its own gets into the pictures; the fog
			// and the act are then set as the story would have them, late on.
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = true);
			context.waitTicks(30);
			context.runOnClient(mc -> {
				ScreenEffects.trigger(new ScreenEffectPayload(ScreenEffectPayload.ACT, 0, 3), mc);
				com.wolfsmask.occupant.client.ClientFog.set(72.0f, 1);
			});
			Cinematic.hud(context, true);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(20);

			shot("treeline", () -> treeline(context, game, woods));
			shot("behind", () -> behind(context, game, woods));
			shot("window", () -> window(context, game, woods));
			shot("cave", () -> cave(context, game, spawn));
			shot("debug", () -> debug(context, game, woods));
			shot("rescue", () -> rescue(context, game, woods));
			shot("plates", () -> plates(context, game, spawn));
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] found footage stopped early", e);
		} finally {
			server.runCommand("kill " + Cinematic.ALL);
			server.runOnServer(s -> Director.get().data(Cinematic.player(s)).paused = false);
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void shot(String name, Runnable take) {
		try {
			take.run();
		} catch (RuntimeException | AssertionError e) {
			Occupant.LOGGER.warn("[client-gametest] found footage: {} not taken", name, e);
		}
	}

	/**
	 * Dusk, walking through the wood with an axe: an ordinary picture of trees. It is standing
	 * between two of them, off to one side, facing you. Most people see the trees first.
	 */
	private static void treeline(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos it = server.computeOnServer(s -> ForestGallery.standSpot(s.overworld(), woods[1], 5));
		if (it == null) return;
		Vec3 eye = server.computeOnServer(s -> Cinematic.findCamera(s.overworld(), Cinematic.player(s), it, -1.0, 0.4,
				new double[]{20, 24, 17}, Cinematic.EYE, 0));
		// Looking past it, a little to its left: it is off to the right of the picture, not in the middle.
		Vec3 at = Vec3.atBottomCenterOf(it).add(0, 2.2, 0);
		Vec3 toward = at.subtract(eye);
		Vec3 left = new Vec3(toward.z, 0, -toward.x).normalize();
		Vec3 look = at.add(left.scale(toward.length() * 0.32)).add(0, -0.6, 0);
		Cinematic.camera(context, game, eye, look, 12950);
		Cinematic.place(server, it, eye, Cinematic.Look.STARING);
		settle(context, "found-treeline");
	}

	/**
	 * Night, a torch in hand, in the open: you turned round. It has bent right down to you, its
	 * face a little above yours, the mouth open, so close that it does not fit in the picture.
	 */
	private static void behind(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[0].offset(60, 0, -60)));
		if (floor == null) return;
		server.runOnServer(s -> Cinematic.fellTrees(s.overworld(), floor, 10));
		BlockPos it = server.computeOnServer(s -> Cinematic.standNear(s.overworld(), floor, 0, 0));
		Vec3 eye = server.computeOnServer(s -> {
			BlockPos e = BlockPos.containing(Vec3.atBottomCenterOf(it).add(3.1, 0, 0.6));
			return new Vec3(e.getX() + 0.5, Cinematic.ground(s.overworld(), e.getX(), e.getZ()) + Cinematic.EYE, e.getZ() + 0.5);
		});
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:torch 23");
		Cinematic.camera(context, game, eye, Vec3.atBottomCenterOf(it).add(0, 3.0, 0), 18000);
		Cinematic.place(server, it, eye, Cinematic.Look.looming(0));
		settle(context, "found-behind");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_axe");
	}

	/**
	 * Night, inside, by the light of one torch. There is a window. A step back from the glass, bent
	 * down to look in, its face.
	 */
	private static void window(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[1].offset(-70, 0, 70)));
		if (floor == null) return;
		Vec3[] eyeAndIt = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Cinematic.fellTrees(level, floor, 14);
			BlockPos o = floor.above();                             // the floor of the room
			BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
			// A small room: planks, a stone floor, a roof; the window in the north wall.
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					for (int y = 0; y <= 4; y++) {
						boolean shell = Math.abs(x) == 3 || Math.abs(z) == 3 || y == 0 || y == 4;
						set(level, o.offset(x, y, z), shell ? (y == 0 ? Blocks.COBBLESTONE.defaultBlockState() : wall)
								: Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int x = -1; x <= 1; x++) {
				for (int y = 2; y <= 3; y++) set(level, o.offset(x, y, -3), Blocks.GLASS_PANE.defaultBlockState());
			}
			// One torch, on the wall behind you; a table and a barrel against the side; nothing else.
			set(level, o.offset(2, 2, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
			set(level, o.offset(-2, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
			set(level, o.offset(-2, 1, 2), Blocks.BARREL.defaultBlockState());
			// Outside the window, clear ground, and no light.
			for (int x = -2; x <= 2; x++) {
				for (int z = -7; z <= -4; z++) {
					for (int y = 1; y <= 6; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
			BlockPos it = Cinematic.standNear(level, o.offset(0, 0, -5), 0, 0);
			Vec3 eye = Vec3.atBottomCenterOf(o.offset(0, 1, 2)).add(0, Cinematic.EYE, 0);
			return new Vec3[]{eye, Vec3.atBottomCenterOf(it)};
		});
		Vec3 eye = eyeAndIt[0];
		BlockPos it = BlockPos.containing(eyeAndIt[1]);
		Cinematic.camera(context, game, eye, Vec3.atBottomCenterOf(floor.above()).add(0, 2.6, -3.0), 18000);
		Cinematic.place(server, it, eye, Cinematic.Look.looming(0));
		settle(context, "found-window");
	}

	/**
	 * Down a mine, two torches behind you and none further on. Where the light gives out, folded
	 * down into the tunnel, something pale, facing you.
	 */
	private static void cave(ClientGameTestContext context, TestSingleplayerContext game, BlockPos spawn) {
		TestServerContext server = game.getServer();
		Vec3[] eyeAndIt = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos o = new BlockPos(spawn.getX() + 40, Math.max(level.getMinY() + 12, -30), spawn.getZ() - 40);
			level.getChunk(o.getX() >> 4, o.getZ() >> 4);
			level.getChunk(o.getX() >> 4, (o.getZ() + 32) >> 4);
			for (int x = -4; x <= 4; x++) {
				for (int z = -3; z <= 30; z++) {
					for (int y = -1; y <= 5; y++) {
						BlockPos p = o.offset(x, y, z);
						boolean tunnel = Math.abs(x) <= 1 && y >= 0 && y <= 2 && z >= 0 && z <= 27;
						long h = (p.asLong() * 0x9E3779B97F4A7C15L) >>> 58;
						BlockState rock = h < 6 ? Blocks.TUFF.defaultBlockState() : h < 9 ? Blocks.COAL_ORE.defaultBlockState()
								: h < 10 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
						set(level, p, tunnel ? Blocks.AIR.defaultBlockState() : rock);
					}
				}
			}
			// Two torches, near you; past them, nothing.
			set(level, o.offset(-1, 1, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
			set(level, o.offset(1, 1, 7), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
			set(level, o.offset(1, 2, 13), Blocks.COBWEB.defaultBlockState());
			Vec3 eye = Vec3.atBottomCenterOf(o.offset(0, 0, 0)).add(0, Cinematic.EYE, 0);
			return new Vec3[]{eye, Vec3.atBottomCenterOf(o.offset(0, 0, 17))};
		});
		Vec3 eye = eyeAndIt[0];
		BlockPos it = BlockPos.containing(eyeAndIt[1]);
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_pickaxe");
		Cinematic.camera(context, game, eye, eyeAndIt[1].add(0, 1.2, 0), 18000);
		Cinematic.place(server, it, eye, Cinematic.Look.looming(0));
		settle(context, "found-cave");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_axe");
	}

	/**
	 * Late in the story, at dusk, the debug screen open for where they are. It will not say; it
	 * asks why they want to know. Out past the trees, in the fog, it stands and watches.
	 */
	private static void debug(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos it = server.computeOnServer(s -> ForestGallery.standSpot(s.overworld(), woods[0], 9));
		if (it == null) return;
		Vec3 eye = server.computeOnServer(s -> Cinematic.findCamera(s.overworld(), Cinematic.player(s), it, 0.3, -1.0,
				new double[]{30, 26, 34}, Cinematic.EYE, 0));
		Cinematic.camera(context, game, eye, Vec3.atBottomCenterOf(it).add(0, 1.4, 0), 13300);
		Cinematic.place(server, it, eye, Cinematic.Look.STARING);
		context.waitTicks(20);
		context.getInput().pressKey(KEY_F3);
		context.waitTicks(25);
		settle(context, "found-debug");
		context.getInput().pressKey(KEY_F3);
	}

	/**
	 * It, against flat green, for the painted pictures to be made round: a box of emerald high in
	 * the sky, everything seen by night vision so there are no lights to show, no fog, no story's
	 * darkening; it standing, bent down over the camera, turned three-quarters, and from below.
	 */
	private static void plates(ClientGameTestContext context, TestSingleplayerContext game, BlockPos spawn) {
		TestServerContext server = game.getServer();
		BlockPos centre = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos o = new BlockPos(spawn.getX() + 300, Math.min(level.getMaxY() - 30, 230), spawn.getZ() + 300);
			for (int cx = (o.getX() - 18) >> 4; cx <= (o.getX() + 18) >> 4; cx++) {
				for (int cz = (o.getZ() - 18) >> 4; cz <= (o.getZ() + 18) >> 4; cz++) level.getChunk(cx, cz);
			}
			BlockState green = Blocks.EMERALD_BLOCK.defaultBlockState();
			for (int x = -17; x <= 17; x++) {
				for (int z = -17; z <= 17; z++) {
					for (int y = -1; y <= 22; y++) {
						boolean shell = Math.abs(x) == 17 || Math.abs(z) == 17 || y == -1 || y == 22;
						set(level, o.offset(x, y, z), shell ? green : Blocks.AIR.defaultBlockState());
					}
				}
			}
			return o;
		});
		server.runCommand("gamemode spectator @p");
		server.runCommand("effect give @p minecraft:night_vision 600 0 true");
		context.runOnClient(mc -> {
			ScreenEffects.trigger(new ScreenEffectPayload(ScreenEffectPayload.ACT, 0, 0), mc);
			com.wolfsmask.occupant.client.ClientFog.set(0.0f, 1);
		});
		Cinematic.hud(context, false);
		Vec3 feet = Vec3.atBottomCenterOf(centre);
		plate(context, game, centre, feet.add(0, Cinematic.EYE, -10.5), feet.add(0, 2.3, 0), Cinematic.Look.STARING, "plate-stand");
		plate(context, game, centre, feet.add(0, 1.9, -3.4), feet.add(0, 2.9, 0), Cinematic.Look.looming(0), "plate-loom");
		plate(context, game, centre, feet.add(-4.5, Cinematic.EYE, -6.0), feet.add(0, 2.3, 0),
				Cinematic.Look.glancing(40f, com.wolfsmask.occupant.entity.OccupantEntity.Mode.STARE, 0), "plate-three-quarter");
		plate(context, game, centre, feet.add(0.3, 0.55, -1.9), feet.add(0, 3.6, 0), Cinematic.Look.looming(0), "plate-below");
		server.runCommand("effect clear @p minecraft:night_vision");
		Cinematic.hud(context, true);
	}

	private static void plate(ClientGameTestContext context, TestSingleplayerContext game, BlockPos it, Vec3 eye, Vec3 look,
							  Cinematic.Look pose, String name) {
		Cinematic.camera(context, game, eye, look, 6000);
		Cinematic.place(game.getServer(), it, eye, pose);
		settle(context, name);
	}

	/** Lets it settle into its pose and the light come right, then takes the picture. */
	/**
	 * Not for the page: the mercy at the edge of death, as it plays, frame by frame, so the way it
	 * moves can be looked at. First one husk, five steps off, that has all but killed them: their
	 * view is drawn round, a leg goes in, it is lifted, hangs, dies up there and is gone, then it is
	 * gone, then the words. Then seven: the three it can reach go up on its legs; the four round
	 * behind them it does not touch, and one by one they are gone.
	 */
	private static void rescue(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = server.computeOnServer(s -> ForestGallery.clearing(s.overworld(), woods[0].offset(-60, 0, 60)));
		if (floor == null) return;
		server.runOnServer(s -> Cinematic.fellTrees(s.overworld(), floor, 14));
		Cinematic.hud(context, true);
		// The test world is peaceful, where no monster can even be summoned.
		server.runCommand("difficulty normal");
		try {
			saved(context, game, floor, "rescue", new double[][]{{0, 5}},
					new int[]{6, 20, 27, 44, 62, 78, 101, 112}, new String[]{"appear", "turn", "stab", "lift", "hang", "dies", "gone", "words"});
			context.waitTicks(30);
			saved(context, game, floor, "rescue-many", new double[][]{{0, 4.5}, {-1.6, 5.2}, {1.7, 5.0}, {-3.2, 0.5}, {3.1, -0.4}, {-1.2, -3.0}, {1.8, -2.8}},
					new int[]{20, 40, 60, 80, 100, 120, 140, 160, 180, 200}, null);
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = Cinematic.player(s);
				Director.get().data(p).paused = true;
				p.setHealth(p.getMaxHealth());
			});
			server.runCommand("kill @e[type=minecraft:husk]");
			server.runCommand("difficulty peaceful");
		}
	}

	/**
	 * Husks at these places round {@code floor} (x and z, in blocks; the first is the one that has
	 * them), and they are about to die to the first: frames of what happens, at these ticks.
	 */
	private static void saved(ClientGameTestContext context, TestSingleplayerContext game, BlockPos floor, String name,
							  double[][] husks, int[] at, String[] names) {
		TestServerContext server = game.getServer();
		Vec3 eye = server.computeOnServer(s -> new Vec3(floor.getX() + 0.5,
				Cinematic.ground(s.overworld(), floor.getX(), floor.getZ()) + Cinematic.EYE, floor.getZ() + 0.5));
		Vec3 first = new Vec3(floor.getX() + 0.5 + husks[0][0], eye.y, floor.getZ() + 0.5 + husks[0][1]);
		// Looking a little above the first, so what comes to stand behind it is in the picture too.
		Cinematic.camera(context, game, eye, first.add(0, 0.7, 1.0), 6000);
		for (double[] h : husks) {
			server.runOnServer(s -> {
				int x = Mth.floor(floor.getX() + 0.5 + h[0]), z = Mth.floor(floor.getZ() + 0.5 + h[1]);
				int y = Cinematic.ground(s.overworld(), x, z);
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), String.format(Locale.ROOT,
						"summon minecraft:husk %.2f %d %.2f {PersistenceRequired:1b,NoAI:1b}", floor.getX() + 0.5 + h[0], y, floor.getZ() + 0.5 + h[1]));
			});
		}
		context.waitTicks(10);
		boolean begun = server.computeOnServer(s -> {
			ServerPlayer p = Cinematic.player(s);
			Mob it = s.overworld().getEntitiesOfClass(Mob.class, AABB.ofSize(first, 1.5, 6.0, 1.5), m -> Kinds.is(m, "husk"))
					.stream().findFirst().orElse(null);
			if (it == null) return false;
			HauntData d = Director.get().data(p);
			d.paused = false;
			d.introduced = true;
			Mercy.allowAgain(p);
			p.setHealth(1.0f);
			return !Mercy.allowDeath(p, p.damageSources().mobAttack(it));
		});
		Occupant.LOGGER.info("[client-gametest] {}: begun {}", name, begun);
		if (!begun) return;
		int waited = 0;
		for (int i = 0; i < at.length; i++) {
			context.waitTicks(at[i] - waited);
			waited = at[i];
			String label = names != null ? (i + 1) + "-" + names[i] : String.format(Locale.ROOT, "%03d", at[i]);
			Occupant.LOGGER.info("[client-gametest] {} {}: {} in sight", name, label, context.computeOnClient(OccupantClientGameTest::seen));
			OccupantClientGameTest.shoot(context, name + "-" + label);
		}
		// Until the scene is over, and their view their own again.
		for (int i = 0; i < 300 && context.computeOnClient(mc -> com.wolfsmask.occupant.client.Cutscene.active()); i++) context.waitTick();
	}

	private static void settle(ClientGameTestContext context, String name) {
		context.waitTicks(30);
		Occupant.LOGGER.info("[client-gametest] {}: {} in sight", name, context.computeOnClient(OccupantClientGameTest::seen));
		OccupantClientGameTest.shoot(context, name);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
