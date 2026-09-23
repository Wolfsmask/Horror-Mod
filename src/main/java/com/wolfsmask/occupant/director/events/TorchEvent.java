package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A torch you placed in a cave is gone when you come back. Only in caves, never near your
 * bed, never where you can see it happen.
 */
public final class TorchEvent extends HorrorEvent {
	public TorchEvent() {
		super("torch_gone", Tier.MINOR, 1, 5, 20);
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
		BlockPos bed = Spots.respawnPos(p);

		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : BlockPos.withinManhattan(p.blockPosition(), 20, 8, 20)) {
			if (torches.size() >= 8) break;
			if (pos.distToCenterSqr(p.position()) < 64) continue;
			if (!Spots.isLoaded(world, pos)) continue;   // it must never pull in a chunk
			if (!WorldBlocks.isTorch(world.getBlockState(pos))) continue;
			if (world.getBrightness(LightLayer.SKY, pos) > 0 || !Spots.isUnderground(world, pos)) continue;
			if (bed != null && bed.distSqr(pos) < 24 * 24) continue;
			if (!Sight.isHidden(p, pos)) continue;
			torches.add(pos.immutable());
		}
		if (torches.isEmpty()) return null;

		int count = Math.min(torches.size(), 1 + ctx.random.nextInt(2));
		for (int i = 0; i < count; i++) {
			BlockPos pos = torches.remove(ctx.random.nextInt(torches.size()));
			world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return new Timeline().at(0, pl -> {
		});
	}
}
