package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Far away, on a ridge or above the treeline, something is standing where you can see it.
 * <p>
 * This is the quietest thing in the mod and the one meant to do the most work. There is no sound,
 * no movement and no reaction: it is simply there, too far off to make out, for as long as you
 * care to look at it. You cannot prove it is a thing rather than a tree. It does not leave when
 * you look at it, because leaving would settle the question; it leaves when you stop looking, or
 * when you set off towards it, so that what you saw is never confirmed either way.
 */
public final class DistantEvent extends HorrorEvent {
	public static final String ID = "distant";
	/** Standing this much above the player reads as "on the hill", not "in the field". */
	private static final int MIN_RISE = 4;

	public DistantEvent() {
		super(ID, Tier.MINOR, 1, 14, 9);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		// Outdoors, with the sky in view, and dim enough that a pale shape is ambiguous.
		return ctx.aloneEnough() && !s.underground() && !s.sheltered() && !s.inCombat() && !s.busy()
				&& !s.inWater() && (s.night() || s.light() <= 11);
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		// It belongs to the open: worth more when the player is out in it and can see a long way.
		return ctx.situation.night() ? 1.6 : 0.8;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		double min = 48.0;
		double max = 104.0;

		// Somewhere ahead of the player, but off to one side: found, not presented.
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, min, max, 12, 60, true, 60,
				pos -> standsAbove(ctx, pos));
		if (spot == null) return null;

		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, ctx.haunt.pickForm(ctx.random));
		if (e == null) return null;
		e.setFootsteps(false);
		return new Distant(ctx.haunt, e, 1200 + ctx.random.nextInt(1800));
	}

	/** High ground with a clear line of sight, and nothing directly around it. */
	private static boolean standsAbove(EventContext ctx, BlockPos pos) {
		ServerPlayer p = ctx.player;
		if (pos.getY() < p.blockPosition().getY() + MIN_RISE) return false;
		if (!ctx.world.canSeeSky(pos.above())) return false;

		// It must break the skyline rather than stand in front of something.
		int surface = ctx.world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		if (pos.getY() < surface - 1) return false;

		Vec3 base = Vec3.atBottomCenterOf(pos);
		if (!Spots.awayFromOthers(p, base, 40)) return false;
		// Its head and chest both have to be visible, or it is only half a shape behind a hill.
		return Sight.hasLineOfSight(p, base.add(0, 3.2, 0)) && Sight.hasLineOfSight(p, base.add(0, 1.8, 0));
	}

	/**
	 * It waits. Looking at it changes nothing, which is the whole trick: there is no jump to
	 * flinch at and no reveal to be relieved by, only a shape that is still there next time.
	 */
	private static final class Distant extends ApparitionSequence {
		private final int maxLife;
		private int unseenFor;

		Distant(Haunt haunt, OccupantEntity entity, int maxLife) {
			super(haunt, entity);
			this.maxLife = maxLife;
			entity.setGazeLocked(true);
		}

		@Override
		protected void onSeen(ServerPlayer player) {
			// Noticing it is the whole event. Little dread now; a great deal of it over an evening.
			haunt.data.addDread(3f);
		}

		@Override
		protected boolean update(ServerPlayer player, boolean looking) {
			if (looking) {
				unseenFor = 0;
			} else if (seen && ++unseenFor > 60) {
				return false;                       // gone the moment you turn away for long enough
			}
			// Coming to find out what it is never works.
			if (entity.distanceTo(player) < 34.0) return false;
			return age < maxLife;
		}
	}
}
