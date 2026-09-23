package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The lights stutter. Later in the story, in the split seconds between the flickers,
 * something is standing in front of you. When the lights settle, it is not.
 */
public final class FlickerEvent extends HorrorEvent {
	/** Must match the client's flicker pattern: the last dark stretch starts here. */
	private static final int FINAL_DARK_TICK = 24;

	public FlickerEvent() {
		super("flicker", Tier.MINOR, 3, 4, 20);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return (s.sheltered() || s.underground() || s.night()) && !s.inCombat() && !s.busy();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		ServerPlayerEntity p = ctx.player;
		if (ctx.aloneEnough() && ctx.random.nextFloat() < 0.45f) {
			BlockPos spot = Spots.aroundPlayer(p, ctx.random, 5, 9, 0, 22, false, 20, pos -> {
				Vec3d base = Vec3d.ofBottomCenter(pos);
				return Math.abs(pos.getY() - p.getBlockY()) <= 2
						&& Sight.hasLineOfSight(p, base.add(0, 1.6, 0))
						&& Sight.hasLineOfSight(p, base.add(0, 0.9, 0));
			});
			if (spot != null) {
				OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, OccupantEntity.Form.HOLLOW);
				if (e != null) return new BetweenFlickers(ctx.haunt, e);
			}
		}
		return new Timeline().at(0, pl -> Cues.effect(pl, ScreenEffectPayload.FLICKER, 30, 1f));
	}

	private static final class BetweenFlickers extends ApparitionSequence {
		BetweenFlickers(Haunt haunt, OccupantEntity entity) {
			super(haunt, entity);
			entity.setFootsteps(false);
		}

		@Override
		protected boolean update(ServerPlayerEntity p, boolean looking) {
			if (age == 1) Cues.effect(p, ScreenEffectPayload.FLICKER, 30, 1f);
			return age < FINAL_DARK_TICK;
		}
	}
}
