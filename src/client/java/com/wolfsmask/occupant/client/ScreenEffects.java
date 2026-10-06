package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.network.WhisperPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything drawn over the screen: blackouts (the "camera cut" after a scare), flickering
 * lights, and analog static that creeps in when the Occupant is close.
 */
public final class ScreenEffects {
	private static final Identifier STATIC_TEXTURE = Occupant.id("textures/misc/static.png");
	private static final int STATIC_SIZE = 128;
	private static final int FADE_OUT_TICKS = 8;

	private static int blackoutAge;
	private static int blackoutLength;
	private static int flickerAge = -1;
	private static int flickerLength;
	private static int staticAge;
	private static int staticLength;
	private static float staticStrength;
	/** Static caused simply by the Occupant being near (smoothed). */
	private static float proximityStatic;
	/** A line of text surfacing on the screen, like writing on the wall of a room. */
	private static String whisperText = "";
	private static int whisperAge;
	private static int whisperLength;
	private static int whisperCorner;

	/** How heavy the atmosphere is right now (smoothed): the dark, and how near it is. */
	private static float atmosphere;
	/** How near it is, 0 to 1 (smoothed), for the pulse at the edges of the screen. */
	private static float nearness;
	/** Ticks since joining a world, for the way in. */
	private static int introAge = -1;
	private static final int INTRO_TICKS = 110;
	private static final String[] INTRO_LINES = {
			"you are not the first to live here", "it was here before you", "something else lives in this world",
			"it has been waiting", "it knows this place better than you"};
	private static String introLine = INTRO_LINES[0];
	/** How long this way in lasts, and the second line under the first, the first time only. */
	private static int introLength = INTRO_TICKS;
	@org.jetbrains.annotations.Nullable
	private static String introSub;
	private static final int ARRIVAL_TICKS = 240;
	private static final String[][] ARRIVAL_LINES = {
			{"There is something in this world with you.", "It was here first."},
			{"You are not alone in this world.", "You never were."},
			{"Something else lives here.", "It has already seen you."}};

	private ScreenEffects() {
	}

	/** On joining a world: a few seconds of black, a line, and then the world, slowly. */
	public static void joined() {
		introAge = 0;
		introLength = INTRO_TICKS;
		introSub = null;
		introLine = INTRO_LINES[ThreadLocalRandom.current().nextInt(INTRO_LINES.length)];
	}

	/** The first time in a world: longer, in black, and it tells you. */
	/** The end of the last night: the same black as the first time, and what it has become. */
	/** Found their last camp. */
	private static final String[][] ENDING_FOUND = {
			{"You found what was left of them.", "Now it has someone new to leave behind."},
			{"You read every word they left.", "It was reading over your shoulder."}};
	/** Hid from it, the whole story. */
	private static final String[][] ENDING_HID = {
			{"You kept the doors shut and the lights on.", "It liked that you stayed home, {player}."},
			{"You never went out to meet it.", "So it came in."}};
	private static final String[][] ENDING_LINES = {
			{"It knows how to be you now, {player}.", "It will be patient."},
			{"You let it come all the way.", "Next time it will not need to ask."},
			{"It was never going to let you leave.", "It is still here."}};

	private static void ended(int which) {
		String[][] set = which == 1 ? ENDING_FOUND : which == 2 ? ENDING_HID : ENDING_LINES;
		String[] lines = set[ThreadLocalRandom.current().nextInt(set.length)];
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player != null ? mc.player.getName().getString() : "";
		introAge = 0;
		introLength = ARRIVAL_TICKS;
		introLine = lines[0].replace(", {player}", name.isEmpty() ? "" : ", " + name);
		introSub = lines[1].replace(", {player}", name.isEmpty() ? "" : ", " + name);
	}

	private static void arrived() {
		String[] lines = ARRIVAL_LINES[ThreadLocalRandom.current().nextInt(ARRIVAL_LINES.length)];
		introAge = 0;
		introLength = ARRIVAL_TICKS;
		introLine = lines[0];
		introSub = lines[1];
	}

