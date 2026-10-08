# Posting The Occupant on Modrinth

Everything here is ready to use: the icon, the gallery, the description and the files.

- `icon.png`: the project icon, 512x512.
- `gallery-nightmare/`: the gallery images (`01-too-close.png` is the one to feature).
- `stills/`: the untouched game frames the gallery is made from, if you want to grade them yourself.
- `description.md`: paste this into the description box.
- `../CREATORS.md` and `../creators/`: the **Creator Pack** (guide, thumbnails, Shorts covers),
  to share with creators directly while the repository is private.
- The mod files, one per Minecraft version, are in `dist/` at the top of the repo.

Every gallery picture is a frame of the real game with the real mod, photographed by the
automated client test. They are only cropped, colour-graded and captioned. No shaders, nothing
painted. Say so if anyone asks; it is a selling point.

---

## 1. Create the project

1. Sign in at [modrinth.com](https://modrinth.com), then **+ (top right) → New project**.
2. **Name:** `The Occupant`
3. **URL:** `the-occupant` (or `occupant`, if that is taken)
4. **Summary** (shown in search results, so it has to sell it in one breath):

   > Something else is living in your world. Slow-burn psychological horror: a seventeen-foot thing that learns how to be you, places people used to be, and a story that's different for every player.

5. **Visibility:** leave it as a draft until everything below is done.

## 2. Settings

- **Icon:** upload `icon.png`.
- **Categories:** pick the featured three: **Adventure**, **Mobs**, **Worldgen**. Add **Game Mechanics** as an additional category.
- **Environment:** *Client side: Required*, *Server side: Required*.
- **License:** *All Rights Reserved*, to match the mod's `fabric.mod.json`. If you would rather let people
  use it in modpacks freely, say so in the description, or pick a licence such as MIT (and change
  `"license"` in `fabric.mod.json` to match).
- **Links:** add the GitHub repository as *Source* and *Issues* only if it is public.

## 3. Description

Open the **Description** tab and paste in all of `description.md`.

Once the gallery is uploaded (step 4), you can put the featured image at the very top of the
description: right-click it in your gallery → *Copy image address*, then add this as the first line:

```
![The Occupant](PASTE-THE-ADDRESS-HERE)
```

## 4. Gallery

Upload the images in `gallery-nightmare/` in this order. Tick **Featured** on the first one only:
it is the image people see in search, so it decides whether they click.

These are painted, not screenshots: every room, window, wood and corridor is made from nothing,
and the only thing taken from the game is it (its face, and its body cut out of the client
test's frames of it). If anyone asks, say that it is the real creature, in scenes made for the
page.

| File | Title | Description |
|---|---|---|
| `01-too-close.png` | Too close | It does not move while you are looking at it. |
| `02-over-you.png` | Over you | You woke up. It was already there. |
| `03-the-window.png` | The window | It waits a step back from the glass. |
| `04-the-treeline.png` | The treeline | Further back than you would ever look. |
| `05-the-doorway.png` | The doorway | You left that door open. |
| `06-the-corridor.png` | The corridor | One light, halfway down. |
| `07-the-tape.png` | The tape | It is on all of them. |

`gallery-dread/` (real game frames, made into bad photographs) and `gallery/` (the earlier film
stills) are there too, if you want a few that are plainly the game.

## 5. Versions

For each file in `dist/`, open **Versions → Create a version** (or drag the jar onto the page):

| File | Version number | Minecraft versions | Channel |
|---|---|---|---|
| `occupant-0.1.2+mc26.3.jar` | `0.1.2+mc26.3` | 26.3 | Release |
| `occupant-0.1.2+mc26.2.jar` | `0.1.2+mc26.2` | 26.2 | Release |
| `occupant-0.1.2+mc26.1.2.jar` | `0.1.2+mc26.1.2` | 26.1.2 | Release |
| `occupant-0.1.2+mc1.21.11.jar` | `0.1.2+mc1.21.11` | 1.21.11 | Release |
| `occupant-0.1.2+mc1.21.1.jar` | `0.1.2+mc1.21.1` | 1.21.1 | Release |
| `occupant-0.1.2+mc1.20.1.jar` | `0.1.2+mc1.20.1` | 1.20.1 | Release |
| `occupant-0.1.2+mc26.4-snapshot-2.jar` | `0.1.2+mc26.4-snapshot-2` | 26.4-snapshot-2 | **Alpha** |

For every version:

- **Version name:** `0.1.2 for <Minecraft version>`, for example `0.1.2 for 1.21.1`.
- **Loaders:** Fabric.
- **Dependencies:** add **Fabric API** as **Required**.
- **Changelog** (the same for all of them):

  ```
  0.1.2: the rescue is a scene now.

  - About to die to monsters? You watch, and you can't look away: your view is drawn round to it, the picture narrows, and nothing you press moves you until it is over.
  - Its legs go into every monster after you that it can reach, one after another (eight at most: it stands on the other two), in at the back and out through the chest. They are lifted with their arms, legs and head hanging loose and swinging, and they die up there. Any it can't reach, your eyes are drawn to, one by one, and they are simply gone.
  - It only takes what is actually after you (not every monster nearby), and it stands behind the one that hit you.
  - Falls: you wake whole, with full health and a full stomach. Falling into the void is always caught now (a second fall soon after the first used to kill you, and so did a totem in your hand, which can't stop the void).
  - After the rescue you are whole again too.
  - Fixed: caught falling far from your bed (or from spawn), you could wake inside the bedrock at the bottom of the world and suffocate.
  - Fixed in the rescue: an enderman no longer teleports off the leg; a big slime no longer splits into more slimes; a jockey's rider is lifted off its mount; a held zombie no longer calls in others on Hard; with its back to a wall, it stands behind another monster instead. /kill always works.

  0.1.1: fixes, and a better rescue.

  - The rescue: it is there behind the monster, one of its long legs goes into it and lifts it off the ground, the monster dies up there, it is gone, and then the words. A creeper already hissing is held too, and does not go off.
  - With a friend (e4mc, e4all, LAN): it now comes for each of you once you are 16 blocks apart (it needed 48, so two friends together hardly ever saw it). Checked in CI by joining a real server over the network.
  - The black way in no longer shows on servers that don't have the mod.
  - It is not hurt or moved by the world (cactus, berry bushes, stray arrows): only by your hand.
  - Nothing it builds or digs goes on, or into, anything you made: its lair, the last camp, tunnels (always a dead end now), marked trees (only real trees).
  - The face at the window is bent down to the glass.
  - Fixes: the flower where you died is never a wither rose; the default sign fits; a place that fails to build is skipped, never a crash.

  0.1: the first release.

  - A story in four acts, three endings, and a survivor's log that leads to their last camp.
  - It never moves while you watch it, and it will not let you die (not like that).
  - Places people used to be: villages, a house, keeps, camps, graveyards, watchtowers, chapels, radio shacks, lighthouses, and its lair.
  - A real fog, a score that listens, and a lot of small wrong things.
  - Creator Cut for recording: the whole story in about forty minutes.
  - Settings in game (pause menu → The Occupant). Menus in English, Español, Français, Deutsch and Português (Brasil).
  ```

Upload the newest Minecraft version last: Modrinth shows the most recently added version first.

## 6. Publish

Check the project page once as a visitor would. Then **Submit for review**. Modrinth's
moderators usually look at a new project within a few days. They check that:

- the description says what the mod does;
- the files are what the page says they are;
- the licence and the links are real.

Before submitting, decide about the **creature design**. It is based on Doctor Nowhere's
Father Fester. The description already credits it and calls the mod an unofficial fan work. It
is still worth asking the artist, since a takedown request could pull the project.

---

## Making people want to play it

- **The featured image and the summary are the whole first impression.** In search, people only
  see those two things. Keep the featured image dark with one clear shape in it, and keep the
  summary to one sentence of mood and one of content.
- **The first two lines of the description** are the hook. Everything after that is for people
  who are already interested.
- **A short video sells horror better than anything else.** 30 to 60 seconds is enough:
  1. walk into the house at dusk;
  2. turn, and it is in the hallway;
  3. cut to black on the title.

  Upload it to YouTube and add it to the top of the description as
  `<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/VIDEO-ID" allowfullscreen></iframe>`.
- **Post it where horror players are:** r/feedthebeast, r/Minecraft (with the *Mods* flair),
  Minecraft horror Discord servers. Post the treeline or hallway image with one of the lines below.

### Lines to use

- There is something in this world with you.
- It was standing there the whole time.
- Don't go down the hallway.
- It is learning how to be you.
- Everyone left. Something stayed.
- It never appears while you're looking.
- Your friend can't see it. That's the worst part.
- WE WERE FOUR. THEN THREE.
- Sound on. Lights off. Give it twenty minutes.
- A horror film that nobody wrote, and it's different every time.
