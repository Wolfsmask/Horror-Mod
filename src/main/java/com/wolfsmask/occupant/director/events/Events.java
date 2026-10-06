package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every event the Director can choose from. */
public final class Events {
	private static final List<HorrorEvent> ALL = List.of(
			// Act 1+: deniable
			new FootstepsEvent(),
			new FamiliarSoundEvent(),
			new DistantEvent(),
			new CaveNoiseEvent(),
			new DistantMiningEvent(),
			new DoorEvent(),
			new ChestEvent(),
			new TorchEvent(),
			new HallwayEvent(),
			new ThunderEvent(),
			new EchoEvent(),
			new PetsEvent(),
			// Act 2+: something is here
			new CloseSoundEvent("breath", 2, 5, 12, () -> ModSounds.BREATH, true, 0.5f),
			new KnockEvent(),
			new FakeJoinEvent(),
			new SignEvent(),
			new RearrangedEvent(),
			new MarkerTorchEvent(),
			new TunnelEvent(),
			new WatcherEvent(),
			new WindowEvent(),
			new RoofEvent(),
			new FireOutEvent(),
			new TurnedEvent(),
			new FootprintsEvent(),
			new SavingEvent(),
			new TeammateEvent(),
			new CorridorEvent(),
			new MorningEvent(),
			new RadioEvent(),
			new LairEvent(),
			new StareEvent(),
			new FakeAdvancementEvent(),
			// Act 3+: it is getting closer
			new CloseSoundEvent("whisper", 3, 4, 15, () -> ModSounds.WHISPER, false, 0.4f),
			new DoppelChatEvent(),
			new StalkerEvent(),
			new FlickerEvent(),
			new StaticEvent(),
			new IntruderEvent(),
			new BehindYouEvent(),
			new WakeEvent(),
			// Act 4: it hunts, and once, the last night
			new HuntEvent(),
			new LastNightEvent()
	);

	private static final Map<String, HorrorEvent> BY_ID = new LinkedHashMap<>();

	static {
		for (HorrorEvent e : ALL) {
			if (BY_ID.put(e.id(), e) != null) throw new IllegalStateException("Duplicate event id " + e.id());
		}
	}

	private Events() {
	}

	public static List<HorrorEvent> all() {
		return ALL;
	}

	@Nullable
	public static HorrorEvent byId(String id) {
		return BY_ID.get(id);
	}

	public static Iterable<String> ids() {
		return BY_ID.keySet();
	}
}
