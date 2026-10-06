package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Down a long, straight tunnel, the one you dug yourself, at the very far end where your light does
 * not reach: something standing in it, facing you. Day or night, it makes no difference down here.
 */
public final class CorridorEvent extends HorrorEvent {
	public static final String ID = "corridor";

	public CorridorEvent() {
		super(ID, Tier.MAJOR, 2, 6, 15);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.aloneEnough() && ctx.situation.underground() && !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		// How far the tunnel runs, straight, in the direction they face (or behind them).
		for (int turn : new int[]{0, 180}) {
			Vec3 dir = Sight.rotateY(Sight.flatLook(p), turn);
			BlockPos far = null;
			for (int d = 4; d <= 48; d++) {
				Vec3 at = p.position().add(dir.scale(d));
				BlockPos feet = BlockPos.containing(at.x, p.getY(), at.z);
				if (!Spots.isLoaded(world, feet)) break;
				if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
						|| !world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()) break;
				if (d >= 18 && Spots.canStand(world, feet) && Spots.isDark(world, feet.above())) far = feet.immutable();
			}
			if (far == null) continue;
			OccupantEntity e = ctx.haunt.spawnOccupant(p, far, OccupantEntity.Mode.STARE, ctx.haunt.pickForm(ctx.random));
			if (e == null) continue;
			return new WatcherSequence(ctx.haunt, e, 10 + ctx.random.nextInt(15), false, 8.0, 900);
		}
		return null;
	}
}
