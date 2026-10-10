package com.wolfsmask.occupant.story;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The mod's own advancements: small things noticed, never anything killed. They live in the data
 * pack (data/occupant/advancement on 1.21 and later, advancements on 1.20.1), each with one
 * criterion that only this class can meet. They are granted through the game's own
 * {@code /advancement} command, which reads the same on every Minecraft version.
 */
public final class Achievements {
	public static final String ROOT = "root";
	public static final String NOT_ALONE = "not_alone";
	public static final String WATCHED = "watched";
	public static final String DEAR_DIARY = "dear_diary";
	public static final String EVERY_WORD = "every_word";
	public static final String SLEEPLESS = "sleepless";
	public static final String DOWN_THERE = "down_there";
	/** The first time its leg goes through them. */
	public static final String ONLY_ME = "only_me";
	public static final String LAST_NIGHT = "last_night";
	public static final String ENDING_FOUND = "ending_found";
	public static final String ENDING_HID = "ending_hid";
	public static final String ENDING_LEARNED = "ending_learned";
	/** It took them, and showed them its wall of names. */
	public static final String ENDING_TAKEN = "ending_taken";

	/** Events that are an advancement the first time they happen. */
	private static final Map<String, String> BY_EVENT = Map.ofEntries(
			Map.entry("knock", "who_is_there"),
			Map.entry("radio", "bad_reception"),
			Map.entry("fire_out", "lights_out"),
			Map.entry("torch_gone", "lights_out"),
			Map.entry("hallway", "somebody_home"),
			Map.entry("lair", DOWN_THERE),
			Map.entry("saving", "saving_world"),
			Map.entry("window", "check_the_windows"),
			Map.entry("footprints", "footprints"),
			Map.entry("teammate", "not_what_i_said"),
			Map.entry("doppel_chat", "not_what_i_said"));

	private Achievements() {
	}

	/** An event has just started for this player. */
	public static void onEvent(ServerPlayer player, String eventId) {
		String name = BY_EVENT.get(eventId);
		if (name != null) grant(player, name);
	}

	public static void grant(ServerPlayer player, String name) {
		try {
			MinecraftServer server = player.level().getServer();
			if (server == null) return;
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"advancement grant " + player.getStringUUID() + " only " + Occupant.MOD_ID + ":" + name);
		} catch (RuntimeException e) {
			Occupant.LOGGER.debug("Could not grant {}", name, e);
		}
	}
}
