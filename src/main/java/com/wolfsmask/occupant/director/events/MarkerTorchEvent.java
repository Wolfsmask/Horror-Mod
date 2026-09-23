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
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, 10, 22, 60, 180, false, 30, pos ->
				world.getBlockState(pos).isAir()
						&& Spots.light(world, pos) <= 3
						&& WorldBlocks.isNaturalFloor(world.getBlockState(pos.below()))
						&& torch.canSurvive(world, pos)
						&& Sight.isHidden(p, pos));
		if (spot == null) return null;
		if (!world.setBlock(spot, torch, Block.UPDATE_ALL)) return null;
		return new Timeline().at(0, pl -> {
		});
	}
}