	/**
	 * Under the HUD, over the world: the dark closing in from the edges, film grain, and a cold
	 * cast. Always a little there; heavier in the dark; much heavier, and slowly pulsing like a
	 * heartbeat at the edges, when it is close.
	 */
	public static void renderAtmosphere(GuiGraphicsExtractor ctx, float tickDelta) {
		ClientConfig cfg = ClientConfig.get();
		if (!cfg.atmosphere) return;
		int w = ctx.guiWidth();
		int h = ctx.guiHeight();
		float a = atmosphere;
		if (a < 0.01f) return;

		ctx.fill(0, 0, w, h, ((int) (a * 34f) << 24) | 0x0A1420);              // cold
		float beat = 0f;
		if (nearness > 0.05f && !cfg.reduceFlashing) {
			Minecraft mc = Minecraft.getInstance();
			float t = (mc.level != null ? mc.level.getGameTime() : 0L) + tickDelta;
			float phase = (t % 22f) / 22f;                                       // lub-dub, about once a second
			beat = (float) (Math.exp(-Math.pow((phase - 0.08f) / 0.05f, 2)) + 0.6 * Math.exp(-Math.pow((phase - 0.28f) / 0.05f, 2)));
			beat *= nearness * 0.25f;
		}
		TitleAtmosphere.drawVignette(ctx, w, h, Math.min(1f, 0.35f + a * 0.55f + beat));
		if (cfg.screenStatic) drawStatic(ctx, w, h, 0.025f + a * 0.05f);
	}

	public static void trigger(ScreenEffectPayload payload, Minecraft client) {
		switch (payload.effect()) {
			case ScreenEffectPayload.BLACKOUT -> {
				blackoutLength = Math.max(1, payload.duration());
				blackoutAge = 0;
			}
			case ScreenEffectPayload.FLICKER -> {
				flickerLength = Math.max(1, payload.duration());
				flickerAge = 0;
			}
			case ScreenEffectPayload.STATIC -> {
				staticLength = Math.max(1, payload.duration());
				staticAge = 0;
				staticStrength = Mth.clamp(payload.intensity(), 0f, 1f);
			}
			case ScreenEffectPayload.SILENCE -> client.getMusicManager().stopPlaying();
			case ScreenEffectPayload.FIRST_ARRIVAL -> arrived();
			case ScreenEffectPayload.FINALE -> ended(Math.round(payload.intensity()));
			case ScreenEffectPayload.SAVING -> ClientScares.saving(payload.duration(), Math.round(payload.intensity()));
			case ScreenEffectPayload.FOG -> ClientFog.set(payload.intensity(), payload.duration());
			case ScreenEffectPayload.ACT -> PauseLines.act = Math.round(payload.intensity());
			default -> {
			}
		}
	}

	/** Show a line of text. It is never written to the chat log: there is nothing to check. */
	public static void whisper(WhisperPayload payload) {
		whisperText = payload.text();
		whisperLength = Math.max(20, payload.duration());
		whisperAge = 0;
		whisperCorner = Math.floorMod(payload.corner(), WhisperPayload.CORNERS);
	}

	public static void reset() {
		blackoutAge = blackoutLength = 0;
		flickerAge = -1;
		staticAge = staticLength = 0;
		proximityStatic = 0f;
		whisperText = "";
		whisperAge = whisperLength = 0;
		introAge = -1;
		atmosphere = nearness = 0f;
		ClientFog.reset();
		PauseLines.reset();
		ClientScares.reset();
		Score.stop();
	}

	static float atmosphere() {
		return atmosphere;
	}

	static float nearness() {
		return nearness;
	}

	/** A short stutter of the light, as if the torch guttered. */
	static void flicker(int ticks) {
		if (flickerAge >= 0) return;
		flickerLength = Math.max(1, ticks);
		flickerAge = 0;
	}

