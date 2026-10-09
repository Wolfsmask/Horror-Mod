package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * An old fire lookout, high over the trees: four tall legs set wide in stone, braced on three
 * sides at three heights, a ladder up the open side to a hatch, and on top a cabin with a waist-high
 * wall and the window gaps above it, under a stepped roof with a spike on it. Whoever kept watch
 * up here left in a hurry, or did not leave. In some the roof has come down.
 * <p>
 * Built to be climbed: the ladder runs on the inside face of a leg from the ground to the hatch,
 * clear of every beam, so whoever climbs it steps straight off onto the boards.
 */
final class Watchtower extends Build {
	private static final String[][] NOTES = {{"SAW IT AT", "THE TREELINE", "AGAIN", ""}, {"DAY 9", "IT DOESNT", "MOVE WHEN", "I LOOK"},
			{"", "IT STANDS", "WHERE THE FOG", "STARTS"}, {"", "DONT COME", "UP HERE", ""}};

	Watchtower(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void make() {
		int top = 11 + random.nextInt(4);                      // how high the floor is: no two the same
		boolean roofless = chance(0.25f);                        // the roof came down in a storm, long ago
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState across = log.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState along = log.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState stripped = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();

		fill(-6, 1, -6, 6, top + 10, 6, Blocks.AIR.defaultBlockState());

		// Four legs, set in stone, up to the floor; a stone footing round each.
		for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			foundation(c[0], 1, c[1], Blocks.COBBLESTONE.defaultBlockState());
			put(c[0], 0, c[1], Blocks.COBBLESTONE.defaultBlockState());
			for (int y = 1; y < top; y++) put(c[0], y, c[1], log);
		}
		// Braces between the legs on three sides, at three heights; the south side is left open
		// for the ladder. Here and there one has rotted through and gone.
		for (int y : new int[]{3, top / 2 + 1, top - 3}) {
			for (int i = -2; i <= 2; i++) {
				if (chance(0.88f)) put(i, y, -3, across);
				if (chance(0.88f)) put(-3, y, i, along);
				if (chance(0.88f)) put(3, y, i, along);
			}
		}
		// The ladder: on the inside face of the south-west leg, from the ground to the hatch.
		for (int y = 1; y <= top; y++) {
			put(-2, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		}

		// The floor, overhanging the legs, with the hatch the ladder comes up through, and the
		// joists under its edge so it does not float.
		fill(-4, top, -4, 4, top, 4, planks);
		put(-2, top, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		for (int i = -4; i <= 4; i++) {
			put(i, top - 1, -4, stripped.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			put(i, top - 1, 4, stripped.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			put(-4, top - 1, i, stripped.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
			put(4, top - 1, i, stripped.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
		}

		// The cabin: corner posts, a waist-high wall of boards, and above it the window gaps,
		// with a post between each pair. Boards have fallen out of the wall here and there.
		for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			int h = roofless ? 1 + random.nextInt(3) : 3;
			for (int y = top + 1; y <= top + h; y++) put(c[0], y, c[1], log);
		}
		List<int[]> sill = new ArrayList<>();
		for (int[] c : ring(-4, -4, 4, 4)) {
			if (Math.abs(c[0]) == 4 && Math.abs(c[1]) == 4) continue;
			if (chance(0.88f)) put(c[0], top + 1, c[1], planks);
			boolean mid = c[0] == 0 || c[1] == 0;
			if (!roofless && mid) {
				put(c[0], top + 2, c[1], log);
				put(c[0], top + 3, c[1], log);
			} else if (chance(0.3f)) {
				sill.add(c);
			}
		}
		connected(sill, top + 2, Blocks.SPRUCE_FENCE);

		if (roofless) {
			// What is left of the roof, in pieces on the boards.
			for (int i = 0; i < 6; i++) {
				int x = -3 + random.nextInt(7);
				int z = -3 + random.nextInt(7);
				if (x == -2 && z == 3) continue;                       // never over the hatch
				put(x, top + 1, z, chance(0.5f) ? Blocks.SPRUCE_SLAB.defaultBlockState()
						: stairs(Blocks.SPRUCE_STAIRS, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
			}
		} else {
			roof(top + 4);
		}
		furnish(top);
	}

	/** A stepped, hipped roof from {@code y}: an overhang, three tiers, a cap and a spike. */
	private void roof(int y) {
		fill(-4, y, -4, 4, y, 4, Blocks.SPRUCE_PLANKS.defaultBlockState());
		for (int t = 0; t <= 2; t++) {
			int r = 5 - t;
			int ry = y + t;
			for (int i = -r; i <= r; i++) {
				put(i, ry, -r, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
				put(i, ry, r, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			}
			for (int i = -r + 1; i <= r - 1; i++) {
				put(-r, ry, i, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
				put(r, ry, i, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
			}
			if (t > 0) fill(-r + 1, ry, -r + 1, r - 1, ry, r - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		fill(-2, y + 3, -2, 2, y + 3, 2, Blocks.SPRUCE_SLAB.defaultBlockState());
		fill(-1, y + 3, -1, 1, y + 3, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
		put(0, y + 4, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(0, y + 5, 0, Blocks.IRON_BARS.defaultBlockState());
	}

	/** What the watcher left on the boards at {@code top}: their things, a note, and them. */
	private void furnish(int top) {
		int f = top + 1;
		container(3, f, -3, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.CAMP);
		put(2, f, -3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));             // the stool, facing out
		put(-3, f, -3, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		put(-3, f, -2, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + random.nextInt(3)));
		if (chance(0.6f)) put(-3, f + 2, 3, Blocks.COBWEB.defaultBlockState());
		if (chance(0.6f)) put(3, f + 2, 3, Blocks.COBWEB.defaultBlockState());
		if (chance(0.55f)) {
			put(2, f, 0, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		}
		put(0, f, -3, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
		Places.sign(level, at(0, f, -3), NOTES[random.nextInt(NOTES.length)]);
		unsettle(-3, -2, 3, 2, f, 2);
	}
}
