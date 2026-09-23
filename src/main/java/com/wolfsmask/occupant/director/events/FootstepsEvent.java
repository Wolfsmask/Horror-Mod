package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
		return ctx.player.isOnGround() && !s.inWater() && !s.sprinting() && !s.inCombat() && !s.busy();
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		return ctx.situation.still() ? 1.6 : 1.0;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayerEntity p = ctx.player;
		Vec3d back = Sight.flatLook(p).multiply(-1);
		for (int attempt = 0; attempt < 10; attempt++) {
			Vec3d dir = Sight.rotateY(back, (ctx.random.nextDouble() - 0.5) * 50.0);
			Vec3d start = p.getPos().add(dir.multiply(6.5 + ctx.random.nextDouble() * 2.0));
			BlockPos ground = Spots.groundNear(ctx.world, MathHelper.floor(start.x), MathHelper.floor(p.getY()), MathHelper.floor(start.z), 3);
			if (ground == null) continue;
			Vec3d at = new Vec3d(start.x, ground.getY(), start.z);
			if (Sight.yawAngleTo(p, at) < 120.0) continue;
			return new Steps(at, 4 + ctx.random.nextInt(4));
		}
		return null;
	}

	private static final class Steps implements Sequence {
		private Vec3d pos;
		private int remaining;
		private int timer;

		Steps(Vec3d start, int count) {
			this.pos = start;
			this.remaining = count;
		}

		@Override
		public boolean tick(ServerPlayerEntity p) {
			if (p.isSprinting() || Sight.yawAngleTo(p, pos) < 75.0) return false;
			if (--timer > 0) return true;
			timer = 9 + p.getRandom().nextInt(3);

			Vec3d toPlayer = new Vec3d(p.getX() - pos.x, 0, p.getZ() - pos.z);
			double dist = toPlayer.length();
			if (dist < 2.6) return false;
			Vec3d next = pos.add(toPlayer.normalize().multiply(Math.min(0.75, dist - 2.5)));
			BlockPos ground = Spots.groundNear(p.getServerWorld(), MathHelper.floor(next.x), MathHelper.floor(pos.y), MathHelper.floor(next.z), 2);
			if (ground == null) return false;
			pos = new Vec3d(next.x, ground.getY(), next.z);

			BlockState floor = p.getWorld().getBlockState(ground.down());
			BlockSoundGroup group = floor.getSoundGroup();
			Cues.sound(p, group.getStepSound(), SoundCategory.PLAYERS, pos, group.getVolume() * 0.22f, group.getPitch());
			return --remaining > 0;
		}
	}
}
