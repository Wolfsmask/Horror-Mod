package com.wolfsmask.occupant.registry;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	/**
	 * The Occupant never saves to disk ({@code disableSaving}): if the world closes mid-scare
	 * it is simply gone, so it can never be left standing around in a save file.
	 */
	public static final EntityType<OccupantEntity> OCCUPANT = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(Occupant.MOD_ID, "occupant"),
			EntityType.Builder.create(OccupantEntity::new, SpawnGroup.MISC)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.maxTrackingRange(8)
					.trackingTickInterval(2)
					.makeFireImmune()
					.disableSaving()
					.build("occupant"));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(OCCUPANT, OccupantEntity.createAttributes());
	}
}
