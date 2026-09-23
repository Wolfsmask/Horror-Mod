# The Occupant

*A psychological horror mod for Minecraft Java Edition 26.2 (Fabric).*

Something else is living in your world, and it is learning how to be you.

Scripted horror videos feel perfect because someone chose every moment: the footsteps start
right as you stop moving, the figure is standing exactly at the edge of your vision, the
knock comes when you are inside at night. The Occupant tries to do the same thing **live, for
every player, without a script**. Nobody gets the same story, but everybody gets a story that
feels directed.

---

## How it works

### The Director

A server-side "AI director" watches each player once per second: is it night, are they
underground, indoors, alone, standing still, in a fight, AFK? It keeps a **dread** value per
player that rises in the dark and when alone, and falls in daylight and in company.

When it is time for something to happen, the Director picks from events that **fit the
situation right now** (a knock needs a door and a night; a cave noise needs a cave), weighted by
where you are in the story. Then comes the important part:

> **Every event must find a convincing place to happen, or it does not happen at all.**
> A scare that looks wrong is worse than no scare. That is how it stays "perfect".

The Occupant only appears on solid ground with headroom, in the shadows, with a clear line of
sight to you. It only ever moves while you are not looking. It never appears inside walls, in
water, in daylight in open fields, or close enough to touch (unless that is the point).

### The story (per player, saved with the world)

| Act | Roughly when | What happens |
|---|---|---|
| 0 | First ~15 min | Nothing. Let the player get comfortable. |
| 1: Signs | ~15-65 min | Deniable things. Footsteps behind you that stop when you turn. A sound you know perfectly, coming from somewhere it cannot be: a creeper's fuse at your back at night, a door in a house with no door. Something standing on a ridge a hundred blocks away that you cannot quite resolve. |
| 2: Presence | then ~40-70 min | It is closer and it is not hiding as well. A figure in the dark at the edge of your vision. Knocking at night. Breathing behind you. A sign you did not place. A fresh tunnel. Someone with a name *almost* like yours joins the game. You can't sleep: "there are monsters nearby". There aren't. |
| 3: Closer | then ~45-80 min | It follows you, moving only when you are not looking. You say things in chat you never typed. The lights flicker and it is standing in front of you. It is by your bed when you get home. It is behind you, and the screen goes quietly out. |
| 4: Hunt | from then on | The music stops. It is out there, looking at you. Then it runs. |

Acts need **both** time and experiences to advance, so you can't skip the story by hiding in a
lit base, and you can't get stuck in it forever either.

After every big scare there is a **long calm** of 12-20 minutes with only small, deniable things.
And the Director builds on absence: the longer nothing has happened, the more it favours
something real, so the next thing lands once you have stopped listening for it.

There are **no pop-out scares with a loud noise**. A bang makes you jump and then laugh, which
discharges exactly the tension the rest of the mod spent an hour building. When it finally gets
to you, the sound drops away instead.

### The entity

- **Only the haunted player can see or hear it.** Your friend standing next to you sees nothing,
  hears nothing, and did not get that "joined the game" message.
- It is **far too tall and far too thin, and the colour of old bone**: a small blank head with
  two black pits and nothing else in its face, a neck twice the length of a neck, a ribcage you
  can count, and legs that are more than half its height. Early in the story it is under a dark
  shroud, so a shape on a hill cannot be identified. Later the shroud is gone.
- **It mostly does nothing, on purpose.** It does not lunge, gesture or posture. It stands, at
  the wrong height, for too long, and now and again its head is a few degrees further round than
  it was. What movement there is happens between frames, the way a thing looks in two photographs
  taken a second apart. Its mouth only opens when it is already too late to matter.
- Further away it reads as **much larger**, because there is nothing beside it to measure it
  against. Nobody ever sees both sizes at once.
- A faint sheen keeps it **just visible in real darkness**, so there is always something to
  half-see over the treeline.
- It cannot be killed, farmed, trapped or pushed. Hit it and it is simply gone.
- It is **never saved to disk**, and it removes itself if nothing is controlling it. You will
  never find it standing around in an old save.

### Built not to break

- Every event runs inside error handling. If something ever goes wrong it is logged, cleaned up
  (no leftover entities), and after three failures that one event is switched off, never the game.
- The Occupant never loads chunks, never digs into anything but natural stone, never breaks into
  water or lava, never removes torches near your bed or outside caves, and never places anything
  over existing blocks.
