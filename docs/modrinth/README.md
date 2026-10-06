# Posting The Occupant on Modrinth

Everything here is ready to use: the icon, the gallery, the description and the files.

- `icon.png`: the project icon, 512x512.
- `gallery/`: the gallery images (`00-featured.png` is the one to feature).
- `stills/`: the untouched game frames the gallery is made from, if you want to grade them yourself.
- `description.md`: paste this into the description box.
- `../CREATORS.md` and `../creators/`: the **Creator Pack** (guide, thumbnails, Shorts covers).
  Link to it from the description if the repository is public, or attach the pictures to a
  post for creators.
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

Upload these in this order. Tick **Featured** on the first one only: it is the image people see
in search, so it decides whether they click.

| File | Title | Description |
|---|---|---|
| `00-featured.png` | The Occupant | There is something in this world with you. |
| `01-treeline.png` | The treeline | It was standing there the whole time. |
| `02-fog.png` | The edge of the fog | The fog comes in as the story goes on. It waits at the very edge of what you can see. |
| `03-village.png` | The village | Abandoned villages, empty houses, a well. Everyone left. Something stayed. |
| `04-ruin.png` | The keep | Ruined keeps with chests that still hold what people left behind. |
| `05-camp.png` | The camp | Cold campsites, and signs for whoever comes next. |
| `06-graves.png` | The graveyard | Two rows of graves. One of them has been dug open. |
| `07-face.png` | Its face | Seventeen feet tall. Ten legs. It is learning how to be you. |
| `08-the-first-screen.png` | The way in | The mod's own start screen. Create World takes you straight in. |

## 5. Versions

For each file in `dist/`, open **Versions → Create a version** (or drag the jar onto the page):

| File | Version number | Minecraft versions | Channel |
|---|---|---|---|
| `occupant-1.0.0+mc26.3.jar` | `1.0.0+mc26.3` | 26.3 | Release |
| `occupant-1.0.0+mc26.2.jar` | `1.0.0+mc26.2` | 26.2 | Release |
| `occupant-1.0.0+mc26.1.2.jar` | `1.0.0+mc26.1.2` | 26.1.2 | Release |
| `occupant-1.0.0+mc1.21.11.jar` | `1.0.0+mc1.21.11` | 1.21.11 | Release |
| `occupant-1.0.0+mc1.21.1.jar` | `1.0.0+mc1.21.1` | 1.21.1 | Release |
| `occupant-1.0.0+mc1.20.1.jar` | `1.0.0+mc1.20.1` | 1.20.1 | Release |
| `occupant-1.0.0+mc26.4-snapshot-2.jar` | `1.0.0+mc26.4-snapshot-2` | 26.4-snapshot-2 | **Alpha** |

For every version:

- **Version name:** `1.0.0 for <Minecraft version>`, for example `1.0.0 for 1.21.1`.
- **Loaders:** Fabric.
- **Dependencies:** add **Fabric API** as **Required**.
- **Changelog** (the same for all of them):

  ```
  1.0: the whole story.

  - Creator Cut: answer "Recording" on the first screen (before the title, off camera). The whole story, ending included, in about forty minutes.
  - Three endings that really are different: where you wake up depends on how you played it.
  - Advancements for the small things it does, and one for each ending. None for killing anything.
  - The survivor's log leads to their last camp, and it is really there.
  - New places: watchtowers, chapels, radio shacks, lighthouses, and its lair.
  - A live score that follows the story, and goes silent when it gets to you.
  - Small things: your heartbeat, your breath in the cold, footsteps that echo one too many, doors open in the morning, footprints in the snow, pets that stare at nothing, villagers who won't open up, a radio that knows your name, "Saving world...".
  - It changes as the story goes on.
  - Settings in game (pause menu → The Occupant): intensity presets, a sound-only mode, music, heartbeat, fog, reduce flashing.
  - Menus in English, Español, Français, Deutsch and Português (Brasil).
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
