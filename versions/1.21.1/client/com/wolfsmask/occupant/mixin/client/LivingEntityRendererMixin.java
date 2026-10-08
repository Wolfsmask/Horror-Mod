package com.wolfsmask.occupant.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Before 1.21.2 a model is posed straight from the entity (see HumanoidModelMixin), so there is no
 * copy to carry the hanging pose: nothing to do here.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
}
