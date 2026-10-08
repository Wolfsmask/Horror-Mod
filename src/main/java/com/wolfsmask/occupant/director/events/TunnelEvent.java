package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;

		// Only beside a wall, somewhere it could have dug in from, out of sight behind the player.
		List<BlockPos> stands = new ArrayList<>(Spots.allAroundPlayer(p, 8, 18, 70, 180, pos ->
				besideStone(world, pos) && Sight.isHidden(p, pos) && Sight.isHidden(p, pos.above())));
		for (int i = stands.size() - 1; i > 0; i--) {
			int j = ctx.random.nextInt(i + 1);
			BlockPos tmp = stands.get(i);
			stands.set(i, stands.get(j));
			stands.set(j, tmp);
		}
		for (int attempt = 0; attempt < Math.min(20, stands.size()); attempt++) {
			BlockPos stand = stands.get(attempt);

			List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
			for (int i = dirs.size() - 1; i > 0; i--) {
				int j = ctx.random.nextInt(i + 1);
				Direction tmp = dirs.get(i);
				dirs.set(i, dirs.get(j));
				dirs.set(j, tmp);
			}

			for (Direction dir : dirs) {
				BlockPos mouth = stand.relative(dir);
				if (!Sight.isHidden(p, mouth) || !Sight.isHidden(p, mouth.above())) continue;
				List<BlockPos> plan = plan(world, stand, dir, 5 + ctx.random.nextInt(5));
				if (plan.size() < 8) continue;

				for (BlockPos b : plan) world.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

				BlockPos end = plan.get(plan.size() - 2);
				List<BlockPos> sounds = new ArrayList<>();
				sounds.add(end.relative(dir));
				sounds.add(end.relative(dir).above());
				sounds.add(end.relative(dir, 2));
				return new MiningSequence(sounds, 5.0);
			}
		}
		return null;
	}

	private static boolean besideStone(ServerLevel world, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(dir);
			if (Spots.isNaturalStone(world.getBlockState(side)) && Spots.isNaturalStone(world.getBlockState(side.above()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Blocks to remove (feet and head, alternating), stopping at anything that is not plain stone,
	 * and before it would break through into any other space: a dead end, always, never a way into
	 * a room someone dug.
	 */
	private static List<BlockPos> plan(ServerLevel world, BlockPos stand, Direction dir, int length) {
		List<BlockPos> out = new ArrayList<>();
		Direction left = dir.getCounterClockWise(), right = dir.getClockWise();
		for (int i = 1; i <= length; i++) {
			BlockPos feet = stand.relative(dir, i);
			BlockPos head = feet.above();
			if (!Spots.isLoaded(world, feet) || !Spots.isLoaded(world, feet.relative(dir))) break;
			if (!Spots.isNaturalStone(world.getBlockState(feet)) || !Spots.isNaturalStone(world.getBlockState(head))) break;
			if (WorldBlocks.touchesFluid(world, feet) || WorldBlocks.touchesFluid(world, head)) break;
			// Walled in on every side but the way it came: ahead, to each side, above and below. At
			// the mouth, the cave itself may be beside it (a cave wall is never flat): space there
			// counts only if it is not open to the cave as well.
			if (open(world, feet.relative(dir)) || open(world, head.relative(dir))) break;
			boolean mouth = i == 1;
			if (elsewhere(world, feet.relative(left), dir, mouth) || elsewhere(world, feet.relative(right), dir, mouth)
					|| elsewhere(world, head.relative(left), dir, mouth) || elsewhere(world, head.relative(right), dir, mouth)
					|| elsewhere(world, head.above(), dir, mouth) || elsewhere(world, feet.below(), dir, mouth)) break;
			out.add(feet);
			out.add(head);
		}
		return out;
	}

	/** Nothing solid there: a space the tunnel would open into. */
	private static boolean open(ServerLevel world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	/**
	 * Open, and not just the cave it was dug from: at the mouth, a space with the cave right behind
	 * it is the same cave.
	 */
	private static boolean elsewhere(ServerLevel world, BlockPos pos, Direction dir, boolean mouth) {
		return open(world, pos) && !(mouth && open(world, pos.relative(dir.getOpposite())));
	}
}
