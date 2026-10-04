package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Two things on the hotbar have changed places. Not the one in your hand: you would notice that
 * straight away. Something you reach for later, and it is not where you put it, and you are sure
 * you did not move it. Nothing is taken and nothing is lost; it is only ever moved.
 */
public final class RearrangedEvent extends HorrorEvent {
	public RearrangedEvent() {
		super("rearranged", Tier.AMBIENT, 2, 3, 40);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		Inventory inv = p.getInventory();
		ItemStack held = p.getMainHandItem();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s != held) slots.add(i);
		}
		if (slots.size() < 2) return null;
		int a = slots.remove(ctx.random.nextInt(slots.size()));
		int b = slots.get(ctx.random.nextInt(slots.size()));
		return new Timeline().at(0, player -> {
			Inventory i = player.getInventory();
			ItemStack first = i.getItem(a);
			ItemStack second = i.getItem(b);
			if (first.isEmpty() || second.isEmpty() || first == player.getMainHandItem() || second == player.getMainHandItem()) return;
			i.setItem(a, second);
			i.setItem(b, first);
		});
	}
}
