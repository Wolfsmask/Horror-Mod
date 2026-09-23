package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import org.jetbrains.annotations.Nullable;

/**
 * One kind of thing the Occupant can do.
 * <p>
 * {@link #fits} is a cheap check of the player's situation. {@link #begin} does the real work of
 * finding a convincing place and time; if it cannot, it returns null and nothing at all happens.
 * That is the key to never looking "broken": when in doubt, do nothing.
 */
public abstract class HorrorEvent {
	/** How big a moment this is. The Director shapes the story by choosing tiers. */
	public enum Tier {
		/** Deniable. Could just be the game. */
		AMBIENT(3f),
		/** Clearly wrong, but small. */
		MINOR(5f),
		/** A sighting or something you cannot explain away. */
		MAJOR(10f),
		/** A scare. Followed by a long calm. */
		PEAK(0f);

		public final float dread;

		Tier(float dread) {
			this.dread = dread;
		}
	}

	private final String id;
	private final Tier tier;
	private final int minAct;
	private final double weight;
	private final int cooldownMinutes;

	protected HorrorEvent(String id, Tier tier, int minAct, double weight, int cooldownMinutes) {
		this.id = id;
		this.tier = tier;
		this.minAct = minAct;
		this.weight = weight;
		this.cooldownMinutes = cooldownMinutes;
	}

	public final String id() {
		return id;
	}

	public final Tier tier() {
		return tier;
	}

	public final int minAct() {
		return minAct;
	}

	public final double weight() {
		return weight;
	}

	public final long cooldownTicks() {
		return cooldownMinutes * 1200L;
	}

	/** Is this event switched on in the config? */
	public boolean allowedBy(OccupantConfig config) {
		return true;
	}

	/** Can only be started by a hook (like waking up), never by the scheduler. */
	public boolean hookOnly() {
		return false;
	}

	/** Would this make sense in the player's current situation? */
	public abstract boolean fits(EventContext ctx);

	/** Multiplier on the weight for the current situation (1 = neutral). */
	public double situationalWeight(EventContext ctx) {
		return 1.0;
	}

	/** Try to start. Return null if there is no convincing way to do it right now. */
	@Nullable
	public abstract Sequence begin(EventContext ctx);
}
