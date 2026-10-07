# The Occupant

*A psychological horror mod for Minecraft Java Edition (Fabric): 1.20.1, 1.21.1, 1.21.11, 26.1.x, 26.2, 26.3 and the 26.4 snapshot.*

Something else is living in your world, and it is learning how to be you.

Scripted horror videos feel perfect because someone chose every moment: the footsteps start
right as you stop moving, the figure is standing exactly at the edge of your vision, the
knock comes when you are inside at night. The Occupant tries to do the same thing **live, for
every player, without a script**. Nobody gets the same story, but everybody gets a story that
feels directed.

> **All in one.** Fog, colour, sound, music, places, story and its own title screen are built in.
> You need Fabric API and nothing else: no shaders, no resource packs. See
> [Compatibility](#compatibility) for the few mods that get in its way.

> **Recording it?** The first screen asks how you're playing: answer **RECORDING** (click it, or
> press 2), then start recording on the title screen after it (click the way in, or press Enter). The whole story, ending included, in about forty minutes. Everything else a creator needs (what to expect and when,
> recording tips, commands for B-roll, thumbnails) is in **[the Creator Pack](docs/CREATORS.md)**.

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
| 1: Signs | ~3-18 min | Deniable things. Footsteps behind you that stop when you turn. A sound you know perfectly, coming from somewhere it cannot be: a creeper's fuse at your back at night, a door in a house with no door. Something standing at the very edge of the fog that you cannot quite resolve. One roll of thunder from a clear night sky. |
| 2: Presence | then ~12-22 min | It is closer and it is not hiding as well. A figure in the dark at the edge of your vision. A face at your window at night, a step back from the glass. Footsteps across your roof that stop right above you. The campfire goes out while your back is turned. Knocking at night. Breathing behind you. A sign you did not place. A fresh tunnel. Every animal in sight stops and stares at you. Someone with a name *almost* like yours joins the game; you make an advancement called *[Not Alone]*. You can't sleep: "there are monsters nearby". There aren't. |
| 3: Closer | then ~15-30 min | It follows you, moving only when you are not looking. You say things in chat you never typed. The lights flicker and it is standing in front of you. It is by your bed when you get home. It is behind you, and the screen goes quietly out. |
| 4: Hunt | from then on | The music stops. It is out there, looking at you. Then it runs. And once, on **the last night**, the fog closes right in and it is standing at the edge of it; every time you look away it is closer when you look back, until it is right in front of you. Then black, and a line, as on the first night. When the picture comes back the fog has lifted, and the story begins again, quieter. |

Acts need **both** time and experiences to advance, so you can't skip the story by hiding in a
lit base, and you can't get stuck in it forever either.

Something happens every minute or three; the first thing it tries is to be seen, far off, and
it is never long before it is seen again: every five minutes or so early on, every three by the
end. Things **follow on** from each other: the lights stutter, and when they settle something
breathes behind you; it knocks, and then it is at the window; it is seen far off, and a minute
later, closer. After every big scare there is a **calm** of a few minutes with only small,
deniable things.
And the Director builds on absence: the longer nothing has happened, the more it favours
something real, so the next thing lands once you have stopped listening for it.

There are **no pop-out scares with a loud noise**. A bang makes you jump and then laugh, which
discharges exactly the tension the rest of the mod spent an hour building. When it finally gets
to you, the sound drops away instead.

There is a **fog**, a real one: it begins a few blocks from you and thickens all the way out,
until at about eighty blocks you cannot see anything at all. It comes in ten blocks with each act,
to about fifty by the end, closer at night, and right round you while it is close, as if it
brings the fog with it. The sky goes into it too, so there is no clear horizon over a fogged
world, and the colour drains out towards grey; sunset and sunrise are only a dull glow through it. Far off, it stands **where it can only just be made out**: a grey shape
in the fog, never so deep in it that you could tell yourself it was a tree. And the animals know before you do:
when it is out there, the cows stop grazing and turn to look at it. Follow their eyes.

If it stands in plain view for **ten seconds and your crosshair never comes near it**, you are
told: *something is watching you.* Never the same words twice running, and never quite the
same way (a line on the screen, a thought, your own name in chat saying it), and less patient
the further the story has gone and the more often you have ignored it.

### The rules

Once the story has started, you are in its world, and you play it fair. Go through a portal and
you are back where you were before you stepped in: *It does not want you in there.* Switch to
creative or spectator and you are put back in survival: *It doesn't want you breaking the
rules.* (Not while the story is paused, not if you chose to leave it alone at the start, and not
once it has let you go.)

### Small things

