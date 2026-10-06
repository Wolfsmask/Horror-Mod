package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One roll of thunder, a long way off, from a clear night sky. No flash, no rain, and never
 * again that night. Was it thunder?
 */
public final class ThunderEvent extends HorrorEvent {
	public static final String ID = "thunder";

	public ThunderEvent() {
		super(ID, Tier.AMBIENT, 1, 2, 40);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.night() && !ctx.situation.underground() && !ctx.world.isRaining() && !ctx.situation.inCombat();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		// Behind them and far off, so there is no flash to look for.
		Vec3 from = p.position().add(Sight.rotateY(Sight.flatLook(p), 150 + ctx.random.nextInt(60)).scale(90.0)).add(0, 30, 0);
		return new Timeline().at(0, pl -> Cues.sound(pl, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, from, 8.0f, 0.55f));
	}
}
