package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

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

	public EventContext(ServerPlayer player, Haunt haunt, Situation situation, boolean forced) {
		this.player = player;
		this.world = player.level();
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

	/** Visual encounters need the player to be alone (unless disabled in the config). */
	public boolean aloneEnough() {
		return forced || !config.requireAlone || situation.alone();
	}
}
