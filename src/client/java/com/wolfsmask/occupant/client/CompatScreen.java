package com.wolfsmask.occupant.client;

import com.wolfsmask.occupant.Compatibility;
import com.wolfsmask.occupant.Occupant;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Before anything else, once: if another installed mod gets in the way of this one (a world
 * generation overhaul, a shader loader, another horror mod), what it is and what it does to the
 * story. Then on. Not shown again for the same set of mods.
 */
public final class CompatScreen extends Screen {
	private final List<Compatibility.Conflict> conflicts;
	private final Runnable next;

	private CompatScreen(List<Compatibility.Conflict> conflicts, Runnable next) {
		super(Component.literal("The Occupant"));
		this.conflicts = conflicts;
		this.next = next;
	}

	/** A key for exactly this set of mods: when it changes, they are told again. */
	private static String key(List<Compatibility.Conflict> conflicts) {
		List<String> ids = new ArrayList<>();
		for (Compatibility.Conflict c : conflicts) ids.add(c.id());
		ids.sort(null);
		return String.join(",", ids);
	}

	/** The warning, if there is anything to warn about and it has not been seen; else null. */
	static Screen ifNeeded(Runnable next) {
		List<Compatibility.Conflict> found = Compatibility.find();
		if (found.isEmpty() || key(found).equals(ClientConfig.get().compatSeen)) return null;
		return new CompatScreen(found, next);
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CompatScreen c) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> c.text(graphics));
			}
		});
	}

	@Override
	protected void init() {
		Occupant.LOGGER.info("[client] warned about {} other mod(s)", conflicts.size());
		addRenderableWidget(Button.builder(Component.literal("I understand, continue"), b -> {
			ClientConfig.get().compatSeen = key(conflicts);
			ClientConfig.save();
			next.run();
		}).bounds(this.width / 2 - 100, this.height - 32, 200, 20).build());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void text(GuiGraphicsExtractor g) {
		Font font = Minecraft.getInstance().font;
		int y = 20;
		y = centred(g, font, "Before you go in: some of your mods get in the way", y, 0xFFE8DECE) + 6;
		y = wrapped(g, font, "The Occupant is all in one: it brings its own fog, colour, sound, places and story, "
				+ "and needs nothing else. These mods change things it depends on:", y, 0xFFA8A098) + 8;
		for (Compatibility.Conflict c : conflicts) {
			if (y > this.height - 60) {
				centred(g, font, "...and more (see the log)", y, 0xFF8A8278);
				break;
			}
			y = wrapped(g, font, (c.serious() ? "! " : "- ") + c.name() + " " + c.why() + ".", y,
					c.serious() ? 0xFFD07A6A : 0xFFCFC6B8) + 4;
		}
		wrapped(g, font, "Performance mods (Sodium, Lithium, ImmediatelyFast, Nvidium, Chunky...) are fine.", this.height - 52, 0xFF7A7268);
	}

	private int centred(GuiGraphicsExtractor g, Font font, String s, int y, int colour) {
		GuiCompat.text(g, font, s, (this.width - font.width(s)) / 2, y, colour);
		return y + font.lineHeight;
	}

	/** Word-wrapped to the middle of the screen; returns where the next line goes. */
	private int wrapped(GuiGraphicsExtractor g, Font font, String s, int y, int colour) {
		int max = Math.min(420, this.width - 40);
		int left = (this.width - max) / 2;
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			if (line.length() > 0 && font.width(line + " " + word) > max) {
				GuiCompat.text(g, font, line.toString(), left, y, colour);
				y += font.lineHeight + 1;
				line.setLength(0);
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) {
			GuiCompat.text(g, font, line.toString(), left, y, colour);
			y += font.lineHeight + 1;
		}
		return y;
	}
}
