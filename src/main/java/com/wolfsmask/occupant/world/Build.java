package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Something built into the world: a house, a cottage, a ruin, a camp. Blocks are placed in the
 * build's own coordinates (x across the front, z from front to back, y up from its floor) and
 * turned with it, block states and all, so a furnace that faces into the room unrotated still
 * faces into the room however the build is turned.
 */
abstract class Build {
	final WorldGenLevel level;
	final BlockPos base;
	final Rotation rotation;
	final RandomSource random;
	/** What it is built of: the builders write spruce and cobblestone, and this swaps them. */
	Palette palette = Palette.AS_BUILT;

	Build(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		this.level = level;
		this.base = base;
		this.rotation = rotation;
		this.random = random;
	}

	abstract void build();

	/** Where a spot in the build's own coordinates is in the world. */
	BlockPos at(int x, int y, int z) {
		return base.offset(new BlockPos(x, y, z).rotate(rotation));
	}

	void put(int x, int y, int z, BlockState state) {
		BlockPos pos = at(x, y, z);
		if (level.ensureCanWrite(pos)) level.setBlock(pos, palette.apply(state, random).rotate(rotation), Block.UPDATE_CLIENTS);
	}

	void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) put(x, y, z, state);
	}

	BlockState old(BlockState clean, BlockState aged, float chance) {
		return random.nextFloat() < chance ? aged : clean;
	}

	boolean chance(float p) {
		return random.nextFloat() < p;
	}

	/** Facing a way in the build's own coordinates (north is its front, -z). */
	static BlockState facing(BlockState state, Direction dir) {
		return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
	}

	static BlockState stairs(Block block, Direction dir) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
	}

	/** The top of the ground at a spot, as a y in the build's own coordinates (0 = level with its floor). */
	int ground(int x, int z) {
		BlockPos p = at(x, 0, z);
		Heightmap.Types surface = level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG;
		return level.getHeight(surface, p.getX(), p.getZ()) - 1 - base.getY();
	}

	/** Fills from just under {@code y} down to the ground with {@code state}, so nothing floats. */
	void foundation(int x, int y, int z, BlockState state) {
		for (int d = y - 1; d >= y - 12; d--) {
			BlockPos p = at(x, d, z);
			BlockState there = level.getBlockState(p);
			if (!there.isAir() && there.getFluidState().isEmpty() && !there.canBeReplaced()) break;
			put(x, d, z, state);
		}
	}

	/**
	 * Steps from a doorway out and down to the ground: from ({@code x}, floor, {@code z}) going
	 * the way {@code out} points, one block down each step, until they meet the ground.
	 */
	void steps(int x, int z, Direction out, Block stair, BlockState fillBelow) {
		Direction up = out.getOpposite();
		for (int k = 1; k <= 8; k++) {
			int sx = x + out.getStepX() * k;
			int sz = z + out.getStepZ() * k;
			int y = 1 - k;                                   // the first step is a block below the doorway
			if (ground(sx, sz) >= y) break;                  // the ground is already this high: done
			fill(sx, y + 1, sz, sx, y + 3, sz, Blocks.AIR.defaultBlockState());
			put(sx, y, sz, stairs(stair, up));
			foundation(sx, y, sz, fillBelow);
		}
	}

	/**
	 * A container with something left in it, and a page of the log for whoever opens it first.
	 * {@code block} is a chest or a barrel; {@code facing} the way its front looks.
	 */
	void container(int x, int y, int z, BlockState state, Loot.Kind kind) {
		put(x, y, z, state);
		BlockPos pos = at(x, y, z);
		Loot.fill(level, pos, random, kind);
	}

	/**
	 * Fences or panes over a set of cells ({x, z} pairs, all at height {@code y}), each joined to
	 * its neighbours in the set, so they make a run rather than a row of lone posts.
	 */
	void connected(java.util.List<int[]> cells, int y, Block block) {
		java.util.Set<Long> set = new java.util.HashSet<>();
		for (int[] c : cells) set.add(key(c[0], c[1]));
		for (int[] c : cells) {
			BlockState state = block.defaultBlockState()
					.setValue(BlockStateProperties.NORTH, set.contains(key(c[0], c[1] - 1)))
					.setValue(BlockStateProperties.SOUTH, set.contains(key(c[0], c[1] + 1)))
					.setValue(BlockStateProperties.WEST, set.contains(key(c[0] - 1, c[1])))
					.setValue(BlockStateProperties.EAST, set.contains(key(c[0] + 1, c[1])));
			put(c[0], y, c[1], state);
		}
	}

	/** The cells round the edge of a rectangle, less any listed in {@code gaps}. */
	static java.util.List<int[]> ring(int x0, int z0, int x1, int z1, int[]... gaps) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x != x0 && x != x1 && z != z0 && z != z1) continue;
				boolean gap = false;
				for (int[] g : gaps) gap |= g[0] == x && g[1] == z;
				if (!gap) out.add(new int[]{x, z});
			}
		}
		return out;
	}

	/** Words scrawled on a sign and left on the floor. */
	private static final String[][] SCRAWLS = {{"", "IT SAW ME", "", ""}, {"", "DONT TURN", "AROUND", ""},
			{"I CAN HEAR", "IT", "BREATHING", ""}, {"", "IT KNOWS", "YOUR NAME", ""}, {"", "COUNT", "THE LEGS", ""},
			{"", "IT WAS HERE", "", ""}, {"WHY DID", "YOU LEAVE", "IT OUT", "THERE"}, {"", "NOT ALONE", "NOT ALONE", "NOT ALONE"}};

	/**
	 * The little things that are wrong, left about the floor between ({@code x0}, {@code z0}) and
	 * ({@code x1}, {@code z1}): a dried trail across it, a skull on its side, a dead plant in its
	 * pot, candles burnt down to nothing, a chair turned to face the wall, words on a sign. Only
	 * ever on empty floor. With {@code y} at {@link Integer#MIN_VALUE}, on the ground wherever it is.
	 */
	void unsettle(int x0, int z0, int x1, int z1, int y, int count) {
		for (int placed = 0, tries = 0; placed < count && tries < count * 10; tries++) {
			int x = x0 + random.nextInt(x1 - x0 + 1);
			int z = z0 + random.nextInt(z1 - z0 + 1);
			int fy = y == Integer.MIN_VALUE ? ground(x, z) + 1 : y;
			if (!emptyFloor(x, fy, z)) continue;
			switch (random.nextInt(7)) {
				case 0 -> {
					// A trail, dried dark, a few steps long.
					Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
					int length = 3 + random.nextInt(3);
					for (int k = 0; k < length; k++) {
						int tx = x + d.getStepX() * k, tz = z + d.getStepZ() * k;
						if (!emptyFloor(tx, fy, tz)) break;
						put(tx, fy, tz, Blocks.REDSTONE_WIRE.defaultBlockState());
					}
				}
				case 1 -> put(x, fy, z, Blocks.SKELETON_SKULL.defaultBlockState()
						.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
				case 2 -> put(x, fy, z, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
				case 3 -> put(x, fy, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + random.nextInt(4)));
				case 4 -> put(x, fy, z, stairs(Blocks.SPRUCE_STAIRS, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
				case 5 -> put(x, fy, z, Blocks.COBWEB.defaultBlockState());
				default -> {
					put(x, fy, z, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
					Places.sign(level, at(x, fy, z), SCRAWLS[random.nextInt(SCRAWLS.length)]);
				}
			}
			placed++;
		}
	}

	/** Air at ({@code x}, {@code y}, {@code z}), on something solid. */
	private boolean emptyFloor(int x, int y, int z) {
		BlockPos p = at(x, y, z);
		if (!level.ensureCanWrite(p) || !level.getBlockState(p).isAir()) return false;
		BlockPos below = p.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}
}
