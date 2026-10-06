package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * You wake, and every door in the house is standing open. You shut them all last night.
 * Only ever on waking, never while you can watch it happen.
 */
public final class MorningEvent extends HorrorEvent {
	public static final String ID = "morning_doors";

	public MorningEvent() {
		super(ID, Tier.MINOR, 2, 1, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean hookOnly() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return true;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		int opened = 0;
		for (BlockPos pos : Compat.withinManhattan(p.blockPosition(), 12, 4, 12)) {
			if (!Spots.isLoaded(world, pos) || !WorldBlocks.isClosedWoodenDoor(world, pos)) continue;
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock door) {
				door.setOpen(null, world, state, pos.immutable(), true);
				if (++opened >= 6) break;
			}
		}
		return opened == 0 ? null : new Timeline().at(0, pl -> {
		});
	}
}
