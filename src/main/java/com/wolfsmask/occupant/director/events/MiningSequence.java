package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.block.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
	public boolean tick(ServerPlayerEntity player) {
		if (index >= blocks.size()) return false;
		BlockPos pos = blocks.get(index);
		Vec3d center = Vec3d.ofCenter(pos);
		if (player.getPos().squaredDistanceTo(center) < stopDistance * stopDistance) return false;
		if (--timer > 0) return true;

		BlockState state = player.getWorld().getBlockState(pos);
		if (state.isAir()) state = Blocks.STONE.getDefaultState();
		BlockSoundGroup group = state.getSoundGroup();

		if (hits < hitsNeeded) {
			hits++;
			timer = 4 + player.getRandom().nextInt(2);
			Cues.sound(player, group.getHitSound(), SoundCategory.BLOCKS, center, 0.5f, group.getPitch() * 0.5f);
		} else {
			Cues.sound(player, group.getBreakSound(), SoundCategory.BLOCKS, center, 1.0f, group.getPitch() * 0.8f);
			index++;
			hits = 0;
			hitsNeeded = 4 + player.getRandom().nextInt(4);
			timer = 12 + player.getRandom().nextInt(16);
		}
		return true;
	}
}
