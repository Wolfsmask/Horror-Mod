package com.wolfsmask.occupant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The drawing calls the menus and overlays use that Minecraft versions keep changing. This copy
 * is for 1.21.1, where a tint is set on the whole GUI rather than passed with each picture.
 */
public final class GuiCompat {
	private GuiCompat() {
	}

	/** Part of a texture, stretched to a box on screen and tinted (alpha included) by {@code color}. */
	public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
							int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
		RenderSystem.enableBlend();
		g.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, (color >>> 24) / 255f);
		g.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
		g.setColor(1f, 1f, 1f, 1f);
		RenderSystem.disableBlend();
	}

	/** Text with no shadow, in {@code color} (alpha included). */
	public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
		g.drawString(font, text, x, y, color, false);
	}

	/** Text in whatever colour its own style gives it. */
	public static void text(GuiGraphics g, int x, int y, Component text) {
		g.drawString(Minecraft.getInstance().font, text, x, y, 0xFFFFFFFF, false);
	}

	public static void push(GuiGraphics g) {
		g.pose().pushPose();
	}

	public static void pop(GuiGraphics g) {
		g.pose().popPose();
	}

	public static void translate(GuiGraphics g, float x, float y) {
		g.pose().translate(x, y, 0f);
	}

	public static void scale(GuiGraphics g, float scale) {
		g.pose().scale(scale, scale, 1f);
	}

	/** Something drawn over a screen's background and under its buttons. */
	public interface Backdrop {
		void draw(GuiGraphics g, Screen screen);
	}

	/**
	 * 1.21.1 has no event between a screen's background and its buttons, so the backdrop is drawn
	 * over the finished screen and the buttons are drawn again on top of it.
	 */
	public static void afterBackground(Screen screen, Backdrop backdrop) {
		ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> {
			backdrop.draw(g, s);
			for (AbstractWidget widget : Screens.getButtons(s)) widget.render(g, mouseX, mouseY, delta);
		});
	}

	/** The game's own world creation; backing out of it runs {@code onBack}. */
	public static void createWorld(Minecraft mc, Runnable onBack) {
		// Here world creation goes back to a screen rather than running something: this one only
		// runs it, on its first tick, and is never seen.
		CreateWorldScreen.openFresh(mc, new Screen(Component.empty()) {
			@Override
			public void tick() {
				onBack.run();
			}
		});
	}
}
