package com.wolfsmask.occupant.util;

import com.wolfsmask.occupant.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
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
	public static boolean isLoaded(ServerLevel world, BlockPos pos) {
		return world.isInWorldBounds(pos)
				&& world.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
	}

	/** Could the Occupant stand with its feet in this block? */
	public static boolean canStand(ServerLevel world, BlockPos feet) {
		if (!isLoaded(world, feet) || !world.isInWorldBounds(feet.above(2))) return false;

		BlockPos below = feet.below();
		BlockState floor = world.getBlockState(below);
		if (!floor.isFaceSturdy(world, below, Direction.UP)) return false;
		if (floor.is(BlockTags.LEAVES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)) return false;

		for (int i = 0; i < 2; i++) {
			BlockPos p = feet.above(i);
			BlockState s = world.getBlockState(p);
			if (!s.getCollisionShape(world, p).isEmpty()) return false;
			if (!s.getFluidState().isEmpty()) return false;
			if (s.is(BlockTags.FIRE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SWEET_BERRY_BUSH)) return false;
		}

		return world.noCollision(ModEntities.OCCUPANT.getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(feet)));
	}

	/** Search a column for standable ground, preferring heights closest to {@code y}. */
	@Nullable
	public static BlockPos groundNear(ServerLevel world, int x, int y, int z, int range) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= range * 2; i++) {
			int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
			m.set(x, y + dy, z);
			if (canStand(world, m)) return m.immutable();
		}
		return null;
	}

	/** Standable ground on the surface (ignores tree canopies), or null. */
	@Nullable
	public static BlockPos surfaceAt(ServerLevel world, int x, int z) {
		if (!isLoaded(world, new BlockPos(x, world.getMinY(), z))) return null;
		int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return groundNear(world, x, top, z, 2);
	}

	/** Combined sky + block light, including night-time darkness. */
	public static int light(ServerLevel world, BlockPos pos) {
		return world.getMaxLocalRawBrightness(pos);
	}

	/** No other player (besides {@code player}) within {@code radius} blocks of {@code pos}. */
	public static boolean awayFromOthers(ServerPlayer player, Vec3 pos, double radius) {
		for (ServerPlayer other : player.level().players()) {
			if (other == player || other.isSpectator()) continue;
			if (other.position().distanceToSqr(pos) < radius * radius) return false;
		}
		return true;
	}

	/**
	 * Pick a spot around the player at a random distance and at a random angle from the
	 * direction the player is facing. {@code minAngle..maxAngle} is measured left/right from
	 * straight ahead (0 = dead ahead, 180 = directly behind).
	 */
	@Nullable
	public static BlockPos aroundPlayer(ServerPlayer player, RandomSource random, double minDist, double maxDist,
										double minAngle, double maxAngle, boolean surface, int attempts,
										Predicate<BlockPos> accept) {
		ServerLevel world = player.level();
		Vec3 origin = player.position();
		Vec3 look = Sight.flatLook(player);
		for (int i = 0; i < attempts; i++) {
			double angle = minAngle + random.nextDouble() * (maxAngle - minAngle);
			if (random.nextBoolean()) angle = -angle;
			double dist = minDist + random.nextDouble() * (maxDist - minDist);
			Vec3 dir = Sight.rotateY(look, angle);
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
	public static BlockPos nearestBlock(ServerLevel world, BlockPos center, int rangeXZ, int rangeY,
										Predicate<BlockPos> accept) {
		for (BlockPos p : BlockPos.withinManhattan(center, rangeXZ, rangeY, rangeXZ)) {
			if (!isLoaded(world, p)) continue;
			if (accept.test(p)) return p.immutable();
		}
		return null;
	}

	/** Is this position underground (no sky above it and well below the surface)? */
	public static boolean isUnderground(ServerLevel world, BlockPos pos) {
		if (world.canSeeSky(pos)) return false;
		int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return pos.getY() < surface - 6;
	}

	/** The player's bed or respawn anchor, if it is in the world they are standing in. */
	@Nullable
	public static BlockPos respawnPos(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		if (config == null || config.respawnData() == null) return null;
		return config.respawnData().dimension() == player.level().dimension() ? config.respawnData().pos() : null;
	}

	/** Natural stone that may be carved without ever damaging a build. */
	public static boolean isNaturalStone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD);
	}
}
