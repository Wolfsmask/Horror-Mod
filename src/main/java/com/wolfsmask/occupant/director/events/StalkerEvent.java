package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * It follows you. You can hear it. It only moves while you are not looking, and freezes the
 * moment you turn around. Stare at it long enough and it is gone. Let it get close and you
 * will hear it breathe.
 */
public final class StalkerEvent extends HorrorEvent {
	public StalkerEvent() {
		super("stalker", Tier.MAJOR, 3, 7, 20);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && !s.inCombat() && !s.busy() && !s.inWater() && s.gloomy() && !s.still();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		boolean underground = ctx.situation.underground();
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, 16, 26, 120, 180, !underground, 40, pos -> {
			Vec3 base = Vec3.atBottomCenterOf(pos);
			return Math.abs(pos.getY() - p.getBlockY()) <= 5
					&& Spots.light(ctx.world, pos.above()) <= 8
					&& Spots.awayFromOthers(p, base, 24)
					&& Sight.angleTo(p, base.add(0, 1.0, 0)) >= 100;
		});
		if (spot == null) return null;

		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STALK, ctx.haunt.pickForm(ctx.random));
		if (e == null) return null;
		return new Stalking(ctx.haunt, e, 1200 + ctx.random.nextInt(600));
	}

	private static final class Stalking extends ApparitionSequence {
		private final int duration;

		Stalking(Haunt haunt, OccupantEntity entity, int duration) {
			super(haunt, entity);
			this.duration = duration;
			entity.setGazeLocked(true);
			entity.setFootsteps(true);
		}

		@Override
		protected boolean update(ServerPlayer p, boolean looking) {
			double dist = entity.distanceTo(p);
			boolean onScreen = Sight.isOnScreen(p, entity);

			if (dist < 5.0) {
				if (!onScreen) Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.6f, 1.0f);
				return false;
			}
			if (dist > 56) return false;
			if (lookTicks > 30) {
				Cues.effect(p, ScreenEffectPayload.STATIC, 8, 0.35f);
				return false;
			}

			if (onScreen) {
				entity.halt();
			} else if (age % 10 == 0) {
				if (dist > 10) entity.chase(p, 1.0);
				else entity.halt();
			}

			if (age > duration && !onScreen) return false;
			return age <= duration + 400;
		}
	}
}
