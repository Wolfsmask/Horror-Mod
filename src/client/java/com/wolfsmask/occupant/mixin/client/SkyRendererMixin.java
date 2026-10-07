package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The sunrise and sunset glow is drawn without fog, so in a fogged world it hung over the land as
 * a hard orange band. In the fog it fades with the fog: a little warmth through it, no band.
 * (1.21.11 to 26.2: the glow's colour is an int.)
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@ModifyVariable(method = "renderSunriseAndSunset", at = @At("HEAD"), argsOnly = true)
	private int occupant$fadeInFog(int colour) {
		float left = ClientFog.sunsetLeft();
		if (left >= 1.0f) return colour;
		int alpha = Math.round((colour >>> 24) * left);
		return (alpha << 24) | (colour & 0xFFFFFF);
	}
}
