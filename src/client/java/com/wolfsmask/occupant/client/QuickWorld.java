package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.jetbrains.annotations.Nullable;

/**
 * CREATE WORLD at the gate goes straight into a new world: the game's own world creation is
 * opened, given the world's name, and confirmed straight after, exactly as if its "Create New
 * World" button had been pressed. Every setting is the game's default.
 */
public final class QuickWorld {
	/** What the new world is called. The game adds a number if there is one already. */
	private static final String NAME = "The Occupant";
	private static boolean pending;
	@Nullable
	private static Screen toConfirm;

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
			toConfirm = creation;
		});
		// Confirmed after the game's tick, not inside the screen's own: confirming closes the screen,
		// and nothing may be left holding on to it when it goes.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen creation = toConfirm;
			if (creation == null) return;
			toConfirm = null;
			Occupant.LOGGER.info("[client] creating the world straight away");
			GuiCompat.confirmWorldCreation(creation);
		});
	}

	/** Opens world creation and has it go straight through; {@code onBack} if it is cancelled. */
	public static void begin(Minecraft mc, Runnable onBack) {
		pending = true;
		GuiCompat.createWorld(mc, onBack);
	}
}
