package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The signature moment: a figure standing in the dark, off to the side, just watching you.
 * Placed in your peripheral vision so you catch it out of the corner of your eye.
 * It is always in the shadows, always with a clear line of sight, and never close enough to reach.
 */
public final class WatcherEvent extends HorrorEvent {
	public WatcherEvent() {
		super("watcher", Tier.MAJOR, 1, 12, 7);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		// In the dark; or, from the second act, in the daytime fog, which hides it just as well.
		boolean hidden = s.gloomy() || ctx.act() >= 2 && com.wolfsmask.occupant.director.Fog.endFor(ctx.player, ctx.haunt) > 0;
		return ctx.aloneEnough() && !s.inCombat() && !s.busy() && !s.inWater() && hidden;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
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
			// Never further than it can be made out through the fog.
			double[] band = com.wolfsmask.occupant.director.Fog.fit(p, ctx.haunt, min, max, 10, 12);
			min = band[0];
			max = band[1];
		}

		// Half the time only just visible, its head past the edge of something; half the time
		// simply standing there, all of it, in the open, which is worse.
		boolean peek = ctx.random.nextBoolean();
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, min, max, minAngle, maxAngle, !underground, peek ? 120 : 60,
				pos -> peek ? peekSpot(ctx, pos, min) : goodSpot(ctx, pos, min));
		if (spot == null) {
			spot = Spots.aroundPlayer(p, ctx.random, min, max, minAngle, maxAngle, !underground, peek ? 40 : 120,
					pos -> peek ? goodSpot(ctx, pos, min) : peekSpot(ctx, pos, min));
		}
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

	/** Dark, out of the way, and mostly hidden behind something, with its head showing past it. */
	static boolean peekSpot(EventContext ctx, BlockPos pos, double minDist) {
		ServerPlayer p = ctx.player;
		Vec3 base = Vec3.atBottomCenterOf(pos);
		if (base.distanceTo(p.position()) < minDist * 0.8) return false;
		if (!concealed(ctx, pos, base)) return false;
		if (!Spots.awayFromOthers(p, base, 24)) return false;
		return Sight.onlyJustVisible(p, base, 4.2);
	}

	static boolean goodSpot(EventContext ctx, BlockPos pos, double minDist) {
		ServerPlayer p = ctx.player;
		Vec3 base = Vec3.atBottomCenterOf(pos);
		if (base.distanceTo(p.position()) < minDist * 0.8) return false;
		if (!concealed(ctx, pos, base)) return false;
		if (!Spots.awayFromOthers(p, base, 24)) return false;
		return Sight.hasLineOfSight(p, base.add(0, 1.6, 0)) && Sight.hasLineOfSight(p, base.add(0, 0.9, 0));
	}

	/** In the dark, or far enough into the fog that the fog half hides it. */
	private static boolean concealed(EventContext ctx, BlockPos pos, Vec3 base) {
		if (Spots.isDark(ctx.world, pos.above())) return true;
		float fog = com.wolfsmask.occupant.director.Fog.endFor(ctx.player, ctx.haunt);
		return fog > 0 && base.distanceTo(ctx.player.position()) >= com.wolfsmask.occupant.util.FogLine.edgeNear(fog) * 0.9;
	}
}
