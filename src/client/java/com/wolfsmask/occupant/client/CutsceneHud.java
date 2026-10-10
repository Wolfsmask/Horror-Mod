package com.wolfsmask.occupant.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import java.util.List;

/**
 * In a scene, the game's own things out of the picture: no crosshair, no hotbar, no hearts. For as
 * long as it lasts it is a film, not a game.
 */
final class CutsceneHud {
	private CutsceneHud() {
	}

	static void register() {
		for (var id : List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
				VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
				VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HELD_ITEM_TOOLTIP)) {
			try {
				HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) -> {
					if (!Cutscene.active()) original.extractRenderState(graphics, deltaTracker);
				});
			} catch (RuntimeException e) {
				// Gone already (another mod's HUD in its place): that part stays in a scene, nothing more.
				com.wolfsmask.occupant.Occupant.LOGGER.debug("No {} on the HUD to leave out of a scene", id, e);
			}
		}
	}
}
