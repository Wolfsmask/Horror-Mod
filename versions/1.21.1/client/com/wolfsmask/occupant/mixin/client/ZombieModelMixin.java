package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A zombie's arms are put out in front of it after the rest of its pose: on a leg, they hang too. */
@Mixin(AbstractZombieModel.class)
public abstract class ZombieModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V", at = @At("TAIL"))
	private void occupant$hangLoose(Monster entity, float limbSwing, float limbSwingAmount, float ageInTicks,
									float netHeadYaw, float headPitch, CallbackInfo ci) {
		float[] pose = Ragdoll.forEntity(entity, ageInTicks);
		if (pose == null) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		Ragdoll.apply(model, pose);
		model.hat.copyFrom(model.head);
	}
}
