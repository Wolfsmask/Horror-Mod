package com.wolfsmask.occupant.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A small abandoned house that turns up on its own in woods and fields, until somebody finds one. Nobody lives in it. There
 * is a main room you walk straight into, with an old barrel and a table and a little grey light from
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
 *   8  # B . . . . . . # .  .  #    B barrel, S shelves, T table, c chair
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
public final class House {
	static final int WIDTH = 12;
	static final int DEPTH = 11;
	/** The local origin is moved to the middle, so a house never reaches more than a chunk away. */
	private static final int CX = 6;
	private static final int CZ = 5;

	/** It stands at least this far from where players first appear, so it is found, not given. */
	private static final int MIN_FROM_SPAWN = 240;
	/** And this far from any other, while there are still more than one. */
	private static final int MIN_APART = 320;
	/** Coming this close to any house (one chunk) is finding it. */
	private static final int FOUND_WITHIN = 16;

	/**
	 * Houses turn up until somebody finds one: once any player has come within a chunk of any of
	 * them, no new one is ever built in that world. Until the world has finished opening this is
	 * also shut, so nothing is built before anyone knows where players will appear. A small file in
	 * the world folder remembers the houses and whether one has been found, across restarts.
	 */
	private static final AtomicBoolean CLOSED = new AtomicBoolean(true);
	private static final List<BlockPos> HOUSES = new CopyOnWriteArrayList<>();
	private static volatile boolean found = true;
	@Nullable
	private static volatile Path record;
	@Nullable
	private static volatile BlockPos spawn;

	private House() {
	}

	/** Called as a world is opened, before any of it is generated. Nothing is built yet. */
	public static void open(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("occupant_house.txt");
		record = file;
		HOUSES.clear();
		found = false;
		CLOSED.set(true);
		Places.open(server);
		if (Files.exists(file)) {
			try {
				List<String> lines = Files.readAllLines(file);
				boolean anyHouse = false;
				boolean oldFormat = false;
				for (String line : lines) {
					String[] p = line.trim().split("\\s+");
					if (p.length == 1 && p[0].equals("found")) found = true;
					if (p.length == 4 && p[0].equals("house")) {
						HOUSES.add(new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
						anyHouse = true;
					}
					if (p.length == 3) {   // written by 0.13 and earlier: one house, and that was that
						HOUSES.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
						oldFormat = true;
					}
				}
				if (oldFormat && !anyHouse) found = true;
			} catch (IOException | RuntimeException e) {
				com.wolfsmask.occupant.Occupant.LOGGER.warn("Could not read where the houses are; building no more", e);
				found = true;
			}
		}
	}

	/** Called once the world is open and its spawn is settled: from now on, houses may be built. */
	public static void started(MinecraftServer server) {
		spawn = com.wolfsmask.occupant.compat.Compat.spawnPos(server.overworld());
		CLOSED.set(found);
	}

	/** Called as a world is closed: nothing may build a house with no world to record it in. */
	public static void close() {
		CLOSED.set(true);
		Places.close();
		record = null;
		spawn = null;
		HOUSES.clear();
	}

	/** Whether this world will build no more houses: one has been found, or no world is open. */
	public static boolean closed() {
		return CLOSED.get();
	}

	/** Every house built in this world so far. */
	public static List<BlockPos> houses() {
		return List.copyOf(HOUSES);
	}

	/** Called every second by the server: finishes what world generation could not. */
	public static void tick(MinecraftServer server) {
		Places.tick(server);
	}

	/** Has anyone found a house in this world yet? */
	public static boolean found() {
		return found;
	}

	/**
	 * Checks whether {@code player} has just come within a chunk of a house. The first time anyone
	 * does, the world stops building them. Cheap: a handful of distance checks.
	 */
	public static void noticeNear(BlockPos feet) {
		if (found) return;
		for (BlockPos h : HOUSES) {
			long dx = h.getX() - feet.getX();
			long dz = h.getZ() - feet.getZ();
			if (dx * dx + dz * dz <= (long) (FOUND_WITHIN + CX) * (FOUND_WITHIN + CX)) {
				markFound();
				return;
			}
		}
	}

	/** No more houses in this world, from now on. */
	public static synchronized void markFound() {
		if (found) return;
		found = true;
		CLOSED.set(true);
		save();
	}

	private static synchronized void save() {
		Path file = record;
		if (file == null) return;
		StringBuilder out = new StringBuilder();
		for (BlockPos h : HOUSES) out.append("house ").append(h.getX()).append(' ').append(h.getY()).append(' ').append(h.getZ()).append('\n');
		if (found) out.append("found\n");
		try {
			Files.writeString(file, out.toString());
		} catch (IOException e) {
			// Worst case it is forgotten on a restart and another may appear far away; never fatal.
			com.wolfsmask.occupant.Occupant.LOGGER.warn("Could not record where the houses are", e);
		}
	}

	/** Claims a place for a new house, unless one is too close or the world has stopped building them. */
	private static synchronized boolean claim(BlockPos base) {
		if (CLOSED.get()) return false;
		for (BlockPos h : HOUSES) {
			long dx = h.getX() - base.getX();
			long dz = h.getZ() - base.getZ();
			if (dx * dx + dz * dz < (long) MIN_APART * MIN_APART) return false;
		}
		HOUSES.add(base);
		save();
		return true;
	}

	/** Is the player standing on the floor of one of these houses? */
	public static boolean isInside(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet.below(2)).is(Blocks.STRUCTURE_VOID)
				&& level.getBlockState(feet.below()).is(Blocks.DARK_OAK_PLANKS);
	}

