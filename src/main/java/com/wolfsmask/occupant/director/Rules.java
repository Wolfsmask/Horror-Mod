package com.wolfsmask.occupant.director;

import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.compat.Compat;
import com.wolfsmask.occupant.util.Cues;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Once the story has started, there are rules, and it keeps them. You stay in its world: go
 * through a portal and you are back where you were, told that it does not want you in there. And
 * you play it fair: switch to creative or spectator and you are put back, told that it saw.
 * <p>
 * Not while the story is paused (/occupant pause, for filming), not in a world where the player
 * chose to leave it alone at the gate, not once it has let them go, and not with
 * {@code keepToTheRules} off, or creative haunting on for recording.
 */
final class Rules {
	private static final String[] ELSEWHERE = {"It does not want you in there.", "Not there. Stay where it can see you.",
			"It wants you here. With it.", "You don't get to leave."};
	private static final String[] CHEATING = {"It doesn't want you breaking the rules.", "No. Play fair.",
			"It saw what you tried to do.", "That isn't how this works."};

	private Rules() {
	}

	static void check(ServerPlayer player, Haunt h, OccupantConfig cfg) {
		HauntData d = h.data;
		if (!cfg.enabled || !cfg.keepToTheRules || d.paused || !d.introduced || d.ending == LastNightEnding.FOUND) return;
		MinecraftServer server = Compat.level(player).getServer();
		ServerLevel overworld = server.overworld();

		if (Compat.level(player) != overworld) {
			if (player.isDeadOrDying()) return;
			BlockPos back = h.lastSafe != null ? h.lastSafe : Compat.spawnPos(overworld);
			run(server, String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.1f %d %.1f",
					player.getStringUUID(), back.getX() + 0.5, back.getY(), back.getZ() + 0.5));
			say(player, ELSEWHERE);
			Occupant.LOGGER.info("{} tried to leave its world; brought back to {}", player.getName().getString(), back);
			return;
		}
		// Where to bring them back to: somewhere they stood in its world, not in a portal.
		if (player.tickCount % 20 == 0 && player.onGround()) {
			BlockState feet = overworld.getBlockState(player.blockPosition());
			if (!feet.is(Blocks.NETHER_PORTAL) && !feet.is(Blocks.END_PORTAL)) h.lastSafe = player.blockPosition();
		}

		if ((player.isCreative() && !cfg.hauntCreative) || player.isSpectator()) {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			say(player, CHEATING);
			Occupant.LOGGER.info("{} tried to leave survival; put back", player.getName().getString());
		}
	}

	private static void say(ServerPlayer player, String[] lines) {
		String line = lines[player.getRandom().nextInt(lines.length)];
		if (OccupantConfig.get().screenWhispers) {
			Cues.whisper(player, line, 90);
		} else {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line)
					.withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), true);
		}
	}

	private static void run(MinecraftServer server, String command) {
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		} catch (RuntimeException e) {
			Occupant.LOGGER.warn("Could not run {}", command, e);
		}
	}
}
