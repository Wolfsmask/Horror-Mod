package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.client.render.OccupantRenderer;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.network.WhisperPayload;
import com.wolfsmask.occupant.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class OccupantClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		EntityRendererRegistry.register(ModEntities.OCCUPANT, OccupantRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(ScreenEffectPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ScreenEffects.trigger(payload, context.client())));

		ClientPlayNetworking.registerGlobalReceiver(WhisperPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ScreenEffects.whisper(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(ScreenEffects::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ClientScares::screenshots);
		ClientTickEvents.END_CLIENT_TICK.register(ClientScares::coordinates);
		// Drawn last, on top of everything else on the HUD (so a blackout really is black).
		HudElementRegistry.addLast(Occupant.id("screen_effects"), (graphics, deltaTracker) ->
				ScreenEffects.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PauseLines.leaving();
			ScreenEffects.reset();
		});
		// Under everything else on the HUD: it belongs to the world, not to the hotbar.
		HudElementRegistry.addFirst(Occupant.id("atmosphere"), (graphics, deltaTracker) ->
				ScreenEffects.renderAtmosphere(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
		TitleAtmosphere.register();
		PauseLines.register();
		SettingsScreen.register();
		CompatScreen.register();
		QuickWorld.register();
	}
}
