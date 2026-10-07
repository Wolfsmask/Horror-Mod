package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A small fenced graveyard: two rows of mounds with stones at their heads, candles long since
 * burnt down, and one grave that has been dug open again. Everything follows the ground.
 */
final class Graves extends Build {
	private static final String[][] SIGNS = {
			{"HERE LIE", "THE LAST OF US", "", ""},
			{"IT TOOK", "THE REST", "", ""},
			{"REST", "", "IF IT LETS YOU", ""}};

	Graves(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private int floor(int x, int z) {
		return ground(x, z) + 1;
	}

	@Override
	void build() {
		// How many graves, and in how many rows: no two graveyards the same.
		int rows = 2 + random.nextInt(2);
		int cols = 2 + random.nextInt(3);
		int[] xs = new int[cols];
		for (int i = 0; i < cols; i++) xs[i] = -3 * (cols - 1) / 2 + 3 * i;
		int[] zs = new int[rows];
		for (int i = 0; i < rows; i++) zs[i] = (rows == 2 ? -2 : -3) + 3 * i;
		int x0 = xs[0] - 2, x1 = xs[cols - 1] + 2;
		int z0 = zs[0] - 2, z1 = zs[rows - 1] + 3;

		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				int g = floor(x, z);
				fill(x, g, z, x, g + 3, z, Blocks.AIR.defaultBlockState());
			}
		}
		// The fence, following the ground, with a way in at the front and gaps where it has rotted.
		java.util.Set<Long> cells = new java.util.HashSet<>();
		java.util.List<int[]> ring = new java.util.ArrayList<>();
		for (int[] c : ring(x0, z0, x1, z1, new int[]{0, z0})) {
			if (random.nextFloat() < 0.12f) continue;
			ring.add(c);
			cells.add(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL));
		}
		for (int[] c : ring) {
			BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState()
					.setValue(BlockStateProperties.NORTH, cells.contains(((long) c[0] << 32) ^ ((c[1] - 1) & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.SOUTH, cells.contains(((long) c[0] << 32) ^ ((c[1] + 1) & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.WEST, cells.contains(((long) (c[0] - 1) << 32) ^ (c[1] & 0xFFFFFFFFL)))
					.setValue(BlockStateProperties.EAST, cells.contains(((long) (c[0] + 1) << 32) ^ (c[1] & 0xFFFFFFFFL)));
			put(c[0], floor(c[0], c[1]), c[1], fence);
		}

		// Rows of graves: a mound, and a stone at its head; one or two missing; one opened.
		int count = rows * cols;
		int open = random.nextInt(count);
		int n = 0;
		for (int z : zs) {
			for (int x : xs) {
				int index = n++;
				if (index != open && random.nextFloat() < 0.12f) continue;      // nothing left of this one
				int g = floor(x, z) - 1;
				if (index == open) {
					fill(x, g - 1, z, x, g, z + 1, Blocks.AIR.defaultBlockState());
					container(x, g - 1, z, facing(Blocks.CHEST.defaultBlockState(), Direction.SOUTH), Loot.Kind.GRAVE);
					put(x, g - 2, z + 1, Blocks.DIRT.defaultBlockState());
				} else {
					put(x, g, z, Blocks.COARSE_DIRT.defaultBlockState());
					put(x, g, z + 1, Blocks.COARSE_DIRT.defaultBlockState());
				}
				BlockState stone = random.nextFloat() < 0.5f ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
						: random.nextFloat() < 0.5f ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState();
				int sg = floor(x, z - 1);
				put(x, sg, z - 1, stone);
				if (random.nextFloat() < 0.4f) put(x, sg + 1, z - 1, Blocks.CANDLE.defaultBlockState());
			}
		}

		unsettle(x0 + 1, z0 + 1, x1 - 1, z1 - 1, Integer.MIN_VALUE, 2);
		// A sign at the gate, facing whoever comes.
		int sy = floor(0, z0 - 1);
		put(0, sy, z0 - 1, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
		Places.sign(level, at(0, sy, z0 - 1), SIGNS[random.nextInt(SIGNS.length)]);
	}
}
