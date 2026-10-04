package com.wolfsmask.occupant.compat;

import com.google.common.collect.Iterables;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

/** The few places where Minecraft versions disagree about something small. This copy is for 26.3 and later. */
public final class Compat {
	private Compat() {
	}

	/** Every position within the given distance of {@code center} on each axis, nearest first. */
	public static Iterable<BlockPos> withinManhattan(BlockPos center, int rx, int ry, int rz) {
		int r = Math.max(rx, Math.max(ry, rz));
		return Iterables.filter(BlockPos.withinManhattan(center, r), p -> Math.abs(p.getX() - center.getX()) <= rx
				&& Math.abs(p.getY() - center.getY()) <= ry && Math.abs(p.getZ() - center.getZ()) <= rz);
	}

	/** Write up to four lines on the front of a sign. */
	public static void writeSign(SignBlockEntity sign, String[] lines) {
		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) messages.add(Component.literal(i < lines.length ? lines[i] : ""));
		sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), SignTextSlot.FRONT);
	}
}
