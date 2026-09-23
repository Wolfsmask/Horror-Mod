package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/**
 * "... joined the game." Nobody did. Early on the name is almost yours.
 * Later it is exactly yours. Nobody else on the server sees the message.
 */
public final class FakeJoinEvent extends HorrorEvent {
	public FakeJoinEvent() {
		super("fake_join", Tier.MINOR, 2, 4, 45);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.fakeMessages;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat();
	}

	@Override
	public Sequence begin(EventContext ctx) {
		String real = ctx.player.getName().getString();
		String name = ctx.act() >= 3 ? real : almost(real, ctx.random);
		int leaveAt = 20 * (30 + ctx.random.nextInt(60));

		Timeline t = new Timeline()
				.at(0, p -> Cues.message(p, Component.translatable("multiplayer.player.joined", name).withStyle(ChatFormatting.YELLOW)))
				.at(leaveAt, p -> Cues.message(p, Component.translatable("multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW)));
		if (ctx.act() >= 3 && ctx.random.nextFloat() < 0.4f) {
			String line = DoppelChatEvent.pickLine(ctx);
			t.at(leaveAt / 2, p -> Cues.message(p, DoppelChatEvent.chat(name, line)));
		}
		return t;
	}

	/** The player's name, very slightly wrong. */
	static String almost(String name, RandomSource random) {
		if (name.length() < 3) return name + name.charAt(name.length() - 1);
		int i = 1 + random.nextInt(name.length() - 2);
		if (random.nextBoolean()) {
			return name.substring(0, i) + name.charAt(i) + name.substring(i);
		}
		char[] c = name.toCharArray();
		char tmp = c[i];
		c[i] = c[i + 1];
		c[i + 1] = tmp;
		String swapped = new String(c);
		return swapped.equals(name) ? name.substring(0, i) + name.charAt(i) + name.substring(i) : swapped;
	}
}
