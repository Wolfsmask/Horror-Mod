package com.wolfsmask.occupant.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Before 1.21.9 there is no separate step where models are posed before drawing: the hanging pose
 * gets the last word in the renderer itself (see LivingEntityRendererMixin). Nothing to do here.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ModelFeatureRendererMixin {
}
