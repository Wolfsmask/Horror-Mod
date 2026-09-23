package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** You say something in chat. Except you did not. Sometimes it is something you said an hour ago. */
public final class DoppelChatEvent extends HorrorEvent {
	public DoppelChatEvent() {
		super("doppel_chat", Tier.MINOR, 3, 4, 30);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.fakeMessages;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		String name = ctx.player.getName().getString();
		String line = pickLine(ctx);
		return new Timeline().at(0, p -> Cues.message(p, chat(name, line)));
	}

	static String pickLine(EventContext ctx) {
		List<String> heard = new ArrayList<>(ctx.data.heardChat);
		String line;
		if (!heard.isEmpty() && ctx.random.nextFloat() < 0.6f) {
			line = heard.get(ctx.random.nextInt(heard.size()));
		} else {
			List<String> lines = ctx.config.chatLines;
			line = lines.get(ctx.random.nextInt(lines.size()));
		}
		return line.replace("{player}", ctx.player.getName().getString());
	}

	/** Formatted exactly like a normal chat message: {@code <name> message}. */
	static Component chat(String name, String message) {
		return Component.translatable("chat.type.text", Component.literal(name), Component.literal(message));
	}
}
