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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("occupant")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("status")
						.executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("trigger")
						.then(argument("player", EntityArgument.player())
								.then(argument("event", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Events.ids(), builder))
										.executes(OccupantCommand::trigger))))
				.then(literal("act")
						.then(argument("player", EntityArgument.player())
								.then(argument("act", IntegerArgumentType.integer(0, HauntData.MAX_ACT))
										.executes(ctx -> modify(ctx, "act set to " + IntegerArgumentType.getInteger(ctx, "act"),
												d -> d.setAct(IntegerArgumentType.getInteger(ctx, "act")))))))
				.then(literal("dread")
						.then(argument("player", EntityArgument.player())
								.then(argument("value", FloatArgumentType.floatArg(0, 100))
										.executes(ctx -> modify(ctx, "dread set",
												d -> d.dread = FloatArgumentType.getFloat(ctx, "value"))))))
				.then(literal("pause")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir != null) dir.stopCurrent(EntityArgument.getPlayer(ctx, "player"));
									return modify(ctx, "paused", d -> d.paused = true);
								})))
				.then(literal("resume")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> modify(ctx, "resumed", d -> d.paused = false))))
				.then(literal("stop")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir == null) return 0;
									dir.stopCurrent(EntityArgument.getPlayer(ctx, "player"));
									ctx.getSource().sendSuccess(() -> Component.literal("Stopped."), false);
									return 1;
								})))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> {
									Director dir = director(ctx.getSource());
									if (dir == null) return 0;
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									dir.reset(p);
									ctx.getSource().sendSuccess(() -> Component.literal("The story starts over for " + p.getName().getString() + "."), true);
									return 1;
								})))
				.then(literal("reload")
						.executes(ctx -> {
							OccupantConfig.load();
							ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/occupant.json."), true);
							return 1;
						})));
	}

	private static Director director(CommandSourceStack src) {
		Director dir = Director.get();
		if (dir == null) src.sendFailure(Component.literal("The Occupant is not running."));
		return dir;
	}

	private static int status(CommandSourceStack src, ServerPlayer p) {
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
		src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int trigger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Director dir = director(ctx.getSource());
		if (dir == null) return 0;
		ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
		String event = StringArgumentType.getString(ctx, "event");
		Director.TriggerResult result = dir.trigger(p, event, true);
		switch (result) {
			case STARTED -> ctx.getSource().sendSuccess(() -> Component.literal("Started " + event + "."), false);
			case UNKNOWN -> ctx.getSource().sendFailure(Component.literal("No such event: " + event));
			case BUSY -> ctx.getSource().sendFailure(Component.literal("Something else is already happening."));
			case NO_SPOT -> ctx.getSource().sendFailure(Component.literal(
					"Couldn't find a convincing place for " + event + " here. Try somewhere darker, a cave, or near a door."));
		}
		return result == Director.TriggerResult.STARTED ? 1 : 0;
	}

	@FunctionalInterface
	private interface DataChange {
		void apply(HauntData data) throws CommandSyntaxException;
	}

	private static int modify(CommandContext<CommandSourceStack> ctx, String what, DataChange change) throws CommandSyntaxException {
		Director dir = director(ctx.getSource());
		if (dir == null) return 0;
		ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
		change.apply(dir.data(p));
		dir.markDirty();
		ctx.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": " + what + "."), true);
		return 1;
	}
}
