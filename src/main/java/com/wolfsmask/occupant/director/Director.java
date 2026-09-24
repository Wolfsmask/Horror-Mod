package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.director.events.WakeEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Director decides what happens, to whom, and when.
 * <p>
 * Scripted horror videos feel perfect because someone chose the timing. The Director does the
 * same thing live: it builds dread slowly, lets it peak, then gives the player room to breathe,
 * and it only ever does something when the player's situation lets it land. Every player gets
 * their own story, told at their own pace.
 * <p>
 * Nothing in here is allowed to crash the game. Any error in an event is caught, logged, cleaned
 * up, and after repeated failures that one event is switched off for the rest of the session.
 */
public final class Director {
	private static final int MAX_FAILURES_PER_EVENT = 3;
	private static final long MINUTE = 1200L;

	@Nullable
	private static Director instance;

	private final MinecraftServer server;
	private final OccupantSaveData save;
	private final Map<UUID, Haunt> haunts = new HashMap<>();
	private final Map<String, Integer> failures = new HashMap<>();
	private final Set<String> disabledEvents = new HashSet<>();
	/** Players who woke up since the last tick. Acted on there, never where the game told us. */
	private final Set<UUID> woke = new HashSet<>();
	private int totalErrors;

	private Director(MinecraftServer server) {
		this.server = server;
		this.save = OccupantSaveData.get(server);
	}

	// ------------------------------------------------------------------ lifecycle

	public static void start(MinecraftServer server) {
		instance = new Director(server);
	}

	public static void stop() {
		if (instance != null) {
			for (Haunt h : instance.haunts.values()) instance.endSequence(h);
			instance.haunts.clear();
		}
		instance = null;
	}

	@Nullable
	public static Director get() {
		return instance;
	}

	/** Errors caught since the server started (used by the game tests). */
	public int totalErrors() {
		return totalErrors;
	}

	/**
	 * Notes that a player woke up. Deliberately does nothing else: this is called from inside
	 * the game's own sleeping code, including while a player who logged out asleep is being
	 * placed into the world, where asking the world anything is not safe yet.
	 */
	public void noteWoke(ServerPlayer player) {
		woke.add(player.getUUID());
	}

	public HauntData data(ServerPlayer player) {
		return haunt(player).data;
	}

	public Haunt haunt(ServerPlayer player) {
		return haunts.computeIfAbsent(player.getUUID(), u -> new Haunt(u, save.forPlayer(u), player.getRandom()));
	}

	public void markDirty() {
		save.setDirty();
	}

	// ------------------------------------------------------------------ main loop

	public void tick() {
		OccupantConfig cfg = OccupantConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Haunt h = null;
			try {
				// Inside the guard: building a player's story state reads the save file, and a
				// failure there must not take the server tick down with it.
				h = haunt(player);
				if (woke.remove(player.getUUID())) wakeUp(h, player);
				tickPlayer(h, player, cfg);
			} catch (Exception | LinkageError e) {
				if (h != null) {
					reportError(h, e);
				} else {
					totalErrors++;
					Occupant.LOGGER.error("The Occupant could not read a player's story and skipped them.", e);
				}
			}
		}
		woke.removeIf(u -> server.getPlayerList().getPlayer(u) == null);

		// Players who left: stop whatever was happening to them.
		Iterator<Haunt> it = haunts.values().iterator();
		while (it.hasNext()) {
			Haunt h = it.next();
			if (server.getPlayerList().getPlayer(h.uuid) == null) {
				endSequence(h);
				it.remove();
			}
		}

