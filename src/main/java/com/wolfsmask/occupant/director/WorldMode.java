package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How this world tells its story: the ordinary slow burn, or the Creator Cut, made for recording.
 * The Creator Cut is the same story, tighter: about forty minutes from the first black screen to
 * the end of the last night, never quiet for long, and the ending always reached.
 * <p>
 * Chosen once, when the world is made (at the gate, or with {@code /occupant creator}), and kept
 * with the world in {@code occupant_mode.txt}.
 */
public final class WorldMode {
	private static final String FILE = "occupant_mode.txt";
	/** Set by the gate, in this same game, just before it makes a world. */
	private static volatile boolean nextWorldIsCreatorCut;
	private static volatile boolean creatorCut;
	private static Path file;

	private WorldMode() {
	}

	/** The next world made in this game is a Creator Cut (or not). */
	public static void requestForNextWorld(boolean creator) {
		nextWorldIsCreatorCut = creator;
	}

	public static boolean creatorCut() {
		return creatorCut;
	}

	/** Reads the world's mode; a world without one is new (or from before there were modes). */
	public static void open(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
		boolean requested = nextWorldIsCreatorCut;
		nextWorldIsCreatorCut = false;
		try {
			if (Files.exists(file)) {
				creatorCut = Files.readString(file).trim().equalsIgnoreCase("creator");
				return;
			}
		} catch (IOException e) {
			Occupant.LOGGER.warn("Could not read {}; telling the story the usual way", file, e);
			creatorCut = false;
			return;
		}
		// The gate asks just before it makes a new world, and takes it back if that is cancelled.
		set(requested);
	}

	public static void set(boolean creator) {
		creatorCut = creator;
		if (file == null) return;
		try {
			Files.writeString(file, creator ? "creator\n" : "story\n");
		} catch (IOException e) {
			Occupant.LOGGER.warn("Could not write {}", file, e);
		}
		Occupant.LOGGER.info("This world tells the story as {}", creator ? "the Creator Cut" : "a slow burn");
	}

	public static void close() {
		creatorCut = false;
		file = null;
	}
}
