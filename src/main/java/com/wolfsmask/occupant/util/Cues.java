package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.network.ScreenEffectPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Sounds, messages and screen effects that only ONE player receives.
 * <p>
 * This is the core of the psychological side: your friend standing next to you hears nothing.
 */
public final class Cues {
	private Cues() {
	}

	public static void sound(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category,
							 Vec3d pos, float volume, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(
				sound, category, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void sound(ServerPlayerEntity player, SoundEvent sound, SoundCategory category,
							 Vec3d pos, float volume, float pitch) {
		sound(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
	}

	/** A sound right at the player's head (not positional in practice). */
	public static void soundAtEars(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category,
								   float volume, float pitch) {
		sound(player, sound, category, player.getEyePos(), volume, pitch);
	}

	public static void effect(ServerPlayerEntity player, int effect, int durationTicks, float intensity) {
		if (ServerPlayNetworking.canSend(player, ScreenEffectPayload.ID)) {
			ServerPlayNetworking.send(player, new ScreenEffectPayload(effect, durationTicks, intensity));
		}
	}

	public static void message(ServerPlayerEntity player, Text text) {
		player.sendMessage(text, false);
	}
}
