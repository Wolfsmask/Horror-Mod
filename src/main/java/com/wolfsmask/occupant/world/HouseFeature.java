package com.wolfsmask.occupant.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A small abandoned house that turns up on its own in woods and fields. Nobody lives in it. There
 * is a main room you walk straight into, with a cold bed and a table and a little grey light from
 * two windows, and off to the right, through a gap in the inside wall, a long narrow hallway with
 * no windows at all that runs the length of the house into the dark.
 * <p>
 * That hallway is the point. The first time you step inside, it is standing in it.
 * <p>
 * Under the floor is a single layer of structure void, which is invisible and can never be got
 * at. It is how the mod knows, from where you are standing, that you are in one of these houses.
 *
 * <pre>
 *   x: 0 1 2 3 4 5 6 7 8 9 10 11
 * z=10 # # # # # # # # # # #  #    back
 *   9  # B . . . S S . # .  .  #
 *   8  # B . . . . . . # .  .  #    B bed, S shelves, T table, c chair
 *   7  # . . . . . . . # .  .  #
 *   6  = . c T . . . . # .  .  #    = window
 *   5  # . . . . . . . # .  .  #
 *   4  = . . . . . . . # .  .  #    . . the hallway, x 9-10, no light
 *   3  # . . . . . . . _ .  .  #    _ the gap into it
 *   2  # . . . . . . . _ .  .  #
 *   1  # . . . . . . . # .  .  #
 *   0  # = = # D # # # # # #  #    front, D the doorway
 * </pre>
 */
public final class HouseFeature extends Feature<NoneFeatureConfiguration> {
	static final int WIDTH = 12;
	static final int DEPTH = 11;
	/** The local origin is moved to the middle, so a house never reaches more than a chunk away. */
	private static final int CX = 6;
	private static final int CZ = 5;

	public HouseFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	/** Is the player standing on the floor of one of these houses? */
	public static boolean isInside(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet.below(2)).is(Blocks.STRUCTURE_VOID)
				&& level.getBlockState(feet.below()).is(Blocks.DARK_OAK_PLANKS);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		WorldGenLevel level = ctx.level();
		RandomSource random = ctx.random();
		BlockPos origin = ctx.origin();
		Rotation rotation = Rotation.getRandom(random);

