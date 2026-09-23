package com.wolfsmask.occupant.registry;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> OCCUPANT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Occupant.id("occupant"));

	/**
	 * The Occupant never saves to disk ({@code noSave}): if the world closes mid-scare
	 * it is simply gone, so it can never be left standing around in a save file.
	 */
	public static final EntityType<OccupantEntity> OCCUPANT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			OCCUPANT_KEY,
			EntityType.Builder.of(OccupantEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.fireImmune()
					.noSave()
					.noLootTable()
					.build(OCCUPANT_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(OCCUPANT, OccupantEntity.createAttributes());
	}
}
