package com.wolfsmask.occupant.client.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.world.House;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The long video: the same camcorder as the short, held the same way, over a few days in a world
 * with the mod in it. Someone films their cabin; finds an empty house in the woods and something
 * in its hallway; a camp somebody left in a hurry; a graveyard, and past it, between the trees,
 * something watching; a knock on their door at night, and a face at the window; the morning after,
 * every door open, the room turned to face the bed, a grave outside with their name on it; their
 * mine, where a torch is gone, a tunnel is new, and something stands at the far end. Then the
 * night it has them (the short's ending, {@link Reel#found}, wide).
 * <p>
 * Each scene is its own clip, recorded when a run asks for it ({@code [tape:home,camp]} in what CI
 * writes into the flag), so the whole tape can be recorded over more than one run.
 */
final class Tape {
	private static final double EYE = Cinematic.EYE;
	/** Who they are: the name on the grave. */
	private static final String NAME = "Wolfsmask";

	private Tape() {
	}

	/** The scenes {@code asked} asks for ("[tape:home,camp]"), in order; none if it asks for none. */
	static List<String> scenes(String asked) {
		int at = asked.indexOf("[tape:");
		if (at < 0) return List.of();
		int end = asked.indexOf(']', at);
		if (end < 0) return List.of();
		List<String> out = new ArrayList<>();
		for (String s : asked.substring(at + 6, end).split(",")) {
			if (!s.isBlank()) out.add(s.trim());
		}
		return out;
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods, List<String> scenes) {
		for (String scene : scenes) {
			Reel.clip(scene, () -> {
				switch (scene) {
					case "home" -> home(context, game, woods);
					case "house" -> house(context, game, woods);
					case "camp" -> camp(context, game, woods);
					case "graves" -> graves(context, game, woods);
					case "knock" -> knock(context, game, woods);
					case "morning" -> morning(context, game, woods);
					case "mine" -> mine(context, game, woods);
					case "radio" -> radio(context, game, woods);
					case "chapel" -> chapel(context, game, woods);
					case "found" -> Reel.found(context, game, woods);
					default -> Occupant.LOGGER.warn("[client-gametest] tape: there is no scene {}", scene);
				}
			});
			game.getServer().runCommand("kill " + Reel.ALL);
			game.getServer().runCommand("kill @e[tag=tape]");
		}
	}

	// ------------------------------------------------------------------ their cabin

	private static final int DUSK = 0, NIGHT = 1, MORNING = 2;

	/** The way up to their door, out of the trees: the same every time, so the wood is too. */
	private static final double[][] CABIN_PATH = {{2.6, -34}, {2.0, -16}, {1.4, -11}, {0.3, -7.5}, {0, -5.3}};

	private static BlockPos cabinSite(TestSingleplayerContext game, BlockPos[] woods) {
		return site(game, woods[0].offset(-150, 0, 60));
	}

	/** Day one, the evening: walking back to the cabin they built, in through the door, round the room. */
	private static void home(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = cabinSite(game, woods);
		if (floor == null) return;
		BlockPos o = floor.above();
		cabin(server, o, DUSK);

		Vec3 start = Reel.local(o, 2.0, EYE, -16);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{2.0, -16}, {1.4, -11}, {0.3, -7.5}, {0, -5.3}}));
		Vec3 front = Reel.local(o, 0, 1.3, -3);
		f.face(front);
		Vec3 trees = Reel.local(o, -9, 2.2, -9);
		Vec3 roof = Reel.local(o, 0.5, 3.6, -2);
		f.walkOn(0.08, t -> t < 45 ? front : t < 85 ? trees : t < 110 ? roof : front);
		// A hand to the door, and it opens.
		f.hold(10, t -> Reel.local(o, 0, 0.9, -3), 0, 0.1);
		int opens = f.size();
		f.markAt(opens, "door");
		f.hold(8, t -> Reel.local(o, 0, 1.2, 0), 0, 0.1);
		f.follow(Reel.Way.local(o, new double[][]{{0, -5.3}, {0, -3}, {0, -1.0}}));
		f.walkOn(0.07, t -> Reel.local(o, 0, 1.2, 2.5));
		// Their things: the chest and the table, the frames, the bed, out of the window, the stand.
		f.hold(45, t -> Reel.local(o, -1.6, 0.9, 2.8), 0, 0.1);
		f.hold(35, t -> Reel.local(o, -0.5, 1.5, 3.4), 0, 0.1);
		f.hold(45, t -> Reel.local(o, 1.9, 0.9, 2.6), 0, 0.1);
		f.hold(45, t -> Reel.local(o, 3.4, 1.5, 1.0), 0, 0.1);
		f.hold(40, t -> Reel.local(o, -2, 1.3, -1), 0, 0.1);
		f.hold(30, t -> Reel.local(o, 0, 1.4, -3), 0, 0.1);
		f.mark("end");

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, front, 12200);
		cabinThings(server, o, DUSK);
		context.waitTicks(10);
		Reel.record(context, server, "home", poses, Integer.MAX_VALUE, f, t -> {
			if (t == opens) server.runOnServer(s -> door(s.overworld(), o.offset(0, 0, -3), Direction.SOUTH, true));
		});
	}

	/**
	 * A night later, inside, the door shut: sitting on the bed filming. Three slow knocks on the
	 * door. Frozen; up; a step or two towards it; and a tap on the glass behind them: it is at the
	 * window, bent down to it, looking in. A look back at the door, and it is gone.
	 */
	private static void knock(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = cabinSite(game, woods);
		if (floor == null) return;
		BlockPos o = floor.above();
		cabin(server, o, NIGHT);

		Vec3 start = Reel.local(o, 1.6, EYE, 2.0);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.lift = -0.3;
		Vec3 door = Reel.local(o, 0, 1.0, -3);
		Vec3 window = Reel.local(o, -3, 1.5, 0.5);
		f.face(door);
		// Sitting up late, filming the room: the door, the window, the torchlight.
		f.hold(35, t -> Reel.local(o, -0.6, 1.2, -2.0), 0.3, 0.15);
		f.hold(40, t -> window, 0.3, 0.15);
		f.hold(30, t -> Reel.local(o, -1.2, 1.4, -1.4), 0.3, 0.15);
		// Three knocks, slow.
		int knock = f.size() + 6;
		f.markAt(knock, "knock");
		f.markAt(knock + 17, "knock");
		f.markAt(knock + 34, "knock");
		f.flinchAt(knock + 1, door, 0.9);
		f.hold(6, t -> Reel.local(o, -1.2, 1.4, -1.4), 0.3, 0.15);
		f.hold(110, t -> door, 0.3, 0.9);
		// Up, slowly, and a step or two towards the door.
		f.hold(35, t -> door, 0.0, 0.7);
		f.follow(Reel.Way.local(o, new double[][]{{1.6, 2.0}, {0.9, 0.6}, {0.6, -0.2}}));
		f.walkOn(0.03, t -> door);
		f.hold(18, t -> door, 0, 0.8);
		// Behind them, a tap on the glass.
		Vec3 feet = Reel.local(o, -5, 0, 0.5);
		Vec3 face = Reel.local(o, -4.6, 1.55, 0.5);
		int placed = f.size() - 12;
		int tap = f.size() + 4;
		f.markAt(tap, "tap");
		f.flinchAt(tap + 1, window, 1.0);
		f.hold(4, t -> door, 0, 0.8);
		f.hold(14, t -> window, 0, 1.0);
		int seen = f.size() - 4;
		f.markAt(seen, "seen");
		f.flinchAt(seen, face, 1.3);
		f.hold(52, t -> face, 0, 1.4);
		f.backAway(12, face, 0.05);
		// The door: did it come from there? And back: nothing at the window.
		f.hold(28, t -> door, 0, 1.0);
		int gone = f.size() - 18;
		f.hold(58, t -> window, 0, 1.0);
		f.markAt(f.size() - 34, "what");
		f.mark("end");
		f.watching(t -> t >= seen - 6 && t < gone ? face : null);

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, door, 18000);
		cabinThings(server, o, NIGHT);
		context.waitTicks(10);
		Cinematic.Look bent = new Cinematic.Look(false, 0f, OccupantEntity.Mode.AMBUSH, 0);
		Reel.record(context, server, "knock", poses, Integer.MAX_VALUE, f, t -> {
			if (t == placed) Cinematic.place(server, BlockPos.containing(feet), eye(poses, t), bent);
			if (t >= placed && t < gone) Reel.look(server, feet, eye(poses, t), 0);
			if (t == gone) server.runCommand("kill " + Reel.ALL);
		});
	}

	/**
	 * The morning after: waking, every door standing open, the torch by the bed burning red, the
	 * pictures upside down, the stand turned to face the bed; and outside, round the side, a grave,
	 * fresh, with their name on it.
	 */
	private static void morning(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = cabinSite(game, woods);
		if (floor == null) return;
		BlockPos o = floor.above();
		cabin(server, o, MORNING);

		Vec3 start = Reel.local(o, 2.0, EYE, 2.85);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.lift = -0.88;
		f.face(Reel.local(o, 1.7, 3.0, 1.6));
		// Lying there, the ceiling; somewhere outside, the last shovelful.
		f.hold(45, t -> Reel.local(o, 1.6 - 0.006 * t, 3.0, 1.5), 0.88, 0.05);
		f.markAt(f.size() + 5, "shovel");
		f.hold(35, t -> Reel.local(o, 0.4, 2.0, -1.0), 0.88, 0.1);
		// Sitting up: the door is standing open.
		Vec3 door = Reel.local(o, 0, 1.1, -3);
		f.hold(40, t -> door, 0.4, 0.3);
		f.hold(30, t -> door, 0.4, 0.4);
		// The torch by the bed, red. The frames. The stand, turned to the bed.
		f.hold(45, t -> Reel.local(o, 1.0, 1.4, 3.3), 0.4, 0.5);
		f.hold(40, t -> Reel.local(o, -0.5, 1.5, 3.4), 0.4, 0.5);
		f.hold(55, t -> Reel.local(o, -2, 1.5, -1), 0.4, 0.8);
		f.hold(30, t -> door, 0.0, 0.6);
		// Out of the door and round the side.
		f.follow(Reel.Way.local(o, new double[][]{{2.0, 2.85}, {0.9, 1.6}, {0.2, -0.6}, {0, -3}, {0, -5.4}, {2.4, -5.8},
				{4.6, -4.4}, {4.6, -0.3}}));
		Vec3 hole = Reel.local(o, 7, -1.2, 1.5);
		Vec3 sign = Reel.local(o, 7, 0.5, 3);
		double gx = o.getX() + 4.0, gz = o.getZ() - 3.5;
		f.walkOn(0.08, t -> f.x > gx && f.z > gz ? hole : f.onWay(4.0, 0, -0.2));
		f.hold(50, t -> hole, 0.1, 0.9);
		f.follow(Reel.Way.local(o, new double[][]{{4.6, -0.3}, {4.4, 1.5}, {4.3, 2.9}}));
		f.walkOn(0.045, t -> sign);
		f.hold(95, t -> sign, 0.45, 1.1);
		f.mark("end");

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, door, 23300);
		cabinThings(server, o, MORNING);
		context.waitTicks(10);
		Reel.record(context, server, "morning", poses, Integer.MAX_VALUE, f, null);
	}

	/** The cabin they built: a room of spruce boards on a stone course, a door, windows, a bed. */
	private static void cabin(TestServerContext server, BlockPos o, int when) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -30, 30, -40, 30, List.of(Reel.Way.local(o, CABIN_PATH)), 41L, new double[][]{{1, 0.5, 9.5}}, null);
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 5; z++) {
					for (int y = 0; y <= 6; y++) Reel.set(level, o.offset(x, y, z), air);
				}
			}
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 4; z++) {
					boolean wall = x == -3 || x == 3 || z == -3 || z == 4;
					Reel.set(level, o.offset(x, -1, z), (wall ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS).defaultBlockState());
					if (!wall) continue;
					Reel.set(level, o.offset(x, 0, z), ((x * 7 + z * 3) % 5 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
					Reel.set(level, o.offset(x, 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
					Reel.set(level, o.offset(x, 2, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
			for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 4}, {3, 4}}) {
				for (int y = 0; y <= 2; y++) Reel.set(level, o.offset(c[0], y, c[1]), Blocks.DARK_OAK_LOG.defaultBlockState());
			}
			// The roof: a layer of boards, eaves round it, and a step up.
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 4; z++) Reel.set(level, o.offset(x, 3, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
			}
			BlockState eave = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			for (int x = -4; x <= 4; x++) {
				Reel.set(level, o.offset(x, 2, -4), eave);
				Reel.set(level, o.offset(x, 2, 5), eave);
			}
			for (int z = -3; z <= 4; z++) {
				Reel.set(level, o.offset(-4, 2, z), eave);
				Reel.set(level, o.offset(4, 2, z), eave);
			}
			BlockState ridge = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 3; z++) Reel.set(level, o.offset(x, 4, z), ridge);
			}
			// The door, shut at night; the windows.
			door(level, o.offset(0, 0, -3), Direction.SOUTH, when == MORNING);
			pane(level, o.offset(-3, 1, 0), true);
			pane(level, o.offset(-3, 1, 1), true);
			pane(level, o.offset(3, 1, 1), true);
			pane(level, o.offset(-2, 1, -3), false);
			pane(level, o.offset(2, 1, -3), false);
			// Inside: a chest, a table and a furnace, three torches (the bed and the rug below).
			Reel.set(level, o.offset(-2, 0, 3), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			Reel.set(level, o.offset(-1, 0, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
			Reel.set(level, o.offset(-2, 0, 2), Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			// The one by the bed is the one that comes back red.
			Reel.set(level, o.offset(1, 1, 3), (when == MORNING ? Blocks.REDSTONE_WALL_TORCH : Blocks.WALL_TORCH).defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			Reel.set(level, o.offset(-2, 1, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			Reel.set(level, o.offset(2, 1, -1), Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
			// Outside: logs stacked against the back of the west wall, a torch on a post by the door.
			BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
			Reel.set(level, o.offset(-4, 0, 2), log);
			Reel.set(level, o.offset(-4, 0, 3), log);
			Reel.set(level, o.offset(-4, 1, 3), log);
			Reel.set(level, o.offset(-1, 0, -5), Blocks.SPRUCE_FENCE.defaultBlockState());
			Reel.set(level, o.offset(-1, 1, -5), Blocks.TORCH.defaultBlockState());
			if (when == MORNING) grave(level, o);
		});
	}

	private static void setblock(TestServerContext server, BlockPos at, String block) {
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:%s", at.getX(), at.getY(), at.getZ(), block));
	}

	/** The bed, the rug, the stand by the door and the two frames over the chest: turned, in the morning. */
	private static void cabinThings(TestServerContext server, BlockPos o, int when) {
		// The bed and the rug by name (the game has changed what it calls its coloured blocks), once
		// the camera is there and the cabin is loaded.
		setblock(server, o.offset(2, 0, 2), "red_bed[facing=south,part=foot]");
		setblock(server, o.offset(2, 0, 3), "red_bed[facing=south,part=head]");
		for (int x = -1; x <= 0; x++) {
			for (int z = 0; z <= 1; z++) setblock(server, o.offset(x, 0, z), "brown_carpet");
		}
		server.runCommand("kill @e[tag=tape]");
		Vec3 stand = Reel.local(o, -2, 0, -1);
		Vec3 bed = Reel.local(o, 2, 0, 2.5);
		float yaw = when == MORNING ? (float) Reel.Fear.yawBetween(stand, bed) : -90.0f;
		server.runCommand(String.format(Locale.ROOT, "summon armor_stand %.3f %.3f %.3f {Rotation:[%.1ff,0.0f],Tags:[\"tape\",\"tape_stand\"]}",
				stand.x, stand.y, stand.z, yaw));
		server.runCommand("item replace entity @e[tag=tape_stand,limit=1] armor.head with minecraft:leather_helmet");
		server.runCommand("item replace entity @e[tag=tape_stand,limit=1] armor.chest with minecraft:leather_chestplate");
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			int turned = when == MORNING ? 4 : 0;
			ItemFrame sword = new ItemFrame(level, o.offset(0, 1, 3), Direction.NORTH);
			sword.setItem(new ItemStack(Items.IRON_SWORD));
			sword.setRotation(turned);
			sword.addTag("tape");
			level.addFreshEntity(sword);
			ItemFrame pick = new ItemFrame(level, o.offset(-1, 1, 3), Direction.NORTH);
			pick.setItem(new ItemStack(Items.IRON_PICKAXE));
			pick.setRotation(1 + turned);
			pick.addTag("tape");
			level.addFreshEntity(pick);
		});
	}

	/** A grave dug in the night round the side of the cabin, as the mod digs one, and the sign at its head. */
	private static void grave(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = 1; z <= 2; z++) {
			for (int y = -2; y <= 0; y++) Reel.set(level, o.offset(7, y, z), air);
			Reel.set(level, o.offset(6, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
		}
		BlockPos at = o.offset(7, 0, 3);
		float yaw = (float) Reel.Fear.yawBetween(Vec3.atBottomCenterOf(at), Vec3.atBottomCenterOf(o.offset(2, 0, 2)));
		BlockState state = Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, RotationSegment.convertToSegment(yaw));
		level.setBlock(at, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
			Compat.writeSign(sign, new String[]{"Here lies", NAME, "", "Day 4"}, true);
			sign.setWaxed(true);
			sign.setChanged();
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------ out in the woods

	/**
	 * Day two: an empty house in the woods, found by walking. In through the open door, round the
	 * room; past the gap in the inside wall, back in the dark of the hallway, something. A look
	 * away, and it is gone. Into the hallway after it, and nothing; and out, quickly.
	 */
	private static void house(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = site(game, woods[0].offset(170, 0, -130));
		if (floor == null) return;
		BlockPos o = floor.above();
		double[][] approach = {{-3.5, -36}, {-3.5, -22}, {-2.6, -15}, {-2.2, -10}, {-2.0, -7.2}};
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -32, 30, -42, 20, List.of(Reel.Way.local(o, approach)), 71L, new double[][]{{-0.5, 0, 11.5}}, null);
			House.build(level, floor, Rotation.NONE, RandomSource.create(9L));
		});

		Vec3 start = Reel.local(o, -3.5, EYE, -22);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{-3.5, -22}, {-2.6, -15}, {-2.2, -10}, {-2.0, -7.2}}));
		Vec3 doorway = Reel.local(o, -2, 1.4, -5);
		f.face(doorway);
		Vec3 roof = Reel.local(o, 0, 4.6, -3);
		Vec3 windows = Reel.local(o, -4.5, 2.1, -5);
		f.walkOn(0.08, t -> t < 60 ? doorway : t < 100 ? roof : t < 140 ? windows : doorway);
		f.hold(30, t -> Reel.local(o, -2, 1.2, -2), 0, 0.3);
		f.follow(Reel.Way.local(o, new double[][]{{-2, -7.2}, {-2, -5}, {-1.9, -1.6}}));
		f.walkOn(0.06, t -> Reel.local(o, -2, 1.3, 3));
		// Round the room: the window, the table, the shelves at the back; on round to the right.
		int placed = f.size();
		f.hold(40, t -> Reel.local(o, -6, 2.1, -1), 0, 0.3);
		f.hold(35, t -> Reel.local(o, -3.5, 0.9, 1), 0, 0.3);
		f.hold(40, t -> Reel.local(o, -2, 1.2, 4), 0, 0.3);
		Vec3 feet = Reel.local(o, 3.5, 0, -1.4);
		Vec3 face = Reel.local(o, 3.5, 2.5, -1.4);
		f.hold(26, t -> Reel.local(o, 1.0, 1.0, -4), 0, 0.3);
		// Back: there, in the gap, in the dark.
		int seen = f.size() + 4;
		f.markAt(seen, "seen");
		f.flinchAt(seen, face, 1.2);
		f.hold(18, t -> face, 0, 1.0);
		f.hold(45, t -> face, 0, 1.3);
		f.backAway(10, face, 0.05);
		f.hold(22, t -> doorway, 0, 1.0);
		int gone = f.size() - 16;
		f.hold(32, t -> face, 0, 1.0);
		f.markAt(f.size() - 16, "what");
		// After it, into the hallway: nothing, either way. And out.
		f.follow(Reel.Way.local(o, new double[][]{{f.x - o.getX() - 0.5, f.z - o.getZ() - 0.5}, {0.6, -2.5}, {2.0, -2.5}, {3.3, -2.5}}));
		f.walkOn(0.045, t -> Reel.local(o, 3.0, 1.5, -2.5));
		f.hold(45, t -> Reel.local(o, 3.5, 1.3, 4), 0, 0.9);
		f.hold(30, t -> Reel.local(o, 3.5, 1.3, -4.2), 0, 0.9);
		f.follow(Reel.Way.local(o, new double[][]{{3.3, -2.5}, {2.0, -2.5}, {-2.2, -2.3}, {-2.3, -3.2}, {-2, -5}, {-2, -9}}));
		int out = f.size();
		while (f.size() < out + 74) f.walk(1, f.z < o.getZ() - 1.0 ? 0.13 : 0.1, t -> f.onWay(3.0, 0, -0.2));
		f.mark("end");
		f.watching(t -> t >= placed && t < gone ? face : null);

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, doorway, 11500);
		Reel.record(context, server, "house", poses, Integer.MAX_VALUE, f, t -> {
			if (t == placed) Cinematic.place(server, BlockPos.containing(feet), eye(poses, t), Reel.STANDING);
			if (t >= placed && t < gone) Reel.look(server, feet, eye(poses, t), 0);
			if (t == gone) server.runCommand("kill " + Reel.ALL);
		});
	}

	/**
	 * A camp somebody left: the fire long cold, logs round it, a tent with a barrel in it, a chest,
	 * and a sign at the edge for whoever comes. A whisper, behind them, and nothing there.
	 */
	private static void camp(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = site(game, woods[0].offset(-80, 0, -260));
		if (floor == null) return;
		BlockPos o = floor.above();
		double[][] approach = {{-2.5, -34}, {-1.5, -18}, {-0.8, -12}, {0.2, -8.5}, {0.2, -6.6}};
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -28, 28, -38, 22, List.of(Reel.Way.local(o, approach)), 83L, new double[][]{{1.5, -0.5, 8.5}}, null);
			campBuild(level, o);
		});

		Vec3 start = Reel.local(o, -1.5, EYE, -18);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{-1.5, -18}, {-0.8, -12}, {0.2, -8.5}, {0.2, -6.6}}));
		Vec3 tent = Reel.local(o, 3, 1.2, -1);
		Vec3 fire = Reel.local(o, 0, 0.2, 0);
		Vec3 sign = Reel.local(o, 0, 0.7, -4);
		f.face(tent);
		f.walkOn(0.08, t -> t < 60 ? tent : t < 100 ? fire : sign);
		// The sign: stooping to read it.
		f.hold(15, t -> sign, 0, 0.15);
		f.hold(60, t -> sign, 0.3, 0.2);
		f.follow(Reel.Way.local(o, new double[][]{{0.2, -6.6}, {1.4, -5.6}, {3.0, -4.2}}));
		f.walkOn(0.07, t -> t < 30 ? fire : tent);
		// Into the tent, crouching.
		Vec3 inside = Reel.local(o, 3, 0.6, -1.2);
		f.hold(15, t -> inside, 0, 0.2);
		f.hold(55, t -> inside, 0.75, 0.25);
		// Up: the cold fire, the chest.
		f.hold(30, t -> fire, 0, 0.2);
		f.hold(30, t -> Reel.local(o, -3, 0.5, 2), 0, 0.2);
		// A whisper behind them. Round: the trees. Nothing.
		Vec3 behind = Reel.local(o, -4, 1.6, -12);
		int whisper = f.size() + 6;
		f.markAt(whisper, "whisper");
		f.hold(10, t -> Reel.local(o, -3, 0.5, 2), 0, 0.2);
		f.flinchAt(whisper + 2, behind, 1.0);
		f.swingTo(9, behind);
		f.hold(48, t -> behind, 0, 1.0);
		f.mark("end");

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, tent, 12300);
		Reel.record(context, server, "camp", poses, Integer.MAX_VALUE, f, null);
	}

	/** The camp as the mod builds one (see Camp), set out the same every time. */
	private static void campBuild(ServerLevel level, BlockPos o) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) Reel.set(level, o.offset(x, -1, z), Blocks.COARSE_DIRT.defaultBlockState());
		}
		Reel.set(level, o, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		BlockState along = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		BlockState across = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		Reel.set(level, o.offset(-2, 0, -1), along);
		Reel.set(level, o.offset(-2, 0, 0), along);
		Reel.set(level, o.offset(-1, 0, 2), across);
		Reel.set(level, o.offset(0, 0, 2), across);
		// The tent: an A-frame of boards, open at the front, the back boarded up.
		for (int z = -2; z <= 0; z++) {
			for (int x = 1; x <= 5; x++) {
				for (int y = 0; y <= 3; y++) Reel.set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
			}
			Reel.set(level, o.offset(1, 0, z), stairs(Direction.EAST));
			Reel.set(level, o.offset(5, 0, z), stairs(Direction.WEST));
			Reel.set(level, o.offset(2, 1, z), stairs(Direction.EAST));
			Reel.set(level, o.offset(4, 1, z), stairs(Direction.WEST));
			Reel.set(level, o.offset(3, 2, z), Blocks.SPRUCE_SLAB.defaultBlockState());
		}
		for (int x = 2; x <= 4; x++) Reel.set(level, o.offset(x, 0, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
		Reel.set(level, o.offset(3, 1, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
		Reel.set(level, o.offset(3, 0, -1), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		Reel.set(level, o.offset(-3, 0, 2), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		sign(level, o.offset(0, 0, -4), Blocks.OAK_SIGN, 8, new String[]{"DON'T FOLLOW", "THE TALL ONE", "", ""});
	}

	/**
	 * Day three, nightfall: a graveyard in the woods. The sign at the gate; along the rows; one
	 * grave dug open, a chest at the bottom of it. Looking up: past the back fence, between the
	 * trees, something tall, watching. The camcorder zoomed in on it; zoomed out, a look away,
	 * and it is not there.
	 */
	private static void graves(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = site(game, woods[1].offset(-150, 0, 260));
		if (floor == null) return;
		BlockPos o = floor.above();
		double[][] approach = {{1.2, -34}, {0.8, -20}, {0.4, -13}, {0.3, -7.3}};
		double[][] trail = {{1.5, 5}, {1.2, 16}, {1.0, 30}, {0.4, 40}};
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -28, 28, -38, 40, List.of(Reel.Way.local(o, approach), Reel.Way.local(o, trail)), 97L,
					new double[][]{{0, 0, 8.5}, {1, 20, 3.5}}, null);
			graveyard(level, o);
		});

		Vec3 start = Reel.local(o, 0.8, EYE, -20);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{0.8, -20}, {0.4, -13}, {0.3, -7.3}}));
		Vec3 middle = Reel.local(o, 0, 1.0, 0);
		Vec3 sign = Reel.local(o, 0, 0.75, -5);
		f.face(middle);
		f.walkOn(0.08, t -> t < 70 ? middle : t < 110 ? Reel.local(o, -3, 0.6, -2) : sign);
		f.hold(60, t -> sign, 0.2, 0.2);
		// In at the gate and up between the rows, to the open one.
		double oz = o.getZ() + 0.5;
		f.follow(Reel.Way.local(o, new double[][]{{0.3, -7.3}, {-0.9, -6.2}, {-0.6, -4.8}, {0, -4}, {1.0, -3.2}, {1.5, -1.5}, {1.45, 0.6}}));
		Vec3 open = Reel.local(o, 0, -1.6, 1.4);
		f.walkOn(0.06, t -> f.z < oz - 3.5 ? Reel.local(o, -2, 0.6, -1) : open);
		int placed = f.size();
		f.hold(55, t -> open, 0.25, 0.35);
		// Up. Past the fence, in the trees.
		Vec3 itFeet = Reel.local(o, 1, 0, 20);
		Vec3 itFace = Reel.local(o, 1, 4.0, 20);
		f.hold(22, t -> itFace, 0, 0.4);
		int seen = f.size() - 6;
		f.markAt(seen, "seen");
		f.flinchAt(seen, itFace, 0.8);
		// Zoomed in on it, held as still as they can.
		int zoomIn = f.size() + 10;
		f.markAt(zoomIn, "zoomin");
		f.hold(90, t -> itFace, 0, 1.2);
		int zoomOut = f.size();
		f.markAt(zoomOut, "zoomout");
		// Out again, a look away to the gate, and back: gone.
		f.hold(25, t -> Reel.local(o, 0, 1.4, -4.5), 0, 1.0);
		int gone = f.size() - 15;
		f.hold(50, t -> itFace, 0, 1.0);
		f.markAt(f.size() - 30, "what");
		f.mark("end");
		f.watching(t -> t >= placed && t < gone ? itFace : null);
		int lens = Reel.WIDE_FOV;
		int[] fov = new int[f.size()];
		for (int t = 0; t < fov.length; t++) {
			double in = Reel.smooth((t - zoomIn) / 30.0) - Reel.smooth((t - zoomOut) / 8.0);
			fov[t] = (int) Math.round(lens - (lens - 22) * in);
		}

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, middle, 13400);
		Reel.record(context, server, "graves", poses, Integer.MAX_VALUE, f, t -> {
			if (t == placed) Cinematic.place(server, BlockPos.containing(itFeet), eye(poses, t), Reel.STANDING);
			if (t >= placed && t < gone) Reel.look(server, itFeet, eye(poses, t), 0);
			if (t == gone) server.runCommand("kill " + Reel.ALL);
		}, fov);
	}

	/** The graveyard as the mod builds one (see Graves): two rows of three, one dug open, a fence, a sign at the gate. */
	private static void graveyard(ServerLevel level, BlockPos o) {
		int[] xs = {-3, 0, 3}, zs = {-2, 1};
		int x0 = -5, x1 = 5, z0 = -4, z1 = 4;
		// The fence, with the gate and a few gaps where it has rotted.
		java.util.Set<Long> cells = new java.util.HashSet<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge || (x == 0 && z == z0) || (x == x0 && z == 1) || (x == 3 && z == z1) || (x == x1 && z == -2)) continue;
				cells.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
			}
		}
		for (long c : cells) {
			int x = (int) (c >> 32), z = (int) c;
			BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState()
					.setValue(BlockStateProperties.NORTH, cells.contains(((long) x << 32) ^ ((z - 1) & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.SOUTH, cells.contains(((long) x << 32) ^ ((z + 1) & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.WEST, cells.contains(((long) (x - 1) << 32) ^ (z & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.EAST, cells.contains(((long) (x + 1) << 32) ^ (z & 0xFFFFFFFFL)));
			Reel.set(level, o.offset(x, 0, z), fence);
		}
		Block[] stones = {Blocks.MOSSY_COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL};
		int n = 0;
		for (int z : zs) {
			for (int x : xs) {
				boolean open = x == 0 && z == 1;
				if (open) {
					for (int y = -2; y <= -1; y++) {
						Reel.set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
						Reel.set(level, o.offset(x, y, z + 1), Blocks.AIR.defaultBlockState());
					}
					Reel.set(level, o.offset(x, -2, z), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
					Reel.set(level, o.offset(x, -3, z + 1), Blocks.DIRT.defaultBlockState());
				} else {
					Reel.set(level, o.offset(x, -1, z), Blocks.COARSE_DIRT.defaultBlockState());
					Reel.set(level, o.offset(x, -1, z + 1), Blocks.COARSE_DIRT.defaultBlockState());
				}
				Reel.set(level, o.offset(x, 0, z - 1), stones[n % stones.length].defaultBlockState());
				if (n % 2 == 0) Reel.set(level, o.offset(x, 1, z - 1), Blocks.CANDLE.defaultBlockState());
				n++;
			}
		}
		sign(level, o.offset(0, 0, z0 - 1), Blocks.OAK_SIGN, 8, new String[]{"IT TOOK", "THE REST", "", ""});
	}

	/**
	 * Day three: a ranger's radio shack in the woods, its aerial up over the roof, and a sign out
	 * front. Inside, the set on the table. It picks something up: static, and in it, a voice.
	 * Backing out of there.
	 */
	private static void radio(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = site(game, woods[1].offset(230, 0, 120));
		if (floor == null) return;
		BlockPos o = floor.above();
		double[][] approach = {{0.8, -34}, {0.6, -22}, {0.4, -12}, {0.6, -6.2}};
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -28, 28, -38, 22, List.of(Reel.Way.local(o, approach)), 59L, new double[][]{{0, 0, 7.5}}, null);
			shack(level, o);
		});

		Vec3 start = Reel.local(o, 0.6, EYE, -20);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{0.6, -20}, {0.4, -12}, {0.6, -6.2}}));
		Vec3 shack = Reel.local(o, 0, 1.4, -2);
		Vec3 aerial = Reel.local(o, 2, 8, 3);
		Vec3 sign = Reel.local(o, 1, 0.6, -4);
		f.face(shack);
		f.walkOn(0.08, t -> t < 55 ? shack : t < 95 ? aerial : sign);
		f.hold(15, t -> sign, 0, 0.15);
		f.hold(55, t -> sign, 0.25, 0.2);
		// Up onto the porch and in.
		f.follow(Reel.Way.local(o, new double[][]{{0.6, -6.2}, {-0.2, -5.0}, {0, -3.5}, {0, -2}, {0, -0.7}}));
		Vec3 set = Reel.local(o, 0, 0.6, 2);
		f.walkOn(0.055, t -> f.z > o.getZ() - 3.0 ? set : Reel.local(o, 0, 1.3, -1));
		// The set: the dials, the lever. Looking it over, leaning in.
		f.hold(40, t -> Reel.local(o, -0.6, 0.6, 2.2), 0.1, 0.2);
		f.hold(35, t -> Reel.local(o, 0.8, 0.6, 2.2), 0.25, 0.2);
		// It comes on by itself: static, and through it, a voice.
		int hiss = f.size() + 4;
		f.markAt(hiss, "static");
		f.flinchAt(hiss + 1, set, 1.0);
		f.hold(20, t -> set, 0.0, 0.8);
		f.markAt(f.size() + 2, "radio");
		f.hold(70, t -> set, 0.0, 1.2);
		f.markAt(f.size() - 22, "static");
		// Out, backwards, the camera still on it.
		f.follow(Reel.Way.local(o, new double[][]{{0, -0.7}, {0, -2}, {0, -3.6}, {-0.3, -6.0}}));
		int out = f.size();
		while (f.size() < out + 56) f.backAway(1, set, 0.07);
		f.hold(30, t -> shack, 0, 1.0);
		f.mark("end");

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, shack, 10800);
		Reel.record(context, server, "radio", poses, Integer.MAX_VALUE, f, null);
	}

	/** The radio shack as the mod builds one (see RadioShack): one room, a porch, the set, the aerial, the sign. */
	private static void shack(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -3; x <= 3; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = 0; y <= 5; y++) Reel.set(level, o.offset(x, y, z), air);
			}
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 3; z++) {
				Reel.set(level, o.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
				boolean edge = x == -2 || x == 2 || z == -2 || z == 3;
				boolean corner = Math.abs(x) == 2 && (z == -2 || z == 3);
				if (edge) {
					for (int y = 0; y <= 2; y++) {
						Reel.set(level, o.offset(x, y, z), (corner ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS).defaultBlockState());
					}
				}
				Reel.set(level, o.offset(x, 3, z), Blocks.SPRUCE_SLAB.defaultBlockState());
			}
		}
		Reel.set(level, o.offset(0, 0, -2), air);
		Reel.set(level, o.offset(0, 1, -2), air);
		pane(level, o.offset(2, 1, 0), true);
		pane(level, o.offset(-2, 1, 0), true);
		pane(level, o.offset(1, 1, -2), false);
		// The porch, its rail posts, its roof.
		BlockState deck = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		for (int x = -2; x <= 2; x++) {
			Reel.set(level, o.offset(x, -1, -3), deck);
			Reel.set(level, o.offset(x, 3, -3), Blocks.SPRUCE_SLAB.defaultBlockState());
		}
		for (int x : new int[]{-2, 2}) {
			for (int y = 0; y <= 2; y++) Reel.set(level, o.offset(x, y, -3), Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		// The set on its table: the radio, the dials, the switch; the aerial over the back corner.
		Reel.set(level, o.offset(-1, 0, 2), Blocks.JUKEBOX.defaultBlockState());
		Reel.set(level, o.offset(0, 0, 2), Blocks.NOTE_BLOCK.defaultBlockState());
		Reel.set(level, o.offset(1, 0, 2), Blocks.LEVER.defaultBlockState());
		for (int y = 4; y <= 10; y++) Reel.set(level, o.offset(2, y, 3), Blocks.IRON_BARS.defaultBlockState());
		Reel.set(level, o.offset(1, 0, -1), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		Reel.set(level, o.offset(-1, 0, -1), Blocks.CRAFTING_TABLE.defaultBlockState());
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		Reel.set(level, o.offset(3, 0, 0), log);
		Reel.set(level, o.offset(3, 1, 0), log);
		Reel.set(level, o.offset(3, 0, 1), log);
		sign(level, o.offset(1, 0, -4), Blocks.OAK_SIGN, 8, new String[]{"", "I HEARD MY", "OWN VOICE", "ON IT"});
	}

	/**
	 * Day three, dusk: a chapel in the woods, long given up. Up the aisle between the pews; every
	 * pew faces the altar but one, turned round to face the door. The candles on the altar burnt
	 * out. A look back at the door; and when they look at the altar again, the candles are lit.
	 */
	private static void chapel(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos floor = site(game, woods[0].offset(-260, 0, -60));
		if (floor == null) return;
		BlockPos o = floor.above();
		double[][] approach = {{0.8, -36}, {0.5, -26}, {0.2, -14}, {0, -8.5}};
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Reel.wood(level, o, -28, 28, -40, 22, List.of(Reel.Way.local(o, approach)), 131L, new double[][]{{0, 1, 9.5}}, null);
			nave(level, o);
		});

		Vec3 start = Reel.local(o, 0.5, EYE, -24);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{0.5, -24}, {0.2, -14}, {0, -8.5}}));
		Vec3 door = Reel.local(o, 0, 1.2, -5);
		Vec3 gable = Reel.local(o, 0, 7.0, -5);
		f.face(door);
		f.walkOn(0.08, t -> t < 60 ? door : t < 110 ? gable : door);
		f.hold(35, t -> gable, 0, 0.2);
		f.hold(20, t -> door, 0, 0.25);
		// In, and slowly up the aisle.
		Vec3 altar = Reel.local(o, 0, 1.0, 6);
		f.follow(Reel.Way.local(o, new double[][]{{0, -8.5}, {0, -5}, {0, -0.3}}));
		f.walkOn(0.05, t -> t < 50 ? altar : t < 80 ? Reel.local(o, -3, 2.4, -3) : t < 110 ? Reel.local(o, 1.5, 0.6, -1) : altar);
		// The pew in front of them faces the wrong way: it faces them.
		f.hold(40, t -> Reel.local(o, -1.5, 0.6, 1), 0, 0.4);
		f.hold(30, t -> Reel.local(o, 1.5, 0.6, 1), 0, 0.5);
		f.hold(45, t -> altar, 0, 0.5);
		// A look back at the door.
		f.hold(48, t -> door, 0, 0.6);
		int lit = f.size() - 30;
		f.markAt(lit, "light");
		// The altar again: the candles are burning.
		Vec3 candles = Reel.local(o, 0, 1.3, 6);
		f.hold(16, t -> candles, 0, 0.8);
		int seen = f.size() - 4;
		f.markAt(seen, "seen");
		f.flinchAt(seen, candles, 0.9);
		f.hold(60, t -> candles, 0, 1.2);
		f.markAt(f.size() - 30, "what");
		f.follow(Reel.Way.local(o, new double[][]{{0, -0.3}, {0, -4.5}}));
		int out = f.size();
		while (f.size() < out + 30) f.backAway(1, candles, 0.06);
		f.mark("end");

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, door, 12900);
		Reel.record(context, server, "chapel", poses, Integer.MAX_VALUE, f, t -> {
			if (t == lit) server.runOnServer(s -> {
				for (int x : new int[]{-1, 1}) {
					Reel.set(s.overworld(), o.offset(x, 1, 6), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
			});
		});
	}

	/** The chapel as the mod builds one (see Chapel): stone, pews in rows, one turned, the altar at the back. */
	private static void nave(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -4; x <= 4; x++) {
			for (int z = -6; z <= 8; z++) {
				for (int y = 0; y <= 10; y++) Reel.set(level, o.offset(x, y, z), air);
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -5; z <= 7; z++) {
				Reel.set(level, o.offset(x, -1, z), ((x * 5 + z * 3) % 4 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState());
				boolean edge = x == -3 || x == 3 || z == -5 || z == 7;
				if (!edge) continue;
				for (int y = 0; y <= 4; y++) {
					if (y >= 2 && (x * 7 + z * 11 + y * 5) % 17 == 0) continue;     // fallen out
					int k = (x * 13 + z * 7 + y * 3) % 6;
					Reel.set(level, o.offset(x, y, z), (k == 0 ? Blocks.MOSSY_STONE_BRICKS : k == 1 ? Blocks.CRACKED_STONE_BRICKS
							: Blocks.STONE_BRICKS).defaultBlockState());
				}
			}
		}
		Reel.set(level, o.offset(0, 0, -5), air);
		Reel.set(level, o.offset(0, 1, -5), air);
		// Windows down both sides, what is left of their glass.
		for (int z = -3; z <= 5; z += 3) {
			for (int x : new int[]{-3, 3}) {
				for (int y = 1; y <= 3; y++) {
					Reel.set(level, o.offset(x, y, z), (x + y + z) % 2 == 0 ? Blocks.GLASS_PANE.defaultBlockState()
							.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true) : air);
				}
			}
		}
		// The roof: stairs up to a ridge, and the gable ends.
		for (int k = 0; k <= 4; k++) {
			for (int z = -6; z <= 8; z++) {
				Reel.set(level, o.offset(-4 + k, 5 + k, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				Reel.set(level, o.offset(4 - k, 5 + k, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
			}
		}
		for (int z = -6; z <= 8; z++) Reel.set(level, o.offset(0, 9, z), Blocks.STONE_BRICK_SLAB.defaultBlockState());
		for (int k = 0; k <= 3; k++) {
			for (int x = -3 + k + 1; x <= 3 - k - 1; x++) {
				Reel.set(level, o.offset(x, 5 + k, -5), Blocks.STONE_BRICKS.defaultBlockState());
				Reel.set(level, o.offset(x, 5 + k, 7), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		// Pews facing the altar, their backs to the door; one turned round.
		for (int z = -3; z <= 3; z += 2) {
			Direction back = z == 1 ? Direction.SOUTH : Direction.NORTH;
			for (int x : new int[]{-2, -1, 1, 2}) Reel.set(level, o.offset(x, 0, z), stairs(back));
		}
		// The rail, the altar, the candles burnt down.
		for (int x : new int[]{-2, -1, 1, 2}) {
			boolean west = x == 2 || x == -1, east = x == -2 || x == 1;
			Reel.set(level, o.offset(x, 0, 5), Blocks.SPRUCE_FENCE.defaultBlockState()
					.setValue(BlockStateProperties.WEST, west).setValue(BlockStateProperties.EAST, east));
		}
		Reel.set(level, o.offset(-1, 0, 6), Blocks.POLISHED_ANDESITE.defaultBlockState());
		Reel.set(level, o.offset(1, 0, 6), Blocks.POLISHED_ANDESITE.defaultBlockState());
		Reel.set(level, o.offset(0, 0, 6), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		Reel.set(level, o.offset(-1, 1, 6), Blocks.CANDLE.defaultBlockState());
		Reel.set(level, o.offset(1, 1, 6), Blocks.CANDLE.defaultBlockState());
		for (int[] c : new int[][]{{-2, 3, -4}, {2, 4, 6}, {-2, 4, 5}}) Reel.set(level, o.offset(c[0], c[1], c[2]), Blocks.COBWEB.defaultBlockState());
		Reel.set(level, o.offset(2, 0, -4), Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 11));
	}

	// ------------------------------------------------------------------ underground

	/**
	 * Their mine, a long straight tunnel lit every few blocks. Somewhere far off, a pick on stone.
	 * On along it: where a torch was there is none, and across from it, a tunnel that was not
	 * there, and someone still digging at the end of it. The digging stops. Down the main tunnel,
	 * past the last light, something stands facing them. Back the way they came, running.
	 */
	private static void mine(ClientGameTestContext context, TestSingleplayerContext game, BlockPos[] woods) {
		TestServerContext server = game.getServer();
		BlockPos column = woods[1].offset(280, 0, -160);
		int top = server.computeOnServer(s -> Cinematic.surface(s.overworld(), column.getX(), column.getZ()));
		BlockPos o = new BlockPos(column.getX(), Math.max(-30, top - 34), column.getZ());
		server.runOnServer(s -> tunnels(s.overworld(), o));

		Vec3 start = Reel.local(o, 0.5, EYE, -4);
		Reel.Fear f = new Reel.Fear(start).wide();
		f.follow(Reel.Way.local(o, new double[][]{{0.5, -4}, {0.5, 56}}));
		f.face(Reel.local(o, 0.5, 1.4, 10));
		// Walking in; a pick on stone, far off. Stopping to listen.
		f.walk(60, 0.075, t -> f.onWay(6.0, 0.3 * Math.sin(t * 0.05), -0.25));
		f.markAt(35, "mine");
		f.hold(55, t -> f.onWay(8.0, -0.3, 0.1), 0, 0.25);
		f.markAt(f.size() - 30, "mine");
		f.follow(Reel.Way.local(o, new double[][]{{0.5, f.z - o.getZ() - 0.5}, {0.5, 21}}));
		f.walkOn(0.075, t -> f.onWay(6.0, 0.4 * Math.sin(t * 0.04), -0.3));
		// The dark stretch: the torch that was there is not.
		f.hold(20, t -> Reel.local(o, 0.5, 1.4, 30), 0, 0.3);
		f.hold(38, t -> Reel.local(o, -0.6, 1.1, 26), 0, 0.35);
		f.follow(Reel.Way.local(o, new double[][]{{0.5, 21}, {0.6, 26.0}}));
		f.walkOn(0.04, t -> Reel.local(o, 1.2, 1.2, 27));
		// On the right, a tunnel that was not there; digging, at the end of it.
		int dig = f.size();
		for (int k = 0; k < 7; k++) f.markAt(dig + 4 + k * 11 + (k % 3) * 2, "dig");
		f.hold(25, t -> Reel.local(o, 6, 1.0, 26), 0, 0.4);
		f.hold(62, t -> Reel.local(o, 9, 0.9, 26), 0.15, 0.5);
		// The digging stops. Down the tunnel, past the last light.
		int placed = dig;
		Vec3 itFeet = Reel.local(o, 0.5, 0, 48);
		Vec3 itFace = Reel.local(o, 0.5, 2.5, 48);
		f.hold(15, t -> Reel.local(o, 9, 0.9, 26), 0.15, 0.5);
		f.hold(18, t -> itFace, 0, 0.6);
		int seen = f.size() - 4;
		f.markAt(seen, "seen");
		f.flinchAt(seen, itFace, 1.3);
		f.hold(45, t -> itFace, 0, 1.4);
		f.backAway(12, itFace, 0.05);
		f.follow(Reel.Way.local(o, new double[][]{{0.5, f.z - o.getZ() - 0.5}, {0.5, -32}}));
		f.swing(8, 180.0, 8.0, 0.2);
		f.run(42, 0.22, 0);
		f.mark("end");
		f.watching(t -> t >= seen - 4 ? itFace : null);

		double[][] poses = f.flat(o.getY());
		Reel.prepare(context, game, start, Reel.local(o, 0.5, 1.4, 10), 6000);
		Reel.record(context, server, "mine", poses, Integer.MAX_VALUE, f, t -> {
			if (t == placed) Cinematic.place(server, BlockPos.containing(itFeet), eye(poses, t), Reel.STANDING);
			if (t >= placed) Reel.look(server, itFeet, eye(poses, t), 0);
		});
	}

	/** Solid rock round the mine, its ores, the tunnel they dug and the one they did not, the torches. */
	private static void tunnels(ServerLevel level, BlockPos o) {
		RandomSource r = RandomSource.create(113L);
		for (int x = -7; x <= 8; x++) {
			for (int z = -36; z <= 62; z++) {
				for (int y = -3; y <= 7; y++) {
					int k = r.nextInt(1000);
					Block b = k < 40 ? Blocks.COAL_ORE : k < 55 ? Blocks.IRON_ORE : k < 63 ? Blocks.COPPER_ORE
							: k < 110 ? Blocks.ANDESITE : k < 140 ? Blocks.GRANITE : k < 160 ? Blocks.DIORITE
							: k < 175 ? Blocks.GRAVEL : Blocks.STONE;
					Reel.set(level, o.offset(x, y, z), b.defaultBlockState());
				}
			}
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = -32; z <= 56; z++) {
			for (int x = 0; x <= 1; x++) {
				for (int y = 0; y <= 2; y++) Reel.set(level, o.offset(x, y, z), air);
				// A floor to walk on: never gravel underfoot.
				Reel.set(level, o.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			}
		}
		for (int x = 2; x <= 14; x++) {
			for (int y = 0; y <= 1; y++) Reel.set(level, o.offset(x, y, 26), air);
		}
		// A torch every eight blocks on the left, as far as they had got; the one at 26 gone.
		BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
		for (int z = -30; z <= 34; z += 8) {
			if (z != 26) Reel.set(level, o.offset(0, 1, z), torch);
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Flat ground somewhere near {@code near}, as the photographs find it; its top block. */
	private static BlockPos site(TestSingleplayerContext game, BlockPos near) {
		BlockPos at = game.getServer().computeOnServer(s -> ForestGallery.clearing(s.overworld(), near));
		if (at == null) Occupant.LOGGER.warn("[client-gametest] tape: no ground near {}", near);
		return at;
	}

	/** Where the camera is at tick {@code t}. */
	private static Vec3 eye(double[][] poses, int t) {
		double[] p = poses[Math.min(t, poses.length - 1)];
		return new Vec3(p[0], p[1], p[2]);
	}

	private static void door(ServerLevel level, BlockPos lower, Direction facing, boolean open) {
		BlockState state = Blocks.SPRUCE_DOOR.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT)
				.setValue(BlockStateProperties.OPEN, open);
		Reel.set(level, lower, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		Reel.set(level, lower.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** A pane of glass in a wall running along z ({@code alongZ}) or along x, joined to the wall either side. */
	private static void pane(ServerLevel level, BlockPos at, boolean alongZ) {
		Reel.set(level, at, Blocks.GLASS_PANE.defaultBlockState()
				.setValue(BlockStateProperties.NORTH, alongZ).setValue(BlockStateProperties.SOUTH, alongZ)
				.setValue(BlockStateProperties.EAST, !alongZ).setValue(BlockStateProperties.WEST, !alongZ));
	}

	private static BlockState stairs(Direction facing) {
		return Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/** A standing sign, turned {@code rotation} sixteenths (8: facing north), with {@code lines} on it. */
	private static void sign(ServerLevel level, BlockPos at, Block kind, int rotation, String[] lines) {
		BlockState state = kind.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rotation);
		level.setBlock(at, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
			Compat.writeSign(sign, lines);
			sign.setWaxed(true);
			sign.setChanged();
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}

	@SuppressWarnings("unused")
	private static IntConsumer none() {
		return t -> {
		};
	}

	@SuppressWarnings("unused")
	private static IntFunction<Vec3> still(Vec3 at) {
		return t -> at;
	}
}