- When it is close and you are not looking at it, you can hear **your own heartbeat**.
- In the cold, **your breath shows**. Underground, **your footsteps come back** a moment late,
  one step too many. Your **torch gutters** when it is near.
- Wake up and **the doors are open**, all of them. The item frames in your house have turned;
  the armour stands are **facing your bed**.
- In the snow there are **footprints** from the treeline to your door. None going away.
- Your dog and your cat stop and **stare at a corner behind you**.
- **Villagers will not open up** after dark once it is about. *Not tonight.*
- A sign with **your coordinates** on it. A long straight tunnel, and at the end of it, it.
- *Saving world...* in the corner. You did not save.
- Near a jukebox, the **radio** picks something up: static, and a voice that knows your name.
- On a server, **a friend says something in chat** that they never typed. Only you see it.
- The pause screen says things it should not. When you die, it lets you know it was there.
- Late in the story, the pause menu is not safe either: it has opinions about you leaving.
- There is **a score**: a low drone once the story has started, bowed glass in the second act,
  and late on, a muffled pulse when it is close. It drops away entirely when it gets to you.
- **It changes as the story goes on**: a little taller each act, leaning further.

### The endings

There are three, and which one you get depends on how you played it. Each is a different place
to wake up in when the black lifts:

- **Left behind** (you found the survivor's last camp: one page of the log says where it is, a
  couple of hundred blocks off, and it really is there). You wake by their fire, lit again, with
  their last page in your pocket. The fog is gone, and nothing follows you any more.
- **So it came in** (you spent the story shut indoors). You wake at home, by your bed, and every
  door in the house is open. The fog stays. It is inside now, and closer than it has ever been.
- **It knows how to be you** (anything else). The fog lifts, and the story begins again,
  quieter. It is still here, and there is one more page to find.

### Advancements

Small things noticed along the way, in their own tab (press L): *You're Not Alone*, *Something
Is Watching You*, *Who's There?*, *Check the Windows*, *Lights Out*, *Monsters Nearby*, *That's
Not What I Said*, *Saving World...*, *Bad Reception*, *Footprints*, *Somebody's Home*, *Dear
Diary*, *Every Word*, *Don't Go Down There*, *The Last Night*, and one for each ending (hidden
until you get it). None of them are for killing anything.

### The land itself

It is a little wrong, everywhere: the grass and the leaves have had some of the colour drained
out of them, and the woods have **dead trees** standing in them with not a leaf left, **fallen
trunks** going soft under moss, **bare patches** where nothing grows, and now and then a bone in
the earth. Nothing big: a walk passes one or two. As the story goes on, the colour goes out of
the picture a little more each act, and late at night something drifts in the air, like ash.

(The grass and leaf colours come from the mod's own colormaps. A resource pack with the game's
original `colormap/grass.png` and `foliage.png` puts them back, if you want.)

### The places

As you explore, you come across places people used to be, all empty:

- **The house**, alone or in an **abandoned village** of cottages, a well and worn paths. Its
  side hallway has no windows. The first time you step inside, it is standing in it. Houses
  keep turning up, away from spawn and from each other, until anyone comes within a chunk of
  one; after that, no more are built.
- **Ruined keeps**: a walled yard, towers on some of its corners, half fallen, a cold fire
  inside. Steps go up to the wall-walk, and the towers still standing have a ladder to the top.
- **Abandoned camps**: a tent (or what is left of one), logs round a dead fire, and a sign left
  for whoever came next.
- **Graveyards**: rows of graves behind a broken fence, one of them dug open.
- **Watchtowers**: a ladder from the ground up through a hatch to a lookout over the trees
  (in some the roof has come down), with someone's things still on it.
- **Chapels**: pews and an altar, one pew turned round to face the door; some with a bell tower,
  some with the roof fallen in.
- **Radio shacks**: a hut with an aerial, a set (a jukebox), a lever, and a note.
- **Lighthouses**, on the shore, their lamps long out: a door at the foot, a ladder up the
  inside, and a gallery round the top to step out onto.

None of them is built the same way twice. Each is put up in the wood that grows where it stands
(spruce in the north, oak and birch and dark oak in the woods) and weathered more or less; each
kind comes in different sizes and shapes; and the same kind is never found close to another.
- **Its lair**: a ring of trampled earth, bones, and a hole with a ladder down. Twenty blocks
  under is a hollow scraped out of the stone, with its things in it. Going down there is a
  moment of its own.

Their chests and barrels hold what was left behind, and the first time each is opened, the
next page of a **survivor's log** is in it: always the next page for you, whichever you open,
so it reads in order. The log keeps pace with the story: a few pages early on, a little while
between pages, and the last of them not until late, so you never read the ending first. It does
not end well, and when it runs out, the pages that are left are not in the same hand.

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

