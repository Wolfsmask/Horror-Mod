package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anything shaped like a person that it has on a leg hangs loose: arms, legs and head (see Ragdoll). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void occupant$hangLoose(HumanoidRenderState state, CallbackInfo ci) {
		float[] pose = Ragdoll.forState(state);
		if (pose != null) Ragdoll.apply((HumanoidModel<?>) (Object) this, pose);
	}
}
