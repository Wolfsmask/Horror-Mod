package com.wolfsmask.occupant.compat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * The few places where Minecraft versions disagree about something small. Each supported version
 * has its own copy of this class (versions/<group>/main/...), so the rest of the mod never changes.
 * This copy is for 1.20.1.
 */
public final class Compat {
	private Compat() {
	}

	/** Every position within the given distance of {@code center} on each axis, nearest first. */
	public static Iterable<BlockPos> withinManhattan(BlockPos center, int rx, int ry, int rz) {
		return BlockPos.withinManhattan(center, rx, ry, rz);
	}

	/** Write up to four lines on the front of a sign. */
	public static void writeSign(SignBlockEntity sign, String[] lines) {
		SignText text = new SignText();
		for (int i = 0; i < 4 && i < lines.length; i++) {
			text = text.setMessage(i, Component.literal(lines[i]));
		}
		sign.setText(text, true);
	}


	/** The time of day in the overworld, in ticks since the world began. */
	public static long dayTime(Level level) {
		return level.getDayTime();
	}


	/** Keep a mob's paths out of water, lava, fire and anything else that hurts. */
	public static void avoidHazards(Mob mob) {
		mob.setPathfindingMalus(BlockPathTypes.WATER, -1.0f);
		mob.setPathfindingMalus(BlockPathTypes.LAVA, -1.0f);
		mob.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0f);
		mob.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0f);
		mob.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, -1.0f);
	}

	/** The level a player is in, as a server level. */
	public static ServerLevel level(ServerPlayer player) {
		return player.serverLevel();
	}

	/** The player's bed or respawn anchor, if it is in the world they are standing in. */
	@Nullable
	public static BlockPos respawnPos(ServerPlayer player) {
		BlockPos pos = player.getRespawnPosition();
		return pos != null && player.getRespawnDimension() == player.level().dimension() ? pos : null;
	}

	/** Set where the player respawns, in the world they are in, without telling them. */
	public static void setRespawn(ServerPlayer player, BlockPos pos) {
		player.setRespawnPosition(player.level().dimension(), pos, 0.0f, true, false);
	}

	/** Where players first appear in this world. */
	public static BlockPos spawnPos(ServerLevel level) {
		return level.getSharedSpawnPos();
	}
}
