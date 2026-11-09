# THE OCCUPANT long video: handoff

Everything a new session needs to keep working on the long found-footage video. Read this file
first, then only the code a task needs. Work on branch `claude/laughing-hopper-3dtbgq`.

## What it is

A ~5-minute found-footage Minecraft horror video of the mod THE OCCUPANT, filmed by a Fabric client
game test in CI (no in-game footage from the user). The user makes the voice lines (ElevenLabs) from
`voice_lines.txt` and sends them; they go in `video-tools/voice/` and the edit mixes them in (see
"Voice lines"). Deliverables each round: the video (full quality, sent to the user), `voice_lines.txt`
(lines already recorded are marked `[recorded]`), `youtube.txt`, and a copy under 100 MB at
`video/the-occupant-long.mp4`. There is also a 37-second short (the ending, portrait), sent to the user.

## The user's standing rules (keep every one)

- **Repo:** only the video download goes in the repo (`video/the-occupant-long.mp4`), plus photos, plus
  this `video-tools/` folder (the user asked for it so a new chat can carry on). Nothing else.
- **No birch** trees, except the smallest amount.
- **No in-game screenshots or recordings** unless the user asks. Everything is filmed by CI.
- **Nothing that crashes the game or drops the connection.**
- **Username:** `vR4xxs_` everywhere (never "Wolfsmask").
- **The body under the camera** is the user's skin, `video-tools/skin/vR4xxs_.png`:
  - jacket, arms, legs and shoes in smooth black concrete. Never wool or concrete powder: they look fuzzy.
  - hair in coal block.
  - face black concrete, one grey eye pixel and two white ones (see `Person.java`).
- **Pacing:** never linger. The creature is not really seen before the haunted house. Before it, it
  appears at most as something tiny and unnoticeable (the far figure in the clearing).
- **No cuts on a fright.** No camera jump when something happens: the startle is off (`Fear.wide()`)
  and the mod's flinch is off while recording (`Reel.run`).
- **No looking away.** He never just looks away while the creature is in sight. Use a blink (tape
  glitch / black flash) or a hand over the lens.
- **Lowering the camera:** turned down smoothly, never dropped. His legs and feet show at the bottom.
  In scared or hurried moments he tips it slightly down and puts his hand over the lens (`Fear.cover`).
- **The haunted house:**
  - the creature is nowhere in the picture until he turns;
  - its whole face is in frame;
  - he goes over backwards within about 0.35 s;
  - the camera falls behind him; he is propped on his hands, turns, and his hand covers the lens.
- **Scene order:** the dead-animals pen stays 3rd, and the house comes late.
- **Animals:** move at natural speed and never clip.
- **Git:**
  - commits end with the session's co-author lines and contain no model names;
  - code and video commits carry `[skip ci]`;
  - recordings are started by dispatching the workflow (below);
  - never open a PR unless asked.

## Where things are

- **Game-side code** (all in `src/client/java/com/wolfsmask/occupant/client/test/`):
  - `Tape.java` holds every scene.
  - `Reel.java` holds:
    - `Fear`, the handheld camera: walk, hold, turnTo, swingTo, backAway, peer, lower, cover,
      blink, jump, dropped, lie;
    - `wood()`, the forest builder;
    - `record()`, which freezes the world and steps it 1 tick per frame.
  - `Walker.java` is the body under the camera; `Sitter.java` is him on the floor in the house;
    `Person.java` holds the skin's blocks and the ending's body.
- **Edit and tools** (`video-tools/`):
  - `edit/make_tape.py`: the editor. It grades, adds VHS cuts, the hand overlay, the blink, sound,
    titles and chapters. Its reel module is `edit/make_reel9.py`, with fonts in `edit/fonts/`.
  - `edit/watch.sh` and `edit/link.py`: fetch the recordings and link them.
  - `edit/deliver.sh`: builds the YouTube text and voice sheet, plus the repo copy.
  - `edit/qa.py`: contact sheets.
  - `sim/`: the camera of every scene worked out without the game (`build.py`), and a collision
    check (`check2.py`).
  - `harness/`: compiles `Walker`/`Sitter`/`Person` against stand-ins. `BodyScan` ray-casts the body
    into every frame to catch it covering the lens.
- **Sounds:**
  - The mod's own sounds are in `src/main/resources/assets/occupant/sounds`.
  - Game sounds come with each recording (`reel/sounds` on the `ci-results` branch).

## Scenes, in order (`Tape.run`, and `SCENES` in make_tape.py)

