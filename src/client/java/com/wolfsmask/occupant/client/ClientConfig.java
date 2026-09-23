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

	public static ClientConfig get() {
		return instance;
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
