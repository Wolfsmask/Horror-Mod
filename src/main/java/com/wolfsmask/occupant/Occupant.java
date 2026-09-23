package com.wolfsmask.occupant;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Occupant implements ModInitializer {
	public static final String MOD_ID = "occupant";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("The Occupant is loading.");
	}
}
