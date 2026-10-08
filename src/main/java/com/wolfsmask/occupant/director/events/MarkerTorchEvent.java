package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A single torch in a part of the cave you have never been to. Later, a red one. */
public final class MarkerTorchEvent extends HorrorEvent {
	public MarkerTorchEvent() {
		super("marker_torch", Tier.MINOR, 2, 3, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.underground() && !ctx.situation.inCombat();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockState torch = (ctx.act() >= 3 ? Blocks.REDSTONE_TORCH : Blocks.TORCH).defaultBlockState();
		// Every spot in a cave, not a few guesses: most of a cave is rock, and guesses all land in it.
		List<BlockPos> spots = Spots.allAroundPlayer(p, 10, 22, 60, 180, pos ->
				world.getBlockState(pos).isAir()
						&& Spots.light(world, pos) <= 3
						&& WorldBlocks.isNaturalFloor(world.getBlockState(pos.below()))
						&& torch.canSurvive(world, pos)
						&& Sight.isHidden(p, pos));
		if (spots.isEmpty()) return null;
		BlockPos spot = spots.get(ctx.random.nextInt(spots.size()));
		if (!world.setBlock(spot, torch, Block.UPDATE_ALL)) return null;
		return new Timeline().at(0, pl -> {
		});
	}
}