	/** Of the chunks world generation offers (one in four), how many get a build: one in six, so one in 24 overall. */
	private static final int BUILD_ONE_IN = 6;

	/**
	 * World generation's chance at a place around {@code origin}'s chunk. While no house has been
	 * found, often a house, and then often a village round it; otherwise a ruin, a camp or a
	 * graveyard, which keep turning up however much of the story has passed. Each Minecraft
	 * version's own feature class calls this.
	 */
	public static boolean tryPlace(WorldGenLevel level, RandomSource random, BlockPos origin) {
		// The land itself, a little wrong, in one chunk in four (placed_feature/house.json): dead
		// trees, fallen trunks, bare ground.
		Blight.tryPlace(level, random, origin);
		// Something built, in one chunk in every BUILD_ONE_IN of those.
		if (random.nextInt(BUILD_ONE_IN) != 0) return false;
		BlockPos from = spawn;
		if (from == null) return false;                       // the world is not open yet
		// The middle of the chunk: from there a build can reach furthest in every direction.
		BlockPos centre = new BlockPos((origin.getX() & ~15) + 8, origin.getY(), (origin.getZ() & ~15) + 8);
		double fromSpawn = Math.sqrt(Math.pow(centre.getX() - from.getX(), 2) + Math.pow(centre.getZ() - from.getZ(), 2));
		if (!CLOSED.get() && random.nextFloat() < 0.6f && tryPlaceHouse(level, random, centre)) return true;
		return Places.tryPlace(level, random, centre, fromSpawn);
	}

	/**
	 * A house at {@code origin}, and sometimes its village. Refuses unless the ground is fairly
	 * flat and dry, it is far enough from spawn and from any other house, and nobody has found a
	 * house in this world yet.
	 */
	public static boolean tryPlaceHouse(WorldGenLevel level, RandomSource random, BlockPos origin) {
		if (CLOSED.get()) return false;                      // the usual answer, before any of the work
		BlockPos from = spawn;
		if (from == null) return false;
		long sx = origin.getX() - from.getX();
		long sz = origin.getZ() - from.getZ();
		if (sx * sx + sz * sz < (long) MIN_FROM_SPAWN * MIN_FROM_SPAWN) return false;
		Rotation rotation = Rotation.getRandom(random);

		// Somewhere fairly flat and dry, or nowhere at all.
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int[] c : new int[][]{{0, 0}, {WIDTH - 1, 0}, {0, DEPTH - 1}, {WIDTH - 1, DEPTH - 1}, {CX, CZ}, {4, -2}}) {
			BlockPos column = origin.offset(new BlockPos(c[0] - CX, 0, c[1] - CZ).rotate(rotation));
			int top = Places.surface(level, column.getX(), column.getZ());
			BlockState ground = level.getBlockState(new BlockPos(column.getX(), top - 1, column.getZ()));
			BlockState above = level.getBlockState(new BlockPos(column.getX(), top, column.getZ()));
			if (!ground.getFluidState().isEmpty() || !above.getFluidState().isEmpty() || ground.is(Blocks.ICE)) return false;
			lowest = Math.min(lowest, top);
			highest = Math.max(highest, top);
		}
		if (highest - lowest > 3) return false;
		BlockPos base = new BlockPos(origin.getX(), highest - 1, origin.getZ());   // the floor
		if (!level.ensureCanWrite(base)) return false;
		// World generation runs on several threads at once: whoever claims the spot builds there.
		if (!claim(base)) return false;

