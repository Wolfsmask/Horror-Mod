# The Occupant

*A psychological horror mod for Minecraft Java Edition (Fabric): 1.20.1, 1.21.1, 1.21.11, 26.1.x, 26.2, 26.3 and the 26.4 snapshot.*

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
| 0 | First ~3 min | Nothing. Let the player get comfortable. The first time in a world opens on black: *there is something in this world with you.* |
| 1: Signs | ~3-18 min | Deniable things. Footsteps behind you that stop when you turn. A sound you know perfectly, coming from somewhere it cannot be: a creeper's fuse at your back at night, a door in a house with no door. Something standing on a ridge a hundred blocks away that you cannot quite resolve. |
| 2: Presence | then ~12-22 min | It is closer and it is not hiding as well. A figure in the dark at the edge of your vision. A face at your window at night, a step back from the glass. Footsteps across your roof that stop right above you. Knocking at night. Breathing behind you. A sign you did not place. A fresh tunnel. Every animal in sight stops and stares at you. Someone with a name *almost* like yours joins the game; you make an advancement called *[Not Alone]*. You can't sleep: "there are monsters nearby". There aren't. |
| 3: Closer | then ~15-30 min | It follows you, moving only when you are not looking. You say things in chat you never typed. The lights flicker and it is standing in front of you. It is by your bed when you get home. It is behind you, and the screen goes quietly out. |
| 4: Hunt | from then on | The music stops. It is out there, looking at you. Then it runs. |

Acts need **both** time and experiences to advance, so you can't skip the story by hiding in a
lit base, and you can't get stuck in it forever either.

Something happens every minute or three; the first thing it tries is to be seen, far off. After
every big scare there is a **calm** of a few minutes with only small, deniable things.
And the Director builds on absence: the longer nothing has happened, the more it favours
something real, so the next thing lands once you have stopped listening for it.

There are **no pop-out scares with a loud noise**. A bang makes you jump and then laugh, which
discharges exactly the tension the rest of the mod spent an hour building. When it finally gets
to you, the sound drops away instead.

There is a **fog**. At first you can see about eight chunks; it comes in half a chunk with each
act, to about six by the end, a little more at night, and all the way in while it is close, as
if it brings the fog with it. Far off, it stands **just this side of where the fog begins**: the
furthest thing you can see, seen whole, never lost in it. And the animals know before you do:
when it is out there, the cows stop grazing and turn to look at it. Follow their eyes.

If it stands in plain view for **ten seconds and your crosshair never comes near it**, you are
told: *something is watching you.* Never the same words twice running, and never quite the
same way (a line on the screen, a thought, your own name in chat saying it), and less patient
the further the story has gone and the more often you have ignored it.

### The places

As you explore, you come across places people used to be, all empty:

- **The house**, alone or in an **abandoned village** of cottages, a well and worn paths. Its
  side hallway has no windows. The first time you step inside, it is standing in it. Houses
  keep turning up, away from spawn and from each other, until anyone comes within a chunk of
  one; after that, no more are built.
- **Ruined keeps**: a walled yard with corner towers, half fallen, a cold fire inside.
- **Abandoned camps**: a tent, logs round a dead fire, and a sign left for whoever came next.
- **Graveyards**: two rows of graves, one of them dug open.

Their chests and barrels hold what was left behind, and the first time each is opened, the
next page of a **survivor's log** is in it: always the next page for you, whichever you open,
so it reads in order. It does not end well, and when it runs out, the pages that are left are
not in the same hand.

### The entity

- **Only the haunted player can see or hear it.** Your friend standing next to you sees nothing,
  hears nothing, and did not get that "joined the game" message.
- It is built after **Father Fester** from Doctor Nowhere's catalogue: a long pale face that
  is mostly mouth, two small black holes for eyes, the jaw stretched far too long and red at
  the bottom, framed by long thin hair the colour of dried blood.
- Below that is a body **seventeen feet tall and as thin as paper**, carried on **ten long pale
  legs** that leave it all the way up the trunk.
- **It does not walk, and it does not crawl like a spider.** Each leg reaches out to the nearest
  real thing it can push against (the ground, a wall, a tree, a ceiling) and stays planted
  there. The body hangs between them, lags behind, and is then shoved after itself all at once,
  leaning into the shove. Legs left stretched too far let go and snap to a new hold. Out in the
  open a few legs have nothing to hold and hang limp beside it.
