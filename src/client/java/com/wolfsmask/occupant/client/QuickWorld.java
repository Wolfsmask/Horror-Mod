package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * CREATE WORLD at the gate goes straight into a new world: the game's own world creation is
 * opened, given the world's name, and confirmed on its first tick, exactly as if its "Create New
 * World" button had been pressed. Every setting is the game's default.
 */
public final class QuickWorld {
	/** What the new world is called. The game adds a number if there is one already. */
	private static final String NAME = "The Occupant";
	private static boolean pending;

	private QuickWorld() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!pending || !(screen instanceof CreateWorldScreen creation)) return;
			pending = false;
			try {
				creation.getUiState().setName(NAME);
			} catch (RuntimeException e) {
				Occupant.LOGGER.warn("Could not name the new world", e);
			}
			boolean[] done = {false};
			ScreenEvents.beforeTick(screen).register(s -> {
				if (done[0]) return;
				done[0] = true;
				Occupant.LOGGER.info("[client] creating the world straight away");
				GuiCompat.confirmWorldCreation(s);
			});
		});
	}

	/** Opens world creation and has it go straight through; {@code onBack} if it is cancelled. */
	public static void begin(Minecraft mc, Runnable onBack) {
		pending = true;
		GuiCompat.createWorld(mc, onBack);
	}
}
