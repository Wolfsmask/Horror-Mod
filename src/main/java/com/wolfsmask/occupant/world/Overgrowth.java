package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Leaves that grew into a place after it was built. The world is made a piece at a time, and the
 * trees round a place are often grown after it, by a tree beside the wall whose leaves come out
 * inside the room, through the wall, as if it were not there. Each place notes the space under its
 * roof; once everything round it has been made (every chunk within two of it), whatever leaves are
 * in that space are taken out again, before anybody has come near enough to see them.
 */
final class Overgrowth {
	/** One place's space under its roof, and the chunk it stands in. */
	private record Waiting(int chunkX, int chunkZ, long[] inside) {
	}

	private static final Queue<Waiting> WAITING = new ConcurrentLinkedQueue<>();

	private Overgrowth() {
	}

	/** The space under a place's roof at {@code base}, to clear of leaves once it is all grown round. */
	static void later(BlockPos base, List<Long> inside) {
		long[] cells = new long[inside.size()];
		for (int i = 0; i < cells.length; i++) cells[i] = inside.get(i);
		WAITING.add(new Waiting(base.getX() >> 4, base.getZ() >> 4, cells));
	}

	/** Every second: any place whose surroundings are all made now is cleared of what grew into it. */
	static void tick(MinecraftServer server) {
		if (WAITING.isEmpty()) return;
		ServerLevel level = server.overworld();
		WAITING.removeIf(w -> {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) if (!level.hasChunk(w.chunkX() + dx, w.chunkZ() + dz)) return false;
			}
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (long cell : w.inside()) {
				p.set(BlockPos.getX(cell), BlockPos.getY(cell), BlockPos.getZ(cell));
				if (Trees.leaf(level.getBlockState(p))) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			return true;
		});
	}

	static void clear() {
		WAITING.clear();
	}
}
