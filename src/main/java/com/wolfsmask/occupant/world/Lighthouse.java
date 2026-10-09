package com.wolfsmask.occupant.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * A lighthouse by the water, its lamp long dead: a banded tower on a stone plinth, a door, a
 * ladder up the inside to the lamp room, and a gallery round the outside of that, with a railing,
 * that you can step out onto and look down at the water from. The keeper's things are up there,
 * and the last thing they wrote down.
 * <p>
 * No two the same: red and white, black and white, white gone grey, or bare stone; slim, or
 * broad with floors inside; often the keeper's hut beside it, with their things still in it; and now
 * and then only the stump of one, its top fallen in round its foot.
 */
final class Lighthouse extends Build {
	/** How its bands are painted. */
	private enum Paint { RED, BLACK, WHITE, STONE }

	private Paint paint = Paint.RED;

	Lighthouse(WorldGenLevel level, BlockPos base, Rotation rotation, RandomSource random) {
		super(level, base, rotation, random);
	}

	private BlockState band(int y, int height) {
		boolean light = (y / 3) % 2 == 0;
		// White, gone grey here and there: patches of a greyer white, not squares of another stone.
		BlockState calcite = old(Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), 0.12f);
		return switch (paint) {
			case RED -> light ? calcite : Blocks.BRICKS.defaultBlockState();
			case BLACK -> light ? calcite
					: old(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), 0.15f);
			// All white, gone grey in patches, with one red band left under the gallery.
			case WHITE -> y >= height - 1 ? Blocks.BRICKS.defaultBlockState()
					: old(Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), 0.22f);
			case STONE -> light ? Blocks.STONE_BRICKS.defaultBlockState() : old(Blocks.ANDESITE.defaultBlockState(),
					Blocks.COBBLESTONE.defaultBlockState(), 0.3f);
		};
	}

	private static boolean corner(int x, int z, int r) {
		return Math.abs(x) == r && Math.abs(z) == r;
	}

	@Override
	void make() {
		float p = random.nextFloat();
		paint = p < 0.35f ? Paint.RED : p < 0.6f ? Paint.BLACK : p < 0.8f ? Paint.WHITE : Paint.STONE;
		int r = chance(0.4f) ? 3 : 2;                         // the tower: slim, or broad with floors in it
		boolean ruined = chance(0.22f);
		boolean hut = chance(0.55f);
		int height = (r == 3 ? 15 : 13) + random.nextInt(5);
		int plinth = r + 1;
		int reach = r + 2;                                     // the gallery, round the lamp room
		int lamp = r;
		int top = height + lamp + 7;

		fill(-reach - 1, 1, -reach - 1, reach + 1, top, reach + 1, Blocks.AIR.defaultBlockState());
		if (hut) fill(plinth, 1, -4, r + 8, 7, 4, Blocks.AIR.defaultBlockState());

		// The plinth: a stone skirt round the foot of the tower.
		for (int x = -plinth; x <= plinth; x++) for (int z = -plinth; z <= plinth; z++) {
			if (corner(x, z, plinth)) continue;
			foundation(x, 1, z, Blocks.COBBLESTONE.defaultBlockState());
			put(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
		}
		for (int[] c : ring(-plinth, -plinth, plinth, plinth)) {
			if (corner(c[0], c[1], plinth)) continue;
			put(c[0], 1, c[1], Blocks.STONE_BRICK_SLAB.defaultBlockState());
		}

		// The tower: rounded corners, banded; a slit of window here and there; the ladder inside.
		int wall = ruined ? height - 2 : height;
		for (int y = 1; y <= wall; y++) {
			for (int[] c : ring(-r, -r, r, r)) {
				if (corner(c[0], c[1], r)) continue;
				if (ruined && y > wall - 4 && !chance(1.0f - (y - (wall - 4)) * 0.22f)) continue;   // broken off, raggedly
				put(c[0], y, c[1], y <= 2 ? Blocks.STONE_BRICKS.defaultBlockState() : band(y, height));
			}
			if (y >= 4 && y < wall - 2 && y % 4 == 0) {
				put(0, y, r, ruined ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
				put(r, y + 2, 0, ruined ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
			}
			if (!ruined || y <= wall - 4) put(-1, y, r - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		// The door, on the side away from the ladder.
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		put(0, 1, -r, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(0, 2, -r, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		put(0, 1, -plinth, Blocks.AIR.defaultBlockState());       // the plinth is open in front of the door
		// A broad one has floors, with the ladder going up through them, and the keeper's things.
		if (r == 3) {
			for (int f = 6; f < wall - 3; f += 5) {
				for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
					if (x == -1 && z == 2) continue;
					put(x, f, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
				unsettle(-2, -2, 2, 1, f + 1, 1);
			}
		}

		if (ruined) {
			// What was up there is down here: the lamp room, the gallery, the top of the tower.
			container(1, 1, r - 1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.RUIN);
			unsettle(-1, -1, 1, 0, 1, 1);
			for (int i = 0; i < 18; i++) {
				double a = random.nextDouble() * Math.PI * 2;
				int d = plinth + 1 + random.nextInt(4);
				int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
				if (Math.abs(x) > reach + 1 || Math.abs(z) > reach + 1) continue;
				int g = ground(x, z) + 1;
				if (g > 3 || g < -6) continue;
				float f = random.nextFloat();
				put(x, g, z, f < 0.35f ? band(3, height) : f < 0.6f ? band(6, height)
						: f < 0.85f ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
			}
			steps(0, -plinth, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
			if (hut) hut(r);
			return;
		}

		// The gallery floor, wider than the tower, with the ladder's hatch; and its railing.
		int gallery = height + 1;
		for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) {
			if (corner(x, z, reach)) continue;
			boolean edge = Math.abs(x) == reach || Math.abs(z) == reach;
			put(x, gallery, z, edge ? Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)
					: Blocks.STONE_BRICKS.defaultBlockState());
		}
		put(-1, gallery, r - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		List<int[]> rail = new ArrayList<>();
		for (int[] c : ring(-reach, -reach, reach, reach)) if (!corner(c[0], c[1], reach) && chance(0.85f)) rail.add(c);
		connected(rail, gallery + 1, Blocks.SPRUCE_FENCE);

		// The lamp room: glass all round, a way out onto the gallery, the lamp gone dark.
		for (int y = gallery + 1; y <= gallery + 3; y++) {
			for (int[] c : ring(-lamp, -lamp, lamp, lamp)) {
				if (corner(c[0], c[1], lamp)) {
					put(c[0], y, c[1], Blocks.STONE_BRICKS.defaultBlockState());
				} else {
					put(c[0], y, c[1], chance(0.12f) ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
				}
			}
		}
		put(0, gallery + 1, -lamp, Blocks.AIR.defaultBlockState());
		put(0, gallery + 2, -lamp, Blocks.AIR.defaultBlockState());
		// Its lamp: dead, mostly. In some, somebody has lit it again, and it shows through the fog at night.
		put(0, gallery + 1, 0, chance(0.5f) ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.REDSTONE_LAMP.defaultBlockState());
		container(1, gallery + 1, -1, facing(Blocks.CHEST.defaultBlockState(), Direction.WEST), Loot.Kind.RUIN);
		if (chance(0.7f)) put(-1, gallery + 3, -1, Blocks.COBWEB.defaultBlockState());
		unsettle(-(lamp - 1), -(lamp - 1), lamp - 1, 0, gallery + 1, 1);

		// The cap: a stepped dome, and an iron spike where the vane was.
		int cap = gallery + 4;
		for (int k = 0; k < lamp; k++) {
			int rr = lamp - k;
			int y = cap + k;
			for (int[] c : ring(-rr, -rr, rr, rr)) {
				if (corner(c[0], c[1], rr)) {
					put(c[0], y, c[1], Blocks.STONE_BRICK_SLAB.defaultBlockState());
					continue;
				}
				Direction in = Math.abs(c[0]) == rr ? (c[0] > 0 ? Direction.WEST : Direction.EAST) : (c[1] > 0 ? Direction.NORTH : Direction.SOUTH);
				put(c[0], y, c[1], stairs(Blocks.STONE_BRICK_STAIRS, in));
			}
			if (rr > 1) fill(-rr + 1, y, -rr + 1, rr - 1, y, rr - 1, Blocks.STONE_BRICKS.defaultBlockState());
			else put(0, y, 0, Blocks.STONE_BRICKS.defaultBlockState());
		}
		put(0, cap + lamp, 0, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		put(0, cap + lamp + 1, 0, Blocks.IRON_BARS.defaultBlockState());
		steps(0, -plinth, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE.defaultBlockState());
		if (hut) hut(r);
	}

	/**
	 * The keeper's hut, against the plinth: stone, a slab roof, one window looking at the water,
	 * a stove gone cold, and the straw they slept on, still pressed flat.
	 */
	private void hut(int r) {
		int x0 = r + 2, x1 = r + 6, z0 = -2, z1 = 2;
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				put(x, 0, z, edge ? cobble : planks);
				foundation(x, 0, z, cobble);
				if (!edge) continue;
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				for (int y = 1; y <= 3; y++) put(x, y, z, corner ? post : old(stone, cobble, 0.3f));
			}
		}
		// Its door, facing the same way as the lighthouse's; a window on the far side.
		int dx = x0 + 2;
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		if (chance(0.7f)) {
			put(dx, 1, z0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			put(dx, 2, z0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		} else {
			fill(dx, 1, z0, dx, 2, z0, Blocks.AIR.defaultBlockState());      // the door is gone
		}
		put(x1, 2, 0, Blocks.GLASS_PANE.defaultBlockState()
				.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true));
		// The roof, a little over the walls; part of it fallen in, now and then.
		boolean holed = chance(0.35f);
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				if (holed && x >= x1 - 2 && z >= 0 && x <= x1 - 1 && z <= 1) continue;
				put(x, 4, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
			}
		}
		put(x1 - 1, 4, z0 + 1, cobble);
		put(x1 - 1, 5, z0 + 1, cobble);
		// Inside: the straw they slept on, against the back; a stove; their things in a barrel.
		BlockState straw = Blocks.HAY_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		put(x0 + 1, 1, z1 - 1, straw);
		put(x0 + 1, 1, z1 - 2, straw);
		put(x1 - 1, 1, z0 + 1, facing(Blocks.FURNACE.defaultBlockState(), Direction.WEST));
		container(x1 - 1, 1, z1 - 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Loot.Kind.HOME);
		unsettle(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 1);
		steps(dx, z0, Direction.NORTH, Blocks.STONE_BRICK_STAIRS, cobble);
	}
}
