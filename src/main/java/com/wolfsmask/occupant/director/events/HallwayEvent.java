package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import com.wolfsmask.occupant.world.House;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * You walk into a house, and off to the side, down the dark hallway, it is standing there. Not in
 * the middle of it: back in the dark, half behind the edge of the doorway, so you see a pale face
 * and a hand of long legs and then you are not sure.
 * <p>
 * It happens in the abandoned houses that generate in the world the first time you step into one,
 * and in any other dark building (a village house, your own) now and then.
 */
public final class HallwayEvent extends HorrorEvent {
	public static final String ID = "hallway";

	public HallwayEvent() {
		super(ID, Tier.MAJOR, 1, 8, 6);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.aloneEnough() && s.sheltered() && !s.underground() && !s.inCombat() && !s.busy() && !s.inWater();
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		return House.isInside(ctx.world, ctx.player.blockPosition()) ? 12.0 : 2.5;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		BlockPos spot = find(ctx.player, true);
		if (spot == null) spot = find(ctx.player, false);
		if (spot == null) return null;
		OccupantEntity e = ctx.haunt.spawnOccupant(ctx.player, spot, OccupantEntity.Mode.STARE,
				ctx.act() <= 1 ? OccupantEntity.Form.VEILED : OccupantEntity.Form.REVEALED);
		if (e == null) return null;
		int reaction = 8 + ctx.random.nextInt(18);
		return new WatcherSequence(ctx.haunt, e, reaction, true, 4.5, 700 + ctx.random.nextInt(400));
	}

	/**
	 * The best dark, narrow, roofed-over spot off to the player's side, three to fourteen blocks
	 * away. With {@code peek}, only places where most of it is hidden and its head shows past the
	 * edge of something count.
	 */
	@Nullable
	public static BlockPos find(ServerPlayer p, boolean peek) {
		ServerLevel world = p.level();
		BlockPos at = p.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -14; dx <= 14; dx++) {
			for (int dz = -14; dz <= 14; dz++) {
				double flat = Math.sqrt(dx * dx + dz * dz);
				if (flat < 3.0 || flat > 14.0) continue;
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos pos = at.offset(dx, dy, dz);
					if (!Spots.canStand(world, pos)) continue;
					Vec3 base = Vec3.atBottomCenterOf(pos);
					double side = Sight.yawAngleTo(p, base);
					if (side < 25.0 || side > 110.0) continue;          // off to the side, not ahead
					if (world.canSeeSky(pos.above())) continue;         // indoors
					if (!Spots.isDark(world, pos.above())) continue;
					if (!enclosed(world, pos)) continue;
					double height = headroom(world, pos);
					if (height > 6.0) continue;
					int mask = Sight.visibleParts(p, base, height);
					int seen = Integer.bitCount(mask);
					boolean head = (mask & (0b111 << 12 | 0b111 << 9)) != 0;
					if (seen == 0) continue;
					// Its head past the edge, and one whole side of it, knees to crown, behind a wall or a
					// doorframe. Furniture is too low to hide that, so a spot behind a table never counts.
					if (peek && (!head || seen > 8 || !sideHidden(mask))) continue;
					// Not right up close, not far off: about seven blocks, at the edge of the screen
					// where you catch it out of the corner of your eye, and as little of it as can be.
					double score = Math.abs(flat - 7.0) * 0.6 + Math.abs(side - 45.0) * 0.04 + seen * 0.35;
					if (score < bestScore) {
						bestScore = score;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	/** One column of the sample points (rows 1 to 4, knee height up) entirely out of sight. */
	private static boolean sideHidden(int mask) {
		for (int col = 0; col < 3; col++) {
			boolean any = false;
			for (int row = 1; row < 5; row++) any |= (mask & (1 << (row * 3 + col))) != 0;
			if (!any) return true;
		}
		return false;
	}

	/** Walled in on at least two sides within a few blocks: a hallway, a gap, a doorway, a corner. */
	private static boolean enclosed(ServerLevel world, BlockPos pos) {
		BlockPos chest = pos.above();
		int walls = 0;
		if (wallWithin(world, chest, 1, 0)) walls++;
		if (wallWithin(world, chest, -1, 0)) walls++;
		if (wallWithin(world, chest, 0, 1)) walls++;
		if (wallWithin(world, chest, 0, -1)) walls++;
		return walls >= 2;
	}

	private static boolean wallWithin(ServerLevel world, BlockPos from, int sx, int sz) {
		for (int i = 1; i <= 3; i++) {
			BlockPos p = from.offset(sx * i, 0, sz * i);
			if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) return true;
		}
		return false;
	}

	/** Blocks of clear space over its feet, up to the ceiling, which is about how tall it is drawn. */
	private static double headroom(ServerLevel world, BlockPos feet) {
		for (int i = 2; i < 8; i++) {
			BlockPos p = feet.above(i);
			if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) return Math.min(4.2, i - 0.15);
		}
		return 8.0;
	}
}
