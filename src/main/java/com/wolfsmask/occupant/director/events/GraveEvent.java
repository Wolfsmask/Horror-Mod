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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * They wake, and somewhere outside, a little way from where they slept, there is a grave: freshly
 * dug, a body's length and two deep, the earth heaped beside it, and at its head a sign with their
 * name on it. Nobody heard the digging. As they get up, the last of it: one more shovelful,
 * somewhere outside.
 * <p>
 * Only on waking, never while they could watch it being dug; only in soft ground nobody built on.
 */
public final class GraveEvent extends HorrorEvent {
	public static final String ID = "grave";

	public GraveEvent() {
		super(ID, Tier.MAJOR, 3, 1, 600);
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
		return !ctx.situation.underground() && !ctx.data.isOnCooldown(ID);
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos bed = Spots.respawnPos(p);
		BlockPos home = bed != null && bed.distSqr(p.blockPosition()) < 16 * 16 ? bed : p.blockPosition();
		for (int tries = 0; tries < 40; tries++) {
			double angle = ctx.random.nextDouble() * Math.PI * 2;
			double r = 5.0 + ctx.random.nextDouble() * 7.0;
			int x = home.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = home.getZ() + (int) Math.round(Math.sin(angle) * r);
			BlockPos foot = Spots.groundNear(world, x, home.getY(), z, 3);
			if (foot == null) continue;
			Direction along = ctx.random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
			BlockPos head = foot.relative(along);
			if (!diggable(world, foot) || !diggable(world, head)) continue;
			// The sign at its head, the earth heaped along one side.
			BlockPos sign = head.relative(along);
			Direction side = along.getClockWise();
			if (!open(world, sign) || !WorldBlocks.isNaturalFloor(world.getBlockState(sign.below()))) continue;
			// Not where they, or anyone, could see it being dug.
			if (!Sight.isHidden(p, foot) || !Sight.isHidden(p, head) || !Spots.awayFromOthers(p, Vec3.atCenterOf(foot), 12)) continue;
			dig(world, foot, head, side);
			placeSign(world, sign, p, home);
			Vec3 at = Vec3.atCenterOf(foot);
			return new Timeline().at(50, pl -> Cues.sound(pl, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, at, 0.7f, 0.6f))
					.at(64, pl -> Cues.sound(pl, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, at, 0.5f, 0.5f));
		}
		return null;
	}

	/**
	 * Somewhere a grave can be dug: open sky above, soft ground nobody built on two deep, firm
	 * below that (it is two deep, no deeper), and no water near.
	 */
	private static boolean diggable(ServerLevel world, BlockPos feet) {
		if (!Spots.isLoaded(world, feet) || !world.canSeeSky(feet)) return false;
		if (!open(world, feet) || !open(world, feet.above())) return false;
		BlockState top = world.getBlockState(feet.below());
		BlockState under = world.getBlockState(feet.below(2));
		BlockState floor = world.getBlockState(feet.below(3));
		if (!WorldBlocks.isSoftGround(top) && !top.is(Blocks.COARSE_DIRT)) return false;
		if (!WorldBlocks.isNaturalFloor(under) || floor.isAir() || !floor.getFluidState().isEmpty()) return false;
		return !WorldBlocks.touchesFluid(world, feet.below()) && !WorldBlocks.touchesFluid(world, feet.below(2));
	}

	/** Nothing there but air, or grass and flowers that would give way: no water. */
	private static boolean open(ServerLevel world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty();
	}

	private static void dig(ServerLevel world, BlockPos foot, BlockPos head, Direction side) {
		for (BlockPos pos : new BlockPos[]{foot, head}) {
			// The grass on it gone with the turf.
			if (!world.getBlockState(pos).isAir()) world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			world.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			world.setBlock(pos.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			// What came out of it, heaped beside it.
			BlockPos heap = pos.relative(side);
			if (open(world, heap) && WorldBlocks.isNaturalFloor(world.getBlockState(heap.below()))) {
				world.setBlock(heap, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static void placeSign(ServerLevel world, BlockPos at, ServerPlayer p, BlockPos home) {
		// Its words to where they sleep.
		float yaw = Sight.yawBetween(Vec3.atBottomCenterOf(home), Vec3.atBottomCenterOf(at)) + 180.0f;
		BlockState state = Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw));
		if (!state.canSurvive(world, at) || !world.setBlock(at, state, Block.UPDATE_ALL)) return;
		if (world.getBlockEntity(at) instanceof SignBlockEntity sign) {
			long day = Compat.dayTime(world) / 24000L + 1;
			Compat.writeSign(sign, new String[]{"Here lies", p.getName().getString(), "", "Day " + day});
			sign.setWaxed(true);
			sign.setChanged();
			world.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}
}
