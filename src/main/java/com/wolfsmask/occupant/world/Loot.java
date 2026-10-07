package com.wolfsmask.occupant.world;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What was left behind in the places the mod builds, and the survivor's log.
 * <p>
 * Every chest and barrel in them is filled with a few ordinary things when it is built, the
 * leftovers of somebody's life. The first time any of them is opened, a page of the log is in it
 * as well: the next page for whoever opened it, so the story is always read in order, however
 * the places are found. A small file in the world folder remembers which have not been opened.
 */
public final class Loot {
	/** What sort of place it is, which decides what is in it. */
	public enum Kind { HOME, RUIN, CAMP, GRAVE }

	private static final Set<BlockPos> UNOPENED = ConcurrentHashMap.newKeySet();
	@Nullable
	private static volatile Path record;
	/** Containers have been built since the file was last written. */
	private static volatile boolean dirty;

	private Loot() {
	}

	public static void open(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("occupant_containers.txt");
		record = file;
		UNOPENED.clear();
		if (!Files.exists(file)) return;
		try {
			for (String line : Files.readAllLines(file)) {
				String[] p = line.trim().split("\\s+");
				if (p.length == 3) UNOPENED.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			}
		} catch (IOException | RuntimeException e) {
			Occupant.LOGGER.warn("Could not read which containers are still unopened", e);
		}
	}

	public static void close() {
		save();
		record = null;
		UNOPENED.clear();
	}

	/** Every second: writes down any containers built since the last time. */
	static void flush() {
		if (dirty) save();
	}

	private static synchronized void save() {
		dirty = false;
		Path file = record;
		if (file == null) return;
		StringBuilder out = new StringBuilder();
		for (BlockPos p : UNOPENED) out.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append('\n');
		try {
			Files.writeString(file, out.toString());
		} catch (IOException e) {
			Occupant.LOGGER.warn("Could not record which containers are still unopened", e);
		}
	}

	/** Fills a container just built at {@code pos}, and remembers it for a page of the log. */
	static void fill(LevelAccessor level, BlockPos pos, RandomSource random, Kind kind) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof Container box)) return;
		List<Item> pool = pool(kind);
		int stacks = 2 + random.nextInt(4);
		int size = box.getContainerSize();
		for (int n = 0; n < stacks; n++) {
			Item item = pool.get(random.nextInt(pool.size()));
			int max = Math.min(new ItemStack(item).getMaxStackSize(), maxCount(item));
			ItemStack stack = new ItemStack(item, 1 + random.nextInt(Math.max(1, max)));
			box.setItem(random.nextInt(size), stack);
		}
		// Now and then, something better, in the places people did not come back for.
		if (random.nextFloat() < (kind == Kind.RUIN ? 0.35f : 0.12f)) {
			Item[] rare = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD, Items.COMPASS, Items.CLOCK, Items.SPYGLASS, Items.NAME_TAG};
			box.setItem(random.nextInt(size), new ItemStack(rare[random.nextInt(rare.length)], 1));
		}
		UNOPENED.add(pos.immutable());
		dirty = true;                       // written within the second, not from world generation itself
	}

	/**
	 * A player is opening the container at {@code pos}. If nobody has before, the next page of the
	 * log goes in first. Called as they open it, so it is there when it opens.
	 */
	public static void opening(ServerPlayer player, BlockPos pos, Container box, int page) {
		opening(player, pos, box, SurvivorLog.page(page, player.getName().getString()));
	}

	/** The first time a container is opened, {@code log} is put in it. */
	public static void opening(ServerPlayer player, BlockPos pos, Container box, ItemStack log) {
		if (!UNOPENED.remove(pos)) return;
		save();
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, log);
				return;
			}
		}
		box.setItem(box.getContainerSize() - 1, log);       // full: the page matters more
	}

	/** Is this one of the containers nobody has opened yet? */
	public static boolean unopened(BlockPos pos) {
		return UNOPENED.contains(pos);
	}

	private static int maxCount(Item item) {
		return item == Items.TORCH || item == Items.ARROW || item == Items.BONE || item == Items.ROTTEN_FLESH ? 12 : 5;
	}

	private static List<Item> pool(Kind kind) {
		List<Item> items = new ArrayList<>();
		switch (kind) {
			case HOME -> items.addAll(List.of(Items.BREAD, Items.APPLE, Items.POTATO, Items.CARROT, Items.WHEAT, Items.CANDLE,
					Items.PAPER, Items.BOOK, Items.STRING, Items.COAL, Items.TORCH, Items.GLASS_BOTTLE, Items.BOWL, Items.STICK,
					Items.LEATHER, Items.FLINT_AND_STEEL, Items.SHEARS));
			case RUIN -> items.addAll(List.of(Items.ARROW, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.BONE, Items.ROTTEN_FLESH,
					Items.STRING, Items.GUNPOWDER, Items.COAL, Items.TORCH, Items.FLINT, Items.LEATHER, Items.SPIDER_EYE,
					Items.STONE_SWORD, Items.SHIELD, Items.BOW));
			case CAMP -> items.addAll(List.of(Items.BREAD, Items.COOKED_COD, Items.APPLE, Items.TORCH, Items.COAL, Items.STRING,
					Items.LEATHER, Items.FLINT_AND_STEEL, Items.STICK, Items.ARROW, Items.CAMPFIRE, Items.FISHING_ROD, Items.PAPER));
			case GRAVE -> items.addAll(List.of(Items.BONE, Items.BONE_MEAL, Items.ROTTEN_FLESH, Items.CANDLE, Items.PAPER,
					Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.STRING, Items.POPPY, Items.WITHER_ROSE));
		}
		return items;
	}
}
