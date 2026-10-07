package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A small ruined keep: a walled yard of old stone, towers on some of its corners, the
 * battlements still standing in places and fallen in others, the gate's bars rusted half away.
 * Somebody held out in here, for a while. The fire in the yard went out a long time ago.
 * <p>
 * Never quite the same twice: bigger or smaller, lower or higher, four towers or two or none but
 * a hall in the yard. Steps go up inside the wall to the wall-walk, and the towers still
 * standing are hollow, with a ladder to the top.
 */
final class Ruin extends Build {
	/** How far the wall is from the middle: the yard is twice this across. */
	private int r;

	Ruin(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState stone() {
		float f = random.nextFloat();
		if (f < 0.25f) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (f < 0.40f) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		if (f < 0.48f) return Blocks.COBBLESTONE.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	@Override
	void build() {
		r = 5 + random.nextInt(3);
		int height = 4 + random.nextInt(2);
		// Which corners have towers: all four, the two either side of the gate, two across from
		// each other, or none, and a hall in the yard instead.
		int layout = random.nextInt(4);
		boolean hall = layout == 3 || chance(0.25f);

		fill(-r - 1, 1, -r - 1, r + 1, height + 6, r + 1, Blocks.AIR.defaultBlockState());

		// The yard: old paving, mostly given way to gravel and earth.
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				float f = random.nextFloat();
				BlockState floor = f < 0.35f ? Blocks.GRAVEL.defaultBlockState()
						: f < 0.55f ? Blocks.COARSE_DIRT.defaultBlockState()
						: f < 0.75f ? Blocks.COBBLESTONE.defaultBlockState() : stone();
				put(x, 0, z, floor);
				foundation(x, 0, z, Blocks.COBBLESTONE.defaultBlockState());
			}
		}

		// The curtain wall. Each stretch has fallen to its own height, a run at a time, never to
		// single floating blocks.
		int drop = 0;
		for (int[] c : ring(-r, -r, r, r)) {
			if (random.nextFloat() < 0.18f) drop = random.nextFloat() < 0.5f ? 0 : 1 + random.nextInt(3);
			int top = height - drop;
			for (int y = 1; y <= top; y++) put(c[0], y, c[1], stone());
			// Battlements, every other block, where the wall still stands to its full height.
			if (drop == 0 && ((c[0] + c[1]) & 1) == 0) put(c[0], top + 1, c[1], stone());
		}

		// The gate, in the front wall, and what is left of its bars.
		fill(-1, 1, -r, 1, 3, -r, Blocks.AIR.defaultBlockState());
		List<int[]> bars = new ArrayList<>();
		for (int x = -1; x <= 1; x++) if (random.nextFloat() < 0.6f) bars.add(new int[]{x, -r});
		connected(bars, 3, Blocks.IRON_BARS);
		put(-2, 4, -r, stone());
		put(2, 4, -r, stone());

		// Steps up the inside of the west wall to the wall-walk, a solid flight.
		for (int k = 1; k < height; k++) {
			int z = -r + 2 + k;
			put(-r + 1, k, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			for (int y = 1; y < k; y++) put(-r + 1, y, z, stone());
		}

		// The towers. One has fallen to below the wall; the rest stand, hollow, a ladder inside.
		int[][] corners = switch (layout) {
			case 0 -> new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}};
			case 1 -> new int[][]{{-r, -r}, {r, -r}};
			case 2 -> random.nextBoolean() ? new int[][]{{-r, -r}, {r, r}} : new int[][]{{r, -r}, {-r, r}};
			default -> new int[0][];
		};
		int fallen = corners.length > 1 ? random.nextInt(corners.length) : -1;
		for (int i = 0; i < corners.length; i++) {
			tower(corners[i][0], corners[i][1], i == fallen ? height - 1 + random.nextInt(2) : height + 3 + random.nextInt(2), i != fallen);
		}

		if (hall) hall();

		// Fallen stone about the yard, before anything is set down on it.
		for (int i = 0; i < 6; i++) {
			int x = -r + 3 + random.nextInt(2 * r - 5);
			int z = -r + 2 + random.nextInt(2 * r - 3);
			if (Math.abs(x) > 1 || Math.abs(z) > 1) {
				put(x, 1, z, random.nextFloat() < 0.5f ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			}
		}

		// The yard: a long-dead fire, somebody's last supplies, cobwebs against the walls.
		put(0, 1, hall ? -1 : 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		container(0, 1, r - 2, facing(Blocks.CHEST.defaultBlockState(), Direction.NORTH), Loot.Kind.RUIN);
		container(-r + 2, 1, r - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.RUIN);
		put(r - 2, 1, r - 3, Blocks.SKELETON_SKULL.defaultBlockState());
		for (int[] c : new int[][]{{r - 1, 1, -2}, {r - 1, 3, 2}, {-r + 1, 1, r - 3}}) {
			if (random.nextFloat() < 0.6f) put(c[0], c[1], c[2], Blocks.COBWEB.defaultBlockState());
		}
		unsettle(-r + 2, -r + 2, r - 2, r - 2, 1, 3);
		steps(0, -r, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
	}

	/**
	 * A three-by-three tower on the corner at ({@code cx}, {@code cz}), to {@code top}. If it is
	 * {@code standing}, it is hollow, with a way in from the yard and a ladder up to the roof.
	 */
	private void tower(int cx, int cz, int top, boolean standing) {
		int sx = Integer.signum(cx);
		int sz = Integer.signum(cz);
		for (int x = cx - 1; x <= cx + 1; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
				for (int y = 1; y <= top; y++) put(x, y, z, stone());
				if (standing && (x != cx || z != cz) && ((x + z) & 1) == 0) put(x, top + 1, z, stone());
			}
		}
		if (!standing) return;
		// Hollow, a ladder on the outer wall from the floor up through the roof.
		Direction away = sx > 0 ? Direction.WEST : Direction.EAST;
		for (int y = 1; y <= top; y++) {
			put(cx, y, cz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, away));
		}
		// The way in: through the wall on the yard side, two blocks high, and along to the ladder.
		fill(cx - sx, 1, cz, cx - sx, 2, cz, Blocks.AIR.defaultBlockState());
		fill(cx - sx, 1, cz - sz, cx - sx, 2, cz - sz, Blocks.AIR.defaultBlockState());
	}

	/** A hall in the back of the yard, its roof long gone: four walls, a doorway, the chest kept in it. */
	private void hall() {
		int z0 = r - 5;
		int z1 = r - 1;
		for (int[] c : ring(-2, z0, 2, z1)) {
			int top = 2 + random.nextInt(2);
			for (int y = 1; y <= top; y++) put(c[0], y, c[1], stone());
		}
		fill(0, 1, z0, 0, 2, z0, Blocks.AIR.defaultBlockState());
		fill(-1, 1, z0 + 1, 1, 3, z1 - 1, Blocks.AIR.defaultBlockState());
	}
}
