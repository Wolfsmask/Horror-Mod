package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * It has them. Its leg goes in at their back and out of their chest, and it lifts them off the
 * ground on it, up to its face, and holds them there, close, while they watch (nothing they press
 * does anything). Its other legs go in, one after another. Then it is over: the first time it
 * lets them drop, at their last half heart, to show them it could have; after that, and always in
 * the last act, it does not let them go. It saves them from everything else so that it is the
 * only thing that ever gets to do this.
 * <p>
 * Run a tick at a time, from a sequence, once it is close enough to reach them.
 */
public final class Strike {
	/** The first leg is in them. */
	private static final int IN = 4;
	/** Lifted, and held at its face from here. */
	private static final int UP = 24;
	/** Its other legs, one after another. */
	private static final int[] MORE = {32, 40, 47};
	/** The end of it. */
	private static final int LAST = 56;
	private static final int BLACK = 60;
	private static final int OVER = 70;
	/** Kept in the story's cooldowns for good: it has let them go once already. */
	public static final String SPARED = "spared";

	/** What it says, holding them up: the time it lets them go, and the time it does not. */
	private static final String[] NOT_YET = {"Not yet.", "I could have.", "Next time I won't stop.",
			"You felt that. Good.", "I kept you alive for this."};
	private static final String[] NOW = {"Mine.", "Nothing saves you from me.", "No one else. Only me.",
			"You were always mine."};

	/** Who its leg is going into this moment (on peaceful the blow cannot be its own, and is not saved from). */
	@Nullable
	private static ServerPlayer striking;

	private final Haunt haunt;
	private final OccupantEntity entity;
	private int t = -1;
	private boolean landed;
	private boolean lethal;
	/** Whom it has, for letting go of however this ends. */
	@Nullable
	private ServerPlayer held;
	private Vec3 start = Vec3.ZERO;
	private Vec3 hold = Vec3.ZERO;

