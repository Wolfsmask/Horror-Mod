package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** You are walking home at night. Someone is already there, standing by your bed. */
public final class IntruderEvent extends HorrorEvent {
	public IntruderEvent() {
		super("intruder", Tier.MAJOR, 3, 5, 30);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		if (!ctx.aloneEnough() || !s.night() || s.underground() || s.sheltered() || s.inCombat() || s.busy()) return false;
		BlockPos bed = Spots.respawnPos(ctx.player);
		if (bed == null) return false;
		double d = Math.sqrt(bed.distToCenterSqr(ctx.player.position()));
		return d >= 14 && d <= 48;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos bed = Spots.respawnPos(p);
		if (bed == null) return null;

		BlockPos spot = Spots.nearestBlock(world, bed, 4, 2, pos ->
				pos.distSqr(bed) >= 2
						&& Spots.canStand(world, pos)
						&& Sight.isHidden(p, pos)
						&& Sight.isHidden(p, pos.above()));
		if (spot == null) return null;

		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, ctx.haunt.pickForm(ctx.random));
		if (e == null) return null;
		return new WatcherSequence(ctx.haunt, e, 12, false, 6.0, 1800);
	}
}
