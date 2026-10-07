package com.wolfsmask.occupant.story;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.HauntData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The other log. A miner's, found only deep underground, in chests nobody built for the story:
 * they read the survivor's pages up on the surface and thought the survivor had it wrong. They
 * had it wrong too. In order, one page now and then, never two from the same chest.
 */
public final class DeepLog {
	private static final List<String> PAGES = List.of(
			"Shift 1.\n\nTook the old shaft down past the last of the lamps. Somebody's been working this seam. Pick marks. No tools, no bodies.",
			"Shift 3.\n\nFound pages up top, in a cabin. Somebody keeping a diary about a tall thing at the treeline.\n\nPoor sod. There's nothing up there.\n\nIt's down here.",
			"Shift 4.\n\nIt doesn't come down here. That's what I told myself.\n\nThe torches I left this morning are all turned to face the wall.",
			"Shift 6.\n\nThe diary has it wrong. It isn't watching him.\n\nIt's watching whoever reads the diary.",
			"Shift 7.\n\nSomebody mining behind me, swing for swing with me. I stopped.\n\nIt did one more.",
			"Shift 9.\n\nI can't find the way up. The ladder has more rungs than I put in.",
			"Shift ?\n\nIf you found the other pages: they were left for you. So were these.\n\nSo was I.",
			"it is down here too");
	/** Below this, with no sky, a chest is deep enough. */
	private static final int DEEP = 40;
	/** Chests that have given a page already, while the server runs. */
	private static final Set<BlockPos> GIVEN = ConcurrentHashMap.newKeySet();

	private DeepLog() {
	}

	/** A player opens a container: if it is deep, and the time is right, the next page is in it. */
	public static void opening(ServerPlayer player, BlockPos pos, Container box) {
		Director director = Director.get();
		if (director == null || !OccupantConfig.get().enabled) return;
		// A chest or a barrel only: in a furnace or a brewing stand a page would sit in the wrong slot.
		if (!(box instanceof ChestBlockEntity) && !(box instanceof BarrelBlockEntity)) return;
		if (pos.getY() > DEEP || Compat.level(player).canSeeSky(pos.above()) || GIVEN.contains(pos)) return;
		HauntData d = director.data(player);
		int found = d.marks >>> 8;
		if (d.act < 2 || found >= PAGES.size() || player.getRandom().nextFloat() > 0.35f) return;
		int slot = -1;
		for (int i = 0; i < box.getContainerSize() && slot < 0; i++) if (box.getItem(i).isEmpty()) slot = i;
		if (slot < 0) return;
		GIVEN.add(pos.immutable());
		found++;
		d.marks = (d.marks & 0xFF) | (found << 8);
		ItemStack page = Compat.writtenBook("A miner's notes, page " + found, "unknown", List.of(PAGES.get(found - 1)));
		box.setItem(slot, page);
		director.markDirty();
	}

	public static void clear() {
		GIVEN.clear();
	}
}
