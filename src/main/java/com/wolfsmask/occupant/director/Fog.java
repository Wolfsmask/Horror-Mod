package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.FogLine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * How far each player can see. The fog comes in as the story goes on (eight chunks at first,
 * six by the end), a little further at night, and all the way in, fast, while it is close: it
 * brings the fog with it. The server decides and tells the client; the client eases it in and
 * out, and the game's own fog still wins wherever that is nearer.
 */
public final class Fog {
	/** While it is this close, the fog rolls in round the player. */
	private static final double ROLLS_IN_WITHIN = 28.0;
	/** How near the fog comes when it does: still well behind it, so it is never lost in it. */
	private static final float ROLLED_IN = 64.0f;

	private Fog() {
	}

	/**
	 * Where the fog is thick for this player now, in blocks, or 0 for no fog at all. Never past
	 * what the player's own render distance shows anyway.
	 */
	public static float endFor(ServerPlayer player, Haunt haunt) {
		OccupantConfig cfg = OccupantConfig.get();
		if (!cfg.fog || cfg.fogChunks <= 0) return 0.0f;
		float end = cfg.fogChunks * 16.0f;
		int act = haunt.data.act;
		// Half a chunk nearer with each act after the first: 8, 7.5, 7, 6.5 chunks with the defaults.
		if (cfg.fogClosesIn) end -= 8.0f * Mth.clamp(act - 1, 0, 3);
		if (cfg.fogClosesIn && Compat.level(player).isDarkOutside()) end -= 8.0f;
		if (cfg.fogClosesIn && closeBy(player, haunt)) end = Math.min(end, ROLLED_IN);
		int view = Compat.viewDistance(player);
		if (view > 2) end = Math.min(end, view * 16.0f);
		return Math.max(FogLine.NEAREST, end);
	}

	/** Where the fog begins for this player now, or a long way off if there is none. */
	public static double startFor(ServerPlayer player, Haunt haunt) {
		float end = endFor(player, haunt);
		return end <= 0 ? 4096.0 : FogLine.start(end);
	}

	/** Tells the client when the fog it should show has moved. Every second, from the Director. */
	static void update(ServerPlayer player, Haunt haunt, boolean eligible) {
		float end = eligible ? endFor(player, haunt) : 0.0f;
		if (haunt.fogSent >= 0 && Math.abs(end - haunt.fogSent) < 2.0f) return;
		// Rolling in round it is quick; everything else drifts, over ten seconds.
		boolean rolling = end > 0 && end <= ROLLED_IN + 0.5f && haunt.fogSent > end;
		Cues.effect(player, ScreenEffectPayload.FOG, rolling ? 60 : 200, end);
		haunt.fogSent = end;
	}

	/** It is out, and near: the fog closes round them. */
	private static boolean closeBy(ServerPlayer player, Haunt haunt) {
		if (haunt.active == null) return false;
		OccupantEntity e = haunt.active.occupant();
		return e != null && e.isAlive() && e.level() == player.level() && e.distanceTo(player) < ROLLS_IN_WITHIN;
	}
}
