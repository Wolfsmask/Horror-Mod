package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * A lighthouse by the water, its lamp long dead: a banded tower on a stone plinth, a door, a
 * ladder up the inside to the lamp room, and a gallery round the outside of that, with a railing,
 * that you can step out onto and look down at the water from. The keeper's things are up there,
 * and the last thing they wrote down.
 */
final class Lighthouse extends Build {
	Lighthouse(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState band(int y) {
		return (y / 3) % 2 == 0 ? old(Blocks.CALCITE.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 0.12f)
				: Blocks.BRICKS.defaultBlockState();
	}

	private static boolean corner(int x, int z, int r) {
		return Math.abs(x) == r && Math.abs(z) == r;
	}

	@Override
	void build() {
		int height = 13 + random.nextInt(5);
		fill(-5, 1, -5, 5, height + 8, 5, Blocks.AIR.defaultBlockState());
		// The plinth: a stone skirt round the foot of the tower.
		for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
			if (corner(x, z, 3)) continue;
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
		}
		for (int[] c : ring(-3, -3, 3, 3)) {
			if (corner(c[0], c[1], 3)) continue;
			put(c[0], 1, c[1], Blocks.STONE_BRICK_SLAB.defaultBlockState());
		}
		// The tower: rounded corners, banded red and white.
		for (int y = 1; y <= height; y++) {
			for (int[] c : ring(-2, -2, 2, 2)) {
				if (corner(c[0], c[1], 2)) continue;
				put(c[0], y, c[1], y <= 2 ? Blocks.STONE_BRICKS.defaultBlockState() : band(y));
			}
			put(-1, y, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		// The door, on the side away from the ladder.
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		put(0, 1, -2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(0, 2, -2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		put(0, 1, -3, Blocks.AIR.defaultBlockState());          // the plinth is open in front of the door

		// The gallery floor, wider than the tower, with the ladder's hatch; and its railing.
		int gallery = height + 1;
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
			if (corner(x, z, 4)) continue;
			boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
			put(x, gallery, z, edge ? Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)
					: Blocks.STONE_BRICKS.defaultBlockState());
		}
		put(-1, gallery, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		List<int[]> rail = new ArrayList<>();
		for (int[] c : ring(-4, -4, 4, 4)) if (!corner(c[0], c[1], 4) && chance(0.85f)) rail.add(c);
		connected(rail, gallery + 1, Blocks.SPRUCE_FENCE);

		// The lamp room: glass all round, a way out onto the gallery, the lamp gone dark.
		for (int y = gallery + 1; y <= gallery + 3; y++) {
			for (int[] c : ring(-2, -2, 2, 2)) {
				if (corner(c[0], c[1], 2)) {
					put(c[0], y, c[1], Blocks.STONE_BRICKS.defaultBlockState());
				} else {
					put(c[0], y, c[1], chance(0.12f) ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
				}
			}
		}
		put(0, gallery + 1, -2, Blocks.AIR.defaultBlockState());
		put(0, gallery + 2, -2, Blocks.AIR.defaultBlockState());
		put(0, gallery + 1, 0, Blocks.REDSTONE_LAMP.defaultBlockState());
		container(1, gallery + 1, -1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.RUIN);
		if (chance(0.7f)) put(-1, gallery + 3, -1, Blocks.COBWEB.defaultBlockState());

		// The cap: a stepped dome, and an iron spike where the vane was.
		int cap = gallery + 4;
		for (int[] c : ring(-2, -2, 2, 2)) {
			if (corner(c[0], c[1], 2)) {
				put(c[0], cap, c[1], Blocks.STONE_BRICK_SLAB.defaultBlockState());
				continue;
			}
			Direction in = Math.abs(c[0]) == 2 ? (c[0] > 0 ? Direction.WEST : Direction.EAST) : (c[1] > 0 ? Direction.NORTH : Direction.SOUTH);
			put(c[0], cap, c[1], stairs(Blocks.STONE_BRICK_STAIRS, in));
		}
		fill(-1, cap, -1, 1, cap, 1, Blocks.STONE_BRICKS.defaultBlockState());
		put(0, cap + 1, 0, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		put(0, cap + 2, 0, Blocks.IRON_BARS.defaultBlockState());
		steps(0, -3, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
