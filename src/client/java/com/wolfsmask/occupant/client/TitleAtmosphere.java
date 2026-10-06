package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.registry.ModSounds;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The title screen. Instead of the panorama: a wood at night under a dirty moon, mist moving
 * through it, and the dark closing in from the edges. Now and then, a long way back between the
 * trees, something pale is standing there, and then it is not. It never moves while you can see
 * it; it is only ever somewhere else the next time it is there. A low drone instead of the music.
 * <p>
 * Nothing on it jumps out or makes a noise. It is the first thing the mod shows anyone, and it
 * should already feel like being watched.
 */
public final class TitleAtmosphere {
	private static final Identifier FOREST = Occupant.id("textures/gui/title_forest.png");
	private static final Identifier FOG = Occupant.id("textures/gui/title_fog.png");
	private static final Identifier FIGURE = Occupant.id("textures/gui/title_figure.png");
	private static final Identifier LOGO = Occupant.id("textures/gui/title_logo.png");
	static final Identifier VIGNETTE = Occupant.id("textures/gui/vignette.png");
	private static final Identifier STATIC = Occupant.id("textures/misc/static.png");

	/** Where it can be standing, in the forest picture's own pixels: x, the ground, and its height. */
	private static final float[][] SPOTS = {{612, 402, 54}, {286, 396, 46}, {808, 410, 66}, {455, 418, 92}, {150, 404, 58}};

	private static long openedAt;
	private static float figureAlpha;
	private static float figureTarget;
	private static int spot;
	private static long nextChange;
	private static long flickerUntil;
	private static long nextFlicker;
	private static long lastFrame;
	@Nullable
	private static SoundInstance drone;

