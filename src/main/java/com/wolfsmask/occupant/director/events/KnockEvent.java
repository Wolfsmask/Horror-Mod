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
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		BlockPos door = Spots.nearestBlock(world, p.getBlockPos(), 10, 3, pos ->
				WorldBlocks.isClosedWoodenDoor(world, pos) && pos.getSquaredDistance(p.getPos()) >= 9);
		if (door == null) return null;

		BlockState state = world.getBlockState(door);
		Direction facing = state.get(DoorBlock.FACING);
		BlockPos a = door.offset(facing);
		BlockPos b = door.offset(facing.getOpposite());
		Direction outside = a.getSquaredDistance(p.getPos()) > b.getSquaredDistance(p.getPos()) ? facing : facing.getOpposite();
		Vec3d knockAt = Vec3d.ofCenter(door).add(0, 0.5, 0).add(Vec3d.of(outside.getVector()).multiply(0.45));

		boolean canReveal = ctx.act() >= 3 && ctx.aloneEnough() && ctx.random.nextFloat() < 0.7f;
		return new Knocking(ctx.haunt, door, outside, knockAt, canReveal, 80 + ctx.random.nextInt(60));
	}

	private static final class Knocking implements Sequence {
		private final Haunt haunt;
		private final BlockPos door;
		private final Direction outside;
		private final Vec3d knockAt;
		private final boolean canReveal;
		private final int secondKnock;
		private int age;
		@Nullable
		private WatcherSequence reveal;

		Knocking(Haunt haunt, BlockPos door, Direction outside, Vec3d knockAt, boolean canReveal, int secondKnock) {
			this.haunt = haunt;
			this.door = door;
			this.outside = outside;
			this.knockAt = knockAt;
			this.canReveal = canReveal;
			this.secondKnock = secondKnock;
		}

		@Override
		public boolean tick(ServerPlayerEntity p) {
			if (reveal != null) return reveal.tick(p);
			ServerWorld world = p.getServerWorld();
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

		private void knock(ServerPlayerEntity p, float pitch) {
			Cues.sound(p, ModSounds.KNOCK, SoundCategory.BLOCKS, knockAt, 1.0f, pitch);
		}

		/** Whoever knocked is standing out there, a little way off, watching the doorway. */
		private void tryReveal(ServerPlayerEntity p, ServerWorld world) {
			Vec3d out = Vec3d.of(outside.getVector());
			Vec3d side = new Vec3d(-out.z, 0, out.x);
			for (int attempt = 0; attempt < 16; attempt++) {
				double dist = 10 + p.getRandom().nextDouble() * 12;
				double lateral = (p.getRandom().nextDouble() - 0.5) * 8;
				Vec3d target = Vec3d.ofCenter(door).add(out.multiply(dist)).add(side.multiply(lateral));
				BlockPos spot = Spots.groundNear(world, MathHelper.floor(target.x), door.getY(), MathHelper.floor(target.z), 4);
				if (spot == null) continue;
				Vec3d base = Vec3d.ofBottomCenter(spot);
				if (Spots.light(world, spot.up()) > 7) continue;
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
