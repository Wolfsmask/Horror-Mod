package com.wolfsmask.occupant.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wolfsmask.occupant.Occupant;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Per-player comfort settings, stored in {@code config/occupant-client.json}. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance = new ClientConfig();

	/** Photosensitivity: replace hard flashes and flicker with slow fades, and calm the static. */
	public boolean reduceFlashing = false;

	/** Show analog static on screen when it is near. */
	public boolean screenStatic = true;

	/** Show the lines of text that surface on the screen. */
	public boolean screenText = true;

	/** The mod's own title screen: the wood at night, and what stands in it. */
	public boolean titleScreen = true;

	/** The dark at the edges of the screen, the grain, the cold: stronger in the dark and when it is near. */
	public boolean atmosphere = true;

	/** The fog the story brings in (the server decides how thick; this only switches it off for you). */
	public boolean fog = true;

	/** Your own heartbeat, loud, when it is close and you are not looking at it. */
	public boolean heartbeat = true;

	/** The mod's own score, rising and falling with the story (under the Music volume). */
	public boolean score = true;

	/** Ask, before the title screen, whether this is for playing or recording. */
	public boolean askHowPlaying = true;

	/** The last answer: recording (the Creator Cut) or not. */
	public boolean recording = false;

	public static ClientConfig get() {
		return instance;
	}

	/** Writes the settings as they are now (after a change from the settings screen). */
	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("occupant-client.json");
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			Occupant.LOGGER.warn("Could not write {}", path, e);
		}
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("occupant-client.json");
		try {
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					ClientConfig loaded = GSON.fromJson(r, ClientConfig.class);
					if (loaded != null) instance = loaded;
				}
			}
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			Occupant.LOGGER.warn("Could not read or write {}, using defaults", path, e);
		}
	}
}
