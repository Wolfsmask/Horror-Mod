package com.wolfsmask.occupant;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Other mods that get in the way of what this one does. Only ones that actually do: performance
 * mods (Sodium, Lithium, ImmediatelyFast, Nvidium, FerriteCore, ModernFix, Entity Culling...),
 * pre-generators like Chunky, and libraries are fine and never listed.
 */
public final class Compatibility {
	/** A mod that is installed and why it matters. {@code serious}: it can break the story, not only the look. */
	public record Conflict(String id, String name, String why, boolean serious) {
	}

	private static final Map<String, String[]> KNOWN = Map.ofEntries(
			// World generation: the house, the places and the fog's edge are all placed for vanilla terrain.
			worldgen("terralith"), worldgen("tectonic"), worldgen("biomesoplenty"), worldgen("byg"),
			worldgen("regions_unexplored"), worldgen("wwoo"), worldgen("continents"), worldgen("lithosphere"),
			worldgen("expanded_ecosphere"), worldgen("natures_spirit"), worldgen("promenade"),
			// Drawing the world past the fog: it stands at the fog's edge, and these show what is behind it.
			Map.entry("distanthorizons", new String[]{"draws land far beyond the fog, so the fog's edge, where it stands, no longer means anything. Turn its LODs off while playing", "y"}),
			Map.entry("voxy", new String[]{"draws land far beyond the fog, so the fog's edge, where it stands, no longer means anything", "y"}),
			// Shader loaders: they work, but replace the game's fog with their own.
			Map.entry("iris", new String[]{"works, but most shader packs draw their own fog instead of this mod's, and the mod's own look is lost. Not recommended; turn shaders off for the intended look", "n"}),
			Map.entry("oculus", new String[]{"works, but shader packs replace this mod's fog. Not recommended", "n"}),
			// The title screen: the mod opens on its own.
			Map.entry("fancymenu", new String[]{"replaces the title screen, which may hide the mod's own first screens", "n"}),
			// Other haunting mods: two directors fighting over the same dark.
			horror("cave_dweller"), horror("the_man_from_the_fog"), horror("mftf"), horror("from_the_fog"),
			horror("fromthefog"), horror("herobrine"), horror("the_obsessed"), horror("thebrokenscript"),
			horror("the_knocker"));

	private Compatibility() {
	}

	private static Map.Entry<String, String[]> worldgen(String id) {
		return Map.entry(id, new String[]{"changes world generation. The house, the old places and its lair are built for the "
				+ "game's own terrain and may not appear, or appear buried or floating", "y"});
	}

	private static Map.Entry<String, String[]> horror(String id) {
		return Map.entry(id, new String[]{"is another horror mod. The two will step on each other's moments, and neither will "
				+ "be as frightening. Play them separately", "y"});
	}

	/** What is installed that gets in the way, if anything. */
	public static List<Conflict> find() {
		List<Conflict> out = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			if (id.equals(Occupant.MOD_ID)) continue;
			String name = mod.getMetadata().getName();
			String[] known = KNOWN.get(id);
			if (known != null) {
				out.add(new Conflict(id, name, known[0], known[1].equals("y")));
			} else if (name.toLowerCase(Locale.ROOT).contains("horror")) {
				out.add(new Conflict(id, name, "looks like another horror mod. The two will step on each other's moments. "
						+ "Play them separately", true));
			}
		}
		return out;
	}

	/** In the log, at start-up, for servers and for anyone reading it. */
	public static void report() {
		for (Conflict c : find()) {
			Occupant.LOGGER.warn("The Occupant: {} ({}) {}.", c.name(), c.id(), c.why());
		}
	}
}
