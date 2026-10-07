package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * A ranger's radio shack, one room and an aerial. The set is still on the table. Now and then,
 * when you are near it, it picks something up.
 * <p>
 * Shorter or longer, flat-roofed or with a roof that slopes off the back, sometimes a porch out
 * front; the aerial on whichever corner it was put up on, as high as they could get it.
 */
final class RadioShack extends Build {
	private static final String[][] SIGNS = {{"", "IF IT TALKS", "DONT ANSWER", ""}, {"", "I HEARD MY", "OWN VOICE", "ON IT"},
			{"RADIO", "OUT OF", "ORDER", "(PLEASE)"}, {"", "STATION 4", "NO ONE", "ON DUTY"}};

	RadioShack(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	@Override
	void build() {
		int back = 2 + random.nextInt(2);                 // five deep, or six
		boolean slope = chance(0.5f);
		boolean porch = chance(0.45f);
		int front = -2;
		BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();

		fill(-3, 1, front - 3, 3, 6, back + 1, Blocks.AIR.defaultBlockState());
		for (int x = -2; x <= 2; x++) for (int z = front; z <= back; z++) {
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		for (int[] c : ring(-2, front, 2, back)) {
			boolean corner = Math.abs(c[0]) == 2 && (c[1] == front || c[1] == back);
			int height = slope && c[1] >= 1 ? 2 : 3;          // under a sloping roof, lower at the back
			for (int y = 1; y <= height; y++) put(c[0], y, c[1], corner ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : wall);
		}
		fill(0, 1, front, 0, 2, front, Blocks.AIR.defaultBlockState());
		// A window or two, wherever they were cut.
		put(2, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
		if (chance(0.6f)) put(-2, 2, 0, chance(0.5f) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		if (chance(0.5f)) put(1, 2, front, Blocks.GLASS_PANE.defaultBlockState());

		// The roof: flat, or stepping down to the back, a stair where it steps.
		if (slope) {
			for (int x = -3; x <= 3; x++) {
				for (int z = front - 1; z <= back + 1; z++) {
					if (z <= 0) put(x, 4, z, Blocks.SPRUCE_SLAB.defaultBlockState());
					else if (z == 1) put(x, 3, z, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
					else put(x, 3, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				}
			}
		} else {
			fill(-2, 4, front, 2, 4, back, Blocks.SPRUCE_SLAB.defaultBlockState());
		}

		// A porch, if they ever got round to it.
		if (porch) {
			for (int x = -2; x <= 2; x++) {
				put(x, 0, front - 1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
				foundation(x, 0, front - 1, Blocks.COBBLESTONE.defaultBlockState());
			}
			List<int[]> rail = new ArrayList<>();
			for (int x : new int[]{-2, 2}) {
				rail.add(new int[]{x, front - 1});
				put(x, 2, front - 1, Blocks.SPRUCE_FENCE.defaultBlockState());
				put(x, 3, front - 1, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
			connected(rail, 1, Blocks.SPRUCE_FENCE);
			if (!slope) fill(-2, 4, front - 1, 2, 4, front - 1, Blocks.SPRUCE_SLAB.defaultBlockState());
		}

		// The set: a jukebox on its table, the dials (note blocks), and the aerial above it.
		put(-1, 1, back - 1, Blocks.JUKEBOX.defaultBlockState());
		put(0, 1, back - 1, Blocks.NOTE_BLOCK.defaultBlockState());
		put(1, 1, back - 1, Blocks.LEVER.defaultBlockState());
		int ax = random.nextBoolean() ? -2 : 2;
		int az = random.nextBoolean() ? front : back;
		int roofAt = slope ? (az <= 0 ? 4 : 3) : 4;
		int aerial = roofAt + 4 + random.nextInt(5);
		for (int y = roofAt + 1; y <= aerial; y++) put(ax, y, az, Blocks.IRON_BARS.defaultBlockState());
		container(1, 1, front + 1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.CAMP);
		if (chance(0.5f)) put(-1, 1, front + 1, Blocks.CRAFTING_TABLE.defaultBlockState());

		// Outside: the sign, and their firewood stacked against the wall.
		if (chance(0.8f)) {
			int sx = 1;
			int sz = porch ? front - 2 : front - 1;
			put(sx, 1, sz, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
			Places.sign(level, at(sx, 1, sz), SIGNS[random.nextInt(SIGNS.length)]);
		}
		if (chance(0.55f)) {
			int side = random.nextBoolean() ? -3 : 3;
			for (int z = 0; z <= Math.min(1, back); z++) {
				int h = 1 + random.nextInt(2);
				for (int y = 1; y <= h; y++) {
					put(side, y, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
				}
				foundation(side, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		unsettle(-1, front + 1, 1, back - 1, 1, 1);
		steps(0, porch ? front - 1 : front, Direction.NORTH, Blocks.SPRUCE_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}
}
