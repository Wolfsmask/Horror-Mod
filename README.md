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
| 1: Signs | ~15-65 min | Deniable things. Footsteps behind you that stop when you turn. Cave sounds. Someone mining inside the rock nearby. A door creaks open. A chest opens behind you. A torch you placed is gone. |
| 2: Presence | then ~40-70 min | First sightings: a figure far away in the dark, in the corner of your eye, gone when you look. Knocking on your door at night. Breathing right behind you. A sign you did not place. A fresh 2x1 tunnel. Someone with a name *almost* like yours joins the game. You can't sleep: "there are monsters nearby". There aren't. |
| 3: Closer | then ~45-80 min | It follows you, moving only when you are not looking. You say things in chat you never typed, sometimes things you said an hour ago. The lights flicker, and between flickers something is standing in front of you. It is standing by your bed when you get home. It is right behind you, and when you turn around, the screen cuts to black. |
| 4: Hunt | from then on | The music stops. It is standing out there, looking at you. Then it runs. |

Acts need **both** time and experiences to advance, so you can't skip the story by hiding in a
lit base, and you can't get stuck in it forever either.

After every big scare there is a **long calm** of 12-20 minutes with only small, deniable
things. Tension and release is what makes it scary instead of exhausting.

### The entity

- **Only the haunted player can see or hear it.** Your friend standing next to you sees nothing,
  hears nothing, and did not get that "joined the game" message.
- It is **a mass of faces**: a hunched, wet black column of people fused into one thing, with ten
  faces pressing out of it at angles no neck could make, each with its own jaw. It has no legs.
  It ends in a shroud that drags along the ground, and two long arms with two-jointed fingers.
- Every face **moves on its own**: its own count for when it wakes, its own moment to turn and
  look at you, its own speed of working its jaw. Nothing about it is ever in unison, and it moves
  in held, snapping poses, like stop-motion footage.
- Early in the story only the faces near the top are uncovered, and they are asleep; from a
  distance it is a tall, still, hunched shape. Later they are all awake, all looking at you, and
  when it hunts you they are all screaming.
- It has ~50 moving bones. Its body and its texture are generated together by
  `tools/generate_model.py`, so the two can never disagree.
- **Words surface on your screen**: short lines out of the dark, the way someone writes on the
  walls of a room they cannot leave. They never appear in the chat log, so there is nothing to
  scroll back to and check.
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

Events: `footsteps`, `cave_noise`, `distant_mining`, `door`, `chest`, `torch_gone`, `breath`,
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
