package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Fog;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * The hunt. The music stops. It is standing out there, looking at you. Then it runs, faster than
 * you can, and the fog comes in round you until you can see about as far as a chunk. Run, and once
 * you have put enough between you it is simply not behind you any more: it is somewhere ahead, off
 * to one side of where you are going, coming out of the fog at you. A minute of that and it lets
 * you go. If it reaches you, it has you (see Strike).
 */
public final class HuntEvent extends HorrorEvent {
	private static final double CHASE_SPEED = 1.6;

	public HuntEvent() {
		super("hunt", Tier.PEAK, 3, 5, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.chases;
	}

	@Override
	public boolean shows() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && !s.inCombat() && !s.busy() && !s.inWater() && !s.sheltered()
				&& (s.gloomy() || ctx.attack) && ctx.player.getHealth() > 8.0f;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		boolean underground = ctx.situation.underground();
		double[] band = Fog.fit(p, ctx.haunt, underground ? 14 : 22, underground ? 22 : 32, 8, 12);
		double min = band[0];
		double max = band[1];
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, min, max, 0, 35, !underground, 40, pos -> {
			Vec3 base = Vec3.atBottomCenterOf(pos);
			return Math.abs(pos.getY() - p.getBlockY()) <= 6
					&& ctx.throughFog(pos)
					&& (ctx.attack || Spots.isDark(ctx.world, pos.above()))     // come to hurt them, by day too
					&& Spots.awayFromOthers(p, base, 24)
					&& Sight.hasLineOfSight(p, base.add(0, 1.6, 0))
					&& Sight.hasLineOfSight(p, base.add(0, 0.9, 0));
		});
		if (spot == null) return null;

