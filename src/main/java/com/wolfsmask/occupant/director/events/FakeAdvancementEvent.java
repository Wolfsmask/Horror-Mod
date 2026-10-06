package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * "{player} has made the advancement [Not Alone]." The game's own words, in the game's own
 * green, for something the player never did. Nobody else sees it, and there is no such
 * advancement to look up afterwards.
 */
public final class FakeAdvancementEvent extends HorrorEvent {
	public static final String ID = "advancement";
	/** By act, from the second on: the titles get less polite. */
	private static final String[][] TITLES = {
			{"Not Alone", "Company", "Night Shift", "Someone Else's Footsteps", "Housemate"},
			{"Seen", "It Knows Your Name", "Don't Turn Around", "Eye Contact", "Leave the Light On"},
			{"Too Late", "It Is Inside", "Last One Left", "Found You", "Stay Where You Are"}};

	public FakeAdvancementEvent() {
		super(ID, Tier.MINOR, 2, 3, 40);
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
		String[] titles = TITLES[Math.max(0, Math.min(TITLES.length - 1, ctx.act() - 2))];
		String title = titles[ctx.random.nextInt(titles.length)];
		Component text = Component.translatable("chat.type.advancement.task", ctx.player.getDisplayName(),
				Component.literal("[" + title + "]").withStyle(ChatFormatting.GREEN));
		return new Timeline().at(0, p -> Cues.message(p, text));
	}
}
