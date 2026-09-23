package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the three places the story mostly happens in (an open field at night, a cave, and a
 * house) and checks that each event really finds its place there. Events are allowed to do
 * nothing when a place is wrong; this proves they DO happen when the place is right.
 */
public final class ArenaGameTests {
	private static final int LIMIT = 12000;
	private static final int TRIES = 3;

	@GameTest(maxTicks = LIMIT)
	public void eventsFindTheirPlace(GameTestHelper helper) {
		Arena arena = new Arena(helper);
		helper.onEachTick(arena::step);
	}

	private static final class Arena {
		private final GameTestHelper helper;
		private final ServerLevel world;
		private final BlockPos field;
		private final BlockPos cave;
		private final BlockPos house;
		/** Forced chunks, as {x, z} pairs. */
		private final List<int[]> chunks = new ArrayList<>();
		private final List<String> failures = new ArrayList<>();

		private int tick;
		private int phase;
		private int builtAt;
		private int errorsBefore;
		private Director director;
		private ServerPlayer player;

		Arena(GameTestHelper helper) {
			this.helper = helper;
			this.world = helper.getLevel();
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			this.field = new BlockPos(base.getX() + 400, 120, base.getZ());
			this.cave = new BlockPos(base.getX() + 400, 60, base.getZ() + 120);
			this.house = new BlockPos(base.getX() + 400, 120, base.getZ() + 240);
		}

		void step() {
			tick++;
			if (phase == 0) {
				setUp();
				phase = 1;
			} else if (phase == 1) {
				if (chunks.stream().allMatch(c -> world.getChunkSource().hasChunk(c[0], c[1]))) {
					build();
					builtAt = tick;
					phase = 2;
				} else if (tick > LIMIT / 2) {
					finish("chunks never loaded");
				}
			} else if (phase == 2) {
				boolean caveDark = world.getBrightness(LightLayer.SKY, cave) == 0;
				boolean fieldLit = world.getBrightness(LightLayer.SKY, field.above()) == 15;
				if ((caveDark && fieldLit && tick - builtAt > 100) || tick - builtAt > 3000) {
					if (!caveDark) failures.add("cave light never settled");
					if (!world.isDarkOutside()) failures.add("it never became night");
					runChecks();
					finish(null);
				}
			}
		}

		private void setUp() {
			director = Director.get();
			helper.assertTrue(director != null, "Director should be running");
			OccupantConfig.get().hauntCreative = true;
			OccupantGameTests.makeNight(world);

			player = helper.makeMockServerPlayerInLevel();
			HauntData data = director.data(player);
			data.setAct(HauntData.MAX_ACT);
			errorsBefore = director.totalErrors();

			forceChunks(field, 2);
			forceChunks(cave, 2);
			forceChunks(house, 1);
		}

