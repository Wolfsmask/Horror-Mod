package com.wolfsmask.occupant.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Late in the story, the pause menu is not quite safe either. Now and then, low down on it, faint,
 * a line that was not there the last time: pausing the game does not pause it.
 */
public final class PauseLines {
	private static final String[] LINES = {"It is still here.", "Pausing does not stop it.", "It can wait.",
			"It is very patient.", "Don't leave it alone too long.", "It knows you stopped.", "It is still looking at you.",
			"Take your time.", "Where are you going?", "Don't leave it here alone.", "It will still be here when you come back."};

	/** How far the story has gone, as the server last said. */
	static int act;
	@Nullable
	private static String line;
	private static long shownAt;

	private PauseLines() {
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof PauseScreen) || act < 3 || !ClientConfig.get().screenText) return;
			ThreadLocalRandom random = ThreadLocalRandom.current();
			if (random.nextFloat() > (act >= 4 ? 0.45f : 0.25f)) return;
			line = LINES[random.nextInt(LINES.length)];
			shownAt = System.currentTimeMillis();
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(graphics, s));
		});
	}

	private static void draw(GuiGraphicsExtractor g, Screen screen) {
		if (line == null) return;
		// It comes up slowly, a second or two after the menu does, and never quite settles.
		float t = (System.currentTimeMillis() - shownAt) / 1000f;
		float alpha = Math.min(1f, Math.max(0f, (t - 1.2f) / 2.5f));
		if (!ClientConfig.get().reduceFlashing) alpha *= 0.8f + 0.2f * (float) Math.sin(t * 2.3f);
		int a = Math.round(alpha * 150);
		if (a < 4) return;
		Minecraft mc = Minecraft.getInstance();
		int x = (screen.width - mc.font.width(line)) / 2;
		GuiCompat.text(g, mc.font, line, x, screen.height - 18, (a << 24) | 0x8A1C1C);
	}

	/** How far the story has gone for this player, as far as their game knows. */
	public static int act() {
		return act;
	}

	static void reset() {
		act = 0;
		line = null;
	}
}
