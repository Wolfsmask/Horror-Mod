package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.ClientFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The story's fog, wherever it is nearer than the game's own. In this version the distances go
 * straight from setupFog into the fog buffer, so they are changed on their way in: the colour,
 * environmental start and end, render-distance start and end, then the sky and the clouds,
 * which are drawn into it too so there is no clear horizon above a fogged world.
 * <p>
 * And in the fog's own record as well, as setupFog returns: other renderers take their copy from
 * there (Sodium draws the land with it), not from the buffer. The colour is greyed where it
 * is, so the one setupFog hands back is the same.
 */
@Mixin(value = FogRenderer.class, priority = 500)
public abstract class FogRendererMixin {
	private static final String UPDATE_BUFFER =
			"Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

	@ModifyArgs(method = "setupFog", at = @At(value = "INVOKE", target = UPDATE_BUFFER))
	private void occupant$closeIn(Args args) {
		if (!ClientFog.active()) return;
		float start = ClientFog.start(), end = ClientFog.end();
		Vector4f colour = args.get(2);
		if (colour != null) {
			float[] rgb = {colour.x(), colour.y(), colour.z()};
			ClientFog.grey(rgb);
			colour.set(rgb[0], rgb[1], rgb[2], colour.w());
		}
		args.set(3, Math.min(args.<Float>get(3), start));
		args.set(4, Math.min(args.<Float>get(4), end));
		args.set(5, Math.min(args.<Float>get(5), start));
		args.set(6, Math.min(args.<Float>get(6), end));
		args.set(7, Math.min(args.<Float>get(7), ClientFog.skyEnd()));
		args.set(8, Math.min(args.<Float>get(8), end));
	}

	/** As setupFog returns: early (priority 500), so before anyone else takes their copy there. */
	@ModifyVariable(method = "setupFog", at = @At("RETURN"))
	private FogData occupant$closeInRecord(FogData fog) {
		if (fog == null || !ClientFog.active()) return fog;
		float start = ClientFog.start(), end = ClientFog.end();
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.skyEnd = Math.min(fog.skyEnd, ClientFog.skyEnd());
		fog.cloudEnd = Math.min(fog.cloudEnd, end);
		return fog;
	}
}
