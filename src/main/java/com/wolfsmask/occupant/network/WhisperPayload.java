package com.wolfsmask.occupant.network;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A line of text to fade up on one player's screen, like something written on a wall that was
 * always there. It is never put in the chat log, so there is nothing to scroll back to.
 *
 * @param text     what it says (already personalised, at most {@value #MAX_LENGTH} characters)
 * @param duration how long it stays, in ticks
 * @param corner   where it sits: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, 4 middle;
 *                 or, at the end of everything, {@value #TITLE} a title across the middle, large, and
 *                 {@value #SUBTITLE} a line under it
 */
public record WhisperPayload(String text, int duration, int corner) implements CustomPacketPayload {
	public static final int MAX_LENGTH = 64;
	public static final int CORNERS = 5;
	/** Not a corner: the title of an ending, large, across the middle of the screen. */
	public static final int TITLE = 5;
	/** Not a corner: a line under the title. */
	public static final int SUBTITLE = 6;

	public static final CustomPacketPayload.Type<WhisperPayload> TYPE =
			new CustomPacketPayload.Type<>(Occupant.id("whisper"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WhisperPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(MAX_LENGTH), WhisperPayload::text,
			ByteBufCodecs.VAR_INT, WhisperPayload::duration,
			ByteBufCodecs.VAR_INT, WhisperPayload::corner,
			WhisperPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
