package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * They wake, and some of the lights in the house are wrong: a torch they put up is a red one now,
 * dim, the colour of something going out. They never had one of those. Only round where they
 * sleep, only ones they could not see while it was done.
 */
public final class RedLightEvent extends HorrorEvent {
	public static final String ID = "red_lights";

	public RedLightEvent() {
		super(ID, Tier.MINOR, 3, 1, 120);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean hookOnly() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.sheltered() && !ctx.data.isOnCooldown(ID);
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : Compat.withinManhattan(p.blockPosition(), 10, 4, 10)) {
			if (!Spots.isLoaded(world, pos)) continue;
			BlockState s = world.getBlockState(pos);
			if (!s.is(Blocks.TORCH) && !s.is(Blocks.WALL_TORCH)) continue;
			if (!Sight.isHidden(p, pos)) continue;
			torches.add(pos.immutable());
			if (torches.size() >= 12) break;
		}
		if (torches.isEmpty()) return null;
		int count = Math.min(torches.size(), 1 + ctx.random.nextInt(3));
		for (int i = 0; i < count; i++) {
			BlockPos pos = torches.remove(ctx.random.nextInt(torches.size()));
			BlockState s = world.getBlockState(pos);
			BlockState red = (s.is(Blocks.WALL_TORCH) ? Blocks.REDSTONE_WALL_TORCH : Blocks.REDSTONE_TORCH).defaultBlockState();
			// On the same wall, facing the same way.
			if (s.hasProperty(HorizontalDirectionalBlock.FACING) && red.hasProperty(HorizontalDirectionalBlock.FACING)) {
				red = red.setValue(HorizontalDirectionalBlock.FACING, s.getValue(HorizontalDirectionalBlock.FACING));
			}
			if (red.canSurvive(world, pos)) world.setBlock(pos, red, Block.UPDATE_ALL);
		}
		return new Timeline().at(0, pl -> {
		});
	}
}
