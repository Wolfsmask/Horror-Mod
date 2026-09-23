package com.wolfsmask.occupant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side settings, stored in {@code config/occupant.json}.
 * <p>
 * Every field has a safe default, and a missing or broken file never stops the game:
 * the defaults are used and a fresh file is written next to the broken one.
 */
public final class OccupantConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "occupant.json";

	private static OccupantConfig instance = new OccupantConfig();

	/** Master switch. */
	public boolean enabled = true;

	/** Real minutes a player must play before anything at all happens. */
	public double graceMinutes = 15.0;

	/** Multiplies how long each act of the story lasts. 2.0 = slow burn, 0.5 = fast. */
	public double storyPace = 1.0;

	/** Multiplies how often things happen. 2.0 = twice as often. */
	public double eventFrequency = 1.0;

	/** Allow the Occupant to change the world: open doors, take torches, dig tunnels, leave signs. */
	public boolean worldChanges = true;

	/** Allow the sudden "it's right behind you" scares, blackouts and loud stingers. */
	public boolean jumpscares = true;

	/** Allow the late-story chase sequences. */
	public boolean chases = true;

	/** Health removed when a chase catches you (0 = never hurts you; 2.0 = one heart). */
	public float chaseDamage = 0.0f;

	/** Allow fake chat and fake "joined the game" messages. */
	public boolean fakeMessages = true;

	/** Allow the Occupant to (rarely) stop you from sleeping. */
	public boolean interruptSleep = true;

	/** Visual encounters only happen when no other player is within {@link #aloneRadius} blocks. */
	public boolean requireAlone = true;
	public int aloneRadius = 48;

	/** Also haunt players in creative mode (useful for recording). */
	public boolean hauntCreative = false;

	/** Only haunt players in the Overworld. */
	public boolean overworldOnly = true;

	/** Lines the Occupant may write on signs. Use {player} for the player's name. Up to 4 lines, split with "|". */
	public List<String> signMessages = new ArrayList<>(List.of(
			"|i was here|first|",
			"you left|the door|open|",
			"|why do you|keep the lights|on",
			"|{player}|",
			"this is|not your|world|",
			"|dont dig|down|",
			"|i watched you|build this|",
			"|it gets|easier|",
			"|go to sleep|{player}|"
	));

	/** Lines the Occupant may say in chat, pretending to be you (when it has none of your own words yet). */
	public List<String> chatLines = new ArrayList<>(List.of(
			"hello?",
			"is someone there",
			"i can see you",
			"why are you still here",
			"this is my world",
			"turn around",
			"you forgot something",
			"i like it here",
			"dont leave"
	));

	/** Log director decisions to the console. */
	public boolean debug = false;

	public static OccupantConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		OccupantConfig loaded = null;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, OccupantConfig.class);
			} catch (Exception e) {
				Occupant.LOGGER.error("Could not read {}, using defaults. The broken file was kept as {}.bak", path, FILE_NAME, e);
				try {
					Files.copy(path, path.resolveSibling(FILE_NAME + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// Nothing else we can do; defaults still apply.
				}
			}
		}

		instance = loaded == null ? new OccupantConfig() : loaded.sanitized();
		save(path);
	}

	private static void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			Occupant.LOGGER.warn("Could not write {}", path, e);
		}
	}

	/** Clamp values so a typo in the file can never produce absurd behaviour. */
	private OccupantConfig sanitized() {
		graceMinutes = clamp(graceMinutes, 0.0, 600.0);
		storyPace = clamp(storyPace, 0.1, 20.0);
		eventFrequency = clamp(eventFrequency, 0.1, 10.0);
		chaseDamage = (float) clamp(chaseDamage, 0.0, 40.0);
		aloneRadius = (int) clamp(aloneRadius, 0, 256);
		if (signMessages == null || signMessages.isEmpty()) signMessages = new OccupantConfig().signMessages;
		if (chatLines == null || chatLines.isEmpty()) chatLines = new OccupantConfig().chatLines;
		signMessages.removeIf(s -> s == null || s.isBlank());
		chatLines.removeIf(s -> s == null || s.isBlank());
		if (signMessages.isEmpty()) signMessages = new OccupantConfig().signMessages;
		if (chatLines.isEmpty()) chatLines = new OccupantConfig().chatLines;
		return this;
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) return min;
		return Math.max(min, Math.min(max, value));
	}
}
