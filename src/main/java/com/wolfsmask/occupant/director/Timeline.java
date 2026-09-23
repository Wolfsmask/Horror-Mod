package com.wolfsmask.occupant.director;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A simple scripted list of actions at fixed tick offsets. */
public final class Timeline implements Sequence {
	private record Step(int tick, Consumer<ServerPlayer> action) {
	}

	private final List<Step> steps = new ArrayList<>();
	private Predicate<ServerPlayer> abortIf = p -> false;
	private Runnable onEnd = () -> {
	};
	private int tick;
	private int next;
	private boolean sorted;

	public Timeline at(int tick, Consumer<ServerPlayer> action) {
		steps.add(new Step(Math.max(0, tick), action));
		sorted = false;
		return this;
	}

	/** Stop early (skipping remaining steps) as soon as this becomes true. */
	public Timeline abortIf(Predicate<ServerPlayer> condition) {
		this.abortIf = condition;
		return this;
	}

	public Timeline onEnd(Runnable onEnd) {
		this.onEnd = onEnd;
		return this;
	}

	@Override
	public boolean tick(ServerPlayer player) {
		if (!sorted) {
			steps.sort(Comparator.comparingInt(Step::tick));
			sorted = true;
		}
		if (abortIf.test(player)) return false;
		while (next < steps.size() && steps.get(next).tick() <= tick) {
			steps.get(next++).action().accept(player);
		}
		tick++;
		return next < steps.size();
	}

	@Override
	public void end() {
		onEnd.run();
	}
}
