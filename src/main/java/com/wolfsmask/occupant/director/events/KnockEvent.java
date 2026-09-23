package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Night. You are inside. Someone knocks on your door: three slow knocks, from the outside.
 * If you open it, later in the story, something may be standing out there in the dark.
 */
public final class KnockEvent extends HorrorEvent {
	public KnockEvent() {
		super("knock", Tier.MAJOR, 2, 6, 25);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.sheltered() && ctx.situation.night() && !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos door = Spots.nearestBlock(world, p.blockPosition(), 10, 3, pos ->
				WorldBlocks.isClosedWoodenDoor(world, pos) && pos.distToCenterSqr(p.position()) >= 9);
		if (door == null) return null;

		BlockState state = world.getBlockState(door);
		Direction facing = state.getValue(DoorBlock.FACING);
		BlockPos a = door.relative(facing);
		BlockPos b = door.relative(facing.getOpposite());
		Direction outside = a.distToCenterSqr(p.position()) > b.distToCenterSqr(p.position()) ? facing : facing.getOpposite();
		Vec3 knockAt = Vec3.atCenterOf(door).add(0, 0.5, 0).add(Vec3.atLowerCornerOf(outside.getUnitVec3i()).scale(0.45));

		boolean canReveal = ctx.act() >= 3 && ctx.aloneEnough() && ctx.random.nextFloat() < 0.7f;
		return new Knocking(ctx.haunt, door, outside, knockAt, canReveal, 80 + ctx.random.nextInt(60));
	}

	private static final class Knocking implements Sequence {
		private final Haunt haunt;
		private final BlockPos door;
		private final Direction outside;
		private final Vec3 knockAt;
		private final boolean canReveal;
		private final int secondKnock;
		private int age;
		@Nullable
		private WatcherSequence reveal;

		Knocking(Haunt haunt, BlockPos door, Direction outside, Vec3 knockAt, boolean canReveal, int secondKnock) {
			this.haunt = haunt;
			this.door = door;
			this.outside = outside;
			this.knockAt = knockAt;
			this.canReveal = canReveal;
			this.secondKnock = secondKnock;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			if (reveal != null) return reveal.tick(p);
			ServerLevel world = p.level();
			age++;

			if (age == 1) knock(p, 1.0f);
			boolean closed = WorldBlocks.isClosedWoodenDoor(world, door);
			if (age == secondKnock && closed) knock(p, 0.9f);

			if (!closed) {
				if (!WorldBlocks.isOpenDoor(world, door)) return false; // the door is gone
				if (canReveal) tryReveal(p, world);
				return reveal != null;
			}
			return age < 600;
		}

		private void knock(ServerPlayer p, float pitch) {
			Cues.sound(p, ModSounds.KNOCK, SoundSource.BLOCKS, knockAt, 1.0f, pitch);
		}

		/** Whoever knocked is standing out there, a little way off, watching the doorway. */
		private void tryReveal(ServerPlayer p, ServerLevel world) {
			Vec3 out = Vec3.atLowerCornerOf(outside.getUnitVec3i());
			Vec3 side = new Vec3(-out.z, 0, out.x);
			for (int attempt = 0; attempt < 16; attempt++) {
				double dist = 10 + p.getRandom().nextDouble() * 12;
				double lateral = (p.getRandom().nextDouble() - 0.5) * 8;
				Vec3 target = Vec3.atCenterOf(door).add(out.scale(dist)).add(side.scale(lateral));
				BlockPos spot = Spots.groundNear(world, Mth.floor(target.x), door.getY(), Mth.floor(target.z), 4);
				if (spot == null) continue;
				Vec3 base = Vec3.atBottomCenterOf(spot);
				if (!Spots.isDark(world, spot.above())) continue;
				if (!Sight.hasLineOfSight(p, base.add(0, 1.6, 0))) continue;
				OccupantEntity e = haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, haunt.pickForm(p.getRandom()));
				if (e == null) continue;
				reveal = new WatcherSequence(haunt, e, 10 + p.getRandom().nextInt(15), false, 7.0, 400);
				return;
			}
		}

		@Override
		public void end() {
			if (reveal != null) reveal.end();
		}
	}
}
