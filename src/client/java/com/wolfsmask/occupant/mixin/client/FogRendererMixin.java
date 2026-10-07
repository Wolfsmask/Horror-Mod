package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The story's fog, wherever it is nearer than the game's own: over the land, and the sky and
 * clouds drawn into it too, so there is no clear horizon above a fogged world; and the colour
 * drained towards grey. Only the result is used, not the arguments: they differ between 26.x
 * releases (26.4 added one).
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
		fog.skyEnd = Math.min(fog.skyEnd, ClientFog.skyEnd());
		fog.cloudEnd = Math.min(fog.cloudEnd, end);
		if (fog.color != null) {
			float[] rgb = {fog.color.x(), fog.color.y(), fog.color.z()};
			ClientFog.grey(rgb);
			fog.color.set(rgb[0], rgb[1], rgb[2], fog.color.w());
		}
	}
}
