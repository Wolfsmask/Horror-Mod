package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The signature moment: a figure standing in the dark, off to the side, just watching you.
 * Placed in your peripheral vision so you catch it out of the corner of your eye.
 * It is always in the shadows, always with a clear line of sight, and never close enough to reach.
 */
public final class WatcherEvent extends HorrorEvent {
	public WatcherEvent() {
		super("watcher", Tier.MAJOR, 2, 12, 10);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && !s.inCombat() && !s.busy() && !s.inWater() && s.gloomy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayerEntity p = ctx.player;
		boolean underground = ctx.situation.underground();
		int act = ctx.act();

		double min;
		double max;
		double minAngle;
		double maxAngle;
		if (underground) {
			min = act >= 3 ? 10 : 14;
			max = min + 10;
			minAngle = 5;
			maxAngle = 40;
		} else {
			min = act <= 2 ? 36 : act == 3 ? 24 : 16;
			max = min + 18;
			minAngle = 20;
			maxAngle = 55;
		}

		BlockPos spot = Spots.aroundPlayer(p, ctx.random, min, max, minAngle, maxAngle, !underground, 40,
				pos -> goodSpot(ctx, pos, min));
		if (spot == null && !underground) {
			spot = Spots.aroundPlayer(p, ctx.random, min, max, 0, 20, true, 25, pos -> goodSpot(ctx, pos, min));
		}
		if (spot == null) return null;

		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, ctx.haunt.pickForm(ctx.random));
		if (e == null) return null;

		int reaction = act >= 3 ? 10 + ctx.random.nextInt(20) : 5 + ctx.random.nextInt(12);
		boolean waitsForLookAway = act >= 3 && ctx.random.nextFloat() < 0.35f;
		double vanishDistance = underground ? (act >= 3 ? 7 : 10) : (act >= 4 ? 10 : act == 3 ? 14 : 20);
		return new WatcherSequence(ctx.haunt, e, reaction, waitsForLookAway, vanishDistance, 900 + ctx.random.nextInt(300));
	}

	static boolean goodSpot(EventContext ctx, BlockPos pos, double minDist) {
		ServerPlayerEntity p = ctx.player;
		Vec3d base = Vec3d.ofBottomCenter(pos);
		if (base.distanceTo(p.getPos()) < minDist * 0.8) return false;
		if (Spots.light(ctx.world, pos.up()) > 7) return false;
		if (!Spots.awayFromOthers(p, base, 24)) return false;
		return Sight.hasLineOfSight(p, base.add(0, 1.6, 0)) && Sight.hasLineOfSight(p, base.add(0, 0.9, 0));
	}
}
