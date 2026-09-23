package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos chest = Spots.nearestBlock(world, p.blockPosition(), 12, 4, pos ->
				world.getBlockState(pos).getBlock() instanceof ChestBlock
						&& pos.distToCenterSqr(p.position()) >= 9
						&& Sight.isHidden(p, pos)
						&& Spots.awayFromOthers(p, Vec3.atCenterOf(pos), 12));
		if (chest == null) return null;

		Vec3 at = Vec3.atCenterOf(chest);
		float pitch = 0.9f + ctx.random.nextFloat() * 0.1f;
		int closeAt = 25 + ctx.random.nextInt(30);
		return new Timeline()
				.at(0, pl -> {
					Cues.sound(pl, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, at, 0.5f, pitch);
					lid(world, chest, 1);
				})
				.at(closeAt, pl -> Cues.sound(pl, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, at, 0.5f, pitch))
				.onEnd(() -> lid(world, chest, 0));
	}

	/** Animate the lid (client-side only; the chest's real state never changes). */
	private static void lid(ServerLevel world, BlockPos pos, int viewers) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			world.blockEvent(pos, state.getBlock(), 1, viewers);
		}
	}
}
