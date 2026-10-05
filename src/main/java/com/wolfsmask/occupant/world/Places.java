package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The other places somebody used to be: a ruined keep, an abandoned camp, a small graveyard, and
 * the cottages and the well of the village the house sometimes stands in. They have nothing to do
 * with the story's rules and keep turning up as the world is explored, well apart, so the world
 * feels lived in, once, by people who are not here any more.
 */
final class Places {
	/** Not right where players first appear. */
	private static final int MIN_FROM_SPAWN = 120;
	/** And well apart from each other. */
	private static final int MIN_APART = 200;

	private static final List<BlockPos> PLACES = new CopyOnWriteArrayList<>();
	@Nullable
	private static volatile Path record;

	private Places() {
	}

	static void open(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("occupant_places.txt");
		record = file;
		PLACES.clear();
		if (!Files.exists(file)) return;
		try {
			for (String line : Files.readAllLines(file)) {
				String[] p = line.trim().split("\\s+");
				if (p.length == 3) PLACES.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			}
		} catch (IOException | RuntimeException e) {
			Occupant.LOGGER.warn("Could not read where the old places are", e);
		}
	}

	static void close() {
		record = null;
		PLACES.clear();
	}

	private static synchronized boolean claim(BlockPos at) {
		for (BlockPos p : PLACES) {
			long dx = p.getX() - at.getX();
			long dz = p.getZ() - at.getZ();
			if (dx * dx + dz * dz < (long) MIN_APART * MIN_APART) return false;
		}
		PLACES.add(at);
		Path file = record;
		if (file != null) {
			StringBuilder out = new StringBuilder();
			for (BlockPos p : PLACES) out.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append('\n');
			try {
				Files.writeString(file, out.toString());
			} catch (IOException e) {
				Occupant.LOGGER.warn("Could not record where the old places are", e);
			}
		}
		return true;
	}

	/** World generation's chance at a ruin, a camp or a graveyard around {@code centre}. */
	static boolean tryPlace(WorldGenLevel level, RandomSource random, BlockPos centre, double fromSpawn) {
		if (fromSpawn < MIN_FROM_SPAWN) return false;
		float roll = random.nextFloat();
		int half = roll < 0.35f ? 7 : roll < 0.75f ? 5 : 6;
		BlockPos base = flatGround(level, centre, half, 3);
		if (base == null || !level.ensureCanWrite(base)) return false;
		if (!claim(base)) return false;
		Rotation rotation = Rotation.getRandom(random);
		Build build = roll < 0.35f ? new Ruin(level, base, rotation, random)
				: roll < 0.75f ? new Camp(level, base, rotation, random)
				: new Graves(level, base, rotation, random);
		build.build();
		return true;
	}

	/** Around a house: a couple of cottages, a well, and the paths between them. */
	static void village(WorldGenLevel level, RandomSource random, BlockPos house, Rotation rotation) {
		// Spots around the house, in its own frame: to its left, behind it to the right, and in front.
		int[][] spots = {{-15, -3}, {15, 6}, {-2, -13}, {13, -12}};
		Rotation[] turns = {Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90, Rotation.NONE, Rotation.CLOCKWISE_180};
		for (int i = 0; i < spots.length; i++) {
			BlockPos column = house.offset(new BlockPos(spots[i][0], 0, spots[i][1]).rotate(rotation));
			boolean well = i == 2;
			BlockPos base = flatGround(level, column, well ? 2 : 4, 2);
			if (base == null || Math.abs(base.getY() - house.getY()) > 5) continue;
			if (i == 3 && random.nextFloat() < 0.5f) continue;              // not every village is the same size
			Rotation turned = rotation.getRotated(turns[i]);
			Build build = well ? new Well(level, base, turned, random) : new Cottage(level, base, turned, random);
			build.build();
			path(level, random, house, base);
		}
	}

	/** A worn path of trodden earth from one place to another, over whatever grass is between. */
	private static void path(WorldGenLevel level, RandomSource random, BlockPos from, BlockPos to) {
		int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
		for (int s = 0; s <= steps; s++) {
			if (random.nextFloat() < 0.15f) continue;                      // worn through in places
			int x = from.getX() + (to.getX() - from.getX()) * s / Math.max(1, steps);
			int z = from.getZ() + (to.getZ() - from.getZ()) * s / Math.max(1, steps);
			BlockPos top = new BlockPos(x, surface(level, x, z) - 1, z);
			if (!level.ensureCanWrite(top)) continue;
			BlockState state = level.getBlockState(top);
			if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)) {
				level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), 2);
			}
		}
	}

	/** The first open y above the ground at a column (not counting leaves or water). */
	static int surface(WorldGenLevel level, int x, int z) {
		Heightmap.Types type = level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG;
		return level.getHeight(type, x, z);
	}

	/**
	 * The middle of a square of ground {@code half} blocks each way from {@code centre}, at the
	 * height of its highest corner, if the ground there is dry and rises or falls by no more than
	 * {@code slope}; otherwise null.
	 */
	@Nullable
	static BlockPos flatGround(WorldGenLevel level, BlockPos centre, int half, int slope) {
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int[] c : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}, {0, 0}}) {
			int x = centre.getX() + c[0];
			int z = centre.getZ() + c[1];
			int top = surface(level, x, z);
			BlockState ground = level.getBlockState(new BlockPos(x, top - 1, z));
			BlockState above = level.getBlockState(new BlockPos(x, top, z));
			if (!ground.getFluidState().isEmpty() || !above.getFluidState().isEmpty() || ground.is(Blocks.ICE)) return null;
			lowest = Math.min(lowest, top);
			highest = Math.max(highest, top);
		}
		if (highest - lowest > slope) return null;
		return new BlockPos(centre.getX(), highest - 1, centre.getZ());
	}

	/** For the game tests: one build of the named kind, with its middle at {@code base}. */
	static void buildForTest(String kind, WorldGenLevel level, BlockPos base, RandomSource random) {
		Build build = switch (kind) {
			case "ruin" -> new Ruin(level, base, Rotation.NONE, random);
			case "camp" -> new Camp(level, base, Rotation.NONE, random);
			case "graves" -> new Graves(level, base, Rotation.NONE, random);
			case "well" -> new Well(level, base, Rotation.NONE, random);
			default -> new Cottage(level, base, Rotation.NONE, random);
		};
		build.build();
	}
}
