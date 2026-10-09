package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.function.Predicate;

/**
 * The land, gone bleak. The woods are thick and dark: dark oak and old twisted oak where the
 * birch and the light woods were, giant spruce among the spruce, the leaves nearly black and the
 * ground under them the colour of rot. The open country is dying: the grass gone the colour of
 * straw, no flowers anywhere. The water is murky. And everywhere, the small wrong things (see
 * {@link Gloom}).
 * <p>
 * Only in new land: what has already been made stays as it was, but for its colours.
 */
final class Bleak {
	/** Every flower there is, and the sunflowers: none of it grows any more. */
	private static final List<String> FLOWERS = List.of("flower_default", "flower_plains", "flower_meadow",
			"flower_forest_flowers", "forest_flowers", "flower_flower_forest", "patch_sunflower", "flower_swamp",
			"flower_warm", "wildflowers_birch_forest", "wildflowers_meadow");
	/** The light woods (all birch, or birch among oak): they grow as dark woods instead. */
	private static final List<String> LIGHT_WOODS = List.of("trees_birch_and_oak", "trees_birch_and_oak_leaf_litter",
			"trees_birch", "trees_birch_leaf_litter", "birch_tall", "trees_flower_forest");

	private static final int WOODS_GRASS = 0x4C5737;
	private static final int WOODS_LEAVES = 0x34402A;
	private static final int OPEN_GRASS = 0x8E8A5B;
	private static final int OPEN_LEAVES = 0x67653F;
	private static final int WATER = 0x34433B;
	private static final int WATER_FOG = 0x161E1A;

	private Bleak() {
	}

	static void init() {
		Predicate<BiomeSelectionContext> woods = BiomeSelectors.tag(BiomeTags.IS_FOREST).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA));
		Predicate<BiomeSelectionContext> open = BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SNOWY_PLAINS, Biomes.MEADOW,
				Biomes.SUNFLOWER_PLAINS);
		if (OccupantConfig.get().bleakLand) bleak(woods, open);
		// The small wrong things, everywhere it haunts: after every tree has grown, so the webs have
		// something to hang from (the game adds them in the order of their names: after "woods").
		BiomeModifications.addFeature(woods.or(open), GenerationStep.Decoration.VEGETAL_DECORATION, ours("wrongness"));
	}

	private static void bleak(Predicate<BiomeSelectionContext> woods, Predicate<BiomeSelectionContext> open) {
		Predicate<BiomeSelectionContext> lightWoods = BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.BIRCH_FOREST,
				Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.FLOWER_FOREST);
		BiomeModification bleak = BiomeModifications.create(Occupant.id("bleak"));
		// Only what the biome has: each version names some of these differently, and asked to take
		// away something there is no such thing as, the game refuses to start.
		bleak.add(ModificationPhase.REMOVALS, woods.or(open), (where, ctx) -> without(where, ctx, FLOWERS));
		bleak.add(ModificationPhase.REPLACEMENTS, lightWoods, (where, ctx) -> without(where, ctx, LIGHT_WOODS));
		BiomeModifications.addFeature(lightWoods, GenerationStep.Decoration.VEGETAL_DECORATION, ours("woods_dark_oak"));
		BiomeModifications.addFeature(lightWoods, GenerationStep.Decoration.VEGETAL_DECORATION, ours("woods_old_oak"));
		BiomeModifications.addFeature(lightWoods, GenerationStep.Decoration.VEGETAL_DECORATION, ours("woods_oak"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_TAIGA), GenerationStep.Decoration.VEGETAL_DECORATION,
				ours("woods_giant_spruce"));
		bleak.add(ModificationPhase.POST_PROCESSING, woods, ctx -> colour(ctx, WOODS_GRASS, WOODS_LEAVES));
		bleak.add(ModificationPhase.POST_PROCESSING, open, ctx -> colour(ctx, OPEN_GRASS, OPEN_LEAVES));
		bleak.add(ModificationPhase.POST_PROCESSING, BiomeSelectors.includeByKey(Biomes.RIVER, Biomes.SWAMP),
				ctx -> colour(ctx, OPEN_GRASS, WOODS_LEAVES));
	}

	private static void without(BiomeSelectionContext where, BiomeModificationContext ctx, List<String> ids) {
		for (String id : ids) {
			ResourceKey<PlacedFeature> key = vanilla(id);
			if (where.hasPlacedFeature(key)) ctx.getGenerationSettings().removeFeature(key);
		}
	}

	private static void colour(BiomeModificationContext ctx, int grass, int leaves) {
		ctx.getEffects().setGrassColorOverride(grass);
		ctx.getEffects().setFoliageColorOverride(leaves);
		ctx.getEffects().setWaterColor(WATER);
		ctx.getEffects().setWaterFogColor(WATER_FOG);
	}

	private static ResourceKey<PlacedFeature> vanilla(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("minecraft", id));
	}

	private static ResourceKey<PlacedFeature> ours(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Occupant.id(id));
	}
}
