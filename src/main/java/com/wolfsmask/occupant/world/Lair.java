package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Where it goes. On the surface: a ring of trampled earth, bones, and a hole with a ladder down.
 * Twenty blocks under it, a hollow scraped out of the stone, with its bones and its things, and
 * the long marks on the walls. Going in is its own moment.
 */
final class Lair extends Build {
	static final int DEPTH = 22;
	static final int RADIUS = 7;

	Lair(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	/** The middle of the hollow, in the world. */
	BlockPos hollow() {
		return at(0, -DEPTH, 0);
	}

	@Override
	void build() {
		// The mark on the surface.
		for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
			if (x * x + z * z > 10) continue;
			put(x, 0, z, chance(0.5f) ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
		}
		put(2, 1, -1, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		put(-2, 1, 2, Blocks.BONE_BLOCK.defaultBlockState());
		put(-1, 1, -2, Blocks.DEAD_BUSH.defaultBlockState());
		// The hole, and a ladder down its side.
		for (int y = 0; y >= -DEPTH + 2; y--) {
			put(0, y, 0, Blocks.AIR.defaultBlockState());
			put(0, y, 1, Blocks.AIR.defaultBlockState());
			put(0, y, -1, stone());
			put(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		// The hollow: rough, low, wider than it is tall.
		int cy = -DEPTH;
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				for (int y = -3; y <= 4; y++) {
					double d = (x * x + z * z) / (double) (RADIUS * RADIUS) + (y * y) / (y < 0 ? 9.0 : 20.0);
					if (d > 1.0 + random.nextDouble() * 0.15) continue;
					put(x, cy + y, z, y <= -2 ? floor() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		// The ladder comes down through the roof.
		for (int y = cy + 1; y <= cy + 4; y++) {
			put(0, y, -1, stone());
			put(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		// Its nest: a ring of old, stripped wood and bones; skulls; what it has kept.
		for (int[] c : ring(-2, 3, 2, 6)) if (chance(0.7f)) put(c[0], cy - 1, c[1], Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
		for (int i = 0; i < 9; i++) {
			int x = random.nextInt(2 * RADIUS - 3) - RADIUS + 2;
			int z = random.nextInt(2 * RADIUS - 3) - RADIUS + 2;
			if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
			BlockState thing = switch (random.nextInt(4)) {
				case 0 -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16));
				case 1 -> Blocks.BONE_BLOCK.defaultBlockState();
				case 2 -> Blocks.COBWEB.defaultBlockState();
				default -> Blocks.CANDLE.defaultBlockState();
			};
			put(x, cy - 1, z, thing);
		}
		container(0, cy - 1, 5, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.GRAVE);
	}

	private BlockState stone() {
		return old(Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0.3f);
	}

	private BlockState floor() {
		return random.nextFloat() < 0.25f ? Blocks.COARSE_DIRT.defaultBlockState()
				: random.nextFloat() < 0.3f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}
}
