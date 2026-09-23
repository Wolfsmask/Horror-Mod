package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * It stands and watches. When you notice it, it is gone a moment later: or, sometimes, it waits
 * for you to look away first. You never get close enough to be sure.
 */
public class WatcherSequence extends ApparitionSequence {
	private final int reactionTicks;
	private final boolean waitsForLookAway;
	private final double vanishDistance;
	private final int maxLife;
	private int seenFor;

	public WatcherSequence(Haunt haunt, OccupantEntity entity, int reactionTicks, boolean waitsForLookAway,
						   double vanishDistance, int maxLife) {
		super(haunt, entity);
		this.reactionTicks = reactionTicks;
		this.waitsForLookAway = waitsForLookAway;
		this.vanishDistance = vanishDistance;
		this.maxLife = maxLife;
		entity.setMode(OccupantEntity.Mode.STARE);
		entity.setGazeLocked(true);
		entity.setFootsteps(false);
	}

	@Override
	protected void onSeen(ServerPlayer player) {
		haunt.data.addDread(6f);
		Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
		if (haunt.data.act >= 3) {
			Cues.soundAtEars(player, ModSounds.DRONE, SoundSource.AMBIENT, 0.45f, 1.0f);
		}
	}

	@Override
	protected boolean update(ServerPlayer player, boolean looking) {
		if (entity.distanceTo(player) < vanishDistance) return false;

		if (seen) {
			seenFor++;
			if (waitsForLookAway) {
				boolean onScreen = Sight.isOnScreen(player, entity);
				if (!onScreen && seenFor > 20) return false;
				if (lookTicks > 160) return staticVanish(player);
			} else if (seenFor >= reactionTicks) {
				return looking && haunt.data.act >= 3 ? staticVanish(player) : false;
			}
		}

		if (age > maxLife && !Sight.isOnScreen(player, entity)) return false;
		return age <= maxLife + 600;
	}

	private boolean staticVanish(ServerPlayer player) {
		Cues.effect(player, ScreenEffectPayload.STATIC, 6, 0.3f);
		return false;
	}
}
