package com.wolfsmask.occupant.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wolfsmask.occupant.Occupant;
import com.wolfsmask.occupant.OccupantConfig;
import com.wolfsmask.occupant.director.Director;
import com.wolfsmask.occupant.director.Haunt;
import com.wolfsmask.occupant.director.HauntData;
import com.wolfsmask.occupant.director.events.Events;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.wolfsmask.occupant.entity.OccupantEntity;
import com.wolfsmask.occupant.registry.ModEntities;
import com.wolfsmask.occupant.util.Sight;
import com.wolfsmask.occupant.util.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
				// Operators, or anyone in their own single-player world (where "cheats" may be off,
				// which would otherwise hide this command completely).
				.requires(src -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src)
						|| src.getServer().isSingleplayer())
				.then(literal("here")
						.executes(ctx -> here(ctx.getSource(), 5.0f))
						.then(argument("distance", FloatArgumentType.floatArg(1.0f, 40.0f))
								.executes(ctx -> here(ctx.getSource(), FloatArgumentType.getFloat(ctx, "distance")))))
				.then(literal("check").executes(ctx -> check(ctx.getSource())))
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

	/**
	 * Puts the Occupant in front of you right now, wherever you are, with no conditions at all.
	 * The story events all need a convincing place before they will run; this one never refuses,
	 * so there is always a way to prove the mod is working.
	 */
	private static int here(CommandSourceStack src, float distance) throws CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		ServerLevel world = p.level();
		Vec3 look = Sight.flatLook(p);
		Vec3 want = p.position().add(look.scale(distance));
		BlockPos feet = Spots.groundNear(world, (int) Math.floor(want.x), (int) Math.floor(want.y),
				(int) Math.floor(want.z), 8);
		Vec3 at = feet != null ? Vec3.atBottomCenterOf(feet) : want;

		OccupantEntity e = ModEntities.OCCUPANT.create(world, EntitySpawnReason.COMMAND);
		if (e == null) {
			src.sendFailure(Component.literal("Could not create the entity."));
			return 0;
		}
		e.standAlone(p);
		float yaw = Sight.yawBetween(at, p.position());
		e.snapTo(at.x, at.y, at.z, yaw, 0.0f);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.setMode(OccupantEntity.Mode.STARE);
		e.setForm(OccupantEntity.Form.REVEALED);
		if (!world.addFreshEntity(e)) {
			src.sendFailure(Component.literal("Could not place it there."));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("It is standing behind you... no, in front of you.")
				.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	/** Answers "is this thing even working?" in one line each. */
	private static int check(CommandSourceStack src) throws CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		OccupantConfig cfg = OccupantConfig.get();
		Director dir = Director.get();
		src.sendSuccess(() -> Component.literal("The Occupant " + Occupant.VERSION_NOTE).withStyle(ChatFormatting.WHITE), false);
		line(src, "mod loaded (server side)", true, "");
		line(src, "director running", dir != null, "restart the world");
		line(src, "enabled in config", cfg.enabled, "set enabled=true in config/occupant.json");
		line(src, "this player can be haunted", !p.isCreative() || cfg.hauntCreative,
				"you are in creative: switch to survival, or set hauntCreative=true");
		line(src, "this world is allowed", !cfg.overworldOnly || p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD,
				"overworldOnly is on and you are not in the Overworld");
		if (dir != null) {
			HauntData d = dir.data(p);
			line(src, "story started (act " + d.act + ")", d.act > 0,
					"it waits " + cfg.graceMinutes + " min before anything happens: /occupant act " + p.getName().getString() + " 2");
			line(src, "not paused", !d.paused, "/occupant resume " + p.getName().getString());
		}
		int near = p.level().getEntitiesOfClass(OccupantEntity.class, new AABB(p.blockPosition()).inflate(64)).size();
		src.sendSuccess(() -> Component.literal("  " + near + " Occupant(s) within 64 blocks. Try /occupant here.")
				.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static void line(CommandSourceStack src, String what, boolean ok, String fix) {
		Component text = Component.literal(ok ? "  [ok] " : "  [--] ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
				.copy().append(Component.literal(what + (ok || fix.isEmpty() ? "" : " -> " + fix)).withStyle(ChatFormatting.GRAY));
		src.sendSuccess(() -> text, false);
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
