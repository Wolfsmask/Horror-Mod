package com.wolfsmask.occupant.story;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When someone dies, late in the story, in a world with other people in it: they lose their
 * voice. For a while nothing they type reaches anyone, and they are told so once. If the server's
 * owner has set up a Discord bot for it, the same happens there: a timeout in their Discord
 * server, so no messages and no speaking in voice, which lifts by itself.
 */
public final class Silence {
	private static final String[] TOLD = {"No one can hear you.", "Your voice stayed where you died.",
			"It has your voice now.", "They can't hear you any more."};
	private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private Silence() {
	}

	/** A player has died. */
	public static void died(ServerPlayer player) {
		OccupantConfig cfg = OccupantConfig.get();
		Director director = Director.get();
		if (!cfg.enabled || cfg.silenceMinutes <= 0 || director == null) return;
		if (director.data(player).act < 2) return;
		var server = player.level().getServer();
		if (server == null || server.getPlayerList().getPlayerCount() < 2) return;   // nobody to be cut off from
		long ms = (long) (cfg.silenceMinutes * 60_000);
		UNTIL.put(player.getUUID(), System.currentTimeMillis() + ms);
		Occupant.LOGGER.info("{} has lost their voice for {} minutes", player.getName().getString(), cfg.silenceMinutes);
		discordTimeout(player.getName().getString(), Instant.now().plusMillis(ms));
	}

	/** Is this player's voice gone right now? */
	public static boolean silenced(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		if (until == null) return false;
		if (System.currentTimeMillis() < until) return true;
		UNTIL.remove(player.getUUID());
		return false;
	}

	/** They tried to say something: it goes nowhere, and they are told. */
	public static void swallowed(ServerPlayer player) {
		String line = TOLD[player.getRandom().nextInt(TOLD.length)];
		player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
	}

	public static void clear() {
		UNTIL.clear();
	}

	/** The same in Discord, if the server's owner has set it up; never on the server's own thread. */
	private static void discordTimeout(String name, Instant until) {
		OccupantConfig cfg = OccupantConfig.get();
		String token = cfg.discordBotToken.trim();
		String guild = cfg.discordServerId.trim();
		String user = null;
		for (Map.Entry<String, String> e : cfg.discordPlayers.entrySet()) {
			if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) user = e.getValue();
		}
		if (token.isEmpty() || guild.isEmpty() || user == null || !user.trim().matches("\\d{5,25}") || !guild.matches("\\d{5,25}")) return;
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/guilds/" + guild + "/members/" + user.trim()))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bot " + token)
				.header("Content-Type", "application/json")
				.header("User-Agent", "DiscordBot (https://github.com/Wolfsmask/Horror-Mod, 1.0)")
				.header("X-Audit-Log-Reason", "The Occupant: died in the story")
				.method("PATCH", HttpRequest.BodyPublishers.ofString("{\"communication_disabled_until\":\"" + until + "\"}"))
				.build();
		HTTP.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
			if (error != null) {
				Occupant.LOGGER.warn("Could not reach Discord to silence {}: {}", name, error.toString());
			} else if (response.statusCode() >= 300) {
				Occupant.LOGGER.warn("Discord would not silence {} (HTTP {}): check the bot can time out members and is above them",
						name, response.statusCode());
			}
		});
	}
}
