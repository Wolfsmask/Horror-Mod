package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.Fog;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.events.DistantEvent;
import com.wolfsmask.occupant.director.events.Events;
import com.wolfsmask.occupant.director.events.HallwayEvent;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.util.FogLine;
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

	/**
	 * It cannot be hurt, killed or farmed: struck by a player, it simply vanishes. The world itself
	 * (a berry bush, a cactus, a stray arrow) does nothing to it at all.
	 */
	@GameTest
	public void damageMakesItVanish(GameTestHelper helper) {
		OccupantEntity e = helper.spawn(ModEntities.OCCUPANT, 1, 2, 1);
		ServerLevel level = helper.getLevel();
		boolean hurt = e.hurtServer(level, level.damageSources().generic(), 5.0f);
		helper.assertTrue(!hurt, "Damage should never land");
		helper.assertTrue(!e.isRemoved(), "The world should not make it vanish");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		hurt = e.hurtServer(level, level.damageSources().playerAttack(player), 5.0f);
		helper.assertTrue(!hurt, "A player's blow should never land");
		helper.assertTrue(e.isRemoved(), "A player's blow should make it vanish");
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
		d.lastNight = true;
		d.lastCamp = 1;
		d.lastCampX = -1234;
		d.lastCampZ = 5678;
		d.insideSeconds = 300;
		d.ending = 2;
		d.lastPageAt = 77;
		d.recordEvent("watcher", 2400);
		d.rememberChat("hello there");
		Tag saved = HauntData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
		HauntData copy = HauntData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(copy.act == 3 && copy.dread == 42.5f && copy.playTicks == 123456, "Core values should round-trip");
		helper.assertTrue(copy.sightings == 7 && copy.encounters == 2, "Counters should round-trip");
		helper.assertTrue(copy.logsFound == 5 && copy.ignored == 3 && copy.introduced && copy.lastNight,
				"The story so far should round-trip");
		helper.assertTrue(copy.lastCamp == 1 && copy.lastCampX == -1234 && copy.lastCampZ == 5678 && copy.insideSeconds == 300
				&& copy.ending == 2 && copy.lastPageAt == 77,
				"Where the last camp is, and how the story was lived, should round-trip");
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
		// High in open air, so nothing anywhere near the test can stand between them.
		BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 1)).above(48);
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180.0f, 0.0f);   // facing north
		player.setYHeadRot(180.0f);                     // where a player looks is where their head points
		OccupantEntity e = ModEntities.OCCUPANT.create(world, EntitySpawnReason.COMMAND);
		helper.assertTrue(e != null, "Could not create the entity");
		e.standAlone(player);
		// Six blocks south, behind. Never added to the world: a mock player is not in the player
		// list, so a real one would leave at once; the watching only needs where it stands.
		e.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 6.5, 180.0f, 0.0f);
		int before = director.data(player).ignored;
		helper.onEachTick(() -> {
			player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180.0f, 0.0f);
			player.setYHeadRot(180.0f);
			director.watchForTest(player, e);
		});
		helper.runAtTickTime(240, () -> {
			int after = director.data(player).ignored;
			String state = director.describeForTest(player, e);
			Occupant.LOGGER.info("[gametest] unnoticed: {}", state);
			helper.assertTrue(after == before + 1, "Ignored for ten seconds, it should have been pointed out once ("
					+ (after - before) + "; " + state + ")");
			helper.succeed();
		});
	}

	/**
	 * The survivor's last camp: built where the log says, with what is left of them and a chest
	 * that holds their last page; and the page that says where it is gives the real place.
	 */
	@GameTest(maxTicks = 60)
	public void theSurvivorsLastCampIsWhereTheLogSays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, -700);
		// Loaded first: the height of a chunk nobody has loaded is the bottom of the world.
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		BlockPos base = new BlockPos(at.getX(), top - 1, at.getZ());
		com.wolfsmask.occupant.world.LastCamp.build(level, base, level.getRandom());
		HauntData d = new HauntData();
		d.lastCamp = 1;
		d.lastCampX = base.getX();
		d.lastCampZ = base.getZ();
		BlockPos box = null;
		boolean skull = false;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-7, -3, -7), base.offset(7, 8, 7))) {
			if (box == null && com.wolfsmask.occupant.world.Loot.unopened(p)) box = p.immutable();
			skull |= level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SKELETON_SKULL);
		}
		helper.assertTrue(box != null, "The last camp should have a chest nobody has opened");
		helper.assertTrue(com.wolfsmask.occupant.world.LastCamp.isTheirs(d, box), "That chest should be known as theirs");
		helper.assertTrue(skull, "What is left of them should be there");
		Occupant.LOGGER.info("[gametest] last camp at {}, chest at {}", base, box);
		helper.succeed();
	}

	/**
	 * Its lair: a hole with a ladder all the way down, and a hollow at the bottom that is known as
	 * one, so going in is noticed, and somewhere outside it is not.
	 */
	@GameTest(maxTicks = 60)
	public void theLairGoesAllTheWayDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = lairSite(level, helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 700));
		House.buildPlaceForTest("lair", level, base, level.getRandom());
		int ladders = 0;
		for (int y = 0; y >= -26; y--) {
			if (level.getBlockState(base.offset(0, y, 0)).is(net.minecraft.world.level.block.Blocks.LADDER)) ladders++;
		}
		helper.assertTrue(ladders >= 20, "The ladder should go all the way down, but there were only " + ladders + " rungs");
		BlockPos inside = base.offset(3, -22, 0);
		helper.assertTrue(com.wolfsmask.occupant.world.Lairs.hollowAt(inside) != null, "The hollow should be known as its lair");
		helper.assertTrue(level.getBlockState(inside).isAir(), "The hollow should be dug out");
		helper.assertTrue(com.wolfsmask.occupant.world.Lairs.hollowAt(base.offset(0, 0, 40)) == null, "Outside it is not its lair");
		// Going in: whatever it does, it must not throw.
		Director director = Director.get();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		director.data(player).setAct(3);
		int errors = director.totalErrors();
		player.snapTo(inside.getX() + 0.5, inside.getY() - 1, inside.getZ() + 0.5, 90.0f, 0.0f);
		Director.TriggerResult result = director.trigger(player, "lair", true);
		director.stopCurrent(player);
		helper.assertTrue(director.totalErrors() == errors, "Going into the lair must not throw");
		Occupant.LOGGER.info("[gametest] lair under {}: {} rungs, going in: {}", base, ladders, result);
		helper.succeed();
	}

	/**
	 * The test world is flat, a few blocks above its floor: no room under it for a lair. So, a
	 * block of stone up in the air to dig it into, its top at y 0. Returns the middle of that top.
	 */
	private static BlockPos lairSite(ServerLevel level, BlockPos at) {
		BlockPos top = new BlockPos(at.getX(), 0, at.getZ());
		for (BlockPos p : BlockPos.betweenClosed(top.offset(-9, -30, -9), top.offset(9, 0, 9))) {
			level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
		return top;
	}

	/**
	 * The endings are different places to wake up in. Found the survivor's camp: by their fire,
	 * lit again, with one more page, and let go. Hid all story long: it came in, and it is closer.
	 */
	@GameTest(maxTicks = 60)
	public void theEndingsAreDifferent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Director director = Director.get();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0)).offset(700, 0, 700);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		BlockPos base = new BlockPos(at.getX(), top - 1, at.getZ());
		com.wolfsmask.occupant.world.LastCamp.build(level, base, level.getRandom());

		HauntData d = director.data(player);
		d.setAct(HauntData.MAX_ACT);
		d.lastCamp = 2;
		d.lastCampX = base.getX();
		d.lastCampZ = base.getZ();
		helper.assertTrue(com.wolfsmask.occupant.director.LastNightEnding.which(d) == com.wolfsmask.occupant.director.LastNightEnding.FOUND,
				"Having found their camp should be the ending that lets you go");
		com.wolfsmask.occupant.director.LastNightEnding.play(player, director.haunt(player), com.wolfsmask.occupant.director.LastNightEnding.FOUND);
		boolean book = false;
		for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
			book |= player.getInventory().getItem(s).is(net.minecraft.world.item.Items.WRITTEN_BOOK);
		}
		boolean lit = false;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-7, -3, -7), base.offset(7, 6, 7))) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			lit |= s.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
		}
		helper.assertTrue(d.lastNight && d.ending == 1, "The story should be over, and remember how");
		helper.assertTrue(book, "Their last page should be in your pocket");
		helper.assertTrue(lit, "Their fire should be lit again");

		HauntData hid = new HauntData();
		hid.insideSeconds = 3000;
		hid.outsideSeconds = 200;
		helper.assertTrue(com.wolfsmask.occupant.director.LastNightEnding.which(hid) == com.wolfsmask.occupant.director.LastNightEnding.HID,
				"Hiding indoors all story should be the ending where it comes in");
		d.ending = -1;
		d.lastNight = false;
		d.setAct(HauntData.MAX_ACT);
		com.wolfsmask.occupant.director.LastNightEnding.play(player, director.haunt(player), com.wolfsmask.occupant.director.LastNightEnding.HID);
		helper.assertTrue(d.ending == 2 && d.act == HauntData.MAX_ACT - 1, "After it comes in, it should still be close (act " + d.act + ")");
		helper.succeed();
	}

	/** The land is a little wrong: over a few chunks, dead trees and bare ground, and not a leaf. */
	@GameTest(maxTicks = 60)
	public void theLandIsALittleWrong(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Its own meadow, up in the air: the test world's ground is not the same on every version.
		BlockPos corner = helper.absolutePos(BlockPos.ZERO).offset(-700, 0, 700);
		BlockPos at = new BlockPos(corner.getX() & ~15, 40, corner.getZ() & ~15);
		int changed = 0;
		int leaves = 0;
		for (int cx = 0; cx < 8; cx++) {
			for (int cz = 0; cz < 8; cz++) {
				BlockPos origin = at.offset(cx * 16, 0, cz * 16);
				level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
				for (BlockPos p : BlockPos.betweenClosed(origin.offset(0, -1, 0), origin.offset(15, -1, 15))) {
					level.setBlock(p, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
				for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(15, 0, 15))) {
					level.setBlock(p, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
				House.blightForTest(level, level.getRandom(), origin);
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(0, -2, 0), at.offset(8 * 16, 12, 8 * 16))) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
					|| s.is(net.minecraft.world.level.block.Blocks.BONE_BLOCK)) changed++;
			if (s.is(net.minecraft.tags.BlockTags.LEAVES)) leaves++;
		}
		Occupant.LOGGER.info("[gametest] blight over 64 chunks: {} blocks changed, {} leaves", changed, leaves);
		helper.assertTrue(changed > 0, "Something in 64 chunks should have gone a little wrong");
		helper.assertTrue(leaves == 0, "Dead trees have no leaves");
		helper.succeed();
	}

	/** The log keeps pace with the story: a few pages early, the last camp not before the third act. */
	@GameTest
	public void theLogKeepsPaceWithTheStory(GameTestHelper helper) {
		HauntData d = new HauntData();
		d.setAct(1);
		d.playTicks = 20 * 600;
		helper.assertTrue(com.wolfsmask.occupant.world.SurvivorLog.ready(d, 1.0), "The first page can be found in the first act");
		d.logsFound = 1;
		d.lastPageAt = 590;
		helper.assertTrue(!com.wolfsmask.occupant.world.SurvivorLog.ready(d, 1.0), "Not two pages within a few seconds");
		d.lastPageAt = 100;
		d.logsFound = 3;
		helper.assertTrue(!com.wolfsmask.occupant.world.SurvivorLog.ready(d, 1.0), "No more than three pages in the first act");
		d.logsFound = com.wolfsmask.occupant.world.SurvivorLog.CAMP_PAGE - 1;
		d.setAct(2);
		helper.assertTrue(!com.wolfsmask.occupant.world.SurvivorLog.ready(d, 1.0), "Not the last camp's page in the second act");
		d.setAct(3);
		d.playTicks = 20 * 6000;
		helper.assertTrue(com.wolfsmask.occupant.world.SurvivorLog.ready(d, 1.0), "The last camp's page in the third act");
		helper.succeed();
	}

	/**
	 * It does not let them die like that: a fatal fall is caught, and a monster's killing blow is
	 * stopped and the monster taken, but not twice within ten minutes.
	 */
	@GameTest(maxTicks = 40)
	public void itDoesNotLetThemDie(GameTestHelper helper) {
		Director director = Director.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// A test player is creative whatever it is told; as with the other tests, haunt creative.
		OccupantConfig cfg = OccupantConfig.get();
		boolean creativeBefore = cfg.hauntCreative;
		cfg.hauntCreative = true;
		HauntData data = director.data(player);
		data.introduced = true;
		data.setAct(2);
		player.setHealth(1.0f);
		boolean fall = com.wolfsmask.occupant.director.Mercy.allowDeath(player, player.damageSources().fall());
		Occupant.LOGGER.info("[gametest] mercy: fall allowed {}, health {}, creative {}, introduced {}", fall, player.getHealth(),
				player.isCreative(), director.haunt(player).data.introduced);
		helper.assertTrue(!fall && player.getHealth() > 1.0f, "A fatal fall should be caught (" + fall + ", " + player.getHealth() + ")");

		// By the game's own command: the zombie's entity type is not reachable by name on every version.
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
				String.format(java.util.Locale.ROOT, "summon minecraft:zombie %.1f %.1f %.1f {Tags:[\"occupant_mercy\"],PersistenceRequired:1b}",
						player.getX() + 2, player.getY(), player.getZ()));
		net.minecraft.world.entity.Mob zombie = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				player.getBoundingBox().inflate(6.0), m -> com.wolfsmask.occupant.util.Kinds.is(m, "zombie")).stream().findFirst().orElse(null);
		helper.assertTrue(zombie != null, "Could not summon a zombie");
		player.setHealth(1.0f);
		boolean first = com.wolfsmask.occupant.director.Mercy.allowDeath(player, player.damageSources().mobAttack(zombie));
		Occupant.LOGGER.info("[gametest] mercy: zombie allowed {}, held {}", first, zombie.isNoAi());
		helper.assertTrue(!first && zombie.isNoAi(), "A zombie's killing blow should be stopped and the zombie taken");
		director.stopCurrent(player);
		helper.assertTrue(!zombie.isNoAi(), "Whatever it did not finish with should be let go again");
		boolean again = com.wolfsmask.occupant.director.Mercy.allowDeath(player, player.damageSources().mobAttack(zombie));
		helper.assertTrue(again, "Not saved from monsters twice in ten minutes");
		zombie.discard();
		cfg.hauntCreative = creativeBefore;
		helper.succeed();
	}

	/**
	 * The rescue, run to its end: the monster is lifted off the ground, and only then dies, and
	 * then it is gone.
	 */
	@GameTest(maxTicks = 160)
	public void theRescueLiftsThenKills(GameTestHelper helper) {
		Director director = Director.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		OccupantConfig cfg = OccupantConfig.get();
		boolean creativeBefore = cfg.hauntCreative;
		cfg.hauntCreative = true;
		HauntData data = director.data(player);
		data.introduced = true;
		data.setAct(2);
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
				String.format(java.util.Locale.ROOT, "summon minecraft:zombie %.1f %.1f %.1f {PersistenceRequired:1b}",
						player.getX() + 2, player.getY(), player.getZ()));
		net.minecraft.world.entity.Mob zombie = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				player.getBoundingBox().inflate(6.0), m -> com.wolfsmask.occupant.util.Kinds.is(m, "zombie")).stream().findFirst().orElse(null);
		helper.assertTrue(zombie != null, "Could not summon a zombie");
		double ground = zombie.getY();
		player.setHealth(1.0f);
		helper.assertTrue(!com.wolfsmask.occupant.director.Mercy.allowDeath(player, player.damageSources().mobAttack(zombie)),
				"A zombie's killing blow should be stopped");
		helper.runAfterDelay(22, () -> {
			Occupant.LOGGER.info("[gametest] rescue: zombie {} above the ground, alive {}", zombie.getY() - ground, zombie.isAlive());
			helper.assertTrue(zombie.isAlive() && zombie.getY() > ground + 0.6, "The zombie should be held up, still alive, before it dies");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombie.isAlive(), "The zombie should die once it has been lifted");
			helper.assertTrue(level.getEntitiesOfClass(OccupantEntity.class, player.getBoundingBox().inflate(16.0)).isEmpty(),
					"Then it should be gone");
			cfg.hauntCreative = creativeBefore;
		});
	}

	/** What it builds while they play (its lair, the last camp) is never put on anything they made. */
	@GameTest
	public void itNeverBuildsOnTheirThings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		boolean before = com.wolfsmask.occupant.world.House.looksBuiltForTest(level, at, 2);
		helper.setBlock(new BlockPos(3, 2, 2), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
		boolean after = com.wolfsmask.occupant.world.House.looksBuiltForTest(level, at, 2);
		helper.assertTrue(!before, "Bare ground should not look built");
		helper.assertTrue(after, "Planks someone put down should");
		helper.succeed();
	}

	/** What comes after an event is always an event there is: a misspelt one would never come. */
	@GameTest
	public void followUpsAreRealEvents(GameTestHelper helper) {
		List<String> unknown = new ArrayList<>();
		for (String id : com.wolfsmask.occupant.director.FollowUps.named()) if (Events.byId(id) == null) unknown.add(id);
		helper.assertTrue(unknown.isEmpty(), "Follow-ups that are not events: " + unknown);
		helper.succeed();
	}

	/** Every one of its advancements loads, in whichever layout this version reads. */
	@GameTest
	public void theAdvancementsLoad(GameTestHelper helper) {
		int ours = 0;
		for (Object a : helper.getLevel().getServer().getAdvancements().getAllAdvancements()) {
			if (String.valueOf(a).contains("occupant:")) ours++;
		}
		Occupant.LOGGER.info("[gametest] advancements of ours loaded: {}", ours);
		helper.assertTrue(ours >= 19, "All 19 advancements should load, but " + ours + " did");
		helper.succeed();
	}

	/** The fog comes in as the story goes on: eight chunks or so at first, six or so by the end. */
	@GameTest
	public void fogThickensWithTheStory(GameTestHelper helper) {
		Director director = Director.get();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		HauntData data = director.data(player);
		data.setAct(1);
		float first = Fog.endFor(player, director.haunt(player));
		data.setAct(HauntData.MAX_ACT);
		float last = Fog.endFor(player, director.haunt(player));
		Occupant.LOGGER.info("[gametest] fog: first act {}, last act {}", first, last);
		helper.assertTrue(first >= 66 && first <= 80, "About eighty blocks of fog at first, not " + first);
		helper.assertTrue(last >= 36 && last <= first - 24, "About fifty by the end, not " + last);
		helper.assertTrue(FogLine.start(last) < FogLine.edgeNear(last) && FogLine.edgeFar(last) < last && FogLine.edgeNear(last) >= 14,
				"The fog begins near, and the band it stands in is inside it, short of where it is thick");
		helper.succeed();
	}

	/**
	 * In fog, the figure far off stands just this side of where the fog begins: the furthest thing
	 * that can be seen, seen whole, and never in it. A ridge is built right across the fog line,
	 * high in the air, so there is somewhere for it to stand.
	 */
	@GameTest(maxTicks = 100)
	public void distantStandsAtTheEdgeOfTheFog(GameTestHelper helper) {
		Director director = Director.get();
		ServerLevel level = helper.getLevel();
		OccupantConfig cfg = OccupantConfig.get();
		int chunksBefore = cfg.fogChunks;
		cfg.fogChunks = 6;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Something far off is only put where the server still runs the world: this server's own
		// setting may be too short for the fog's edge to be inside it.
		net.minecraft.server.players.PlayerList list = level.getServer().getPlayerList();
		int simulationBefore = list.getSimulationDistance();
		if (simulationBefore < 8) list.setSimulationDistance(10);
		try {
			BlockPos centre = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 900).atY(200);
			director.data(player).setAct(1);
			float end = Fog.endFor(player, director.haunt(player));
			double start = FogLine.edgeFar(end);
			helper.assertTrue(end >= 48 && end <= 60, "Sixty blocks of fog in the first act, not " + end);
			int r = (int) start + 10;
			net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r) continue;
					int top = d >= FogLine.edgeNear(end) - 2 && d <= start + 3 ? 5 : 0;
					for (int y = 0; y <= top; y++) {
						level.setBlock(centre.offset(dx, y, dz), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
					}
				}
			}
			player.snapTo(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 0.0f, 0.0f);
			player.setYHeadRot(0.0f);
			Director.TriggerResult result = director.trigger(player, DistantEvent.ID, true);
			List<OccupantEntity> found = level.getEntitiesOfClass(OccupantEntity.class, new AABB(centre).inflate(r + 8),
					e -> e.isHaunting(player));
			helper.assertTrue(result == Director.TriggerResult.STARTED, "It should find the ridge: " + result
					+ " (simulation distance " + simulationBefore + ", fog begins at " + start + ")");
			helper.assertTrue(!found.isEmpty(), "It should be standing somewhere");
			OccupantEntity e = found.get(0);
			double dist = Math.hypot(e.getX() - player.getX(), e.getZ() - player.getZ());
			Occupant.LOGGER.info("[gametest] fog thick at {}, begins at {}; it stands {} away", end, start, dist);
			helper.assertTrue(dist >= FogLine.edgeNear(end) - 1.0 && dist <= start + 1.0,
					"It should stand in the fog where it can just be made out (" + FogLine.edgeNear(end) + " to " + start + "), not " + dist);
			helper.succeed();
		} finally {
			director.stopCurrent(player);
			cfg.fogChunks = chunksBefore;
			if (list.getSimulationDistance() != simulationBefore) list.setSimulationDistance(simulationBefore);
		}
	}

	/**
	 * Every kind of place builds, has something left in it, and the first time one of its
	 * containers is opened, the next page of the survivor's log is in there.
	 */
	@GameTest(maxTicks = 60)
	public void placesHoldLootAndTheLog(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-640, 0, 0);
		String[] kinds = {"ruin", "camp", "graves", "cottage", "watchtower", "chapel", "radio", "lighthouse", "lair"};
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
			BlockPos base = kinds[i].equals("lair") ? lairSite(level, at) : new BlockPos(at.getX(), top - 1, at.getZ());
			House.buildPlaceForTest(kinds[i], level, base, level.getRandom());
			BlockPos box = null;
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -28, -8), base.offset(8, 22, 8))) {
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

	/**
	 * Every kind of place, built six different ways (a seed each: size, shape, wood, what has
	 * fallen in): however it comes out, its chest or barrel is still there to be found.
	 */
	@GameTest(maxTicks = 100)
	public void everyWayAPlaceIsBuiltKeepsItsThings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String[] kinds = {"ruin", "camp", "graves", "cottage", "watchtower", "chapel", "radio", "lighthouse"};
		int ways = 6;
		int apart = 28;
		BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-1400, 0, -1400);
		java.util.Set<Long> chunks = new java.util.HashSet<>();
		for (int i = 0; i < kinds.length; i++) {
			for (int k = 0; k < ways; k++) {
				BlockPos at = start.offset(i * apart, 0, k * apart);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int cx = (at.getX() >> 4) + dx;
						int cz = (at.getZ() >> 4) + dz;
						if (chunks.add(((long) cx << 32) | (cz & 0xFFFFFFFFL))) level.setChunkForced(cx, cz, true);
					}
				}
			}
		}
		List<String> missing = new ArrayList<>();
		try {
			for (int i = 0; i < kinds.length; i++) {
				for (int k = 0; k < ways; k++) {
					BlockPos at = start.offset(i * apart, 0, k * apart);
					int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
					BlockPos base = new BlockPos(at.getX(), top - 1, at.getZ());
					House.buildPlaceForTest(kinds[i], level, base, net.minecraft.util.RandomSource.create(1000L * i + k));
					boolean found = false;
					for (BlockPos p : BlockPos.betweenClosed(base.offset(-9, -4, -9), base.offset(9, 22, 9))) {
						if (level.getBlockEntity(p) instanceof net.minecraft.world.Container && com.wolfsmask.occupant.world.Loot.unopened(p)) {
							found = true;
							break;
						}
					}
					if (!found) missing.add(kinds[i] + " #" + k);
				}
			}
		} finally {
			for (long c : chunks) level.setChunkForced((int) (c >> 32), (int) c, false);
		}
		Occupant.LOGGER.info("[gametest] ways of building a place that lost their things: {}", missing);
		helper.assertTrue(missing.isEmpty(), "Every way of building a place should keep its chest or barrel: missing " + missing);
		helper.succeed();
	}

	@GameTest(maxTicks = 40 + TICKS_PER_EVENT * 45)
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
