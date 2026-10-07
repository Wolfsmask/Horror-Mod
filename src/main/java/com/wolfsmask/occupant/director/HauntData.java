package com.wolfsmask.occupant.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the Occupant remembers about one player. Saved with the world, so the story
 * picks up exactly where it left off after a restart.
 * <p>
 * All timers are measured in "haunt ticks": ticks that player has actually spent online.
 * That keeps the pacing tied to the player's experience, not to /time set or server uptime.
 */
public final class HauntData {
	public static final int MAX_ACT = 4;
	private static final int HISTORY_SIZE = 6;
	private static final int CHAT_MEMORY = 8;

	/** The later additions, kept together (one codec takes at most sixteen fields). */
	private static final Codec<int[]> STORY = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("logsFound", 0).forGetter(a -> a[0]),
			Codec.INT.optionalFieldOf("ignored", 0).forGetter(a -> a[1]),
			Codec.BOOL.optionalFieldOf("introduced", false).forGetter(a -> a[2] != 0),
			Codec.BOOL.optionalFieldOf("lastNight", false).forGetter(a -> a[3] != 0),
			Codec.INT.optionalFieldOf("lastCamp", 0).forGetter(a -> a[4]),
			Codec.INT.optionalFieldOf("lastCampX", 0).forGetter(a -> a[5]),
			Codec.INT.optionalFieldOf("lastCampZ", 0).forGetter(a -> a[6]),
			Codec.BOOL.optionalFieldOf("pageAfter", false).forGetter(a -> a[7] != 0),
			Codec.INT.optionalFieldOf("insideSeconds", 0).forGetter(a -> a[8]),
			Codec.INT.optionalFieldOf("outsideSeconds", 0).forGetter(a -> a[9]),
			Codec.INT.optionalFieldOf("ending", -1).forGetter(a -> a[10]),
			Codec.INT.optionalFieldOf("lastPageAt", -1).forGetter(a -> a[11]),
			Codec.INT.optionalFieldOf("tallyX", 0).forGetter(a -> a[12]),
			Codec.INT.optionalFieldOf("tallyY", Integer.MIN_VALUE).forGetter(a -> a[13]),
			Codec.INT.optionalFieldOf("tallyZ", 0).forGetter(a -> a[14]),
			Codec.INT.optionalFieldOf("marks", 0).forGetter(a -> a[15])
	).apply(i, (logs, ignored, introduced, lastNight, camp, campX, campZ, after, inside, outside, ending, pageAt, tx, ty, tz, marks) -> new int[]{
			logs, ignored, introduced ? 1 : 0, lastNight ? 1 : 0, camp, campX, campZ, after ? 1 : 0, inside, outside, ending, pageAt,
			tx, ty, tz, marks}));

	/** Every field is optional with a default, so old or partial saves always load. */
	public static final Codec<HauntData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("act", 0).forGetter(d -> d.act),
			Codec.FLOAT.optionalFieldOf("dread", 0f).forGetter(d -> d.dread),
			Codec.LONG.optionalFieldOf("playTicks", 0L).forGetter(d -> d.playTicks),
			Codec.LONG.optionalFieldOf("actStartedAt", 0L).forGetter(d -> d.actStartedAt),
			Codec.INT.optionalFieldOf("eventCount", 0).forGetter(d -> d.eventCount),
			Codec.INT.optionalFieldOf("actEventCount", 0).forGetter(d -> d.actEventCount),
			Codec.INT.optionalFieldOf("sightings", 0).forGetter(d -> d.sightings),
			Codec.INT.optionalFieldOf("encounters", 0).forGetter(d -> d.encounters),
			Codec.LONG.optionalFieldOf("calmUntil", 0L).forGetter(d -> d.calmUntil),
			Codec.LONG.optionalFieldOf("lastPeakAt", -1L).forGetter(d -> d.lastPeakAt),
			Codec.LONG.optionalFieldOf("sleepDenyDay", -1L).forGetter(d -> d.sleepDenyDay),
			Codec.BOOL.optionalFieldOf("paused", false).forGetter(d -> d.paused),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(HauntData::activeCooldowns),
			Codec.STRING.listOf().optionalFieldOf("history", List.of()).forGetter(d -> new ArrayList<>(d.history)),
			Codec.STRING.listOf().optionalFieldOf("heardChat", List.of()).forGetter(d -> new ArrayList<>(d.heardChat)),
			STORY.optionalFieldOf("story", new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 0, Integer.MIN_VALUE, 0, 0}).forGetter(d -> new int[]{
					d.logsFound, d.ignored, d.introduced ? 1 : 0, d.lastNight ? 1 : 0, d.lastCamp, d.lastCampX, d.lastCampZ,
					d.pageAfter ? 1 : 0, d.insideSeconds, d.outsideSeconds, d.ending, d.lastPageAt, d.tallyX, d.tallyY, d.tallyZ, d.marks})
	).apply(i, HauntData::fromCodec));

	/** 0 = nothing yet, 1 = signs, 2 = presence, 3 = closer, 4 = hunt. */
	public int act = 0;
	/** How scared the player "should" be right now, 0..100. Drives pacing. */
	public float dread = 0f;
	/** Total ticks this player has been online while eligible for haunting. */
	public long playTicks = 0;
	/** playTicks value when the current act began. */
	public long actStartedAt = 0;

	/** Events that actually happened, in total and during the current act. */
	public int eventCount = 0;
	public int actEventCount = 0;
	/** Times the player actually looked at the Occupant. */
	public int sightings = 0;
	/** Close encounters (behind-you, caught in a chase, and so on). */
	public int encounters = 0;

	/** No major/peak scare until playTicks reaches this. */
	public long calmUntil = 0;
	/** playTicks when the last peak scare happened. */
	public long lastPeakAt = -1;
	/** World day on which sleep was last interrupted (so it happens at most once per night). */
	public long sleepDenyDay = -1;

	/** Pages of the survivor's log this player has found so far. */
	public int logsFound = 0;
	/** Times it stood there and the player never noticed: the warnings get worse with each. */
	public int ignored = 0;
	/** Whether the player has had the black screen that tells them, on first arriving. */
	public boolean introduced = false;
	/** The last night has happened: it came all the way, and the story began again, quieter. */
	public boolean lastNight = false;
	/** The survivor's last camp: 0 not yet, 1 built and written down, 2 found. */
	public int lastCamp = 0;
	public int lastCampX;
	public int lastCampZ;
	/** The page that turns up after the last night has been found. */
	public boolean pageAfter = false;
	/** How the story was lived: seconds spent under a roof, and out under the sky. */
	public int insideSeconds;
	/**
	 * How the story ended, once it has: 0 the plain ending (it begins again, quieter), 1 they found
	 * what was left of the survivor (it lets them go), 2 they hid (it came in). -1 before then.
	 */
	public int ending = -1;
	/** When the last page of the log was found, in seconds of play; -1 before the first. */
	public int lastPageAt = -1;
	/** Where its tally of their days is kept, by their bed (tallyY at its lowest: none yet). */
	public int tallyX;
	public int tallyY = Integer.MIN_VALUE;
	public int tallyZ;
	/** What it has done once and for all: bit 0, the trees marked; from bit 8 up, pages of the second log found. */
	public int marks;
	public int outsideSeconds;

	/** Paused by an operator with /occupant pause. */
	public boolean paused = false;

	/** Per-event: playTicks at which the event may run again. */
	public final Map<String, Long> cooldowns = new HashMap<>();
	/** Most recent event ids, newest first. */
	public final Deque<String> history = new ArrayDeque<>();
	/** A few things the player has said in chat. The Occupant likes to repeat them. */
	public final Deque<String> heardChat = new ArrayDeque<>();

	private static HauntData fromCodec(int act, float dread, long playTicks, long actStartedAt, int eventCount,
									   int actEventCount, int sightings, int encounters, long calmUntil, long lastPeakAt,
									   long sleepDenyDay, boolean paused, Map<String, Long> cooldowns,
									   List<String> history, List<String> heardChat, int[] story) {
		HauntData d = new HauntData();
		d.act = Math.max(0, Math.min(MAX_ACT, act));
		d.dread = Float.isNaN(dread) ? 0f : Math.max(0f, Math.min(100f, dread));
		d.playTicks = Math.max(0, playTicks);
		d.actStartedAt = Math.min(d.playTicks, Math.max(0, actStartedAt));
		d.eventCount = eventCount;
		d.actEventCount = actEventCount;
		d.sightings = sightings;
		d.encounters = encounters;
		d.calmUntil = calmUntil;
		d.lastPeakAt = lastPeakAt;
		d.sleepDenyDay = sleepDenyDay;
		d.paused = paused;
		d.cooldowns.putAll(cooldowns);
		history.stream().limit(HISTORY_SIZE).forEach(d.history::addLast);
		heardChat.stream().limit(CHAT_MEMORY).forEach(d.heardChat::addLast);
		if (story.length >= 3) {
			d.logsFound = Math.max(0, story[0]);
			d.ignored = Math.max(0, story[1]);
			d.introduced = story[2] != 0;
		}
		if (story.length >= 10) {
			d.lastNight = story[3] != 0;
			d.lastCamp = story[4];
			d.lastCampX = story[5];
			d.lastCampZ = story[6];
			d.pageAfter = story[7] != 0;
			d.insideSeconds = story[8];
			d.outsideSeconds = story[9];
		}
		if (story.length >= 11) d.ending = story[10];
		if (story.length >= 12) d.lastPageAt = story[11];
		if (story.length >= 16) {
			d.tallyX = story[12];
			d.tallyY = story[13];
			d.tallyZ = story[14];
			d.marks = story[15];
		}
		return d;
	}

	private Map<String, Long> activeCooldowns() {
		Map<String, Long> out = new HashMap<>();
		cooldowns.forEach((id, ready) -> {
			if (ready > playTicks) out.put(id, ready);
		});
		return out;
	}

	public void recordEvent(String id, long cooldownTicks) {
		eventCount++;
		actEventCount++;
		cooldowns.put(id, playTicks + cooldownTicks);
		history.addFirst(id);
		while (history.size() > HISTORY_SIZE) history.removeLast();
	}

	public boolean isOnCooldown(String id) {
		Long readyAt = cooldowns.get(id);
		return readyAt != null && playTicks < readyAt;
	}

	/** 0 = most recent event, 1 = the one before... or -1 if not in recent history. */
	public int recency(String id) {
		int i = 0;
		for (String s : history) {
			if (s.equals(id)) return i;
			i++;
		}
		return -1;
	}

	public void setAct(int newAct) {
		newAct = Math.max(0, Math.min(MAX_ACT, newAct));
		if (newAct != act) {
			act = newAct;
			actStartedAt = playTicks;
			actEventCount = 0;
		}
	}

	public void addDread(float amount) {
		dread = Math.max(0f, Math.min(100f, dread + amount));
	}

	public void rememberChat(String message) {
		heardChat.addFirst(message);
		while (heardChat.size() > CHAT_MEMORY) heardChat.removeLast();
	}
}
