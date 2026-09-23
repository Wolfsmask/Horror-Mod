package com.wolfsmask.occupant.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wolfsmask.occupant.Occupant;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stores every player's {@link HauntData} with the world, in {@code data/occupant/haunts.dat}. */
public final class OccupantSaveData extends SavedData {
	private static final Codec<OccupantSaveData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, HauntData.CODEC).optionalFieldOf("players", Map.of())
					.forGetter(d -> d.players)
	).apply(i, OccupantSaveData::new));

	public static final SavedDataType<OccupantSaveData> TYPE = new SavedDataType<>(
			Occupant.id("haunts"), OccupantSaveData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, HauntData> players = new HashMap<>();

	public OccupantSaveData() {
	}

	private OccupantSaveData(Map<UUID, HauntData> loaded) {
		this.players.putAll(loaded);
	}

	public static OccupantSaveData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public HauntData forPlayer(UUID uuid) {
		return players.computeIfAbsent(uuid, u -> {
			setDirty();
			return new HauntData();
		});
	}

	public void reset(UUID uuid) {
		players.put(uuid, new HauntData());
		setDirty();
	}
}
