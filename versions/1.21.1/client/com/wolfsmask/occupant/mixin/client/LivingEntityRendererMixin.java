package com.wolfsmask.occupant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Before 1.21.2 a model is posed straight from the entity, here: the hanging pose of anything on
 * its leg gets the last word, however its own model would have held its arms out (a zombie
 * villager's, a piglin's, a drowned's). What it wears copies the pose from this model.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
	private void occupant$hangLoose(EntityModel<?> model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
									float netHeadYaw, float headPitch, Operation<Void> original) {
		original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		if (!(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof LivingEntity living)) return;
		float[] pose = Ragdoll.forEntity(living, ageInTicks);
		if (pose == null) return;
		Ragdoll.apply(humanoid, pose);
		humanoid.hat.copyFrom(humanoid.head);
	}
}
