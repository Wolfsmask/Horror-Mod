package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Run on a real headless server by CI ({@code ./gradlew runGametest}).
 * These exist so the mod can be trusted not to break a world or a server.
 */
public final class OccupantGameTests implements FabricGameTest {
	private static final int TICKS_PER_EVENT = 120;

	/** An Occupant that nothing controls must remove itself immediately. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void uncontrolledOccupantVanishes(TestContext ctx) {
		OccupantEntity e = ctx.spawnEntity(ModEntities.OCCUPANT, 1, 2, 1);
		ctx.runAtTick(5, () -> {
			ctx.assertTrue(e.isRemoved(), "An uncontrolled Occupant should vanish on its own");
			ctx.complete();
		});
	}

	/** It cannot be hurt, killed or farmed: any damage just makes it vanish. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void damageMakesItVanish(TestContext ctx) {
		OccupantEntity e = ctx.spawnEntity(ModEntities.OCCUPANT, 1, 2, 1);
		boolean hurt = e.damage(ctx.getWorld().getDamageSources().generic(), 5.0f);
		ctx.assertTrue(!hurt, "Damage should never land");
		ctx.assertTrue(e.isRemoved(), "Damage should make it vanish");
		ctx.complete();
	}

	/** Story progress survives a save and load exactly. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void storyDataRoundTrips(TestContext ctx) {
		HauntData d = new HauntData();
		d.setAct(3);
		d.dread = 42.5f;
		d.playTicks = 123456;
		d.sightings = 7;
		d.encounters = 2;
		d.recordEvent("watcher", 2400);
		d.rememberChat("hello there");
		HauntData copy = HauntData.fromNbt(d.toNbt());
		ctx.assertTrue(copy.act == 3 && copy.dread == 42.5f && copy.playTicks == 123456, "Core values should round-trip");
		ctx.assertTrue(copy.sightings == 7 && copy.encounters == 2, "Counters should round-trip");
		ctx.assertTrue(copy.isOnCooldown("watcher") && copy.recency("watcher") == 0, "Cooldowns and history should round-trip");
		ctx.assertTrue("hello there".equals(copy.heardChat.peekFirst()), "Remembered chat should round-trip");
		ctx.complete();
	}

	/**
	 * The big one: a player at the very end of the story, at night, and every single event
	 * forced one after another. Nothing may throw, and nothing may be left behind.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40 + TICKS_PER_EVENT * 30)
	public void everyEventRunsCleanly(TestContext ctx) {
		Director director = Director.get();
		ctx.assertTrue(director != null, "Director should be running");
		ServerWorld world = ctx.getWorld();
		OccupantConfig cfg = OccupantConfig.get();
		boolean previousCreative = cfg.hauntCreative;
		cfg.hauntCreative = true;
		world.setTimeOfDay(18000);

		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		BlockPos start = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		player.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);

		HauntData data = director.data(player);
		data.setAct(HauntData.MAX_ACT);
		data.dread = 100f;
		int errorsBefore = director.totalErrors();

		List<HorrorEvent> events = Events.all();
		List<String> started = new ArrayList<>();
		for (int i = 0; i < events.size(); i++) {
			String id = events.get(i).id();
			int at = 40 + i * TICKS_PER_EVENT;
			ctx.runAtTick(at, () -> {
				data.dread = 100f;
				data.lastPeakAt = -1;
				data.calmUntil = 0;
				if (director.trigger(player, id, true) == Director.TriggerResult.STARTED) started.add(id);
			});
		}

		ctx.runAtTick(40 + events.size() * TICKS_PER_EVENT, () -> {
			director.stopCurrent(player);
			cfg.hauntCreative = previousCreative;
			Occupant.LOGGER.info("[gametest] events that found a place to happen: {}", started);
			ctx.assertTrue(director.totalErrors() == errorsBefore,
					"No event may throw (errors: " + (director.totalErrors() - errorsBefore) + ", see log)");
			List<OccupantEntity> leftovers = world.getEntitiesByClass(OccupantEntity.class,
					new Box(player.getBlockPos()).expand(128), e -> !e.isRemoved());
			ctx.assertTrue(leftovers.isEmpty(), "No Occupant may be left in the world after a sequence ends");
			ctx.complete();
		});
	}
}
