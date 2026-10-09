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
	void make() {
		// The mark on the surface, seen from a long way off even in the fog: a clearing where nothing
		// grows, ringed with trees that died standing, bones about it, the earth it dug out heaped up
		// round a hole that is wide and black.
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (x * x + z * z > 42 + random.nextInt(8)) continue;
				int g = ground(x, z);
				if (Math.abs(g) > 2) continue;
				float f = random.nextFloat();
				put(x, g, z, (f < 0.45f ? Blocks.COARSE_DIRT : f < 0.75f ? Blocks.ROOTED_DIRT : Blocks.PODZOL).defaultBlockState());
				BlockState above = level.getBlockState(at(x, g + 1, z));
				if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) put(x, g + 1, z, Blocks.AIR.defaultBlockState());
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + random.nextDouble() * 0.6;
			deadTree((int) Math.round(Math.cos(a) * 6.5), (int) Math.round(Math.sin(a) * 6.5));
		}
		for (int i = 0; i < 7; i++) {
			int x = random.nextInt(11) - 5, z = random.nextInt(11) - 5;
			if (Math.abs(x) <= 2 && Math.abs(z) <= 2) continue;
			int g = ground(x, z) + 1;
			if (Math.abs(g) > 3 || !level.getBlockState(at(x, g, z)).isAir()) continue;
			put(x, g, z, switch (random.nextInt(3)) {
				case 0 -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16));
				case 1 -> Blocks.BONE_BLOCK.defaultBlockState();
				default -> Blocks.DEAD_BUSH.defaultBlockState();
			});
		}
		// The earth it dug out, heaped beside the hole.
		for (int[] c : new int[][]{{-2, 1}, {2, 2}, {-2, 3}, {2, 0}, {1, 4}}) {
			int g = ground(c[0], c[1]) + 1;
			if (Math.abs(g) <= 2 && level.getBlockState(at(c[0], g, c[1])).isAir()) {
				put(c[0], g, c[1], (chance(0.5f) ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState());
			}
		}
		// The hole: its mouth three wide and black, and a ladder down its side.
		for (int y = 0; y >= -2; y--) {
			for (int x = -1; x <= 1; x++) for (int z = 0; z <= 2; z++) put(x, y, z, Blocks.AIR.defaultBlockState());
		}
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

	/** A tree that died standing, at (x, z) in the lair's own coordinates, on whatever ground is there. */
	private void deadTree(int x, int z) {
		int g = ground(x, z);
		if (Math.abs(g) > 3) return;
		BlockState log = (chance(0.5f) ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
		int tall = 5 + random.nextInt(4);
		for (int y = 1; y <= tall; y++) {
			if (!level.getBlockState(at(x, g + y, z)).isAir() && !level.getBlockState(at(x, g + y, z)).canBeReplaced()) return;
		}
		for (int y = 1; y <= tall; y++) put(x, g + y, z, log);
		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (Direction d : new Direction[]{side, side.getOpposite()}) {
			int y = g + tall - 1 - random.nextInt(2);
			if (level.getBlockState(at(x + d.getStepX(), y, z + d.getStepZ())).isAir()) {
				put(x + d.getStepX(), y, z + d.getStepZ(), log.setValue(BlockStateProperties.AXIS, d.getAxis()));
			}
		}
	}

	private BlockState stone() {
		return old(Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), 0.3f);
	}

	private BlockState floor() {
		return random.nextFloat() < 0.25f ? Blocks.COARSE_DIRT.defaultBlockState()
				: random.nextFloat() < 0.3f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}
}
