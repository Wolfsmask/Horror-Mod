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
	private static final int MIN_APART = 260;
	/** And never the same kind of place twice in a walk: two camps are this far apart at least. */
	private static final int SAME_KIND_APART = 600;
	/** Never this close to a house, whose village reaches twenty-odd blocks round it, or a house to them. */
	static final int CLEAR_OF_OTHERS = 80;

	/** The kinds of place, how often each comes up, and how far each reaches from its middle. */
	private static final String[] KINDS = {"lair", "lighthouse", "ruin", "camp", "graves", "watchtower", "chapel", "radio"};
	private static final float[] SHARES = {0.06f, 0.07f, 0.14f, 0.19f, 0.14f, 0.14f, 0.14f, 0.12f};

	private static final List<BlockPos> PLACES = new CopyOnWriteArrayList<>();
	/** Which kind each place is, where known (worlds from before this was kept have none). */
	private static final java.util.Map<BlockPos, String> KIND_AT = new java.util.concurrent.ConcurrentHashMap<>();
	@Nullable
	private static volatile Path record;

	private Places() {
	}

	static void open(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("occupant_places.txt");
		record = file;
		PLACES.clear();
		KIND_AT.clear();
		if (!Files.exists(file)) return;
		try {
			for (String line : Files.readAllLines(file)) {
				String[] p = line.trim().split("\\s+");
				if (p.length < 3) continue;
				BlockPos at = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
				PLACES.add(at);
				if (p.length >= 4) KIND_AT.put(at, p[3]);
			}
		} catch (IOException | RuntimeException e) {
			Occupant.LOGGER.warn("Could not read where the old places are", e);
		}
	}

	static void close() {
		record = null;
		PLACES.clear();
		KIND_AT.clear();
		SIGNS.clear();
	}

	/**
	 * Words for signs placed while the world was being generated. A sign cannot be written then
	 * (there is no world yet for it to tell), so it is put up blank and written here, on the
	 * server's own thread, as soon as its chunk is properly loaded.
	 */
	private static final java.util.Map<BlockPos, String[]> SIGNS = new java.util.concurrent.ConcurrentHashMap<>();

	static void sign(WorldGenLevel level, BlockPos pos, String[] lines) {
		if (level instanceof ServerLevel live) {
			write(live, pos, lines);
		} else {
			SIGNS.put(pos.immutable(), lines);
		}
	}

	/** Called every second: writes any waiting signs whose chunks are now loaded. */
	public static void tick(MinecraftServer server) {
		if (SIGNS.isEmpty()) return;
		ServerLevel level = server.overworld();
		SIGNS.entrySet().removeIf(e -> {
			BlockPos pos = e.getKey();
			if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
			write(level, pos, e.getValue());
			return true;
		});
	}

	private static void write(ServerLevel level, BlockPos pos, String[] lines) {
		if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			com.wolfsmask.occupant.compat.Compat.writeSign(sign, lines);
		}
	}

	/**
	 * A new lair, dug while the world runs, 60 to 100 blocks from {@code home}, somewhere loaded,
	 * flat and solid underneath: it is moving closer. False if nowhere suited.
	 */
	static boolean lairNear(ServerLevel level, BlockPos home, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int r = 60 + random.nextInt(40);
			int x = home.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = home.getZ() + (int) Math.round(Math.sin(angle) * r);
			boolean loaded = true;
			for (int dx = -1; dx <= 1 && loaded; dx++) {
				for (int dz = -1; dz <= 1 && loaded; dz++) loaded = level.hasChunk((x >> 4) + dx, (z >> 4) + dz);
			}
			if (!loaded) continue;
			BlockPos base = flatGround(level, new BlockPos(x, home.getY(), z), 4, 2);
			if (base == null || !solidBelow(level, base, Lair.DEPTH + 4)) continue;
			// Never into anything anyone made: a farm, a path, a house out here, a village.
			if (looksBuilt(level, base, 10)) continue;
			Lair lair = new Lair(level, base, Rotation.getRandom(random), random);
			lair.build();
			Lairs.add(lair.hollow());
			return true;
		}
		return false;
	}

	/**
	 * Whether anything made stands within {@code r} blocks of {@code at}: planks, doors, beds,
	 * glass, torches, chests, tilled earth and the like. Grown and natural things do not count.
	 */
	static boolean looksBuilt(ServerLevel level, BlockPos at, int r) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -4, -r), at.offset(r, 8, r))) {
			BlockState s = level.getBlockState(p);
			if (s.isAir()) continue;
			if (s.is(net.minecraft.tags.BlockTags.PLANKS) || s.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS)
					|| s.is(net.minecraft.tags.BlockTags.WOODEN_SLABS) || s.is(net.minecraft.tags.BlockTags.DOORS)
					|| s.is(net.minecraft.tags.BlockTags.BEDS) || s.is(net.minecraft.tags.BlockTags.WOOL)
					|| s.is(net.minecraft.tags.BlockTags.RAILS) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)
					|| s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.CRAFTING_TABLE)
					|| s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.FURNACE) || s.is(Blocks.FARMLAND)
					|| s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.DIRT_PATH)) {
				return true;
			}
		}
		return false;
	}

	/** Is there an old place within {@code r} blocks of {@code at}? */
	static boolean near(BlockPos at, int r) {
		for (BlockPos p : PLACES) {
			long dx = p.getX() - at.getX();
			long dz = p.getZ() - at.getZ();
			if (dx * dx + dz * dz < (long) r * r) return true;
		}
		return false;
	}

	private static synchronized boolean claim(BlockPos at, String kind) {
		for (BlockPos p : PLACES) {
			long dx = p.getX() - at.getX();
			long dz = p.getZ() - at.getZ();
			long apart = kind.equals(KIND_AT.get(p)) ? SAME_KIND_APART : MIN_APART;
			if (dx * dx + dz * dz < apart * apart) return false;
		}
		PLACES.add(at);
		KIND_AT.put(at, kind);
		Path file = record;
		if (file != null) {
			StringBuilder out = new StringBuilder();
			for (BlockPos p : PLACES) {
				out.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
				String k = KIND_AT.get(p);
				if (k != null) out.append(' ').append(k);
				out.append('\n');
			}
			try {
				Files.writeString(file, out.toString());
			} catch (IOException e) {
				Occupant.LOGGER.warn("Could not record where the old places are", e);
			}
		}
		return true;
	}

	/** Which kind of place to try, by its share. */
	private static int roll(RandomSource random) {
		float r = random.nextFloat() * 0.999f;
		for (int i = 0; i < SHARES.length; i++) {
			r -= SHARES[i];
			if (r < 0) return i;
		}
		return 3;
	}

	/** Is there a place of this kind too near {@code at} already? */
	private static boolean sameKindNear(String kind, BlockPos at) {
		for (java.util.Map.Entry<BlockPos, String> e : KIND_AT.entrySet()) {
			if (!e.getValue().equals(kind)) continue;
			long dx = e.getKey().getX() - at.getX();
			long dz = e.getKey().getZ() - at.getZ();
			if (dx * dx + dz * dz < (long) SAME_KIND_APART * SAME_KIND_APART) return true;
		}
		return false;
	}

	/** World generation's chance at one of the old places around {@code centre}. */
	static boolean tryPlace(WorldGenLevel level, RandomSource random, BlockPos centre, double fromSpawn) {
		if (fromSpawn < MIN_FROM_SPAWN) return false;
		// Most chunks are near a place already: cheap to tell, before looking at the ground.
		if (near(centre, MIN_APART) || House.near(centre, CLEAR_OF_OTHERS)) return false;
		// Fairly flat ground for the widest of them, so any kind can go here.
		BlockPos base = flatGround(level, centre, 7, 3);
		if (base == null || !level.ensureCanWrite(base)) return false;
		// A kind that suits the ground, and is not one passed a little way back.
		int kind = -1;
		for (int tries = 0; tries < 5 && kind < 0; tries++) {
			int k = roll(random);
			boolean suits = switch (k) {
				case 0 -> solidBelow(level, base, Lair.DEPTH + 4);
				case 1 -> nearWater(level, base);
				default -> true;
			};
			if (suits && !sameKindNear(KINDS[k], base)) kind = k;
		}
		if (kind < 0 || !claim(base, KINDS[kind])) return false;
		Rotation rotation = Rotation.getRandom(random);
		Build build;
		switch (kind) {
			case 0 -> {
				Lair lair = new Lair(level, base, rotation, random);
				lair.build();
				Lairs.add(lair.hollow());
				return true;
			}
			case 1 -> build = new Lighthouse(level, base, rotation, random);
			case 2 -> build = new Ruin(level, base, rotation, random);
			case 3 -> build = new Camp(level, base, rotation, random);
			case 4 -> build = new Graves(level, base, rotation, random);
			case 5 -> build = new Watchtower(level, base, rotation, random);
			case 6 -> build = new Chapel(level, base, rotation, random);
			default -> build = new RadioShack(level, base, rotation, random);
		}
		build.palette = Palette.pick(level, base, random);
		build.build();
		return true;
	}

	/** Around a house: a couple of cottages, a well, and the paths between them. */
	static void village(WorldGenLevel level, RandomSource random, BlockPos house, Rotation rotation, Palette palette) {
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
			build.palette = palette;
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

	/** Solid stone all the way down this far: somewhere a hollow can be dug. */
	private static boolean solidBelow(WorldGenLevel level, BlockPos base, int depth) {
		for (int d = 6; d <= depth; d += 4) {
			BlockState s = level.getBlockState(base.below(d));
			if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	/** Water within a few blocks of the ground here: the shore. */
	private static boolean nearWater(WorldGenLevel level, BlockPos base) {
		for (int[] c : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
			int x = base.getX() + c[0], z = base.getZ() + c[1];
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
			if (!level.getFluidState(top).isEmpty()) return true;
		}
		return false;
	}

	/** For the game tests: one build of the named kind, with its middle at {@code base}. */
	static void buildForTest(String kind, WorldGenLevel level, BlockPos base, RandomSource random) {
		if (kind.equals("lair")) {
			Lair lair = new Lair(level, base, Rotation.NONE, random);
			lair.build();
			Lairs.add(lair.hollow());
			return;
		}
		Build build = switch (kind) {
			case "watchtower" -> new Watchtower(level, base, Rotation.NONE, random);
			case "chapel" -> new Chapel(level, base, Rotation.NONE, random);
			case "radio" -> new RadioShack(level, base, Rotation.NONE, random);
			case "lighthouse" -> new Lighthouse(level, base, Rotation.NONE, random);
			case "ruin" -> new Ruin(level, base, Rotation.NONE, random);
			case "camp" -> new Camp(level, base, Rotation.NONE, random);
			case "graves" -> new Graves(level, base, Rotation.NONE, random);
			case "well" -> new Well(level, base, Rotation.NONE, random);
			default -> new Cottage(level, base, Rotation.NONE, random);
		};
		build.palette = Palette.pick(level, base, random);
		build.build();
	}
}
