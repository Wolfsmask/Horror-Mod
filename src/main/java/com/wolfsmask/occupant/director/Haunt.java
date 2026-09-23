package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The live (not saved) side of one player's haunting: what is happening right now. */
public final class Haunt {
	public final UUID uuid;
	public final HauntData data;

	@Nullable
	Sequence active;
	@Nullable
	String activeId;
	/** Ticks until the next once-per-second evaluation. */
	int evalTimer = 20;
	/** Haunt ticks until the Director next tries to start something. */
	int nextEventIn;
	/**
	 * Seconds since anything more than background noise happened. Tension is built by absence,
	 * so the longer this runs, the more the Director favours something big: the scare lands when
	 * the player has decided nothing is coming.
	 */
	int quietSeconds;
	/** Whether the last snapshot was taken at night (used only for pacing). */
	boolean lastSituationWasNight;

	// Motion tracking for Situation.
	@Nullable
	private Vec3 lastPos;
	private float lastYaw;
	private float lastPitch;
	private int idleSeconds;
	private double lastSpeed;

	Haunt(UUID uuid, HauntData data, RandomSource random) {
		this.uuid = uuid;
		this.data = data;
		// After logging in, let the player settle before anything happens.
		this.nextEventIn = 20 * (90 + random.nextInt(120));
	}

	public boolean isBusy() {
		return active != null;
	}

	@Nullable
	public String activeEventId() {
		return activeId;
	}

	Situation capture(ServerPlayer player) {
		ServerLevel world = player.level();
		Vec3 pos = player.position();
		if (lastPos != null) {
			lastSpeed = pos.distanceTo(lastPos) / 20.0;
			boolean turned = Math.abs(player.getYRot() - lastYaw) > 2.0f || Math.abs(player.getXRot() - lastPitch) > 2.0f;
			idleSeconds = (lastSpeed < 0.005 && !turned) ? idleSeconds + 1 : 0;
		}
		lastPos = pos;
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();

		BlockPos feet = player.blockPosition();
		BlockPos head = feet.above();
		int light = Spots.light(world, head);
		boolean underground = Spots.isUnderground(world, feet);
		boolean sheltered = !underground && !world.canSeeSky(head);

		OccupantConfig cfg = OccupantConfig.get();
		boolean alone = Spots.awayFromOthers(player, pos, cfg.aloneRadius);

		boolean inCombat = player.tickCount - player.getLastHurtByMobTimestamp() < 200 || player.tickCount - player.getLastHurtMobTimestamp() < 200;
		boolean busy = player.containerMenu != player.inventoryMenu
				|| player.isSleeping() || player.isPassenger() || player.isFallFlying();

		lastSituationWasNight = world.isDarkOutside();
		return new Situation(world.isDarkOutside(), light <= 5 || Spots.isDark(world, head), underground, sheltered, alone,
				lastSpeed < 0.04, player.isSprinting(), inCombat, player.isInWater(), busy,
				light, idleSeconds);
	}

	/** Early on it keeps its face hidden. Later, less and less. */
	public OccupantEntity.Form pickForm(RandomSource random) {
		float veiledChance = switch (data.act) {
			case 0, 1, 2 -> 0.85f;
			case 3 -> 0.5f;
			default -> 0.25f;
		};
		return random.nextFloat() < veiledChance ? OccupantEntity.Form.VEILED : OccupantEntity.Form.REVEALED;
	}

	/**
	 * Put the Occupant into the world at {@code feet}, visible only to {@code player}.
	 * Returns null (and leaves no trace) if anything about it would be wrong.
	 */
	@Nullable
	public OccupantEntity spawnOccupant(ServerPlayer player, BlockPos feet, OccupantEntity.Mode mode,
										OccupantEntity.Form form) {
		ServerLevel world = player.level();
		if (!Spots.canStand(world, feet)) return null;
		OccupantEntity e = ModEntities.OCCUPANT.create(world, EntitySpawnReason.EVENT);
		if (e == null) return null;
		e.bindTo(player);
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = Sight.yawBetween(at, player.position());
		e.snapTo(at.x, at.y, at.z, yaw, 0.0f);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.setMode(mode);
		e.setForm(form);
		if (!world.addFreshEntity(e)) return null;
		return e;
	}

	/** True if this player is somewhere the Occupant is allowed to be. */
	static boolean worldAllowed(ServerPlayer player) {
		return !OccupantConfig.get().overworldOnly || player.level().dimension() == Level.OVERWORLD;
	}
}
