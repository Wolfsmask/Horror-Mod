package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.registry.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Finds places where the Occupant can stand, or where something can happen, that will look
 * completely natural: solid ground, enough headroom, not in water, not floating, not in a wall.
 * If no good spot exists the caller simply does nothing; a scare that looks wrong is worse than none.
 */
public final class Spots {
	private Spots() {
	}

	/** Never touches chunks that are not already loaded (that would stall the server). */
	public static boolean isLoaded(ServerWorld world, BlockPos pos) {
		return world.isInBuildLimit(pos)
				&& world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
	}

	/** Could the Occupant stand with its feet in this block? */
	public static boolean canStand(ServerWorld world, BlockPos feet) {
		if (!isLoaded(world, feet) || !world.isInBuildLimit(feet.up(2))) return false;

		BlockPos below = feet.down();
		BlockState floor = world.getBlockState(below);
		if (!floor.isSideSolidFullSquare(world, below, Direction.UP)) return false;
		if (floor.isIn(BlockTags.LEAVES) || floor.isOf(Blocks.MAGMA_BLOCK) || floor.isOf(Blocks.CACTUS)) return false;

		for (int i = 0; i < 2; i++) {
			BlockPos p = feet.up(i);
			BlockState s = world.getBlockState(p);
			if (!s.getCollisionShape(world, p).isEmpty()) return false;
			if (!s.getFluidState().isEmpty()) return false;
			if (s.isIn(BlockTags.FIRE) || s.isOf(Blocks.POWDER_SNOW) || s.isOf(Blocks.SWEET_BERRY_BUSH)) return false;
		}

		Vec3d bottom = Vec3d.ofBottomCenter(feet);
		return world.isSpaceEmpty(ModEntities.OCCUPANT.getDimensions().getBoxAt(bottom));
	}

	/** Search a column for standable ground, preferring heights closest to {@code y}. */
	@Nullable
	public static BlockPos groundNear(ServerWorld world, int x, int y, int z, int range) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i <= range * 2; i++) {
			int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
			m.set(x, y + dy, z);
			if (canStand(world, m)) return m.toImmutable();
		}
		return null;
	}

	/** Standable ground on the surface (ignores tree canopies), or null. */
	@Nullable
	public static BlockPos surfaceAt(ServerWorld world, int x, int z) {
		BlockPos probe = new BlockPos(x, world.getBottomY(), z);
		if (!isLoaded(world, probe)) return null;
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return groundNear(world, x, top, z, 2);
	}

	/** Combined sky + block light, including night-time darkness. */
	public static int light(ServerWorld world, BlockPos pos) {
		return world.getLightLevel(pos);
	}

	/** No other player (besides {@code player}) within {@code radius} blocks of {@code pos}. */
	public static boolean awayFromOthers(ServerPlayerEntity player, Vec3d pos, double radius) {
		for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
			if (other == player || other.isSpectator()) continue;
			if (other.getPos().squaredDistanceTo(pos) < radius * radius) return false;
		}
		return true;
	}

	/**
	 * Pick a spot around the player at a random distance and at a random angle from the
	 * direction the player is facing. {@code minAngle..maxAngle} is measured left/right from
	 * straight ahead (0 = dead ahead, 180 = directly behind).
	 */
	@Nullable
	public static BlockPos aroundPlayer(ServerPlayerEntity player, Random random, double minDist, double maxDist,
										double minAngle, double maxAngle, boolean surface, int attempts,
										Predicate<BlockPos> accept) {
		ServerWorld world = player.getServerWorld();
		Vec3d origin = player.getPos();
		Vec3d look = Sight.flatLook(player);
		for (int i = 0; i < attempts; i++) {
			double angle = minAngle + random.nextDouble() * (maxAngle - minAngle);
			if (random.nextBoolean()) angle = -angle;
			double dist = minDist + random.nextDouble() * (maxDist - minDist);
			Vec3d dir = Sight.rotateY(look, angle);
			int x = (int) Math.floor(origin.x + dir.x * dist);
			int z = (int) Math.floor(origin.z + dir.z * dist);

			BlockPos spot = surface
					? surfaceAt(world, x, z)
					: groundNear(world, x, (int) Math.floor(origin.y), z, 6);
			if (spot == null) continue;
			if (accept.test(spot)) return spot;
		}
		return null;
	}

	/** Nearest block matching the predicate, searching outward from {@code center}. */
	@Nullable
	public static BlockPos nearestBlock(ServerWorld world, BlockPos center, int rangeXZ, int rangeY,
										Predicate<BlockPos> accept) {
		for (BlockPos p : BlockPos.iterateOutwards(center, rangeXZ, rangeY, rangeXZ)) {
			if (!isLoaded(world, p)) continue;
			if (accept.test(p)) return p.toImmutable();
		}
		return null;
	}

	/** Is this position underground (no sky above it and well below the surface)? */
	public static boolean isUnderground(ServerWorld world, BlockPos pos) {
		if (world.isSkyVisible(pos)) return false;
		int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return pos.getY() < surface - 6;
	}

	/** Natural stone that may be carved without ever damaging a build. */
	public static boolean isNaturalStone(BlockState state) {
		return state.isIn(BlockTags.BASE_STONE_OVERWORLD);
	}
}
