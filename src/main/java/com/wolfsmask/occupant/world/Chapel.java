package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A small stone chapel, long given up: pews still in rows, candles burnt down on the altar, the
 * windows broken. Every pew faces the altar except one, which has been turned round to face the
 * door.
 */
final class Chapel extends Build {
	Chapel(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState wall() {
		return old(Blocks.STONE_BRICKS.defaultBlockState(), random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
				: Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 0.35f);
	}

	@Override
	void build() {
		fill(-4, 1, -6, 4, 9, 7, Blocks.AIR.defaultBlockState());
		for (int x = -3; x <= 3; x++) for (int z = -5; z <= 6; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, old(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.3f));
		}
		for (int[] c : ring(-3, -5, 3, 6)) {
			for (int y = 1; y <= 5; y++) {
				if (chance(0.06f) && y > 2) continue;                     // fallen out
				put(c[0], y, c[1], wall());
			}
		}
		// The door, and windows with what is left of their panes.
		fill(0, 1, -5, 0, 2, -5, Blocks.AIR.defaultBlockState());
		for (int z : new int[]{-2, 2}) {
			for (int x : new int[]{-3, 3}) {
				put(x, 3, z, chance(0.5f) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		// The roof: stairs up to a ridge.
		for (int k = 0; k <= 4; k++) {
			for (int z = -6; z <= 7; z++) {
				put(-4 + k, 6 + k, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
				put(4 - k, 6 + k, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
			}
		}
		for (int z = -6; z <= 7; z++) put(0, 10, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		for (int k = 0; k <= 3; k++) {
			for (int x = -3 + k + 1; x <= 3 - k - 1; x++) {
				put(x, 6 + k, -5, wall());
				put(x, 6 + k, 6, wall());
			}
		}
		// Pews, facing the altar; one turned round, to face the door.
		int turned = random.nextInt(4);
		int n = 0;
		for (int z = -3; z <= 3; z += 2) {
			Direction faces = n++ == turned ? Direction.SOUTH : Direction.NORTH;
			for (int x : new int[]{-2, -1, 1, 2}) put(x, 1, z, stairs(Blocks.SPRUCE_STAIRS, faces.getOpposite()));
		}
		// The altar, the candles burnt down, and under the cloth, what was hidden there.
		List<int[]> rail = new ArrayList<>();
		for (int x = -2; x <= 2; x++) if (x != 0) rail.add(new int[]{x, 4});
		connected(rail, 1, Blocks.SPRUCE_FENCE);
		put(-1, 1, 5, Blocks.POLISHED_ANDESITE.defaultBlockState());
		put(1, 1, 5, Blocks.POLISHED_ANDESITE.defaultBlockState());
		container(0, 1, 5, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.GRAVE);
		put(-1, 2, 5, Blocks.CANDLE.defaultBlockState());
		put(1, 2, 5, Blocks.CANDLE.defaultBlockState());
		put(0, 5, 6, Blocks.BELL.defaultBlockState());
		for (int[] c : new int[][]{{-2, 4, -4}, {2, 5, 5}, {-2, 5, 4}}) if (chance(0.6f)) put(c[0], c[1], c[2], Blocks.COBWEB.defaultBlockState());
		steps(0, -5, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
