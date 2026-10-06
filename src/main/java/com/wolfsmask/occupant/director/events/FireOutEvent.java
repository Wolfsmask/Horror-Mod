package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * At night, a fire near you goes out: a campfire, or the candles, somewhere you were not looking.
 * A soft hiss, and that corner is dark. Nothing else; it can be lit again.
 */
public final class FireOutEvent extends HorrorEvent {
	public static final String ID = "fire_out";

	public FireOutEvent() {
		super(ID, Tier.MINOR, 2, 5, 20);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.night() && !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos fire = null;
		for (BlockPos pos : Compat.withinManhattan(p.blockPosition(), 14, 5, 14)) {
			if (pos.distToCenterSqr(p.position()) < 9 || !Spots.isLoaded(world, pos)) continue;
			BlockState state = world.getBlockState(pos);
			if (!lit(state) || !Sight.isHidden(p, pos)) continue;
			fire = pos.immutable();
			break;
		}
		if (fire == null) return null;
		BlockPos at = fire;
		world.setBlock(at, world.getBlockState(at).setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
		return new Timeline().at(0, pl -> Cues.sound(pl, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
				Vec3.atCenterOf(at), 0.5f, 1.4f));
	}

	/** A lit campfire or candle: things that are meant to go out, and can be lit again. */
	static boolean lit(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) return false;
		String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return name.contains("campfire") || name.endsWith("candle");
	}
}
