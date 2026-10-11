package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.TakenEnding;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * It has them. Their view is pulled round to it, up over them, so they see it coming; then a leg
 * comes down through them and pins them to the ground, flat (something over their head holds them
 * down, as low as a crawl), and its other legs come down on them, one after another, while it
 * watches their face. Then it is over: it lets them up, at their last half heart, to show them it
 * could have. Only at the very end of the story (see {@link TakenEnding#ready}) does it not let them
 * go: it takes them, and the ending begins. It never simply kills them. It saves them from
 * everything else so that it is the only thing that ever gets to do this.
 * <p>
 * All of it is in the world: anyone near sees them go down and stay down, and it over them.
 * <p>
 * Run a tick at a time, from a sequence, once it is close enough to reach them.
 */
public final class Strike {
	/** Long enough for their view to come round to it, whichever way they were facing. */
	private static final int DOWN = 20;
	/** Its other legs, coming down on them, one after another; each in them this long before it rises. */
	private static final int[] BEATS = {34, 46, 58};
	private static final int BEAT_FOR = 5;
	/** The end of it. */
	private static final int LAST = 70;
	private static final int BLACK = 76;
	private static final int OVER = 86;
	/** Kept in the story's cooldowns for good: it has let them go once already. */
	public static final String SPARED = "spared";

	/** What it says, over them: the time it lets them go, and the time it does not. */
	private static final String[] NOT_YET = {"Not yet.", "I could have.", "Not yet. Soon.",
			"You felt that. Good.", "I kept you alive for this."};
	private static final String[] NOW = {"Mine.", "You're coming with me.", "No one else. Only me.",
			"You were always mine."};

	/** Who its leg is going into this moment (on peaceful the blow cannot be its own, and is not saved from). */
	@Nullable
	private static ServerPlayer striking;

	private final Haunt haunt;
	private final OccupantEntity entity;
	private int t = -1;
	private boolean landed;
	private boolean lethal;
	/** It has taken them: the ending begins once this is over. */
	private boolean taken;
	/** Whom it has, for letting go of however this ends. */
	@Nullable
	private ServerPlayer held;
	/** Where it holds them down. */
	private Vec3 pin = Vec3.ZERO;
	/** What it put over their head to keep them down (nothing anyone can see), to be taken away again. */
	@Nullable
	private BlockPos lid;
	@Nullable
	private ServerLevel lidLevel;

	public Strike(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	/** Whether this time it does not let them go, but takes them: only at the very end of the story. */
	public static boolean lethal(HauntData d) {
		// One at a time: while it is taking someone else, it lets this one go.
		return TakenEnding.ready(d) && !com.wolfsmask.occupant.director.Director.someoneTaken();
	}

	/** How much the first leg takes: enough to feel, never enough to end it before it is done. */
	public static float damage(int act) {
		float set = OccupantConfig.get().chaseDamage;
		if (set > 0.0f) return Math.min(set, 6.0f);
		return act >= 4 ? 6.0f : 4.0f;
	}

	/** The first leg's share of what they have: never all of it. */
	public static float amount(int act, float health) {
		return Math.max(0.0f, Math.min(damage(act), health - 1.0f));
	}

	/**
	 * What to hurt them by for the game to take {@code amount} after it has scaled a monster's
	 * blow for {@code difficulty} (on easy half and one more, never more than the blow itself; on
	 * hard half again).
	 */
	public static float beforeDifficulty(float amount, Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> amount <= 2.0f ? amount : 2.0f * (amount - 1.0f);
			case HARD -> amount * 2.0f / 3.0f;
			default -> amount;
		};
	}

	/** Whether this is its leg going into {@code player}, now: nothing saves them from that. */
	public static boolean isStriking(ServerPlayer player) {
		return striking == player;
	}

	/** Whether the leg went in. */
	public boolean landed() {
		return landed;
	}

	/** One tick of it; false once it is over. */
	public boolean tick(ServerPlayer p) {
		t++;
		held = p;
		if (t == 0) begin(p);
		if (!p.isAlive()) {
			// It is done with them: the rest is only the dark.
			letGo(p, false);
			if (t < BLACK) t = BLACK;
		} else if (t < DOWN) {
			rear(p);
		} else if (t < (taken ? BLACK : LAST)) {
			// Taken, it does not let them up: it is still holding them down when the black comes.
			holdDown(p);
		}
		if (t == DOWN && p.isAlive()) down(p);
		for (int k = 0; k < BEATS.length; k++) {
			if (t == BEATS[k] && p.isAlive()) beat(p, k);
			// It rises again for the next.
			if (t == BEATS[k] + BEAT_FOR && p.isAlive()) entity.setHeld(List.of(p));
		}
		if (t == LAST && p.isAlive()) last(p);
		if (t == BLACK) {
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 50, 1f);
			Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		}
		if (t >= OVER) {
			release();
			if (taken) TakenEnding.begin(haunt);
			return false;
		}
		return true;
	}

	/**
	 * However it ends: they are let up, and their view is theirs again; unless it has taken them
	 * and this is the end beginning, which goes on being a scene without a break.
	 */
	public void release() {
		haunt.taking(false);
		haunt.pinned(false);
		ServerPlayer p = held;
		if (p == null) {
			liftLid();
			return;
		}
		letGo(p, !(taken && t >= OVER));
		held = null;
	}

	private void begin(ServerPlayer p) {
		HauntData d = haunt.data;
		lethal = lethal(d);
		haunt.taking(lethal);
		haunt.pinned(true);
		entity.halt();
		entity.setMode(OccupantEntity.Mode.AMBUSH);
		// From here no one's blow stops it, theirs or a friend's: it is too late for that.
		entity.setUnmoved(true);
		entity.setFocus(null);
		entity.faceTowards(p.getEyePosition());
		entity.setHeld(List.of());
		// They watch the rest: their view drawn round to it, nothing they press doing anything.
		Cues.effect(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		// No stinger: a breath, very close, while they come round to it.
		Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.9f, 0.6f);
		pin = p.position();
	}

	/** While their view comes round: it over them, still, its face on theirs; they cannot get away. */
	private void rear(ServerPlayer p) {
		p.setDeltaMovement(0.0, Math.min(p.getDeltaMovement().y, 0.0), 0.0);
		p.hurtMarked = true;
		entity.faceTowards(p.getEyePosition());
		if (t == DOWN - 6) Cues.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, entity.getEyePosition(), 0.5f, 0.35f);
	}

	/** Down: a leg through them into the ground, and something over their head to keep them there. */
	private void down(ServerPlayer p) {
		landed = true;
		int act = haunt.data.act;
		haunt.data.encounters++;
		ServerLevel level = Compat.level(p);
		// On the ground, where they are (or the ground under them, if they were caught off it).
		BlockPos feet = p.blockPosition();
		if (!p.onGround()) {
			BlockPos ground = com.wolfsmask.occupant.util.Spots.groundNear(level, feet.getX(), feet.getY(), feet.getZ(), 4);
			if (ground != null) feet = ground;
		}
		pin = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		// Flat: with something over them where their head would be, they can only lie there. It is
		// nothing anyone can see, and it is taken away again the moment it lets them up.
		BlockPos head = feet.above();
		if (level.getBlockState(head).isAir() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
			level.setBlock(head, Blocks.BARRIER.defaultBlockState(), 3);
			lid = head;
			lidLevel = level;
		}
		entity.setHeld(List.of(p));
		// Standing over them, close.
		Vec3 away = new Vec3(entity.getX() - pin.x, 0.0, entity.getZ() - pin.z);
		away = away.lengthSqr() < 1.0e-4 ? Sight.flatLook(p) : away.normalize();
		Vec3 over = pin.add(away.scale(1.1));
		if (level.noCollision(entity, entity.getBoundingBox().move(over.x - entity.getX(), 0.0, over.z - entity.getZ()))) {
			entity.setPos(over.x, entity.getY(), over.z);
		}
		entity.faceTowards(p.getEyePosition());
		hurt(p, amount(act, p.getHealth()));
		com.wolfsmask.occupant.story.Achievements.grant(p, com.wolfsmask.occupant.story.Achievements.ONLY_ME);
		Cues.effect(p, ScreenEffectPayload.STATIC, 10, 0.55f);
		Cues.sound(p, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, pin, 0.9f, 0.4f);
		Cues.sound(p, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, p.getEyePosition(), 1.0f, 0.6f);
	}

	/** Each tick it has them down: held where it pinned them, its face on theirs. */
	private void holdDown(ServerPlayer p) {
		Vec3 v = new Vec3(pin.x - p.getX(), 0.0, pin.z - p.getZ());
		if (v.length() > 0.5) v = v.normalize().scale(0.5);
		p.setDeltaMovement(v.x * 0.6, Math.min(p.getDeltaMovement().y, 0.0), v.z * 0.6);
		p.hurtMarked = true;
		p.resetFallDistance();
		entity.faceTowards(p.getEyePosition());
	}

	/** Another of its legs, down on them: k from 0. */
	private void beat(ServerPlayer p, int k) {
		entity.setHeld(List.of(p, p));
		// Never the end of them yet: that is for the last of it.
		hurt(p, Math.max(0.0f, Math.min(2.0f, p.getHealth() - 1.0f)));
		Cues.effect(p, ScreenEffectPayload.STATIC, 6, 0.45f + 0.1f * k);
		Cues.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, pin, 1.0f, 0.45f + 0.05f * k);
		Cues.sound(p, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, p.getEyePosition(), 1.0f, 0.5f + 0.08f * k);
		if (k == 0) {
			String[] lines = lethal ? NOW : NOT_YET;
			Cues.whisper(p, lines[p.getRandom().nextInt(lines.length)], 70);
		}
		if (k == BEATS.length - 1) Cues.sound(p, ModSounds.STATIC, SoundSource.HOSTILE, entity.getEyePosition(), 0.7f, 0.5f);
	}

	private void last(ServerPlayer p) {
		if (lethal) {
			// All of them at once, and it does not stop; and it does not let them up. It takes them.
			entity.setHeld(List.of(p, p, p));
			hurt(p, Math.max(0.0f, p.getHealth() - 1.0f));
			taken = true;
			return;
		}
		// Down to their last half heart, and let up: it wanted them to know.
		hurt(p, Math.max(0.0f, p.getHealth() - 1.0f));
		haunt.data.cooldowns.put(SPARED, Long.MAX_VALUE);
		letGo(p, false);
	}

	/** Its legs out of them, and they can get up; with {@code scene}, their view is given back too. */
	private void letGo(ServerPlayer p, boolean scene) {
		entity.setHeld(List.of());
		liftLid();
		if (p.isNoGravity()) {
			p.setNoGravity(false);
			p.setDeltaMovement(0.0, -0.1, 0.0);
			p.hurtMarked = true;
		}
		p.resetFallDistance();
		if (scene) Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
	}

	/** What it put over their head, gone (if it is still what it put there). */
	private void liftLid() {
		BlockPos at = lid;
		ServerLevel level = lidLevel;
		lid = null;
		lidLevel = null;
		if (at != null && level != null && level.getBlockState(at).is(Blocks.BARRIER)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
	}

	/** Hurts them by exactly {@code amount}, whatever the difficulty; nothing saves them from it. */
	static void hurt(ServerPlayer p, float amount) {
		if (amount <= 0.0f) return;
		OccupantEntity by = null;
		Difficulty difficulty = p.level().getDifficulty();
		striking = p;
		try {
			for (OccupantEntity e : Compat.level(p).getEntitiesOfClass(OccupantEntity.class, p.getBoundingBox().inflate(8.0), x -> !x.isRemoved())) by = e;
			// The game halves what a monster does on easy, adds half on hard (which would make the
			// warning a killing blow) and takes all of it away on peaceful, where nothing else is
			// allowed to hurt them and it still does.
			if (difficulty == Difficulty.PEACEFUL || by == null) p.hurtServer(p.level(), p.damageSources().generic(), amount);
			else p.hurtServer(p.level(), p.damageSources().mobAttack(by), beforeDifficulty(amount, difficulty));
		} finally {
			striking = null;
		}
	}
}