		private void forceChunks(BlockPos center, int radius) {
			int cx = SectionPos.blockToSectionCoord(center.getX());
			int cz = SectionPos.blockToSectionCoord(center.getZ());
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					world.setChunkForced(cx + x, cz + z, true);
					chunks.add(new int[]{cx + x, cz + z});
				}
			}
		}

		// ------------------------------------------------------------------ building

		private void build() {
			// Open field: a big grass platform high in the air, nothing around it.
			fill(field.offset(-30, -1, -30), field.offset(30, -1, 30), Blocks.GRASS_BLOCK.defaultBlockState());

			// Cave: a solid stone mass with a long, dark, 5-wide corridor through the middle.
			fill(cave.offset(-20, -6, -20), cave.offset(20, 8, 20), Blocks.STONE.defaultBlockState());
			fill(cave.offset(-18, 0, -2), cave.offset(18, 2, 2), Blocks.AIR.defaultBlockState());

			// House: a plank box with a door behind the player and a chest in the corner.
			fill(house.offset(-5, -1, -5), house.offset(5, 4, 5), Blocks.OAK_PLANKS.defaultBlockState());
			fill(house.offset(-4, 0, -4), house.offset(4, 3, 4), Blocks.AIR.defaultBlockState());
			BlockPos door = house.offset(0, 0, -5);
			BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
					.setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
			world.setBlock(door, lower, Block.UPDATE_ALL);
			world.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
			world.setBlock(house.offset(3, 0, -3), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		}

		private void fill(BlockPos from, BlockPos to, BlockState state) {
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				world.setBlock(p, state, Block.UPDATE_CLIENTS);
			}
		}

		// ------------------------------------------------------------------ checks

		private void runChecks() {
			// Field, facing south (+Z), at night.
			place(field, 0.0f);
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(
					LevelData.RespawnData.of(world.dimension(), field.offset(0, 0, -22), 0.0f, 0.0f), true), false);
			expect("field", "watcher", "stalker", "hunt", "behind_you", "footsteps", "sign", "intruder");

			// Cave, facing east (+X) down the corridor.
			place(cave, -90.0f);
			diagnoseCave();
			expect("cave", "cave_noise", "distant_mining", "watcher", "marker_torch", "tunnel");
			world.setBlock(cave.offset(-11, 0, 0), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
			world.setBlock(cave.offset(-14, 0, 1), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
			expect("cave", "torch_gone");

			// House, facing away from the door.
			place(house, 0.0f);
			expect("house", "knock", "chest", "door");
		}

		/** Logs what the cave looks like to the spot checks, so a failure here is easy to understand. */
		private void diagnoseCave() {
			BlockPos feet = player.blockPosition();
			int stand = 0;
			int dark = 0;
			int seen = 0;
			for (int x = 8; x <= 18; x++) {
				for (int z = -2; z <= 2; z++) {
					BlockPos p = cave.offset(x, 0, z);
					if (!Spots.canStand(world, p)) continue;
					stand++;
					if (Spots.light(world, p.above()) <= 7) dark++;
					Vec3 base = Vec3.atBottomCenterOf(p);
					if (Sight.hasLineOfSight(player, base.add(0, 1.6, 0)) && Sight.hasLineOfSight(player, base.add(0, 0.9, 0))) seen++;
				}
			}
			Occupant.LOGGER.info("[gametest] cave: yaw={} feet={} underground={} skyVisible={} skyLight={} topY={} light={} look={} | ahead: standable={} dark={} visible={}",
					player.getYRot(), feet, Spots.isUnderground(world, feet), world.canSeeSky(feet), world.getBrightness(LightLayer.SKY, feet),
					world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()), Spots.light(world, feet.above()),
					Sight.flatLook(player), stand, dark, seen);
		}

		private void place(BlockPos pos, float yaw) {
			director.stopCurrent(player);
			player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
			// Set every rotation field directly; mock players do not always take it from the call above.
			player.setYRot(yaw);
			player.setXRot(0.0f);
			player.setYHeadRot(yaw);
			player.setYBodyRot(yaw);
			player.yRotO = yaw;
			player.xRotO = 0.0f;
		}

		private void expect(String where, String... events) {
			for (String id : events) {
				boolean started = false;
				for (int i = 0; i < TRIES && !started; i++) {
					started = director.trigger(player, id, true) == Director.TriggerResult.STARTED;
					director.stopCurrent(player);
				}
				if (!started) failures.add(id + " (" + where + ")");
			}
		}

		private void finish(String fatal) {
			phase = 3;
			director.stopCurrent(player);
			for (int[] c : chunks) world.setChunkForced(c[0], c[1], false);

			if (fatal != null) failures.add(fatal);
			Occupant.LOGGER.info("[gametest] arena failures: {}", failures);
			helper.assertTrue(director.totalErrors() == errorsBefore, "No event may throw in the arena (see log)");
			helper.assertTrue(failures.isEmpty(), "Events that could not find their place: " + failures);

			List<OccupantEntity> leftovers = new ArrayList<>();
			for (BlockPos p : List.of(field, cave, house)) {
				leftovers.addAll(world.getEntitiesOfClass(OccupantEntity.class, new AABB(p).inflate(64), e -> !e.isRemoved()));
			}
			helper.assertTrue(leftovers.isEmpty(), "No Occupant may be left behind");
			helper.succeed();
		}
	}
}
