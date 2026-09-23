package com.wolfsmask.occupant.director.events;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Small block checks shared by the events that touch the world. */
final class WorldBlocks {
	private WorldBlocks() {
	}

	/** The bottom half of a closed wooden door (the kind a hand can open). */
	static boolean isClosedWoodenDoor(ServerWorld world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.isIn(BlockTags.WOODEN_DOORS) && s.getBlock() instanceof DoorBlock
				&& s.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !s.get(DoorBlock.OPEN);
	}

	static boolean isOpenDoor(ServerWorld world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.getBlock() instanceof DoorBlock && s.get(DoorBlock.OPEN);
	}

	static boolean isTorch(BlockState s) {
		return s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH) || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.SOUL_WALL_TORCH);
	}

	/** Ground nobody built: dirt, stone, sand, gravel, snow. */
	static boolean isNaturalFloor(BlockState s) {
		return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.SAND)
				|| s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.PODZOL) || s.isOf(Blocks.COARSE_DIRT);
	}

	static boolean touchesFluid(ServerWorld world, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (!world.getFluidState(pos.offset(d)).isEmpty()) return true;
		}
		return false;
	}
}
