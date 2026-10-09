package com.wolfsmask.occupant.world;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The land made wrong, as a world-generation feature (to 26.2). Everything it does is in {@link Gloom}. */
public final class GloomFeature extends Feature<NoneFeatureConfiguration> {
	public GloomFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		return Gloom.place(ctx.level(), ctx.random(), ctx.origin());
	}
}
