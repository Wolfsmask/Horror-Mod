package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stay in too long (a house, a hole, a cave, anywhere with a roof over you) and it wants to know
 * why you left it out there. Once in a while, from the second act; more often, and less kindly,
 * as the story goes on. Seconds spent standing still and away from the keys do not count.
 */
final class LeftBehind {
	/** By act, from the second: what it asks. {player} is filled in. */
	private static final String[][] LINES = {
			{"Why did you leave it out there?", "It is still outside. Waiting for you.", "It noticed you went in.",
					"It doesn't know why you went in without it."},
			{"Why did you leave it?", "It doesn't like it when you hide.", "Come back out. It is lonely out there.",
					"It is waiting where you went in.", "Why won't you come out, {player}?"},
			{"why did you leave it", "Come out, {player}.", "It knows where you went in.",
					"You can't stay in there forever.", "It is tired of waiting, {player}."}};
	/** Not asked again for this long, in ticks of play. */
	private static final long AGAIN_AFTER = 20L * 60 * 12;

	private LeftBehind() {
	}

	/** Once a second, from the Director, with what the player is doing now. */
	static void tick(ServerPlayer player, Haunt h, Situation s, OccupantConfig cfg) {
		HauntData d = h.data;
		if (!s.sheltered() && !s.underground()) {
			h.confinedSeconds = 0;
			return;
		}
		if (s.afk()) return;
		h.confinedSeconds++;
		if (d.act < 2 || h.active != null || s.inCombat() || s.busy()) return;
		int limit = (int) ((d.act >= 4 ? 180 : d.act == 3 ? 240 : 360) / Pacing.frequency(cfg));
		if (h.confinedSeconds < limit) return;
		if (h.leftBehindAt >= 0 && d.playTicks - h.leftBehindAt < AGAIN_AFTER) return;
		h.leftBehindAt = d.playTicks;
		h.confinedSeconds = 0;

		String[] lines = LINES[Math.min(LINES.length - 1, d.act - 2)];
		String line = lines[player.getRandom().nextInt(lines.length)].replace("{player}", player.getName().getString());
		if (cfg.screenWhispers) {
			Cues.whisper(player, line, 110);
		} else {
			Cues.message(player, Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		d.addDread(4f);
	}
}
