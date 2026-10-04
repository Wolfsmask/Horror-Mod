package com.wolfsmask.occupant.world;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The house, as a world-generation feature (26.1 and 26.2). Everything it does is in {@link House}. */
public final class HouseFeature extends Feature<NoneFeatureConfiguration> {
	public HouseFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		return House.tryPlace(ctx.level(), ctx.random(), ctx.origin());
	}
}
