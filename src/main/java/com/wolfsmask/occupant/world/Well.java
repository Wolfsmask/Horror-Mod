package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** The village well: a ring of stone, dark water a few blocks down, posts and a little roof. */
final class Well extends Build {
	Well(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void make() {
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		fill(-2, 1, -2, 2, 5, 2, Blocks.AIR.defaultBlockState());
		for (int y = -4; y <= 1; y++) {
			for (int[] c : ring(-2, -2, 2, 2)) put(c[0], y, c[1], old(cobble, mossy, 0.4f));
		}
		fill(-1, -4, -1, 1, -4, 1, cobble);
		fill(-1, -3, -1, 1, -1, 1, Blocks.WATER.defaultBlockState());
		fill(-1, 0, -1, 1, 1, 1, Blocks.AIR.defaultBlockState());
		for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			put(c[0], 2, c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
			put(c[0], 3, c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		fill(-2, 4, -2, 2, 4, 2, Blocks.SPRUCE_SLAB.defaultBlockState());
		if (chance(0.5f)) put(0, 4, 0, Blocks.AIR.defaultBlockState());
	}
}
