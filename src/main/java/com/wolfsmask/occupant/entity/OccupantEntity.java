package com.wolfsmask.occupant.entity;

import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockSoundGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Occupant.
 * <p>
 * It is a puppet: all decisions are made by the Director's sequences, which call
 * {@link #keepAlive()} every tick. The entity itself only enforces the rules that keep it
 * from ever "breaking":
 * <ul>
 *     <li>Only the player it is haunting can see it ({@link #canBeSpectated}).</li>
 *     <li>It is never saved to disk (see ModEntities) and removes itself if nothing controls it.</li>
 *     <li>Any damage makes it vanish instantly: it cannot be killed, farmed, trapped or pushed.</li>
 *     <li>It makes no sound of its own; footsteps are sent to its target only.</li>
 * </ul>
 */
public class OccupantEntity extends PathAwareEntity {
	/** How it is behaving. The client uses this for posture and screen static. */
	public enum Mode { IDLE, STARE, STALK, CHASE, AMBUSH }

	/** What it looks like. MIRROR wears the viewer's own skin; HOLLOW is what is underneath. */
	public enum Form { MIRROR, HOLLOW }

	private static final TrackedData<Byte> MODE = DataTracker.registerData(OccupantEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> FORM = DataTracker.registerData(OccupantEntity.class, TrackedDataHandlerRegistry.BYTE);

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

	public OccupantEntity(EntityType<? extends OccupantEntity> type, World world) {
		super(type, world);
		this.setSilent(true);
		this.setPersistent();
		this.experiencePoints = 0;
		this.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
		this.setPathfindingPenalty(PathNodeType.LAVA, -1.0f);
		this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0f);
		this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0f);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(MODE, (byte) Mode.IDLE.ordinal());
		builder.add(FORM, (byte) Form.MIRROR.ordinal());
	}

	// ------------------------------------------------------------------ state

	public Mode getMode() {
		byte b = this.dataTracker.get(MODE);
		Mode[] values = Mode.values();
		return b >= 0 && b < values.length ? values[b] : Mode.IDLE;
	}

	public void setMode(Mode mode) {
		this.dataTracker.set(MODE, (byte) mode.ordinal());
	}

	public Form getForm() {
		byte b = this.dataTracker.get(FORM);
		Form[] values = Form.values();
		return b >= 0 && b < values.length ? values[b] : Form.MIRROR;
	}

	public void setForm(Form form) {
		this.dataTracker.set(FORM, (byte) form.ordinal());
	}

	/** Must be called before the entity is added to the world. */
	public void bindTo(ServerPlayerEntity target) {
		this.targetUuid = target.getUuid();
	}

	public boolean isHaunting(PlayerEntity player) {
		return targetUuid != null && targetUuid.equals(player.getUuid());
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
	public ServerPlayerEntity findHauntedPlayer(boolean requireSameWorld) {
		if (targetUuid == null || this.getWorld().isClient) return null;
		if (this.getServer() == null) return null;
		ServerPlayerEntity p = this.getServer().getPlayerManager().getPlayer(targetUuid);
		if (p == null || p.isRemoved()) return null;
		if (requireSameWorld && p.getWorld() != this.getWorld()) return null;
		return p;
	}

	// ------------------------------------------------------------------ movement helpers for sequences

	public void faceTowards(Vec3d point) {
		float yaw = Sight.yawBetween(this.getPos(), point);
		double dx = point.x - this.getX();
		double dz = point.z - this.getZ();
		double dy = point.y - this.getEyeY();
		float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(MathHelper.clamp(pitch, -60.0f, 60.0f));
	}

	public void walkTo(Vec3d pos, double speed) {
		this.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
	}

	public void chase(Entity entity, double speed) {
		this.getNavigation().startMovingTo(entity, speed);
	}

	public void halt() {
		this.getNavigation().stop();
		Vec3d v = this.getVelocity();
		this.setVelocity(0, Math.min(0, v.y), 0);
	}

	public boolean isPathing() {
		return !this.getNavigation().isIdle();
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient || this.isRemoved()) return;

		ServerPlayerEntity target = findHauntedPlayer(true);
		if (target == null || !target.isAlive() || ++ticksUncontrolled > ORPHAN_TICKS || this.age > MAX_LIFETIME
				|| this.squaredDistanceTo(target) > 160 * 160) {
			vanish();
			return;
		}

		if (gazeLocked && !isPathing()) {
			faceTowards(target.getEyePos());
		} else if (gazeLocked) {
			this.getLookControl().lookAt(target, 60.0f, 60.0f);
		}

		if (footsteps) tickFootsteps(target);
	}

	private void tickFootsteps(ServerPlayerEntity target) {
		if (!this.isOnGround()) return;
		Vec3d v = this.getVelocity();
		stepAccumulator += Math.sqrt(v.x * v.x + v.z * v.z);
		if (stepAccumulator < STEP_DISTANCE) return;
		stepAccumulator = 0;

		BlockPos below = this.getBlockPos().down();
		BlockState floor = this.getWorld().getBlockState(below);
		if (floor.isAir()) return;
		BlockSoundGroup group = floor.getSoundGroup();
		float volume = getMode() == Mode.CHASE ? group.getVolume() * 0.35f : group.getVolume() * 0.18f;
		Cues.sound(target, group.getStepSound(), SoundCategory.PLAYERS, this.getPos(), volume, group.getPitch());
	}

	// ------------------------------------------------------------------ rules that keep it unbreakable

	/** Only the haunted player's client is ever told this entity exists. */
	@Override
	public boolean canBeSpectated(ServerPlayerEntity spectator) {
		return isHaunting(spectator);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!this.getWorld().isClient && !this.isRemoved()) {
			if (source.getAttacker() instanceof ServerPlayerEntity player && isHaunting(player)) {
				Cues.sound(player, ModSounds.STATIC, SoundCategory.HOSTILE, this.getEyePos(), 0.8f, 1.0f);
			}
			vanish();
		}
		return false;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushAway(Entity entity) {
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean canAvoidTraps() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Lifetime is managed by the Director, never by vanilla despawning.
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldRenderName() {
		return false;
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}

	@Override
	protected boolean canStartRiding(Entity entity) {
		return false;
	}
}
