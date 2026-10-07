package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The sunrise and sunset glow is drawn without fog, so in a fogged world it hung over the land as
 * a hard orange band. In the fog it fades with the fog. (26.3 and later: the glow's colour is a
 * vector, and 26.4 adds an argument after it, which this does not need to know about.)
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@ModifyVariable(method = "renderSunriseAndSunset", at = @At("HEAD"), argsOnly = true)
	private Vector4fc occupant$fadeInFog(Vector4fc colour) {
		float left = ClientFog.sunsetLeft();
		if (left >= 1.0f || colour == null) return colour;
		return new Vector4f(colour.x(), colour.y(), colour.z(), colour.w() * left);
	}
}
