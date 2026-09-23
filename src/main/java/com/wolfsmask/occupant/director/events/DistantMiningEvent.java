package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Somebody is mining inside the rock nearby. Block by block. Toward you. */
public final class DistantMiningEvent extends HorrorEvent {
	public DistantMiningEvent() {
		super("distant_mining", Tier.AMBIENT, 1, 7, 12);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.underground() && !ctx.situation.inCombat();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		for (int attempt = 0; attempt < 20; attempt++) {
			Vec3 dir = Sight.rotateY(Sight.flatLook(p), ctx.random.nextDouble() * 360.0);
			double dist = 9 + ctx.random.nextDouble() * 5;
			BlockPos start = BlockPos.containing(p.position().add(dir.scale(dist)).add(0, ctx.random.nextInt(5) - 2, 0));
			if (!Spots.isLoaded(ctx.world, start) || !Spots.isNaturalStone(ctx.world.getBlockState(start))) continue;
			if (!Sight.isHidden(p, start)) continue;

			// A short run of blocks heading toward the player.
			Direction toward = Direction.getApproximateNearest(-dir.x, 0, -dir.z);
			List<BlockPos> blocks = new ArrayList<>();
			int count = 3 + ctx.random.nextInt(3);
			for (int i = 0; i < count; i++) {
				BlockPos b = start.relative(toward, i / 2).above(i % 2);
				blocks.add(b);
			}
			return new MiningSequence(blocks, 6.0);
		}
		return null;
	}
}
