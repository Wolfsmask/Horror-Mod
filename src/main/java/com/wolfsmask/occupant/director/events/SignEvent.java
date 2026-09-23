package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3d;
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
		ServerPlayerEntity p = ctx.player;
		ServerWorld world = ctx.world;
		BlockPos spot = Spots.aroundPlayer(p, ctx.random, 7, 16, 100, 180, !ctx.situation.underground(), 30, pos ->
				world.getBlockState(pos).isAir()
						&& WorldBlocks.isNaturalFloor(world.getBlockState(pos.down()))
						&& Sight.isHidden(p, pos)
						&& Spots.awayFromOthers(p, Vec3d.ofCenter(pos), 16));
		if (spot == null) return null;

		float yaw = Sight.yawBetween(p.getPos(), Vec3d.ofBottomCenter(spot)) + 180.0f;
		BlockState state = Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION, RotationPropertyHelper.fromYaw(yaw));
		if (!state.canPlaceAt(world, spot)) return null;
		if (!world.setBlockState(spot, state, Block.NOTIFY_ALL)) return null;

		if (world.getBlockEntity(spot) instanceof SignBlockEntity sign) {
			String[] lines = pickMessage(ctx).split("\\|", -1);
			SignText text = new SignText();
			for (int i = 0; i < 4 && i < lines.length; i++) {
				text = text.withMessage(i, Text.literal(lines[i]));
			}
			sign.setText(text, true);
			sign.setWaxed(true);
			sign.markDirty();
			world.updateListeners(spot, state, state, Block.NOTIFY_ALL);
		}
		return new Timeline().at(0, pl -> {
		});
	}

	private static String pickMessage(EventContext ctx) {
		List<String> options = ctx.config.signMessages;
		String msg = options.get(ctx.random.nextInt(options.size()));
		long day = ctx.world.getTimeOfDay() / 24000L + 1;
		return msg.replace("{player}", ctx.player.getName().getString()).replace("{day}", Long.toString(day));
	}
}
