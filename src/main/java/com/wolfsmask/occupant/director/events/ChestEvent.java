package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** One of your chests opens behind you. A moment later it closes. Nothing is missing. You think. */
public final class ChestEvent extends HorrorEvent {
	public ChestEvent() {
		super("chest", Tier.AMBIENT, 1, 4, 15);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		BlockPos chest = Spots.nearestBlock(world, p.getBlockPos(), 12, 4, pos ->
				world.getBlockState(pos).getBlock() instanceof ChestBlock
						&& pos.getSquaredDistance(p.getPos()) >= 9
						&& Sight.isHidden(p, pos)
						&& Spots.awayFromOthers(p, Vec3d.ofCenter(pos), 12));
		if (chest == null) return null;

		Vec3d at = Vec3d.ofCenter(chest);
		float pitch = 0.9f + ctx.random.nextFloat() * 0.1f;
		int closeAt = 25 + ctx.random.nextInt(30);
		return new Timeline()
				.at(0, pl -> {
					Cues.sound(pl, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, at, 0.5f, pitch);
					lid(world, chest, 1);
				})
				.at(closeAt, pl -> Cues.sound(pl, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, at, 0.5f, pitch))
				.onEnd(() -> lid(world, chest, 0));
	}

	/** Animate the lid (client-side only; the chest's real state never changes). */
	private static void lid(ServerWorld world, BlockPos pos, int viewers) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			world.addSyncedBlockEvent(pos, state.getBlock(), 1, viewers);
		}
	}
}
