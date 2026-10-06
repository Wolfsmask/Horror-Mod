package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ranger's radio shack, one room and an aerial. The set is still on the table. Now and then,
 * when you are near it, it picks something up.
 */
final class RadioShack extends Build {
	private static final String[][] SIGNS = {{"", "IF IT TALKS", "DONT ANSWER", ""}, {"", "I HEARD MY", "OWN VOICE", "ON IT"},
			{"RADIO", "OUT OF", "ORDER", "(PLEASE)"}};

	RadioShack(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void build() {
		BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
		fill(-3, 1, -3, 3, 5, 3, Blocks.AIR.defaultBlockState());
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		for (int[] c : ring(-2, -2, 2, 2)) {
			for (int y = 1; y <= 3; y++) put(c[0], y, c[1], (Math.abs(c[0]) == 2 && Math.abs(c[1]) == 2)
					? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : wall);
		}
		fill(0, 1, -2, 0, 2, -2, Blocks.AIR.defaultBlockState());
		put(2, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
		fill(-2, 4, -2, 2, 4, 2, Blocks.SPRUCE_SLAB.defaultBlockState());
		// The set: a jukebox on its table, the dials (note blocks), and the aerial above it.
		put(-1, 1, 1, Blocks.JUKEBOX.defaultBlockState());
		put(0, 1, 1, Blocks.NOTE_BLOCK.defaultBlockState());
		put(1, 1, 1, Blocks.LEVER.defaultBlockState());
		for (int y = 5; y <= 9; y++) put(-1, y, 1, Blocks.IRON_BARS.defaultBlockState());
		put(-1, 10, 1, Blocks.LIGHTNING_ROD.defaultBlockState());
		container(1, 1, -1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.CAMP);
		put(1, 1, -3, Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16, 8));
		Places.sign(level, at(1, 1, -3), SIGNS[random.nextInt(SIGNS.length)]);
		steps(0, -2, Direction.NORTH, Blocks.SPRUCE_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