		if (server.getTickCount() % 100 == 0) save.setDirty();
	}

	/** Waking up is not always a relief. Run a tick later, once the player is really in the world. */
	private void wakeUp(Haunt h, ServerPlayer player) {
		if (h.isBusy() || player.getRandom().nextFloat() >= 0.3f) return;
		trigger(player, WakeEvent.ID, false);
	}

	private void tickPlayer(Haunt h, ServerPlayer player, OccupantConfig cfg) {
		boolean eligible = isEligible(player, h, cfg);

		if (h.active != null) {
			if (!eligible || !player.isAlive()) {
				endSequence(h);
			} else if (!h.active.tick(player)) {
				endSequence(h);
			}
		}

		if (!eligible) return;
		h.data.playTicks++;

		if (--h.evalTimer > 0) return;
		h.evalTimer = 20;
		h.quietSeconds++;

		Situation s = h.capture(player);
		updateDread(h.data, s);
		int actBefore = h.data.act;
		updateAct(h.data, cfg, player);
		if (actBefore == 0 && h.data.act == 1) {
			// The very first thing should come a little while after the grace period, not on the dot.
			h.nextEventIn = 20 * (120 + player.getRandom().nextInt(180));
		}

		if (h.active != null || h.data.act == 0) return;
		h.nextEventIn -= 20;
		if (h.nextEventIn > 0) return;

		if (s.afk() || s.busy() || player.isCreative() && !cfg.hauntCreative) {
			h.nextEventIn = 20 * 15;
			return;
		}
		scheduleNext(h, player, s, cfg);
	}

	private boolean isEligible(ServerPlayer player, Haunt h, OccupantConfig cfg) {
		if (!cfg.enabled || h.data.paused) return false;
		if (player.isSpectator() || player.isDeadOrDying()) return false;
		if (player.isCreative() && !cfg.hauntCreative) return false;
		return Haunt.worldAllowed(player);
	}

	// ------------------------------------------------------------------ pacing

	private static void updateDread(HauntData d, Situation s) {
		if (d.act == 0) return;
		float delta = 0.02f;
		if (s.night()) delta += 0.03f;
		if (s.dark()) delta += 0.04f;
		if (s.underground()) delta += 0.03f;
		if (s.alone()) delta += 0.02f;
		else delta -= 0.08f;
		if (!s.night() && !s.underground() && !s.sheltered() && !s.dark()) delta -= 0.08f;
		d.addDread(delta);
	}

	/**
	 * The story has acts. Each one needs time AND enough to have happened, so a player who spends
	 * hours in a lit base does not skip straight to the ending, but nobody gets stuck forever either.
	 */
	private void updateAct(HauntData d, OccupantConfig cfg, ServerPlayer player) {
		double pace = cfg.storyPace;
		long inAct = d.playTicks - d.actStartedAt;
		int before = d.act;

		switch (d.act) {
			case 0 -> {
				if (d.playTicks >= cfg.graceMinutes * MINUTE) d.setAct(1);
			}
			case 1 -> {
				if (inAct >= 30 * MINUTE * pace && d.actEventCount >= 4 || inAct >= 50 * MINUTE * pace) d.setAct(2);
			}
			case 2 -> {
				if (inAct >= 40 * MINUTE * pace && d.sightings >= 2 || inAct >= 70 * MINUTE * pace) d.setAct(3);
			}
			case 3 -> {
				if (inAct >= 45 * MINUTE * pace && (d.encounters >= 1 || d.sightings >= 4) || inAct >= 80 * MINUTE * pace) {
					d.setAct(4);
				}
			}
			default -> {
			}
		}

		if (d.act != before) {
			debug("{} entered act {}", player.getName().getString(), d.act);
			save.setDirty();
		}
	}

	/** Minutes until the Director next tries something, for the current act. */
	private static double[] intervalMinutes(int act) {
		return switch (act) {
			case 1 -> new double[]{5.0, 9.0};
			case 2 -> new double[]{3.5, 7.0};
			case 3 -> new double[]{2.5, 5.5};
			default -> new double[]{2.0, 4.5};
		};
	}

	/**
	 * How likely each tier is to be picked, per act: the shape of the story.
	 * <p>
	 * {@code quietSeconds} bends it. A player who has heard nothing for a quarter of an hour has
	 * stopped listening, and that is exactly when the story should stop being deniable.
	 */
	private static Map<HorrorEvent.Tier, Double> tierWeights(int act, int quietSeconds) {
		Map<HorrorEvent.Tier, Double> w = new EnumMap<>(HorrorEvent.Tier.class);
		switch (act) {
			case 1 -> {
				w.put(HorrorEvent.Tier.AMBIENT, 80.0);
				w.put(HorrorEvent.Tier.MINOR, 20.0);
			}
			case 2 -> {
				w.put(HorrorEvent.Tier.AMBIENT, 40.0);
				w.put(HorrorEvent.Tier.MINOR, 35.0);
				w.put(HorrorEvent.Tier.MAJOR, 25.0);
			}
			case 3 -> {
				w.put(HorrorEvent.Tier.AMBIENT, 20.0);
				w.put(HorrorEvent.Tier.MINOR, 35.0);
				w.put(HorrorEvent.Tier.MAJOR, 33.0);
				w.put(HorrorEvent.Tier.PEAK, 12.0);
			}
			default -> {
				w.put(HorrorEvent.Tier.AMBIENT, 15.0);
				w.put(HorrorEvent.Tier.MINOR, 25.0);
				w.put(HorrorEvent.Tier.MAJOR, 35.0);
				w.put(HorrorEvent.Tier.PEAK, 25.0);
			}
		}

		// Up to three times as likely to be something real, after ten quiet minutes.
		double pressure = 1.0 + 2.0 * Math.min(1.0, Math.max(0, quietSeconds - 240) / 600.0);
		w.computeIfPresent(HorrorEvent.Tier.MAJOR, (t, v) -> v * pressure);
		w.computeIfPresent(HorrorEvent.Tier.PEAK, (t, v) -> v * pressure);
		w.computeIfPresent(HorrorEvent.Tier.AMBIENT, (t, v) -> v / Math.sqrt(pressure));
		return w;
	}

	private void scheduleNext(Haunt h, ServerPlayer player, Situation s, OccupantConfig cfg) {
		HauntData d = h.data;
		RandomSource random = player.getRandom();
		EventContext ctx = new EventContext(player, h, s, false);
		boolean calm = d.playTicks < d.calmUntil;

		// Gather everything that could work right now, grouped by tier.
		Map<HorrorEvent.Tier, List<HorrorEvent>> byTier = new EnumMap<>(HorrorEvent.Tier.class);
		for (HorrorEvent e : Events.all()) {
			if (!isCandidate(e, ctx, calm)) continue;
			byTier.computeIfAbsent(e.tier(), t -> new ArrayList<>()).add(e);
		}

		HorrorEvent.Tier[] order = pickTierOrder(tierWeights(d.act, h.quietSeconds), byTier.keySet(), random);
		for (HorrorEvent.Tier tier : order) {
			List<HorrorEvent> pool = byTier.get(tier);
			while (pool != null && !pool.isEmpty()) {
				HorrorEvent e = pickWeighted(pool, ctx, random);
				pool.remove(e);
				if (tryBegin(h, e, ctx)) {
					// Background noise does not relieve the pressure; it is part of the waiting.
					if (e.tier() != HorrorEvent.Tier.AMBIENT) h.quietSeconds = 0;
					scheduleAfter(h, e, random, cfg);
					return;
				}
			}
		}

		// Nothing could happen convincingly. Try again soon.
		h.nextEventIn = 20 * (20 + random.nextInt(25));
	}

	private boolean isCandidate(HorrorEvent e, EventContext ctx, boolean calm) {
		HauntData d = ctx.data;
		if (e.hookOnly() || disabledEvents.contains(e.id())) return false;
		if (d.act < e.minAct() || d.isOnCooldown(e.id()) || !e.allowedBy(ctx.config)) return false;
		if (d.recency(e.id()) == 0) return false; // never the same thing twice in a row
		if (calm && e.tier() != HorrorEvent.Tier.AMBIENT) return false;
		if (e.tier() == HorrorEvent.Tier.PEAK && !peakReady(d)) return false;
		return e.fits(ctx);
	}

	public static boolean peakReady(HauntData d) {
		if (d.dread < 55f) return false;
		return d.lastPeakAt < 0 || d.playTicks - d.lastPeakAt >= 20 * MINUTE;
	}

	private static HorrorEvent.Tier[] pickTierOrder(Map<HorrorEvent.Tier, Double> weights,
													Set<HorrorEvent.Tier> available, RandomSource random) {
		List<HorrorEvent.Tier> remaining = new ArrayList<>();
		for (HorrorEvent.Tier t : HorrorEvent.Tier.values()) {
			if (available.contains(t) && weights.getOrDefault(t, 0.0) > 0) remaining.add(t);
		}
		HorrorEvent.Tier[] order = new HorrorEvent.Tier[remaining.size()];
		for (int i = 0; i < order.length; i++) {
			double total = 0;
			for (HorrorEvent.Tier t : remaining) total += weights.get(t);
			double roll = random.nextDouble() * total;
			HorrorEvent.Tier chosen = remaining.get(remaining.size() - 1);
			for (HorrorEvent.Tier t : remaining) {
				roll -= weights.get(t);
				if (roll <= 0) {
					chosen = t;
					break;
				}
			}
			order[i] = chosen;
			remaining.remove(chosen);
		}
		return order;
	}

	private static HorrorEvent pickWeighted(List<HorrorEvent> pool, EventContext ctx, RandomSource random) {
		double[] w = new double[pool.size()];
		double total = 0;
		for (int i = 0; i < pool.size(); i++) {
			HorrorEvent e = pool.get(i);
			double weight = e.weight() * Math.max(0.0, e.situationalWeight(ctx));
			int recency = ctx.data.recency(e.id());
			if (recency >= 0) weight *= 0.3 + 0.12 * recency; // recently seen things are less likely
			w[i] = Math.max(0.0001, weight);
			total += w[i];
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < w.length; i++) {
			roll -= w[i];
			if (roll <= 0) return pool.get(i);
		}
		return pool.get(pool.size() - 1);
	}

	private boolean tryBegin(Haunt h, HorrorEvent e, EventContext ctx) {
		Sequence seq;
		try {
			seq = e.begin(ctx);
		} catch (Exception | LinkageError ex) {
			recordFailure(e.id(), ex);
			return false;
		}
		if (seq == null) {
			debug("{}: {} found no convincing way to happen", ctx.player.getName().getString(), e.id());
			return false;
		}

		HauntData d = h.data;
		h.active = seq;
		h.activeId = e.id();
		d.recordEvent(e.id(), e.cooldownTicks());
		if (e.tier() == HorrorEvent.Tier.PEAK) {
			d.lastPeakAt = d.playTicks;
			d.dread = 15f;
			d.calmUntil = d.playTicks + (long) ((12 + ctx.random.nextInt(9)) * MINUTE / ctx.config.eventFrequency);
		} else {
			d.addDread(e.tier().dread);
			if (e.tier() == HorrorEvent.Tier.MAJOR) {
				d.calmUntil = Math.max(d.calmUntil, d.playTicks + (long) ((3 + ctx.random.nextInt(3)) * MINUTE / ctx.config.eventFrequency));
			}
		}
		save.setDirty();
		debug("{}: started {} (act {}, dread {})", ctx.player.getName().getString(), e.id(), d.act, (int) d.dread);
		return true;
	}

	private void scheduleAfter(Haunt h, HorrorEvent e, RandomSource random, OccupantConfig cfg) {
		double[] range = intervalMinutes(h.data.act);
		double minutes = range[0] + random.nextDouble() * (range[1] - range[0]);
		minutes *= 1.0 - Math.min(0.4, h.data.dread / 250.0); // more dread, faster pace
		if (h.data.playTicks < h.data.calmUntil) minutes *= 1.6;
		if (h.lastSituationWasNight) minutes *= 0.75;         // the nights are busier than the days
		minutes /= cfg.eventFrequency;
		h.nextEventIn = (int) Math.max(20 * 30, minutes * MINUTE);
	}

	// ------------------------------------------------------------------ errors

	private void endSequence(Haunt h) {
		Sequence seq = h.active;
		h.active = null;
		String id = h.activeId;
		h.activeId = null;
		if (seq != null) {
			try {
				seq.end();
			} catch (Exception | LinkageError e) {
				if (id != null) recordFailure(id, e);
			}
		}
	}

	private void reportError(Haunt h, Throwable t) {
		String id = h.activeId;
		endSequence(h);
		if (id != null) {
			recordFailure(id, t);
		} else {
			totalErrors++;
			Occupant.LOGGER.error("The Occupant hit an error and skipped a beat (the game is fine).", t);
		}
	}

	private void recordFailure(String eventId, Throwable t) {
		totalErrors++;
		int count = failures.merge(eventId, 1, Integer::sum);
		Occupant.LOGGER.error("Event '{}' failed ({} of {}); it was stopped safely.", eventId, count, MAX_FAILURES_PER_EVENT, t);
		if (count >= MAX_FAILURES_PER_EVENT && disabledEvents.add(eventId)) {
			Occupant.LOGGER.warn("Event '{}' is disabled until the server restarts.", eventId);
		}
	}

	// ------------------------------------------------------------------ hooks and commands

	/** A player said something in chat. It remembers. */
	public void onChat(ServerPlayer player, String message) {
		String m = message.strip();
		if (m.length() < 2 || m.length() > 60 || m.startsWith("/") || m.contains("://")) return;
		haunt(player).data.rememberChat(m);
	}

	/** Start an event right now (used by hooks and /occupant trigger). */
	public TriggerResult trigger(ServerPlayer player, String eventId, boolean forced) {
		HorrorEvent e = Events.byId(eventId);
		if (e == null) return TriggerResult.UNKNOWN;
		Haunt h = haunt(player);
		if (!forced) {
			if (h.active != null || disabledEvents.contains(e.id())) return TriggerResult.BUSY;
			if (!isEligible(player, h, OccupantConfig.get()) || h.data.act == 0) return TriggerResult.BUSY;
		} else {
			endSequence(h);
		}
		Situation s = h.capture(player);
		EventContext ctx = new EventContext(player, h, s, forced);
		if (!forced && (!e.allowedBy(ctx.config) || !e.fits(ctx))) return TriggerResult.NO_SPOT;
		return tryBegin(h, e, ctx) ? TriggerResult.STARTED : TriggerResult.NO_SPOT;
	}

	public void stopCurrent(ServerPlayer player) {
		endSequence(haunt(player));
	}

	public void reset(ServerPlayer player) {
		Haunt old = haunts.remove(player.getUUID());
		if (old != null) endSequence(old);
		save.reset(player.getUUID());
	}

	public enum TriggerResult { STARTED, UNKNOWN, BUSY, NO_SPOT }

	static void debug(String message, Object... args) {
		if (OccupantConfig.get().debug) Occupant.LOGGER.info("[director] " + message, args);
	}
}
