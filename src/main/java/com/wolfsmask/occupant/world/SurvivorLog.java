package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.compat.Compat;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The survivor's log: the pages somebody kept, the last time it lived in a world. One turns up in
 * every chest and barrel that nobody has opened, always the next page for whoever opens it, so it
 * reads in order however the places are found.
 */
public final class SurvivorLog {
	private static final String AUTHOR = "unknown";
	private static final List<String> PAGES = List.of(
			"Day 1.\n\nFound this place empty. Door open, food still on the table. Whoever lived here left in a hurry.\n\nI'll stay a night or two.",
			"Day 3.\n\nThere is someone at the treeline in the evenings. Tall. Too tall.\n\nIt doesn't move when I wave.\n\nI stopped waving.",
			"Day 4.\n\nTried to count its legs through the window. Lost count.\n\nI keep the light low now.",
			"Day 6.\n\nIt was closer this morning. Same place it stood yesterday, only closer. As if I had walked towards it in my sleep.",
			"Day 7.\n\nIf you are reading this, you have the same problem I did.\n\nDon't look at it for long. It gets braver when you look.",
			"Day 9.\n\nHeard my own footsteps on the path behind me.\n\nI was standing still.",
			"Day 11.\n\nThe torches by the door were gone when I woke. Set out in a line towards the trees.\n\nPointing at me.",
			"Day 12.\n\nIt has a face like ours. Mostly.\n\nThe mouth doesn't stop.",
			"Day 14.\n\nSomeone knocked tonight. Three times, then three more.\n\nNobody else lives within a day's walk of here.",
			"Day 15.\n\nI tried to leave. Every path came back round to here.\n\nIt is always standing at the edge of what I can see.",
			"Day 16.\n\nI've moved everything I have left to a camp of my own, out where it can't stand behind the trees.\n\n{camp}\n\nIf I don't come back, that's where I'll be.",
			"Day 17.\n\nI don't think it wants to kill me.\n\nI think it wants to BE me. It practises my walk at night.",
			"Day 18.\n\nDon't sleep in the dark rooms. Don't go down the hallway.\n\nIf it is in the hallway, it has already seen you.",
			"Day ??\n\nIt's watching me write this. I can see it in the window glass.\n\nIt's smiling.\n\nIt's coming.",
			"it is coming it is coming it is coming it is coming it is coming\n\nit is already here\n\nit is reading this with you");

	private SurvivorLog() {
	}

	/** The page that says where the last camp is. */
	public static final int CAMP_PAGE = 11;

	/**
	 * Whether the next page can be found yet. The log keeps pace with the story rather than with
	 * how fast somebody loots: a few pages early on, the page that says where the last camp is not
	 * before the third act, and a little while between any two. A chest opened too soon has its
	 * things in it but no page; the page can turn up in it later.
	 */
	public static boolean ready(com.wolfsmask.occupant.director.HauntData d, double pace) {
		int next = d.logsFound + 1;
		int allowed = switch (d.act) {
			case 0, 1 -> 3;
			case 2 -> 7;
			case 3 -> CAMP_PAGE;
			default -> Integer.MAX_VALUE;
		};
		if (next > allowed) return false;
		int now = (int) (d.playTicks / 20);
		int gap = (int) (150 * pace);                           // two and a half minutes, at the story's pace
		return d.lastPageAt < 0 || now - d.lastPageAt >= gap;
	}

	/** How many pages there are before they run out. */
	public static int length() {
		return PAGES.size();
	}

	/** Page {@code n} (from 1) as a written book, for {@code reader}. */
	public static ItemStack page(int n, String reader) {
		return page(n, reader, null);
	}

	/** As {@link #page(int, String)}; {@code camp} is where the last camp is, if it was built. */
	public static ItemStack page(int n, String reader, int[] camp) {
		if (n >= 1 && n <= PAGES.size()) {
			String text = PAGES.get(n - 1).replace("{camp}", camp != null
					? "It's at x " + camp[0] + ", z " + camp[1] + ". I scratched the numbers into the table so I'd remember."
					: "Out past the fog. I don't remember how far any more.");
			return Compat.writtenBook("Survivor's log, page " + n, AUTHOR, List.of(text));
		}
		// The log has run out. What is left is not in the same hand.
		return Compat.writtenBook("A torn page", AUTHOR, List.of(
				"The rest of the pages have been torn out.\n\nAt the bottom, very neatly, in a different hand:\n\nI SEE YOU, " + reader.toUpperCase() + "."));
	}

	/** The last thing they wrote, left at their last camp. */
	public static ItemStack finalEntry(String reader) {
		return Compat.writtenBook("Survivor's log, the last page", AUTHOR, List.of(
				"Last day.\n\nIt stood at the edge of the camp all night and I sat by the fire and let it look.\n\nI think it has what it needs now.",
				"It isn't going to kill anyone.\n\nIt's going to wear them.\n\nIf you are reading this, it has started on you. "
						+ "Don't let it see you read.\n\nI'm sorry, " + reader + ". I'm so sorry."));
	}

	/** Found after the last night: in a hand like the survivor's, and like the reader's. */
	public static ItemStack pageAfter(String reader) {
		return Compat.writtenBook("A page in your handwriting", reader, List.of(
				"It let me go.\n\nIt said it would come back when it had learned the rest of me.\n\nI don't remember writing this."));
	}
}
