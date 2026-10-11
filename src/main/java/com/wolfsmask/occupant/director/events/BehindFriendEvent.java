package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Party;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * With a friend: they are facing you, talking, or just standing there, and right behind them, a
 * step back, it is standing too, looking at you over their shoulder. They do not know. If you
 * look long enough, they hear it breathe. If they turn round, it is gone (the light stutters for
 * anyone who had it in sight), and there was never anything there.
 */
public final class BehindFriendEvent extends HorrorEvent {
	public static final String ID = "behind_friend";
	/** How far off the friend can be, and how far back behind them it stands. */
	private static final double NEAREST = 5.0;
	private static final double FURTHEST = 26.0;
	/** The friend must be facing them, about: what is behind the friend, the friend cannot see. */
	private static final double FACING_WITHIN = 55.0;

	public BehindFriendEvent() {
		super(ID, Tier.MAJOR, 2, 9, 20);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return !s.inCombat() && !s.busy() && !s.inWater() && friend(ctx.player) != null;
	}

	/** A friend in sight of them, not too near, facing them. */
	@Nullable
	private static ServerPlayer friend(ServerPlayer p) {
		ServerPlayer best = null;
		double bestAngle = FACING_WITHIN;
		for (ServerPlayer o : Party.others(p, FURTHEST)) {
			if (o.distanceTo(p) < NEAREST || o.isSleeping() || o.isPassenger()) continue;
			double facing = Sight.angleTo(o, p.getEyePosition());
			if (facing > bestAngle || !Sight.canSeeAnyPart(p, o)) continue;
			best = o;
			bestAngle = facing;
		}
		return best;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerPlayer friend = friend(p);
		if (friend == null) return null;
		ServerLevel level = Compat.level(p);
		// Straight back from where the friend is looking, a step or two, a little to one side or the other.
		Vec3 back = Sight.flatLook(friend).scale(-1.0);
		for (int tries = 0; tries < 8; tries++) {
			double d = 1.8 + ctx.random.nextDouble() * 1.4;
			Vec3 at = friend.position().add(Sight.rotateY(back, (ctx.random.nextDouble() - 0.5) * 50.0).scale(d));
			BlockPos feet = Spots.groundNear(level, Mth.floor(at.x), friend.getBlockY(), Mth.floor(at.z), 2);
			if (feet == null || Math.abs(feet.getY() - friend.getBlockY()) > 2) continue;
			// Out of the friend's sight, wherever it is.
			if (Sight.angleTo(friend, Vec3.atCenterOf(feet.above())) < 100.0) continue;
			OccupantEntity e = ctx.haunt.spawnOccupant(p, feet, OccupantEntity.Mode.STARE, ctx.haunt.pickForm(ctx.random));
			if (e == null) continue;
			return new Behind(ctx.haunt, e, friend.getUUID(), level);
		}
		return null;
	}

	private static final class Behind extends ApparitionSequence {
		/** Looked at this long, the one it came for: the friend hears it, just behind them. */
		private static final int BREATHES_AFTER = 30;
		private static final int LONGEST = 20 * 30;
		private final UUID friend;
		private final ServerLevel level;
		private boolean breathed;

		Behind(Haunt haunt, OccupantEntity entity, UUID friend, ServerLevel level) {
			super(haunt, entity);
			this.friend = friend;
			this.level = level;
			entity.setMode(OccupantEntity.Mode.STARE);
			entity.setGazeLocked(true);
			entity.setFootsteps(false);
		}

		/** Long looked at, and it stays: this one wants to be seen, over their friend's shoulder. */
		@Override
		protected int stareLimit() {
			return 140;
		}

		@Override
		protected void onSeen(ServerPlayer player) {
			haunt.data.addDread(8f);
			Cues.effect(player, ScreenEffectPayload.SILENCE, 0, 1f);
		}

		@Override
		protected boolean update(ServerPlayer player, boolean looking) {
			ServerPlayer them = level.getServer().getPlayerList().getPlayer(friend);
			// The friend gone, or turned round to it: gone, and it was never there.
			if (them == null || them.level() != entity.level() || !them.isAlive()) return false;
			if (!entity.isConcealed() && Sight.angleTo(them, entity.getEyePosition()) < 70.0 && Sight.canSeeAnyPart(them, entity)) {
				return false;
			}
			if (seen && !breathed && lookTicks >= BREATHES_AFTER) {
				breathed = true;
				// Right behind the friend's head: they hear it, and do not know what it is.
				Cues.soundOnly(them, ModSounds.BREATH, SoundSource.HOSTILE, them.getEyePosition().add(Sight.flatLook(them).scale(-0.6)), 0.7f, 0.7f);
			}
			return age < LONGEST;
		}
	}
}
