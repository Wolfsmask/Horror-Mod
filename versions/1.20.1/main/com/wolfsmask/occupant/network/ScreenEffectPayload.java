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
	/** The fog: {@code intensity} is where it is thick, in blocks (0 = none), reached over {@code duration} ticks. */
	public static final int FOG = 5;
	/** Where the story is: {@code intensity} is the act (0 while the haunting is off). */
	public static final int ACT = 6;
	/** The end of the last night: black, and a line, like the first time. */
	public static final int FINALE = 7;
	/** "Saving world..." in the corner, when nothing is: {@code intensity} picks what it says it is saving. */
	public static final int SAVING = 8;
	/** They have just come into a world it haunts: the few seconds of black and a line, the way in. */
	public static final int JOINED = 9;
	/**
	 * A scene they watch: for {@code duration} ticks (0 ends it) their view is drawn to what it is
	 * doing, nothing they press moves them, and the picture narrows to a band.
	 */
	public static final int CUTSCENE = 10;
	/**
	 * What a scene looks at, when it is a place and not the Occupant: {@code duration} is that
	 * place's x (then y, then z), in eighths of a block. The scene turns to it once it has all three.
	 */
	public static final int LOOK_X = 11;
	public static final int LOOK_Y = 12;
	public static final int LOOK_Z = 13;
	/** The scene looks at the Occupant again. */
	public static final int LOOK_FREE = 14;

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
