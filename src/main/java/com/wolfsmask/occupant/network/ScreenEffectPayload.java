package com.wolfsmask.occupant.network;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells one client to play a screen effect.
 *
 * @param effect    one of the constants below
 * @param duration  length in ticks
 * @param intensity 0..1
 */
public record ScreenEffectPayload(int effect, int duration, float intensity) implements CustomPacketPayload {
	/** Cut to black, hold, then fade back in. */
	public static final int BLACKOUT = 0;
	/** The "lights" stutter a few times. */
	public static final int FLICKER = 1;
	/** Analog static over the screen. */
	public static final int STATIC = 2;
	/** Stop whatever music is playing, as if something is listening. */
	public static final int SILENCE = 3;

	public static final CustomPacketPayload.Type<ScreenEffectPayload> TYPE =
			new CustomPacketPayload.Type<>(Occupant.id("screen_effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenEffectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ScreenEffectPayload::effect,
			ByteBufCodecs.VAR_INT, ScreenEffectPayload::duration,
			ByteBufCodecs.FLOAT, ScreenEffectPayload::intensity,
			ScreenEffectPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
