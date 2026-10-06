package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where it has snowed: a line of footprints across the snow, out of the trees, straight to your
 * door. They stop at the door. None lead away.
 */
public final class FootprintsEvent extends HorrorEvent {
	public static final String ID = "footprints";

	public FootprintsEvent() {
		super(ID, Tier.MINOR, 2, 5, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.sheltered() && !ctx.situation.inCombat();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos door = Spots.nearestBlock(world, p.blockPosition(), 16, 3, pos -> WorldBlocks.isClosedWoodenDoor(world, pos));
		if (door == null) return null;
		Direction facing = world.getBlockState(door).getValue(DoorBlock.FACING);
		BlockPos a = door.relative(facing), b = door.relative(facing.getOpposite());
		Direction out = a.distToCenterSqr(p.position()) > b.distToCenterSqr(p.position()) ? facing : facing.getOpposite();
		Direction side = out.getClockWise();
		List<BlockPos> prints = new ArrayList<>();
		for (int k = 2; k <= 18; k++) {
			BlockPos col = door.relative(out, k).relative(side, (k & 1) == 0 ? 0 : 1);
			if (!Spots.isLoaded(world, col)) break;
			int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
			BlockPos snow = new BlockPos(col.getX(), top, col.getZ());
			if (!world.getBlockState(snow).is(Blocks.SNOW)) {
				snow = snow.below();
				if (!world.getBlockState(snow).is(Blocks.SNOW)) continue;
			}
			if (!Sight.isHidden(p, snow)) continue;
			prints.add(snow);
		}
		if (prints.size() < 5) return null;
		for (BlockPos pos : prints) world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return new Timeline().at(0, pl -> {
		});
	}
}
