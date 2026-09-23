package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
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
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		BlockPos bed = p.getSpawnPointPosition();

		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterateOutwards(p.getBlockPos(), 20, 8, 20)) {
			if (torches.size() >= 8) break;
			if (pos.getSquaredDistance(p.getPos()) < 64) continue;
			if (!WorldBlocks.isTorch(world.getBlockState(pos))) continue;
			if (world.getLightLevel(LightType.SKY, pos) > 0 || !Spots.isUnderground(world, pos)) continue;
			if (bed != null && bed.getSquaredDistance(pos) < 24 * 24) continue;
			if (!Sight.isHidden(p, pos)) continue;
			torches.add(pos.toImmutable());
		}
		if (torches.isEmpty()) return null;

		int count = Math.min(torches.size(), 1 + ctx.random.nextInt(2));
		for (int i = 0; i < count; i++) {
			BlockPos pos = torches.remove(ctx.random.nextInt(torches.size()));
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
		return new Timeline().at(0, pl -> {
		});
	}
}
