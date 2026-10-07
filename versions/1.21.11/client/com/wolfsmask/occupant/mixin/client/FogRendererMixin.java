package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The story's fog, wherever it is nearer than the game's own. In this version the distances go
 * straight from setupFog into the fog buffer, so they are changed on their way in: the colour,
 * environmental start and end, render-distance start and end, then the sky and the clouds,
 * which are drawn into it too so there is no clear horizon above a fogged world.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyArgs(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void occupant$closeIn(Args args) {
		if (!ClientFog.active()) return;
		float start = ClientFog.start(), end = ClientFog.end();
		Vector4f colour = args.get(2);
		if (colour != null) {
			float[] rgb = {colour.x(), colour.y(), colour.z()};
			ClientFog.grey(rgb);
			args.set(2, new Vector4f(rgb[0], rgb[1], rgb[2], colour.w()));
		}
		args.set(3, Math.min(args.<Float>get(3), start));
		args.set(4, Math.min(args.<Float>get(4), end));
		args.set(5, Math.min(args.<Float>get(5), start));
		args.set(6, Math.min(args.<Float>get(6), end));
		args.set(7, Math.min(args.<Float>get(7), ClientFog.skyEnd()));
		args.set(8, Math.min(args.<Float>get(8), end));
	}
}
