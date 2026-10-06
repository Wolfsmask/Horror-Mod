package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The story's fog, wherever it is nearer than the game's own. The sky and clouds are left be. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void occupant$closeIn(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkness,
								  ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		if (!ClientFog.active()) return;
		FogData fog = cir.getReturnValue();
		float start = ClientFog.start(), end = ClientFog.end();
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
	}
}
