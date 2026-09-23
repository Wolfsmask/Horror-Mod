package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The exact sound of a player mining, block after block, somewhere you cannot see. Stops if you get close. */
final class MiningSequence implements Sequence {
	private final List<BlockPos> blocks;
	private final double stopDistance;
	private int index;
	private int hits;
	private int hitsNeeded = 5;
	private int timer;

	MiningSequence(List<BlockPos> blocks, double stopDistance) {
		this.blocks = blocks;
		this.stopDistance = stopDistance;
	}

	@Override
	public boolean tick(ServerPlayer player) {
		if (index >= blocks.size()) return false;
		BlockPos pos = blocks.get(index);
		Vec3 center = Vec3.atCenterOf(pos);
		if (player.position().distanceToSqr(center) < stopDistance * stopDistance) return false;
		if (--timer > 0) return true;

		BlockState state = player.level().getBlockState(pos);
		if (state.isAir()) state = Blocks.STONE.defaultBlockState();
		SoundType group = state.getSoundType();

		if (hits < hitsNeeded) {
			hits++;
			timer = 4 + player.getRandom().nextInt(2);
			Cues.sound(player, group.getHitSound(), SoundSource.BLOCKS, center, 0.5f, group.getXRot() * 0.5f);
		} else {
			Cues.sound(player, group.getBreakSound(), SoundSource.BLOCKS, center, 1.0f, group.getXRot() * 0.8f);
			index++;
			hits = 0;
			hitsNeeded = 4 + player.getRandom().nextInt(4);
			timer = 12 + player.getRandom().nextInt(16);
		}
		return true;
	}
}
