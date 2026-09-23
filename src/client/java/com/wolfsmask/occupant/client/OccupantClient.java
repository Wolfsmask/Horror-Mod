package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.client.render.OccupantRenderer;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class OccupantClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		EntityRendererRegistry.register(ModEntities.OCCUPANT, OccupantRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(ScreenEffectPayload.ID, (payload, context) ->
				context.client().execute(() -> ScreenEffects.trigger(payload, context.client())));

		ClientTickEvents.END_CLIENT_TICK.register(ScreenEffects::tick);
		HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
				ScreenEffects.render(drawContext, tickCounter.getTickDelta(false)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScreenEffects.reset());
	}
}
