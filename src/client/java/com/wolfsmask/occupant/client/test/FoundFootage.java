package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.ScreenEffects;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
			// One torch, on the wall behind you; a bed against the side; nothing else.
			set(level, o.offset(2, 2, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
			set(level, o.offset(-2, 1, 1), Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
			set(level, o.offset(-2, 1, 2), Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
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

	/** Lets it settle into its pose and the light come right, then takes the picture. */
	private static void settle(ClientGameTestContext context, String name) {
		context.waitTicks(30);
		Occupant.LOGGER.info("[client-gametest] {}: {} in sight", name, context.computeOnClient(OccupantClientGameTest::seen));
		OccupantClientGameTest.shoot(context, name);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
