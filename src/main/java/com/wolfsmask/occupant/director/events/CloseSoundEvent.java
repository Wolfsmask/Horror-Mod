package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** A sound right next to you, from somewhere you are not looking: breathing, or whispering. */
public final class CloseSoundEvent extends HorrorEvent {
	private final Supplier<RegistryEntry<SoundEvent>> sound;
	private final boolean behind;
	private final float volume;

	public CloseSoundEvent(String id, int minAct, double weight, int cooldown, Supplier<RegistryEntry<SoundEvent>> sound,
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
		ServerPlayerEntity p = ctx.player;
		Vec3d look = Sight.flatLook(p);
		double[] angles = behind ? new double[]{180, 155, -155} : new double[]{95, -95, 120, -120};
		for (double angle : angles) {
			Vec3d dir = Sight.rotateY(look, angle + (ctx.random.nextDouble() - 0.5) * 20);
			Vec3d pos = p.getEyePos().add(dir.multiply(behind ? 1.4 : 2.0)).add(0, 0.1, 0);
			BlockPos block = BlockPos.ofFloored(pos);
			if (!ctx.world.getBlockState(block).getCollisionShape(ctx.world, block).isEmpty()) continue;
			if (!Sight.hasLineOfSight(p, pos)) continue;
			float pitch = 0.92f + ctx.random.nextFloat() * 0.14f;
			return new Timeline().at(0, pl -> Cues.sound(pl, sound.get(), SoundCategory.HOSTILE, pos, volume, pitch));
		}
		return null;
	}
}