- It backs off when you are in a fight, in a menu, riding, flying, in water, AFK, or low on health.
- CI builds the mod and runs **game tests on a real headless server** on every push:
  - every event is forced on a player, and the test fails if anything throws or an Occupant is left behind;
  - an "arena" test builds an open field at night, a cave and a house, and fails unless each event
    designed for that setting actually finds its place there;
  - it cannot be hurt, removes itself when nothing controls it, and story progress survives a save and load.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.19 or newer** for **Minecraft 26.2**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2 in your `mods` folder.
3. Put `occupant-<version>+mc26.2.jar` (from the `dist/` folder of this repo) in your `mods` folder.

The mod is needed on **both** the server and every client. Minecraft 26.2 runs on Java 25, which
the official launcher already includes.

## Config

`config/occupant.json` (server) is created on first launch:

| Setting | Default | |
|---|---|---|
| `enabled` | `true` | Master switch |
| `graceMinutes` | `15` | Minutes of play before anything happens |
| `storyPace` | `1.0` | How long each act lasts. `2.0` = slow burn, `0.5` = fast |
| `eventFrequency` | `1.0` | How often things happen |
| `worldChanges` | `true` | Doors, torches, tunnels, signs |
| `jumpscares` | `true` | "Behind you" scares, blackouts, stingers |
| `chases` | `true` | Act 4 hunts |
| `chaseDamage` | `0.0` | Damage if a chase catches you. `0` = it only scares you |
| `fakeMessages` | `true` | Fake chat and fake join messages |
| `interruptSleep` | `true` | Occasionally "there are monsters nearby" |
| `requireAlone` | `true` | Visual encounters only when no other player is within `aloneRadius` |
| `hauntCreative` | `false` | Also haunt creative players (for recording) |
| `signMessages`, `chatLines` | | What it writes and says. `{player}` and `{day}` work in signs |
| `screenWhispers` | `true` | Lines of text that surface on the player's screen |
| `whisperLines` | | What those lines say. `{player}` works |
| `debug` | `false` | Log the Director's decisions |

`config/occupant-client.json` (each player):

| Setting | Default | |
|---|---|---|
| `reduceFlashing` | `false` | **Photosensitivity:** turns hard flashes and flicker into slow fades |
| `screenStatic` | `true` | Analog static when it is near |
| `screenText` | `true` | The lines of text that surface on screen |

## Commands (operators)

For testing, and for recording your own videos:

```
/occupant check                      why is nothing happening? (works in single-player, cheats or not)
/occupant here [distance]            put it in front of you right now, anywhere, no conditions
/occupant status [player]
/occupant trigger <player> <event>   start an event now (it still needs a valid place to happen)
/occupant act <player> <0-4>         jump to a point in the story
/occupant dread <player> <0-100>
/occupant pause <player> / resume <player>
/occupant stop <player>              end whatever is happening
/occupant reset <player>             start the story over
/occupant reload                     reload config/occupant.json
```

Events: `footsteps`, `familiar`, `distant`, `cave_noise`, `distant_mining`, `door`, `chest`, `torch_gone`, `breath`,
`knock`, `fake_join`, `sign`, `marker_torch`, `tunnel`, `watcher`, `whisper`, `doppel_chat`,
`stalker`, `flicker`, `static`, `intruder`, `behind_you`, `wake`, `hunt`.

If you just installed it and want to see something **immediately**: `/occupant here`. That one
never refuses. `/summon occupant:occupant` works too; it will haunt whoever is nearest for a
minute. Everything else in the story deliberately waits for the right moment.

Tip: to film a scene, `/occupant act @s 4`, go somewhere dark, and `/occupant trigger @s watcher`.
If it says it could not find a convincing place, that's intentional: try somewhere darker, or
with a longer view.

## Building

Requires Java 25.

```
./gradlew build          # jar in build/libs/
./gradlew runClient      # test in a dev client
./gradlew runGametest    # run the game tests on a headless server
```

The body and its texture are generated by `tools/generate_model.py`; the sounds, screen static and
icon by `tools/generate_assets.py` (`pip install numpy pillow soundfile`).
Replace any file in `src/main/resources/assets/occupant/` with your own art or recordings.

## Project layout

```
src/main/java/.../occupant/
  director/          the Director, per-player story state, pacing
  director/events/   every event (one class each)
  entity/            the Occupant entity
  util/              line-of-sight, spot finding, player-only sounds
  command/           /occupant
  test/              game tests
src/client/java/.../occupant/client/
  render/            model, renderer, face and eyes
  ScreenEffects      blackout, flicker, static
tools/generate_assets.py
```
