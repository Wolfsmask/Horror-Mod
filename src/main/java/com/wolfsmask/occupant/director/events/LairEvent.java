package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Spots;
import com.wolfsmask.occupant.world.Lairs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Down the hole, into its hollow. The music stops. It is there, at the far side, among its
 * things, and it has turned round to see who has come in. Started by going in, never by the
 * scheduler.
 */
public final class LairEvent extends HorrorEvent {
	public static final String ID = "lair";

	public LairEvent() {
		super(ID, Tier.MAJOR, 1, 1, 10);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean hookOnly() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return Lairs.hollowAt(ctx.player.blockPosition()) != null;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		BlockPos hollow = Lairs.hollowAt(p.blockPosition());
		if (hollow == null) return null;
		// The far side of the hollow from where they are.
		double dx = hollow.getX() - p.getX(), dz = hollow.getZ() - p.getZ();
		double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		BlockPos far = null;
		for (int r = 5; r >= 2 && far == null; r--) {
			int x = (int) Math.floor(hollow.getX() + dx / len * r);
			int z = (int) Math.floor(hollow.getZ() + dz / len * r);
			far = Spots.groundNear(ctx.world, x, hollow.getY(), z, 4);
		}
		if (far == null) return null;
		OccupantEntity e = ctx.haunt.spawnOccupant(p, far, OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
		if (e == null) return null;
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1.0f);
		return new WatcherSequence(ctx.haunt, e, 20, true, 3.0, 1200);
	}
}
