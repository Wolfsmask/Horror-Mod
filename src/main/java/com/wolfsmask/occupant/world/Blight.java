package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The land itself, a little wrong. Not structures, only the ground: a dead tree with no leaves
 * left on it, a fallen trunk going soft, a patch where nothing grows, now and then a bone in the
 * earth. Scattered thinly through the woods and open country, so a walk anywhere passes one or
 * two, and none of it is ever more than a little odd.
 * <p>
 * Only ever on natural ground, into open air, and never over anything a player could have made.
 */
final class Blight {
	private Blight() {
	}

	/** One chance in this chunk. */
	static void tryPlace(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int x = (origin.getX() & ~15) + 2 + random.nextInt(12);
		int z = (origin.getZ() & ~15) + 2 + random.nextInt(12);
		int top = Places.surface(level, x, z);
		BlockPos ground = new BlockPos(x, top - 1, z);
		if (!level.ensureCanWrite(ground) || !natural(level.getBlockState(ground))) return;
		BlockPos above = ground.above();
		if (!open(level, above)) return;
		float roll = random.nextFloat();
		if (roll < 0.38f) deadTree(level, random, ground);
		else if (roll < 0.68f) fallenLog(level, random, ground);
		else if (roll < 0.95f) badGround(level, random, ground);
		else remains(level, random, ground);
	}

	private static boolean natural(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT)
				|| s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT);
	}

	/** Air, or something the ground grows that would be trampled anyway (grass, snow, flowers). */
	private static boolean open(WorldGenLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
	}

	private static void set(WorldGenLevel level, BlockPos p, BlockState s) {
		if (level.ensureCanWrite(p)) level.setBlock(p, s, Block.UPDATE_CLIENTS);
	}

	private static Block log(WorldGenLevel level, BlockPos ground, RandomSource random) {
		BlockState above = level.getBlockState(ground.above());
		if (above.is(Blocks.SNOW)) return Blocks.SPRUCE_LOG;
		return random.nextFloat() < 0.5f ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG;
	}

	/** A trunk, five or six high, with a few bare branches. Not a leaf on it. */
	private static void deadTree(WorldGenLevel level, RandomSource random, BlockPos ground) {
		Block log = log(level, ground, random);
		int height = 4 + random.nextInt(4);
		for (int y = 1; y <= height + 1; y++) if (!open(level, ground.above(y))) return;
		for (int y = 1; y <= height; y++) set(level, ground.above(y), log.defaultBlockState());
		int branches = 1 + random.nextInt(3);
		for (int i = 0; i < branches; i++) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int from = 2 + random.nextInt(Math.max(1, height - 2));
			int length = 1 + random.nextInt(2);
			BlockPos p = ground.above(from);
			for (int k = 1; k <= length; k++) {
				BlockPos b = p.relative(d, k);
				if (!open(level, b)) break;
				set(level, b, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis()));
			}
			// The end of a long branch turns up, like a hand.
			if (length == 2 && open(level, p.relative(d, 2).above())) set(level, p.relative(d, 2).above(), log.defaultBlockState());
		}
		if (random.nextFloat() < 0.3f && open(level, ground.above(height).relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)))) {
			set(level, ground.above(height).relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)), Blocks.COBWEB.defaultBlockState());
		}
	}

	/** A trunk that came down a long time ago, with moss on it and mushrooms beside it. */
	private static void fallenLog(WorldGenLevel level, RandomSource random, BlockPos ground) {
		Block log = log(level, ground, random);
		Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int length = 3 + random.nextInt(3);
		for (int k = 0; k < length; k++) {
			BlockPos p = ground.above().relative(d, k);
			BlockPos under = p.below();
			if (!open(level, p) || level.getBlockState(under).canBeReplaced() || !level.getBlockState(under).getFluidState().isEmpty()) {
				if (k < 3) return;                                   // too short to be a fallen tree
				length = k;
				break;
			}
		}
		for (int k = 0; k < length; k++) {
			BlockPos p = ground.above().relative(d, k);
			set(level, p, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis()));
			if (random.nextFloat() < 0.45f && open(level, p.above())) set(level, p.above(), Blocks.MOSS_CARPET.defaultBlockState());
		}
		BlockPos side = ground.above().relative(d.getClockWise());
		if (random.nextFloat() < 0.6f && open(level, side) && !level.getBlockState(side.below()).canBeReplaced()) {
			set(level, side, (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
		}
	}

	/** A ragged patch where nothing will grow: bare earth, roots, a dead bush. */
	private static void badGround(WorldGenLevel level, RandomSource random, BlockPos ground) {
		int r = 2 + random.nextInt(2);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r + random.nextInt(3) - 1) continue;
				BlockPos p = ground.offset(dx, 0, dz);
				int top = Places.surface(level, p.getX(), p.getZ());
				if (Math.abs(top - 1 - ground.getY()) > 1) continue;
				p = new BlockPos(p.getX(), top - 1, p.getZ());
				if (!natural(level.getBlockState(p))) continue;
				float f = random.nextFloat();
				set(level, p, (f < 0.55f ? Blocks.COARSE_DIRT : f < 0.85f ? Blocks.PODZOL : Blocks.ROOTED_DIRT).defaultBlockState());
				BlockPos up = p.above();
				BlockState there = level.getBlockState(up);
				if (!there.isAir() && there.canBeReplaced() && !there.is(Blocks.SNOW)) set(level, up, Blocks.AIR.defaultBlockState());
				if (random.nextFloat() < 0.08f && level.getBlockState(up).isAir()) set(level, up, Blocks.DEAD_BUSH.defaultBlockState());
			}
		}
	}

	/** Something that was alive once, mostly in the ground. */
	private static void remains(WorldGenLevel level, RandomSource random, BlockPos ground) {
		set(level, ground, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
				random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z));
		BlockPos next = ground.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
		if (natural(level.getBlockState(next))) set(level, next, Blocks.COARSE_DIRT.defaultBlockState());
		if (random.nextFloat() < 0.35f && open(level, ground.above())) {
			set(level, ground.above(), Blocks.SKELETON_SKULL.defaultBlockState()
					.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		}
	}
}
