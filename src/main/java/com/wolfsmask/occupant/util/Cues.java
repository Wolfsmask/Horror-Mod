package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.Party;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.network.WhisperPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Sounds, words and what happens on the screen, for a player and for whoever is there with them.
 * <p>
 * It is one world: it is seen by everyone near (see the entity), a sound is heard by everyone near
 * enough to where it is, what it says to one player the others near them read, and what it writes
 * in the chat, everyone does. The light stuttering and the static are there for everyone watching
 * too. Only what it does to someone's own eyes (the black, a scene their view is drawn into) stays
 * with whoever it is doing it to, so the others are free to see it happen, or to walk up to it; as
 * do their fog, where their story is, and the end of their story (the {@code ...Only} calls).
 */
public final class Cues {
	private Cues() {
	}

	// ------------------------------------------------------------------ sound

	/** A sound at {@code pos}: for {@code player}, and for anyone else near enough to it to hear. */
	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category,
							 Vec3 pos, float volume, float pitch) {
		ClientboundSoundPacket packet = new ClientboundSoundPacket(
				sound, category, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong());
		player.connection.send(packet);
		// As far as the game itself carries a sound of this loudness, and a little more.
		double range = 16.0 * Math.max(1.0f, volume) + 4.0;
		for (ServerPlayer o : Party.near(player, pos, range)) o.connection.send(packet);
	}

	public static void sound(ServerPlayer player, SoundEvent sound, SoundSource category,
							 Vec3 pos, float volume, float pitch) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, pos, volume, pitch);
	}

	/** A sound right at the player's head: the others near them hear it there too. */
	public static void soundAtEars(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category,
								   float volume, float pitch) {
		sound(player, sound, category, player.getEyePosition(), volume, pitch);
	}

	/** A sound only {@code player} hears. */
	public static void soundOnly(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category,
								 Vec3 pos, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(
				sound, category, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void soundOnly(ServerPlayer player, SoundEvent sound, SoundSource category,
								 Vec3 pos, float volume, float pitch) {
		soundOnly(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, pos, volume, pitch);
	}

	// ------------------------------------------------------------------ the screen

	/** What is there for anyone watching: the light stuttering, the static, the world "saving". */
	private static boolean shared(int effect) {
		return effect == ScreenEffectPayload.FLICKER || effect == ScreenEffectPayload.STATIC
				|| effect == ScreenEffectPayload.SAVING;
	}

	/**
	 * Something on {@code player}'s screen. The light stuttering and the static, for those near
	 * watching with them as well (close enough to see it, not fighting, not in a scene of their
	 * own); the black, a scene, the fog and the rest are theirs alone.
	 */
	public static void effect(ServerPlayer player, int effect, int durationTicks, float intensity) {
		effectOnly(player, effect, durationTicks, intensity);
		if (!shared(effect)) return;
		for (ServerPlayer o : watchers(player)) effectOnly(o, effect, durationTicks, intensity);
	}

	/** Something on the screens of all of {@code players} (everyone it is doing something to). */
	public static void effectFor(java.util.Collection<ServerPlayer> players, int effect, int durationTicks, float intensity) {
		for (ServerPlayer p : players) effectOnly(p, effect, durationTicks, intensity);
	}

	/** Those watching {@code player}: near, free, and not in a scene of their own. */
	private static java.util.List<ServerPlayer> watchers(ServerPlayer player) {
		Director director = Director.get();
		java.util.List<ServerPlayer> out = new java.util.ArrayList<>(2);
		for (ServerPlayer o : Party.watchers(player)) {
			if (director == null || !director.inOwnScene(o)) out.add(o);
		}
		return out;
	}

	/** Something on {@code player}'s screen and no one else's. */
	public static void effectOnly(ServerPlayer player, int effect, int durationTicks, float intensity) {
		if (ServerPlayNetworking.canSend(player, ScreenEffectPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ScreenEffectPayload(effect, durationTicks, intensity));
		}
	}

	// ------------------------------------------------------------------ words

	/**
	 * Fades a line of text up on this player's screen (and the others' near them). Picks one of
	 * the configured lines at random; does nothing if they are switched off.
	 */
	public static void whisper(ServerPlayer player, int durationTicks) {
		OccupantConfig cfg = OccupantConfig.get();
		if (!cfg.screenWhispers || cfg.whisperLines.isEmpty()) return;
		String line = cfg.whisperLines.get(player.getRandom().nextInt(cfg.whisperLines.size()));
		whisper(player, line, durationTicks);
	}

	/** A line on {@code player}'s screen, and on the screens of the others near them: they all read it. */
	public static void whisper(ServerPlayer player, String line, int durationTicks) {
		WhisperPayload payload = whisperPayload(player, line, durationTicks);
		if (payload == null) return;
		send(player, payload);
		for (ServerPlayer o : Party.others(player, Party.NEAR)) send(o, payload);
	}

	/** A line on {@code player}'s screen and no one else's. */
	public static void whisperOnly(ServerPlayer player, String line, int durationTicks) {
		WhisperPayload payload = whisperPayload(player, line, durationTicks);
		if (payload != null) send(player, payload);
	}

	/** A line on the screens of the others near {@code player}, not theirs: what they see happen to them. */
	public static void whisperToOthers(ServerPlayer player, String line, int durationTicks) {
		WhisperPayload payload = whisperPayload(player, line, durationTicks);
		if (payload == null) return;
		for (ServerPlayer o : Party.others(player, Party.NEAR)) send(o, payload);
	}

	@org.jetbrains.annotations.Nullable
	private static WhisperPayload whisperPayload(ServerPlayer player, String line, int durationTicks) {
		if (!OccupantConfig.get().screenWhispers) return null;
		String text = line.replace("{player}", player.getName().getString());
		if (text.length() > WhisperPayload.MAX_LENGTH) text = text.substring(0, WhisperPayload.MAX_LENGTH);
		int corner = player.getRandom().nextInt(WhisperPayload.CORNERS);
		return new WhisperPayload(text, durationTicks, corner);
	}

	private static void send(ServerPlayer to, WhisperPayload payload) {
		if (ServerPlayNetworking.canSend(to, WhisperPayload.TYPE)) ServerPlayNetworking.send(to, payload);
	}

	/**
	 * The title of an ending, large across the middle of the screen ({@code under}: the line
	 * beneath it instead): {@code player}'s own, the end of their story. Shown whatever the
	 * whispers are set to.
	 */
	public static void title(ServerPlayer player, String line, int durationTicks, boolean under) {
		if (!ServerPlayNetworking.canSend(player, WhisperPayload.TYPE)) return;
		String text = line.replace("{player}", player.getName().getString());
		if (text.length() > WhisperPayload.MAX_LENGTH) text = text.substring(0, WhisperPayload.MAX_LENGTH);
		ServerPlayNetworking.send(player, new WhisperPayload(text, durationTicks, under ? WhisperPayload.SUBTITLE : WhisperPayload.TITLE));
	}

	/** The scene they are watching looks at this place, until {@link #lookFree}: theirs alone. */
	public static void lookAt(ServerPlayer player, Vec3 at) {
		effectOnly(player, ScreenEffectPayload.LOOK_X, (int) Math.round(at.x * 8.0), 1f);
		effectOnly(player, ScreenEffectPayload.LOOK_Y, (int) Math.round(at.y * 8.0), 1f);
		effectOnly(player, ScreenEffectPayload.LOOK_Z, (int) Math.round(at.z * 8.0), 1f);
	}

	/** The scene they are watching looks at the Occupant again. */
	public static void lookFree(ServerPlayer player) {
		effectOnly(player, ScreenEffectPayload.LOOK_FREE, 0, 1f);
	}

	/** In the chat, for everyone: the chat is the whole server's. */
	public static void message(ServerPlayer player, Component text) {
		for (ServerPlayer o : Party.everyone(player)) o.sendSystemMessage(text);
	}

	/** In the chat of {@code player} and the others near them. */
	public static void messageNear(ServerPlayer player, Component text) {
		player.sendSystemMessage(text);
		for (ServerPlayer o : Party.others(player, Party.NEAR)) o.sendSystemMessage(text);
	}

	/** In {@code player}'s chat alone. */
	public static void messageOnly(ServerPlayer player, Component text) {
		player.sendSystemMessage(text);
	}
}
