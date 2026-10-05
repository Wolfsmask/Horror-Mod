package com.wolfsmask.occupant.network;

import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A line of text to fade up on one player's screen, never put in the chat log. This copy is for
 * 1.20.1, which has Fabric's packet types rather than the game's payloads.
 *
 * @param text     what it says (already personalised, at most {@value #MAX_LENGTH} characters)
 * @param duration how long it stays, in ticks
 * @param corner   where it sits: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, 4 middle
 */
public record WhisperPayload(String text, int duration, int corner) implements FabricPacket {
	public static final int MAX_LENGTH = 64;
	public static final int CORNERS = 5;

	public static final PacketType<WhisperPayload> TYPE = PacketType.create(Occupant.id("whisper"),
			buf -> new WhisperPayload(buf.readUtf(MAX_LENGTH), buf.readVarInt(), buf.readVarInt()));

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(text, MAX_LENGTH);
		buf.writeVarInt(duration);
		buf.writeVarInt(corner);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
