package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The finale beat. The music stops. It is standing out there, looking at you.
 * Then it runs. It is about as fast as you sprinting, so you can get away: break line of
 * sight, get behind a door, keep going. If it reaches you, the screen goes black.
 * It never kills you unless the server config says it may hurt you.
 */
public final class HuntEvent extends HorrorEvent {
	private static final double CHASE_SPEED = 1.45;

	public HuntEvent() {
		super("hunt", Tier.PEAK, 4, 5, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.chases;
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && !s.inCombat() && !s.busy() && !s.inWater() && !s.sheltered() && s.gloomy()
				&& ctx.player.getHealth() > 8.0f;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayerEntity p = ctx.player;
		boolean underground = ctx.situation.underground();
		double min = underground ? 14 : 22;
		double max = underground ? 22 : 32;
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, min, max, 0, 35, !underground, 40, pos -> {
			Vec3d base = Vec3d.ofBottomCenter(pos);
			return Math.abs(pos.getY() - p.getBlockY()) <= 6
					&& Spots.light(ctx.world, pos.up()) <= 8
					&& Spots.awayFromOthers(p, base, 24)
					&& Sight.hasLineOfSight(p, base.add(0, 1.6, 0))
					&& Sight.hasLineOfSight(p, base.add(0, 0.9, 0));
		});
		if (spot == null) return null;

		OccupantEntity.Form form = ctx.random.nextFloat() < 0.8f ? OccupantEntity.Form.HOLLOW : OccupantEntity.Form.MIRROR;
		OccupantEntity e = ctx.haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, form);
		if (e == null) return null;
		return new Hunt(ctx.haunt, e, 50 + ctx.random.nextInt(30));
	}

	private static final class Hunt extends ApparitionSequence {
		private final int stareTicks;
		private boolean chasing;
		private int chaseTicks;
		private int noSight;
		private int stuck;
		private int caughtAt = -1;

		Hunt(Haunt haunt, OccupantEntity entity, int stareTicks) {
			super(haunt, entity);
			this.stareTicks = stareTicks;
			entity.setFootsteps(true);
			entity.setGazeLocked(true);
		}

		@Override
		protected boolean update(ServerPlayerEntity p, boolean looking) {
			if (caughtAt >= 0) return age < caughtAt + 3;
			double dist = entity.distanceTo(p);

			if (!chasing) {
				if (age == 1) {
					Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
					Cues.soundAtEars(p, ModSounds.DRONE, SoundCategory.AMBIENT, 0.8f, 0.9f);
				}
				if (age >= stareTicks || (seen && lookTicks > 15) || dist < 6) {
					chasing = true;
					entity.setMode(OccupantEntity.Mode.CHASE);
					Cues.sound(p, ModSounds.STATIC, SoundCategory.HOSTILE, entity.getEyePos(), 1.0f, 0.8f);
					Cues.effect(p, ScreenEffectPayload.STATIC, 10, 0.5f);
				}
				return age < 400;
			}

			chaseTicks++;
			if (chaseTicks % 5 == 1) entity.chase(p, CHASE_SPEED);
			if (chaseTicks % 18 == 0) {
				Cues.sound(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, p.getEyePos(), 1.0f, 1.1f);
			}

			if (dist < 1.7) {
				caught(p);
				return true;
			}

			noSight = Sight.canSeeAnyPart(p, entity) ? 0 : noSight + 1;
			if (noSight > 80 && dist > 14) return false; // it lost you
			if (dist > 56) return false;
			stuck = (!entity.isPathing() && dist > 3) ? stuck + 1 : 0;
			if (stuck > 40) return false;
			return chaseTicks < 400;
		}

		private void caught(ServerPlayerEntity p) {
			caughtAt = age;
			entity.halt();
			haunt.data.encounters++;
			Cues.sound(p, ModSounds.STINGER, SoundCategory.HOSTILE, entity.getEyePos(), 1.0f, 0.9f);
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 30, 1f);
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, false));
			float damage = OccupantConfig.get().chaseDamage;
			if (damage > 0) p.damage(p.getDamageSources().generic(), damage);
		}
	}
}
