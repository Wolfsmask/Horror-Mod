package com.wolfsmask.occupant.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.events.Events;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator tools for testing and for recording videos.
 * <pre>
 * /occupant status [player]
 * /occupant trigger &lt;player&gt; &lt;event&gt;   start any event now (still needs a valid place)
 * /occupant act &lt;player&gt; &lt;0-4&gt;          jump to a point in the story
 * /occupant dread &lt;player&gt; &lt;0-100&gt;
 * /occupant pause|resume &lt;player&gt;
 * /occupant stop &lt;player&gt;               end whatever is happening right now
 * /occupant reset &lt;player&gt;              start the story over
 * /occupant reload                       reload config/occupant.json
 * </pre>
 */
public final class OccupantCommand {
	private OccupantCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("occupant")
				.requires(src -> src.hasPermissionLevel(2))
				.then(literal("status")
						.executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> status(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
				.then(literal("trigger")
						.then(argument("player", EntityArgumentType.player())
								.then(argument("event", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(Events.ids(), builder))
										.executes(OccupantCommand::trigger))))
				.then(literal("act")
						.then(argument("player", EntityArgumentType.player())
								.then(argument("act", IntegerArgumentType.integer(0, HauntData.MAX_ACT))
										.executes(ctx -> modify(ctx, "act set to " + IntegerArgumentType.getInteger(ctx, "act"),
												d -> d.setAct(IntegerArgumentType.getInteger(ctx, "act")))))))
				.then(literal("dread")
						.then(argument("player", EntityArgumentType.player())
								.then(argument("value", FloatArgumentType.floatArg(0, 100))
										.executes(ctx -> modify(ctx, "dread set",
												d -> d.dread = FloatArgumentType.getFloat(ctx, "value"))))))
				.then(literal("pause")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir != null) dir.stopCurrent(EntityArgumentType.getPlayer(ctx, "player"));
									return modify(ctx, "paused", d -> d.paused = true);
								})))
				.then(literal("resume")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> modify(ctx, "resumed", d -> d.paused = false))))
				.then(literal("stop")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir == null) return 0;
									dir.stopCurrent(EntityArgumentType.getPlayer(ctx, "player"));
									ctx.getSource().sendFeedback(() -> Text.literal("Stopped."), false);
									return 1;
								})))
				.then(literal("reset")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir == null) return 0;
									ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
									dir.reset(p);
									ctx.getSource().sendFeedback(() -> Text.literal("The story starts over for " + p.getName().getString() + "."), true);
									return 1;
								})))
				.then(literal("reload")
						.executes(ctx -> {
							OccupantConfig.load();
							ctx.getSource().sendFeedback(() -> Text.literal("Reloaded config/occupant.json."), true);
							return 1;
						})));
	}

	private static Director director(ServerCommandSource src) {
		Director dir = Director.get();
		if (dir == null) src.sendError(Text.literal("The Occupant is not running."));
		return dir;
	}

	private static int status(ServerCommandSource src, ServerPlayerEntity p) {
		Director dir = director(src);
		if (dir == null) return 0;
		Haunt h = dir.haunt(p);
		HauntData d = h.data;
		String active = h.activeEventId();
		String text = String.format(
				"%s: act %d, dread %.0f, played %d min (this act %d min), events %d, sightings %d, encounters %d%s%s",
				p.getName().getString(), d.act, d.dread, d.playTicks / 1200, (d.playTicks - d.actStartedAt) / 1200,
				d.eventCount, d.sightings, d.encounters,
				active != null ? ", now: " + active : "",
				d.paused ? " [paused]" : "");
		src.sendFeedback(() -> Text.literal(text).formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int trigger(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		Director dir = director(ctx.getSource());
		if (dir == null) return 0;
		ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
		String event = StringArgumentType.getString(ctx, "event");
		Director.TriggerResult result = dir.trigger(p, event, true);
		switch (result) {
			case STARTED -> ctx.getSource().sendFeedback(() -> Text.literal("Started " + event + "."), false);
			case UNKNOWN -> ctx.getSource().sendError(Text.literal("No such event: " + event));
			case BUSY -> ctx.getSource().sendError(Text.literal("Something else is already happening."));
			case NO_SPOT -> ctx.getSource().sendError(Text.literal(
					"Couldn't find a convincing place for " + event + " here. Try somewhere darker, a cave, or near a door."));
		}
		return result == Director.TriggerResult.STARTED ? 1 : 0;
	}

	@FunctionalInterface
	private interface DataChange {
		void apply(HauntData data) throws CommandSyntaxException;
	}

	private static int modify(CommandContext<ServerCommandSource> ctx, String what, DataChange change) throws CommandSyntaxException {
		Director dir = director(ctx.getSource());
		if (dir == null) return 0;
		ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
		change.apply(dir.data(p));
		dir.markDirty();
		ctx.getSource().sendFeedback(() -> Text.literal(p.getName().getString() + ": " + what + "."), true);
		return 1;
	}
}