## Compatibility

**The Occupant is all in one.** Its fog, its colour, its sounds and music, its places, its
story and its own title screen are all built in. You do not need shaders, a resource pack, a
sound pack or anything else to make it look and feel like the videos: just Fabric API.

| | |
|---|---|
| **Fine** | Performance mods: Sodium, Lithium, ImmediatelyFast, Nvidium, FerriteCore, ModernFix, Entity Culling, C2ME, Chunky (pre-generating), and so on. Mod Menu, minimaps, JEI/EMI/REI. (CI runs the game with Sodium and checks it draws the land with this fog.) If you use **Sodium Extra**, leave its fog settings on: turned off, they take the fog away. |
| **Not recommended, but works** | **Shaders** (Iris / Oculus): most shader packs draw their own fog instead of the mod's, so the fog it stands at the edge of is lost, along with the mod's colour. Play without them for the intended look. |
| **Breaks things** | **World generation overhauls**: Terralith, Tectonic, Biomes O' Plenty, Oh The Biomes You'll Go, Regions Unexplored, William Wythers' Overhauled Overworld, Continents and the like. The house, the old places and its lair are built for the game's own terrain and may not appear, or appear buried or floating. **Distant Horizons / Voxy**: they draw land far past the fog, so the fog's edge, where it stands, means nothing. **Other horror mods** (The Man From The Fog, Cave Dweller, From The Fog...): two of them will step on each other's moments. |
| **Changes the opening** | Menu mods like FancyMenu can hide the mod's first screens. |

If you have any of these installed, the mod tells you once, before its first screen, which ones
and why (and writes it in the log on servers). It still runs; it just may not be at its best.

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
| `fogChunks` | `8` | How thick the fog is: about ten blocks of seeing for each (8 = thick at about 80 blocks) |
| `fogClosesIn` | `true` | The fog comes in as the story goes on (to about 50 blocks), at night, and when it is close |
| `keepToTheRules` | `true` | Once the story starts, no other dimensions (*It does not want you in there*) and no creative or spectator (*It doesn't want you breaking the rules*). Not while the story is paused |
| `fakeMessages` | `true` | Fake chat, join and advancement messages |
| `interruptSleep` | `true` | Occasionally "there are monsters nearby" |
| `requireAlone` | `true` | Visual encounters only when no other player is within `aloneRadius` |
| `hauntCreative` | `false` | Also haunt creative players (for recording) |
| `intensity` | `normal` | `subtle` (slower, fewer events), `normal`, or `relentless` (faster, more) |
| `soundOnly` | `false` | It is never seen: only heard. Everything visual is left out |
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
| `heartbeat` | `true` | Your heartbeat when it is close and you are not looking |
| `score` | `true` | The music (under the game's Music volume) |
| `atmosphere`, `titleScreen` | `true` | The darkening at night, and the mod's title screen |
| `askHowPlaying` | `true` | Ask before the title screen whether you are playing or recording (the Creator Cut) |

All of these can also be changed in game: **Options → The Occupant** on the pause screen.

## Recording a video

See **[the Creator Pack](docs/CREATORS.md)**. In short:

- **Answer RECORDING** on the first screen, before the title screen. The whole story, the last night included,
  fits in about forty minutes, with the quiet stretches cut short. (`/occupant creator on`
  turns it on for a world you already have.)
- **Play in survival.** It leaves creative players alone unless `hauntCreative` is on.
- **Sound on, headphones, night.** Most of it is heard before it is seen.
- **A shot at the edge of the fog:** `/occupant here 60` puts it sixty blocks in front of you.
- **The ending, on cue:** outdoors at night, `/occupant act <you> 4`, then
  `/occupant trigger <you> last_night`.
- **Photosensitive viewers:** `reduceFlashing` in `config/occupant-client.json` turns every
  flash into a slow fade.

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
/occupant creator [on|off]           this world's mode: the Creator Cut or the slow burn
/occupant reload                     reload config/occupant.json
```

Events: `footsteps`, `familiar`, `thunder`, `distant`, `cave_noise`, `distant_mining`, `echo`, `door`, `chest`, `torch_gone`,
`breath`, `knock`, `fake_join`, `sign`, `marker_torch`, `tunnel`, `corridor`, `watcher`, `window`, `roof`, `fire_out`, `stare`,
`pets`, `advancement`, `whisper`, `doppel_chat`, `teammate`, `radio`, `saving`, `footprints`, `turned`, `rearranged`,
`morning_doors`, `stalker`, `flicker`, `static`, `intruder`, `hallway`, `behind_you`, `wake`, `hunt`, `lair`, `last_night`.

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
