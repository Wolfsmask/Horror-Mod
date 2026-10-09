package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * Its leg, through them. It saves them from everything else so that it is the only thing that
 * ever gets to hurt them. In the third act it stops short of killing them, every time, to show
 * them it could; in the last act it does not stop.
 * <p>
 * Run a tick at a time, from a sequence, once it is close enough to reach them: the leg goes in
 * fast (each client draws it going through them, as it does through what it saves them from),
 * it hurts, it comes out, the screen goes black, and in the black it is gone.
 */
public final class Strike {
	/** Ticks from the start: the leg reaches them, comes out, the screen goes black, it is over. */
	private static final int IN = 4;
	private static final int OUT = 16;
	private static final int BLACK = 17;
	private static final int OVER = 24;

	/** What it says, the leg still in them: in the third act, and in the last. */
	private static final String[] NOT_YET = {"Not yet.", "I could have.", "Next time I won't stop.",
			"You felt that. Good.", "I kept you alive for this."};
	private static final String[] NOW = {"Mine.", "Nothing saves you from me.", "No one else. Only me.",
			"You were always mine."};

	private final Haunt haunt;
	private final OccupantEntity entity;
	private int t = -1;
	private boolean landed;

	public Strike(Haunt haunt, OccupantEntity entity) {
		this.haunt = haunt;
		this.entity = entity;
	}

	/** How much a strike takes, so far into the story: the config's, if it gives one. */
	public static float damage(int act) {
		float set = OccupantConfig.get().chaseDamage;
		if (set > 0.0f) return set;
		return act >= 4 ? 10.0f : 7.0f;
	}

	/**
	 * How much it takes from someone with {@code health}: before the last act never all of it
	 * (barely, so they know it could have), in the last act all of it if that is what it comes to.
	 */
	public static float amount(int act, float health) {
		float amount = damage(act);
		return act < 4 ? Math.max(0.0f, Math.min(amount, health - 1.0f)) : amount;
	}

	/** Whether the leg went in. */
	public boolean landed() {
		return landed;
	}

	/** One tick of it; false once it is over. */
	public boolean tick(ServerPlayer p) {
		t++;
		if (t == 0) {
			entity.halt();
			entity.setMode(OccupantEntity.Mode.AMBUSH);
			entity.faceTowards(p.getEyePosition());
			entity.setHeld(List.of(p));
			// No stinger: a breath, very close, and the leg is already coming.
			Cues.sound(p, ModSounds.BREATH, SoundSource.HOSTILE, entity.getEyePosition(), 0.9f, 0.6f);
		}
		if (t == IN) hurt(p);
		if (t == OUT) entity.setHeld(List.of());
		if (t == BLACK) Cues.effect(p, ScreenEffectPayload.BLACKOUT, 40, 1f);
		return t < OVER;
	}

	private void hurt(ServerPlayer p) {
		landed = true;
		int act = haunt.data.act;
		haunt.data.encounters++;
		float amount = amount(act, p.getHealth());
		if (amount > 0.0f) p.hurtServer(p.level(), p.damageSources().mobAttack(entity), amount);
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
		Cues.effect(p, ScreenEffectPayload.STATIC, 14, 0.7f);
		String[] lines = act >= 4 ? NOW : NOT_YET;
		if (p.isAlive()) Cues.whisper(p, lines[p.getRandom().nextInt(lines.length)], 80);
	}
}
