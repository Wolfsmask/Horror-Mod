package com.wolfsmask.occupant.client.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Method;

/** The test API calls that differ between versions. This copy is for 26.3 and later. */
final class TestCompat {
	private static final String TEST_API = "net.fabricmc.fabric.api.client.gametest";

	private TestCompat() {
	}

	/**
	 * Wait until the world around the player has loaded and been drawn. In 26.3 this moved behind
	 * the connection; found by name so a further rename in a snapshot only weakens the wait. Only
	 * the test API's own objects are ever called into: the game's objects may only be touched
	 * from the game's thread, and this runs on the test's.
	 */
	static void waitForWorld(TestSingleplayerContext game) {
		waitThrough(game.getConnection());
	}

	/**
	 * Start a real server, join it over the network, wait for the world, and run {@code body}. The
	 * connection's own methods are found by name, as for {@link #waitForWorld}.
	 */
	static void joinServer(ClientGameTestContext context, java.util.function.Consumer<TestServerContext> body) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			Object connection = server.connect();
			try {
				waitThrough(connection);
				body.accept(server);
			} finally {
				for (String name : new String[]{"close", "disconnect"}) {
					Method m = null;
					for (Method c : connection.getClass().getMethods()) {
						if (c.getName().equals(name) && c.getParameterCount() == 0) m = c;
					}
					if (m != null) {
						invoke(connection, m);
						break;
					}
				}
			}
		}
	}

	private static void waitThrough(Object connection) {
		if (waitOn(connection)) return;
		for (Method m : connection.getClass().getMethods()) {
			if (m.getParameterCount() == 0 && m.getReturnType().getName().startsWith(TEST_API)
					&& m.getDeclaringClass() != Object.class && waitOn(invoke(connection, m))) {
				return;
			}
		}
		throw new IllegalStateException("no way to wait for chunks on " + connection.getClass());
	}

	private static boolean waitOn(Object target) {
		if (target == null) return false;
		for (String name : new String[]{"waitForChunksRender", "waitForChunksDownload"}) {
			for (Method m : target.getClass().getMethods()) {
				if (m.getName().equals(name) && m.getParameterCount() == 0) {
					invoke(target, m);
					return true;
				}
			}
		}
		return false;
	}

	private static Object invoke(Object on, Method m) {
		try {
			m.setAccessible(true);
			return m.invoke(on);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
