package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.HauntData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Where the survivor went at the end: a camp of their own, out past the fog, made for each reader
 * of the log when they reach the page that says where it is. What is left of them is there, and
 * their last page is in the chest.
 */
public final class LastCamp {
	private LastCamp() {
	}

	/** Builds it for this player, somewhere between 160 and 220 blocks off; false if nowhere would do. */
	public static boolean build(ServerPlayer player, HauntData data) {
		ServerLevel level = Compat.level(player);
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double d = 160 + random.nextInt(60);
			int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
			level.getChunk(x >> 4, z >> 4);
			BlockPos base = Places.flatGround(level, new BlockPos(x, Places.surface(level, x, z), z), 5, 2);
			if (base == null || Places.looksBuilt(level, base, 8)) continue;    // never on anything anyone made
			build(level, base, random);
			data.lastCamp = 1;
			data.lastCampX = base.getX();
			data.lastCampZ = base.getZ();
			return true;
		}
		return false;
	}

	/** The camp itself, at {@code base} (the ground in its middle). */
	public static void build(ServerLevel level, BlockPos base, RandomSource random) {
		new Camp(level, base, Rotation.NONE, random).build();
		// What is left of them, by the fire.
		BlockPos by = base.offset(1, 0, 2);
		int y = Places.surface(level, by.getX(), by.getZ());
		level.setBlock(new BlockPos(by.getX(), y, by.getZ()), Blocks.SKELETON_SKULL.defaultBlockState()
				.setValue(BlockStateProperties.ROTATION_16, 8), Block.UPDATE_ALL);
		BlockPos bones = base.offset(2, 0, 3);
		int by2 = Places.surface(level, bones.getX(), bones.getZ());
		level.setBlock(new BlockPos(bones.getX(), by2, bones.getZ()), Blocks.BONE_BLOCK.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
	}

	/** Is this one of the containers at this player's last camp? */
	public static boolean isTheirs(HauntData data, BlockPos pos) {
		return data.lastCamp == 1 && Math.abs(pos.getX() - data.lastCampX) <= 6 && Math.abs(pos.getZ() - data.lastCampZ) <= 6;
	}
}
