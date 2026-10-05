package com.wolfsmask.occupant.client.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** The test API calls that differ between versions. This copy is for 1.21.11. */
final class TestCompat {
	private TestCompat() {
	}

	/** Wait until the world around the player has loaded and been drawn. */
	static void waitForWorld(TestSingleplayerContext game) {
		game.getClientWorld().waitForChunksRender();
	}
}
