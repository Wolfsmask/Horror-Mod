package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
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

	// Motion tracking for Situation.
	@Nullable
	private Vec3d lastPos;
	private float lastYaw;
	private float lastPitch;
	private int idleSeconds;
	private double lastSpeed;

	Haunt(UUID uuid, HauntData data, Random random) {
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

	Situation capture(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d pos = player.getPos();
		if (lastPos != null) {
			lastSpeed = pos.distanceTo(lastPos) / 20.0;
			boolean turned = Math.abs(player.getYaw() - lastYaw) > 2.0f || Math.abs(player.getPitch() - lastPitch) > 2.0f;
			idleSeconds = (lastSpeed < 0.005 && !turned) ? idleSeconds + 1 : 0;
		}
		lastPos = pos;
		lastYaw = player.getYaw();
		lastPitch = player.getPitch();

		BlockPos feet = player.getBlockPos();
		BlockPos head = feet.up();
		int light = Spots.light(world, head);
		boolean underground = Spots.isUnderground(world, feet);
		boolean sheltered = !underground && !world.isSkyVisible(head);

		OccupantConfig cfg = OccupantConfig.get();
		boolean alone = Spots.awayFromOthers(player, pos, cfg.aloneRadius);

		boolean inCombat = player.age - player.getLastAttackedTime() < 200 || player.age - player.getLastAttackTime() < 200;
		boolean busy = player.currentScreenHandler != player.playerScreenHandler
				|| player.isSleeping() || player.hasVehicle() || player.isFallFlying();

		return new Situation(world.isNight(), light <= 5, underground, sheltered, alone,
				lastSpeed < 0.04, player.isSprinting(), inCombat, player.isTouchingWater(), busy,
				light, idleSeconds);
	}

	/** Early on it wears your face. Later, less and less. */
	public OccupantEntity.Form pickForm(Random random) {
		float mirrorChance = switch (data.act) {
			case 0, 1, 2 -> 0.85f;
			case 3 -> 0.5f;
			default -> 0.25f;
		};
		return random.nextFloat() < mirrorChance ? OccupantEntity.Form.MIRROR : OccupantEntity.Form.HOLLOW;
	}

	/**
	 * Put the Occupant into the world at {@code feet}, visible only to {@code player}.
	 * Returns null (and leaves no trace) if anything about it would be wrong.
	 */
	@Nullable
	public OccupantEntity spawnOccupant(ServerPlayerEntity player, BlockPos feet, OccupantEntity.Mode mode,
										OccupantEntity.Form form) {
		ServerWorld world = player.getServerWorld();
		if (!Spots.canStand(world, feet)) return null;
		OccupantEntity e = ModEntities.OCCUPANT.create(world);
		if (e == null) return null;
		e.bindTo(player);
		Vec3d at = Vec3d.ofBottomCenter(feet);
		float yaw = Sight.yawBetween(at, player.getPos());
		e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0f);
		e.setHeadYaw(yaw);
		e.setBodyYaw(yaw);
		e.setMode(mode);
		e.setForm(form);
		if (!world.spawnEntity(e)) return null;
		return e;
	}

	/** True if this player is somewhere the Occupant is allowed to be. */
	static boolean worldAllowed(ServerPlayerEntity player) {
		return !OccupantConfig.get().overworldOnly || player.getWorld().getRegistryKey() == World.OVERWORLD;
	}
}
