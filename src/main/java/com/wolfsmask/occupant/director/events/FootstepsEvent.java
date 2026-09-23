package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Footsteps behind you, on whatever you are standing on, slowly getting closer.
 * They stop the instant you turn around.
 */
public final class FootstepsEvent extends HorrorEvent {
	public FootstepsEvent() {
		super("footsteps", Tier.AMBIENT, 1, 10, 6);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return ctx.player.onGround() && !s.inWater() && !s.sprinting() && !s.inCombat() && !s.busy();
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		return ctx.situation.still() ? 1.6 : 1.0;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		Vec3 back = Sight.flatLook(p).scale(-1);
		for (int attempt = 0; attempt < 10; attempt++) {
			Vec3 dir = Sight.rotateY(back, (ctx.random.nextDouble() - 0.5) * 50.0);
			Vec3 start = p.position().add(dir.scale(6.5 + ctx.random.nextDouble() * 2.0));
			BlockPos ground = Spots.groundNear(ctx.world, Mth.floor(start.x), Mth.floor(p.getY()), Mth.floor(start.z), 3);
			if (ground == null) continue;
			Vec3 at = new Vec3(start.x, ground.getY(), start.z);
			if (Sight.yawAngleTo(p, at) < 120.0) continue;
			return new Steps(at, 4 + ctx.random.nextInt(4));
		}
		return null;
	}

	private static final class Steps implements Sequence {
		private Vec3 pos;
		private int remaining;
		private int timer;

		Steps(Vec3 start, int count) {
			this.pos = start;
			this.remaining = count;
		}

		@Override
		public boolean tick(ServerPlayer p) {
			if (p.isSprinting() || Sight.yawAngleTo(p, pos) < 75.0) return false;
			if (--timer > 0) return true;
			timer = 9 + p.getRandom().nextInt(3);

			Vec3 toPlayer = new Vec3(p.getX() - pos.x, 0, p.getZ() - pos.z);
			double dist = toPlayer.length();
			if (dist < 2.6) return false;
			Vec3 next = pos.add(toPlayer.normalize().scale(Math.min(0.75, dist - 2.5)));
			BlockPos ground = Spots.groundNear(p.level(), Mth.floor(next.x), Mth.floor(pos.y), Mth.floor(next.z), 2);
			if (ground == null) return false;
			pos = new Vec3(next.x, ground.getY(), next.z);

			BlockState floor = p.level().getBlockState(ground.below());
			SoundType group = floor.getSoundType();
			Cues.sound(p, group.getStepSound(), SoundSource.PLAYERS, pos, group.getVolume() * 0.22f, group.getPitch());
			return --remaining > 0;
		}
	}
}
