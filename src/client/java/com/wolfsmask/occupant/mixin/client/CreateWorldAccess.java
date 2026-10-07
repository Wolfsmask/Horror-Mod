package com.wolfsmask.occupant.mixin.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * World creation's own "Create New World", on every version: by its own name where the game keeps
 * names, and by whatever name it has been given where it does not (the invoker is renamed to
 * match when the mod is built).
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldAccess {
	@Invoker("onCreate")
	void occupant$create();
}
