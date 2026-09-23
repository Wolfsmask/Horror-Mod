package com.wolfsmask.occupant.director;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
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

	/** Paused by an operator with /occupant pause. */
	public boolean paused = false;

	/** Per-event: playTicks at which the event may run again. */
	public final Map<String, Long> cooldowns = new HashMap<>();
	/** Most recent event ids, newest first. */
	public final Deque<String> history = new ArrayDeque<>();
	/** A few things the player has said in chat. The Occupant likes to repeat them. */
	public final Deque<String> heardChat = new ArrayDeque<>();

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

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("act", act);
		nbt.putFloat("dread", dread);
		nbt.putLong("playTicks", playTicks);
		nbt.putLong("actStartedAt", actStartedAt);
		nbt.putInt("eventCount", eventCount);
		nbt.putInt("actEventCount", actEventCount);
		nbt.putInt("sightings", sightings);
		nbt.putInt("encounters", encounters);
		nbt.putLong("calmUntil", calmUntil);
		nbt.putLong("lastPeakAt", lastPeakAt);
		nbt.putLong("sleepDenyDay", sleepDenyDay);
		nbt.putBoolean("paused", paused);

		NbtCompound cd = new NbtCompound();
		cooldowns.forEach((id, ready) -> {
			if (ready > playTicks) cd.putLong(id, ready);
		});
		nbt.put("cooldowns", cd);

		NbtList hist = new NbtList();
		history.forEach(id -> hist.add(NbtString.of(id)));
		nbt.put("history", hist);

		NbtList chat = new NbtList();
		heardChat.forEach(line -> chat.add(NbtString.of(line)));
		nbt.put("heardChat", chat);
		return nbt;
	}

	public static HauntData fromNbt(NbtCompound nbt) {
		HauntData d = new HauntData();
		d.act = Math.max(0, Math.min(MAX_ACT, nbt.getInt("act")));
		d.dread = Math.max(0f, Math.min(100f, nbt.getFloat("dread")));
		d.playTicks = Math.max(0, nbt.getLong("playTicks"));
		d.actStartedAt = Math.min(d.playTicks, Math.max(0, nbt.getLong("actStartedAt")));
		d.eventCount = nbt.getInt("eventCount");
		d.actEventCount = nbt.getInt("actEventCount");
		d.sightings = nbt.getInt("sightings");
		d.encounters = nbt.getInt("encounters");
		d.calmUntil = nbt.getLong("calmUntil");
		d.lastPeakAt = nbt.contains("lastPeakAt") ? nbt.getLong("lastPeakAt") : -1;
		d.sleepDenyDay = nbt.contains("sleepDenyDay") ? nbt.getLong("sleepDenyDay") : -1;
		d.paused = nbt.getBoolean("paused");

		NbtCompound cd = nbt.getCompound("cooldowns");
		for (String key : cd.getKeys()) d.cooldowns.put(key, cd.getLong(key));

		NbtList hist = nbt.getList("history", NbtElement.STRING_TYPE);
		for (int i = 0; i < hist.size() && i < HISTORY_SIZE; i++) d.history.addLast(hist.getString(i));

		NbtList chat = nbt.getList("heardChat", NbtElement.STRING_TYPE);
		for (int i = 0; i < chat.size() && i < CHAT_MEMORY; i++) d.heardChat.addLast(chat.getString(i));
		return d;
	}
}
