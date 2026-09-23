package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stores every player's {@link HauntData} in {@code <world>/data/occupant.dat}. */
public final class OccupantSaveData extends PersistentState {
	private static final String ID = Occupant.MOD_ID;

	public static final PersistentState.Type<OccupantSaveData> TYPE =
			new PersistentState.Type<>(OccupantSaveData::new, OccupantSaveData::fromNbt, null);

	private final Map<UUID, HauntData> players = new HashMap<>();

	public static OccupantSaveData get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	public HauntData forPlayer(UUID uuid) {
		return players.computeIfAbsent(uuid, u -> {
			markDirty();
			return new HauntData();
		});
	}

	public void reset(UUID uuid) {
		players.put(uuid, new HauntData());
		markDirty();
	}

	private static OccupantSaveData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		OccupantSaveData data = new OccupantSaveData();
		NbtCompound all = nbt.getCompound("players");
		for (String key : all.getKeys()) {
			try {
				data.players.put(UUID.fromString(key), HauntData.fromNbt(all.getCompound(key)));
			} catch (Exception e) {
				// One corrupt entry must never cost anyone else their progress.
				Occupant.LOGGER.warn("Skipping unreadable Occupant data for {}", key, e);
			}
		}
		return data;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtCompound all = new NbtCompound();
		players.forEach((uuid, data) -> all.put(uuid.toString(), data.toNbt()));
		nbt.put("players", all);
		return nbt;
	}
}
