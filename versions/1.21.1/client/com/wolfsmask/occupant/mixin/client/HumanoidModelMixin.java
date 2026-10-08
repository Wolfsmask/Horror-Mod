package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anything shaped like a person that it has on a leg hangs loose: arms, legs and head (see Ragdoll). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void occupant$hangLoose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
									float netHeadYaw, float headPitch, CallbackInfo ci) {
		float[] pose = Ragdoll.forEntity(entity, ageInTicks);
		if (pose == null) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		Ragdoll.apply(model, pose);
		model.hat.copyFrom(model.head);
	}
}
