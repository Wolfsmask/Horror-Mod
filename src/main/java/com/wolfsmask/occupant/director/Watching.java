package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.entity.OccupantEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * It is there for someone near them: while it is, nothing of their own begins. There is only one
 * of it, and they are in the same moment as the one it came for: they see it, they hear it, they
 * watch what it does (see {@link com.wolfsmask.occupant.util.Cues}); their own story waits until
 * it is over, and is quiet for a while after.
 */
final class Watching implements Sequence {
	static final String ID = "watching";

	/** Whose it is, and what it is doing to them. */
	private final Haunt leader;
	private final Sequence scene;
	private final MinecraftServer server;

	Watching(Haunt leader, Sequence scene, MinecraftServer server) {
		this.leader = leader;
		this.scene = scene;
		this.server = server;
	}

	/** Whose scene it is. */
	Haunt leader() {
		return leader;
	}

	@Override
	public boolean tick(ServerPlayer player) {
		if (leader.active != scene) return false;
		ServerPlayer them = server.getPlayerList().getPlayer(leader.uuid);
		// Gone from them (another world, or a long way off): it is not theirs to wait on any more.
		return them != null && them.level() == player.level() && them.distanceTo(player) <= Party.ONE_OF_IT;
	}

	/** It: the fog closes in round them too while it is close. */
	@Override
	@Nullable
	public OccupantEntity occupant() {
		return scene.occupant();
	}
}