		// Somewhere fairly flat and dry, or nowhere at all.
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int[] c : new int[][]{{0, 0}, {WIDTH - 1, 0}, {0, DEPTH - 1}, {WIDTH - 1, DEPTH - 1}, {CX, CZ}, {4, -2}}) {
			BlockPos column = origin.offset(new BlockPos(c[0] - CX, 0, c[1] - CZ).rotate(rotation));
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.getX(), column.getZ());
			BlockState ground = level.getBlockState(new BlockPos(column.getX(), top - 1, column.getZ()));
			if (!ground.getFluidState().isEmpty() || ground.is(Blocks.ICE)) return false;
			lowest = Math.min(lowest, top);
			highest = Math.max(highest, top);
		}
		if (highest - lowest > 3) return false;
		BlockPos base = new BlockPos(origin.getX(), highest - 1, origin.getZ());   // the floor
		if (!level.ensureCanWrite(base)) return false;

		build(level, base, rotation, random);
		return true;
	}

	/**
	 * Builds a house with its floor at {@code floor} (the middle of it), turned by {@code rotation}.
	 * Unrotated, the front door is at {@code floor + (-2, 1, -5)} and the house runs towards +z.
	 */
	public static void build(WorldGenLevel level, BlockPos floor, Rotation rotation, RandomSource random) {
		new House(level, floor, rotation, random).build();
	}

	/** Where, in the world, a spot in the house's own coordinates is. */
	public static BlockPos local(BlockPos floor, Rotation rotation, int x, int y, int z) {
		return floor.offset(new BlockPos(x - CX, y, z - CZ).rotate(rotation));
	}

	/** Places blocks in the house's own coordinates: x across the front, z from front to back. */
	private static final class House {
		private final WorldGenLevel level;
		private final BlockPos base;
		private final Rotation rotation;
		private final RandomSource random;

		House(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
			this.level = level;
			this.base = base;
			this.rotation = rotation;
			this.random = random;
		}

		void put(int x, int y, int z, BlockState state) {
			BlockPos at = base.offset(new BlockPos(x - CX, y, z - CZ).rotate(rotation));
			level.setBlock(at, state.rotate(rotation), 2);
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = x0; x <= x1; x++)
				for (int y = y0; y <= y1; y++)
					for (int z = z0; z <= z1; z++) put(x, y, z, state);
		}

		BlockState old(BlockState clean, BlockState aged, float chance) {
			return random.nextFloat() < chance ? aged : clean;
		}

		void build() {
			BlockState air = Blocks.AIR.defaultBlockState();
			BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
			BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
			BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
			BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
			BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();

			// Clear the ground it stands in and the air above it.
			fill(-1, 1, -1, WIDTH, 7, DEPTH, air);

			// Foundation, down to whatever is below, and the hidden marker under the floor.
			for (int x = 0; x < WIDTH; x++) {
				for (int z = 0; z < DEPTH; z++) {
					boolean inside = x > 0 && x < WIDTH - 1 && z > 0 && z < DEPTH - 1;
					put(x, 0, z, inside ? floor : old(cobble, mossy, 0.3f));
					put(x, -1, z, inside ? Blocks.STRUCTURE_VOID.defaultBlockState() : old(cobble, mossy, 0.3f));
					for (int y = -2; y >= -8; y--) {
						BlockPos at = base.offset(new BlockPos(x - CX, y, z - CZ).rotate(rotation));
						BlockState there = level.getBlockState(at);
						if (!there.isAir() && there.getFluidState().isEmpty() && !there.canBeReplaced()) break;
						put(x, y, z, old(cobble, mossy, 0.3f));
					}
				}
			}

			// Walls: a stone course at the bottom, boards above, dark posts at the corners.
			for (int x = 0; x < WIDTH; x++) {
				for (int z = 0; z < DEPTH; z++) {
					boolean edge = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
					if (!edge) continue;
					put(x, 1, z, old(cobble, mossy, 0.35f));
					put(x, 2, z, planks);
					put(x, 3, z, planks);
				}
			}
			for (int[] c : new int[][]{{0, 0}, {WIDTH - 1, 0}, {0, DEPTH - 1}, {WIDTH - 1, DEPTH - 1}, {8, 0}, {8, DEPTH - 1}}) {
				fill(c[0], 1, c[1], c[0], 3, c[1], post);
			}

			// The wall between the room and the hallway, with a gap in it near the front.
			fill(8, 1, 1, 8, 3, DEPTH - 2, planks);
			fill(8, 1, 2, 8, 2, 3, air);

			// The doorway, and the windows (the room only; the hallway has none).
			fill(4, 1, 0, 4, 2, 0, air);
			BlockState paneAcross = Blocks.GLASS_PANE.defaultBlockState()
					.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
			BlockState paneAlong = Blocks.GLASS_PANE.defaultBlockState()
					.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
			put(1, 2, 0, paneAcross);
			put(2, 2, 0, paneAcross);
			put(0, 2, 4, paneAlong);
			put(0, 2, 6, paneAlong);

			// Ceiling and a stepped roof.
			fill(0, 4, 0, WIDTH - 1, 4, DEPTH - 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			BlockState eave = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			for (int x = -1; x <= WIDTH; x++) {
				put(x, 3, -1, eave);
				put(x, 3, DEPTH, eave);
			}
			for (int z = 0; z < DEPTH; z++) {
				put(-1, 3, z, eave);
				put(WIDTH, 3, z, eave);
			}
			fill(1, 5, 1, WIDTH - 2, 5, DEPTH - 2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			fill(3, 6, 3, WIDTH - 4, 6, DEPTH - 4,
					Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

			// What was left behind.
			put(1, 1, 8, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
					.setValue(BedBlock.PART, BedPart.FOOT));
			put(1, 1, 9, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
					.setValue(BedBlock.PART, BedPart.HEAD));
			put(3, 1, 6, Blocks.SPRUCE_FENCE.defaultBlockState());
			put(3, 2, 6, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
			put(2, 1, 6, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
			put(5, 1, 9, Blocks.BOOKSHELF.defaultBlockState());
			put(6, 1, 9, Blocks.BOOKSHELF.defaultBlockState());
			put(6, 2, 9, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
			put(7, 1, 1, Blocks.FURNACE.defaultBlockState());
			BlockState web = Blocks.COBWEB.defaultBlockState();
			put(1, 3, 1, web);
			put(7, 3, 9, web);
			put(1, 3, 9, web);
			put(9, 3, 4, web);
			put(10, 3, 7, web);
			put(10, 1, 9, web);
		}
	}
}
