package com.wolfsmask.occupant;

import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.command.OccupantCommand;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.network.ScreenEffectPayload;
import com.wolfsmask.occupant.network.WhisperPayload;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.world.House;
import com.wolfsmask.occupant.world.ModWorld;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.world.Loot;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Occupant implements ModInitializer {
	public static final String MOD_ID = "occupant";
	/** Shown by /occupant check, so a report always says which build it came from. */
	public static final String VERSION_NOTE = "is loaded.";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Runs mod code that the game called into. Nothing the Occupant does may ever escape back
	 * out into vanilla: an exception thrown inside one of these callbacks does not stop at the
	 * mod, it comes out inside whatever the game was doing at the time. Thrown while the server
	 * is placing a player into the world, for instance, it ends the join with "Invalid player
	 * data" and the player cannot get in at all.
	 */
	private static void guard(String what, Runnable action) {
		try {
			action.run();
		} catch (Exception | LinkageError e) {
			LOGGER.error("The Occupant failed during {} and was ignored. The game is unaffected.", what, e);
		}
	}

	private static <T> T guard(String what, java.util.function.Supplier<T> action, T fallback) {
		try {
			return action.get();
		} catch (Exception | LinkageError e) {
			LOGGER.error("The Occupant failed during {} and was ignored. The game is unaffected.", what, e);
			return fallback;
		}
	}

	@Override
	public void onInitialize() {
		if (Boolean.getBoolean("occupant.smoke")) registerSmokeTest();
		OccupantConfig.load();
		ModSounds.init();
		ModEntities.init();
		ModWorld.init();
		Compat.serverToClient().register(ScreenEffectPayload.TYPE, ScreenEffectPayload.CODEC);
		Compat.serverToClient().register(WhisperPayload.TYPE, WhisperPayload.CODEC);

		// Before any of the world is generated: which houses exist, and whether one has been found.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> guard("opening the world", () -> {
			House.open(server);
			Loot.open(server);
		}));
		// Only once the world is open is it known where players appear, so only then are houses built.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> guard("starting the world", () -> House.started(server)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> guard("closing the world", () -> {
			House.close();
			Loot.close();
		}));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> guard("start-up", () -> Director.start(server)));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> guard("shutdown", Director::stop));
		ServerTickEvents.END_SERVER_TICK.register(server -> guard("the server tick", () -> {
			Director director = Director.get();
			if (director != null) director.tick();
			if (server.getTickCount() % 20 == 0) House.tick(server);
		}));

		// It listens.
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> guard("a chat message", () -> {
			Director director = Director.get();
			if (director != null) director.onChat(sender, message.signedContent());
		}));

		// "You may not rest now, there are monsters nearby." There are none you can see. Later on,
		// sometimes, "This bed is occupied." There is nobody in it.
		EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) ->
				guard("a sleep attempt", () -> sleepProblem(player), null));

		// Waking up is not always a relief.
		//
		// This only notes that the player woke; the Director acts on it on its next tick. The
		// game also wakes a player while it is placing them into the world, when they logged
		// out asleep, and at that moment the player is not yet somewhere the world can be asked
		// questions about. Doing the work here would break the join.
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> guard("waking up", () -> {
			Director director = Director.get();
			if (director != null && entity instanceof ServerPlayer player) director.noteWoke(player);
		}));

		// Opening a chest or barrel nobody has opened before: the next page of the survivor's log is in it.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				BlockPos pos = hit.getBlockPos();
				if (Loot.unopened(pos)) guard("opening a container", () -> {
					Director director = Director.get();
					if (director != null && world.getBlockEntity(pos) instanceof Container box) {
						HauntData data = director.data(sp);
						data.logsFound++;
						Loot.opening(sp, pos, box, data.logsFound);
						director.markDirty();
					}
				});
			}
			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				guard("registering commands", () -> OccupantCommand.register(dispatcher)));

		LOGGER.info("The Occupant has moved in. Type /occupant check in game to test it.");
	}

	/**
	 * Only for CI's check that the game starts on versions with no client test API: once the
	 * first player has been in the world for five seconds, it is put in front of them, so the
	 * client has to draw it. Off unless the game was started with -Doccupant.smoke=true.
	 */
	private static void registerSmokeTest() {
		boolean[] done = {false};
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (done[0]) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.tickCount < 100) continue;
				done[0] = true;
				LOGGER.info("[smoke] putting it in front of {}", p.getName().getString());
				server.getCommands().performPrefixedCommand(
						server.createCommandSourceStack().withEntity(p).withPosition(p.position()), "occupant here 6 stare");
				return;
			}
		});
	}

	@org.jetbrains.annotations.Nullable
	private static Player.BedSleepingProblem sleepProblem(Player player) {
		OccupantConfig cfg = OccupantConfig.get();
		Director director = Director.get();
		if (!cfg.enabled || !cfg.interruptSleep || director == null) return null;
		if (!(player instanceof ServerPlayer sp) || sp.isCreative() && !cfg.hauntCreative) return null;

		HauntData data = director.data(sp);
		if (data.paused || data.act < 2) return null;
		long day = Compat.dayTime(sp.level()) / 24000L;
		if (data.sleepDenyDay == day) return null;
		if (sp.getRandom().nextFloat() >= 0.35f) return null;

		data.sleepDenyDay = day;
		data.addDread(5f);
		director.markDirty();
		if (data.act >= 3 && sp.getRandom().nextBoolean()) {
			sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("block.minecraft.bed.occupied"), true);
			return Player.BedSleepingProblem.OTHER_PROBLEM;
		}
		return Player.BedSleepingProblem.NOT_SAFE;
	}
}
