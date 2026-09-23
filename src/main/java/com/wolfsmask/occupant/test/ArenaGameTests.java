package com.wolfsmask.occupant.test;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.entity.OccupantEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the three places the story mostly happens in (an open field at night, a cave, and a
 * house) and checks that each event really finds its place there. Events are allowed to do
 * nothing when a place is wrong; this proves they DO happen when the place is right.
 */
public final class ArenaGameTests implements FabricGameTest {
	private static final int LIMIT = 12000;
	private static final int TRIES = 3;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "arena", tickLimit = LIMIT)
	public void eventsFindTheirPlace(TestContext ctx) {
		Arena arena = new Arena(ctx);
		for (int t = 1; t < LIMIT - 10; t++) {
			int tick = t;
			ctx.runAtTick(t, () -> arena.step(tick));
		}
	}

	private static final class Arena {
		private final TestContext ctx;
		private final ServerWorld world;
		private final BlockPos field;
		private final BlockPos cave;
		private final BlockPos house;
		private final List<ChunkPos> chunks = new ArrayList<>();
		private final List<String> failures = new ArrayList<>();

		private int phase;
		private int builtAt;
		private boolean previousCreative;
		private int errorsBefore;
		private Director director;
		private ServerPlayerEntity player;

		Arena(TestContext ctx) {
			this.ctx = ctx;
			this.world = ctx.getWorld();
			BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN);
			this.field = new BlockPos(base.getX() + 400, 120, base.getZ());
			this.cave = new BlockPos(base.getX() + 400, 60, base.getZ() + 120);
			this.house = new BlockPos(base.getX() + 400, 120, base.getZ() + 240);
		}

		void step(int tick) {
			if (phase == 0) {
				setUp();
				phase = 1;
			} else if (phase == 1) {
				if (chunks.stream().allMatch(c -> world.getChunkManager().isChunkLoaded(c.x, c.z))) {
					build();
					builtAt = tick;
					phase = 2;
				} else if (tick > LIMIT / 2) {
					finish("chunks never loaded");
				}
			} else if (phase == 2) {
				boolean caveDark = world.getLightLevel(LightType.SKY, cave) == 0;
				boolean fieldLit = world.getLightLevel(LightType.SKY, field.up()) == 15;
				if ((caveDark && fieldLit && tick - builtAt > 100) || tick - builtAt > 3000) {
					if (!caveDark) failures.add("cave light never settled");
					runChecks();
					finish(null);
				}
			}
		}

		private void setUp() {
			director = Director.get();
			ctx.assertTrue(director != null, "Director should be running");
			OccupantConfig cfg = OccupantConfig.get();
			previousCreative = cfg.hauntCreative;
			cfg.hauntCreative = true;
			world.setTimeOfDay(18000);

			player = ctx.createMockCreativeServerPlayerInWorld();
			HauntData data = director.data(player);
			data.setAct(HauntData.MAX_ACT);
			errorsBefore = director.totalErrors();

			forceChunks(field, 2);
			forceChunks(cave, 2);
			forceChunks(house, 1);
		}

		private void forceChunks(BlockPos center, int radius) {
			ChunkPos c = new ChunkPos(center);
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					ChunkPos p = new ChunkPos(c.x + x, c.z + z);
					world.setChunkForced(p.x, p.z, true);
					chunks.add(p);
				}
			}
		}

		// ------------------------------------------------------------------ building

		private void build() {
			// Open field: a big grass platform high in the air, nothing around it.
			fill(field.add(-30, -1, -30), field.add(30, -1, 30), Blocks.GRASS_BLOCK.getDefaultState());

			// Cave: a solid stone mass with a long, dark, 5-wide corridor through the middle.
			fill(cave.add(-20, -6, -20), cave.add(20, 8, 20), Blocks.STONE.getDefaultState());
			fill(cave.add(-18, 0, -2), cave.add(18, 2, 2), Blocks.AIR.getDefaultState());

			// House: a plank box with a door behind the player and a chest in the corner.
			fill(house.add(-5, -1, -5), house.add(5, 4, 5), Blocks.OAK_PLANKS.getDefaultState());
			fill(house.add(-4, 0, -4), house.add(4, 3, 4), Blocks.AIR.getDefaultState());
			BlockPos door = house.add(0, 0, -5);
			BlockState lower = Blocks.OAK_DOOR.getDefaultState()
					.with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
			world.setBlockState(door, lower, Block.NOTIFY_ALL);
			world.setBlockState(door.up(), lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
			world.setBlockState(house.add(3, 0, -3), Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
		}

		private void fill(BlockPos from, BlockPos to, BlockState state) {
			for (BlockPos p : BlockPos.iterate(from, to)) {
				world.setBlockState(p, state, Block.NOTIFY_LISTENERS);
			}
		}

		// ------------------------------------------------------------------ checks

		private void runChecks() {
			// Field, facing south (+Z), at night.
			place(field, 0.0f);
			player.setSpawnPoint(world.getRegistryKey(), field.add(0, 0, -22), 0.0f, true, false);
			expect("field", "watcher", "stalker", "hunt", "behind_you", "footsteps", "sign", "intruder");

			// Cave, facing east (+X) down the corridor.
			place(cave, -90.0f);
			expect("cave", "cave_noise", "distant_mining", "watcher", "marker_torch", "tunnel");
			world.setBlockState(cave.add(-11, 0, 0), Blocks.TORCH.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(cave.add(-14, 0, 1), Blocks.TORCH.getDefaultState(), Block.NOTIFY_ALL);
			expect("cave", "torch_gone");

			// House, facing away from the door.
			place(house, 0.0f);
			expect("house", "door", "chest", "knock");
		}

		private void place(BlockPos pos, float yaw) {
			director.stopCurrent(player);
			player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
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
			OccupantConfig.get().hauntCreative = previousCreative;
			for (ChunkPos c : chunks) world.setChunkForced(c.x, c.z, false);

			if (fatal != null) failures.add(fatal);
			Occupant.LOGGER.info("[gametest] arena failures: {}", failures);
			ctx.assertTrue(director.totalErrors() == errorsBefore, "No event may throw in the arena (see log)");
			ctx.assertTrue(failures.isEmpty(), "Events that could not find their place: " + failures);

			List<OccupantEntity> leftovers = new ArrayList<>();
			for (BlockPos p : List.of(field, cave, house)) {
				leftovers.addAll(world.getEntitiesByClass(OccupantEntity.class, new Box(p).expand(64), e -> !e.isRemoved()));
			}
			ctx.assertTrue(leftovers.isEmpty(), "No Occupant may be left behind");
			ctx.complete();
		}
	}
}
