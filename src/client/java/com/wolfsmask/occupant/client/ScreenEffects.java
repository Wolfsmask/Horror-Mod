package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
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

	private ScreenEffects() {
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
			default -> {
			}
		}
	}

	public static void reset() {
		blackoutAge = blackoutLength = 0;
		flickerAge = -1;
		staticAge = staticLength = 0;
		proximityStatic = 0f;
	}

	public static void tick(Minecraft client) {
		if (blackoutAge < blackoutLength) blackoutAge++;
		if (flickerAge >= 0 && ++flickerAge >= flickerLength) flickerAge = -1;
		if (staticAge < staticLength) staticAge++;

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
	}

	public static void render(GuiGraphicsExtractor ctx, float tickDelta) {
		ClientConfig cfg = ClientConfig.get();
		int w = ctx.guiWidth();
		int h = ctx.guiHeight();

		float burst = staticAge < staticLength ? staticStrength * (1f - (staticAge + tickDelta) / staticLength) : 0f;
		float noise = Math.max(proximityStatic, burst);
		if (cfg.reduceFlashing) noise *= 0.4f;
		if (cfg.screenStatic && noise > 0.01f) drawStatic(ctx, w, h, Math.min(0.85f, noise));

		float dark = Math.max(flickerDarkness(cfg), blackoutDarkness(tickDelta, cfg));
		if (dark > 0.001f) {
			int alpha = (int) (Mth.clamp(dark, 0f, 1f) * 255f);
			ctx.fill(0, 0, w, h, alpha << 24);
		}
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
				ctx.blit(RenderPipelines.GUI_TEXTURED, STATIC_TEXTURE, x, y, 0f, 0f,
						STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, STATIC_SIZE, color);
			}
		}
	}
}