	public Strike(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	/** Whether this one kills: always in the last act, and from the second time it has them. */
	public static boolean lethal(HauntData d) {
		return d.act >= 4 || d.cooldowns.containsKey(SPARED);
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
		} else if (t >= IN && t < LAST) {
			carry(p);
		}
		if (t == IN && p.isAlive()) firstLeg(p);
		for (int k = 0; k < MORE.length; k++) if (t == MORE[k] && p.isAlive()) anotherLeg(p, k);
		if (t == LAST && p.isAlive()) last(p);
		if (t == BLACK) {
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, 50, 1f);
			Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		}
		if (t >= OVER) {
			release();
			return false;
		}
		return true;
	}

	/** However it ends: they are let go of, and their view is theirs again. */
	public void release() {
		ServerPlayer p = held;
		if (p == null) return;
		letGo(p, true);
		held = null;
	}

	private void begin(ServerPlayer p) {
		HauntData d = haunt.data;
		lethal = lethal(d);
		entity.halt();
		entity.setMode(OccupantEntity.Mode.AMBUSH);
		entity.setFocus(null);
		entity.faceTowards(p.getEyePosition());
		entity.setHeld(List.of(p));
		// They watch the rest: their view drawn round to it, nothing they press doing anything.
		Cues.effect(p, ScreenEffectPayload.CUTSCENE, OVER + 40, 1f);
		Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		// No stinger: a breath, very close, and the leg is already coming.
		Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.9f, 0.6f);
		start = p.position();
		hold = holdPoint(p);
	}

	/**
	 * Where it holds them: just in front of its face, their eyes level with its own, or as high
	 * as there is room for them under whatever is overhead.
	 */
	private Vec3 holdPoint(ServerPlayer p) {
		ServerLevel level = Compat.level(p);
		Vec3 base = entity.position();
		Vec3 toThem = new Vec3(p.getX() - base.x, 0.0, p.getZ() - base.z);
		double flat = toThem.length();
		Vec3 dir = flat < 1.0e-3 ? Sight.flatLook(p).scale(-1.0) : toThem.scale(1.0 / flat);
		double face = Sight.drawnBlocks(Math.max(1.0, flat), haunt.data.act) * 0.86;
		double rise = Math.max(0.0, face - p.getEyeHeight() - (p.getY() - base.y));
		Vec3 want = new Vec3(base.x + dir.x * 1.5, p.getY(), base.z + dir.z * 1.5);
		// Not into a wall on the way in to it.
		if (!level.noCollision(p, p.getBoundingBox().move(want.subtract(p.position())))) want = p.position();
		// As high as there is room for, a little at a time.
		double up = 0.0;
		for (double step = 0.25; step <= rise; step += 0.25) {
			AABB box = p.getBoundingBox().move(want.x - p.getX(), step, want.z - p.getZ());
			if (!level.noCollision(p, box)) break;
			up = step;
		}
		return want.add(0.0, up, 0.0);
	}

	/** Each tick from the first leg on: up off the ground on it, to its face, and held there. */
	private void carry(ServerPlayer p) {
		double f = Math.min(1.0, (t - IN) / (double) (UP - IN));
		double e = f * f * (3.0 - 2.0 * f);
		Vec3 want = start.lerp(hold, e);
		// A jolt with each leg that goes in, and the slow sway of hanging on it.
		boolean jolt = false;
		for (int m : MORE) jolt |= t >= m && t < m + 3;
		double sway = Math.sin(t * 0.21) * 0.04;
		if (jolt) want = want.add((p.getRandom().nextDouble() - 0.5) * 0.25, -0.12, (p.getRandom().nextDouble() - 0.5) * 0.25);
		Vec3 v = want.subtract(p.position()).add(sway, 0.0, -sway);
		if (v.length() > 0.9) v = v.normalize().scale(0.9);
		p.setNoGravity(true);
		p.setDeltaMovement(v.scale(0.6));
		p.hurtMarked = true;
		p.resetFallDistance();
		entity.faceTowards(p.getEyePosition());
	}

	private void firstLeg(ServerPlayer p) {
		landed = true;
		int act = haunt.data.act;
		haunt.data.encounters++;
		hurt(p, amount(act, p.getHealth()));
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false));
		com.wolfsmask.occupant.story.Achievements.grant(p, com.wolfsmask.occupant.story.Achievements.ONLY_ME);
		Cues.effect(p, ScreenEffectPayload.STATIC, 10, 0.55f);
		Cues.sound(p, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, p.getEyePosition(), 1.0f, 0.6f);
	}

	/** Another leg, in at their back: k from 0. */
	private void anotherLeg(ServerPlayer p, int k) {
		List<ServerPlayer> legs = new ArrayList<>();
		for (int n = 0; n < k + 2; n++) legs.add(p);
		entity.setHeld(legs);
		// Never the end of them yet: that is for the last of it.
		hurt(p, Math.max(0.0f, Math.min(2.0f, p.getHealth() - 1.0f)));
		Cues.effect(p, ScreenEffectPayload.STATIC, 6, 0.45f + 0.1f * k);
		Cues.sound(p, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, p.getEyePosition(), 1.0f, 0.5f + 0.08f * k);
		if (k == 0) {
			String[] lines = lethal ? NOW : NOT_YET;
			Cues.whisper(p, lines[p.getRandom().nextInt(lines.length)], 70);
		}
		if (k == MORE.length - 1) Cues.sound(p, ModSounds.STATIC, SoundSource.HOSTILE, entity.getEyePosition(), 0.7f, 0.5f);
	}

	private void last(ServerPlayer p) {
		if (lethal) {
			// All of them at once, and it does not stop.
			hurt(p, 1000.0f);
			if (p.isAlive()) haunt.data.cooldowns.remove(SPARED);   // a totem: they are owed nothing now
		} else {
			// Down to their last half heart, and dropped: it wanted them to know.
			hurt(p, Math.max(0.0f, p.getHealth() - 1.0f));
			haunt.data.cooldowns.put(SPARED, Long.MAX_VALUE);
		}
		letGo(p, false);
	}

	/** Its legs out of them, and down they go; with {@code scene}, their view is given back too. */
	private void letGo(ServerPlayer p, boolean scene) {
		entity.setHeld(List.of());
		if (p.isNoGravity()) {
			p.setNoGravity(false);
			p.setDeltaMovement(0.0, -0.1, 0.0);
			p.hurtMarked = true;
		}
		p.resetFallDistance();
		if (scene) Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
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
