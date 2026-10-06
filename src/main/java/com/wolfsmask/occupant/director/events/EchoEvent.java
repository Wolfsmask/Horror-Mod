package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Underground, your own footsteps come back to you, a beat late, from where you just were. For
 * a little while every step you take is taken again behind you. Then you stop, and the last one
 * takes one more.
 */
public final class EchoEvent extends HorrorEvent {
	public static final String ID = "echo";

	public EchoEvent() {
		super(ID, Tier.AMBIENT, 1, 4, 15);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.situation.underground() && !ctx.situation.inCombat() && !ctx.situation.still();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		return new Echo(240 + ctx.random.nextInt(120));
	}

	private static final class Echo implements Sequence {
		private static final int LAG = 14;
		private final int length;
		private final Deque<long[]> steps = new ArrayDeque<>();
		private Vec3 last;
		private int age;
		private boolean extra;

		Echo(int length) {
			this.length = length;
		}

		@Override
		public boolean tick(ServerPlayer player) {
			age++;
			Vec3 here = player.position();
			if (last == null) last = here;
			// Every step they take is noted...
			if (age < length && player.onGround() && horizontal(here, last) > 1.1) {
				BlockPos under = player.blockPosition().below();
				steps.addLast(new long[]{age + LAG, under.asLong()});
				last = here;
			}
			// ...and taken again, a beat later, where they were.
			while (!steps.isEmpty() && steps.peekFirst()[0] <= age) {
				step(player, BlockPos.of(steps.pollFirst()[1]), 0.55f);
			}
			if (age >= length && steps.isEmpty()) {
				if (!extra && last != null) {
					extra = true;
					step(player, BlockPos.containing(last).below(), 0.45f);    // one more, after they stopped
				}
				return false;
			}
			return true;
		}

		private static double horizontal(Vec3 a, Vec3 b) {
			double dx = a.x - b.x, dz = a.z - b.z;
			return Math.sqrt(dx * dx + dz * dz);
		}

		private static void step(ServerPlayer player, BlockPos under, float volume) {
			Cues.sound(player, player.level().getBlockState(under).getSoundType().getStepSound(), SoundSource.PLAYERS,
					Vec3.atCenterOf(under).add(0, 0.5, 0), volume, 0.95f);
		}
	}
}
