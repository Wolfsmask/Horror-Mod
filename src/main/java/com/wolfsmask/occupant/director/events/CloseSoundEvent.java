package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** A sound right next to you, from somewhere you are not looking: breathing, or whispering. */
public final class CloseSoundEvent extends HorrorEvent {
	private final Supplier<Holder<SoundEvent>> sound;
	private final boolean behind;
	private final float volume;

	public CloseSoundEvent(String id, int minAct, double weight, int cooldown, Supplier<Holder<SoundEvent>> sound,
						   boolean behind, float volume) {
		super(id, Tier.MINOR, minAct, weight, cooldown);
		this.sound = sound;
		this.behind = behind;
		this.volume = volume;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return (s.sheltered() || s.underground() || s.night() || s.dark()) && !s.sprinting() && !s.inCombat() && !s.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		Vec3 look = Sight.flatLook(p);
		double[] angles = behind ? new double[]{180, 155, -155} : new double[]{95, -95, 120, -120};
		for (double angle : angles) {
			Vec3 dir = Sight.rotateY(look, angle + (ctx.random.nextDouble() - 0.5) * 20);
			Vec3 pos = p.getEyePosition().add(dir.scale(behind ? 1.4 : 2.0)).add(0, 0.1, 0);
			BlockPos block = BlockPos.containing(pos);
			if (!ctx.world.getBlockState(block).getCollisionShape(ctx.world, block).isEmpty()) continue;
			if (!Sight.hasLineOfSight(p, pos)) continue;
			float pitch = 0.92f + ctx.random.nextFloat() * 0.14f;
			return new Timeline().at(0, pl -> Cues.sound(pl, sound.get(), SoundSource.HOSTILE, pos, volume, pitch));
		}
		return null;
	}
}
