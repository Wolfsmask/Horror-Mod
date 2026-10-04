package com.wolfsmask.occupant.compat;

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
}
