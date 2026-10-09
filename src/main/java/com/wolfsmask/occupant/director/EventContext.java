package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.OccupantConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Everything an event needs to decide whether, where and how to happen. */
public final class EventContext {
	public final ServerPlayer player;
	public final ServerLevel world;
	public final Haunt haunt;
	public final HauntData data;
	public final Situation situation;
	public final RandomSource random;
	public final OccupantConfig config;
	/** Triggered by a command: situation checks are skipped, but physical placement checks never are. */
	public final boolean forced;
	/**
	 * It is due to hurt them (see Director): the events it can do that in (the hunt, behind you)
	 * happen by day as well as night, and end with its leg in them, not only a fright.
	 */
	public boolean attack;
	/** How far off it can be made out through the fog, once worked out; below 0 until then. */
	private double seen = -1.0;

	public EventContext(ServerPlayer player, Haunt haunt, Situation situation, boolean forced) {
		this.player = player;
		this.world = Compat.level(player);
		this.haunt = haunt;
		this.data = haunt.data;
		this.situation = situation;
		this.random = player.getRandom();
		this.config = OccupantConfig.get();
		this.forced = forced;
	}

	public int act() {
		return data.act;
	}

	/**
	 * Could it stand at {@code feet} and still be made out through the fog? For the spots an
	 * event looks at, so a spot on a hillside past the fog is passed over and the search goes on,
	 * rather than being chosen and then turned down.
	 */
	public boolean throughFog(BlockPos feet) {
		if (seen < 0) seen = Fog.seenUpTo(player, haunt);
		return Vec3.atBottomCenterOf(feet).distanceTo(player.position()) <= seen;
	}

	/** Visual encounters need the player to be alone (unless disabled in the config). */
	public boolean aloneEnough() {
		return forced || !config.requireAlone || situation.alone();
	}
}
