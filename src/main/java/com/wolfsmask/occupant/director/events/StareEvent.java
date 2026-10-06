package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every animal in sight stops what it is doing, turns, and stares at you. The music stops.
 * Nothing else happens. After a few seconds they go back to grazing as if nothing did.
 * <p>
 * Nothing is spawned and nothing is hurt: it is only the world, briefly, paying you attention.
 */
public final class StareEvent extends HorrorEvent {
	public static final String ID = "stare";
	private static final double REACH = 24.0;

	public StareEvent() {
		super(ID, Tier.MINOR, 2, 4, 30);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return !s.inCombat() && !s.busy() && !s.underground() && starers(ctx.player).size() >= 2;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		List<Mob> mobs = starers(ctx.player);
		if (mobs.size() < 2) return null;
		Cues.effect(ctx.player, ScreenEffectPayload.SILENCE, 0, 1.0f);
		return new Stare(mobs, 110 + ctx.random.nextInt(60));
	}

	/** Animals near the player that the player can see. */
	static List<Mob> starers(ServerPlayer player) {
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REACH),
				m -> m.isAlive() && m.getType().getCategory() == MobCategory.CREATURE && Sight.canSeeAnyPart(player, m));
	}

	private static final class Stare implements Sequence {
		private final List<Mob> mobs;
		private final int length;
		private int age;

		Stare(List<Mob> mobs, int length) {
			this.mobs = mobs;
			this.length = length;
		}

		@Override
		public boolean tick(ServerPlayer player) {
			// A beat for them all to turn, as if something told them to.
			if (++age > 6) {
				for (Mob m : mobs) {
					if (!m.isAlive() || m.level() != player.level()) continue;
					m.getNavigation().stop();
					m.getLookControl().setLookAt(player, 40.0f, 40.0f);
				}
			}
			return age < length;
		}
	}
}