	public static void tick(Minecraft client) {
		ClientFog.tick();
		ClientScares.tick(client);
		Score.tick(client);
		if (blackoutAge < blackoutLength) blackoutAge++;
		if (flickerAge >= 0 && ++flickerAge >= flickerLength) flickerAge = -1;
		if (staticAge < staticLength) staticAge++;
		if (whisperAge < whisperLength) whisperAge++;

		float target = 0f;
		LocalPlayer player = client.player;
		if (player != null && client.level != null) {
			List<OccupantEntity> near = client.level.getEntitiesOfClass(OccupantEntity.class,
					player.getBoundingBox().inflate(24.0), e -> !e.isRemoved());
			for (OccupantEntity e : near) {
				double d = e.distanceTo(player);
				float t = switch (e.getMode()) {
					case CHASE -> (float) Mth.clamp(1.0 - d / 24.0, 0.05, 1.0) * 0.45f;
					case STARE, STALK -> d < 14 ? (float) (1.0 - d / 14.0) * 0.22f : 0f;
					default -> 0f; // an ambush must give nothing away
				};
				target = Math.max(target, t);
			}
		}
		proximityStatic += (target - proximityStatic) * 0.2f;

		// The atmosphere: how dark it is where the player is standing, and how near it is.
		float near = 0f;
		float dark = 0f;
		if (player != null && client.level != null) {
			int light = client.level.getMaxLocalRawBrightness(player.blockPosition());
			dark = 1f - light / 15f;
			for (OccupantEntity e : client.level.getEntitiesOfClass(OccupantEntity.class,
					player.getBoundingBox().inflate(40.0), e -> !e.isRemoved() && !e.isConcealed())) {
				near = Math.max(near, (float) Mth.clamp(1.0 - e.distanceTo(player) / 40.0, 0.0, 1.0));
			}
		}
		float want = Mth.clamp(0.15f + dark * 0.45f + near * 0.5f, 0f, 1f);
		atmosphere += (want - atmosphere) * 0.05f;
		nearness += (near - nearness) * 0.08f;
		if (introAge >= 0 && ++introAge > introLength) introAge = -1;
	}

	public static void render(GuiGraphicsExtractor ctx, float tickDelta) {
		ClientConfig cfg = ClientConfig.get();
		int w = ctx.guiWidth();
		int h = ctx.guiHeight();
		ClientScares.render(ctx, w, h);

		float burst = staticAge < staticLength ? staticStrength * (1f - (staticAge + tickDelta) / staticLength) : 0f;
		float noise = Math.max(proximityStatic, burst);
		if (cfg.reduceFlashing) noise *= 0.4f;
		if (cfg.screenStatic && noise > 0.01f) drawStatic(ctx, w, h, Math.min(0.85f, noise));

		float dark = Math.max(flickerDarkness(cfg), blackoutDarkness(tickDelta, cfg));
		if (dark > 0.001f) {
			int alpha = (int) (Mth.clamp(dark, 0f, 1f) * 255f);
			ctx.fill(0, 0, w, h, alpha << 24);
		}

		// Drawn after the blackout, so a line can surface in the dark and be the only thing there.
		if (cfg.screenText) drawWhisper(ctx, w, h, tickDelta, cfg);
		drawIntro(ctx, w, h, tickDelta);
	}

