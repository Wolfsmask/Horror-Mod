package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A zombie's arms are put out in front of it after the rest of its pose: on a leg, they hang too. */
@Mixin(AbstractZombieModel.class)
public abstract class ZombieModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At("TAIL"))
	private void occupant$hangLoose(ZombieRenderState state, CallbackInfo ci) {
		float[] pose = Ragdoll.forState(state);
		if (pose != null) Ragdoll.apply((HumanoidModel<?>) (Object) this, pose);
	}
}
