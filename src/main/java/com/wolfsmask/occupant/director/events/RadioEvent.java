package com.wolfsmask.occupant.director.events;

import com.wolfsmask.occupant.director.EventContext;
import com.wolfsmask.occupant.director.HorrorEvent;
import com.wolfsmask.occupant.director.Sequence;
import com.wolfsmask.occupant.director.Timeline;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Near a radio set (any jukebox will do), it picks something up: static, and through it, broken
 * up, a voice that knows your name, and where you are standing.
 */
public final class RadioEvent extends HorrorEvent {
	public static final String ID = "radio";
	private static final String[] LINES = {"...can anyone hear me... {player}... it's at the edge of the trees...",
			"...{player}... don't go back to the house... it's...", "...this is... is anyone still... it knows the way to {player}...",
			"...stay where you are, {player}. I can see you from here...", "...if you can hear this, it's already behind..."};

	public RadioEvent() {
		super(ID, Tier.MINOR, 2, 5, 15);
	}

	@Override
	public boolean fits(EventContext ctx) {
		return !ctx.situation.inCombat() && radio(ctx) != null;
	}

	@Nullable
	private static BlockPos radio(EventContext ctx) {
		return Spots.nearestBlock(ctx.world, ctx.player.blockPosition(), 14, 4, pos -> ctx.world.getBlockState(pos).is(Blocks.JUKEBOX));
	}

	@Override
	@Nullable
	public Sequence begin(EventContext ctx) {
		BlockPos radio = radio(ctx);
		if (radio == null) return null;
		Vec3 at = Vec3.atCenterOf(radio);
		String line = LINES[ctx.random.nextInt(LINES.length)].replace("{player}", ctx.player.getName().getString());
		return new Timeline()
				.at(0, p -> Cues.sound(p, ModSounds.STATIC, SoundSource.BLOCKS, at, 1.0f, 0.9f))
				.at(18, p -> Cues.message(p, broken(line, ctx)))
				.at(40, p -> Cues.sound(p, ModSounds.STATIC, SoundSource.BLOCKS, at, 0.8f, 0.7f));
	}

	/** The line as it comes through: grey, and here and there eaten by the static. */
	private static Component broken(String line, EventContext ctx) {
		MutableComponent out = Component.literal("");
		for (String word : line.split(" ")) {
			boolean lost = ctx.random.nextFloat() < 0.18f;
			out.append(Component.literal(word + " ").withStyle(lost
					? new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.OBFUSCATED}
					: new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}));
		}
		return out;
	}
}
