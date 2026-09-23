package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.network.ServerPlayerEntity;

/** Base for every sequence that puts the Occupant in the world. Always removes it at the end. */
public abstract class ApparitionSequence implements Sequence {
	protected final Haunt haunt;
	protected final OccupantEntity entity;
	protected int age;
	/** The player has looked right at it at least once. */
	protected boolean seen;
	/** Consecutive ticks the player has been looking right at it. */
	protected int lookTicks;

	protected ApparitionSequence(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	@Override
	public final boolean tick(ServerPlayerEntity player) {
		if (entity.hasVanished() || player.getWorld() != entity.getWorld()) return false;
		entity.keepAlive();
		age++;

		boolean looking = Sight.isLookingAt(player, entity);
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

	/** @return false to end (the entity then vanishes) */
	protected abstract boolean update(ServerPlayerEntity player, boolean looking);

	protected void onSeen(ServerPlayerEntity player) {
	}

	@Override
	public void end() {
		entity.vanish();
	}
}
