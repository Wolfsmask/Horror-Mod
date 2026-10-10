package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Base for every sequence that puts the Occupant in the world. Always removes it at the end. */
public abstract class ApparitionSequence implements Sequence {
	protected final Haunt haunt;
	protected final OccupantEntity entity;
	protected int age;
	/** The player has looked right at it at least once. */
	protected boolean seen;
	/** Consecutive ticks the player has been looking right at it. */
	protected int lookTicks;
	/** The animals near the player, who know it is there. */
	private List<Mob> animals = List.of();
	/** Ticks it was there, and on their screen, without their ever looking at it. */
	private int watchedUnaware;
	@org.jetbrains.annotations.Nullable
	private ServerPlayer watching;
	/** The monsters near it, who want nothing to do with it. */
	private List<Mob> monsters = List.of();

	protected ApparitionSequence(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	@Override
	public final boolean tick(ServerPlayer player) {
		if (entity.hasVanished() || player.level() != entity.level()) return false;
		entity.keepAlive();
		age++;
		animalsNotice(player);

		// Nothing is there to see until it has been revealed.
		boolean looking = !entity.isConcealed() && Sight.isLookingAt(player, entity);
		watching = player;
		if (age % 5 == 0 && !entity.isConcealed() && (looking || Sight.isOnScreen(player, entity))) {
			haunt.markShown();
			if (!seen) watchedUnaware += 5;
		}
		if (looking) {
			lookTicks++;
			haunt.markSeen();
			// What it cannot stand is being looked at. Stared at, it is gone, and sooner the better
			// they know it: it is most frightening when they do not know it is there.
			if (lookTicks > stareLimit()) {
				// Gone in a stutter of the light, so it is never seen simply blinking out.
				Cues.effect(player, com.wolfsmask.occupant.network.ScreenEffectPayload.FLICKER, 5, 1f);
				return false;
			}
			if (!seen) {
				seen = true;
				haunt.data.sightings++;
				com.wolfsmask.occupant.story.Achievements.grant(player, com.wolfsmask.occupant.story.Achievements.NOT_ALONE);
				// It had been watching them a long while before they saw it: the first thing they
				// know of it is its breath, at their ear, however far off it stands.
				if (watchedUnaware >= 20 * 10) {
					Cues.soundAtEars(player, com.wolfsmask.occupant.registry.ModSounds.BREATH,
							net.minecraft.sounds.SoundSource.HOSTILE, 0.55f, 0.85f);
				}
				onSeen(player);
			}
		} else {
			lookTicks = 0;
		}
		return update(player, looking) && !entity.hasVanished();
	}

	/**
	 * The animals near the player know it is there before the player does: they stop what they
	 * are doing and turn to look at it. Follow their eyes.
	 */
	private void animalsNotice(ServerPlayer player) {
		if (entity.isConcealed()) return;
		if (age % 40 == 1) {
			animals = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0),
					m -> m.isAlive() && m.getType().getCategory() == MobCategory.CREATURE && m.distanceTo(entity) < 72.0);
		}
		for (Mob m : animals) {
			if (!m.isAlive() || m.level() != entity.level()) continue;
			m.getNavigation().stop();
			m.getLookControl().setLookAt(entity, 30.0f, 30.0f);
		}
		// Even the things that come out at night back away from it.
		if (age % 20 == 1) {
			monsters = entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(16.0),
					m -> m.isAlive() && m != entity && !(m instanceof OccupantEntity)
							&& m.getType().getCategory() == MobCategory.MONSTER);
			for (Mob m : monsters) {
				Vec3 away = m.position().subtract(entity.position());
				away = new Vec3(away.x, 0, away.z);
				if (away.lengthSqr() < 1.0E-4) continue;
				away = away.normalize().scale(12.0);
				m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.25);
				m.getLookControl().setLookAt(entity, 30.0f, 30.0f);
			}
		}
	}

	/**
	 * Ticks of being looked straight at before it goes: a little over three seconds the first
	 * times, barely more than one once it has been seen often. Sequences that need to be watched
	 * (the hunt's stare, the last night) give more.
	 */
	protected int stareLimit() {
		return Math.max(24, 70 - haunt.data.sightings * 3);
	}

	/** @return false to end (the entity then vanishes) */
	protected abstract boolean update(ServerPlayer player, boolean looking);

	protected void onSeen(ServerPlayer player) {
	}

	@Override
	public void end() {
		Vec3 stood = entity.position();
		entity.vanishFrom(watching);
		// The animals that watched it, afterwards, stand in a ring where it stood, facing out,
		// as if they had kept it there. Only those the player cannot see being moved.
		ServerPlayer viewer = watching;
		if (viewer != null && age > 100 && animals.size() >= 3) {
			int placed = 0;
			int count = Math.min(6, animals.size());
			for (Mob m : animals) {
				if (placed >= count || !m.isAlive() || m.level() != entity.level() || !wild(m)) continue;
				if (!Sight.isHidden(viewer, m.blockPosition().above())) continue;
				double angle = Math.PI * 2 * placed / count;
				double x = stood.x + Math.cos(angle) * 2.5;
				double z = stood.z + Math.sin(angle) * 2.5;
				net.minecraft.core.BlockPos feet = com.wolfsmask.occupant.util.Spots.groundNear(
						(net.minecraft.server.level.ServerLevel) entity.level(), net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(stood.y),
						net.minecraft.util.Mth.floor(z), 3);
				if (feet == null || !Sight.isHidden(viewer, feet.above())) continue;
				float yaw = (float) Math.toDegrees(Math.atan2(-(x - stood.x), z - stood.z));
				m.getNavigation().stop();
				m.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0.0f);
				m.setYHeadRot(yaw);
				m.setYBodyRot(yaw);
				placed++;
			}
		}
		// Gone without their ever noticing, after watching them a good while: now and then, they
		// are told, afterwards, when there is nothing left to look at.
		ServerPlayer p = watching;
		if (p != null && !seen && watchedUnaware >= 20 * 12 && p.isAlive() && p.getRandom().nextFloat() < 0.45f) {
			int seconds = watchedUnaware / 20;
			String[] lines = {"It watched you for " + seconds + " seconds.", "You didn't see it. It saw you.",
					"It was there the whole time.", "It stood there for " + seconds + " seconds. You never looked."};
			Cues.whisper(p, lines[p.getRandom().nextInt(lines.length)], 100);
		}
	}

	/**
	 * Only the wild ones are moved: nothing of theirs (named, tamed, on a lead, ridden) and nothing
	 * penned in, which would be let out of its pen.
	 */
	private static boolean wild(Mob m) {
		if (m.hasCustomName() || m.isLeashed() || m.isPassenger() || m.isVehicle()) return false;
		if (m instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return false;
		net.minecraft.core.BlockPos at = m.blockPosition();
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-6, -2, -6), at.offset(6, 2, 6))) {
			net.minecraft.world.level.block.state.BlockState s = m.level().getBlockState(p);
			if (s.is(net.minecraft.tags.BlockTags.FENCES) || s.is(net.minecraft.tags.BlockTags.FENCE_GATES)
					|| s.is(net.minecraft.tags.BlockTags.WALLS)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public OccupantEntity occupant() {
		return entity;
	}
}
