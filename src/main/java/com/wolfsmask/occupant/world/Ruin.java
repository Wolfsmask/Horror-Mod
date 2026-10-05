package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A small ruined keep: a walled yard of old stone with a tower at each corner, the battlements
 * still standing in places and fallen in others, the gate's bars rusted half away. Somebody held
 * out in here, for a while. The fire in the yard went out a long time ago.
 */
final class Ruin extends Build {
	private static final int R = 6;

	Ruin(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState stone() {
		float r = random.nextFloat();
		if (r < 0.25f) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (r < 0.40f) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		if (r < 0.48f) return Blocks.COBBLESTONE.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	@Override
	void build() {
		fill(-R - 1, 1, -R - 1, R + 1, 11, R + 1, Blocks.AIR.defaultBlockState());

		// The yard: old paving, mostly given way to gravel and earth.
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				float r = random.nextFloat();
				BlockState floor = r < 0.35f ? Blocks.GRAVEL.defaultBlockState()
						: r < 0.55f ? Blocks.COARSE_DIRT.defaultBlockState()
						: r < 0.75f ? Blocks.COBBLESTONE.defaultBlockState() : stone();
				put(x, 0, z, floor);
				foundation(x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
			}
		}

		// The curtain wall. Each stretch has fallen to its own height, a run at a time, never to
		// single floating blocks.
		int height = 5;
		int drop = 0;
		for (int[] c : ring(-R, -R, R, R)) {
			if (random.nextFloat() < 0.18f) drop = random.nextFloat() < 0.5f ? 0 : 1 + random.nextInt(3);
			int top = height - drop;
			for (int y = 1; y <= top; y++) put(c[0], y, c[1], stone());
			// Battlements, every other block, where the wall still stands to its full height.
			if (drop == 0 && ((c[0] + c[1]) & 1) == 0) put(c[0], top + 1, c[1], stone());
		}

		// The gate, in the front wall, and what is left of its bars.
		fill(-1, 1, -R, 1, 3, -R, Blocks.AIR.defaultBlockState());
		List<int[]> bars = new ArrayList<>();
		for (int x = -1; x <= 1; x++) if (random.nextFloat() < 0.6f) bars.add(new int[]{x, -R});
		connected(bars, 3, Blocks.IRON_BARS);
		put(-2, 4, -R, stone());
		put(2, 4, -R, stone());

		// A tower at each corner; one has fallen to half its height.
		int fallen = random.nextInt(4);
		int n = 0;
		for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) {
			int top = n++ == fallen ? 4 + random.nextInt(2) : 8;
			for (int x = c[0] - 1; x <= c[0] + 1; x++) {
				for (int z = c[1] - 1; z <= c[1] + 1; z++) {
					for (int y = 1; y <= top; y++) put(x, y, z, stone());
					if (top == 8 && (x != c[0] || z != c[1]) && ((x + z) & 1) == 0) put(x, top + 1, z, stone());
				}
			}
		}

		// Fallen stone about the yard, before anything is set down on it.
		for (int i = 0; i < 6; i++) {
			int x = -R + 2 + random.nextInt(2 * R - 3);
			int z = -R + 2 + random.nextInt(2 * R - 3);
			if (Math.abs(x) > 1 || Math.abs(z) > 1) {
				put(x, 1, z, random.nextFloat() < 0.5f ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			}
		}

		// The yard: a long-dead fire, somebody's last supplies, cobwebs against the walls.
		put(0, 1, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		container(0, 1, R - 2, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.RUIN);
		container(-R + 2, 1, R - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.RUIN);
		put(R - 2, 1, R - 3, Blocks.SKELETON_SKULL.defaultBlockState());
		for (int[] c : new int[][]{{-R + 1, 2, 0}, {R - 1, 1, -2}, {R - 1, 3, 2}, {-R + 1, 3, -3}}) {
			if (random.nextFloat() < 0.6f) put(c[0], c[1], c[2], Blocks.COBWEB.defaultBlockState());
		}
		steps(0, -R, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
