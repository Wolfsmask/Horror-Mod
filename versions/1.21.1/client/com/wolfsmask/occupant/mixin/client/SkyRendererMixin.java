package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The sunrise and sunset glow is drawn without fog, so in a fogged world it hung over the land as
 * a hard orange band. In the fog it fades with the fog. (1.20.1 and 1.21.1: the glow's colour
 * comes from here, as red, green, blue and alpha.)
 */
@Mixin(DimensionSpecialEffects.class)
public abstract class SkyRendererMixin {
	@Inject(method = "getSunriseColor", at = @At("RETURN"))
	private void occupant$fadeInFog(float timeOfDay, float partialTicks, CallbackInfoReturnable<float[]> cir) {
		float[] colour = cir.getReturnValue();
		if (colour != null && colour.length >= 4) colour[3] *= ClientFog.sunsetLeft();
	}
}
