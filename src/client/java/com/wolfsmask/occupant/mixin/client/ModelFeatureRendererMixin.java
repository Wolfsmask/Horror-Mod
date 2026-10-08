package com.wolfsmask.occupant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wolfsmask.occupant.client.render.Ragdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The last word on a model's pose, just before it is drawn: whatever on its leg is shaped like a
 * person hangs loose, however its own model would have held its arms out (a zombie villager's, a
 * piglin's, a drowned's), and so does anything worn over it.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@WrapOperation(method = {"prepareModel", "renderModel"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
	private void occupant$hangLoose(Model<?> model, Object state, Operation<Void> original) {
		original.call(model, state);
		if (!(model instanceof HumanoidModel<?> humanoid)) return;
		float[] pose = Ragdoll.forState(state);
		if (pose != null) Ragdoll.apply(humanoid, pose);
	}
}
