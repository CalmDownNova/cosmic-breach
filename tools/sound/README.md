# Cosmic Breach sound set

Every sound in `src/main/resources/assets/cosmicbreach/sounds/` is made by the
Python scripts in this folder: synthesized, or shaped from a recorded take kept in
`eleven/` (the voice lines and a few effects; see `eleven.py`). The build never
downloads anything. Every random choice is seeded, so rerunning the build on the
same setup reproduces the .ogg files bit for bit (a file only changes in git when
its audio changes).

## Regenerate

Needs Python 3.10+, `numpy`, `scipy`, `Pillow` and `soundfile`
(`pip install numpy scipy pillow soundfile`), plus `ffmpeg` built with libvorbis on PATH.

```
python tools/sound/build.py                          # every sound (about 15 s)
python tools/sound/build.py combat/hit shardling/tell  # only events whose names start with these
python tools/sound/build.py --no-sheet               # skip the contact sheet
```

It writes:

- `src/main/resources/assets/cosmicbreach/sounds/<category>/<event>[_n].ogg`: mono, 44.1 kHz, Ogg Vorbis
- `tools/sound/manifest.json`: per event, its files (`cosmicbreach:combat/hit_1`, ...) and subtitle text; `"loop": true` marks the looping sound
- `tools/sound/preview/sheet.png`: waveform and spectrogram of every file, labelled with duration, peak and loudness
- `tools/sound/build/`: intermediate WAVs (git ignores it through the repo's `build/` rule)

The printed report lists each file's duration, sample peak, true peak, loudness
and target, and ends with a warning count. It should say `0 warning(s)`.

## Files

| File | What it holds |
|---|---|
| `dsp.py` | Low-level tools: envelopes, filters, noise with a moving spectrum, oscillators, soft clipper, transient limiter |
| `layers.py` | Sound ingredients: struck resonators (glass, bells), cracks, thumps, shatter, whooshes, reverb, formant choir, the seamless Shepard choir |
| `combat.py` | Recipes and the event list for `combat/` |
| `shardling.py` | Recipes and the event list for `shardling/` |
| `progression.py` | Recipes and the event list for `progression/` (the level-up chime, -17 LUFS) |
| `edges.py` | Recipes and the event list for `edges/`, the Binary Edges: paired metallic "tsching"s (a hiss of contact into a short inharmonic star-metal ring, F#6 for the right blade and D6 for the left), the Gyre's whirl, the cut, the Tether's spinning whirr, the bite, the reel home, the blink's "shff" and its A-E glass chime, the Orbit and the Twin Meteor's landing. Meteor -14, blink slash -15, hit -16, cross and gyre -17, swing, blink and orbit -18, throw and stick -19, chime -20, return -21 LUFS |
| `maul.py` | Recipes and the event list for `maul/`, the Comet Maul: a deep swing whoosh (the game pitches a charged release by its charge), the slam (a boom with a sub layer), the hit, the charge loop (pitch rises with the charge), the Gravity Well (a reversed sweep into a rising hum) and its Collapse (a sucked-in "thoom"). Slam and Collapse -12, hit -14, swing -16, well -17, charge -23 LUFS |
| `forge.py` | Recipes and the event list for `forge/` and `vanguard/`: the Astral Forge's anvil ring (a struck steel bar, a D major pentatonic chime and sparks) and its tier-up (a reversed swell into a deep D, A and F# bell chord); the Starfall Vanguard's Heavy Landing shockwave, Meteor Call's 1.5 s warning (a falling whistle and a swelling roar that end at the impact) and the impact (a boom with a sub, rock, then fire). Impact -11, shockwave -13, tier up -14, craft and warning -15 LUFS |
| `sets.py` | Recipes and the event list for `driftweave/` and `regalia/` (G2b), on D major pentatonic: the Afterimage appearing (star dust sizzling into shape, A6 then E7 glass notes) and striking (a thin slash, an F#6 glass ting and its fainter echo), Drift starting (air rushing up, a soft low release, a rising D, F#, A arpeggio and sparkle) and ending (the air sinking into a settling thump), an echo firing (a reversed swell into a distant sung D, F#, A with a bell and a long tail) and the Hymn of Alignment's ring (a sung D3, A3, D4, F#4 chord swelling over a deep bell, a high shimmer, a hall tail). Hymn -16, drift start and echo -17, afterimage -18, strike -19, drift end -20 LUFS |
| `colossus.py` | Recipes and the event list for `colossus/` and the boss loop `music/colossus` (G3): grinding crystal as it turns, a crown crystal's turn, the gold glint's ting, the slam (a deep glass chime on a thump), the sweep, Refraction's rising charge and glassy beam hum, the core hit, the Prism Burst, the Break, the Fracture's roar of breaking glass, the Shatter, the re-merge, the death's D major chord, the shards' chitter, tell, lunge and death, the Guardian Echo. The loop: 8 bars at 100 BPM in D minor, a 1.6 s tail, retriggered on the Vesper clock's bar lines by the client. Shatter -11, slam, core hit and fracture -12, music -18 LUFS |
| `aetheria.py` | Aetheria's ambient beds (`ambient/`, seamless loops the biomes play) and music (`music/`, 100 BPM, pentatonic, 28 bars; the Arrival cue) |
| `onboarding.py` | Recipes and the event list for `onboarding/`, the way in (W4): the Starfall's streak (rushing air, a falling glass whistle, sparkles shed along the way), its boom (the recorded distant meteor impact, `eleven/onboarding/meteor_boom`, for the deep boom and three seconds of rumbling earth, under the synthesized punch, crack and debris, then a D major crystal chord ringing out; heard to 128 blocks through `attenuation_distance` in sounds.json), the shard's hum, a ring opening, the Breach's wind (the recorded draught, `eleven/onboarding/breach_wind`, made a seamless 7.75 s loop: its last 0.25 s crossfaded into its start, gusts evened out, all circular; the client keeps it looping near an open Breach) and falling up. Boom -13, streak and ring -16, fall up -17, wind -21 (loop), hum -27 LUFS |
| `voice.py` | The Starfall's voice (A1), `echo/`: the seven recorded lines (`eleven/echo/`) shaped into the Choir's last hum: a 110 Hz high-pass, a +2 dB air shelf, a phrase leveller (whispered phrases 60% of the way up to the loudest, gain moving only in the pauses), a peak limiter at 14 dB over the line's programme loudness; the shard's crystal hum (D and A) swelling in over the 0.7 s before the first word and sinking 12 dB under the words; a shimmer (the words an octave up by STFT phase doubling, doubled 2.5 Hz flat and sharp, into its own reverb, 20 dB down); a long bright reverb (T60 2.8 s, 35 ms predelay) at a low wet. Every line -17 LUFS programme loudness (`speech=True`), Vorbis quality 6. `python tools/sound/voice.py` measures the encoded lines: loudness against the music and beds, the quietest phrase, peaks, the words over the rest, and every word onset (zero lag, rise kept, margin over the hum and reverb) |
| `crypt.py` | Recipes and the event list for `choir/` and `crypt/` (W6): the Choir Floor's eight pad notes (D major pentatonic from D4: a crystal bell over a sung body; subtitles "Chime, first pad" and on), the Conductor's call (a hushed choir swell), the metronome tick, the Discord (a semitone clash), the round and solve chords, the ring's turn; the Void Rift's crack and fall, the Rift Seal opening, the Gravity Plate's hum and the Piston's slam, the Chute's glint and impact. Solve -17, discord and the traps' impacts -18, pads -21, call -24, tick and glint -27 LUFS |
| `unsung.py` | Recipes and the event list for `unsung/` and the choir's music `music/unsung/` (G8): the three lines formant-synthesized in D minor at 100 BPM (Alto "ah", Tenor "oh", Bass "oo"), cut in 8-beat blocks per chord (D minor, B flat, G minor, A), sung or hummed ("mm"), plus a rising hum per voice for Harmonize's warning, 27 blocks the client starts together on each line; the attacks are the lines' notes (the Alto's on beats 0, 2, 4, 6, the Tenor's breath and swell on 1 and 5, the Bass's strikes on 0 and 4 and its octave drop on 7). Effects: the hymnal, the awakening, the lichen dimming, each mask's first note, a note forming, bursting and breaking, the inhale, the wave, the ripples' tell and thrum, the Bass rising and dropping, the gold glint, the porcelain tink and crack, the Break, a mask's cry, the circles lighting, the Harmonize stab, the last soft D major chord. Harmonize -10, drop and crack -12, sung blocks -18, hummed blocks -27 LUFS |
| `stalker.py` | Recipes and the event list for `stalker/` (G7): the whispers are the committed takes in `eleven/stalker/` (so_quiet, light_dying, there_you_are while it stalks; behind_you, dont_turn, stay before a Grasp) made darker and breathier (6% slower, 140 Hz to 5 kHz, a breath riding the words, a short dark room) at -25 LUFS programme loudness; the Rend's rising whisper and gold ting, the Rend, the Grasp, the mask's shriek, a Shadow Step's hush, hurt, and death (a sigh and a glass crack). Rend -16, death -17, grasp and shriek -19, glint -20, rend_tell and hurt -21, whispers -25, step -27 LUFS |
| `astrolabe.py` | Recipes and the event list for `astrolabe/` (G7): one crystal-and-bell chime on D5 the game pitches to every note of D major pentatonic (A4 to D6; `AstrolabeTunes`), the rings' flick, a bolt's landing, the Pocket Star kindling, its warm hum (a seamless 2 s loop on D3, A3, D4), its pulse, a swallowed shot, a mark's ting, the Constellation's beam, the charge (a seamless 1 s loop the game pitches up with the charge) and the Supernova's choir hit (a sung D major over a deep bell). Supernova -15, beam -18, chime and star_place -19, bolt_hit -20, hum -28 LUFS |
| `sanctum.py` | Recipes and the event list for `sanctum/` (W7): the Gate opening (leaves grinding apart, a hushed choir swell on D and A into a crystal shimmer) and staying shut (a damped metal knock and a souring minor second), an Eclipse Lock lighting (a deep bell and a rising D, F#, A of glass), the Throne Seal dissolving (a long descending rumble under a sung D major chord, embers crackling), the throne's hum (a beating low D with a thin overtone, about 3 s), the Breach throwing a player back (a rush up out of the void, a thump, a falling shimmer). Stair -12, lock -14, gate and rescue -15, refusal -18, hum -19 LUFS |
| `heliarch.py` | Recipes and the event list for `heliarch/` and the boss loops `music/heliarch_regent`, `music/heliarch_hollow`, `music/heliarch_collapse` (G9a): the six committed takes in `eleven/heliarch/` (intro, hollowing, nova, collapse, death, player_down) made into the Regent's voice (the words cleaned and levelled as in `voice.py`, a metal comb "hollow" 16 dB under, a D1 and D2 sub hum that swells with the words 22 dB under, a dark throne-room reverb 13 dB under) at -16 LUFS programme loudness, 7 LU over the loops; the committed effects `nova_charge` (under Nova's last five seconds) and `eclipse_drone` (the Collapse); synthesized in D: the assembly, the halo opening and closing, the hand rising, the gold glint, the slam and the parry, the sweep, the lance's tracking, lock and fire, the Corona Flare's warning and burst (G9c), the shed, the Break, the tear and the monoliths' slam, a pip lost, a monolith shattering, the beam's charge and burn, the lash, a tendril cut, the inversion, a Star Seed and its burst, Nova's blast and break, a crack and a fall, Solar Rain, the death, the Reliquary, the seal. The loops: 8 bars at 100 BPM from their downbeat with a 1.6 s tail (D major for the Regent; D minor, a heartbeat sub and a distant choir for the Hollow; driving D minor for the Collapse). Slam, monolith slam, Nova's blast and break -11; parry, Break, fall and death -12; most effects -13 to -15; the voice and rain -16; the loops -17.5 to -19; the halo's open and close -21 to -22 LUFS |
| `curios.py` | Recipes and the event list for `accessory/` (G6a), in D: an accessory fastening (a metal click and a D6, A6 ting), the Twin Comet Band's level air dash (a bright rush and a rising glass sparkle), the Leechstar Signet drinking (a reversed shimmer into a warm D and A hum), the Gravity Loop's well (a reversed sweep into a low rising hum), the Heart's nova (a boom with a sub and a D major bell burst), a Halo shard shattering, growing back (a soft A, D, F# of glass) and cutting, the black hole (air sucked in to a deep thoom), the Hourglass (a bell whose pitch sinks, sand hissing). Nova -13, black hole -15, well, shard and hourglass -17, equip, comet and leech -18, cut -20, regrow -23 LUFS |
| `mounts.py` | Recipes and the event list for `mount/` (G6b), in D: the Lumen Stag's hooves on crystal (a hoof's knock and a small glass ting, three variants), its call (a bugle climbing and falling through an open "ah" with a shimmer of the antlers), its hurt; the Drift Manta's song note (a sung "oo" on D4 that scoops up into the note like whale song, with a glass overtone; the game pitches it to the phrase's notes up to D5), its groan, the sour note of a missed answer (D against E flat, sagging); the Resonance Chime (a crystal bar on D5, pitched to the note it answers); Phase Blink (air torn open, a reversed glitter, a bright ting); a mount's trust given (a D major arpeggio of glass over a sung D). Call -16, blink -17, the rest -18 to -19, hooves -22 LUFS |
| `familiars.py` | Recipes and the event list for `familiar/` (G10), in D: summon (a reversed glass shimmer into a D and A chime), dismiss (a falling A to D chime into a lantern click), the stance tap, a Star Egg hatching (the shell crackling into a D major glass burst) and settling in the brazier, a familiar's hurt and death, the Emberwisp's strike, Scorch (a small flame whoosh) and Kindled (a rising sparkle into D and A), the Gravikin's hop, slam and taunt (a deep stomp, a stone gong, a rising hum), the Prism Moth's strike, glint (a D6 ting), Refract shattering and the cleanse (a rising D, F#, A, D of glass). Hatch -15, taunt -16, refract break -17, summon and death -18, hop -24 LUFS |
| `event.py` | The `Event` record: name, subtitle, variants, max length, loudness level, fade, loop, encoder quality, speech (levelled by programme loudness) |
| `analysis.py` | K-weighted loudness (loudest 100 ms, and gated programme loudness for speech), true peak, edge and loop-seam checks |
| `eleven.py` | ElevenLabs as a source of raw takes (kept in `eleven/`, every request in `eleven/ledger.jsonl`); sets load a take with `eleven.sample("category/name")`. The build never calls the network |
| `sheet.py` | The contact sheet |
| `build.py` | Render, master, level, encode, decode and check, write the manifest and the sheet |

## Tuning a sound

Each sound is one function in `combat.py` or `shardling.py` that mixes layers
with explicit dB gains. Change a number, run the build for that event, look at
the sheet. The event list at the bottom of each file sets, per event:

- `length`: the longest it may be. The build trims the silent tail (below -50 dB of its loudest moment) and fades the end, so most files come out shorter.
- `level`: loudness relative to `combat/hit`, in LU. Variants of one event are levelled to the same target.
- `variants`: one renderer per file. Variants differ by seed and a few parameters (pitch of the ring, peak timing, band).

Tuned chimes use D major pentatonic (D E F# A B), so cues that overlap in play
(crit chime, parry ring, charge ready, dodge, resonance sparkle) never clash.

## Mastering chain (build.py)

1. Remove DC (20 Hz high-pass), trim the tail below -50 dB, cap at `length`, 1 ms fade in, raised-cosine fade out.
2. Loudness = the loudest 100 ms, K-weighted (the LUFS weighting). 100 ms rather than a meter's 400 ms because most sounds are shorter than 400 ms.
   A spoken line (`speech=True`) is measured by its gated programme loudness instead (BS.1770: 400 ms blocks, -70 LUFS and -10 LU gates), since its loudest 100 ms is one stressed syllable.
3. Scale to `REFERENCE_LUFS + level` (hit = -15 LUFS). A lookahead limiter shaves transient needles, at most 6 dB, so nothing exceeds -1 dBTP (4x oversampled). If it cannot, the whole sound is turned down and the report says so.
4. Encode with `ffmpeg -y -i in.wav -ac 1 -ar 44100 -c:a libvorbis -q:a 5 -fflags +bitexact -flags:a +bitexact out.ogg`
   (the bitexact flags only fix the Ogg serial number and drop version tags), then decode the .ogg again and check it.

Loudness targets (LUFS, loudest 100 ms):

| combat | | shardling | |
|---|---|---|---|
| plunge_impact, parry | -12 | death | -14 |
| hit_crit | -12.5 | tell, hurt | -16 |
| charge_release | -13 | shard_burst | -18 |
| hit | -15 | lunge, spit | -19 |
| zenith | -16 | shard_tick | -21 |
| swing_heavy, charge_ready | -17 | needle_hit | -23 |
| swing_light, perfect_dodge | -18 | step | -27 |
| plunge_whistle, dash | -19 | | |
| whiff | -22 | | |
| resonance_full | -23 | | |
| charge_loop | -24 | | |

## Aetheria's beds and music (aetheria.py)

Beds are seamless loops the biomes play as their ambient sound: `ambient/reach` (24 s: high wind with a
wandering whistle, crystal chimes in D major pentatonic), `ambient/drift` (32 s: a drone on D and A whose
beating has whole cycles per loop, low air, two far "oo" glides for the whale), `ambient/deep` (28 s:
sub-bass at 36 and 43 Hz, rumble, formant whispers). Noise is cross-faded round the seam, events wrap,
filters and reverb are circular. Levels: -25 to -26 LUFS (loudest 100 ms).

Music follows Vesper's clock: 100 BPM (a beat is 0.6 s, 12 game ticks), 28 bars of 4 (67 s), pentatonic.
`music/reach` D major (bells, harp, pad, bass), `music/drift` A minor (marimba, flute, pad, far glides),
`music/deep` D minor (low bells, distant choir, a slow sub heartbeat), `music/arrival` (22 s, played once
per player on first entering Aetheria). Mono, streamed, Vorbis quality 1 to 2 so the four together stay
under 1 MB. Levels: -17.5 to -19 LUFS.

## The loop (combat/charge_loop)

A distant choir "ahh" in stacked fifths (like medieval organum) that rises
forever: a Shepard-Risset glissando. Every voice glides up a fifth per 2 s loop
while fading in at the bottom and out at the top under a fixed envelope, so at
the loop point each voice has become its neighbour. Phases are handed from voice
to voice and vibrato completes whole cycles, so the loop is exact to floating
point; `shepard_choir` renders past the end and raises an error if the overshoot
does not match the start. Filtering and reverb on the loop are circular, so they
keep it seamless.

Any Vorbis file has a little extra codec noise at its first and last few
milliseconds, and in a loop that noise repeats at the seam. At `-q:a 5` it
showed as a faint high-frequency tick (48 dB below the loop); at `-q:a 8` the
seam measures the same as the rest of the loop, so this one file uses quality 8
(`quality=8` on its event, 19 KB instead of 15 KB). Everything else is quality 5.

## Checks the build runs

- Decoded length equals the WAV length. Decoding uses libsndfile (the reference
  libvorbis decoder, which honours the end-of-stream granule position). ffmpeg's
  built-in Vorbis decoder is not used for this: on files that fit in one Ogg
  page (most of these) it drops or adds a partial block at the end, although the
  files themselves are correct.
- True peak at or below -1 dBTP after encoding.
- Loudness on target; variants of an event within 0.5 LU of each other.
- Silent edges: the last samples are below -60 dBFS. The first sample may carry
  a little Vorbis pre-echo from an onset 1 ms later, which is masked when that
  onset is at least 25 dB louder; anything else is flagged.
- Loop seam: the step from the last sample to the first against ordinary steps,
  high-frequency energy at the seam against the rest, and codec error at the
  seam against mid-loop.
