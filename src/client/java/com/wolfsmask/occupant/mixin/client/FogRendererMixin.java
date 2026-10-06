package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The story's fog, wherever it is nearer than the game's own. The sky and clouds are left be.
 * Only the result is used, not the arguments: they differ between 26.x releases (26.4 added one).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void occupant$closeIn(CallbackInfoReturnable<?> cir) {
		if (!ClientFog.active() || !(cir.getReturnValue() instanceof FogData fog)) return;
		float start = ClientFog.start(), end = ClientFog.end();
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
	}
}
