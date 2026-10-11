package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Ask the dark something in the chat ("hello?", "is anyone there", "what was that") and, now and
 * then, a few seconds later, it answers. On the screen of the one who asked, and no one else's:
 * their friends read the question, and only they read the answer.
 */
final class Replies {
	private static final String ID = "reply";
	private static final String LEFT = "one_less";
	/** Not every time: something that always answers is a game, not something in the dark with you. */
	private static final float CHANCE = 0.6f;
	private static final long AGAIN_AFTER = 20L * 60 * 12;

	private static final String GREET = "(h+e+l+o+|hi+|hey+)( there)?( anyone)?";
	private static final String[] THERE = {"anyone there", "anybody there", "someone there", "somebody there", "whos there",
			"who is there", "who s there", "are you there", "anyone here", "is someone here", "im not alone"};
	private static final String[] WHO = {"who are you", "what are you", "what is it", "who is it"};
	private static final String[] WANT = {"what do you want", "what does it want", "what you want"};
	private static final String[] WHAT = {"what was that", "did you see that", "did you hear that", "whats that",
			"what is that", "what the", "did anyone see", "you see that", "you hear that"};
	private static final String[] LEAVE = {"go away", "leave me alone", "get away", "stop following me", "leave us alone"};

	private Replies() {
	}

	/** They said this. Perhaps, in a little while, it says something back. */
	static void heard(Haunt h, ServerPlayer player, String said) {
		HauntData d = h.data;
		if (d.act < 2 || h.reply != null || d.isOnCooldown(ID) || !OccupantConfig.get().screenWhispers) return;
		RandomSource random = player.getRandom();
		String[] lines = answers(normal(said), d.act);
		if (lines == null || random.nextFloat() >= CHANCE) return;
		h.reply = lines[random.nextInt(lines.length)];
		// Long enough that it is not a reply to a message; short enough that they know it is.
		h.replyAt = d.playTicks + 50 + random.nextInt(90);
		d.cooldowns.put(ID, d.playTicks + AGAIN_AFTER);
	}

	/**
	 * Someone they were with has left the world: a little later, perhaps, it counts for them.
	 * {@code were}: how many of them there were, the one who left included.
	 */
	static void oneLess(Haunt h, ServerPlayer player, int were) {
		HauntData d = h.data;
		if (d.act < 2 || h.reply != null || d.isOnCooldown(LEFT) || !OccupantConfig.get().screenWhispers) return;
		RandomSource random = player.getRandom();
		if (random.nextFloat() >= 0.5f) return;
		int now = were - 1;
		String[] lines = now == 1
				? new String[]{"Just you now.", "Just us now.", "There were " + count(were) + " of you."}
				: new String[]{"One less.", "Just " + count(now) + " now.", "There were " + count(were) + " of you."};
		h.reply = lines[random.nextInt(lines.length)];
		h.replyAt = d.playTicks + 100 + random.nextInt(100);
		d.cooldowns.put(LEFT, d.playTicks + 20L * 60 * 20);
	}

	private static String count(int n) {
		String[] words = {"no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
		return n >= 0 && n < words.length ? words[n] : Integer.toString(n);
	}

	/** Each tick they are being haunted: the answer, when it is time. */
	static void tick(Haunt h, ServerPlayer player) {
		if (h.reply == null || h.data.playTicks < h.replyAt) return;
		String line = h.reply;
		h.reply = null;
		Cues.whisperOnly(player, line, 90);
	}

	/** Lower case, letters and single spaces only: "Who's there??" is "whos there". */
	static String normal(String said) {
		String s = said.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
		return s.replaceAll("[^a-z ]", " ").replaceAll(" +", " ").strip();
	}

	@Nullable
	static String[] answers(String s, int act) {
		if (s.isEmpty()) return null;
		boolean late = act >= 3;
		if (s.matches(GREET)) return late ? new String[]{"Hello, {player}.", "Hello again."} : new String[]{"Hello."};
		if (any(s, THERE)) return late ? new String[]{"Right behind you.", "Closer than that.", "Yes."} : new String[]{"Yes."};
		if (any(s, WANT)) return late ? new String[]{"You.", "To stay."} : new String[]{"You know."};
		if (any(s, WHO)) return late ? new String[]{"Turn around and ask.", "You know.", "Yours."} : new String[]{"You know."};
		if (any(s, WHAT)) return late ? new String[]{"Me.", "Don't look."} : new String[]{"Nothing.", "Nothing. Keep going."};
		if (any(s, LEAVE) || s.equals("stop")) return late ? new String[]{"No.", "You came here.", "Not yet."} : new String[]{"No."};
		return null;
	}

	private static boolean any(String s, String[] phrases) {
		String padded = " " + s + " ";
		for (String p : phrases) if (padded.contains(" " + p + " ")) return true;
		return false;
	}
}