	private TitleAtmosphere() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof TitleScreen) || !ClientConfig.get().titleScreen) return;
			// The first thing anyone sees is the gate, not the menu.
			// Before that, off camera, whether this is for playing or for recording.
			if (!GateScreen.passed()) {
				boolean ask = !ModeScreen.chosen() && !ModeScreen.skip();
				Runnable onward = () -> client.setScreenAndShow(ask ? new ModeScreen() : new GateScreen());
				// And before even that, once, if another mod gets in the way of this one.
				Screen warning = CompatScreen.ifNeeded(onward);
				client.execute(warning != null ? () -> client.setScreenAndShow(warning) : onward);
				return;
			}
			// Said no at the gate: the game's own title screen, untouched.
			if (!GateScreen.accepted()) return;
			opened();
			GuiCompat.afterBackground(screen, (graphics, s) -> drawScene(graphics, s.width, s.height, s.height));
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawTitle(graphics, s));
			ScreenEvents.beforeTick(screen).register(s -> tick(client));
			ScreenEvents.remove(screen).register(s -> closed(client));
		});
	}

	static void opened() {
		long now = System.currentTimeMillis();
		if (openedAt == 0L) {
			openedAt = now;
			nextChange = now + 9000;
			nextFlicker = now + 20000;
		}
	}

	static void tick(Minecraft client) {
		// No music here: just the drone, over and over.
		client.getMusicManager().stopPlaying();
		if (drone == null || !client.getSoundManager().isActive(drone)) {
			drone = SimpleSoundInstance.forUI(ModSounds.DRONE.value(), 0.8f, 0.45f);
			client.getSoundManager().play(drone);
		}
	}

	static void closed(Minecraft client) {
		if (drone != null) client.getSoundManager().stop(drone);
		drone = null;
	}

	/** The wood, the mist, it, the grain and the dark at the edges, drawn down to {@code bottom}. */
	static void drawScene(GuiGraphicsExtractor g, int w, int h, int bottom) {
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float t = (now - openedAt) / 1000f;
		advanceFigure(now, dt);

		g.fill(0, 0, w, bottom, 0xFF000000);

		// The wood, filling the screen, drifting very slowly as if the camera were breathing.
		float scale = Math.max(w / 1024f, h / 512f) * 1.08f;
		int dw = Mth.ceil(1024 * scale);
		int dh = Mth.ceil(512 * scale);
		int x0 = (w - dw) / 2 + Math.round(Mth.sin(t * 0.021f) * (dw - w) * 0.4f);
		int y0 = (h - dh) / 2 + Math.round(Mth.sin(t * 0.017f + 1.0f) * (dh - h) * 0.3f);
		int visible = Math.min(dh, bottom - y0);
		if (visible > 0) {
			int region = Math.max(1, Math.round(visible / scale));
			GuiCompat.blit(g, FOREST, x0, y0, 0f, 0f, dw, Math.round(region * scale), 1024, region, 1024, 512, 0xFFFFFFFF);
		}
		if (bottom < h) {
			// Just the top band, redrawn over the game's own logo: the same picture, the same dark.
			drawGrain(g, w, bottom);
			int vh = Math.max(1, Math.round(256f * bottom / h));
			GuiCompat.blit(g, VIGNETTE, 0, 0, 0f, 0f, w, bottom, 256, vh, 256, 256, 0xFFFFFFFF);
			return;
		}

		// It, if it is there.
		if (figureAlpha > 0.004f) {
			float[] s = SPOTS[spot];
			int fh = Math.round(s[2] * scale);
			int fw = fh / 2;
			int fx = x0 + Math.round(s[0] * scale) - fw / 2;
			int fy = y0 + Math.round(s[1] * scale) - fh;
			int a = (int) (Mth.clamp(figureAlpha, 0f, 1f) * 255f);
			GuiCompat.blit(g, FIGURE, fx, fy, 0f, 0f, fw, fh, 64, 128, 64, 128, a << 24 | 0xFFFFFF);
		}

		// Mist in two layers, moving at different speeds across the lower half.
		drawFog(g, w, y0 + Math.round(330 * scale), Math.round(150 * scale), t * 9f, 0.75f);
		drawFog(g, w, y0 + Math.round(372 * scale), Math.round(120 * scale), -t * 5f + 211f, 0.55f);

		drawGrain(g, w, h);
		drawVignette(g, w, h, 1.0f);
		ThreadLocalRandom r = ThreadLocalRandom.current();

		// Now and then the light goes, for a moment, the way a bulb does.
		if (now >= nextFlicker) {
			flickerUntil = now + 90 + r.nextInt(160);
			nextFlicker = now + 25000 + r.nextInt(30000);
		}
		if (now < flickerUntil && !ClientConfig.get().reduceFlashing) g.fill(0, 0, w, h, 0xB0000000);
	}

	/**
	 * Fades in, slowly, somewhere between the trees, stays a while, and fades out. It only ever
	 * moves while it cannot be seen, so the next time it is there, it is simply somewhere else.
	 */
	private static void advanceFigure(long now, float dt) {
		if (now >= nextChange) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			if (figureTarget > 0f) {
				figureTarget = 0f;
				nextChange = now + 14000 + r.nextInt(22000);
			} else if (figureAlpha < 0.01f) {
				spot = r.nextInt(SPOTS.length);
				figureTarget = 0.35f + r.nextFloat() * 0.35f;
				nextChange = now + 5000 + r.nextInt(9000);
			}
		}
		float rate = figureTarget > figureAlpha ? 0.12f : 0.25f;
		figureAlpha += Mth.clamp(figureTarget - figureAlpha, -rate * dt, rate * dt);
	}

	/** Film grain over everything, fresh every frame. Static within the frame, so seams cannot show. */
	private static int grainX;
	private static int grainY;
	private static long grainFrame;

	private static void drawGrain(GuiGraphicsExtractor g, int w, int h) {
		long frame = System.currentTimeMillis() / 40;
		if (frame != grainFrame) {
			grainFrame = frame;
			grainX = ThreadLocalRandom.current().nextInt(128);
			grainY = ThreadLocalRandom.current().nextInt(128);
		}
		for (int x = -grainX; x < w; x += 128) {
			for (int y = -grainY; y < h; y += 128) {
				int rows = Math.min(128, h - y);
				if (rows <= 0) continue;
				GuiCompat.blit(g, STATIC, x, y, 0f, 0f, 128, rows, 128, rows, 128, 128, 0x12FFFFFF);
			}
		}
	}

	/** Draw it forward: the next time it is there, it is the nearest it can be, and it stays. */
	static void beckon(boolean on) {
		if (on) {
			if (spot != 3) {
				if (figureAlpha < 0.01f) {
					spot = 3;
				} else {
					figureTarget = 0f;          // gone first; it never moves while it is seen
				}
			}
			if (spot == 3) figureTarget = 0.85f;
			nextChange = System.currentTimeMillis() + 4000;
		}
	}

	private static void drawFog(GuiGraphicsExtractor g, int w, int y, int height, float scroll, float strength) {
		int tw = Math.round(512f * height / 128f);
		int off = Math.floorMod(Math.round(scroll), tw);
		int a = (int) (strength * 255f);
		for (int x = -off; x < w; x += tw) {
			GuiCompat.blit(g, FOG, x, y, 0f, 0f, tw, height, 512, 128, 512, 128, a << 24 | 0xFFFFFF);
		}
	}

	static void drawVignette(GuiGraphicsExtractor g, int w, int h, float strength) {
		int a = (int) (Mth.clamp(strength, 0f, 1f) * 255f);
		GuiCompat.blit(g, VIGNETTE, 0, 0, 0f, 0f, w, h, 256, 256, 256, 256, a << 24 | 0xFFFFFF);
	}

	/** Over the top band, where the game's own logo and splash would be: the wood again, and the title. */
	private static void drawTitle(GuiGraphicsExtractor g, Screen screen) {
		int w = screen.width;
		int h = screen.height;
		int band = h / 4 + 40;
		drawScene(g, w, h, band);

		drawLogo(g, w, h, Math.max(8, h / 4 - Math.min(512, Math.round(w * 0.78f)) * 96 / 512 - 4));
	}

	/** THE OCCUPANT, worn, slipping now and then by a pixel with a red ghost behind it. */
	static void drawLogo(GuiGraphicsExtractor g, int w, int h, int ly) {
		int lw = Math.min(512, Math.round(w * 0.78f));
		int lh = lw * 96 / 512;
		int lx = (w - lw) / 2;
		long now = System.currentTimeMillis();
		// Every so often it slips, by a pixel, and a red ghost of it lags behind.
		boolean slip = (now / 120) % 53 == 0 && !ClientConfig.get().reduceFlashing;
		if (slip) {
			GuiCompat.blit(g, LOGO, lx + 2, ly, 0f, 0f, lw, lh, 512, 96, 512, 96, 0x90B02020);
		}
		GuiCompat.blit(g, LOGO, lx + (slip ? -1 : 0), ly, 0f, 0f, lw, lh, 512, 96, 512, 96, 0xFFFFFFFF);
	}
}
