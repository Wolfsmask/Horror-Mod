package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * On a server with friends: one of them says something in chat, only to you. They never said it.
 * "come here, I found something." "why are you standing there." "who is that behind you"
 */
public final class TeammateEvent extends HorrorEvent {
	public static final String ID = "teammate";
	private static final String[] LINES = {"come here, I found something", "where did you go", "why are you just standing there",
			"who's that behind you", "stop following me", "did you build that thing by the trees?", "I'm at your house, let me in",
			"are you still there?", "I can see you from here", "don't come over here"};

	public TeammateEvent() {
		super(ID, Tier.MINOR, 2, 4, 25);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.fakeMessages;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !others(ctx.player).isEmpty() && !ctx.situation.inCombat();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		List<ServerPlayer> others = others(ctx.player);
		if (others.isEmpty()) return null;
		ServerPlayer friend = others.get(ctx.random.nextInt(others.size()));
		// Only someone not right next to them: they would see the lie at once.
		if (friend.distanceTo(ctx.player) < 24.0) return null;
		String name = friend.getName().getString();
		String line = LINES[ctx.random.nextInt(LINES.length)];
		return new Timeline().at(0, p -> Cues.message(p, DoppelChatEvent.chat(name, line)));
	}

	private static List<ServerPlayer> others(ServerPlayer player) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			if (p != player && !p.isSpectator()) out.add(p);
		}
		return out;
	}
}
