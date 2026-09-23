package com.wolfsmask.occupant.director;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Something that is happening to a player over time. Only one runs per player at once.
 * <p>
 * Rule: never keep a reference to the player between ticks (they are a new object after
 * respawning). The Director passes the current player in every tick.
 */
public interface Sequence {
	/**
	 * Advance one tick.
	 *
	 * @return false once finished
	 */
	boolean tick(ServerPlayerEntity player);

	/** Called exactly once when the sequence ends, however it ends. Remove anything temporary here. */
	default void end() {
	}
}
