package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
		ServerPlayerEntity p = ctx.player;
		for (int attempt = 0; attempt < 20; attempt++) {
			Vec3d dir = Sight.rotateY(Sight.flatLook(p), ctx.random.nextDouble() * 360.0);
			double dist = 9 + ctx.random.nextDouble() * 5;
			BlockPos start = BlockPos.ofFloored(p.getPos().add(dir.multiply(dist)).add(0, ctx.random.nextInt(5) - 2, 0));
			if (!Spots.isLoaded(ctx.world, start) || !Spots.isNaturalStone(ctx.world.getBlockState(start))) continue;
			if (!Sight.isHidden(p, start)) continue;

			// A short run of blocks heading toward the player.
			Direction toward = Direction.getFacing(-dir.x, 0, -dir.z);
			List<BlockPos> blocks = new ArrayList<>();
			int count = 3 + ctx.random.nextInt(3);
			for (int i = 0; i < count; i++) {
				BlockPos b = start.offset(toward, i / 2).up(i % 2);
				blocks.add(b);
			}
			return new MiningSequence(blocks, 6.0);
		}
		return null;
	}
}
