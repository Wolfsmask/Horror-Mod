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

	/** What it says as you leave, late in the story: on the next menu, once, and then not again. */
	private static final String[] PARTING = {"It will still be here when you come back.", "It will keep your place.",
			"You can leave. It can't.", "It will wait up for you.", "See you tonight."};
	@Nullable
	private static String parting;
	private static long partingAt;

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
		// The first menu after leaving a world the story had got far in.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (parting == null || client.level != null || screen instanceof PauseScreen) return;
			if (partingAt == 0) partingAt = System.currentTimeMillis();
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawParting(graphics, s));
		});
	}

	/** Called as the player leaves a world, before anything is reset. */
	static void leaving() {
		if (act < 3 || !ClientConfig.get().screenText || ThreadLocalRandom.current().nextFloat() > 0.6f) return;
		parting = PARTING[ThreadLocalRandom.current().nextInt(PARTING.length)];
		partingAt = 0;
	}

	private static void drawParting(GuiGraphicsExtractor g, Screen screen) {
		String text = parting;
		if (text == null) return;
		float t = (System.currentTimeMillis() - partingAt) / 1000f;
		// Up slowly, held, and gone; after that it is not said again.
		float alpha = Math.min(Math.min(1f, Math.max(0f, (t - 1.5f) / 2f)), Math.max(0f, (11f - t) / 2f));
		if (t > 11f) {
			parting = null;
			return;
		}
		int a = Math.round(alpha * 170);
		if (a < 4) return;
		Minecraft mc = Minecraft.getInstance();
		GuiCompat.text(g, mc.font, text, (screen.width - mc.font.width(text)) / 2, screen.height - 14, (a << 24) | 0x8A1C1C);
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
