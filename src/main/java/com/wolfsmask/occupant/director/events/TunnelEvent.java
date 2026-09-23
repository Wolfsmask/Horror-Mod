package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A fresh 1x2 tunnel in a cave wall that was solid a minute ago, and the sound of someone
 * still digging at the far end. Only ever carved through natural stone, never into water or lava.
 */
public final class TunnelEvent extends HorrorEvent {
	public TunnelEvent() {
		super("tunnel", Tier.MINOR, 2, 4, 35);
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

		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos stand = Spots.aroundPlayer(p, ctx.random, 8, 18, 70, 180, false, 3, pos ->
					Sight.isHidden(p, pos) && Sight.isHidden(p, pos.up()));
			if (stand == null) continue;

			List<Direction> dirs = new ArrayList<>(Direction.Type.HORIZONTAL.stream().toList());
			for (int i = dirs.size() - 1; i > 0; i--) {
				int j = ctx.random.nextInt(i + 1);
				Direction tmp = dirs.get(i);
				dirs.set(i, dirs.get(j));
				dirs.set(j, tmp);
			}

			for (Direction dir : dirs) {
				BlockPos mouth = stand.offset(dir);
				if (!Sight.isHidden(p, mouth) || !Sight.isHidden(p, mouth.up())) continue;
				List<BlockPos> plan = plan(world, stand, dir, 5 + ctx.random.nextInt(5));
				if (plan.size() < 8) continue;

				for (BlockPos b : plan) world.setBlockState(b, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

				BlockPos end = plan.get(plan.size() - 2);
				List<BlockPos> sounds = new ArrayList<>();
				sounds.add(end.offset(dir));
				sounds.add(end.offset(dir).up());
				sounds.add(end.offset(dir, 2));
				return new MiningSequence(sounds, 5.0);
			}
		}
		return null;
	}

	/** Blocks to remove (feet and head, alternating), stopping at anything that is not plain stone. */
	private static List<BlockPos> plan(ServerWorld world, BlockPos stand, Direction dir, int length) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 1; i <= length; i++) {
			BlockPos feet = stand.offset(dir, i);
			BlockPos head = feet.up();
			if (!Spots.isLoaded(world, feet)) break;
			if (!Spots.isNaturalStone(world.getBlockState(feet)) || !Spots.isNaturalStone(world.getBlockState(head))) break;
			if (WorldBlocks.touchesFluid(world, feet) || WorldBlocks.touchesFluid(world, head)) break;
			out.add(feet);
			out.add(head);
		}
		return out;
	}
}
