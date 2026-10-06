package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.story.Achievements;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import com.wolfsmask.occupant.director.events.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * How the last night ends, which depends on how the story was lived. All of it happens in the
 * dark, while the screen is black and the line is up; the picture comes back somewhere else.
 * <ul>
 *   <li><b>It knows how to be you</b> (anything else): the fog lifts, and the story begins again,
 *   quieter. It is still here.</li>
 *   <li><b>Left behind</b> (found the survivor's last camp): it lets you go. You wake by their
 *   fire, lit again, with one more page; the fog is gone, and nothing follows you any more.</li>
 *   <li><b>So it came in</b> (hid indoors most of the story): you wake at home, by your bed, and
 *   every door in the house is open. The fog stays. It did not leave; it is inside, and it is
 *   closer than it has ever been.</li>
 * </ul>
 */
public final class LastNightEnding {
	public static final int LEARNED = 0;
	public static final int FOUND = 1;
	public static final int HID = 2;
	/** After the plain ending, a quarter of an hour with no fog at all. */
	private static final long LIFTED_FOR = 20L * 60 * 15;

	private LastNightEnding() {
	}

	/** Which ending this story has earned. */
	public static int which(HauntData d) {
		if (d.lastCamp == 2) return FOUND;
		if (d.insideSeconds > 2 * Math.max(120, d.outsideSeconds)) return HID;
		return LEARNED;
	}

	/** Called in the dark, a moment after the screen has gone black. */
	public static void play(ServerPlayer player, Haunt haunt, int which) {
		HauntData d = haunt.data;
		d.lastNight = true;
		d.ending = which;
		d.dread = 0f;
		haunt.releaseFog();
		Achievements.grant(player, Achievements.LAST_NIGHT);
		ServerLevel world = Compat.level(player);
		long now = world.getServer().getTickCount();
		switch (which) {
			case FOUND -> {
				Achievements.grant(player, Achievements.ENDING_FOUND);
				d.setAct(1);
				wakeAtTheCamp(player, world, d);
			}
			case HID -> {
				Achievements.grant(player, Achievements.ENDING_HID);
				// It did not leave. Closer than ever, and soon.
				d.setAct(HauntData.MAX_ACT - 1);
				d.addDread(40f);
				haunt.nextEventIn = 20 * 45;
				wakeAtHome(player, world);
			}
			default -> {
				Achievements.grant(player, Achievements.ENDING_LEARNED);
				d.setAct(2);
				haunt.liftFog(now + LIFTED_FOR);
			}
		}
	}

	/** By their fire, lit again, facing it; and their last words, in your pocket. */
	private static void wakeAtTheCamp(ServerPlayer player, ServerLevel world, HauntData d) {
		if (world != world.getServer().overworld() || d.lastCamp == 0) return;
		int cx = d.lastCampX, cz = d.lastCampZ;
		world.getChunk(cx >> 4, cz >> 4);                      // loaded: it is the middle of nowhere
		int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		BlockPos fire = null;
		for (BlockPos p : Compat.withinManhattan(new BlockPos(cx, top, cz), 6, 4, 6)) {
			BlockState s = world.getBlockState(p);
			if (s.is(Blocks.CAMPFIRE) && s.hasProperty(BlockStateProperties.LIT)) {
				world.setBlock(p, s.setValue(BlockStateProperties.LIT, true), 3);
				fire = p.immutable();
				break;
			}
		}
		Vec3 look = Vec3.atCenterOf(fire != null ? fire : new BlockPos(cx, top, cz));
		BlockPos feet = standNear(world, fire != null ? fire : new BlockPos(cx, top, cz), 3);
		if (feet != null) teleport(player, feet, look);
		ItemStack page = Compat.writtenBook("The Last Page", "?", List.of(
				"You found me.\n\nNobody was meant to. I left the pages for whoever came next, and I am sorry it was you.",
				"It doesn't want the ones who come looking. It wants the ones who stay home with the door shut and wait for it to go away.\n\nIt won't follow you now.",
				"Keep the fire going.\n\nIf you ever hear knocking, don't answer. That isn't me."));
		if (!player.getInventory().add(page)) player.drop(page, false);
	}

	/** Back at home, by the bed, and every door open. */
	private static void wakeAtHome(ServerPlayer player, ServerLevel world) {
		BlockPos bed = Compat.respawnPos(player);
		if (bed == null || world != world.getServer().overworld()) return;
		BlockPos feet = standNear(world, bed, 2);
		if (feet == null) return;
		teleport(player, feet, Vec3.atCenterOf(bed));
		int opened = 0;
		for (BlockPos p : Compat.withinManhattan(bed, 16, 4, 16)) {
			if (!Spots.isLoaded(world, p) || !WorldBlocks.isClosedWoodenDoor(world, p)) continue;
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock door) {
				door.setOpen(null, world, s, p.immutable(), true);
				if (++opened >= 12) break;
			}
		}
	}

	/** Somewhere to stand a step or two from {@code centre}, not on it. */
	@Nullable
	private static BlockPos standNear(ServerLevel world, BlockPos centre, int reach) {
		for (int r = 1; r <= reach + 1; r++) {
			for (int[] o : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
				BlockPos feet = Spots.groundNear(world, centre.getX() + o[0], centre.getY(), centre.getZ() + o[1], 3);
				if (feet != null) return feet;
			}
		}
		return null;
	}

	/** By the game's own command, which is the same on every version. */
	private static void teleport(ServerPlayer player, BlockPos feet, Vec3 lookAt) {
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = Sight.yawBetween(at, lookAt);
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f 10", player.getStringUUID(), at.x, at.y, at.z, yaw));
		} catch (RuntimeException e) {
			Occupant.LOGGER.warn("Could not move {} for the ending", player.getName().getString(), e);
		}
	}
}
