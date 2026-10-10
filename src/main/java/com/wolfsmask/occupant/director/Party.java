package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.compat.Compat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who else is there. It is one world and one thing in it: what happens to one player happens in
 * front of the others near them. They hear what is near enough to hear, they see it when it is
 * there, and when it comes for one of them the ones close by watch it happen.
 * <p>
 * In the game tests, where every test's player stands a few blocks from every other's, players
 * only count as together when a test has put them in the same party ({@link #testParty}).
 */
public final class Party {
	/** Near enough to be in it with them: they watch its scenes, and its words reach them. */
	public static final double NEAR = 48.0;
	/** Near enough that a second one of it would be seen with the first: there is only ever one. */
	public static final double ONE_OF_IT = 96.0;
	/** Near enough that its turning their eyes to a scene is fair: they could see it from there. */
	public static final double WATCH = 32.0;

	private static final boolean TESTING = System.getProperty("fabric-api.gametest") != null;
	/** In the game tests: which party each test's players are in. */
	private static final Map<UUID, Integer> TEST_PARTIES = new ConcurrentHashMap<>();

	private Party() {
	}

	/** For the game tests: these players are together, and with no one else. */
	public static void testParty(int party, ServerPlayer... players) {
		for (ServerPlayer p : players) TEST_PARTIES.put(p.getUUID(), party);
	}

	/** Whether these two could be in the same moment at all (the tests keep theirs apart). */
	public static boolean couldMeet(UUID a, UUID b) {
		if (a.equals(b)) return false;
		if (!TESTING) return true;
		Integer pa = TEST_PARTIES.get(a), pb = TEST_PARTIES.get(b);
		return pa != null && pa.equals(pb);
	}

	/** Someone who can take part: alive, in the world, not watching as a spectator. */
	private static boolean present(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isRemoved();
	}

	/** The other players within {@code radius} of {@code player}, in the same world. */
	public static List<ServerPlayer> others(ServerPlayer player, double radius) {
		return near(player, player.position(), radius);
	}

	/** The other players (not {@code player}) within {@code radius} of {@code at}, in {@code player}'s world. */
	public static List<ServerPlayer> near(ServerPlayer player, Vec3 at, double radius) {
		MinecraftServer server = Compat.level(player).getServer();
		if (server == null) return List.of();
		List<ServerPlayer> out = new ArrayList<>(2);
		double r2 = radius * radius;
		for (ServerPlayer o : server.getPlayerList().getPlayers()) {
			if (o == player || o.level() != player.level() || !present(o)) continue;
			if (!couldMeet(player.getUUID(), o.getUUID())) continue;
			if (o.position().distanceToSqr(at) <= r2) out.add(o);
		}
		return out;
	}

	/**
	 * The others who watch a scene of {@code player}'s: close enough to see it, and not in a fight
	 * of their own (their eyes are not taken from something that is trying to kill them).
	 */
	public static List<ServerPlayer> watchers(ServerPlayer player) {
		List<ServerPlayer> out = new ArrayList<>(2);
		for (ServerPlayer o : others(player, WATCH)) {
			boolean fighting = o.tickCount - o.getLastHurtByMobTimestamp() < 100;
			if (!fighting) out.add(o);
		}
		return out;
	}

	/** Every player on the server who can be told something: the whole world hears it. */
	public static List<ServerPlayer> everyone(ServerPlayer player) {
		MinecraftServer server = Compat.level(player).getServer();
		if (server == null) return List.of(player);
		List<ServerPlayer> out = new ArrayList<>();
		out.add(player);
		for (ServerPlayer o : server.getPlayerList().getPlayers()) {
			if (o != player && couldMeet(player.getUUID(), o.getUUID())) out.add(o);
		}
		return out;
	}
}
