package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.OccupantConfig;
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
 * Sounds, messages and screen effects that only ONE player receives.
 * <p>
 * This is the core of the psychological side: your friend standing next to you hears nothing.
 */
public final class Cues {
	private Cues() {
	}

	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category,
							 Vec3 pos, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(
				sound, category, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void sound(ServerPlayer player, SoundEvent sound, SoundSource category,
							 Vec3 pos, float volume, float pitch) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, pos, volume, pitch);
	}

	/** A sound right at the player's head (not positional in practice). */
	public static void soundAtEars(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category,
								   float volume, float pitch) {
		sound(player, sound, category, player.getEyePosition(), volume, pitch);
	}

	public static void effect(ServerPlayer player, int effect, int durationTicks, float intensity) {
		if (ServerPlayNetworking.canSend(player, ScreenEffectPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ScreenEffectPayload(effect, durationTicks, intensity));
		}
	}

	/**
	 * Fades a line of text up on this player's screen and nowhere else. Picks one of the
	 * configured lines at random; does nothing if they are switched off.
	 */
	public static void whisper(ServerPlayer player, int durationTicks) {
		OccupantConfig cfg = OccupantConfig.get();
		if (!cfg.screenWhispers || cfg.whisperLines.isEmpty()) return;
		String line = cfg.whisperLines.get(player.getRandom().nextInt(cfg.whisperLines.size()));
		whisper(player, line, durationTicks);
	}

	public static void whisper(ServerPlayer player, String line, int durationTicks) {
		OccupantConfig cfg = OccupantConfig.get();
		if (!cfg.screenWhispers || !ServerPlayNetworking.canSend(player, WhisperPayload.TYPE)) return;
		String text = line.replace("{player}", player.getName().getString());
		if (text.length() > WhisperPayload.MAX_LENGTH) text = text.substring(0, WhisperPayload.MAX_LENGTH);
		int corner = player.getRandom().nextInt(WhisperPayload.CORNERS);
		ServerPlayNetworking.send(player, new WhisperPayload(text, durationTicks, corner));
	}

	public static void message(ServerPlayer player, Component text) {
		player.sendSystemMessage(text);
	}
}