		build(level, base, rotation, random);
		// Now and then it was not alone: the rest of a village stands round it, as empty as it is.
		if (random.nextFloat() < 0.55f) Places.village(level, random, base, rotation);
		return true;
	}

	/**
	 * Builds a house with its floor at {@code floor} (the middle of it), turned by {@code rotation}.
	 * Unrotated, the front door is at {@code floor + (-2, 1, -5)} and the house runs towards +z.
	 */
	public static void build(WorldGenLevel level, BlockPos floor, Rotation rotation, RandomSource random) {
		new Builder(level, floor, rotation, random).build();
	}

	/** For the game tests: one chance at the blight (dead trees, bare ground) in the chunk at {@code origin}. */
	public static void blightForTest(WorldGenLevel level, RandomSource random, BlockPos origin) {
		Blight.tryPlace(level, random, origin);
	}

	/** For the game tests: a house, a village, a ruin, camp, graves, well or cottage, its middle at {@code base}. */
	public static void buildPlaceForTest(String kind, WorldGenLevel level, BlockPos base, RandomSource random) {
		if (kind.equals("village") || kind.equals("house")) {
			build(level, base, Rotation.NONE, random);
			if (kind.equals("village")) Places.village(level, random, base, Rotation.NONE);
			return;
		}
		Places.buildForTest(kind, level, base, random);
	}

	/** Where, in the world, a spot in the house's own coordinates is. */
	public static BlockPos local(BlockPos floor, Rotation rotation, int x, int y, int z) {
		return floor.offset(new BlockPos(x - CX, y, z - CZ).rotate(rotation));
	}

	/** Places blocks in the house's own coordinates: x across the front, z from front to back. */
	private static final class Builder extends Build {
		Builder(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
			super(level, base, rotation, random);
		}

		/** The house's own coordinates start at its front left corner, not its middle. */
		@Override
		BlockPos at(int x, int y, int z) {
			return base.offset(new BlockPos(x - CX, y, z - CZ).rotate(rotation));
		}

		@Override
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
			fill(8, 1, 2, 8, 3, 3, air);                 // floor to ceiling, so you can see up into it

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
			container(1, 1, 9, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.HOME);
			put(1, 1, 8, Blocks.SPRUCE_SLAB.defaultBlockState());
			put(3, 1, 6, Blocks.SPRUCE_FENCE.defaultBlockState());
			put(3, 2, 6, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
			put(2, 1, 6, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
			put(5, 1, 9, Blocks.BOOKSHELF.defaultBlockState());
			put(6, 1, 9, Blocks.BOOKSHELF.defaultBlockState());
			put(6, 2, 9, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
			put(7, 1, 1, facing(Blocks.FURNACE.defaultBlockState(), Direction.SOUTH));   // into the room
			// At the far end of the dark hallway, something worth walking down it for.
			container(9, 1, 9, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.HOME);
			BlockState web = Blocks.COBWEB.defaultBlockState();
			put(1, 3, 1, web);
			put(7, 3, 9, web);
			put(1, 3, 9, web);
			put(9, 3, 4, web);
			put(10, 3, 7, web);
			put(10, 1, 9, web);

			// However the ground falls away, there is a way up to the door.
			steps(4, 0, Direction.NORTH, Blocks.COBBLESTONE_STAIRS, cobble);
		}
	}
}
