package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A door you cannot see creaks open. Sometimes it closes again a few seconds later,
 * as if someone came in.
 */
public final class DoorEvent extends HorrorEvent {
	public DoorEvent() {
		super("door", Tier.AMBIENT, 1, 6, 15);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		return (ctx.situation.sheltered() ? 1.5 : 1.0) * (ctx.situation.night() ? 1.3 : 1.0);
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		List<BlockPos> doors = new ArrayList<>();
		for (BlockPos pos : BlockPos.withinManhattan(p.blockPosition(), 14, 4, 14)) {
			if (doors.size() >= 6) break;
			if (pos.distToCenterSqr(p.position()) < 16) continue;
			if (!WorldBlocks.isClosedWoodenDoor(world, pos)) continue;
			if (!Sight.isHidden(p, pos) || !Sight.isHidden(p, pos.above())) continue;
			doors.add(pos.immutable());
		}
		if (doors.isEmpty()) return null;

		BlockPos door = doors.get(ctx.random.nextInt(doors.size()));
		setOpen(world, door, true);

		Timeline t = new Timeline().at(0, pl -> {
		});
		if (ctx.random.nextBoolean()) {
			int closeAt = 50 + ctx.random.nextInt(60);
			t.at(closeAt, pl -> {
				if (WorldBlocks.isOpenDoor(world, door) && Sight.isHidden(pl, door) && Sight.isHidden(pl, door.above())) {
					setOpen(world, door, false);
				}
			});
		}
		return t;
	}

	private static void setOpen(ServerLevel world, BlockPos pos, boolean open) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock door) {
			door.setOpen(null, world, state, pos, open);
		}
	}
}
