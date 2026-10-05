package com.wolfsmask.occupant.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The drawing calls the menus and overlays use that Minecraft versions keep changing. Each
 * version has its own copy of this class (versions/<group>/client/...). This copy is for 26.x
 * (and, renamed, 1.21.11).
 */
public final class GuiCompat {
	private GuiCompat() {
	}

	/** Part of a texture, stretched to a box on screen and tinted (alpha included) by {@code color}. */
	public static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int width, int height,
							int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight,
				textureWidth, textureHeight, color);
	}

	/** Text with no shadow, in {@code color} (alpha included). */
	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.text(font, text, x, y, color, false);
	}

	/** Text in whatever colour its own style gives it. */
	public static void text(GuiGraphicsExtractor g, int x, int y, Component text) {
		g.textRenderer().accept(x, y, text);
	}

	public static void push(GuiGraphicsExtractor g) {
		g.pose().pushMatrix();
	}

	public static void pop(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	public static void translate(GuiGraphicsExtractor g, float x, float y) {
		g.pose().translate(x, y);
	}

	public static void scale(GuiGraphicsExtractor g, float scale) {
		g.pose().scale(scale, scale);
	}

	/** Something drawn over a screen's background and under its buttons. */
	public interface Backdrop {
		void draw(GuiGraphicsExtractor g, Screen screen);
	}

	public static void afterBackground(Screen screen, Backdrop backdrop) {
		ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, delta) -> backdrop.draw(g, s));
	}

	/** The game's own world creation; backing out of it runs {@code onBack}. */
	public static void createWorld(Minecraft mc, Runnable onBack) {
		CreateWorldScreen.openFresh(mc, onBack);
	}
}
