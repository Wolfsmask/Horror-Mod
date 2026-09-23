package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** One of the game's own cave sounds. Completely deniable. That is the point. */
public final class CaveNoiseEvent extends HorrorEvent {
	public CaveNoiseEvent() {
		super("cave_noise", Tier.AMBIENT, 1, 6, 10);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return (ctx.situation.underground() || ctx.situation.night()) && !ctx.situation.inCombat();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		Vec3d dir = Sight.rotateY(Sight.flatLook(ctx.player), ctx.random.nextDouble() * 360.0);
		Vec3d pos = ctx.player.getEyePos().add(dir.multiply(10 + ctx.random.nextDouble() * 6)).add(0, ctx.random.nextDouble() * 8 - 4, 0);
		float pitch = 0.75f + ctx.random.nextFloat() * 0.25f;
		return new Timeline().at(0, p -> Cues.sound(p, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, pos, 0.9f, pitch));
	}
}
