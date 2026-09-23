package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Situation;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A sound you already know, in a place it has no business being: a creeper's fuse right behind
 * you at night, a door in a house with no door, a zombie two blocks away in a sealed room.
 * <p>
 * This is the most deniable thing the Occupant does and probably the most effective. A noise you
 * do not recognise is a mod; a noise you recognise perfectly, coming from the wrong place, is
 * your own game turning on you. You will turn around. There will be nothing there, and there is
 * no way to tell whether something got away or whether you imagined where it came from.
 * <p>
 * Nothing is ever spawned, so there is nothing to find, and it never hurts anyone.
 */
public final class FamiliarSoundEvent extends HorrorEvent {
	public static final String ID = "familiar";

	/** Sounds anyone who plays this game knows in their bones. */
	private record Cue(SoundEvent sound, float volume, float pitch, boolean close) {
	}

	private static final Cue[] CUES = {
			// The fuse. Nothing is more likely to make someone spin around.
			new Cue(SoundEvents.CREEPER_PRIMED.value(), 0.7f, 1.0f, true),
			new Cue(SoundEvents.CREEPER_PRIMED.value(), 0.5f, 1.05f, false),
			// Things that live in caves, at the surface, where they should not be.
			new Cue(SoundEvents.ZOMBIE_AMBIENT.value(), 0.5f, 0.85f, false),
			new Cue(SoundEvents.SKELETON_AMBIENT.value(), 0.45f, 0.9f, false),
			new Cue(SoundEvents.SPIDER_AMBIENT.value(), 0.5f, 0.95f, false),
			// A door in your base, when you are the only one in it.
			new Cue(SoundEvents.WOODEN_DOOR_OPEN.value(), 0.8f, 1.0f, true),
			new Cue(SoundEvents.WOODEN_DOOR_CLOSE.value(), 0.8f, 1.0f, false),
			new Cue(SoundEvents.CHEST_OPEN.value(), 0.6f, 1.0f, true),
			// Someone else working, somewhere behind you.
			new Cue(SoundEvents.STONE_BREAK.value(), 0.7f, 0.9f, false),
			new Cue(SoundEvents.ITEM_PICKUP.value(), 0.5f, 1.0f, true),
	};

	public FamiliarSoundEvent() {
		super(ID, Tier.AMBIENT, 1, 16, 4);
	}

	@Override
	public boolean fits(EventContext ctx) {
		Situation s = ctx.situation;
		return !s.inCombat() && !s.busy() && !s.sprinting();
	}

	@Override
	public double situationalWeight(EventContext ctx) {
		Situation s = ctx.situation;
		// At night, indoors, alone and standing still is when your own game sounds frighten you.
		double w = s.night() ? 1.8 : 0.7;
		if (s.sheltered()) w *= 1.4;
		if (s.still()) w *= 1.3;
		return w;
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		Cue cue = CUES[ctx.random.nextInt(CUES.length)];

		// Always behind, never in front: it must be a thing you have to turn around to look for.
		Vec3 back = Sight.flatLook(p).scale(-1);
		double distance = cue.close() ? 2.5 + ctx.random.nextDouble() * 2.5 : 7.0 + ctx.random.nextDouble() * 9.0;
		Vec3 at = null;
		for (int attempt = 0; attempt < 8; attempt++) {
			Vec3 dir = Sight.rotateY(back, (ctx.random.nextDouble() - 0.5) * 70.0);
			Vec3 want = p.position().add(dir.scale(distance));
			BlockPos ground = Spots.groundNear(ctx.world, Mth.floor(want.x), Mth.floor(p.getY()), Mth.floor(want.z), 4);
			if (ground == null) continue;
			// Behind the player, and from a spot that is really there rather than inside a wall.
			if (Sight.yawAngleTo(p, want) < 100.0) continue;
			at = new Vec3(want.x, ground.getY() + 0.5, want.z);
			break;
		}
		if (at == null) return null;

		Vec3 from = at;
		Timeline timeline = new Timeline().at(0, player ->
				Cues.sound(player, cue.sound(), SoundSource.HOSTILE, from, cue.volume(), cue.pitch()));

		// Half the time, one more from a slightly different place: something moved between them.
		if (ctx.random.nextFloat() < 0.45f) {
			Vec3 second = from.add((ctx.random.nextDouble() - 0.5) * 6.0, 0, (ctx.random.nextDouble() - 0.5) * 6.0);
			int delay = 25 + ctx.random.nextInt(45);
			timeline.at(delay, player ->
					Cues.sound(player, cue.sound(), SoundSource.HOSTILE, second, cue.volume() * 0.8f, cue.pitch()));
		}
		return timeline;
	}
}
