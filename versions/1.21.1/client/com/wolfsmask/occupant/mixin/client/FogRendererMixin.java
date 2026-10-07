package com.wolfsmask.occupant.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The story's fog, wherever it is nearer than the game's own: over the land, and the sky drawn
 * into it too, so there is no clear horizon above a fogged world; and the colour drained
 * towards grey.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void occupant$closeIn(Camera camera, FogRenderer.FogMode mode, float farPlaneDistance, boolean thickFog,
										 float partialTick, CallbackInfo ci) {
		if (!ClientFog.active()) return;
		if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
			RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), ClientFog.start()));
			RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), ClientFog.end()));
		} else if (mode == FogRenderer.FogMode.FOG_SKY) {
			RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), ClientFog.skyEnd()));
		}
	}

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void occupant$grey(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken,
									  CallbackInfo ci) {
		if (!ClientFog.active()) return;
		float[] rgb = {fogRed, fogGreen, fogBlue};
		ClientFog.grey(rgb);
		fogRed = rgb[0];
		fogGreen = rgb[1];
		fogBlue = rgb[2];
		RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0f);
	}
}
