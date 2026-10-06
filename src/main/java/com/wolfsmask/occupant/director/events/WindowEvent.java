package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Night, and you are indoors. Something is standing right outside one of your windows, a step
 * back from the glass, looking in. It was not there a moment ago, and it will not be there when
 * you go to the window: when you look away, or come close, it is gone. Once, in the last act, it
 * taps on the glass first.
 */
public final class WindowEvent extends HorrorEvent {
	public static final String ID = "window";
	private static final int REACH = 10;

	public WindowEvent() {
		super(ID, Tier.MAJOR, 2, 9, 12);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && s.sheltered() && !s.underground() && !s.inCombat() && !s.busy() && s.gloomy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos feet = p.blockPosition();
		BlockPos best = null;
		BlockPos glass = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos g : BlockPos.betweenClosed(feet.offset(-REACH, -1, -REACH), feet.offset(REACH, 3, REACH))) {
			if (!isGlass(world.getBlockState(g))) continue;
			Vec3 centre = Vec3.atCenterOf(g);
			double dist = centre.distanceTo(p.getEyePosition());
			if (dist < 2.5 || dist > REACH) continue;
			// The glass itself has to be in view from where they stand: a window of this room.
			BlockHitResult hit = world.clip(new ClipContext(p.getEyePosition(), centre, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, p));
			if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(g)) continue;
			// Outwards: away from the player, along whichever way the window faces most.
			Vec3 away = centre.subtract(p.getEyePosition());
			Direction out = Math.abs(away.x) > Math.abs(away.z)
					? (away.x > 0 ? Direction.EAST : Direction.WEST)
					: (away.z > 0 ? Direction.SOUTH : Direction.NORTH);
			BlockPos outside = g.relative(out);
			if (!world.getBlockState(outside).getCollisionShape(world, outside).isEmpty()) continue;
			// A step back from the glass, on the ground, in the dark, with its face about level with the window.
			BlockPos stand = Spots.groundNear(world, outside.relative(out).getX(), g.getY() - 1, outside.relative(out).getZ(), 2);
			if (stand == null || stand.getY() > g.getY() || stand.getY() < g.getY() - 3) continue;
			if (!Spots.isDark(world, stand.above()) || !Spots.awayFromOthers(p, Vec3.atBottomCenterOf(stand), 16)) continue;
			double score = Math.abs(dist - 6.0) + ctx.random.nextDouble() * 2.0;
			if (score < bestScore) {
				bestScore = score;
				best = stand.immutable();
				glass = g.immutable();
			}
		}
		if (best == null) return null;
		OccupantEntity e = ctx.haunt.spawnOccupant(p, best, OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
		if (e == null) return null;
		e.setFootsteps(false);
		e.setGazeLocked(true);
		boolean taps = ctx.act() >= 4 && ctx.random.nextFloat() < 0.5f;
		return new AtTheWindow(ctx.haunt, e, Vec3.atCenterOf(glass), taps);
	}

	/** Glass of any kind, and panes of it: anything the game calls glass. */
	static boolean isGlass(BlockState state) {
		return !state.isAir() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	private static final class AtTheWindow extends ApparitionSequence {
		private final Vec3 glass;
		private final boolean taps;
		private int seenFor;

		AtTheWindow(Haunt haunt, OccupantEntity entity, Vec3 glass, boolean taps) {
			super(haunt, entity);
			this.glass = glass;
			this.taps = taps;
		}

		@Override
		protected void onSeen(ServerPlayer player) {
			haunt.data.addDread(6f);
			if (taps) {
				Cues.sound(player, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, glass, 0.6f, 0.7f);
			}
		}

		@Override
		protected boolean update(ServerPlayer player, boolean looking) {
			if (seen) seenFor++;
			if (taps && seenFor == 12) Cues.sound(player, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, glass, 0.6f, 0.65f);
			// Going to the window never finds it there.
			if (player.getEyePosition().distanceTo(glass) < 2.5) return false;
			// Seen, held a moment: gone the first moment they are not looking.
			if (seen && seenFor > 30 && !looking) return false;
			return seen ? seenFor < 200 : age < 900;
		}
	}
}
