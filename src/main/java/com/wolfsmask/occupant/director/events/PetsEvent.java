package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Your pets stop, and stare at an empty corner behind you, and will not look away. The cat hisses
 * at it. There is nothing there. Then there is nothing there for them either.
 */
public final class PetsEvent extends HorrorEvent {
	public static final String ID = "pets";

	public PetsEvent() {
		super(ID, Tier.MINOR, 1, 5, 25);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy() && !pets(ctx.player).isEmpty();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		List<TamableAnimal> pets = pets(p);
		if (pets.isEmpty()) return null;
		// Somewhere a few blocks behind the player, at head height.
		Vec3 corner = p.position().add(Sight.rotateY(Sight.flatLook(p), 150 + ctx.random.nextInt(60)).scale(5.0)).add(0, 1.6, 0);
		return new Staring(pets, corner, 140 + ctx.random.nextInt(80));
	}

	static List<TamableAnimal> pets(ServerPlayer player) {
		return player.level().getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(16.0),
				a -> a.isAlive() && a.isTame());
	}

	private static final class Staring implements Sequence {
		private final List<TamableAnimal> pets;
		private final Vec3 corner;
		private final int length;
		private int age;

		Staring(List<TamableAnimal> pets, Vec3 corner, int length) {
			this.pets = pets;
			this.corner = corner;
			this.length = length;
		}

		@Override
		public boolean tick(ServerPlayer player) {
			age++;
			for (TamableAnimal a : pets) {
				if (!a.isAlive() || a.level() != player.level()) continue;
				a.getNavigation().stop();
				a.getLookControl().setLookAt(corner.x, corner.y, corner.z, 40.0f, 40.0f);
				if (age == 20 && a.getType() == EntityType.CAT) {
					Cues.sound(player, SoundEvents.CAT_HISS, SoundSource.NEUTRAL, a.position(), 0.8f, 1.0f);
				}
			}
			return age < length;
		}
	}
}
