package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.director.events.Found;
import com.wolfsmask.occupant.registry.ModSounds;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * It does not like it when you hide. Shut away from it (in a house, down a hole, under the ground)
 * long enough, and it says so; a minute more, and it says that is the last time it will; half a
 * minute after that, it comes for you. What it does when it gets there depends on where you hid:
 * it takes the roof off a house, it smashes its way down into a hole, and in the deep places it
 * hunts you through the dark (see {@link Found}).
 * <p>
 * Asleep in a bed is not hiding. From the second act; and after it has come for them, not again
 * for a while.
 */
public final class Hiding {
	/** Where they are, hidden: nowhere (they are out in the open), indoors, in a hole, or deep down. */
	public enum Where { OPEN, HOUSE, HOLE, CAVE }

	/** Seconds hidden before each of what it does: its warning, the last warning, and coming. */
	static final int WARN = 30;
	static final int LAST_WARNING = 90;
	static final int COMES = 120;
	/** Back out in the open this long, and it starts counting again from nothing. */
	private static final int FORGIVEN_AFTER = 8;
	/** After it has come for them, not again for this long, in ticks of play. */
	private static final long CALM = 20L * 60 * 6;
	/** How much ground over their head still counts as a hole rather than the deep: four blocks of it. */
	public static final int SHALLOW = 4;

	private static final String[] WARNINGS = {"It doesn't like it when you hide.", "It doesn't like it when you hide, {player}.",
			"Why are you hiding from it?"};
	private static final String[] LAST = {"Last warning.", "Last warning, {player}.", "It won't tell you again."};

	private Hiding() {
	}

	/** Once a second, from the Director, with what the player is doing now. */
	static void tick(ServerPlayer player, Haunt h, Situation s, OccupantConfig cfg, Director director) {
		HauntData d = h.data;
		Where where = where(player);
		if (where == Where.OPEN) {
			if (++h.outFor >= FORGIVEN_AFTER) h.hiddenSeconds = 0;
			return;
		}
		h.outFor = 0;
		if (player.isSleeping() || !cfg.hidingPunished || d.act < 2 || d.playTicks < h.hideCalmUntil) return;
		// While something else is happening to them, the clock stops.
		if (h.active != null) return;
		h.hiddenSeconds++;
		int n = h.hiddenSeconds;
		if (n == WARN) {
			say(player, WARNINGS, cfg);
			d.addDread(4f);
		} else if (n == LAST_WARNING) {
			say(player, LAST, cfg);
			Cues.soundAtEars(player, ModSounds.KNOCK, SoundSource.HOSTILE, 0.9f, 0.7f);
			d.addDread(8f);
		} else if (n >= COMES) {
			h.hiddenSeconds = 0;
			h.hideCalmUntil = d.playTicks + CALM;
			// In sound-only mode it is never seen: only what it said.
			if (cfg.soundOnly) return;
			Sequence found = Found.begin(h, player, where);
			if (found != null) director.beginNow(player, Found.ID, found);
		}
	}

	private static void say(ServerPlayer player, String[] lines, OccupantConfig cfg) {
		String line = lines[player.getRandom().nextInt(lines.length)].replace("{player}", player.getName().getString());
		if (cfg.screenWhispers) {
			Cues.whisper(player, line, 110);
		} else {
			Cues.message(player, Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/**
	 * Where they are. Under something they could have built (planks, glass, brick, wool) is a
	 * house; under a few blocks of the ground itself is a hole; under more than that, the deep. Under
	 * leaves, or nothing, is out in the open.
	 */
	public static Where where(ServerPlayer player) {
		ServerLevel world = Compat.level(player);
		BlockPos head = player.blockPosition().above();
		if (world.canSeeSky(head)) return Where.OPEN;
		int made = 0;
		int natural = 0;
		boolean roof = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int up = 1; up <= 12; up++) {
			at.set(head.getX(), head.getY() + up, head.getZ());
			BlockState state = world.getBlockState(at);
			if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
			if (state.getCollisionShape(world, at).isEmpty() && state.getFluidState().isEmpty()) continue;
			roof = true;
			if (made(state)) made++;
			else natural++;
		}
		if (!roof) return Where.OPEN;
		if (made > 0 && made >= natural) return Where.HOUSE;
		int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
		return surface - 1 - head.getY() <= SHALLOW ? Where.HOLE : Where.CAVE;
	}

	/** Something somebody made, not something that was there. */
	public static boolean made(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS)
				|| s.is(BlockTags.WOOL) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS)
				|| s.is(BlockTags.FENCES) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE)
				|| s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.SMOOTH_STONE)
				|| s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_DIORITE) || s.is(Blocks.POLISHED_GRANITE)
				|| s.is(Blocks.QUARTZ_BLOCK) || s.is(Blocks.HAY_BLOCK) || s.is(Blocks.BOOKSHELF);
	}
}
