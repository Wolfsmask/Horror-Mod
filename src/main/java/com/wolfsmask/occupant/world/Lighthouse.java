package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lighthouse by the water, its lamp long dead. The keeper's things are at the top, and the
 * last thing they wrote down.
 */
final class Lighthouse extends Build {
	private static final int HEIGHT = 16;

	Lighthouse(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState band(int y) {
		return (y / 3) % 2 == 0 ? old(Blocks.CALCITE.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 0.15f)
				: Blocks.BRICKS.defaultBlockState();
	}

	@Override
	void build() {
		fill(-3, 1, -3, 3, HEIGHT + 5, 3, Blocks.AIR.defaultBlockState());
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
		}
		for (int y = 1; y <= HEIGHT; y++) {
			for (int[] c : ring(-2, -2, 2, 2)) {
				if (Math.abs(c[0]) == 2 && Math.abs(c[1]) == 2) continue;          // rounded corners
				put(c[0], y, c[1], band(y));
			}
			put(-1, y, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		fill(0, 1, -2, 0, 2, -2, Blocks.AIR.defaultBlockState());
		// The lamp room: glass all round, the lamp gone dark.
		fill(-2, HEIGHT + 1, -2, 2, HEIGHT + 1, 2, Blocks.STONE_BRICKS.defaultBlockState());
		put(-1, HEIGHT + 1, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		for (int y = HEIGHT + 2; y <= HEIGHT + 4; y++) {
			for (int[] c : ring(-2, -2, 2, 2)) put(c[0], y, c[1], Blocks.GLASS.defaultBlockState());
		}
		fill(-2, HEIGHT + 5, -2, 2, HEIGHT + 5, 2, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		put(0, HEIGHT + 2, 0, Blocks.REDSTONE_LAMP.defaultBlockState());
		container(1, HEIGHT + 2, -1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.RUIN);
		if (chance(0.7f)) put(-1, HEIGHT + 4, -1, Blocks.COBWEB.defaultBlockState());
		steps(0, -2, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
