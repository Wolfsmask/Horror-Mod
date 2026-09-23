package com.wolfsmask.occupant.director.events;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Small block checks shared by the events that touch the world. */
final class WorldBlocks {
	private WorldBlocks() {
	}

	/** The bottom half of a closed wooden door (the kind a hand can open). */
	static boolean isClosedWoodenDoor(ServerLevel world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.is(BlockTags.WOODEN_DOORS) && s.getBlock() instanceof DoorBlock
				&& s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !s.getValue(DoorBlock.OPEN);
	}

	static boolean isOpenDoor(ServerLevel world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.OPEN);
	}

	static boolean isTorch(BlockState s) {
		return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
	}

	/** Ground nobody built: dirt, stone, sand, gravel, snow. */
	static boolean isNaturalFloor(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT);
	}

	static boolean touchesFluid(ServerLevel world, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (!world.getFluidState(pos.relative(d)).isEmpty()) return true;
		}
		return false;
	}
}
