package com.wolfsmask.occupant.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** What kind of thing an entity is, by its name: the same in every version of the game. */
public final class Kinds {
	private Kinds() {
	}

	public static boolean is(Entity entity, String name) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(name);
	}
}
