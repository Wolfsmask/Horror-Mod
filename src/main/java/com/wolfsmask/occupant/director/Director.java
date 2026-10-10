package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.director.events.HallwayEvent;
import com.wolfsmask.occupant.director.events.WakeEvent;
import com.wolfsmask.occupant.world.House;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
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
	/**
	 * When it next comes to hurt them, in play ticks. Kept among the cooldowns, which are saved,
	 * as the time until which it is not due: once that has passed (or in a save from before there
	 * was one), it is due.
	 */
	public static final String ATTACK_DUE = "attack_due";
	/** The ways it can come to hurt them, in the order they are tried: out in the open, the hunt. */
	private static final List<String> ATTACKS = List.of("hunt", "behind_you");

	@Nullable
	private static Director instance;

	private final MinecraftServer server;
	private final OccupantSaveData save;
	private final Map<UUID, Haunt> haunts = new HashMap<>();
	private final Map<UUID, Haunt> watchedForTest = new HashMap<>();
	private final Map<String, Integer> failures = new HashMap<>();
	private final Set<String> disabledEvents = new HashSet<>();
	/** Players who woke up since the last tick. Acted on there, never where the game told us. */
	private final Set<UUID> woke = new HashSet<>();
	private int totalErrors;
	private double tickCost;
	private long worstTick;

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
			instance.watchedForTest.clear();
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

	/** How far their story has gone, 0 if it has not begun; asking starts nothing. */
	public int actOf(ServerPlayer player) {
		Haunt h = haunts.get(player.getUUID());
		return h == null ? 0 : h.data.act;
	}

	public Haunt haunt(ServerPlayer player) {
		return haunts.computeIfAbsent(player.getUUID(), u -> new Haunt(u, save.forPlayer(u), player.getRandom()));
	}

	/**
	 * For the game tests: one tick of watching whether this player has noticed {@code standing}.
	 * A mock player is never in the player list, so the tick drops its Haunt straight away; the
	 * watching is kept here instead, over the same saved story.
	 */
	public void watchForTest(ServerPlayer player, OccupantEntity standing) {
		Haunt h = watchedForTest.computeIfAbsent(player.getUUID(), u -> new Haunt(u, save.forPlayer(u), player.getRandom()));
		h.unnoticed.watchOnly(standing);
		h.unnoticed.tick(player, h);
	}

	/** For the game tests: how the watching of {@code standing} is going. */
	public String describeForTest(ServerPlayer player, OccupantEntity standing) {
		Haunt h = watchedForTest.get(player.getUUID());
		if (h == null) return "never watched";
		return h.unnoticed.describe(player, standing) + ", ignored " + h.data.ignored
				+ ", same story " + (h.data == save.forPlayer(player.getUUID()));
	}

	public void markDirty() {
		save.setDirty();
	}

	// ------------------------------------------------------------------ main loop

	public void tick() {
		long started = System.nanoTime();
		tickInner();
		long took = System.nanoTime() - started;
		tickCost = tickCost * 0.99 + took * 0.01;
		if (took > worstTick || server.getTickCount() % 6000 == 0) worstTick = took;
	}

	/** What the Director costs the server each tick, on average and at worst lately, in milliseconds. */
	public double[] cost() {
		return new double[]{tickCost / 1e6, worstTick / 1e6};
	}

	private void tickInner() {
		OccupantConfig cfg = OccupantConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Haunt h = null;
			try {
				// Inside the guard: building a player's story state reads the save file, and a
				// failure there must not take the server tick down with it.
				h = haunt(player);
				if (!h.joinSent) rejoined(h, player);
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
				Trifles.forget(null, h);
				endSequence(h);
				it.remove();
			}
		}

		if (server.getTickCount() % 100 == 0) save.setDirty();
	}

	/**
	 * Just come into the world: whatever a scene left half done when they last went (it could
	 * only put it right while they were here) put right now. Held up on its legs, they had no
	 * weight, and that is kept with them; taken, they were in its lair.
	 */
	private void rejoined(Haunt h, ServerPlayer player) {
		if (player.isNoGravity() && !player.isSpectator()) {
			player.setNoGravity(false);
			player.resetFallDistance();
		}
		TakenEnding.recover(h, player);
	}

	/** Waking up is not always a relief. Run a tick later, once the player is really in the world. */
	private void wakeUp(Haunt h, ServerPlayer player) {
		if (h.isBusy()) return;
		float roll = player.getRandom().nextFloat();
		if (roll < 0.3f) trigger(player, WakeEvent.ID, false);
		else if (roll < 0.55f) trigger(player, com.wolfsmask.occupant.director.events.MorningEvent.ID, false);
	}

	private void tickPlayer(Haunt h, ServerPlayer player, OccupantConfig cfg) {
		Rules.check(player, h, cfg);
		boolean eligible = isEligible(player, h, cfg);

		if (h.active != null) {
			if (!eligible || !player.isAlive()) {
				endSequence(h);
			} else if (!h.active.tick(player)) {
				String done = h.activeId;
				endSequence(h);
				planFollowUp(h, done, player);
			}
		}
		// What was to begin the moment that was over (the end, when it has taken them).
		if (h.active == null && h.queued != null) {
			// The credits run even when the ending was that it let them go.
			if (player.isAlive() && (eligible || Credits.ID.equals(h.queuedId))) {
				h.active = h.queued;
				h.activeId = h.queuedId;
				h.pending = null;
			}
			h.queued = null;
			h.queuedId = null;
		}

		// Just come into the world: the way in, only where it haunts them (the client never shows
		// it by itself, so a server without this mod, or with it switched off, has none).
		if (!h.joinSent) {
			h.joinSent = true;
			if (eligible) Cues.effect(player, ScreenEffectPayload.JOINED, 0, 1f);
		}

		// Once a second, what the client needs to know: the fog (cleared while they are not being
		// haunted), and how far the story has gone.
		if (player.tickCount % 20 == 7) {
			Fog.update(player, h, eligible);
			int act = eligible ? h.data.act : 0;
			if (act != h.actSent) {
				Cues.effect(player, ScreenEffectPayload.ACT, 0, act);
				h.actSent = act;
			}
		}

		if (!eligible) return;
		h.data.playTicks++;
		Trifles.tick(player, h, cfg);

		// The first time in this world, once they are actually in it: black, and it tells them.
		if (!h.data.introduced && player.tickCount > 60) {
			h.data.introduced = true;
			Cues.effect(player, ScreenEffectPayload.FIRST_ARRIVAL, 240, 1.0f);
			com.wolfsmask.occupant.story.Achievements.grant(player, com.wolfsmask.occupant.story.Achievements.ROOT);
			save.setDirty();
		}
		h.unnoticed.tick(player, h);

		if (--h.evalTimer > 0) return;
		h.evalTimer = 20;
		h.quietSeconds++;

		Situation s = h.capture(player);
		if (h.data.act > 0) {
			if (s.sheltered()) h.data.insideSeconds++;
			else h.data.outsideSeconds++;
		}
		House.noticeNear(player.blockPosition());
		Hiding.tick(player, h, s, cfg, this);
		// Held up on its leg is the only time they float: anything left over from that, gone.
		if (player.isNoGravity() && h.active == null && !player.isCreative() && !player.isSpectator()) player.setNoGravity(false);
		updateDread(h.data, s);
		int actBefore = h.data.act;
		updateAct(h.data, cfg, player);
		if (actBefore == 0 && h.data.act == 1) {
			// The very first thing should come a little while after the grace period, not on the dot.
			h.nextEventIn = 20 * (45 + player.getRandom().nextInt(75));
		}

		if (h.active != null || h.data.act == 0) return;
		if (h.pending != null && server.getTickCount() >= h.pendingAt) {
			FollowUps.Plan plan = h.pending;
			h.pending = null;
			if (followUp(h, player, s, cfg, plan)) return;
		}
		if (enteredHouse(h, player)) return;
		if (enteredLair(h, player)) return;
		// Once it is due to hurt them, it does not wait for the story's next turn: after a little
		// quiet, it comes as soon as they are somewhere it can (looked at every few seconds).
		if (h.quietSeconds >= 40 && h.quietSeconds % 5 == 0 && attackDue(h.data, cfg) && !s.afk() && !s.busy()
				&& !(player.isCreative() && !cfg.hauntCreative) && attack(h, player, s, cfg, false)) {
			return;
		}
		h.nextEventIn -= 20;
		if (h.nextEventIn > 0) return;

		if (s.afk() || s.busy() || player.isCreative() && !cfg.hauntCreative) {
			h.nextEventIn = 20 * 15;
			return;
		}
		scheduleNext(h, player, s, cfg);
	}

	/**
	 * Walking into one of the abandoned houses is a moment of its own, whatever the pacing says:
	 * the first time in each house (or the first time in a long while), it is in the hallway.
	 */
	private boolean enteredHouse(Haunt h, ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		if (!House.isInside(Compat.level(player), feet)) return false;
		long now = server.getTickCount();
		boolean sameHouse = h.lastHouse != null && h.lastHouse.closerThan(feet, 20.0);
		if (sameHouse && now - h.lastHouseTick < 20L * 60 * 20) return false;   // twenty minutes
		if (now < h.houseRetryAt) return false;
		if (trigger(player, HallwayEvent.ID, false) != TriggerResult.STARTED) {
			h.houseRetryAt = now + 60;          // no good spot from here yet; look again as they move
			return false;
		}
		h.lastHouse = feet;
		h.lastHouseTick = now;
		return true;
	}

	/** Going down into one of its lairs is a moment of its own, once in a long while. */
	private boolean enteredLair(Haunt h, ServerPlayer player) {
		if (com.wolfsmask.occupant.world.Lairs.hollowAt(player.blockPosition()) == null) return false;
		long now = server.getTickCount();
		if (now < h.lairAgainAt) return false;
		if (trigger(player, com.wolfsmask.occupant.director.events.LairEvent.ID, false) != TriggerResult.STARTED) {
			h.lairAgainAt = now + 100;
			return false;
		}
		h.lairAgainAt = now + 20L * 60 * 15;
		return true;
	}

	/** An event has run its course: what, if anything, comes after it. */
	private void planFollowUp(Haunt h, @org.jetbrains.annotations.Nullable String done, ServerPlayer player) {
		h.pending = null;
		if (done == null || h.chain >= FollowUps.MAX_CHAIN) {
			h.chain = 0;
			return;
		}
		FollowUps.Plan plan = FollowUps.plan(done, player.getRandom());
		if (plan.events().isEmpty()) {
			h.chain = 0;
			return;
		}
		h.pending = plan;
		h.pendingAt = server.getTickCount() + 20L * plan.seconds();
		debug("{}: after {}, perhaps {} in {}s", player.getName().getString(), done, plan.events(), plan.seconds());
	}

	/** The first of the follow-ups that fits where the player is now; false if none does. */
	private boolean followUp(Haunt h, ServerPlayer player, Situation s, OccupantConfig cfg, FollowUps.Plan plan) {
		HauntData d = h.data;
		if (s.afk() || s.busy()) return false;
		EventContext ctx = new EventContext(player, h, s, false);
		for (String id : plan.events()) {
			HorrorEvent e = Events.byId(id);
			if (e == null || e.hookOnly() || disabledEvents.contains(id)) continue;
			if (d.act < e.minAct() || d.isOnCooldown(id) || !e.allowedBy(cfg)) continue;
			if (cfg.soundOnly && e.shows()) continue;
			if (e.tier() == HorrorEvent.Tier.PEAK && !peakReady(d)) continue;
			if (!e.fits(ctx)) continue;
			if (tryBegin(h, e, ctx)) {
				h.chain++;
				if (e.tier() != HorrorEvent.Tier.AMBIENT) h.quietSeconds = 0;
				h.nextEventIn = Math.max(h.nextEventIn, 20 * 30);
				return true;
			}
		}
		h.chain = 0;
		return false;
	}

	/**
	 * Has it been too long since they last looked right at it? Sounds and signs keep a story
	 * going, but it is being seen that people remember: every five minutes or so early on, every
	 * three by the end. Only their looking at it counts: a knock that never shows it, or a figure
	 * at the edge of the fog that they never noticed, does not.
	 */
	private static boolean sightingDue(Haunt h, OccupantConfig cfg) {
		double minutes = switch (h.data.act) {
			case 1 -> 5.0;
			case 2 -> 4.0;
			case 3 -> 3.5;
			default -> 3.0;
		};
		// And not one straight after another while they are not looking: the rest of the story
		// still has to happen in between.
		if (h.lastShowTriedAt >= 0 && h.data.playTicks - h.lastShowTriedAt < 90 * 20) return false;
		return h.data.playTicks - h.lastSeenAt >= minutes * MINUTE / Pacing.frequency(cfg);
	}

	private boolean isEligible(ServerPlayer player, Haunt h, OccupantConfig cfg) {
		if (!cfg.enabled || h.data.paused) return false;
		// It let them go: nothing more, but the end of the story.
		if (h.data.ending == LastNightEnding.FOUND && !Credits.ID.equals(h.activeId)) return false;
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
		double pace = Pacing.storyPace(cfg);
		long inAct = d.playTicks - d.actStartedAt;
		int before = d.act;

		switch (d.act) {
			case 0 -> {
				if (d.playTicks >= Pacing.graceMinutes(cfg) * MINUTE) d.setAct(1);
			}
			case 1 -> {
				if (inAct >= 8 * MINUTE * pace && d.actEventCount >= 3 || inAct >= 15 * MINUTE * pace) d.setAct(2);
			}
			case 2 -> {
				if (inAct >= 12 * MINUTE * pace && d.sightings >= 2 || inAct >= 22 * MINUTE * pace) d.setAct(3);
			}
			case 3 -> {
				if (inAct >= 15 * MINUTE * pace && (d.encounters >= 1 || d.sightings >= 4) || inAct >= 30 * MINUTE * pace) {
					d.setAct(4);
				}
			}
			default -> {
			}
		}

		if (d.act != before) {
			debug("{} entered act {}", player.getName().getString(), d.act);
			save.setDirty();
			// From the third act it hurts them: the first time a few minutes in.
			if (d.act == 3 && before < 3) d.cooldowns.put(ATTACK_DUE, d.playTicks + (long) (5 * MINUTE * pace));
			// From the third act its lair moves closer: dug anew, out in the fog, nearer their home.
			if (d.act >= 3 && OccupantConfig.get().worldChanges) {
				BlockPos home = Compat.respawnPos(player);
				if (home == null) home = player.blockPosition();
				try {
					if (com.wolfsmask.occupant.world.House.lairNear(Compat.level(player).getServer().overworld(), home, player.getRandom())) {
						// They are meant to find it: something heavy, digging, out that way, and the words.
						BlockPos lair = com.wolfsmask.occupant.world.Lairs.newest();
						if (lair != null && player.isAlive()) {
							net.minecraft.world.phys.Vec3 way = new net.minecraft.world.phys.Vec3(lair.getX() - player.getX(), 0.0, lair.getZ() - player.getZ());
							if (way.lengthSqr() > 1.0) way = way.normalize();
							Cues.sound(player, net.minecraft.sounds.SoundEvents.GRAVEL_BREAK, net.minecraft.sounds.SoundSource.HOSTILE,
									player.getEyePosition().add(way.scale(14.0)), 1.0f, 0.5f);
							Cues.whisper(player, "It has dug itself a place. Near you.", 120);
						}
					}
				} catch (RuntimeException e) {
					Occupant.LOGGER.warn("Could not dig its lair closer", e);
				}
			}
			// Each turn of the story is marked, once, quietly: what it has learned about them.
			String[] beat = d.act >= 2 && d.act - 2 < ACT_BEATS.length ? ACT_BEATS[d.act - 2] : null;
			if (beat != null && player.isAlive()) {
				Cues.whisper(player, beat[player.getRandom().nextInt(beat.length)]
						.replace("{player}", player.getName().getString()), 140);
			}
		}
	}

	/** Said once as each act begins, from the second: how much closer it has come. */
	private static final String[][] ACT_BEATS = {
			{"It has learned the way you walk.", "It knows where you sleep now.", "It followed you home today."},
			{"It has started to wear your shape.", "It practised your voice while you were away.",
					"It knows your name now, {player}."},
			{"It is done watching.", "It doesn't need to hide from you any more.", "Tonight it stops pretending."}};

	/** Tried first, in this order, until the player has seen it once. */
	private static final List<String> FIRST_SIGHTINGS = List.of("distant", "hallway", "watcher");

	/** Minutes until the Director next tries something, for the current act. */
	private static double[] intervalMinutes(int act) {
		return switch (act) {
			case 1 -> new double[]{1.5, 3.0};
			case 2 -> new double[]{1.2, 2.5};
			case 3 -> new double[]{1.0, 2.2};
			default -> new double[]{0.8, 1.8};
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
				w.put(HorrorEvent.Tier.AMBIENT, 50.0);
				w.put(HorrorEvent.Tier.MINOR, 40.0);
				w.put(HorrorEvent.Tier.MAJOR, 10.0);
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

		// Up to three times as likely to be something real, after a few quiet minutes.
		double pressure = 1.0 + 2.0 * Math.min(1.0, Math.max(0, quietSeconds - 120) / 300.0);
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

		// Until it has been seen at least once, being seen comes first: far off, then closer.
		if (d.sightings == 0) {
			for (String id : FIRST_SIGHTINGS) {
				for (List<HorrorEvent> pool : byTier.values()) {
					for (HorrorEvent e : List.copyOf(pool)) {
						if (e.id().equals(id) && tryBegin(h, e, ctx)) {
							h.quietSeconds = 0;
							scheduleAfter(h, e, random, cfg);
							return;
						}
					}
				}
			}
		}

		// From the third act it hurts them, every so often, whatever the dread, the hour or where
		// they are: it saves them from everything else so that only it gets to.
		if (attackDue(d, cfg) && attack(h, player, s, cfg, false)) return;

		// Too long since it was seen: something it can be seen in comes first, if anything fits.
		if (sightingDue(h, cfg)) {
			List<HorrorEvent> visible = new ArrayList<>();
			for (List<HorrorEvent> pool : byTier.values()) {
				for (HorrorEvent e : pool) if (e.shows() && e.tier() != HorrorEvent.Tier.PEAK) visible.add(e);
			}
			while (!visible.isEmpty()) {
				HorrorEvent e = pickWeighted(visible, ctx, random);
				visible.remove(e);
				if (tryBegin(h, e, ctx)) {
					h.quietSeconds = 0;
					h.chain = 0;
					scheduleAfter(h, e, random, cfg);
					return;
				}
			}
		}

		HorrorEvent.Tier[] order = pickTierOrder(tierWeights(d.act, Pacing.quietSeconds(h.quietSeconds)), byTier.keySet(), random);
		for (HorrorEvent.Tier tier : order) {
			List<HorrorEvent> pool = byTier.get(tier);
			while (pool != null && !pool.isEmpty()) {
				HorrorEvent e = pickWeighted(pool, ctx, random);
				pool.remove(e);
				if (tryBegin(h, e, ctx)) {
					// Background noise does not relieve the pressure; it is part of the waiting.
					if (e.tier() != HorrorEvent.Tier.AMBIENT) h.quietSeconds = 0;
					h.chain = 0;
					scheduleAfter(h, e, random, cfg);
					return;
				}
			}
		}

		// Nothing could happen convincingly. Try again soon.
		h.nextEventIn = 20 * (20 + random.nextInt(25));
	}

	/**
	 * Whether it is due to come and hurt them: from the third act, once its time has come round
	 * (see ATTACK_DUE), and only once they have looked right at it twice: it shows itself before it
	 * ever lays a leg on them.
	 */
	public static boolean attackDue(HauntData d, OccupantConfig cfg) {
		return cfg.attacks && !cfg.soundOnly && d.act >= 3 && d.sightings >= 2 && !d.isOnCooldown(ATTACK_DUE);
	}

	/** They got away from it: it comes again sooner than it would have. */
	public static void attackSoon(HauntData d) {
		long soon = d.playTicks + 3 * MINUTE;
		Long due = d.cooldowns.get(ATTACK_DUE);
		if (due == null || due > soon) d.cooldowns.put(ATTACK_DUE, soon);
	}

	/**
	 * It comes to hurt them: out in the open it hunts them, if it has not lately; anywhere, it is
	 * right behind them. False if neither can happen here and now (it is still due, and tried again
	 * next time). The fright-only versions of these keep their own cooldowns and rules. Forced (by
	 * a command), what they are doing is not asked, only whether there is somewhere it can be.
	 */
	private boolean attack(Haunt h, ServerPlayer player, Situation s, OccupantConfig cfg, boolean forced) {
		HauntData d = h.data;
		EventContext ctx = new EventContext(player, h, s, forced);
		ctx.attack = true;
		for (String id : ATTACKS) {
			HorrorEvent e = Events.byId(id);
			if (e == null || disabledEvents.contains(id) || d.act < e.minAct()) continue;
			if (id.equals("hunt") && (!cfg.chases || d.isOnCooldown(id))) continue;
			if (!forced && !e.fits(ctx) || !tryBegin(h, e, ctx)) continue;
			d.cooldowns.put(ATTACK_DUE, d.playTicks + (long) ((d.act >= 4 ? 8 : 12) * MINUTE / Pacing.frequency(cfg)));
			h.quietSeconds = 0;
			h.chain = 0;
			scheduleAfter(h, e, player.getRandom(), cfg);
			debug("{}: it came to hurt them ({})", player.getName().getString(), id);
			return true;
		}
		return false;
	}

	private boolean isCandidate(HorrorEvent e, EventContext ctx, boolean calm) {
		HauntData d = ctx.data;
		if (e.hookOnly() || disabledEvents.contains(e.id())) return false;
		if (d.act < e.minAct() || d.isOnCooldown(e.id()) || !e.allowedBy(ctx.config)) return false;
		if (ctx.config.soundOnly && e.shows()) return false;
		if (d.recency(e.id()) == 0) return false; // never the same thing twice in a row
		if (calm && e.tier() != HorrorEvent.Tier.AMBIENT) return false;
		if (e.tier() == HorrorEvent.Tier.PEAK && !peakReady(d)) return false;
		return e.fits(ctx);
	}

	public static boolean peakReady(HauntData d) {
		if (d.dread < Pacing.peakDread()) return false;
		return d.lastPeakAt < 0 || d.playTicks - d.lastPeakAt >= Pacing.minutesBetweenPeaks() * MINUTE;
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
		if (e.shows()) h.lastShowTriedAt = d.playTicks;
		d.recordEvent(e.id(), e.cooldownTicks());
		if (e.tier() == HorrorEvent.Tier.PEAK) {
			d.lastPeakAt = d.playTicks;
			d.dread = 15f;
			d.calmUntil = d.playTicks + (long) ((4 + ctx.random.nextInt(4)) * MINUTE / Pacing.frequency(ctx.config));
		} else {
			d.addDread(e.tier().dread);
			if (e.tier() == HorrorEvent.Tier.MAJOR) {
				d.calmUntil = Math.max(d.calmUntil, d.playTicks + (long) ((1 + ctx.random.nextInt(2)) * MINUTE / Pacing.frequency(ctx.config)));
			}
		}
		save.setDirty();
		debug("{}: started {} (act {}, dread {})", ctx.player.getName().getString(), e.id(), d.act, (int) d.dread);
		com.wolfsmask.occupant.story.Achievements.onEvent(ctx.player, e.id());
		return true;
	}

	private void scheduleAfter(Haunt h, HorrorEvent e, RandomSource random, OccupantConfig cfg) {
		double[] range = intervalMinutes(h.data.act);
		double minutes = range[0] + random.nextDouble() * (range[1] - range[0]);
		minutes *= 1.0 - Math.min(0.4, h.data.dread / 250.0); // more dread, faster pace
		if (h.data.playTicks < h.data.calmUntil) minutes *= 1.6;
		if (h.lastSituationWasNight) minutes *= 0.75;         // the nights are busier than the days
		minutes /= Pacing.frequency(cfg);
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
			if (OccupantConfig.get().soundOnly && e.shows()) return TriggerResult.BUSY;
		} else {
			endSequence(h);
		}
		Situation s = h.capture(player);
		EventContext ctx = new EventContext(player, h, s, forced);
		if (!forced && (!e.allowedBy(ctx.config) || !e.fits(ctx))) return TriggerResult.NO_SPOT;
		return tryBegin(h, e, ctx) ? TriggerResult.STARTED : TriggerResult.NO_SPOT;
	}

	/**
	 * It comes to hurt them now, the way it does when it is due (for /occupant attack and the
	 * tests), over whatever was happening: STARTED, or NO_SPOT if it cannot here and now.
	 */
	public TriggerResult attackNow(ServerPlayer player) {
		Haunt h = haunt(player);
		endSequence(h);
		return attack(h, player, h.capture(player), OccupantConfig.get(), true) ? TriggerResult.STARTED : TriggerResult.NO_SPOT;
	}

	/** Starts something that is not one of the events, now, over whatever was happening. */
	public void beginNow(ServerPlayer player, String id, Sequence sequence) {
		Haunt h = haunt(player);
		endSequence(h);
		h.pending = null;
		h.active = sequence;
		h.activeId = id;
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
