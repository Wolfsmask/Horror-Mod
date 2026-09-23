package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		List<BlockPos> doors = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterateOutwards(p.getBlockPos(), 14, 4, 14)) {
			if (doors.size() >= 6) break;
			if (pos.getSquaredDistance(p.getPos()) < 16) continue;
			if (!WorldBlocks.isClosedWoodenDoor(world, pos)) continue;
			if (!Sight.isHidden(p, pos) || !Sight.isHidden(p, pos.up())) continue;
			doors.add(pos.toImmutable());
		}
		if (doors.isEmpty()) return null;

		BlockPos door = doors.get(ctx.random.nextInt(doors.size()));
		setOpen(world, door, true);

		Timeline t = new Timeline().at(0, pl -> {
		});
		if (ctx.random.nextBoolean()) {
			int closeAt = 50 + ctx.random.nextInt(60);
			t.at(closeAt, pl -> {
				if (WorldBlocks.isOpenDoor(world, door) && Sight.isHidden(pl, door) && Sight.isHidden(pl, door.up())) {
					setOpen(world, door, false);
				}
			});
		}
		return t;
	}

	private static void setOpen(ServerWorld world, BlockPos pos, boolean open) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock door) {
			door.setOpen(null, world, state, pos, open);
		}
	}
}
