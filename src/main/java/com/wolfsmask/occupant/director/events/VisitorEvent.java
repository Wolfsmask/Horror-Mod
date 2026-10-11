package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * They wake, and one of the animals from outside is in the room with them: a sheep, a cow, by the
 * bed, perfectly still, looking at them. Nobody let it in. After a while it moves again, as if
 * nothing were the matter, and wanders off to find the way out.
 */
public final class VisitorEvent extends HorrorEvent {
	public static final String ID = "visitor";
	/** How long it stands there, still, looking at them. */
	private static final int STILL_FOR = 20 * 9;

	public VisitorEvent() {
		super(ID, Tier.MINOR, 2, 1, 60);
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
		ServerLevel level = ctx.world;
		// One of the animals out there, nobody's: the wild ones only, out of their sight.
		List<Mob> animals = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48.0), m -> m.isAlive()
				&& m.getType().getCategory() == MobCategory.CREATURE && !m.isBaby() && ApparitionSequence.wild(m)
				&& m.distanceTo(p) > 8.0 && Sight.isHidden(p, m.blockPosition().above()));
		if (animals.isEmpty()) return null;
		Mob animal = animals.get(ctx.random.nextInt(animals.size()));
		BlockPos spot = inside(level, p, animal, ctx);
		if (spot == null) return null;
		Vec3 at = Vec3.atBottomCenterOf(spot);
		float yaw = Sight.yawBetween(at, p.position());
		animal.getNavigation().stop();
		animal.snapTo(at.x, at.y, at.z, yaw, 0.0f);
		animal.setYHeadRot(yaw);
		animal.setYBodyRot(yaw);
		animal.setNoAi(true);
		return new Still(animal);
	}

	/** Somewhere in the room with them, a few steps off, in their sight, with room for it to stand. */
	@Nullable
	private static BlockPos inside(ServerLevel level, ServerPlayer p, Mob animal, EventContext ctx) {
		BlockPos at = p.blockPosition();
		for (int tries = 0; tries < 30; tries++) {
			int dx = ctx.random.nextInt(9) - 4, dz = ctx.random.nextInt(9) - 4;
			if (Math.abs(dx) + Math.abs(dz) < 2) continue;
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos feet = at.offset(dx, dy, dz);
				if (!Spots.isLoaded(level, feet) || level.canSeeSky(feet)) continue;
				if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
				AABB box = animal.getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(feet));
				if (!level.noCollision(animal, box) || !level.getFluidState(feet).isEmpty()) continue;
				if (!Sight.hasLineOfSight(p, Vec3.atCenterOf(feet))) continue;
				return feet;
			}
		}
		return null;
	}

	/** It stands there, looking at them; then it is only an animal again. */
	private static final class Still implements Sequence {
		private final Mob animal;
		private int t;

		Still(Mob animal) {
			this.animal = animal;
		}

		@Override
		public boolean tick(ServerPlayer player) {
			if (!animal.isAlive() || animal.level() != player.level()) return false;
			// Its eyes on them wherever they go in the room.
			Vec3 eye = player.getEyePosition();
			double dx = eye.x - animal.getX(), dz = eye.z - animal.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			animal.setYHeadRot(yaw);
			animal.setYBodyRot(yaw);
			animal.setYRot(yaw);
			return ++t < STILL_FOR;
		}

		@Override
		public void end() {
			// However it ends: never left standing there for good.
			animal.setNoAi(false);
		}
	}
}
