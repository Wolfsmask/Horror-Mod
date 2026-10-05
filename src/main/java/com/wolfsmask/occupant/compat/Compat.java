package com.wolfsmask.occupant.compat;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * The few places where Minecraft versions disagree about something small. Each supported version
 * has its own copy of this class (versions/<group>/main/...), so the rest of the mod never changes.
 * This copy is for 26.1 and 26.2.
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

	/** Where the server-to-client payloads are registered. */
	public static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverToClient() {
		return PayloadTypeRegistry.clientboundPlay();
	}

	/** The time of day in the overworld, in ticks since the world began. */
	public static long dayTime(Level level) {
		return level.getOverworldClockTime();
	}

	/** The type of a piece of data saved with the world, under {@code data/occupant/<name>}. */
	public static <T extends SavedData> SavedDataType<T> savedData(String name, Supplier<T> fresh, Codec<T> codec, DataFixTypes fix) {
		return new SavedDataType<>(com.wolfsmask.occupant.Occupant.id(name), fresh, codec, fix);
	}

	/** Keep a mob's paths out of water, lava, fire and anything else that hurts. */
	public static void avoidHazards(Mob mob) {
		mob.setPathfindingMalus(PathType.WATER, -1.0f);
		mob.setPathfindingMalus(PathType.LAVA, -1.0f);
		mob.setPathfindingMalus(PathType.FIRE, -1.0f);
		mob.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0f);
		mob.setPathfindingMalus(PathType.DAMAGING, -1.0f);
	}
}
