package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.compat.Compat;
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
	/** What begins the moment the one that is running is over (it has taken them: the end), and its id. */
	@Nullable
	Sequence queued;
	@Nullable
	String queuedId;
	/** The fog this player's client was last told to show (blocks; 0 = none), or -1 before the first time. */
	float fogSent = -1.0f;
	/** The act this player's client was last told, or -1 before the first time. */
	int actSent = -1;
	/** Whether the way in (on joining) has been shown, this time they are here. */
	boolean joinSent;
	/** Where they last stood in its world, out of any portal: where they are brought back to. */
	@Nullable
	net.minecraft.core.BlockPos lastSafe;
	/** What was left to come after the last event, and the server tick it is due. */
	@Nullable
	FollowUps.Plan pending;
	long pendingAt;
	/** How many follow-ups in a row there have been. */
	int chain;
	/** When it was last on the player's screen, in ticks of play. */
	long lastShownAt;
	/** When something that could show it last began, in ticks of play. */
	long lastShowTriedAt = -1;
	/** When they last looked right at it, in ticks of play: on their screen is not the same as seen. */
	long lastSeenAt;
	/** The last of it, there for someone else, that they looked right at (its entity id): seen once. */
	int lastSeenTheirs = -1;
	/** Seconds hidden away (a house, a hole, under the ground), out of the open, and seconds since back in it. */
	int hiddenSeconds;
	int outFor;
	/** Not counted again before this (in ticks of play): it has just come for them. */
	long hideCalmUntil;
	/** The small things (see Trifles): what they last said and when it comes back, the step after theirs, a wrong name, home. */
	@Nullable
	String echoOf;
	long echoAt = -1;
	long stepAt;
	long stepAgainAt;
	boolean wasWalking;
	long renamedUntil;
	long renameAgainAt;
	long awaySince = -1;
	long skullDay = -1;
	long chestDay = -1;
	long doorAgainAt;
	/** Something answers the note they played, or the bell they rang: when, and which. */
	long answerAt;
	boolean answerBell;
	long answerAgainAt;
	/** A grey day: the fog right in, until this server tick. */
	long greyUntil;
	long greyDay = -1;
	long tallyDay = -1;
	/** The night their pets last sat down by themselves. */
	long petsNight = -1;
	/** When it last saved them from a fall, and from monsters (ticks of play; -1 never). */
	long mercyFallAt = -1;
	long mercyMobAt = -1;
	/** Not again in the lair before this server tick. */
	long lairAgainAt;
	/** While above 0, the fog is no further than this (blocks): something is bringing it in. */
	private float fogCloses;
	/** Until this server tick, no fog at all. */
	private long fogLiftedUntil;
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
	/** The last abandoned house it was waiting in, and when, so each house happens once a visit. */
	@Nullable
	BlockPos lastHouse;
	long lastHouseTick;
	long houseRetryAt;
	/** Watches for it standing in plain view without being noticed. */
	final Unnoticed unnoticed = new Unnoticed();

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
		this.lastShownAt = data.playTicks;
		this.lastSeenAt = data.playTicks;
		this.uuid = uuid;
		this.data = data;
		// After logging in, let the player settle before anything happens.
		this.nextEventIn = 20 * (90 + random.nextInt(120));
	}

	public boolean isBusy() {
		return active != null;
	}

	/** It is on their screen now: whether or not they look straight at it, it has been seen. */
	public void markShown() {
		lastShownAt = data.playTicks;
	}

	/** They are looking right at it. */
	public void markSeen() {
		lastSeenAt = data.playTicks;
		lastShownAt = data.playTicks;
	}

	/**
	 * Whether it has them, this time not to let go (see {@link com.wolfsmask.occupant.director.events.Strike}):
	 * from then until the end begins, nothing else gets to kill them.
	 */
	boolean taking;

	public void taking(boolean taking) {
		this.taking = taking;
	}

	/** Begins {@code sequence} the moment whatever is running now is over. */
	public void queue(String id, Sequence sequence) {
		queued = sequence;
		queuedId = id;
	}

	@Nullable
	public String activeEventId() {
		return activeId;
	}

	Situation capture(ServerPlayer player) {
		ServerLevel world = Compat.level(player);
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
	 * Put the Occupant into the world at {@code feet}, come for {@code player} (seen by everyone
	 * near). Returns null (and leaves no trace) if anything about it would be wrong: above all if it
	 * is already out near them for someone else, for there is only one of it.
	 */
	@Nullable
	public OccupantEntity spawnOccupant(ServerPlayer player, BlockPos feet, OccupantEntity.Mode mode,
										OccupantEntity.Form form) {
		return spawnOccupant(player, feet, mode, form, false);
	}

	/** As above; {@code home}: in its own lair, at the end, where it is, whoever else's it is out there. */
	@Nullable
	public OccupantEntity spawnOccupant(ServerPlayer player, BlockPos feet, OccupantEntity.Mode mode,
										OccupantEntity.Form form, boolean home) {
		ServerLevel world = Compat.level(player);
		if (!Spots.canStand(world, feet)) return null;
		if (!home && alreadyOut(world, player, Vec3.atBottomCenterOf(feet))) return null;
		// Never in the fog where it can be seen: there it would only be a smudge. (Somewhere out of
		// sight, waiting to be come across, the fog does not matter.)
		if (Vec3.atBottomCenterOf(feet).distanceTo(player.position()) > Fog.seenUpTo(player, this)
				&& !Sight.isHidden(player, feet.above())) return null;
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
		e.setAct(data.act);
		// It always arrives unseen, and is only there once they have been looking away for a
		// moment: what the server knows of where they look is a moment behind their screen.
		e.setConcealed(true);
		if (!world.addFreshEntity(e)) return null;
		return e;
	}

	/** It is out already, for someone else, near enough to them or to where it would be to be seen with it. */
	private static boolean alreadyOut(ServerLevel world, ServerPlayer player, Vec3 at) {
		net.minecraft.world.phys.AABB around = player.getBoundingBox().minmax(new net.minecraft.world.phys.AABB(at, at)).inflate(Party.ONE_OF_IT);
		for (OccupantEntity e : world.getEntitiesOfClass(OccupantEntity.class, around, x -> !x.isRemoved() && !x.hasVanished())) {
			java.util.UUID theirs = e.hauntedId();
			if (theirs == null || theirs.equals(player.getUUID())) continue;
			if (!Party.couldMeet(player.getUUID(), theirs)) continue;
			if (e.position().distanceTo(player.position()) <= Party.ONE_OF_IT || e.position().distanceTo(at) <= Party.ONE_OF_IT) return true;
		}
		return false;
	}

	/** Brings the fog in to {@code blocks}, until {@link #releaseFog}. */
	public void closeFog(float blocks) {
		fogCloses = blocks;
	}

	public void releaseFog() {
		fogCloses = 0;
	}

	/** No fog at all, for a while. */
	public void liftFog(long untilTick) {
		fogLiftedUntil = untilTick;
	}

	/** The fog's end once anything bringing it in or lifting it is taken into account. */
	float fogAfterEvents(float end, long now) {
		if (now < fogLiftedUntil || data.ending == LastNightEnding.FOUND) return 0.0f;
		return fogCloses > 0 ? Math.min(end, fogCloses) : end;
	}

	/** True if this player is somewhere the Occupant is allowed to be. */
	static boolean worldAllowed(ServerPlayer player) {
		return !OccupantConfig.get().overworldOnly || player.level().dimension() == Level.OVERWORLD;
	}
}
