package com.wolfsmask.occupant.mixin.client;

import net.minecraft.client.model.monster.zombie.ZombieModel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * From 26.3 a zombie is posed entirely in HumanoidModel (HumanoidModelMixin hangs it loose), so
 * there is nothing to do here.
 */
@Mixin(ZombieModel.class)
public abstract class ZombieModelMixin {
}