| Scene | Day | What happens |
|---|---|---|
| home | 1 | Into the cabin; the chest opens and shuts behind him. |
| camp | 2 | Abandoned camp; a whisper behind him. |
| pasture | 2 | The animals stop and stare, then are all dead. |
| well | 2 | He drops a stone, no splash; it flies back out onto the rim. |
| field | 2 | A tiny far figure; his name from the trees; it's gone. |
| intruder | 2, night | A "go to sleep" sign; his door open; it slams from inside. |
| visitor | 3 | A cow by his bed. |
| doorway | 3 | A lone door in the woods; he knocks, it knocks back, it opens. |
| radio | 3 | The set turns on with a voice. |
| house | 3 | The creature is in the wall gap; he falls, it's gone, hand over the lens. |
| chapel | 3 | The candles light themselves. |
| graves | 3 | A dug grave; a figure in the trees; a blink and it's gone. |
| knock | 3, night | Steps on the roof, knocks, the face at the window, a blink. |
| morning | 4 | His name on a grave. |
| mine | 4 | His torch is missing; a dug tunnel; the creature down the tunnel. |
| found | 4, night | The ending (`Reel.found`). |

## Voice lines

- **The long video:** `video-tools/voice/` holds the recordings, with readable names, and
  `voice_map.json`, which says which sheet line each one is: `{"scene", "line", "file", "gain"?}`.
  - `"line"` must be the line's text exactly as `LINES` in make_tape.py has it.
  - When a recording fits a line better reworded, change the text in `LINES` and in the map together.
  - One file can serve several lines; "Hello?" and "What was that?" are also used in the ending.
  - The edit (`voice.py`, `long_bus`) puts each line where the sheet puts it.
    - **The camcorder voice** (`camcorder`): 180 Hz to about 5 kHz with a lift at 2 kHz, the camera's
      automatic level (fast down, slow up), light distortion, tape wow, and the space they are in.
    - **Spatial:** the voice comes from their head and is heard at the camera (`voice_context` and
      `head_held` in make_tape.py, `spatial` in voice.py).
      - Each frame of a line, from the sim's camera and pose (`.raw`), it works out where the head is:
        - camera in hand: just behind it at eye level, further back when it points down;
        - camera dropped: where they are (`HEAD_DOWN`, e.g. sat on the house floor), reached over
          the fall.
      - The level follows that distance (`HELD` 0.32 blocks is the set level; at most 12 dB down).
        Further off is also darker, the room's answer stays the same, and the voice pans to their
        side.
      - So a line said while setting the camera down gets quieter as it goes down.
      - This needs the sim's `.csv`/`.raw` for the scene (run the sim first). A scene without them
        (the ending) falls back to a fixed distance.
    - **Per-line context** (`voice_context` in make_tape.py, from the sim camera and the marks):
      - indoors gets a small room, outdoors stays almost dry;
      - running, the camera held down, or the camera dropped (after `land`/`hit`) is further away:
        darker, more room, quieter;
      - after `cover` the voice is muffled.
      - A map entry can force it with `"ctx": {...}`.
    - The voice goes on after the rest is brought to -23 LUFS (`VOICE_GAIN`), so the effects keep
      their level.
  - **Ambience** (`ambience()` in make_tape.py), also added after the loudness step
    (`AMBIENCE_GAIN`) and dipping 6 dB under the voice:
    - wind in the trees (crickets at night); indoors a low room tone with the wind through the walls;
    - the tunnel's rumble in the mine; wind buffeting the mic when moving fast outside;
    - tape hiss throughout, and wind fading in under the title;
    - the pen stays near-silent after the animals stop, on purpose.
    - Levels: about -35 to -40 dB in the quiet stretches.
  - **Sound-only changes** (voice, ambience, mix): add `--remux=<previous full-quality mp4>` to the
    make_tape.py command. It rebuilds only the sound (about 5 min) and copies the picture as is. Then
    `deliver.sh OUT --remux` gives the repo copy the new sound without re-encoding the picture.
  - **Never two lines at once:** at least 0.8 s between lines (`GAP`).
  - **A line may start at most 0.4 s early** (`EARLY`) to finish before its tape stretch is cut.
    Otherwise it is LEFT OUT and the log says so; move its moment in `LINES` instead.
  - **Check placement cheaply:** add `--audio-only` to the make_tape.py command (no picture render)
    and read the `voice:` lines in the log.
  - **Few lines.** The user wants only the important ones: one or two per scene, silence between.
    The sheet was cut from 83 lines to about 26. Do not add more without being asked.
  - Recordings no longer in `LINES` stay in the folder, unmapped.
  - **Part of a recording:** a map entry can take only part of it (`"from"`/`"to"`, in seconds into
    the file) and shorten its pauses (`"squeeze"`: the longest pause in seconds) so it fits its
    moment. Find where the words fall with faster-whisper `word_timestamps=True`.
  - The user asked to drop the whispered "what was that?" everywhere, the short included.
  - **Map entry options:**
    - `"gap"`: seconds before the line, in place of `GAP`. The ending's panicked lines use 0.1.
    - `"past_end": true`: the line may run on past the tape's end. The voice is not cut with the
      tape, so the final scream plays into the black.
    - `"echo"`: the line drawn out at its end (`voice.echo`). No repeats (the user did not want an
      echo), only a smooth dark tail dying away over a couple of seconds, "AHHHhhh...". 1 is slight;
      the final scream uses 2.5, so it trails off into the dark after the cut.
  - **The ending (knocked down, the camera in the leaves):** he is 0.7 blocks from it. Shouted lines
    need a high `"gain"` (about 5), because the chain levels every take like talking.
  - **The short:** `short_map.json` entries take the same `from`/`to`/`gain`/`echo`, plus `"ctx"` for
    distance. Its ending lines are at times taken from the same take's marks (land 28.45 s, stab
    32.75 s).
  - **The mine:** no made-up drips (the user found them odd), only the low rumble. Ambience overall
    is at `AMBIENCE_GAIN` 0.7, and the radio's voice was raised (0.9).
  - Pass the folder as make_tape.py's third argument: `video-tools/voice`.
