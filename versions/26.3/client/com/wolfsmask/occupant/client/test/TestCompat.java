package com.wolfsmask.occupant.client.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Method;

/** The test API calls that differ between versions. This copy is for 26.3 and later. */
final class TestCompat {
	private TestCompat() {
	}

	/**
	 * Wait until the world around the player has loaded and been drawn. In 26.3 this moved behind
	 * the connection; found by name so a further rename in a snapshot only weakens the wait.
	 */
	static void waitForWorld(TestSingleplayerContext game) {
		Object connection = game.getConnection();
		Object target = call(connection, "getClientLevel");
		if (target == null) target = connection;
		if (call(target, "waitForChunksRender") == null && call(target, "waitForChunksDownload") == null) {
			throw new IllegalStateException("no way to wait for chunks on " + target.getClass());
		}
	}

	private static Object call(Object on, String name) {
		if (on == null) return null;
		for (Method m : on.getClass().getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == 0) {
				try {
					Object r = m.invoke(on);
					return r == null ? Boolean.TRUE : r;
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		return null;
	}
}
