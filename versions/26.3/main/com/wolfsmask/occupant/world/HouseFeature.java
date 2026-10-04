package com.wolfsmask.occupant.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** The house, as a world-generation feature (26.3 and later). Everything it does is in {@link House}. */
public record HouseFeature() implements Feature {
	public static final MapCodec<HouseFeature> CODEC = MapCodec.unit(HouseFeature::new);

	@Override
	public MapCodec<HouseFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return House.tryPlace(level, random, origin);
	}
}
