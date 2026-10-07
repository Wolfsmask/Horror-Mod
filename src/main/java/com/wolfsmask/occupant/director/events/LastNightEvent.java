package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Fog;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.LastNightEnding;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Pacing;
import com.wolfsmask.occupant.director.WorldMode;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.FogLine;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The last night. Once, late in the story, outdoors after dark: the music stops and the fog
 * closes right in. It is standing at the edge of it. Every time you look away, it is closer when
 * you look back, until it is right in front of you. Then black, and a line, as on the first night.
 * When the picture comes back the fog has lifted and the story begins again, quieter. It is still
 * here.
 */
public final class LastNightEvent extends HorrorEvent {
	public static final String ID = "last_night";
	/** How far the fog comes in for it, at most: as near as it ever comes round it. */
	private static final float FOG = 36.0f;
	/** How close it is each time you look back. */
	private static final double[] CLOSER = {14.0, 10.0, 6.5, 3.5};

	public LastNightEvent() {
		super(ID, Tier.PEAK, 4, 40, 90);
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		HauntData d = ctx.data;
		long inAct = d.playTicks - d.actStartedAt;
		// In the Creator Cut it is always reached: a few minutes into the last act, seen or not.
		boolean creator = WorldMode.creatorCut();
		double minutes = creator ? 4.0 : 12.0;
		return !d.lastNight && inAct >= 20L * 60 * minutes * Pacing.storyPace(ctx.config) && (creator || d.encounters >= 1)
				&& ctx.aloneEnough()
				&& s.night() && !s.sheltered() && !s.underground() && !s.inCombat() && !s.busy() && !s.inWater();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		// The fog comes in, unless it is already nearer than that; and it stands at the edge of it.
		float now = Fog.endFor(p, ctx.haunt);
		float fog = now > 0 ? Math.min(FOG, now) : FOG;
		ctx.haunt.closeFog(fog);
		double far = FogLine.edgeFar(fog) - 1.0;
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, far - 5.0, far, 0, 50, true, 60,
				pos -> Math.abs(pos.getY() - p.getBlockY()) <= 8
						&& Sight.hasLineOfSight(p, Vec3.atBottomCenterOf(pos).add(0, 2.5, 0)));
		OccupantEntity e = spot == null ? null
				: ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, OccupantEntity.Form.REVEALED);
		if (e == null) {
			ctx.haunt.releaseFog();
			return null;
		}
		e.setFootsteps(false);
		e.setGazeLocked(true);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1.0f);
		return new LastNight(ctx.haunt, e);
	}

	private static final class LastNight extends ApparitionSequence {
		private int stage = -1;
		private int seenFor;
		private int awayFor;
		private int endAt = -1;
		private int which;
		/** Flat, from the player towards it, the last time they saw it. */
		@Nullable
		private Vec3 towards;

		LastNight(Haunt haunt, OccupantEntity entity) {
			super(haunt, entity);
		}

		@Override
		protected boolean update(ServerPlayer player, boolean looking) {
			if (endAt >= 0) {
				// In the dark, once the screen is black: wherever the story ends.
				if (age == endAt - 1) {
					entity.discard();
					LastNightEnding.play(player, haunt, which);
				}
				return age < endAt;
			}
			if (age > 20 * 150) return false;               // it gives up, for tonight
			if (looking) {
				seenFor++;
				awayFor = 0;
				Vec3 to = entity.position().subtract(player.position());
				if (to.x * to.x + to.z * to.z > 1.0E-4) towards = new Vec3(to.x, 0, to.z).normalize();
				if (stage == CLOSER.length - 1 && seenFor >= 14) {
					// Face to face. Black, and the line: which line depends on how the story was lived.
					which = LastNightEnding.which(haunt.data);
					Cues.effect(player, ScreenEffectPayload.FINALE, 240, which);
					endAt = age + 20;
				}
				return true;
			}
			// It has to have been seen, properly, before it moves; and then only once they have
			// looked well away.
			if (seenFor < 12 || towards == null || ++awayFor < 8) return true;
			// The next step that is really closer than where it stands now.
			int next = stage + 1;
			double now = entity.distanceTo(player);
			while (next < CLOSER.length - 1 && CLOSER[next] > now - 2.0) next++;
			if (next < CLOSER.length && moveCloser(player, CLOSER[next])) {
				stage = next;
				seenFor = 0;
				awayFor = 0;
			}
			return true;
		}

		/** Where they last saw it, but closer; only somewhere they cannot see it arrive. */
		private boolean moveCloser(ServerPlayer player, double distance) {
			ServerLevel world = Compat.level(player);
			for (int turn : new int[]{0, 12, -12, 25, -25}) {
				Vec3 dir = Sight.rotateY(towards, turn);
				Vec3 want = player.position().add(dir.scale(distance));
				BlockPos feet = Spots.groundNear(world, Mth.floor(want.x), player.getBlockY(), Mth.floor(want.z), 4);
				if (feet == null) continue;
				Vec3 at = Vec3.atBottomCenterOf(feet);
				if (Sight.angleTo(player, at.add(0, 2.0, 0)) < 75.0) return false;   // they would see it move
				float yaw = Sight.yawBetween(at, player.position());
				entity.snapTo(at.x, at.y, at.z, yaw, 0.0f);
				entity.setYHeadRot(yaw);
				entity.setYBodyRot(yaw);
				return true;
			}
			return false;
		}

		@Override
		public void end() {
			super.end();
			haunt.releaseFog();
		}
	}
}
