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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * It is standing right behind you. After a moment you hear it breathe.
 * When you turn around: face to face, a stinger, and the screen cuts to black.
 * When the picture comes back, there is nothing there.
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
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && ctx.player.onGround() && !s.sprinting() && !s.inCombat() && !s.busy()
				&& !s.inWater() && (s.sheltered() || s.gloomy());
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

			OccupantEntity e = ctx.haunt.spawnOccupant(p, feet, OccupantEntity.Mode.AMBUSH, OccupantEntity.Form.HOLLOW);
			if (e == null) continue;
			return new Ambush(ctx.haunt, e);
		}
		return null;
	}

	private static final class Ambush extends ApparitionSequence {
		private int scaredAt = -1;

		Ambush(Haunt haunt, OccupantEntity entity) {
			super(haunt, entity);
			entity.setFootsteps(false);
			entity.setGazeLocked(true);
		}

		@Override
		protected boolean update(ServerPlayer p, boolean looking) {
			if (scaredAt >= 0) return age < scaredAt + 3;
			if (entity.distanceTo(p) > 4.5) return false; // walked away without ever knowing

			if (age == 30) {
				Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.8f, 0.9f);
			}

			if (Sight.angleTo(p, entity.getEyePosition()) <= 55.0 && Sight.canSeeAnyPart(p, entity)) {
				Cues.sound(p, ModSounds.STINGER, SoundSource.HOSTILE, entity.getEyePosition(), 1.0f, 1.0f);
				Cues.effect(p, ScreenEffectPayload.BLACKOUT, 16, 1f);
				haunt.data.encounters++;
				scaredAt = age;
				return true;
			}
			return age < 240;
		}
	}
}
