package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.sounds.SoundSource;

/** For a second, the picture breaks up, like a bad signal. Something is interfering. */
public final class StaticEvent extends HorrorEvent {
	public StaticEvent() {
		super("static", Tier.MINOR, 3, 2, 25);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.gloomy() && !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		return new Timeline().at(0, p -> {
			Cues.effect(p, ScreenEffectPayload.STATIC, 14, 0.5f);
			Cues.soundAtEars(p, ModSounds.STATIC, SoundSource.AMBIENT, 0.35f, 1.0f);
			// Something surfaces in the noise, the way words surface on the wall of a room
			// somebody was kept in.
			if (p.getRandom().nextFloat() < 0.75f) Cues.whisper(p, 70);
		});
	}
}
