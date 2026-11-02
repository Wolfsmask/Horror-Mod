"""The long video's YouTube title, description (with chapters) and tags, checked against YouTube's limits."""
from pathlib import Path

HERE = Path(__file__).parent
import sys
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE  # the folder the edit wrote chapters.txt to
chapters = (OUT / "chapters.txt").read_text().strip()
titles = [
    "I Recorded 4 Days in a Haunted Minecraft World... (Found Footage)",
    "Something Was Living in My Minecraft World. I Filmed It.",
    "4 Days With The Occupant | Minecraft Horror Found Footage",
]
hashtags = ["#minecraft", "#minecrafthorror", "#horror", "#foundfootage", "#theoccupant", "#minecraftmods", "#minecraftmod",
            "#fabricmod", "#modrinth", "#minecraftjava", "#horrorgaming", "#psychologicalhorror", "#analoghorror", "#scary",
            "#creepy", "#gaming"]
desc = f"""Four days of tape from a single Minecraft world. It starts with a cabin. It ends in the woods.

Day by day the camera finds more: a camp left in a hurry, a pen of animals that all stop and stare (and then are all dead), an old well that gives back what you drop in it, a voice in the trees that knows my name, a door I know I shut standing open, a cow by my bed that nobody let in, a door in the middle of the woods with nothing behind it that knocks back, a radio that picks up a voice it shouldn't, an empty house with something in its wall, a chapel where the candles light themselves, a graveyard with something watching from the trees. Then something walks across my roof and knocks on the door. The next morning there is a grave outside with my name on it.

This is THE OCCUPANT, a psychological horror mod for Minecraft. Everything you see here is something it can do in your world.

Download (free): [YOUR MODRINTH LINK]
Fabric | Minecraft 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3

Chapters
{chapters}

Captured in-game with The Occupant. Headphones on, lights off.

Which day would you have stopped filming?

""" + " ".join(hashtags)
tags = ["minecraft horror", "minecraft horror mod", "the occupant", "the occupant minecraft", "found footage",
        "minecraft found footage", "minecraft mod", "minecraft mods", "fabric mod", "modrinth", "minecraft java",
        "psychological horror", "analog horror", "horror game", "scary minecraft", "minecraft creepy",
        "haunted minecraft", "minecraft 1.21", "minecraft horror story", "minecraft mystery"]
for t in titles:
    print(len(t), "chars (limit 100):", t)
print("description:", len(desc), "chars (limit 5000); hashtags:", len(hashtags), "(over 60 are all ignored)")
tagline = ", ".join(tags)
print("tags field:", len(tagline), "chars (limit 500)")
(OUT / "yt_long_description.txt").write_text(desc)
(OUT / "yt_long_tags.txt").write_text(tagline)
print("---")
print(desc)
print("---")
print(tagline)
