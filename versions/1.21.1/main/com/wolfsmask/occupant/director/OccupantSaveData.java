package com.wolfsmask.occupant.director;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores every player's {@link HauntData} with the world, in {@code data/occupant_haunts.dat}.
 * This copy is for 1.21.1, where saved data is written to NBT by hand.
 */
public final class OccupantSaveData extends SavedData {
	private static final Codec<Map<UUID, HauntData>> PLAYERS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, HauntData.CODEC);

	private static final SavedData.Factory<OccupantSaveData> FACTORY = new SavedData.Factory<>(
			OccupantSaveData::new, OccupantSaveData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, HauntData> players = new HashMap<>();

	public OccupantSaveData() {
	}

	private static OccupantSaveData load(CompoundTag tag, HolderLookup.Provider registries) {
		OccupantSaveData data = new OccupantSaveData();
		if (tag.contains("players")) {
			PLAYERS.parse(NbtOps.INSTANCE, tag.get("players")).result().ifPresent(data.players::putAll);
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		PLAYERS.encodeStart(NbtOps.INSTANCE, players).result().ifPresent(t -> tag.put("players", t));
		return tag;
	}

	public static OccupantSaveData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "occupant_haunts");
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