	/** Black, then a line rising out of it and sinking back, then the world fading up. */
	private static void drawIntro(GuiGraphicsExtractor ctx, int w, int h, float tickDelta) {
		if (introAge < 0) return;
		float t = (introAge + tickDelta) / introLength;
		boolean arrival = introSub != null;
		float hold = arrival ? 0.72f : 0.55f;
		float black = t < hold ? 1f : Mth.clamp(1f - (t - hold) / (1f - hold), 0f, 1f);
		ctx.fill(0, 0, w, h, ((int) (black * 255f) << 24));
		float textIn = Mth.clamp((t - 0.08f) / 0.15f, 0f, 1f) * Mth.clamp((hold + 0.05f - t) / 0.15f, 0f, 1f);
		if (textIn <= 0.02f || !ClientConfig.get().screenText) return;
		int v = (int) Mth.lerp(textIn, 10f, arrival ? 190f : 150f);
		Component line = Component.literal(introLine).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(v << 16 | v << 8 | (v + 4))));
		Font font = Minecraft.getInstance().font;
		if (!arrival) {
			GuiCompat.text(ctx, (w - font.width(introLine)) / 2, h / 2 - 4, line);
			return;
		}
		// The first time: larger, and after a moment a second line under it, in a colour like old blood.
		float scale = 1.6f;
		GuiCompat.push(ctx);
		GuiCompat.translate(ctx, (w - font.width(introLine) * scale) / 2f, h / 2f - 18f);
		GuiCompat.scale(ctx, scale);
		GuiCompat.text(ctx, 0, 0, line);
		GuiCompat.pop(ctx);
		float subIn = Mth.clamp((t - 0.32f) / 0.12f, 0f, 1f) * Mth.clamp((hold + 0.05f - t) / 0.15f, 0f, 1f);
		if (subIn > 0.02f) {
			int r = (int) Mth.lerp(subIn, 10f, 150f);
			Component sub = Component.literal(introSub).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(r << 16 | (r / 5) << 8 | (r / 6))));
			GuiCompat.text(ctx, (w - font.width(introSub)) / 2, h / 2 + 8, sub);
		}
	}

	/**
	 * Text does not fade in Minecraft (its colour has no alpha), so it surfaces instead: the
	 * letters arrive one at a time out of the dark, hold, and then sink back into it.
	 */
	private static void drawWhisper(GuiGraphicsExtractor ctx, int w, int h, float tickDelta, ClientConfig cfg) {
		if (whisperText.isEmpty() || whisperAge >= whisperLength) return;
		float t = (whisperAge + tickDelta) / whisperLength;

		float in = cfg.reduceFlashing ? 0.35f : 0.22f;
		float brightness = t < in ? t / in : (t > 0.75f ? (1f - t) / 0.25f : 1f);
		brightness = Mth.clamp(brightness, 0f, 1f);
		if (brightness <= 0.02f) return;

		// The letters arrive one by one while it surfaces.
		int shown = Mth.clamp(Mth.ceil(whisperText.length() * (t / in)), 1, whisperText.length());
		String text = whisperText.substring(0, shown);

		// From barely-there to a dull grey, never white: it should look like it was always there.
		float peak = cfg.reduceFlashing ? 0.55f : 0.8f;
		int v = (int) Mth.lerp(brightness * peak, 22f, 170f);
		Component line = Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(v << 16 | v << 8 | (v + 6))));

		Font font = Minecraft.getInstance().font;
		int tw = font.width(text);
		int margin = 14;
		int x = switch (whisperCorner) {
			case 1, 3 -> w - margin - tw;
			case 4 -> (w - tw) / 2;
			default -> margin;
		};
		int y = switch (whisperCorner) {
			case 2, 3 -> h - margin - font.lineHeight * 3;
			case 4 -> h / 3;
			default -> margin + font.lineHeight;
		};
		GuiCompat.text(ctx, x, y, line);
	}

	private static float blackoutDarkness(float tickDelta, ClientConfig cfg) {
		if (blackoutAge >= blackoutLength) return 0f;
		float t = blackoutAge + tickDelta;
		float attack = cfg.reduceFlashing ? 6f : 1.5f;
		if (t < attack) return t / attack;
		float remaining = blackoutLength - t;
		return remaining < FADE_OUT_TICKS ? Math.max(0f, remaining / FADE_OUT_TICKS) : 1f;
	}

	/** Dark on ticks 0-3, 8-10, 14-20 and 24+ (FlickerEvent relies on the last one). */
	private static float flickerDarkness(ClientConfig cfg) {
		if (flickerAge < 0) return 0f;
		if (cfg.reduceFlashing) {
			float p = flickerAge / (float) flickerLength;
			return 0.7f * Mth.sin(p * Mth.PI);
		}
		int t = flickerAge;
		boolean dark = t <= 3 || (t >= 8 && t <= 10) || (t >= 14 && t <= 20) || t >= 24;
		return dark ? 0.94f : 0f;
	}

	private static void drawStatic(GuiGraphicsExtractor ctx, int w, int h, float alpha) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int ox = random.nextInt(STATIC_SIZE);
		int oy = random.nextInt(STATIC_SIZE);
		int color = ((int) (alpha * 255f) << 24) | 0xFFFFFF;
		for (int x = -ox; x < w; x += STATIC_SIZE) {
			for (int y = -oy; y < h; y += STATIC_SIZE) {
				GuiCompat.blit(ctx, STATIC_TEXTURE, x, y, 0f, 0f, STATIC_SIZE, STATIC_SIZE,
						STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, color);
			}
		}
	}
}
