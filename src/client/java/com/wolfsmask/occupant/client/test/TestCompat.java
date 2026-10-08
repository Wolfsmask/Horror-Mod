package com.wolfsmask.occupant.client.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.function.Consumer;

/** The test API calls that differ between versions. This copy is for 26.1 and 26.2. */
final class TestCompat {
	private TestCompat() {
	}

	/** Wait until the world around the player has loaded and been drawn. */
	static void waitForWorld(TestSingleplayerContext game) {
		game.getClientLevel().waitForChunksRender();
	}

	/** Start a real server, join it over the network, wait for the world, and run {@code body}. */
	static void joinServer(ClientGameTestContext context, Consumer<TestServerContext> body) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestServerConnection connection = server.connect()) {
				connection.getClientLevel().waitForChunksRender();
				body.accept(server);
			}
		}
	}
}
