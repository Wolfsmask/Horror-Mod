package com.wolfsmask.occupant.client.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.function.Consumer;

/** The test API calls that differ between versions. This copy is for 1.21.11. */
final class TestCompat {
	private TestCompat() {
	}

	/** Wait until the world around the player has loaded and been drawn. */
	static void waitForWorld(TestSingleplayerContext game) {
		game.getClientWorld().waitForChunksRender();
	}

	/** Not on 1.21.11: its test runs without the network synchroniser a joined server needs. */
	static void joinServer(ClientGameTestContext context, Consumer<TestServerContext> body) {
		com.wolfsmask.occupant.Occupant.LOGGER.info("[client-gametest] over the network: not on 1.21.11");
	}
}
