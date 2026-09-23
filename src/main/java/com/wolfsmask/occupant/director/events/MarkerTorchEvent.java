package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		BlockState torch = (ctx.act() >= 3 ? Blocks.REDSTONE_TORCH : Blocks.TORCH).getDefaultState();
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, 10, 22, 60, 180, false, 30, pos ->
				world.getBlockState(pos).isAir()
						&& Spots.light(world, pos) <= 3
						&& WorldBlocks.isNaturalFloor(world.getBlockState(pos.down()))
						&& torch.canPlaceAt(world, pos)
						&& Sight.isHidden(p, pos));
		if (spot == null) return null;
		if (!world.setBlockState(spot, torch, Block.NOTIFY_ALL)) return null;
		return new Timeline().at(0, pl -> {
		});
	}
}
