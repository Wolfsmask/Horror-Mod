package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Night, and you are inside. Something walks across the roof: slow, heavy steps, in the sound of
 * whatever your roof is made of, from one side towards the other, and they stop right above you.
 * Then nothing at all for a long moment. Then one more.
 * <p>
 * Nothing is spawned; there is nothing up there when you go and look.
 */
public final class RoofEvent extends HorrorEvent {
	public static final String ID = "roof";
	private static final int STEPS = 9;
	private static final int STEP_TICKS = 12;

	public RoofEvent() {
		super(ID, Tier.MINOR, 2, 6, 20);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return s.sheltered() && !s.underground() && s.gloomy() && !s.inCombat() && !s.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		// The ceiling right above them, and the open air on top of the roof over it.
		BlockPos ceiling = null;
		for (int dy = 1; dy <= 6 && ceiling == null; dy++) {
			if (solid(world, eye.above(dy))) ceiling = eye.above(dy);
		}
		if (ceiling == null) return null;
		BlockPos top = null;
		for (int dy = 1; dy <= 6 && top == null; dy++) {
			if (!solid(world, ceiling.above(dy))) top = ceiling.above(dy);
		}
		if (top == null || !world.canSeeSky(top)) return null;

		// A line across the roof that ends right over their head.
		double angle = ctx.random.nextDouble() * Math.PI * 2.0;
		Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		List<BlockPos> steps = new ArrayList<>();
		for (int i = 0; i < STEPS; i++) {
			double along = (STEPS - 1 - i) * 0.9;
			BlockPos under = roofAt(world, BlockPos.containing(p.getX() + dir.x * along, top.getY(), p.getZ() + dir.z * along));
			if (under != null && (steps.isEmpty() || !steps.get(steps.size() - 1).equals(under))) steps.add(under);
		}
		if (steps.size() < 4) return null;

		Timeline t = new Timeline();
		int tick = 0;
		for (BlockPos under : steps) {
			t.at(tick, pl -> step(pl, under, 0.9f));
			tick += STEP_TICKS + ctx.random.nextInt(5);
		}
		BlockPos last = steps.get(steps.size() - 1);
		// It has stopped, right over you. A long nothing. Then it shifts its weight.
		t.at(tick + 90 + ctx.random.nextInt(60), pl -> step(pl, last, 1.0f));
		return t;
	}

	private static void step(ServerPlayer player, BlockPos under, float volume) {
		Vec3 at = Vec3.atCenterOf(under).add(0, 0.5, 0);
		Cues.sound(player, player.level().getBlockState(under).getSoundType().getStepSound(), SoundSource.BLOCKS, at, volume, 0.6f);
	}

	/** The top of the roof in this column: a solid block with open air on it, near {@code from}'s height. */
	@Nullable
	private static BlockPos roofAt(ServerLevel world, BlockPos from) {
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos q = from.above(dy);
			if (solid(world, q) && !solid(world, q.above())) return q.immutable();
		}
		return null;
	}

	private static boolean solid(ServerLevel world, BlockPos pos) {
		return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}
}
