package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.WorldMode;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Options → The Occupant: the settings, in the game, without editing a file or needing another
 * mod. Your own settings always; the world's (how hard it pushes, sound only, the Creator Cut)
 * only in a single-player world, where you are the server.
 * <p>
 * Plain buttons, and the text drawn after the screen is, so it is the same on every Minecraft
 * version.
 */
public final class SettingsScreen extends Screen {
	private static final List<String> INTENSITIES = List.of("subtle", "normal", "relentless");
	@Nullable
	private final Screen parent;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("occupant.settings.title"));
		this.parent = parent;
	}

	/** A small button on the pause menu, in the corner, out of the game's own way. */
	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				((com.wolfsmask.occupant.mixin.client.ScreenAccess) screen).occupant$addRenderableWidget(Button.builder(
						Component.translatable("occupant.settings.open"),
						b -> client.setScreenAndShow(new SettingsScreen(screen))).bounds(4, 4, 98, 20).build());
			} else if (screen instanceof SettingsScreen settings) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> settings.labels(graphics));
			}
		});
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int y = 46;
		ClientConfig c = ClientConfig.get();
		toggle(left, y, "heartbeat", () -> c.heartbeat, v -> c.heartbeat = v);
		toggle(right, y, "score", () -> c.score, v -> c.score = v);
		y += 24;
		toggle(left, y, "static", () -> c.screenStatic, v -> c.screenStatic = v);
		toggle(right, y, "text", () -> c.screenText, v -> c.screenText = v);
		y += 24;
		toggle(left, y, "fog", () -> c.fog, v -> c.fog = v);
		toggle(right, y, "atmosphere", () -> c.atmosphere, v -> c.atmosphere = v);
		y += 24;
		toggle(left, y, "flashing", () -> c.reduceFlashing, v -> c.reduceFlashing = v);
		toggle(right, y, "title_screen", () -> c.titleScreen, v -> c.titleScreen = v);
		y += 24;
		toggle(left, y, "ask", () -> c.askHowPlaying, v -> c.askHowPlaying = v);

		y += 44;
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			OccupantConfig w = OccupantConfig.get();
			String[] intensity = {INTENSITIES.contains(w.intensity) ? w.intensity : "normal"};
			addRenderableWidget(Button.builder(intensityLabel(intensity[0]), b -> {
				String next = INTENSITIES.get((INTENSITIES.indexOf(intensity[0]) + 1) % INTENSITIES.size());
				intensity[0] = next;
				server.execute(() -> {
					OccupantConfig.get().intensity = next;
					OccupantConfig.save();
				});
				b.setMessage(intensityLabel(next));
			}).bounds(left, y, 150, 20).build());
			boolean[] soundOnly = {w.soundOnly};
			addRenderableWidget(Button.builder(onOff("sound_only", soundOnly[0]), b -> {
				boolean next = !soundOnly[0];
				soundOnly[0] = next;
				server.execute(() -> {
					OccupantConfig.get().soundOnly = next;
					OccupantConfig.save();
				});
				b.setMessage(onOff("sound_only", next));
			}).bounds(right, y, 150, 20).build());
			y += 24;
			boolean[] creator = {WorldMode.creatorCut()};
			addRenderableWidget(Button.builder(onOff("creator", creator[0]), b -> {
				boolean next = !creator[0];
				creator[0] = next;
				server.execute(() -> WorldMode.set(next));
				b.setMessage(onOff("creator", next));
			}).bounds(left, y, 310, 20).build());
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	private void toggle(int x, int y, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(onOff(key, get.get()), b -> {
			boolean next = !get.get();
			set.accept(next);
			ClientConfig.save();
			b.setMessage(onOff(key, next));
		}).bounds(x, y, 150, 20).build());
	}

	private static Component onOff(String key, boolean on) {
		return Component.translatable("occupant.settings." + key, Component.translatable(on ? "options.on" : "options.off"));
	}

	private static Component intensityLabel(String intensity) {
		return Component.translatable("occupant.settings.intensity", Component.translatable("occupant.settings.intensity." + intensity));
	}

	/** The headings, drawn over the screen once it has drawn itself. */
	private void labels(GuiGraphicsExtractor g) {
		centred(g, this.title.getString(), 18, 0xFFE8DECE);
		centred(g, Component.translatable("occupant.settings.yours").getString(), 34, 0xFF8A8A8A);
		if (Minecraft.getInstance().getSingleplayerServer() != null) {
			centred(g, Component.translatable("occupant.settings.world").getString(), 46 + 24 * 5 + 8, 0xFF8A8A8A);
		} else {
			centred(g, Component.translatable("occupant.settings.world_remote").getString(), 46 + 24 * 5 + 8, 0xFF6A6A6A);
		}
	}

	private void centred(GuiGraphicsExtractor g, String text, int y, int color) {
		var font = Minecraft.getInstance().font;
		GuiCompat.text(g, font, text, (this.width - font.width(text)) / 2, y, color);
	}

	@Override
	public void onClose() {
		ClientConfig.save();
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
