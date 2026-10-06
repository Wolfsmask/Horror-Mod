#!/usr/bin/env python3
"""
Writes the mod's advancements, in both layouts Minecraft has used:

  data/occupant/advancement/   (1.21 and later: icon by "id")
  data/occupant/advancements/  (1.20.1: icon by "item")

Each version reads only its own folder. Titles and descriptions are translation keys
(advancements.occupant.<name>.title / .description) in assets/occupant/lang. Each advancement
has one criterion, "granted", that nothing in the game meets: the mod grants it
(story/Achievements.java). The root's background is filled in at build time, as the texture's
name changed in 1.21.5.

    python3 tools/generate_advancements.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
DATA = ROOT / "data/occupant"

# name, parent, icon, frame, hidden, English title, English description
ADVANCEMENTS = [
    ("root", None, "minecraft:soul_lantern", "task", False,
     "The Occupant", "There is something in this world with you"),
    ("not_alone", "root", "minecraft:spyglass", "task", False,
     "You're Not Alone", "See it for the first time"),
    ("watched", "not_alone", "minecraft:ender_eye", "task", False,
     "Something Is Watching You", "Let it watch you for a while without noticing"),
    ("who_is_there", "root", "minecraft:oak_door", "task", False,
     "Who's There?", "Hear knocking at night"),
    ("check_the_windows", "who_is_there", "minecraft:glass_pane", "task", False,
     "Check the Windows", "See a face at the glass"),
    ("lights_out", "root", "minecraft:torch", "task", False,
     "Lights Out", "Your light went out. You didn't do that"),
    ("sleepless", "root", "minecraft:red_bed", "task", False,
     "Monsters Nearby", "Be told you can't sleep. There aren't any"),
    ("not_what_i_said", "root", "minecraft:oak_sign", "task", False,
     "That's Not What I Said", "Read something in chat that nobody typed"),
    ("saving_world", "not_what_i_said", "minecraft:paper", "task", False,
     "Saving World...", "You didn't save"),
    ("bad_reception", "root", "minecraft:jukebox", "task", False,
     "Bad Reception", "Hear a voice on the radio that knows your name"),
    ("footprints", "root", "minecraft:snowball", "task", False,
     "Footprints", "Find tracks in the snow, leading to your door"),
    ("somebody_home", "root", "minecraft:spruce_door", "task", False,
     "Somebody's Home", "Step into the house"),
    ("dear_diary", "root", "minecraft:writable_book", "task", False,
     "Dear Diary", "Find a page of the survivor's log"),
    ("every_word", "dear_diary", "minecraft:campfire", "goal", False,
     "Every Word", "Find the survivor's last camp"),
    ("down_there", "root", "minecraft:ladder", "goal", True,
     "Don't Go Down There", "Find where it sleeps"),
    ("last_night", "not_alone", "minecraft:clock", "goal", False,
     "The Last Night", "See the last night through to the end"),
    ("ending_found", "last_night", "minecraft:skeleton_skull", "challenge", True,
     "Left Behind", "End the story having found what was left of them"),
    ("ending_hid", "last_night", "minecraft:white_bed", "challenge", True,
     "So It Came In", "End the story having hidden from it"),
    ("ending_learned", "last_night", "minecraft:player_head", "challenge", True,
     "It Knows How to Be You", "End the story any other way"),
]


def advancement(name, parent, icon, frame, hidden, old):
    display = {
        "icon": {"item": icon} if old else {"id": icon},
        "title": {"translate": f"advancements.occupant.{name}.title"},
        "description": {"translate": f"advancements.occupant.{name}.description"},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": hidden,
    }
    if parent is None:
        display["background"] = "${adv_background}"
        display["show_toast"] = False
        display["announce_to_chat"] = False
    out = {}
    if parent is not None:
        out["parent"] = f"occupant:{parent}"
    out["display"] = display
    out["criteria"] = {"granted": {"trigger": "minecraft:impossible"}}
    return out


def main():
    for folder, old in (("advancement", False), ("advancements", True)):
        d = DATA / folder
        d.mkdir(parents=True, exist_ok=True)
        for name, parent, icon, frame, hidden, _, _ in ADVANCEMENTS:
            (d / f"{name}.json").write_text(json.dumps(advancement(name, parent, icon, frame, hidden, old), indent=2) + "\n")
    lang = ROOT / "assets/occupant/lang/en_us.json"
    keys = json.loads(lang.read_text(encoding="utf-8"))
    for name, _, _, _, _, title, desc in ADVANCEMENTS:
        keys[f"advancements.occupant.{name}.title"] = title
        keys[f"advancements.occupant.{name}.description"] = desc
    lang.write_text(json.dumps(keys, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote", len(ADVANCEMENTS), "advancements")


if __name__ == "__main__":
    main()
