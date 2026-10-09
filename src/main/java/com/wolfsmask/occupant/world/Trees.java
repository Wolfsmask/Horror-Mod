package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The trees a build went through, taken down whole. A place is cleared for before it is built,
 * and whatever was standing there is cut where the clearing ends: half a canopy hanging in the air
 * over nothing, a trunk with no top, a top with no trunk. Instead, every tree it touched comes down
 * entirely (trunk, branches, a hive on it, a huge mushroom), and then whatever leaves are left with
 * nothing holding them up go too, as they would have if the tree had been cut down by hand.
 */
final class Trees {
	/** At most this much of the woods is taken down for one build, however thick they are. */
	private static final int MOST = 2500;
	/** One tree, trunk and branches, is never wider than this each way from where it was found. */
	private static final int REACH = 12;
	/** Leaves this many steps from the nearest log, through other leaves, still hang on (as in the game). */
	private static final int HOLD = 6;
	/** Every block touching another, by a face, an edge or a corner. */
	private static final int[][] AROUND;

	static {
		List<int[]> around = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++)
				for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dy != 0 || dz != 0) around.add(new int[]{dx, dy, dz});
		AROUND = around.toArray(new int[0][]);
	}

	private final Build build;
	private final WorldGenLevel level;
	private final Set<Long> written;
	private int taken;
	/** Round everything taken or cut: where leaves may have been left hanging. */
	private int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
	private int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;

	Trees(Build build, Set<Long> written) {
		this.build = build;
		this.level = build.level;
		this.written = written;
	}

	/** Leaves that grew there (not ones somebody placed, which stay where they are put). */
	static boolean leaf(BlockState s) {
		return s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.PERSISTENT) && !s.getValue(BlockStateProperties.PERSISTENT)
				&& s.hasProperty(BlockStateProperties.DISTANCE);
	}

	/** Part of a huge mushroom. */
	static boolean mushroom(BlockState s) {
		return s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK);
	}

	/**
	 * Something made, that a tree is never felled into: planks, stairs, slabs, fences, doors,
	 * glass, laid stone. A log touching one is a beam in somebody's wall, not a branch.
	 */
	private static boolean made(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.FENCES)
				|| s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS)
				|| s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
				|| s.is(Blocks.BRICKS) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.CRAFTING_TABLE)
				|| s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN);
	}

	private boolean log(BlockPos p, BlockState s) {
		return s.is(BlockTags.LOGS) && !written.contains(p.asLong());
	}

	/** Takes down every tree the build went through, and the leaves they leave hanging. */
	void fell(Map<Long, Integer> cutLeaves, Set<Long> cutLogs, Set<Long> cutMushrooms) {
		for (long c : cutLeaves.keySet()) grow(c);
		for (long c : cutLogs) grow(c);
		for (long c : cutMushrooms) grow(c);

		Set<Long> seeds = new LinkedHashSet<>();
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		// What is left of a trunk or a branch it was built through.
		for (long c : cutLogs) {
			for (int[] d : AROUND) {
				n.set(BlockPos.getX(c) + d[0], BlockPos.getY(c) + d[1], BlockPos.getZ(c) + d[2]);
				if (build.reachable(n) && log(n, level.getBlockState(n))) seeds.add(n.asLong());
			}
		}
		// The trunk each of the leaves it cut hung from.
		for (Map.Entry<Long, Integer> e : cutLeaves.entrySet()) {
			Long trunk = trunkOf(e.getKey(), e.getValue());
			if (trunk != null) seeds.add(trunk);
		}
		Set<Long> gone = new HashSet<>();
		Set<Long> refused = new HashSet<>();
		for (long seed : seeds) {
			if (taken >= MOST || gone.contains(seed) || refused.contains(seed)) continue;
			List<Long> tree = tree(seed, refused);
			if (tree == null) continue;
			for (long l : tree) {
				take(BlockPos.of(l));
				gone.add(l);
			}
		}
		for (long c : cutMushrooms) mushroom(BlockPos.of(c));
		decay();
	}

	private void grow(long c) {
		int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
		x0 = Math.min(x0, x);
		y0 = Math.min(y0, y);
		z0 = Math.min(z0, z);
		x1 = Math.max(x1, x);
		y1 = Math.max(y1, y);
		z1 = Math.max(z1, z);
	}

	private void take(BlockPos p) {
		if (!level.ensureCanWrite(p)) return;
		level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		taken++;
		grow(p.asLong());
		// A hive on the trunk comes down with it.
		for (Direction d : Direction.values()) {
			BlockPos side = p.relative(d);
			if (build.reachable(side) && level.getBlockState(side).is(Blocks.BEE_NEST) && level.ensureCanWrite(side)) {
				level.setBlock(side, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * The log a leaf hung from: from leaf to leaf, each nearer a log than the last (every leaf
	 * knows how far it is from one), until the next is the log. Null if the way there is gone.
	 */
	private Long trunkOf(long start, int distance) {
		BlockPos at = BlockPos.of(start);
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int step = 0; step <= HOLD + 1; step++) {
			BlockPos next = null;
			int best = distance;
			for (Direction d : Direction.values()) {
				n.setWithOffset(at, d);
				if (!build.reachable(n)) continue;
				BlockState s = level.getBlockState(n);
				if (log(n, s)) return n.asLong();
				if (leaf(s) && s.getValue(BlockStateProperties.DISTANCE) < best) {
					best = s.getValue(BlockStateProperties.DISTANCE);
					next = n.immutable();
				}
			}
			if (next == null) return null;
			at = next;
			distance = best;
		}
		return null;
	}

	/**
	 * The whole of the tree a log is part of: every log joined to it, near enough to be the same
	 * tree. Null if it is part of something made instead, a beam in a wall or a post in a fence.
	 */
	private List<Long> tree(long seed, Set<Long> refused) {
		List<Long> tree = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<Long> open = new ArrayDeque<>();
		open.add(seed);
		seen.add(seed);
		int sx = BlockPos.getX(seed), sy = BlockPos.getY(seed), sz = BlockPos.getZ(seed);
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		while (!open.isEmpty()) {
			long c = open.poll();
			BlockPos p = BlockPos.of(c);
			for (Direction d : Direction.values()) {
				n.setWithOffset(p, d);
				if (!build.reachable(n) || written.contains(n.asLong())) continue;
				if (made(level.getBlockState(n))) {
					refused.addAll(seen);
					return null;
				}
			}
			tree.add(c);
			if (tree.size() > 400) {                             // no tree is this big: something else
				refused.addAll(seen);
				return null;
			}
			for (int[] d : AROUND) {
				n.set(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]);
				if (Math.abs(n.getX() - sx) > REACH || Math.abs(n.getZ() - sz) > REACH || Math.abs(n.getY() - sy) > REACH * 3) continue;
				long k = n.asLong();
				if (seen.contains(k) || !build.reachable(n)) continue;
				if (!log(n, level.getBlockState(n))) continue;
				seen.add(k);
				open.add(k);
			}
		}
		return tree;
	}

	/** A huge mushroom it was built through: the rest of it, stem and cap. */
	private void mushroom(BlockPos cut) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(cut);
		seen.add(cut.asLong());
		int count = 0;
		while (!open.isEmpty() && count < 300 && taken < MOST) {
			BlockPos p = open.poll();
			for (int[] d : AROUND) {
				BlockPos n = p.offset(d[0], d[1], d[2]);
				if (!seen.add(n.asLong()) || !build.reachable(n) || written.contains(n.asLong())) continue;
				if (!mushroom(level.getBlockState(n))) continue;
				take(n);
				count++;
				open.add(n);
			}
		}
	}

	/**
	 * Leaves left with nothing holding them: none of the logs left near enough to reach through
	 * other leaves. Those go, as they would have rotted away if the tree had been cut by hand.
	 */
	private void decay() {
		if (x0 > x1) return;
		// Where leaves may have been left hanging, and round that, as far as a log could hold them from.
		int ix0 = x0 - HOLD - 1, iy0 = y0 - HOLD - 1, iz0 = z0 - HOLD - 1;
		int ix1 = x1 + HOLD + 1, iy1 = y1 + HOLD + 1, iz1 = z1 + HOLD + 1;
		int ox0 = ix0 - HOLD, oy0 = iy0 - HOLD, oz0 = iz0 - HOLD;
		int sx = ix1 + HOLD - ox0 + 1, sy = iy1 + HOLD - oy0 + 1, sz = iz1 + HOLD - oz0 + 1;
		if ((long) sx * sy * sz > 800_000L) return;           // far too much to look at: leave it be
		int size = sx * sy * sz;
		// 0: nothing to do with it; 1: leaves; 2: holds leaves up (a log, or out of reach and so left alone).
		byte[] kind = new byte[size];
		byte[] far = new byte[size];
		int[] queue = new int[size];
		int head = 0, tail = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				for (int y = 0; y < sy; y++) {
					int i = (x * sz + z) * sy + y;
					far[i] = Byte.MAX_VALUE;
					p.set(ox0 + x, oy0 + y, oz0 + z);
					if (!build.reachable(p)) {
						kind[i] = 2;
					} else {
						BlockState s = level.getBlockState(p);
						if (s.isAir()) continue;
						if (s.is(BlockTags.LOGS)) kind[i] = 2;
						else if (leaf(s)) kind[i] = 1;
					}
					if (kind[i] == 2) {
						far[i] = 0;
						queue[tail++] = i;
					}
				}
			}
		}
		// How far each leaf is from something holding it up, through other leaves.
		while (head < tail) {
			int i = queue[head++];
			if (far[i] >= HOLD) continue;
			int y = i % sy, z = (i / sy) % sz, x = i / (sy * sz);
			for (Direction d : Direction.values()) {
				int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
				int j = (nx * sz + nz) * sy + ny;
				if (kind[j] != 1 || far[j] <= far[i] + 1) continue;
				far[j] = (byte) (far[i] + 1);
				queue[tail++] = j;
			}
		}
		for (int x = ix0 - ox0; x <= ix1 - ox0; x++) {
			for (int z = iz0 - oz0; z <= iz1 - oz0; z++) {
				for (int y = iy0 - oy0; y <= iy1 - oy0; y++) {
					int i = (x * sz + z) * sy + y;
					if (kind[i] != 1 || far[i] <= HOLD) continue;
					p.set(ox0 + x, oy0 + y, oz0 + z);
					if (level.ensureCanWrite(p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
