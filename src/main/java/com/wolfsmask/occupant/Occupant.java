package com.wolfsmask.occupant;

import com.wolfsmask.occupant.command.OccupantCommand;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.events.WakeEvent;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Occupant implements ModInitializer {
	public static final String MOD_ID = "occupant";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		OccupantConfig.load();
		ModSounds.init();
		ModEntities.init();
		PayloadTypeRegistry.playS2C().register(ScreenEffectPayload.ID, ScreenEffectPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(Director::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Director.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Director director = Director.get();
			if (director != null) director.tick();
		});

		// It listens.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			Director director = Director.get();
			if (director != null) director.onChat(sender, message.getSignedContent());
		});

		// "You may not rest now, there are monsters nearby." There are none you can see.
		EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> shouldDenySleep(player)
				? PlayerEntity.SleepFailureReason.NOT_SAFE : null);

		// Waking up is not always a relief.
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			Director director = Director.get();
			if (director == null || !(entity instanceof ServerPlayerEntity player)) return;
			if (player.getRandom().nextFloat() < 0.3f && !director.haunt(player).isBusy()) {
				director.trigger(player, WakeEvent.ID, false);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				OccupantCommand.register(dispatcher));

		LOGGER.info("The Occupant has moved in.");
	}

	private static boolean shouldDenySleep(PlayerEntity player) {
		OccupantConfig cfg = OccupantConfig.get();
		Director director = Director.get();
		if (!cfg.enabled || !cfg.interruptSleep || director == null) return false;
		if (!(player instanceof ServerPlayerEntity sp) || sp.isCreative() && !cfg.hauntCreative) return false;

		HauntData data = director.data(sp);
		if (data.paused || data.act < 2) return false;
		long day = sp.getServerWorld().getTimeOfDay() / 24000L;
		if (data.sleepDenyDay == day) return false;
		if (sp.getRandom().nextFloat() >= 0.35f) return false;

		data.sleepDenyDay = day;
		data.addDread(5f);
		director.markDirty();
		return true;
	}
}