- In a cramped space it **folds down into it and braces its legs against the walls** rather
  than shrinking, so a corridor makes it look worse, not smaller.
- **It mostly does nothing, on purpose.** It stands, at the wrong height, for too long, and now
  and again its head is a few degrees further round than it was. What movement there is happens
  between frames, the way a thing looks in two photographs taken a second apart.
- Early in the story it keeps its head down, so the face is hidden and the shape is only a shape.
- Further away it reads as **much larger**, because there is nothing beside it to measure it
  against, and indoors it never stands up through the ceiling.
- A faint sheen keeps the face and legs **just visible in real darkness**: in a black forest
  they are the one thing you can half-see.
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

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.19 or newer** for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for that version in your `mods` folder.
3. Put the matching jar from the `dist/` folder of this repo in your `mods` folder:

| Minecraft | Jar | Java |
|---|---|---|
| 1.20.1 | `occupant-<version>+mc1.20.1.jar` | 17+ |
| 1.21.1 | `occupant-<version>+mc1.21.1.jar` | 21+ |
| 1.21.11 | `occupant-<version>+mc1.21.11.jar` | 21+ |
| 26.1.2 (26.1 and 26.1.1 are allowed but untested) | `occupant-<version>+mc26.1.2.jar` | 25 |
| 26.2 | `occupant-<version>+mc26.2.jar` | 25 |
| 26.3 | `occupant-<version>+mc26.3.jar` | 25 |
| 26.4-snapshot-2 | `occupant-<version>+mc26.4-snapshot-2.jar` | 25 |

The mod is needed on **both** the server and every client. The official launcher already ships
the right Java for each version. The snapshot jar is built against one snapshot and will likely
need rebuilding for the next.

## Config

`config/occupant.json` (server) is created on first launch:

| Setting | Default | |
|---|---|---|
| `enabled` | `true` | Master switch |
| `graceMinutes` | `3` | Minutes of play before anything happens (configs from 0.13 that still had the old `15` are moved to `3` once) |
| `storyPace` | `1.0` | How long each act lasts. `2.0` = slow burn, `0.5` = fast |
| `eventFrequency` | `1.0` | How often things happen |
| `worldChanges` | `true` | Doors, torches, tunnels, signs |
| `jumpscares` | `true` | "Behind you" scares, blackouts, stingers |
| `chases` | `true` | Act 4 hunts |
| `chaseDamage` | `0.0` | Damage if a chase catches you. `0` = it only scares you |
| `fog` | `true` | The fog over the world |
| `fogChunks` | `8` | How far you can see at first, in chunks |
| `fogClosesIn` | `true` | The fog comes in as the story goes on (to about six chunks), at night, and when it is close |
| `fakeMessages` | `true` | Fake chat, join and advancement messages |
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
| `fog` | `true` | Draw the story's fog (the server decides how thick; this only turns it off for you) |

## Commands (operators)

For testing, and for recording your own videos:

```
/occupant check                      why is nothing happening? (works in single-player, cheats or not)
/occupant here [distance]            put it in front of you right now (up to 128 blocks), no conditions
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
`knock`, `fake_join`, `sign`, `marker_torch`, `tunnel`, `watcher`, `window`, `roof`, `stare`, `advancement`, `whisper`,
`doppel_chat`, `stalker`, `flicker`, `static`, `intruder`, `behind_you`, `wake`, `hunt`.

If you just installed it and want to see something **immediately**: `/occupant here`. That one
never refuses. `/summon occupant:occupant` works too; it will haunt whoever is nearest for a
minute. Everything else in the story deliberately waits for the right moment.

Tip: to film a scene, `/occupant act @s 4`, go somewhere dark, and `/occupant trigger @s watcher`.
If it says it could not find a convincing place, that's intentional: try somewhere darker, or
with a longer view.

## Building

Requires Java 25 (Gradle runs on it; older versions are compiled for their own Java).

```
./gradlew build                 # 26.2, jar in build/libs/
./gradlew build -Pmc=1.20.1     # any version with a file in versions/
./gradlew runClient             # test in a dev client
./gradlew runGametest           # run the game tests on a headless server
```

One set of sources serves every version. `versions/<mc>.properties` says which Minecraft and
Fabric API to build against, and `versions/<group>/` holds what differs: whole files that replace
the shared ones (mostly the small `Compat` and `GuiCompat` helpers, and the renderer before
render states existed), and `replace.txt`, a list of plain renames for things that only changed
name. 1.20.1 builds on 1.21.1's group and adds its own.

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