- **Sending new lines:** the user sends zips of ElevenLabs mp3s with no useful names. To work out
  which line each is:
  - transcribe them with faster-whisper (`pip install faster-whisper`; decode with ffmpeg to 16 kHz
    mono and pass the array, because its own decoder clashes with the installed PyAV);
  - match the text to the sheet;
  - give each a readable name, add it to the map, and render again.
- **The short:** `masters/the-occupant-short-novoice.mp4` is its voice-free master, and
  `masters/short_voice_at.json` holds where its lines go:
  `hello, what, seen1 (breath catches), seen (scream), no1, no2, no3, scream`.
  - `voice/short_map.json` maps those keys to files.
  - Run `python3 video-tools/edit/voice_short.py video-tools/masters/the-occupant-short-novoice.mp4 video-tools/masters/short_voice_at.json video-tools/voice OUT.mp4`.
  - Send OUT to the user. The short is not in the repo, only its master.

## Recording (CI)

1. Commit and push the code with `[skip ci]`.
2. Dispatch `build.yml` on the branch with input `tape`: `[keep] [tape:a,b,c]`, e.g. with the GitHub
   MCP tool `actions_run_trigger` (`run_workflow`, owner `wolfsmask`, repo `horror-mod`, ref
   `claude/laughing-hopper-3dtbgq`).
   - Group 3–4 scenes per run, about 1,100 frames; a run takes 15–25 min.
   - Five runs cover all 16 scenes: `home,camp,pasture,well` · `field,intruder,visitor,doorway` ·
     `radio,house,chapel` · `graves,knock,morning` · `mine,found`.
   - Always use `[keep]`, or a run wipes the other runs' clips.
3. Each finished run replaces the `ci-results` branch tip with a "Reel" commit. `reel/` holds:
   - `name.N.mp4`: 300-frame pieces at 20 fps, 1920×1080;
   - `name.0.txt`: marks, as frame and kind;
   - `name.0.roll`: the camera per frame.
4. Fetch: `bash video-tools/edit/watch.sh WORK 5` (WORK is any folder outside the repo), then
   `python3 video-tools/edit/link.py WORK`. It links the newest take of each scene into `WORK/clips`.
   - Check every scene's take is new (compare file hashes with the previous take). A cancelled run
     can also save clips.

## Checking before recording (cheap)

- `cd video-tools/sim && python3 build.py && javac SimTape.java && java SimTape && python3 check2.py`:
  frame counts, marks and camera of every scene, and the camera never within 0.15 of a block.
  - Add new scenes to the `scenes` list in `build.py` and to the boxes in `check2.py`.
- `bash video-tools/harness/build.sh && java -cp video-tools/harness/out com.wolfsmask.occupant.client.test.BodyScan video-tools/sim <scene>`:
  frames where the body covers more than 12% of the picture.
  - Expected: cover frames at the end of a scene, and legs when looking straight down.
  - Anything else is a bug.
- After recording, look at contact sheets of the key moments:
  `python3 video-tools/edit/qa.py WORK/clips <scene> out.jpg t1 t2 …`.

## Edit and deliver

```
python3 video-tools/edit/make_tape.py WORK/clips src/main/resources/assets/occupant/sounds <empty folder> OUT/the-occupant-long.mp4
bash video-tools/edit/deliver.sh OUT
```

- **Grade cache:** graded frames are cached in `WORK/clips/work`. After re-recording scenes, pass
  `--fresh=scene1,scene2`, or delete the folder.
