package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Where its lairs are (the middle of each hollow), kept with the world in occupant_lairs.txt. */
public final class Lairs {
	private static final List<BlockPos> HOLLOWS = new CopyOnWriteArrayList<>();
	@Nullable
	private static volatile Path record;

	private Lairs() {
	}

	public static void open(MinecraftServer server) {
		record = server.getWorldPath(LevelResource.ROOT).resolve("occupant_lairs.txt");
		HOLLOWS.clear();
		if (!Files.exists(record)) return;
		try {
			for (String line : Files.readAllLines(record)) {
				String[] p = line.trim().split("\\s+");
				if (p.length == 3) HOLLOWS.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			}
		} catch (IOException | RuntimeException e) {
			Occupant.LOGGER.warn("Could not read where its lairs are", e);
		}
	}

	public static void close() {
		record = null;
		HOLLOWS.clear();
	}

	static void add(BlockPos hollow) {
		HOLLOWS.add(hollow.immutable());
		Path file = record;
		if (file == null) return;
		StringBuilder out = new StringBuilder();
		for (BlockPos p : HOLLOWS) out.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append('\n');
		try {
			Files.writeString(file, out.toString());
		} catch (IOException e) {
			Occupant.LOGGER.warn("Could not write where its lairs are", e);
		}
	}

	/** The hollow this position is in, if it is in one. */
	@Nullable
	public static BlockPos hollowAt(BlockPos pos) {
		for (BlockPos h : HOLLOWS) {
			if (Math.abs(pos.getY() - h.getY()) <= 5 && h.distSqr(new BlockPos(pos.getX(), h.getY(), pos.getZ())) <= Lair.RADIUS * Lair.RADIUS) {
				return h;
			}
		}
		return null;
	}
}