		OccupantEntity.Form form = ctx.random.nextFloat() < 0.8f ? OccupantEntity.Form.REVEALED : OccupantEntity.Form.VEILED;
		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, form);
		if (e == null) return null;
		return new Hunt(ctx.haunt, e, 50 + ctx.random.nextInt(30), ctx.attack, null);
	}

	/**
	 * What happens when it reaches them: by default it has them (Strike); for hiding, given here,
	 * whatever the hiding comes to.
	 */
	public interface Caught {
		/** One tick of it, from the tick it reached them; false once it is over. */
		boolean tick(ServerPlayer p, OccupantEntity entity, int sinceCaught);
	}

	/**
	 * The chase, from it standing there to it reaching them or letting them go. Also run for the
	 * hiding, which starts it already running (no stare) and gives what happens at the end.
	 */
	public static final class Hunt extends ApparitionSequence {
		/** How close the fog comes in while it runs: about a chunk. */
		private static final float CHASE_FOG = 18.0f;
		/** The longest it runs before letting them go. */
		private static final int MOST = 20 * 60;
		/** Never cut them off twice in less than this. */
		private static final int CUT_OFF_EVERY = 50;

		@Override
		protected int stareLimit() {
			return Integer.MAX_VALUE;                      // being looked at is how the hunt begins
		}

		private final int stareTicks;
		private boolean chasing;
		private int chaseTicks;
		private int noSight;
		private int stuck;
		private int caughtAt = -1;
		/** Ticks it has actually been there: nothing starts before it is. */
		private int thereFor;
		/** It came to hurt them, and the leg once it reaches them. */
		private final boolean attack;
		@Nullable
		private Strike strike;
		/** What happens at the end instead, for hiding. */
		@Nullable
		private final Caught caught;
		private int cutOffAt = -1000;
		/** Where they were, the last couple of seconds: which way they are going. */
		private final ArrayDeque<Vec3> trail = new ArrayDeque<>();

		public Hunt(Haunt haunt, OccupantEntity entity, int stareTicks, boolean attack, @Nullable Caught caught) {
			super(haunt, entity);
			this.stareTicks = stareTicks;
			this.attack = attack;
			this.caught = caught;
			entity.setFootsteps(true);
			entity.setGazeLocked(true);
		}

		@Override
		protected boolean update(ServerPlayer p, boolean looking) {
			if (strike != null) return strike.tick(p);
			if (caughtAt >= 0) {
				if (caught != null) return caught.tick(p, entity, age - caughtAt);
				return age < caughtAt + 3;
			}
			double dist = entity.distanceTo(p);
			trail.addLast(p.position());
			while (trail.size() > 40) trail.removeFirst();

			if (!chasing) {
				if (age == 1) {
					Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
					Cues.soundAtEars(p, ModSounds.DRONE, SoundSource.AMBIENT, 0.8f, 0.9f);
					Cues.whisper(p, "run", 40);
				}
				if (entity.isConcealed()) return age < 400;
				thereFor++;
				if (thereFor >= stareTicks || (seen && lookTicks > 15) || dist < 6) startChase(p);
				return age < 400;
			}

			chaseTicks++;
			if (chaseTicks % 5 == 1) entity.chase(p, CHASE_SPEED);
			if (chaseTicks % 18 == 0) {
				Cues.sound(p, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, p.getEyePosition(), 0.8f, 1.1f);
			}
			// The dark comes and goes in waves while it is after them.
			if (chaseTicks % 100 == 1) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));

			if (dist < 1.7) {
				if (caught != null) {
					caughtAt = age;
					entity.halt();
					return caught.tick(p, entity, 0);
				}
				if (OccupantConfig.get().attacks) {
					strike = new Strike(haunt, entity);
					return strike.tick(p);
				}
				blackout(p);
				return true;
			}

			noSight = Sight.canSeeAnyPart(p, entity) ? 0 : noSight + 1;
			stuck = (!entity.isPathing() && dist > 3) ? stuck + 1 : 0;
			// Out of reach, and out of sight or a long way back, or with no way to them: it is not
			// behind them any more. It is ahead.
			boolean behindThem = dist > 16 && (noSight > 15 || dist > 24);
			if ((behindThem || stuck > 30) && age - cutOffAt >= CUT_OFF_EVERY && cutOff(p)) stuck = 0;
			return chaseTicks < MOST;
		}

		private void startChase(ServerPlayer p) {
			chasing = true;
			entity.setMode(OccupantEntity.Mode.CHASE);
			haunt.closeFog(CHASE_FOG);
			// The moment it starts running should be a drop in sound, not a bang.
			Cues.sound(p, ModSounds.STATIC, SoundSource.HOSTILE, entity.getEyePosition(), 0.5f, 0.8f);
			Cues.effect(p, ScreenEffectPayload.STATIC, 10, 0.4f);
		}

		/** For hiding: running already, from the first tick it is there. */
		public void runAtOnce() {
			thereFor = stareTicks;
		}

		/**
		 * Somewhere ahead of them, off to one side of the way they are going (never dead ahead), out
		 * in the fog where it cannot be seen: it is there, and coming. False if nowhere would do.
		 */
		private boolean cutOff(ServerPlayer p) {
			ServerLevel world = Compat.level(p);
			Vec3 now = p.position();
			Vec3 then = trail.isEmpty() ? now : trail.peekFirst();
			Vec3 going = new Vec3(now.x - then.x, 0.0, now.z - then.z);
			Vec3 dir = going.length() > 1.0 ? going.normalize() : Sight.flatLook(p);
			float fog = Fog.endFor(p, haunt);
			double far = (fog > 0 ? Math.min(fog, 40.0f) : 24.0) + 3.0;
			for (int attempt = 0; attempt < 14; attempt++) {
				double side = p.getRandom().nextBoolean() ? 1.0 : -1.0;
				double angle = side * (10.0 + p.getRandom().nextDouble() * 20.0);
				Vec3 at = now.add(Sight.rotateY(dir, angle).scale(far + p.getRandom().nextDouble() * 5.0));
				BlockPos feet = Spots.groundNear(world, Mth.floor(at.x), p.getBlockY(), Mth.floor(at.z), 8);
				if (feet == null || Math.abs(feet.getY() - p.getBlockY()) > 8) continue;
				Vec3 base = Vec3.atBottomCenterOf(feet);
				// Out in the fog, or behind something: never where they could see it arrive.
				boolean unseen = base.distanceTo(p.position()) > fog - 1.0 || Sight.isHidden(p, feet.above());
				if (fog > 0 && !unseen) continue;
				if (fog <= 0 && !Sight.isHidden(p, feet.above())) continue;
				float yaw = Sight.yawBetween(base, p.position());
				entity.snapTo(base.x, base.y, base.z, yaw, 0.0f);
				entity.setYHeadRot(yaw);
				entity.setYBodyRot(yaw);
				entity.getNavigation().stop();
				entity.chase(p, CHASE_SPEED);
				cutOffAt = age;
				noSight = 0;
				return true;
			}
			return false;
		}

		@Override
		public void end() {
			if (strike != null) strike.release();
			haunt.releaseFog();
			// They got away from it, when it had come to hurt them: it will not wait as long next time.
			if (attack && caught == null && (strike == null || !strike.landed())) Director.attackSoon(haunt.data);
			super.end();
		}

		/** With hurting them turned off: it reaches them and everything simply stops. */
		private void blackout(ServerPlayer p) {
			caughtAt = age;
			entity.halt();
			haunt.data.encounters++;
			Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.6f, 0.7f);
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 30, 1f);
			Cues.whisper(p, 80);
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
		}
	}
}
