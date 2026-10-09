package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A small chapel, long given up: pews still in rows, candles burnt down on the altar, the
 * windows broken. Every pew faces the altar except one, which has been turned round to face the
 * door.
 * <p>
 * No two the same: stone, or timber on a stone footing; longer or shorter; with a bell tower
 * over the door or without; and in some the roof has come in over the pews.
 */
final class Chapel extends Build {
	private static final int FRONT = -5;

	private boolean wooden;
	/** Where the back wall is: how long the nave is. */
	private int back;

	Chapel(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState wall(int x, int y, int z) {
		if (!wooden) {
			return old(Blocks.STONE_BRICKS.defaultBlockState(), random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
					: Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 0.35f);
		}
		if (y <= 1) return Blocks.COBBLESTONE.defaultBlockState();
		// Timber: posts at the corners and every few blocks along, boards between.
		boolean post = Math.abs(x) == 3 && ((z - FRONT) % 3 == 0 || z == back);
		return post ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	private Block roofStairs() {
		return wooden ? Blocks.SPRUCE_STAIRS : Blocks.STONE_BRICK_STAIRS;
	}

	private BlockState roofSlab() {
		return (wooden ? Blocks.SPRUCE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState();
	}

	@Override
	void make() {
		wooden = chance(0.35f);
		back = 5 + random.nextInt(3);
		boolean tower = chance(0.55f);
		// Where the roof has come in, if it has: two rows, on one side.
		boolean caved = chance(0.4f);
		int caveAt = FRONT + 2 + random.nextInt(back - FRONT - 4);
		int caveSide = random.nextBoolean() ? 1 : -1;

		fill(-4, 1, FRONT - 4, 4, 10, back + 1, Blocks.AIR.defaultBlockState());
		for (int x = -3; x <= 3; x++) for (int z = FRONT; z <= back; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, wooden ? Blocks.SPRUCE_PLANKS.defaultBlockState()
					: old(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.3f));
		}
		for (int[] c : ring(-3, FRONT, 3, back)) {
			for (int y = 1; y <= 5; y++) {
				if (chance(0.06f) && y > 2) continue;                     // fallen out
				put(c[0], y, c[1], wall(c[0], y, c[1]));
			}
		}
		// The door, and windows down both sides with what is left of their panes.
		fill(0, 1, FRONT, 0, 2, FRONT, Blocks.AIR.defaultBlockState());
		for (int z = FRONT + 2; z <= back - 2; z += 3) {
			for (int x : new int[]{-3, 3}) {
				for (int y = 2; y <= (wooden ? 3 : 4); y++) {
					put(x, y, z, chance(0.5f) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}

		// The roof: stairs up to a ridge, and the gable ends.
		Block stair = roofStairs();
		for (int k = 0; k <= 4; k++) {
			for (int z = FRONT - 1; z <= back + 1; z++) {
				boolean fallen = caved && z >= caveAt && z <= caveAt + 1 && k >= 1 && k <= 3;
				if (!(fallen && caveSide < 0)) put(-4 + k, 6 + k, z, stairs(stair, Direction.EAST));
				if (!(fallen && caveSide > 0)) put(4 - k, 6 + k, z, stairs(stair, Direction.WEST));
			}
		}
		for (int z = FRONT - 1; z <= back + 1; z++) put(0, 10, z, roofSlab());
		for (int k = 0; k <= 3; k++) {
			for (int x = -3 + k + 1; x <= 3 - k - 1; x++) {
				put(x, 6 + k, FRONT, wall(x, 6 + k, FRONT));
				put(x, 6 + k, back, wall(x, 6 + k, back));
			}
		}
		// What came down with it, on the pews.
		if (caved) {
			for (int z = caveAt - 1; z <= caveAt + 2; z++) {
				for (int x = caveSide; Math.abs(x) <= 2; x += caveSide) {
					if (!chance(0.35f)) continue;
					put(x, 1, z, chance(0.5f) ? stairs(stair, Direction.Plane.HORIZONTAL.getRandomDirection(random))
							: wooden ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}

		// Pews, facing the altar; one turned round, to face the door.
		int rows = 0;
		for (int z = FRONT + 2; z <= back - 3; z += 2) rows++;
		int turned = random.nextInt(Math.max(1, rows));
		int n = 0;
		for (int z = FRONT + 2; z <= back - 3; z += 2) {
			Direction faces = n++ == turned ? Direction.SOUTH : Direction.NORTH;
			for (int x : new int[]{-2, -1, 1, 2}) {
				if (caved && z >= caveAt - 1 && z <= caveAt + 2 && x * caveSide > 0) continue;   // under the rubble
				put(x, 1, z, stairs(Blocks.SPRUCE_STAIRS, faces.getOpposite()));
			}
		}
		// The altar, the candles burnt down, and under the cloth, what was hidden there.
		List<int[]> rail = new ArrayList<>();
		for (int x = -2; x <= 2; x++) if (x != 0) rail.add(new int[]{x, back - 2});
		connected(rail, 1, Blocks.SPRUCE_FENCE);
		put(-1, 1, back - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
		put(1, 1, back - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
		container(0, 1, back - 1, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.GRAVE);
		put(-1, 2, back - 1, Blocks.CANDLE.defaultBlockState());
		put(1, 2, back - 1, Blocks.CANDLE.defaultBlockState());
		for (int[] c : new int[][]{{-2, 4, FRONT + 1}, {2, 5, back - 1}, {-2, 5, back - 2}}) {
			if (chance(0.6f)) put(c[0], c[1], c[2], Blocks.COBWEB.defaultBlockState());
		}

		unsettle(-2, FRONT + 1, 2, back - 3, 1, 3);
		if (tower) {
			belfry();
			steps(0, FRONT - 2, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
		} else {
			steps(0, FRONT, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
		}
	}

	/** A square tower over the door, that you walk in through: open at the top, the bell still in it. */
	private void belfry() {
		int top = 12 + random.nextInt(2);
		int z0 = FRONT - 2;
		int z1 = FRONT;
		// Room for it: in front of the nave all the way up, and over the nave's roof above the ridge.
		fill(-2, 1, z0 - 1, 2, top + 3, z0, Blocks.AIR.defaultBlockState());
		fill(-2, 11, z0 + 1, 2, top + 3, z1 + 1, Blocks.AIR.defaultBlockState());
		for (int x = -1; x <= 1; x++) for (int z = z0; z < z1; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, wooden ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
		}
		for (int[] c : ring(-1, z0, 1, z1)) {
			boolean corner = Math.abs(c[0]) == 1 && (c[1] == z0 || c[1] == z1);
			for (int y = 1; y < top; y++) {
				BlockState s = wooden && !corner ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: wooden ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
						: old(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 0.3f);
				put(c[0], y, c[1], s);
			}
		}
		// Inside it, nothing but the way through and the drop from the bell.
		fill(0, 1, FRONT - 1, 0, top - 1, FRONT - 1, Blocks.AIR.defaultBlockState());
		fill(0, 1, z0, 0, 2, z0, Blocks.AIR.defaultBlockState());
		fill(0, 1, z1, 0, 2, z1, Blocks.AIR.defaultBlockState());
		// The belfry: open on every side.
		for (int y = top - 3; y <= top - 1; y++) {
			put(0, y, z0, Blocks.AIR.defaultBlockState());
			put(0, y, z1, Blocks.AIR.defaultBlockState());
			put(-1, y, FRONT - 1, Blocks.AIR.defaultBlockState());
			put(1, y, FRONT - 1, Blocks.AIR.defaultBlockState());
		}
		fill(-1, top, z0, 1, top, z1, wooden ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
		put(0, top - 1, FRONT - 1, Blocks.BELL.defaultBlockState()
				.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		// A low spire.
		Block stair = roofStairs();
		for (int[] c : ring(-2, z0 - 1, 2, z1 + 1)) {
			Direction in = c[1] == z0 - 1 ? Direction.SOUTH : c[1] == z1 + 1 ? Direction.NORTH : c[0] < 0 ? Direction.EAST : Direction.WEST;
			put(c[0], top, c[1], stairs(stair, in));
		}
		for (int[] c : ring(-1, z0, 1, z1)) {
			Direction in = c[1] == z0 ? Direction.SOUTH : c[1] == z1 ? Direction.NORTH : c[0] < 0 ? Direction.EAST : Direction.WEST;
			put(c[0], top + 1, c[1], stairs(stair, in));
		}
		put(0, top + 1, FRONT - 1, wooden ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
		put(0, top + 2, FRONT - 1, roofSlab());
		put(0, top + 3, FRONT - 1, Blocks.SPRUCE_FENCE.defaultBlockState());
	}
}
