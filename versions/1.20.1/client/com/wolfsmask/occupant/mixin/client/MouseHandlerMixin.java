package com.wolfsmask.occupant.mixin.client;

import com.wolfsmask.occupant.client.Cutscene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every frame, once the mouse has turned the view: in a scene, the view is drawn round to what it
 * is doing instead, and whatever the mouse did is undone. On 1.20.1 the mouse turns the view in
 * turnPlayer(), every frame.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("RETURN"))
	private void occupant$scene(CallbackInfo ci) {
		Cutscene.frame(Minecraft.getInstance());
	}
}
