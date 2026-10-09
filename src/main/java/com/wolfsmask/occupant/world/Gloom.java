package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The land itself, made wrong, a chunk at a time, after its trees have grown. In the woods: webs
 * strung under the canopy, the floor gone to moss and fern and rot, mushrooms in the shade, trees
 * that died standing. Out in the open: the grass dying back in patches, dead bushes, a lone dead
 * tree, now and then a ring of old stones with something in the middle, or a scarecrow facing the
 * wrong way. Never on anything a player made: it is all done as the world is first made.
 */
final class Gloom {
	private Gloom() {
	}

	/** World generation's turn at the chunk {@code origin} is in. */
	static boolean place(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int cx = origin.getX() & ~15;
		int cz = origin.getZ() & ~15;
		int top = Places.surface(level, cx + 8, cz + 8);
		Holder<Biome> biome = level.getBiome(new BlockPos(cx + 8, top, cz + 8));
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
			woods(level, random, cx, cz);
		} else if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW)
				|| biome.is(Biomes.SNOWY_PLAINS)) {
			open(level, random, cx, cz);
		}
		return true;
	}

	/** The top of the ground at a column of this chunk (not counting the trees), or null if it is not ground. */
	private static BlockPos ground(WorldGenLevel level, RandomSource random, int cx, int cz) {
		int x = cx + 2 + random.nextInt(12);
		int z = cz + 2 + random.nextInt(12);
		BlockPos g = new BlockPos(x, Places.surface(level, x, z) - 1, z);
		return level.ensureCanWrite(g) && Blight.natural(level.getBlockState(g)) ? g : null;
	}

	private static void woods(WorldGenLevel level, RandomSource random, int cx, int cz) {
		// Webs, strung under the canopy.
		int webs = random.nextInt(4);
		for (int i = 0; i < webs; i++) {
			BlockPos g = ground(level, random, cx, cz);
			if (g == null) continue;
			for (int up = 3; up <= 14; up++) {
				BlockPos leaf = g.above(up);
				if (!level.getBlockState(leaf).is(BlockTags.LEAVES)) continue;
				BlockPos under = leaf.below();
				if (level.getBlockState(under).isAir()) Blight.set(level, under, Blocks.COBWEB.defaultBlockState());
				break;
			}
		}
		// The floor: moss, fern, rot, and mushrooms in the shade.
		for (int i = 0; i < 10; i++) {
			BlockPos g = ground(level, random, cx, cz);
			if (g == null || !Blight.open(level, g.above())) continue;
			float r = random.nextFloat();
			if (r < 0.32f) {
				Blight.set(level, g.above(), Blocks.MOSS_CARPET.defaultBlockState());
			} else if (r < 0.52f) {
				Blight.set(level, g.above(), Blocks.FERN.defaultBlockState());
			} else if (r < 0.66f) {
				Blight.set(level, g, Blocks.PODZOL.defaultBlockState());
				Blight.set(level, g.above(), Blocks.DEAD_BUSH.defaultBlockState());
			} else if (r < 0.78f) {
				Blight.set(level, g, Blocks.PODZOL.defaultBlockState());
				Blight.set(level, g.above(), (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
			} else if (r < 0.86f) {
				Blight.set(level, g, Blocks.COARSE_DIRT.defaultBlockState());
				if (Blight.open(level, g.above()) && !level.getBlockState(g.above()).isAir()) Blight.set(level, g.above(), Blocks.AIR.defaultBlockState());
			}
		}
		// Trees that died where they stood, among the living ones.
		if (random.nextFloat() < 0.35f) {
			BlockPos g = ground(level, random, cx, cz);
			if (g != null && Blight.open(level, g.above())) Blight.deadTree(level, random, g);
		}
		if (random.nextFloat() < 0.07f) {
			BlockPos g = ground(level, random, cx, cz);
			if (g != null && Blight.open(level, g.above())) Blight.remains(level, random, g);
		}
	}

	private static void open(WorldGenLevel level, RandomSource random, int cx, int cz) {
		// The grass dying back, in patches.
		int patches = 1 + random.nextInt(2);
		for (int i = 0; i < patches; i++) {
			BlockPos g = ground(level, random, cx, cz);
			if (g != null && Blight.open(level, g.above())) Blight.badGround(level, random, g);
		}
		// Dead bushes, here and there in the grass.
		for (int i = 0; i < 4; i++) {
			BlockPos g = ground(level, random, cx, cz);
			if (g == null || !Blight.open(level, g.above())) continue;
			if (random.nextFloat() < 0.5f) Blight.set(level, g, Blocks.COARSE_DIRT.defaultBlockState());
			Blight.set(level, g.above(), Blocks.DEAD_BUSH.defaultBlockState());
		}
		float r = random.nextFloat();
		BlockPos g = ground(level, random, cx, cz);
		if (g == null || !Blight.open(level, g.above())) return;
		if (r < 0.16f) Blight.deadTree(level, random, g);
		else if (r < 0.19f) stones(level, random, g);
		else if (r < 0.22f) scarecrow(level, random, g);
		else if (r < 0.27f) Blight.remains(level, random, g);
	}

	/** A ring of old stones, leaning, half sunk, and something left in the middle of them. */
	private static void stones(WorldGenLevel level, RandomSource random, BlockPos centre) {
		int count = 5 + random.nextInt(3);
		double turn = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			double a = turn + i * Math.PI * 2 / count;
			int x = centre.getX() + (int) Math.round(Math.cos(a) * 3.5);
			int z = centre.getZ() + (int) Math.round(Math.sin(a) * 3.5);
			BlockPos g = new BlockPos(x, Places.surface(level, x, z) - 1, z);
			if (Math.abs(g.getY() - centre.getY()) > 1 || !level.ensureCanWrite(g) || !Blight.natural(level.getBlockState(g))) continue;
			int tall = 1 + random.nextInt(3);
			for (int y = 1; y <= tall; y++) {
				if (!Blight.open(level, g.above(y))) break;
				float f = random.nextFloat();
				Blight.set(level, g.above(y), (f < 0.5f ? Blocks.MOSSY_COBBLESTONE : f < 0.8f ? Blocks.COBBLESTONE : Blocks.STONE).defaultBlockState());
			}
		}
		Blight.set(level, centre, Blocks.BONE_BLOCK.defaultBlockState());
		if (Blight.open(level, centre.above())) {
			Blight.set(level, centre.above(), Blocks.SKELETON_SKULL.defaultBlockState()
					.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		}
	}

	/** A scarecrow, over nothing that grows, its head turned to look the way you came. */
	private static void scarecrow(WorldGenLevel level, RandomSource random, BlockPos g) {
		for (int y = 1; y <= 3; y++) if (!Blight.open(level, g.above(y))) return;
		Direction arm = random.nextBoolean() ? Direction.EAST : Direction.NORTH;
		BlockState post = Blocks.SPRUCE_FENCE.defaultBlockState();
		Blight.set(level, g, Blocks.COARSE_DIRT.defaultBlockState());
		Blight.set(level, g.above(), post);
		Blight.set(level, g.above(2), post.setValue(prop(arm), true).setValue(prop(arm.getOpposite()), true));
		for (Direction side : new Direction[]{arm, arm.getOpposite()}) {
			BlockPos p = g.above(2).relative(side);
			if (Blight.open(level, p)) Blight.set(level, p, post.setValue(prop(side.getOpposite()), true));
		}
		Blight.set(level, g.above(3), Blocks.CARVED_PUMPKIN.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
	}

	private static net.minecraft.world.level.block.state.properties.BooleanProperty prop(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}
}
