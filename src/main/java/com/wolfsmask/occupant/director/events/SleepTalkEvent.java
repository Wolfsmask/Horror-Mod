package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Party;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * They wake, and are told they talk in their sleep. Then what they kept saying: something they
 * said in the chat, days ago, or something they never said at all. Their friends near were told
 * too.
 */
public final class SleepTalkEvent extends HorrorEvent {
	public static final String ID = "sleep_talk";
	private static final String[] NEVER_SAID = {"it's under the floor", "don't open the door", "I can see it from here",
			"it's still in the room", "let me up", "it knows where we sleep", "count them again", "it's wearing my face",
			"I'm not the one in the bed", "it's standing right there"};

	public SleepTalkEvent() {
		super(ID, Tier.MINOR, 2, 1, 45);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.screenWhispers;
	}

	@Override
	public boolean hookOnly() {
		return true;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.data.isOnCooldown(ID);
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		List<String> heard = new ArrayList<>(ctx.data.heardChat);
		String line = !heard.isEmpty() && ctx.random.nextFloat() < 0.65f
				? heard.get(ctx.random.nextInt(heard.size()))
				: NEVER_SAID[ctx.random.nextInt(NEVER_SAID.length)];
		String name = ctx.player.getName().getString();
		Component said = Component.literal("You kept saying \"" + line + "\".").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		Component toThem = Component.literal(name + " was talking in their sleep.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		return new Timeline()
				.at(30, p -> Cues.whisperOnly(p, "You talk in your sleep.", 110))
				.at(140, p -> {
					Cues.messageOnly(p, said);
					for (ServerPlayer o : Party.others(p, Party.NEAR)) Cues.messageOnly(o, toThem);
				});
	}
}
