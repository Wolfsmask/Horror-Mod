package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Run on a real headless server by CI ({@code ./gradlew runGametest}).
 * These exist so the mod can be trusted not to break a world or a server.
 */
public final class OccupantGameTests {
	private static final int TICKS_PER_EVENT = 120;

	/** Makes it night using the game's own command, so the tests do not depend on clock internals. */
	static void makeNight(ServerLevel level) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set night");
	}

	/** An Occupant that nothing controls must remove itself immediately. */
	@GameTest
	public void uncontrolledOccupantVanishes(GameTestHelper helper) {
		OccupantEntity e = helper.spawn(ModEntities.OCCUPANT, 1, 2, 1);
		helper.runAtTickTime(5, () -> {
			helper.assertTrue(e.isRemoved(), "An uncontrolled Occupant should vanish on its own");
			helper.succeed();
		});
	}

	/**
	 * The real /summon path. It used to spawn an entity with nobody to haunt, which orphan
	 * protection removed on its first tick, so absolutely nothing appeared. This runs the
	 * actual command rather than building the entity by hand, because the bug was in the
	 * difference between the two.
	 */
	@GameTest
	public void summonedOccupantAdoptsAPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.0, 0.0f, 0.0f);

		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				String.format("execute in %s run summon occupant:occupant %d %d %d",
						level.dimension().location(), at.getX(), at.getY(), at.getZ()));

		helper.runAtTickTime(5, () -> {
			List<OccupantEntity> found = level.getEntitiesOfClass(OccupantEntity.class,
					new AABB(at).inflate(6.0), e -> !e.isRemoved());
			helper.assertTrue(!found.isEmpty(), "/summon should leave an Occupant standing there");
			OccupantEntity e = found.get(0);
			helper.assertTrue(e.isSummoned(), "It should know it is driving itself");
			helper.assertTrue(e.isHaunting(player), "It should have adopted the nearest player");
			helper.assertTrue(e.broadcastToPlayer(player), "Its target must be sent the entity");
			e.vanish();
			helper.succeed();
		});
	}

	/** It cannot be hurt, killed or farmed: any damage just makes it vanish. */
	@GameTest
	public void damageMakesItVanish(GameTestHelper helper) {
		OccupantEntity e = helper.spawn(ModEntities.OCCUPANT, 1, 2, 1);
		ServerLevel level = helper.getLevel();
		boolean hurt = e.hurtServer(level, level.damageSources().generic(), 5.0f);
		helper.assertTrue(!hurt, "Damage should never land");
		helper.assertTrue(e.isRemoved(), "Damage should make it vanish");
		helper.succeed();
	}

	/** Story progress survives a save and load exactly. */
	@GameTest
	public void storyDataRoundTrips(GameTestHelper helper) {
		HauntData d = new HauntData();
		d.setAct(3);
		d.dread = 42.5f;
		d.playTicks = 123456;
		d.sightings = 7;
		d.encounters = 2;
		d.recordEvent("watcher", 2400);
		d.rememberChat("hello there");
		Tag saved = HauntData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
		HauntData copy = HauntData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(copy.act == 3 && copy.dread == 42.5f && copy.playTicks == 123456, "Core values should round-trip");
		helper.assertTrue(copy.sightings == 7 && copy.encounters == 2, "Counters should round-trip");
		helper.assertTrue(copy.isOnCooldown("watcher") && copy.recency("watcher") == 0, "Cooldowns and history should round-trip");
		helper.assertTrue("hello there".equals(copy.heardChat.peekFirst()), "Remembered chat should round-trip");
		helper.succeed();
	}

	/**
	 * A player at the very end of the story, at night, and every single event forced one after
	 * another. Nothing may throw, and nothing may be left behind.
	 */
	@GameTest(maxTicks = 40 + TICKS_PER_EVENT * 30)
	public void everyEventRunsCleanly(GameTestHelper helper) {
		Director director = Director.get();
		helper.assertTrue(director != null, "Director should be running");
		ServerLevel level = helper.getLevel();
		OccupantConfig.get().hauntCreative = true;
		makeNight(level);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
		player.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);

		HauntData data = director.data(player);
		data.setAct(HauntData.MAX_ACT);
		data.dread = 100f;
		int errorsBefore = director.totalErrors();

		List<HorrorEvent> events = Events.all();
		List<String> started = new ArrayList<>();
		for (int i = 0; i < events.size(); i++) {
			String id = events.get(i).id();
			helper.runAtTickTime(40 + (long) i * TICKS_PER_EVENT, () -> {
				data.dread = 100f;
				data.lastPeakAt = -1;
				data.calmUntil = 0;
				if (director.trigger(player, id, true) == Director.TriggerResult.STARTED) started.add(id);
			});
		}

		helper.runAtTickTime(40 + (long) events.size() * TICKS_PER_EVENT, () -> {
			director.stopCurrent(player);
			Occupant.LOGGER.info("[gametest] events that found a place to happen: {}", started);
			helper.assertTrue(director.totalErrors() == errorsBefore,
					"No event may throw (errors: " + (director.totalErrors() - errorsBefore) + ", see log)");
			List<OccupantEntity> leftovers = level.getEntitiesOfClass(OccupantEntity.class,
					new AABB(player.blockPosition()).inflate(128), e -> !e.isRemoved());
			helper.assertTrue(leftovers.isEmpty(), "No Occupant may be left in the world after a sequence ends");
			helper.succeed();
		});
	}
}
