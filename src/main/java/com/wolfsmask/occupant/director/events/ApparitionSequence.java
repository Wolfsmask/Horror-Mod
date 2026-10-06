package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Base for every sequence that puts the Occupant in the world. Always removes it at the end. */
public abstract class ApparitionSequence implements Sequence {
	protected final Haunt haunt;
	protected final OccupantEntity entity;
	protected int age;
	/** The player has looked right at it at least once. */
	protected boolean seen;
	/** Consecutive ticks the player has been looking right at it. */
	protected int lookTicks;
	/** The animals near the player, who know it is there. */
	private List<Mob> animals = List.of();
	/** The monsters near it, who want nothing to do with it. */
	private List<Mob> monsters = List.of();

	protected ApparitionSequence(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	@Override
	public final boolean tick(ServerPlayer player) {
		if (entity.hasVanished() || player.level() != entity.level()) return false;
		entity.keepAlive();
		age++;
		animalsNotice(player);

		// Nothing is there to see until it has been revealed.
		boolean looking = !entity.isConcealed() && Sight.isLookingAt(player, entity);
		if (looking) {
			lookTicks++;
			if (!seen) {
				seen = true;
				haunt.data.sightings++;
				onSeen(player);
			}
		} else {
			lookTicks = 0;
		}
		return update(player, looking) && !entity.hasVanished();
	}

	/**
	 * The animals near the player know it is there before the player does: they stop what they
	 * are doing and turn to look at it. Follow their eyes.
	 */
	private void animalsNotice(ServerPlayer player) {
		if (entity.isConcealed()) return;
		if (age % 40 == 1) {
			animals = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0),
					m -> m.isAlive() && m.getType().getCategory() == MobCategory.CREATURE && m.distanceTo(entity) < 72.0);
		}
		for (Mob m : animals) {
			if (!m.isAlive() || m.level() != entity.level()) continue;
			m.getNavigation().stop();
			m.getLookControl().setLookAt(entity, 30.0f, 30.0f);
		}
		// Even the things that come out at night back away from it.
		if (age % 20 == 1) {
			monsters = entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(16.0),
					m -> m.isAlive() && m != entity && !(m instanceof OccupantEntity)
							&& m.getType().getCategory() == MobCategory.MONSTER);
			for (Mob m : monsters) {
				Vec3 away = m.position().subtract(entity.position());
				away = new Vec3(away.x, 0, away.z);
				if (away.lengthSqr() < 1.0E-4) continue;
				away = away.normalize().scale(12.0);
				m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.25);
				m.getLookControl().setLookAt(entity, 30.0f, 30.0f);
			}
		}
	}

	/** @return false to end (the entity then vanishes) */
	protected abstract boolean update(ServerPlayer player, boolean looking);

	protected void onSeen(ServerPlayer player) {
	}

	@Override
	public void end() {
		entity.vanish();
	}

	@Override
	public OccupantEntity occupant() {
		return entity;
	}
}
