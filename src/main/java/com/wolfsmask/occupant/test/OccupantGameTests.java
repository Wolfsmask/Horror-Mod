package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.director.events.HallwayEvent;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import net.minecraft.world.entity.EntitySpawnReason;
import com.wolfsmask.occupant.world.House;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
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
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level);
		server.getCommands().performPrefixedCommand(source,
				String.format("summon occupant:occupant %d %d %d", at.getX(), at.getY(), at.getZ()));

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

	/**
	 * The game wakes a sleeping player while it is placing them into the world, which is before
	 * the world can safely be asked questions about where they are. Doing any real work in that
	 * callback ends the join with "Invalid player data" and the player cannot get in at all, so
	 * the hook must do nothing but make a note.
	 */
	@GameTest
	public void wakingUpNeverThrows(GameTestHelper helper) {
		Director director = Director.get();
		helper.assertTrue(director != null, "Director should be running");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		director.data(player).setAct(HauntData.MAX_ACT);
		int before = director.totalErrors();

		// Exactly what the game does on the join path, and again on a normal morning.
		for (int i = 0; i < 20; i++) director.noteWoke(player);

		helper.runAtTickTime(4, () -> {
			helper.assertTrue(director.totalErrors() == before,
					"Waking up must never raise an error (see the log)");
			director.stopCurrent(player);
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
		d.logsFound = 5;
		d.ignored = 3;
		d.introduced = true;
		d.recordEvent("watcher", 2400);
		d.rememberChat("hello there");
		Tag saved = HauntData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
		HauntData copy = HauntData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(copy.act == 3 && copy.dread == 42.5f && copy.playTicks == 123456, "Core values should round-trip");
		helper.assertTrue(copy.sightings == 7 && copy.encounters == 2, "Counters should round-trip");
		helper.assertTrue(copy.logsFound == 5 && copy.ignored == 3 && copy.introduced, "The story so far should round-trip");
		helper.assertTrue(copy.isOnCooldown("watcher") && copy.recency("watcher") == 0, "Cooldowns and history should round-trip");
		helper.assertTrue("hello there".equals(copy.heardChat.peekFirst()), "Remembered chat should round-trip");
		helper.succeed();
	}

	/**
	 * A player at the very end of the story, at night, and every single event forced one after
	 * another. Nothing may throw, and nothing may be left behind.
	 */
	/**
	 * The abandoned house: it builds, the mod can tell when you are standing in it, and from just
	 * inside the front door there is somewhere in its dark side hallway for it to stand, mostly
	 * hidden behind the wall. Built well away from the other tests so it cannot touch them.
	 */
	@GameTest(maxTicks = 60)
	public void itWaitsInTheHouseHallway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		makeNight(level);
		BlockPos floor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 320);
		// Held loaded for the whole test: nobody is standing there, so otherwise the game is free to
		// unload part of the house again before the check runs.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) level.setChunkForced((floor.getX() >> 4) + dx, (floor.getZ() >> 4) + dz, true);
		}
		House.build(level, floor, Rotation.NONE, level.getRandom());

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos inside = House.local(floor, Rotation.NONE, 4, 1, 1);   // just through the door
		player.snapTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0.0f, 0.0f);

		helper.runAtTickTime(5, () -> {
			helper.assertTrue(House.isInside(level, inside), "Standing inside the house should count as inside it");
			helper.assertTrue(!House.isInside(level, inside.offset(0, 0, -3)), "Outside the front door is not inside");
			// Stood just inside the door, facing into the house. Set again here: a mock player is not
			// held still between ticks on every version.
			player.snapTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0.0f, 0.0f);
			player.setYHeadRot(0.0f);
			Occupant.LOGGER.info("[gametest] house test player at {} facing {} / {}", player.blockPosition().subtract(floor),
					player.getYRot(), player.getViewVector(1.0f));
			BlockPos peek = HallwayEvent.find(player, true);
			BlockPos any = peek != null ? peek : HallwayEvent.find(player, false);
			Occupant.LOGGER.info("[gametest] house hallway spot: peeking {} / any {} (relative to the floor: {})", peek, any,
					any == null ? "-" : any.subtract(floor));
			Occupant.LOGGER.info("[gametest] hallway light at the far end: block {} sky {}, dark {}",
					level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, House.local(floor, Rotation.NONE, 10, 2, 7)),
					level.getBrightness(net.minecraft.world.level.LightLayer.SKY, House.local(floor, Rotation.NONE, 10, 2, 7)),
					com.wolfsmask.occupant.util.Spots.isDark(level, House.local(floor, Rotation.NONE, 10, 2, 7)));
			if (any != null) {
				Occupant.LOGGER.info("[gametest] that spot: mask {}, block light {}, sky {}, main room middle block light {} sky {}",
						Integer.toBinaryString(com.wolfsmask.occupant.util.Sight.visibleParts(player, net.minecraft.world.phys.Vec3.atBottomCenterOf(any), 4.2)),
						level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, any.above()),
						level.getBrightness(net.minecraft.world.level.LightLayer.SKY, any.above()),
						level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, House.local(floor, Rotation.NONE, 4, 2, 5)),
						level.getBrightness(net.minecraft.world.level.LightLayer.SKY, House.local(floor, Rotation.NONE, 4, 2, 5)));
			}
			if (any == null || any.getX() < House.local(floor, Rotation.NONE, 8, 1, 1).getX()) {
				StringBuilder why = new StringBuilder();
				for (int z = 1; z <= 9; z++) {
					for (int x = 8; x <= 10; x++) {
						BlockPos at = House.local(floor, Rotation.NONE, x, 1, z);
						why.append(" (").append(x).append(',').append(z).append(") ").append(HallwayEvent.explain(player, at, true));
					}
				}
				Occupant.LOGGER.info("[gametest] hallway spots:{}", why);
			}
			helper.assertTrue(any != null, "There should be somewhere in the dark hallway for it to stand");
			BlockPos hallStart = House.local(floor, Rotation.NONE, 9, 1, 1);
			helper.assertTrue(any.getX() >= hallStart.getX() - 1 && any.getX() <= hallStart.getX() + 1,
					"It should be in the side hallway or its doorway, not the main room (got " + any + ")");
			helper.assertTrue(peek != null, "From the door it should be only just visible, past the edge of the gap");
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) level.setChunkForced((floor.getX() >> 4) + dx, (floor.getZ() >> 4) + dz, false);
			}
			helper.succeed();
		});
	}

	/**
	 * Houses keep turning up, well apart, until somebody comes within a chunk of one. From then
	 * on, nothing builds another, however far away and however suitable the ground.
	 */
	@GameTest(maxTicks = 40)
	public void housesStopOnceOneIsFound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0)).offset(600, 0, 0);
		BlockPos tooClose = first.offset(0, 0, 100);
		BlockPos second = first.offset(0, 0, 400);
		BlockPos third = first.offset(0, 0, 800);
		for (BlockPos at : List.of(first, tooClose, second, third)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
			}
		}
		boolean foundBefore = House.found();
		boolean a = House.tryPlaceHouse(level, level.getRandom(), first);
		boolean b = House.tryPlaceHouse(level, level.getRandom(), tooClose);
		boolean c = House.tryPlaceHouse(level, level.getRandom(), second);
		House.noticeNear(first.offset(10, 1, 4));          // a player walks up to the first one
		boolean d = House.tryPlaceHouse(level, level.getRandom(), third);
		Occupant.LOGGER.info("[gametest] houses: found before {}, first {}, too close {}, second {}, after finding {}",
				foundBefore, a, b, c, d);
		if (!foundBefore) {
			helper.assertTrue(a, "On open flat ground far from spawn, a house should be built");
			helper.assertTrue(!b, "Never two houses close together");
			helper.assertTrue(c, "Until one has been found, another far enough away may be built");
		}
		helper.assertTrue(House.found(), "Walking up to a house should count as finding it");
		helper.assertTrue(!d, "Once a house has been found, no other may ever be built");
		helper.succeed();
	}

	/**
	 * It stands in plain view and the player looks the other way: after ten seconds they are told,
	 * once. Looking at it before then means nothing is said.
	 */
	@GameTest(maxTicks = 300)
	public void anUnnoticedSightingIsPointedOut(GameTestHelper helper) {
		Director director = Director.get();
		helper.assertTrue(director != null, "Director should be running");
		ServerLevel world = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 1));
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180.0f, 0.0f);   // facing north
		OccupantEntity e = ModEntities.OCCUPANT.create(world, EntitySpawnReason.COMMAND);
		helper.assertTrue(e != null, "Could not create the entity");
		e.standAlone(player);
		// Six blocks south, behind. Never added to the world: a mock player is not in the player
		// list, so a real one would leave at once; the watching only needs where it stands.
		e.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 6.5, 180.0f, 0.0f);
		int before = director.data(player).ignored;
		helper.onEachTick(() -> {
			player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180.0f, 0.0f);
			director.watchForTest(player, e);
		});
		helper.runAtTickTime(240, () -> {
			int after = director.data(player).ignored;
			helper.assertTrue(after == before + 1, "Ignored for ten seconds, it should have been pointed out once (" + (after - before) + ")");
			helper.succeed();
		});
	}

	/**
	 * Every kind of place builds, has something left in it, and the first time one of its
	 * containers is opened, the next page of the survivor's log is in there.
	 */
	@GameTest(maxTicks = 60)
	public void placesHoldLootAndTheLog(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-640, 0, 0);
		String[] kinds = {"ruin", "camp", "graves", "cottage"};
		List<BlockPos> chunks = new java.util.ArrayList<>();
		for (int i = 0; i < kinds.length; i++) {
			BlockPos at = start.offset(i * 40, 0, 0);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, true);
					chunks.add(new BlockPos((at.getX() >> 4) + dx, 0, (at.getZ() >> 4) + dz));
				}
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> missing = new java.util.ArrayList<>();
		boolean pageFound = false;
		for (int i = 0; i < kinds.length; i++) {
			BlockPos at = start.offset(i * 40, 0, 0);
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			BlockPos base = new BlockPos(at.getX(), top - 1, at.getZ());
			House.buildPlaceForTest(kinds[i], level, base, level.getRandom());
			BlockPos box = null;
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -3, -8), base.offset(8, 10, 8))) {
				if (level.getBlockEntity(p) instanceof net.minecraft.world.Container && com.wolfsmask.occupant.world.Loot.unopened(p)) {
					box = p.immutable();
					break;
				}
			}
			if (box == null) {
				missing.add(kinds[i]);
				continue;
			}
			net.minecraft.world.Container container = (net.minecraft.world.Container) level.getBlockEntity(box);
			boolean anything = false;
			for (int s = 0; s < container.getContainerSize(); s++) anything |= !container.getItem(s).isEmpty();
			if (!anything) missing.add(kinds[i] + " (empty)");
			com.wolfsmask.occupant.world.Loot.opening(player, box, container, 1);
			for (int s = 0; s < container.getContainerSize(); s++) {
				pageFound |= container.getItem(s).is(net.minecraft.world.item.Items.WRITTEN_BOOK);
			}
			helper.assertTrue(!com.wolfsmask.occupant.world.Loot.unopened(box), "An opened container is no longer unopened");
		}
		for (BlockPos c : chunks) level.setChunkForced(c.getX(), c.getZ(), false);
		Occupant.LOGGER.info("[gametest] places without loot: {}, page found: {}", missing, pageFound);
		helper.assertTrue(missing.isEmpty(), "Every place should have a container with something in it: missing " + missing);
		helper.assertTrue(pageFound, "Opening a container nobody has opened should put a page of the log in it");
		helper.succeed();
	}

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
