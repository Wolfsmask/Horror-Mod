package com.wolfsmask.occupant.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The story's fog over the terrain, wherever it is nearer than the game's own. The sky is left be. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void occupant$closeIn(Camera camera, FogRenderer.FogMode mode, float farPlaneDistance, boolean thickFog,
										 float partialTick, CallbackInfo ci) {
		if (mode != FogRenderer.FogMode.FOG_TERRAIN || !ClientFog.active()) return;
		RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), ClientFog.start()));
		RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), ClientFog.end()));
	}
}
