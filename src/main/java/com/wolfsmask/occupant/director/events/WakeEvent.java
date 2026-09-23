package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** You wake up. It is standing at the foot of your bed. Only started by the wake-up hook. */
public final class WakeEvent extends HorrorEvent {
	public static final String ID = "wake";

	public WakeEvent() {
		super(ID, Tier.PEAK, 3, 1, 30);
	}

	@Override
	public boolean hookOnly() {
		return true;
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.jumpscares;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return ctx.act() >= 3 && ctx.aloneEnough() && Director.peakReady(ctx.data) && !ctx.data.isOnCooldown(ID);
	}

	@Override
	public Sequence begin(EventContext ctx) {
		return new Waking(ctx.haunt);
	}

	private static final class Waking implements Sequence {
		private static final int SETTLE_TICKS = 12;
		private final Haunt haunt;
		@Nullable
		private OccupantEntity entity;
		private int age;
		private int lookTicks;

		Waking(Haunt haunt) {
			this.haunt = haunt;
		}

		@Override
		public boolean tick(ServerPlayerEntity p) {
			age++;
			if (entity == null) {
				// Give the player a moment to get up and for their view to settle.
				if (age < SETTLE_TICKS) return true;
				BlockPos spot = Spots.aroundPlayer(p, p.getRandom(), 2.5, 4.5, 0, 35, false, 20, pos -> {
					Vec3d base = Vec3d.ofBottomCenter(pos);
					return Math.abs(pos.getY() - p.getBlockY()) <= 1 && Sight.hasLineOfSight(p, base.add(0, 1.6, 0));
				});
				if (spot == null) return false;
				entity = haunt.spawnOccupant(p, spot, OccupantEntity.Mode.STARE, OccupantEntity.Form.HOLLOW);
				if (entity == null) return false;
				entity.setFootsteps(false);
				return true;
			}

			if (entity.hasVanished() || p.getWorld() != entity.getWorld()) return false;
			entity.keepAlive();
			lookTicks = Sight.isLookingAt(p, entity) ? lookTicks + 1 : 0;
			if (lookTicks >= 8) {
				haunt.data.encounters++;
				Cues.soundAtEars(p, ModSounds.DRONE, SoundCategory.AMBIENT, 0.7f, 1.2f);
				Cues.effect(p, ScreenEffectPayload.STATIC, 8, 0.4f);
				return false;
			}
			return age < SETTLE_TICKS + 100;
		}

		@Override
		public void end() {
			if (entity != null) entity.vanish();
		}
	}
}