- **Grade only:** `--grade-only` grades without rendering.
- **Output:** the edit writes `chapters.txt` and `voice_sheet.txt` beside the video; `deliver.sh`
  makes `youtube.txt`, `voice_lines.txt` and the repo copy (about 95 MB, two-pass).
- **Where scenes are configured** in `make_tape.py`:
  - `SCENES`: date, clock, exposure, act, day;
  - `PUNCH`: digital zooms;
  - `LINES`: voice lines at marks;
  - per-mark sounds: in `make_audio`;
  - `names`: chapter titles.
- **Finishing:** commit `video/the-occupant-long.mp4` with `[skip ci]`, push, and send the user the
  full-quality video, `voice_lines.txt` and `youtube.txt`.
- **Thumbnails** are staged in-game stills, not frames from the video (the user found frame grabs
  "generic, like a Canva presentation"). Run CI with `[keep] [tape:thumbs]`: `Tape.thumbs()` builds
  the house at night (their body on the floor, it in the wall, lit by hidden `light` blocks) and the
  glade (it towering over them, the moon behind, 1080×1920), and saves 4 angles of each as
  `client-test/thumb-long-0..3.png` and `thumb-short-0..3.png` on `ci-results`. Don't push while it
  runs or the screenshots aren't saved. Then
  `python3 edit/make_thumbs.py thumb-long-0.png thumb-short-1.png OUT` does the film-poster finish
  (a lit pool on the face, warm grade, bloom, lens fringes, vignette, grain, an IM Fell title with
  one red word, a Cinzel credit) and writes `thumbnail-long.jpg` ("IT WAS IN THE WALL") and
  `thumbnail-short.jpg` ("IT WAS BEHIND ME"). The chosen ones are copied into `video/`. The `pool=`
  numbers in `make_thumbs.py` say where the light falls if another angle is used. The short's
  YouTube text is in `edit/yt_short.txt`; the long's comes from `deliver.sh`.
- **Requirements:** Python 3 with numpy, pillow and imageio-ffmpeg; ffmpeg/ffprobe; a JDK for the
  sim and harness. Rendering needs about 15 GB free.

## Things learned the hard way

- **The picture lags the pose by one frame.** The edit shifts the camera data by 1.
- **Placing the creature (`Cinematic.place`):** it only turns AI off when the look's turn is not 0.
  Use `0.01f`, or it shakes as its AI moves it and the scene snaps it back.
- **Keep the creature out of view until it should be seen:** spawn it at
  `firstInView(poses, face, from, until)`.
  - Under a low ceiling it crouches (`OccupantFit`). Aim at its face where it actually is: about
    0.95 up in the house, not 1.9.
- **The body's facing (`Tape.Body`)**:
  - it follows the walking direction;
  - backing away (`backAway`/`stagger` set a flag in the pose), it faces the camera;
  - at a jump cut it snaps to the camera;
  - it is never more than 60° from the camera. Without these it walks into the lens as a black slab.
- **No dirt walls:** `wood()` puts a leaf thicket round the edge of every wood and trees across the
  ends of its paths.
- **Items:** natural item physics plays back correctly under tick stepping (the well's stone).
- **Lighting:** a vertical shaft gets skylight down it unless it is roofed.
- **Disk:** delete old `WORK/clips/work` caches when the disk fills. Background jobs stop when the
  machine restarts; just start them again.

## State at handoff

v14 is delivered: the v4 cut (4:54, 16 scenes) with the voice cut down to the important lines (about
26 on the sheet). 15 of them are recorded and mixed in with the camcorder voice; none overlap.
Ambience runs throughout. The short has "Hello?" and
"What was that?". `video/the-occupant-long.mp4` (about 95 MB) is the current cut.

v4 covered all of the user's earlier feedback:
- no jump on a fright;
- the house creature still, its whole face framed, and he goes over about 0.35 s after it shows;
- no dirt wall at the ends of the paths;
- the creature unseen before the house, except the tiny far figure in the clearing;
- two new creature-free scenes (the well, the door);
- the body no longer covers the lens when he backs away;
- the body in the skin's smooth charcoal.

Still to come from the user: the lines in `voice_lines.txt` not marked `[recorded]`. Also optional,
`radio.wav` and `name.wav`; until then the mod's whisper stands in for both. The ending's lines
("(scream)", "NO!", "(scream, cut off)") go in the short too: map them in `short_map.json` to
`seen`, `no2` and `scream`.

The last takes of every scene are in the `Reel` commit `13b72fb4` on `ci-results` (later runs replace
it). A render in a new session regrades everything from those clips, which takes about an hour of
machine time; send OUT after `deliver.sh`.
