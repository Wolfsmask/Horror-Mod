package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Sight;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When it stands where the player could see it and they do not notice (their crosshair never
 * comes near it) for ten seconds, they are told: "there's something watching you". Never the
 * same words twice running, and never quite the same way: sometimes a line surfacing on the
 * screen, sometimes a thought, sometimes their own name in chat saying it. The longer the story
 * has gone on, and the more often it has been ignored, the less patient the warnings get.
 */
final class Unnoticed {
	/** Ten seconds, in ticks, of it being in plain view without being noticed. */
	private static final int PATIENCE = 200;
	/** How often its view is checked, in ticks. Looking at it is checked every tick. */
	private static final int CHECK = 5;

	/** By tier: uneasy, then insistent, then not asking any more. {where} and {player} are filled in. */
	private static final String[][] LINES = {
			{"Something is watching you.", "You feel eyes on the back of your neck.", "Somewhere near, something has stopped moving.",
					"You are not alone out here.", "Something is watching you, {where}.", "Did something just move, {where}?",
					"It's gone very quiet. Something is listening.", "There's someone standing {where}."},
			{"It has been watching you for a while now.", "Why won't you look at it?", "It is closer than it was a moment ago.",
					"Turn around.", "It knows you saw it. It knows you're pretending you didn't.",
					"Every time you look away, it gets a little closer.", "It's {where}. It has been the whole time."},
			{"LOOK AT IT.", "It is done waiting for you to notice.", "You can't ignore it forever, {player}.",
					"It's right there. It's RIGHT THERE.", "Stop pretending. It can hear you breathing.", "IT SEES YOU, {player}.",
					"{where}. {where}. {where}."}};
	private static final String[] THOUGHTS = {"You get the feeling", "A thought that is not yours:", "Somewhere in your head",
			"Very quietly, close by:"};

	/** For each appearance being watched: ticks in view and unnoticed, or -1 once noticed or warned. */
	private final Map<Integer, Integer> watching = new HashMap<>();
	private final Deque<String> recent = new ArrayDeque<>();
	private List<OccupantEntity> nearby = List.of();
	private int age;

	void tick(ServerPlayer player, Haunt haunt) {
		age++;
		if (age % 20 == 0) {
			// Every second: what is standing round this player, for them.
			nearby = new ArrayList<>(Compat.level(player).getEntitiesOfClass(OccupantEntity.class,
					player.getBoundingBox().inflate(128.0), e -> e.isAlive() && e.isHaunting(player)));
			watching.keySet().removeIf(id -> nearby.stream().noneMatch(e -> e.getId() == id));
		}
		for (OccupantEntity e : nearby) {
			if (!e.isAlive() || e.isConcealed()) continue;
			int seen = watching.getOrDefault(e.getId(), 0);
			if (seen < 0) continue;
			if (noticed(player, e)) {
				watching.put(e.getId(), -1);
				continue;
			}
			if (age % CHECK != 0 || !Sight.canSeeAnyPart(player, e)) continue;
			seen += CHECK;
			if (seen >= PATIENCE) {
				warn(player, haunt, e);
				watching.put(e.getId(), -1);
			} else {
				watching.put(e.getId(), seen);
			}
		}
	}

	/** Their crosshair is on it, or near it. */
	private static boolean noticed(ServerPlayer player, OccupantEntity e) {
		if (Sight.isLookingAt(player, e)) return true;
		double h = e.getBbHeight() * Sight.DRAWN_HEIGHT_FACTOR;
		Vec3 centre = e.position().add(0, h * 0.55, 0);
		double dist = Math.max(0.5, player.getEyePosition().distanceTo(centre));
		double near = 15.0 + Math.toDegrees(Math.atan((h * 0.5) / dist));
		return Sight.angleTo(player, centre) <= near && Sight.canSeeAnyPart(player, e);
	}

	private void warn(ServerPlayer player, Haunt haunt, OccupantEntity e) {
		HauntData d = haunt.data;
		RandomSource random = player.getRandom();
		int tier = Mth.clamp(Math.max(d.act - 2, 0) + d.ignored / 4, 0, LINES.length - 1);
		String line = pick(LINES[tier], random)
				.replace("{where}", where(player, e))
				.replace("{player}", player.getName().getString());
		d.ignored++;

		// A different way each time, in turn.
		OccupantConfig cfg = OccupantConfig.get();
		int style = d.ignored % 3;
		if (style == 0 && cfg.screenWhispers) {
			Cues.whisper(player, line, 80 + 20 * tier);
		} else if (style == 1 || !cfg.fakeMessages) {
			String lead = THOUGHTS[random.nextInt(THOUGHTS.length)];
			Cues.message(player, Component.literal(lead + " ").append(Component.literal(lowerFirst(line)))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else {
			// In their own name, as if they had said it.
			Cues.message(player, Component.literal("<" + player.getName().getString() + "> " + line));
		}
		if (tier >= 2) Cues.effect(player, ScreenEffectPayload.STATIC, 12, 0.5f);
		else if (tier == 1 && random.nextFloat() < 0.4f) Cues.effect(player, ScreenEffectPayload.FLICKER, 10, 0.4f);
	}

	private String pick(String[] lines, RandomSource random) {
		String line = lines[random.nextInt(lines.length)];
		for (int tries = 0; tries < 8 && recent.contains(line); tries++) line = lines[random.nextInt(lines.length)];
		recent.addFirst(line);
		while (recent.size() > 5) recent.removeLast();
		return line;
	}

	/** Which way it is from where they are looking, in words. */
	private static String where(ServerPlayer player, OccupantEntity e) {
		Vec3 look = Sight.flatLook(player);
		Vec3 to = e.position().subtract(player.position());
		double angle = Math.toDegrees(Math.atan2(look.x * to.z - look.z * to.x, look.x * to.x + look.z * to.z));
		double dy = e.getY() - player.getY();
		if (dy > 6) return "above you";
		if (Math.abs(angle) > 125) return "behind you";
		if (Math.abs(angle) < 35) return "ahead of you, in plain sight";
		return angle > 0 ? "off to your right" : "off to your left";
	}

	private static String lowerFirst(String s) {
		if (s.isEmpty() || s.equals(s.toUpperCase())) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}
}
