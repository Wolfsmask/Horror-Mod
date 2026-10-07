package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A cottage in the house's village: one room, a pitched roof, a cold hearth and somebody's things.
 * Some have lost part of their roof. Its door is at the front (-z), with steps if the ground drops.
 * <p>
 * Each a little different: deeper or not, a chimney over the hearth, a lean-to for the wood,
 * the door still hanging in some.
 */
final class Cottage extends Build {
	Cottage(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void build() {
		BlockState air = Blocks.AIR.defaultBlockState();
		boolean spruce = chance(0.6f);
		BlockState planks = (spruce ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
		Block stair = spruce ? Blocks.SPRUCE_STAIRS : Blocks.OAK_STAIRS;
		BlockState log = (spruce ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG).defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		boolean collapsed = chance(0.4f);
		int back = chance(0.5f) ? 3 : 2;
		boolean chimney = chance(0.5f);
		boolean shed = !collapsed && chance(0.4f);

		fill(-4, 1, -4, 4, 9, back + 1, air);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= back; z++) {
				boolean edge = x == -3 || x == 3 || z == -3 || z == back;
				put(x, 0, z, edge ? old(cobble, mossy, 0.3f) : old(planks, Blocks.COARSE_DIRT.defaultBlockState(), 0.08f));
				foundation(x, 0, z, cobble);
				if (!edge) continue;
				put(x, 1, z, old(cobble, mossy, 0.35f));
				boolean corner = (x == -3 || x == 3) && (z == -3 || z == back);
				put(x, 2, z, corner ? log : planks);
				put(x, 3, z, corner ? log : planks);
				put(x, 4, z, corner ? log : planks);
			}
		}
		// The door, and a window in each side and the back.
		fill(0, 1, -3, 0, 2, -3, air);
		BlockState along = Blocks.GLASS_PANE.defaultBlockState()
				.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
		BlockState across = Blocks.GLASS_PANE.defaultBlockState()
				.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
		put(-3, 2, 0, along);
		put(3, 2, back - 2, along);
		put(0, 2, back, collapsed ? air : across);
		if (chance(0.5f)) {
			put(-2, 2, -3, across);
			put(2, 2, -3, chance(0.4f) ? air : across);
		}
		if (chance(0.35f)) {
			// The door, still on its hinges, left open.
			BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.OPEN, true);
			put(0, 1, -3, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
			put(0, 2, -3, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		}

		// A pitched roof, ridge running front to back, overhanging all round, gable ends filled.
		for (int z = -4; z <= back + 1; z++) {
			for (int k = 0; k <= 3; k++) {
				put(-4 + k, 4 + k, z, stairs(stair, Direction.EAST));
				put(4 - k, 4 + k, z, stairs(stair, Direction.WEST));
			}
			put(0, 7, z, planks);                                         // the ridge
			put(0, 8, z, Blocks.SPRUCE_SLAB.defaultBlockState());
		}
		for (int z : new int[]{-3, back}) {
			for (int y = 5; y <= 7; y++) {
				for (int x = -(7 - y); x <= 7 - y; x++) put(x, y, z, planks);
			}
		}
		if (collapsed) {
			// Part of the roof has come in, and lies on the floor.
			for (int x = 1; x <= 4; x++) {
				for (int z = -1; z <= back + 1; z++) {
					if (chance(0.7f)) fill(x, 4 + Math.max(0, 4 - x), z, x, 8, z, air);
				}
			}
			for (int n = 0; n < 4; n++) put(1 + random.nextInt(2), 1, -1 + random.nextInt(3), old(Blocks.GRAVEL.defaultBlockState(), cobble, 0.5f));
		}

		// The chimney, up the wall behind the hearth and out past the roof.
		if (chimney) {
			for (int y = 1; y <= 8; y++) put(-3, y, -1, old(cobble, mossy, 0.25f));
			put(-4, 1, -1, cobble);
			foundation(-4, 1, -1, cobble);
			for (int y = 2; y <= 3; y++) put(-4, y, -1, old(cobble, mossy, 0.25f));
		}
		// A lean-to on the side, where the wood was kept.
		if (shed) {
			for (int z = -1; z <= 1; z++) {
				put(4, 0, z, cobble);
				foundation(4, 0, z, cobble);
				put(5, 0, z, cobble);
				foundation(5, 0, z, cobble);
				put(5, 1, z, z == 0 ? Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z)
						: Blocks.SPRUCE_FENCE.defaultBlockState());
				put(5, 2, z, Blocks.SPRUCE_FENCE.defaultBlockState());
				put(4, 3, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
						net.minecraft.world.level.block.state.properties.SlabType.TOP));
				put(5, 3, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				put(4, 1, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
				if (chance(0.6f)) put(4, 2, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
			}
		}

		// What was left behind.
		container(-2, 1, back - 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.HOME);
		put(2, 1, back - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(-2, 1, -1, facing(Blocks.FURNACE.defaultBlockState(), Direction.EAST));   // facing into the room
		put(2, 1, -1, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(2, 2, -1, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
		put(-2, 3, back - 1, Blocks.COBWEB.defaultBlockState());
		if (chance(0.5f)) put(2, 3, -2, Blocks.COBWEB.defaultBlockState());
		unsettle(-2, -2, 2, back - 1, 1, 2);
		steps(0, -3, Direction.NORTH, Blocks.COBBLESTONE_STAIRS, cobble);
	}
}
