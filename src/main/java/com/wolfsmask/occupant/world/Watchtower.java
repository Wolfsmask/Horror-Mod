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

import java.util.ArrayList;
import java.util.List;

/**
 * An old fire watchtower: four log legs, a ladder, and a little cabin at the top where somebody
 * sat and watched the treeline. From up there you can see right to the edge of the fog.
 */
final class Watchtower extends Build {
	private static final int TOP = 10;

	Watchtower(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void build() {
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			foundation(c[0], 1, c[1], Blocks.COBBLESTONE.defaultBlockState());
			for (int y = 1; y <= TOP + 4; y++) put(c[0], y, c[1], log);
		}
		// Cross braces partway up.
		List<int[]> brace = new ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			brace.add(new int[]{x, -2});
			brace.add(new int[]{x, 2});
		}
		connected(brace, 5, Blocks.SPRUCE_FENCE);
		// The ladder, up the south side of the south-west leg, through a hole in the floor.
		for (int y = 1; y <= TOP; y++) {
			put(-2, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		fill(-3, TOP, -3, 3, TOP, 3, planks);
		put(-2, TOP, 3, Blocks.AIR.defaultBlockState());
		put(-2, TOP, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		// A railing, half rotted away.
		List<int[]> rail = new ArrayList<>();
		for (int[] c : ring(-3, -3, 3, 3, new int[]{-2, 3})) if (chance(0.8f)) rail.add(c);
		connected(rail, TOP + 1, Blocks.SPRUCE_FENCE);
		// The roof.
		fill(-3, TOP + 5, -3, 3, TOP + 5, 3, Blocks.SPRUCE_SLAB.defaultBlockState());
		// What the watcher left.
		container(1, TOP + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.CAMP);
		put(0, TOP + 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		if (chance(0.7f)) put(-1, TOP + 1, -1, Blocks.COBWEB.defaultBlockState());
		put(1, TOP + 1, -1, Blocks.SKELETON_SKULL.defaultBlockState());
	}
}
