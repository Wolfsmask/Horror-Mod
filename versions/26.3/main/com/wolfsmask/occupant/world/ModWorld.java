package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The abandoned house, as part of world generation. How it is placed (how rare, on the surface)
 * lives in data/occupant/worldgen/placed_feature/house.json; this only says which biomes.
 */
public final class ModWorld {
	public static final MapCodec<HouseFeature> HOUSE = Registry.register(BuiltInRegistries.FEATURE_TYPE, Occupant.id("house"),
			HouseFeature.CODEC);
	public static final ResourceKey<PlacedFeature> HOUSE_PLACED =
			ResourceKey.create(Registries.PLACED_FEATURE, Occupant.id("house"));
	public static final MapCodec<GloomFeature> GLOOM = Registry.register(BuiltInRegistries.FEATURE_TYPE, Occupant.id("gloom"),
			GloomFeature.CODEC);

	private ModWorld() {
	}

	public static void init() {
		// Woods and open country: the places a house would be, and somebody might walk into it.
		BiomeModifications.addFeature(
				BiomeSelectors.tag(BiomeTags.IS_FOREST)
						.or(BiomeSelectors.tag(BiomeTags.IS_TAIGA))
						.or(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SNOWY_PLAINS, Biomes.MEADOW,
								Biomes.SUNFLOWER_PLAINS)),
				GenerationStep.Decoration.SURFACE_STRUCTURES, HOUSE_PLACED);
		Bleak.init();
	}
}
