package com.wolfsmask.occupant.network;

import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Tells one client to play a screen effect. This copy is for 1.20.1, which has Fabric's packet
 * types rather than the game's payloads.
 *
 * @param effect    one of the constants below
 * @param duration  length in ticks
 * @param intensity 0..1
 */
public record ScreenEffectPayload(int effect, int duration, float intensity) implements FabricPacket {
	/** Cut to black, hold, then fade back in. */
	public static final int BLACKOUT = 0;
	/** The "lights" stutter a few times. */
	public static final int FLICKER = 1;
	/** Analog static over the screen. */
	public static final int STATIC = 2;
	/** Stop whatever music is playing, as if something is listening. */
	public static final int SILENCE = 3;
	/** The first time a player is in a world: a long black, and it tells them they are not alone. */
	public static final int FIRST_ARRIVAL = 4;

	public static final PacketType<ScreenEffectPayload> TYPE = PacketType.create(Occupant.id("screen_effect"),
			buf -> new ScreenEffectPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(effect);
		buf.writeVarInt(duration);
		buf.writeFloat(intensity);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
