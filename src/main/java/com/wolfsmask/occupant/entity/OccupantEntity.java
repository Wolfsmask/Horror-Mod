package com.wolfsmask.occupant.entity;

import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Occupant.
 * <p>
 * It is a puppet: all decisions are made by the Director's sequences, which call
 * {@link #keepAlive()} every tick. The entity itself only enforces the rules that keep it
 * from ever "breaking":
 * <ul>
 *     <li>Only the player it is haunting can see it ({@link #broadcastToPlayer}).</li>
 *     <li>It is never saved to disk (see ModEntities) and removes itself if nothing controls it.</li>
 *     <li>Any damage makes it vanish instantly: it cannot be killed, farmed, trapped or pushed.</li>
 *     <li>It makes no sound of its own; footsteps are sent to its target only.</li>
 * </ul>
 */
public class OccupantEntity extends PathfinderMob {
	/** How it is behaving. The client uses this for posture and screen static. */
	public enum Mode { IDLE, STARE, STALK, CHASE, AMBUSH }

	/** What it looks like. MIRROR wears the viewer's own skin; HOLLOW is what is underneath. */
	public enum Form { MIRROR, HOLLOW }

	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(OccupantEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> FORM = SynchedEntityData.defineId(OccupantEntity.class, EntityDataSerializers.BYTE);

	/** Removed if no sequence has touched it for this long (orphan protection). */
	private static final int ORPHAN_TICKS = 40;
	/** Absolute upper bound on how long it can exist, whatever happens. */
	private static final int MAX_LIFETIME = 20 * 60 * 3;
	/** Horizontal distance between footstep sounds while it walks. */
	private static final double STEP_DISTANCE = 1.6;

	@Nullable
	private UUID targetUuid;
	private int ticksUncontrolled;
	private boolean gazeLocked = true;
	private boolean footsteps = true;
	private double stepAccumulator;
	private boolean vanished;

	public OccupantEntity(EntityType<? extends OccupantEntity> type, Level level) {
		super(type, level);
		this.setSilent(true);
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.setPathfindingMalus(PathType.WATER, -1.0f);
		this.setPathfindingMalus(PathType.LAVA, -1.0f);
		this.setPathfindingMalus(PathType.FIRE, -1.0f);
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0f);
		this.setPathfindingMalus(PathType.DAMAGING, -1.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MODE, (byte) Mode.IDLE.ordinal());
		builder.define(FORM, (byte) Form.MIRROR.ordinal());
	}

	// ------------------------------------------------------------------ state

	public Mode getMode() {
		byte b = this.entityData.get(MODE);
		Mode[] values = Mode.values();
		return b >= 0 && b < values.length ? values[b] : Mode.IDLE;
	}

	public void setMode(Mode mode) {
		this.entityData.set(MODE, (byte) mode.ordinal());
	}

	public Form getForm() {
		byte b = this.entityData.get(FORM);
		Form[] values = Form.values();
		return b >= 0 && b < values.length ? values[b] : Form.MIRROR;
	}

	public void setForm(Form form) {
		this.entityData.set(FORM, (byte) form.ordinal());
	}

	/** Must be called before the entity is added to the world. */
	public void bindTo(ServerPlayer target) {
		this.targetUuid = target.getUUID();
	}

	public boolean isHaunting(Player player) {
		return targetUuid != null && targetUuid.equals(player.getUUID());
	}

	/** Called every tick by whichever sequence is controlling it. */
	public void keepAlive() {
		this.ticksUncontrolled = 0;
	}

	/** When locked, it keeps its face turned toward its target. */
	public void setGazeLocked(boolean locked) {
		this.gazeLocked = locked;
	}

	public void setFootsteps(boolean enabled) {
		this.footsteps = enabled;
	}

	public boolean hasVanished() {
		return vanished || this.isRemoved();
	}

	/** Gone. No particles, no sound: was it ever there? */
	public void vanish() {
		if (!vanished) {
			vanished = true;
			this.discard();
		}
	}

	@Nullable
	public ServerPlayer findHauntedPlayer(boolean requireSameWorld) {
		if (targetUuid == null || !(this.level() instanceof ServerLevel serverLevel)) return null;
		ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(targetUuid);
		if (p == null || p.isRemoved()) return null;
		if (requireSameWorld && p.level() != this.level()) return null;
		return p;
	}

	// ------------------------------------------------------------------ movement helpers for sequences

	public void faceTowards(Vec3 point) {
		float yaw = Sight.yawBetween(this.position(), point);
		double dx = point.x - this.getX();
		double dz = point.z - this.getZ();
		double dy = point.y - this.getEyeY();
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		this.setYRot(yaw);
		this.setYBodyRot(yaw);
		this.setYHeadRot(yaw);
		this.setXRot(Mth.clamp(pitch, -60.0f, 60.0f));
	}

	public void walkTo(Vec3 pos, double speed) {
		this.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
	}

	public void chase(Entity entity, double speed) {
		this.getNavigation().moveTo(entity, speed);
	}

	public void halt() {
		this.getNavigation().stop();
		Vec3 v = this.getDeltaMovement();
		this.setDeltaMovement(0, Math.min(0, v.y), 0);
	}

	public boolean isPathing() {
		return this.getNavigation().isInProgress();
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() || this.isRemoved()) return;

		ServerPlayer target = findHauntedPlayer(true);
		if (target == null || !target.isAlive() || ++ticksUncontrolled > ORPHAN_TICKS || this.tickCount > MAX_LIFETIME
				|| this.distanceToSqr(target) > 160 * 160) {
			vanish();
			return;
		}

		if (gazeLocked && !isPathing()) {
			faceTowards(target.getEyePosition());
		} else if (gazeLocked) {
			this.getLookControl().setLookAt(target, 60.0f, 60.0f);
		}

		if (footsteps) tickFootsteps(target);
	}

	private void tickFootsteps(ServerPlayer target) {
		if (!this.onGround()) return;
		Vec3 v = this.getDeltaMovement();
		stepAccumulator += Math.sqrt(v.x * v.x + v.z * v.z);
		if (stepAccumulator < STEP_DISTANCE) return;
		stepAccumulator = 0;

		BlockPos below = this.blockPosition().below();
		BlockState floor = this.level().getBlockState(below);
		if (floor.isAir()) return;
		SoundType group = floor.getSoundType();
		float volume = getMode() == Mode.CHASE ? group.getVolume() * 0.35f : group.getVolume() * 0.18f;
		Cues.sound(target, group.getStepSound(), SoundSource.PLAYERS, this.position(), volume, group.getPitch());
	}

	// ------------------------------------------------------------------ rules that keep it unbreakable

	/** Only the haunted player's client is ever told this entity exists. */
	@Override
	public boolean broadcastToPlayer(ServerPlayer player) {
		return isHaunting(player);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!this.isRemoved()) {
			if (source.getEntity() instanceof ServerPlayer player && isHaunting(player)) {
				Cues.sound(player, ModSounds.STATIC, SoundSource.HOSTILE, this.getEyePosition(), 0.8f, 1.0f);
			}
			vanish();
		}
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Lifetime is managed by the Director, never by vanilla despawning.
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected boolean canRide(Entity entity) {
		return false;
	}
}
