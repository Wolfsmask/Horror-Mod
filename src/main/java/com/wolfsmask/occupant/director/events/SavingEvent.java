package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;

/**
 * "Saving world..." in the corner of the screen, when nothing is saving. Later on it is not the
 * world it says it is saving.
 */
public final class SavingEvent extends HorrorEvent {
	public static final String ID = "saving";

	public SavingEvent() {
		super(ID, Tier.AMBIENT, 2, 2, 35);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		// intensity: 0 = "Saving world...", 1 = "Saving you...", 2 = "Saving {player}..."
		float which = ctx.act() >= 4 && ctx.random.nextBoolean() ? 2f : ctx.act() >= 3 && ctx.random.nextBoolean() ? 1f : 0f;
		return new Timeline().at(0, p -> Cues.effect(p, ScreenEffectPayload.SAVING, 70, which));
	}
}
