package com.wolfsmask.occupant.director;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;

/**
 * What comes after. A scare that is never followed up is something that happened once; one
 * that is, is something that is still going on. When an event ends, it may leave something to
 * come a few seconds or a minute later: the lights stutter, and then, in the dark that settles,
 * something breathes behind you. It knocks, and then it is at the window. It is seen far off,
 * and a minute later, closer.
 * <p>
 * Each follow-up is tried in turn, and only if it fits where the player is now (a knock needs a
 * door, a face at the window needs them inside), so it is never forced.
 */
public final class FollowUps {
	/** One thing that may come after: which event, how likely, and after how many seconds. */
	record Next(String event, float chance, int minSeconds, int maxSeconds) {
	}

	/** How deep a chain may run: a follow-up may have its own, but no further. */
	static final int MAX_CHAIN = 2;

	private static final Map<String, List<Next>> AFTER = Map.ofEntries(
			// The lights stutter: it was in front of you. When they settle, it is behind you.
			Map.entry("flicker", List.of(new Next("breath", 0.6f, 4, 9), new Next("whisper", 0.35f, 5, 10),
					new Next("behind_you", 0.25f, 10, 20))),
			// Seen far off. A little while later, closer.
			Map.entry("distant", List.of(new Next("watcher", 0.45f, 45, 110), new Next("stalker", 0.3f, 60, 120),
					new Next("footsteps", 0.35f, 20, 50))),
			// It was watching. Then it is not there, and something walks behind you.
			Map.entry("watcher", List.of(new Next("footsteps", 0.45f, 15, 40), new Next("stare", 0.3f, 20, 60),
					new Next("breath", 0.2f, 25, 60))),
			// Knocking at the door. Then a face at the window. Or the door.
			Map.entry("knock", List.of(new Next("window", 0.55f, 8, 20), new Next("door", 0.35f, 10, 25),
					new Next("roof", 0.3f, 15, 30))),
			Map.entry("window", List.of(new Next("roof", 0.4f, 10, 25), new Next("knock", 0.25f, 20, 40))),
			Map.entry("roof", List.of(new Next("knock", 0.35f, 10, 25), new Next("window", 0.3f, 15, 30))),
			// The fire goes out. In the dark, it breathes.
			Map.entry("fire_out", List.of(new Next("breath", 0.5f, 6, 14), new Next("watcher", 0.3f, 20, 45))),
			Map.entry("torch_gone", List.of(new Next("marker_torch", 0.35f, 40, 90), new Next("footsteps", 0.4f, 10, 30))),
			Map.entry("static", List.of(new Next("flicker", 0.45f, 6, 15), new Next("whisper", 0.3f, 5, 12))),
			// Footsteps behind you that stop when you do. Sometimes, when you turn round, it is there.
			Map.entry("footsteps", List.of(new Next("behind_you", 0.12f, 6, 15), new Next("breath", 0.2f, 8, 20))),
			Map.entry("breath", List.of(new Next("behind_you", 0.15f, 5, 12))),
			// Someone joined the game. Then they say something.
			Map.entry("fake_join", List.of(new Next("doppel_chat", 0.5f, 30, 90), new Next("teammate", 0.35f, 30, 90))),
			Map.entry("sign", List.of(new Next("watcher", 0.4f, 30, 80))),
			Map.entry("marker_torch", List.of(new Next("watcher", 0.35f, 30, 80))),
			Map.entry("hallway", List.of(new Next("footsteps", 0.5f, 10, 30), new Next("door", 0.4f, 20, 45))),
			Map.entry("stalker", List.of(new Next("breath", 0.4f, 10, 25), new Next("whisper", 0.25f, 15, 30))),
			Map.entry("radio", List.of(new Next("knock", 0.3f, 20, 45), new Next("static", 0.3f, 10, 25))),
			Map.entry("pets", List.of(new Next("watcher", 0.4f, 15, 40))),
			Map.entry("stare", List.of(new Next("watcher", 0.35f, 10, 30))),
			Map.entry("cave_noise", List.of(new Next("echo", 0.3f, 15, 40), new Next("corridor", 0.2f, 30, 70))),
			Map.entry("door", List.of(new Next("footsteps", 0.35f, 5, 15))),
			Map.entry("saving", List.of(new Next("whisper", 0.3f, 5, 12))));

	private FollowUps() {
	}

	/** Every event named here, before or after: for the test that they all exist. */
	public static java.util.Set<String> named() {
		java.util.Set<String> all = new java.util.TreeSet<>(AFTER.keySet());
		for (List<Next> options : AFTER.values()) for (Next n : options) all.add(n.event());
		return all;
	}

	/**
	 * What may follow {@code event}: the candidates in the order to try them (each having made
	 * its roll), and when, in seconds. Empty if nothing follows this time.
	 */
	static Plan plan(String event, RandomSource random) {
		List<Next> options = AFTER.get(event);
		if (options == null) return Plan.NONE;
		List<String> chosen = new java.util.ArrayList<>();
		int seconds = Integer.MAX_VALUE;
		for (Next n : options) {
			if (random.nextFloat() >= n.chance()) continue;
			chosen.add(n.event());
			seconds = Math.min(seconds, n.minSeconds() + random.nextInt(Math.max(1, n.maxSeconds() - n.minSeconds() + 1)));
		}
		return chosen.isEmpty() ? Plan.NONE : new Plan(List.copyOf(chosen), seconds);
	}

	/** Follow-ups chosen for after an event: try them in order, {@code seconds} after it ended. */
	record Plan(List<String> events, int seconds) {
		static final Plan NONE = new Plan(List.of(), 0);
	}
}
