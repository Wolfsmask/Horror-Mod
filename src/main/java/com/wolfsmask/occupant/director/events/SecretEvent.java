package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Party;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * With friends near: a line on their screen and no one else's, about one of the others. "Don't
 * tell Alex." Nobody else saw it. Whether they say anything is up to them; either way, they look
 * at their friend a little differently afterwards.
 */
public final class SecretEvent extends HorrorEvent {
	public static final String ID = "secret";
	private static final String[] LINES = {"Don't tell {friend}.", "{friend} doesn't know yet.", "Stay away from {friend}.",
			"Ask {friend} what they saw.", "{friend} was looking at me.", "Not {friend}. You.", "{friend} is next."};

	public SecretEvent() {
		super(ID, Tier.MINOR, 2, 4, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.screenWhispers;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !Party.others(ctx.player, Party.NEAR).isEmpty();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		List<ServerPlayer> others = Party.others(ctx.player, Party.NEAR);
		if (others.isEmpty()) return null;
		String name = others.get(ctx.random.nextInt(others.size())).getName().getString();
		String line = LINES[ctx.random.nextInt(LINES.length)].replace("{friend}", name);
		// Theirs alone: the friend it names never sees it.
		return new Timeline().at(0, p -> Cues.whisperOnly(p, line, 90));
	}
}
