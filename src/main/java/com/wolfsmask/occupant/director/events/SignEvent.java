package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A sign appears behind you, facing where you were standing. It is waxed. You cannot change what it says. */
public final class SignEvent extends HorrorEvent {
	public SignEvent() {
		super("sign", Tier.MINOR, 2, 4, 40);
	}

	@Override
	public boolean allowedBy(OccupantConfig config) {
		return config.worldChanges;
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && !ctx.situation.busy();
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		ServerPlayer p = ctx.player;
		ServerLevel world = ctx.world;
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, 7, 16, 100, 180, !ctx.situation.underground(), 30, pos ->
				world.getBlockState(pos).isAir()
						&& WorldBlocks.isNaturalFloor(world.getBlockState(pos.below()))
						&& Sight.isHidden(p, pos)
						&& Spots.awayFromOthers(p, Vec3.atCenterOf(pos), 16));
		if (spot == null) return null;

		float yaw = Sight.yawBetween(p.position(), Vec3.atBottomCenterOf(spot)) + 180.0f;
		BlockState state = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw));
		if (!state.canSurvive(world, spot)) return null;
		if (!world.setBlock(spot, state, Block.UPDATE_ALL)) return null;

		if (world.getBlockEntity(spot) instanceof SignBlockEntity sign) {
			String[] lines = pickMessage(ctx).split("\\|", -1);
			SignText text = new SignText();
			for (int i = 0; i < 4 && i < lines.length; i++) {
				text = text.setMessage(i, Component.literal(lines[i]));
			}
			sign.setText(text, true);
			sign.setWaxed(true);
			sign.setChanged();
			world.sendBlockUpdated(spot, state, state, Block.UPDATE_ALL);
		}
		return new Timeline().at(0, pl -> {
		});
	}

	private static String pickMessage(EventContext ctx) {
		List<String> options = ctx.config.signMessages;
		String msg = options.get(ctx.random.nextInt(options.size()));
		long day = ctx.world.getOverworldClockTime() / 24000L + 1;
		return msg.replace("{player}", ctx.player.getName().getString()).replace("{day}", Long.toString(day));
	}
}
