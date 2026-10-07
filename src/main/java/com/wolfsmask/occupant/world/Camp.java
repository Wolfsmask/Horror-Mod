package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * An abandoned camp: a cold fire, logs to sit on, a little tent, their supplies, and a sign that
 * somebody put up on their way out. Everything sits on the ground as it lies.
 */
final class Camp extends Build {
	private static final String[][] SIGNS = {
			{"WE WERE FOUR", "", "THEN THREE", ""},
			{"DON'T FOLLOW", "THE TALL ONE", "", ""},
			{"IT WEARS", "OUR FACES", "", "TURN BACK"},
			{"IF YOU SEE IT", "DON'T LOOK", "AWAY", ""},
			{"GONE NORTH", "", "IT FOLLOWED", ""}};

	Camp(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	/** On the ground at (x, z), whatever height that is. */
	private int floor(int x, int z) {
		return ground(x, z) + 1;
	}

	@Override
	void build() {
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				int g = floor(x, z);
				fill(x, g, z, x, g + 4, z, Blocks.AIR.defaultBlockState());
			}
		}
		// The fire, burnt out, in a patch of trodden earth.
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put(x, floor(x, z) - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
		put(0, floor(0, 0), 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		// Logs to sit on, round it.
		BlockState along = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		BlockState across = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		// Which of the seats are still there.
		if (chance(0.8f)) put(-2, floor(-2, -1), -1, along);
		if (chance(0.8f)) put(-2, floor(-2, 0), 0, along);
		if (chance(0.7f)) put(-1, floor(-1, 2), 2, across);
		if (chance(0.7f)) put(0, floor(0, 2), 2, across);
		if (chance(0.3f)) put(-1, floor(-1, -2), -2, across);

		// The tent: an A-frame of boards over a space three wide, open at the front, closed at the
		// back, their things inside; as deep as they had boards for. Now and then it has come
		// down, and is a heap of boards on the ground.
		int t = floor(3, 0);
		int back = -1 + random.nextInt(3);                     // the back wall's z: -1, 0 or 1
		boolean fallen = chance(0.25f);
		for (int z = -2; z <= back; z++) {
			fill(1, t, z, 5, t + 3, z, Blocks.AIR.defaultBlockState());
			for (int x = 1; x <= 5; x++) foundation(x, t, z, Blocks.DIRT.defaultBlockState());
			if (fallen) {
				for (int x = 1; x <= 5; x++) if (chance(0.7f)) put(x, t, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				continue;
			}
			put(1, t, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(5, t, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
			put(2, t + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
			put(4, t + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
			put(3, t + 2, z, Blocks.SPRUCE_SLAB.defaultBlockState());
		}
		if (!fallen) {
			fill(2, t, back, 4, t, back, Blocks.SPRUCE_PLANKS.defaultBlockState());
			put(3, t + 1, back, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		container(3, t, back - 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.CAMP);

		// Supplies by the fire, and a sign at the edge of the camp, facing whoever comes.
		container(-3, floor(-3, 2), 2, facing(Blocks.CHEST.defaultBlockState(), Direction.EAST), Loot.Kind.CAMP);
		if (chance(0.7f)) {
			int sy = floor(0, -4);
			put(0, sy, -4, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
			Places.sign(level, at(0, sy, -4), SIGNS[random.nextInt(SIGNS.length)]);
		}
	}
}
