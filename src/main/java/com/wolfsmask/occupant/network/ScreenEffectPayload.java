package com.wolfsmask.occupant.network;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Tells one client to play a screen effect.
 *
 * @param effect    one of the constants below
 * @param duration  length in ticks
 * @param intensity 0..1
 */
public record ScreenEffectPayload(int effect, int duration, float intensity) implements CustomPayload {
	/** Cut to black, hold, then fade back in. */
	public static final int BLACKOUT = 0;
	/** The "lights" stutter a few times. */
	public static final int FLICKER = 1;
	/** Analog static over the screen. */
	public static final int STATIC = 2;
	/** Stop whatever music is playing, as if something is listening. */
	public static final int SILENCE = 3;

	public static final CustomPayload.Id<ScreenEffectPayload> ID =
			new CustomPayload.Id<>(Identifier.of(Occupant.MOD_ID, "screen_effect"));

	public static final PacketCodec<RegistryByteBuf, ScreenEffectPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ScreenEffectPayload::effect,
			PacketCodecs.VAR_INT, ScreenEffectPayload::duration,
			PacketCodecs.FLOAT, ScreenEffectPayload::intensity,
			ScreenEffectPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
