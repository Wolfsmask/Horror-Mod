package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A skeleton's bow arm is raised after the rest of its pose: on a leg, it hangs too. */
@Mixin(SkeletonModel.class)
public abstract class SkeletonModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
	private void occupant$hangLoose(Mob entity, float limbSwing, float limbSwingAmount, float ageInTicks,
									float netHeadYaw, float headPitch, CallbackInfo ci) {
		float[] pose = Ragdoll.forEntity(entity, ageInTicks);
		if (pose == null) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		Ragdoll.apply(model, pose);
		model.hat.copyFrom(model.head);
	}
}
