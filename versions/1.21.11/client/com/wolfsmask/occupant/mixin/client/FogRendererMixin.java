package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The story's fog, wherever it is nearer than the game's own. In this version the distances go
 * straight from setupFog into the fog buffer, so they are changed on their way in: environmental
 * start and end, then render-distance start and end. The sky and clouds are left be.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyArgs(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void occupant$closeIn(Args args) {
		if (!ClientFog.active()) return;
		float start = ClientFog.start(), end = ClientFog.end();
		args.set(3, Math.min(args.<Float>get(3), start));
		args.set(4, Math.min(args.<Float>get(4), end));
		args.set(5, Math.min(args.<Float>get(5), start));
		args.set(6, Math.min(args.<Float>get(6), end));
	}
}
