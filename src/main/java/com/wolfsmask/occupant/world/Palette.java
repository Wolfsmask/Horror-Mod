package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

/**
 * What a place is built of. The builders are written in spruce and cobblestone; a palette swaps
 * that for the wood that grows round where it stands (spruce in the north, oak and birch and dark
 * oak in the woods), and weathers its stone more or less, so no two camps or towers or chapels
 * look like the same one set down twice.
 */
final class Palette {
	/** Leaves every block as the builder wrote it. */
	static final Palette AS_BUILT = new Palette(Map.of(), 0f);

	private final Map<Block, Block> swaps;
	/** How much of the plain stone has gone to moss, 0 to 1. */
	private final float moss;

	private Palette(Map<Block, Block> swaps, float moss) {
		this.swaps = swaps;
		this.moss = moss;
	}

	/** A palette for a place at {@code pos}: the local wood, and some weather. */
	static Palette pick(WorldGenLevel level, BlockPos pos, RandomSource random) {
		Wood wood;
		var biome = level.getBiome(pos);
		if (biome.is(BiomeTags.IS_TAIGA) || random.nextFloat() < 0.15f) {
			wood = Wood.SPRUCE;
		} else {
			float r = random.nextFloat();
			wood = r < 0.4f ? Wood.OAK : r < 0.7f ? Wood.DARK_OAK : Wood.BIRCH;
		}
		return new Palette(wood.swaps(), random.nextFloat() * 0.45f);
	}

	/** {@code state} as this palette builds it. */
	BlockState apply(BlockState state, RandomSource random) {
		Block to = swaps.get(state.getBlock());
		if (to != null) state = copy(state, to);
		if (moss > 0 && random.nextFloat() < moss) {
			if (state.is(Blocks.COBBLESTONE)) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			if (state.is(Blocks.STONE_BRICKS)) return (random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
		}
		return state;
	}

	/** {@code to}, with every property of {@code from} that it shares: facing, half, axis, shape. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState copy(BlockState from, Block to) {
		BlockState out = to.defaultBlockState();
		for (Property p : from.getProperties()) {
			if (out.hasProperty(p)) out = out.setValue(p, from.getValue(p));
		}
		return out;
	}

	private enum Wood {
		SPRUCE, OAK, DARK_OAK, BIRCH;

		Map<Block, Block> swaps() {
			Map<Block, Block> m = new HashMap<>();
			switch (this) {
				case SPRUCE -> {
				}
				case OAK -> put(m, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_LOG,
						Blocks.STRIPPED_OAK_LOG, Blocks.OAK_PRESSURE_PLATE, Blocks.OAK_TRAPDOOR, Blocks.OAK_DOOR, Blocks.OAK_FENCE_GATE);
				case DARK_OAK -> put(m, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE,
						Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_PRESSURE_PLATE, Blocks.DARK_OAK_TRAPDOOR,
						Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_FENCE_GATE);
				case BIRCH -> put(m, Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE, Blocks.BIRCH_LOG,
						Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_PRESSURE_PLATE, Blocks.BIRCH_TRAPDOOR, Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE_GATE);
			}
			return m;
		}

		private static void put(Map<Block, Block> m, Block planks, Block stairs, Block slab, Block fence, Block log, Block stripped,
								Block plate, Block trapdoor, Block door, Block gate) {
			m.put(Blocks.SPRUCE_PLANKS, planks);
			m.put(Blocks.SPRUCE_STAIRS, stairs);
			m.put(Blocks.SPRUCE_SLAB, slab);
			m.put(Blocks.SPRUCE_FENCE, fence);
			m.put(Blocks.SPRUCE_LOG, log);
			m.put(Blocks.STRIPPED_SPRUCE_LOG, stripped);
			m.put(Blocks.SPRUCE_PRESSURE_PLATE, plate);
			m.put(Blocks.SPRUCE_TRAPDOOR, trapdoor);
			m.put(Blocks.SPRUCE_DOOR, door);
			m.put(Blocks.SPRUCE_FENCE_GATE, gate);
		}
	}
}
