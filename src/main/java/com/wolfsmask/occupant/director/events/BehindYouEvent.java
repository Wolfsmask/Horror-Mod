package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * It is standing right behind you. After a moment you hear it breathe.
 * When you turn around: face to face, and the screen cuts to black. When the picture comes back,
 * there is nothing there.
 * <p>
 * From the third act, when it is due to hurt you (see Director), it does not wait to be seen: it
 * keeps close behind you, breathes, and when you turn round, or soon if you never do, its leg
 * goes through you (see Strike). Indoors or out, by day or night.
 */
public final class BehindYouEvent extends HorrorEvent {
	public BehindYouEvent() {
		super("behind_you", Tier.PEAK, 3, 6, 25);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.jumpscares;
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && ctx.player.onGround() && !s.sprinting() && !s.inCombat() && !s.busy()
				&& !s.inWater() && (ctx.attack || s.sheltered() || s.gloomy());
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		Vec3 back = Sight.flatLook(p).scale(-1);
		for (int attempt = 0; attempt < 12; attempt++) {
			Vec3 dir = Sight.rotateY(back, (ctx.random.nextDouble() - 0.5) * 50.0);
			Vec3 at = p.position().add(dir.scale(1.7 + ctx.random.nextDouble() * 0.8));
			BlockPos feet = Spots.groundNear(ctx.world, Mth.floor(at.x), Mth.floor(p.getY()), Mth.floor(at.z), 1);
			if (feet == null) continue;
			Vec3 head = Vec3.atBottomCenterOf(feet).add(0, 1.7, 0);
			if (Sight.angleTo(p, head) < 110.0 || !Sight.hasLineOfSight(p, head)) continue;

			OccupantEntity e = ctx.haunt.spawnOccupant(p, feet, OccupantEntity.Mode.AMBUSH, OccupantEntity.Form.REVEALED);
			if (e == null) continue;
			return new Ambush(ctx.haunt, e, ctx.attack);
		}
		return null;
	}

	private static final class Ambush extends ApparitionSequence {
		private int scaredAt = -1;
		/** It came to hurt them, and the leg once it does. */
		private final boolean attack;
		@Nullable
		private Strike strike;
		/** When it was really there, behind them; -1 before then. */
		private int thereAt = -1;
		/** When they turned and saw it; -1 if they have not. */
		private int seenAt = -1;
		/** As far off as a leg reaches somebody from, with a little to spare: no nearer, no strike. */
		private static final double REACH = 2.6;

		Ambush(Haunt haunt, OccupantEntity entity, boolean attack) {
			super(haunt, entity);
			this.attack = attack;
			entity.setFootsteps(false);
			entity.setGazeLocked(true);
		}

		@Override
		protected int stareLimit() {
			return attack ? Integer.MAX_VALUE : super.stareLimit();   // come to hurt them, being seen does not stop it
		}

		@Override
		protected boolean update(ServerPlayer p, boolean looking) {
			if (strike != null) return strike.tick(p);
			if (scaredAt >= 0) return age < scaredAt + 3;
			if (attack) return closeIn(p);
			if (entity.distanceTo(p) > 4.5) return false; // walked away without ever knowing

			if (age == 30) {
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.8f, 0.9f);
			}

			if (!entity.isConcealed() && Sight.angleTo(p, entity.getEyePosition()) <= 55.0 && Sight.canSeeAnyPart(p, entity)) {
				// No stinger. A loud noise makes you jump and then laugh; this should make you
				// stand very still instead. One breath at your ear, and the screen goes quietly out.
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.55f, 0.8f);
				Cues.effect(p, ScreenEffectPayload.STATIC, 22, 0.35f);
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 34, 1f);
				Cues.whisper(p, "it was behind you the whole time", 60);
				haunt.data.encounters++;
				scaredAt = age;
				return true;
			}
			return age < 240;
		}

		/**
		 * Come to hurt them. It keeps close behind them, silently; a breath at their ear; and when
		 * they turn round, or a few seconds after if they never do, the leg. Only ever from where a
		 * leg can reach them: seen from further off, it comes for them first, fast.
		 */
		private boolean closeIn(ServerPlayer p) {
			if (entity.isConcealed()) return age < 300;    // not there yet: it never arrives in sight
			if (thereAt < 0) thereAt = age;
			int there = age - thereAt;
			double dist = entity.distanceTo(p);
			if (there == 12) Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.8f, 0.9f);
			boolean turned = Sight.angleTo(p, entity.getEyePosition()) <= 55.0 && Sight.canSeeAnyPart(p, entity);
			if (turned && seenAt < 0) seenAt = age;
			boolean due = seenAt >= 0 || there >= 60 || dist < 1.2;
			if (due && dist <= REACH) {
				strike = new Strike(haunt, entity);
				return strike.tick(p);
			}
			if (dist > 9.0) return false;                 // they ran: it will not wait as long next time
			// Seen, or done waiting: straight at them. Otherwise it keeps close behind, at a walk.
			if (due && age % 3 == 0) entity.chase(p, 1.6);
			else if (dist > 2.4 && age % 5 == 0) entity.chase(p, 1.15);
			return age < 600;
		}

		@Override
		public void end() {
			if (attack && (strike == null || !strike.landed())) Director.attackSoon(haunt.data);
			super.end();
		}
	}
}
