package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The score: three loops, mixed live. A low drone that is there once the story has started and
 * grows in the dark; high, bowed glass from the second act, swelling when it is near; and, late
 * on, a muffled pulse when it is close. Under the game's Music volume.
 */
public final class Score {
	private static final Layer[] LAYERS = new Layer[3];

	private Score() {
	}

	static void tick(Minecraft mc) {
		int act = PauseLines.act;
		if (mc.level == null || mc.player == null || act <= 0 || !ClientConfig.get().score) {
			stop();
			return;
		}
		float dark = ScreenEffects.atmosphere();
		float near = ScreenEffects.nearness();
		float[] want = {
				0.10f + 0.25f * dark,
				act >= 2 ? 0.06f * (act - 1) + 0.35f * near : 0f,
				act >= 3 ? 0.9f * near : 0f};
		SoundEvent[] sounds = {ModSounds.DREAD_LOW.value(), ModSounds.DREAD_HIGH.value(), ModSounds.DREAD_PULSE.value()};
		for (int i = 0; i < LAYERS.length; i++) {
			Layer l = LAYERS[i];
			if (want[i] > 0.01f && (l == null || l.isStopped() || !mc.getSoundManager().isActive(l))) {
				l = new Layer(sounds[i]);
				LAYERS[i] = l;
				mc.getSoundManager().play(l);
			}
			if (l != null) l.target = want[i];
		}
	}

	static void stop() {
		for (int i = 0; i < LAYERS.length; i++) {
			if (LAYERS[i] != null) LAYERS[i].target = 0f;
		}
	}

	/** One loop, its volume eased towards where the story wants it. */
	private static final class Layer extends AbstractTickableSoundInstance {
		float target;

		Layer(SoundEvent sound) {
			super(sound, SoundSource.MUSIC, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.02f;
		}

		@Override
		public void tick() {
			this.volume += (target - this.volume) * 0.02f;
			if (target <= 0.001f && this.volume < 0.01f) this.stop();
		}
	}
}
