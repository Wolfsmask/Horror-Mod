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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * An old fire lookout: four legs braced together, a ladder up the inside to a hatch in the floor,
 * and a lookout box on top under a pitched roof, its railing gone in places. From up there you
 * can see right to the edge of the fog.
 * <p>
 * Built to be climbed: the ladder starts at the ground on the open side and comes up through the
 * middle of the floor, clear of every post, so whoever climbs it steps straight off onto the
 * boards.
 */
final class Watchtower extends Build {
	Watchtower(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void build() {
		int top = 8 + random.nextInt(4);                       // how high the floor is: no two the same
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();

		// Clear the air it stands in.
		fill(-4, 1, -4, 4, top + 6, 4, Blocks.AIR.defaultBlockState());
		// Four legs, set in stone, up to the floor.
		for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			foundation(c[0], 1, c[1], Blocks.COBBLESTONE.defaultBlockState());
			for (int y = 1; y < top; y++) put(c[0], y, c[1], log);
		}
		// Beams between the legs on three sides, at two heights; the south side is left open for
		// the ladder. Here and there one has rotted through and gone.
		for (int y : new int[]{3, top - 3}) {
			for (int i = -1; i <= 1; i++) {
				if (chance(0.88f)) put(i, y, -2, log.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
				if (chance(0.88f)) put(-2, y, i, log.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
				if (chance(0.88f)) put(2, y, i, log.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
			}
		}
		// The ladder: on the inside face of the south-west leg, from the ground to the hatch.
		for (int y = 1; y <= top; y++) {
			put(-1, y, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		}

		// The floor, overhanging the legs, with the hatch the ladder comes up through.
		fill(-3, top, -3, 3, top, 3, planks);
		put(-1, top, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		// Under the overhang, a beam on each side so it does not float.
		for (int i = -3; i <= 3; i++) {
			put(i, top - 1, -3, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			put(i, top - 1, 3, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		}
		// Corner posts above the floor hold up the roof; the railing between them, half gone.
		for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			for (int y = top + 1; y <= top + 3; y++) put(c[0], y, c[1], log);
		}
		List<int[]> rail = new ArrayList<>();
		for (int[] c : ring(-3, -3, 3, 3)) {
			boolean corner = Math.abs(c[0]) == 3 && Math.abs(c[1]) == 3;
			if (!corner && chance(0.75f)) rail.add(c);
		}
		connected(rail, top + 1, Blocks.SPRUCE_FENCE);

		// A pitched roof: stairs all round the edge, rising to a ridge of slabs.
		int roof = top + 4;
		for (int x = -4; x <= 4; x++) {
			put(x, roof, -4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			put(x, roof, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		}
		for (int z = -3; z <= 3; z++) {
			put(-4, roof, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(4, roof, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
		}
		fill(-3, roof, -3, 3, roof, 3, planks);
		for (int x = -3; x <= 3; x++) {
			put(x, roof + 1, -3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			put(x, roof + 1, 3, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		}
		for (int z = -2; z <= 2; z++) {
			put(-3, roof + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(3, roof + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
		}
		fill(-2, roof + 2, -2, 2, roof + 2, 2, Blocks.SPRUCE_SLAB.defaultBlockState());
		// What the watcher left: a stool, their things, and them.
		container(2, top + 1, -2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.CAMP);
		put(1, top + 1, -2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
		if (chance(0.6f)) put(-2, top + 1, -2, Blocks.COBWEB.defaultBlockState());
		if (chance(0.5f)) {
			put(2, top + 1, 0, Blocks.SKELETON_SKULL.defaultBlockState()
					.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		}
	}
}
