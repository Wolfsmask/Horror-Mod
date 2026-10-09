package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.util.Kinds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Some villages are dead. Nobody is in them: where the villagers were there is a dark smear going
 * off somewhere, and a skull; where the iron golem stood there is only what it was made of, lying
 * on the ground, and a flower. It got hungry. Which villages, the world's seed decides, so it is
 * the same village every time, and about half of them.
 * <p>
 * Each villager and golem is looked at once, the first time it is loaded, and never again.
 */
public final class DeadVillages {
	/** How many villages are dead. */
	private static final float DEAD = 0.5f;
	/** Put on everything looked at, so it is only ever looked at once. */
	private static final String SEEN = "occupant_seen";

	private DeadVillages() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			try {
				onLoad(entity, level);
			} catch (RuntimeException e) {
				com.wolfsmask.occupant.Occupant.LOGGER.debug("Could not look at a villager", e);
			}
		});
	}

	private static void onLoad(Entity entity, ServerLevel level) {
		boolean villager = Kinds.is(entity, "villager");
		boolean golem = Kinds.is(entity, "iron_golem");
		if (!villager && !golem) return;
		if (entity.getTags().contains(SEEN)) return;
		entity.addTag(SEEN);
		if (!OccupantConfig.get().enabled || !OccupantConfig.get().deadVillages || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
		StructureStart village = level.structureManager().getStructureWithPieceAt(entity.blockPosition(), StructureTags.VILLAGE);
		if (village == null || !village.isValid()) return;
		BoundingBox box = village.getBoundingBox();
		long seed = level.getSeed() ^ (box.minX() * 341873128712L + box.minZ() * 132897987541L);
		if (RandomSource.create(seed).nextFloat() >= DEAD) return;
		// Not in the middle of being loaded: a moment later.
		level.getServer().execute(() -> {
			if (entity.isRemoved()) return;
			RandomSource random = level.getRandom();
			if (golem) {
				int iron = 3 + random.nextInt(3);
				drop(level, entity, new ItemStack(Items.IRON_INGOT, iron));
				drop(level, entity, new ItemStack(Items.POPPY));
				smear(level, entity.blockPosition(), random, 4);
			} else {
				smear(level, entity.blockPosition(), random, 2 + random.nextInt(4));
				if (random.nextFloat() < 0.6f) place(level, entity.blockPosition(), Blocks.SKELETON_SKULL.defaultBlockState()
						.setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
			}
			entity.discard();
		});
	}

	/** Lying there, for good, where it was. */
	private static void drop(ServerLevel level, Entity at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.2, at.getZ(), stack);
		item.setUnlimitedLifetime();
		item.setDeltaMovement(level.getRandom().nextGaussian() * 0.05, 0.1, level.getRandom().nextGaussian() * 0.05);
		level.addFreshEntity(item);
	}

	/** A dark smear across the ground, a few steps long, going off somewhere. */
	private static void smear(ServerLevel level, BlockPos from, RandomSource random, int length) {
		Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos p = from;
		for (int k = 0; k < length; k++) {
			place(level, p, Blocks.REDSTONE_WIRE.defaultBlockState());
			p = p.relative(random.nextFloat() < 0.25f ? d.getClockWise() : d);
		}
	}

	/** Only into the air, on top of something solid. */
	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		if (!level.getBlockState(pos).isAir()) return;
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return;
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}
}
