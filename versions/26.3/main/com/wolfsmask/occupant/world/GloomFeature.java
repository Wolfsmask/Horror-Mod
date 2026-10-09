package com.wolfsmask.occupant.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** The land made wrong, as a world-generation feature (26.3 and later). Everything it does is in {@link Gloom}. */
public record GloomFeature() implements Feature {
	public static final MapCodec<GloomFeature> CODEC = MapCodec.unit(GloomFeature::new);

	@Override
	public MapCodec<GloomFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return Gloom.place(level, random, origin);
	}
}
