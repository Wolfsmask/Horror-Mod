package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The end of the story, whichever way it went: in the black, the name of the thing, how long they
 * lasted, how often they saw it, and which of the endings this was. Then the picture again, and
 * the story goes on.
 * <p>
 * Run as a sequence of its own after the last night ({@link LastNightEnding}); the end in its
 * lair ({@link TakenEnding}) runs the same lines itself, from its own black.
 */
public final class Credits implements Sequence {
	public static final String ID = "credits";
	/** From the start of the black to the picture coming back. */
	public static final int LENGTH = 440;

	/** Each ending's name, and the last thing said about it, by {@link HauntData#ending}. */
	private static final String[][] ENDINGS = {
			{"It Knows How to Be You", "It is still here. It is patient."},
			{"Left Behind", "It let you go. Keep the fire going."},
			{"So It Came In", "It is inside now. It is closer than it has ever been."},
			{"Taken", "It is out there now, being you."}};

	private final int delay;
	private final int ending;
	private int t;
	@Nullable
	private MinecraftServer server;
	@Nullable
	private UUID who;
	private String name = "";
	private long days;
	private int seen;

	/** After {@code delay} ticks (whatever black and words came first), the credits for {@code ending}. */
	public Credits(int delay, int ending) {
		this.delay = delay;
		this.ending = ending;
	}

	@Override
	public boolean tick(ServerPlayer p) {
		t++;
		if (t == 1) {
			Director director = Director.get();
			server = Compat.level(p).getServer();
			who = p.getUUID();
			name = p.getName().getString();
			days = days(p);
			seen = director == null ? 0 : director.data(p).sightings;
			// Nothing they press does anything until it is over.
			Cues.effect(p, ScreenEffectPayload.CUTSCENE, delay + LENGTH + 20, 1f);
		}
		if (t == delay) {
			Cues.effect(p, ScreenEffectPayload.BLACKOUT, LENGTH, 1f);
			Cues.effect(p, ScreenEffectPayload.SILENCE, 0, 1f);
		}
		if (t >= delay) roll(p, t - delay, name, days, seen, ending);
		if (t == delay + LENGTH) Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
		return t < delay + LENGTH;
	}

	/** How many days they lasted: the days of the world they are in. */
	static long days(ServerPlayer p) {
		return Math.max(1L, Compat.dayTime(Compat.level(p).getServer().overworld()) / 24000L + 1L);
	}

	/** The lines, {@code k} ticks into the black. */
	static void roll(ServerPlayer p, int k, String name, long days, int seen, int ending) {
		String[] end = ENDINGS[Math.max(0, Math.min(ENDINGS.length - 1, ending))];
		if (k == 30) Cues.title(p, "THE OCCUPANT", 150, false);
		if (k == 62) Cues.title(p, name + " lasted " + days + (days == 1 ? " day." : " days."), 80, true);
		if (k == 142) Cues.title(p, "You saw it " + (seen == 1 ? "once" : seen + " times") + ". It saw you every time.", 80, true);
		if (k == 222) Cues.title(p, end[1], 80, true);
		if (k == 300) Cues.title(p, "Thank you for playing.", 120, false);
		if (k == 330) Cues.title(p, "Ending: " + end[0] + ". One of four.", 90, true);
	}

	@Override
	public void end() {
		// Cut short: their view their own again (the black runs out by itself).
		if (t >= delay + LENGTH || server == null || who == null) return;
		ServerPlayer p = server.getPlayerList().getPlayer(who);
		if (p != null) Cues.effect(p, ScreenEffectPayload.CUTSCENE, 0, 0f);
	}
}
