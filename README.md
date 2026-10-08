# Cosmic Breach

An action-RPG dimension mod for **NeoForge 1.21.1**, built by Claude Opus 5.5 in Claude Code.

A star falls on your first night. Build a ring of copper around it, step into the hole it opens, and fall *up* into
Aetheria: a shattered sky realm stacked in four layers, each guarded by a boss, with combat rebuilt around dashes,
parries and telegraphs you can read.

> **Free to play, include in modpacks (link to the Modrinth page, don't re-upload), and make videos and streams,
> monetized ones included.** Code is MIT; assets are All Rights Reserved (see [Licence](#licence)).

This README covers the first layer and the first boss. The other **three layers and bosses** are left for you to find,
and the source is spoiler territory: read the code after you've played, if you care.

## Download

Download: [latest GitHub release](https://github.com/CalmDownNova/cosmic-breach/releases/latest) (Modrinth page coming soon). Requires [GeckoLib](https://modrinth.com/mod/geckolib),
[playerAnimator](https://modrinth.com/mod/playeranimator), [Curios API](https://modrinth.com/mod/curios) and
[GuideME](https://modrinth.com/mod/guideme), on NeoForge 21.1.252 or newer. Install it on the client and the server.

On a dedicated server, set `allow-flight=true` in `server.properties`. The mod's movement (dashes, mounts, the fall up
through the ring) can look like flying to vanilla's anti-fly check, and without the setting players get kicked.

## What's in layer one

- **The Starfall.** At your first sunset a Starfall Shard lands nearby under a beam of light. It gives you the Starfall
  Codex, the in-game guide.
- **The Breach Ring.** Eight copper-and-stone frames in a ring; use the shard on it and fall up. No iron or diamonds
  needed, so any seed gets there in about 20 minutes.
- **The Upper Reach.** Floating islands in daylight: the Shattered Spires and the Sunfield Terraces, Lumen Stags to
  tame and ride, and packs of Shardlings that will beat a vanilla sword.
- **The Astral Forge.** One station that tiers up. Forge Meridian and the Starfall Vanguard armour.
- **The Prism Colossus.** A crystal giant at the top of the Crown Spire: parryable slams, a sweep you dash through,
  and a laser that bounces between the arena's crystals, which you can turn back into its core. It shatters into three
  shards at the end, and they have to die fast.

Controls: dash Left Alt, parry V, set ability Z, attunement screen K. All rebindable under Options, Controls, Key
Binds, "Cosmic Breach". Loot, XP and attunement are per player; it's built for co-op and tested on a dedicated server.

## Building from source

Java 21. From the repository root:

```powershell
.\gradlew.bat build releaseMods
```

`build\release\mods\` then holds the mod jar and the four library jars it was built and tested against.
`.\gradlew.bat test` runs the unit tests.

## How it was made

Claude Opus 5.5, working in Claude Code, wrote the design document, the Java code, and the Python tools that generate
every texture, model, animation and most of the audio, then tested its own work in a hidden game client that plays
scripted scenarios and takes screenshots. It ran for 54 hours ($2,032 of usage at API prices, on a Max plan). Nate
supplied the idea and the playtesting.

- `src/`: the mod.
- `tools/art`, `tools/anim`, `tools/sky`: generators for textures, models and animations (Python, numpy, Pillow).
  Outputs are deterministic; rerun a script instead of hand-editing its output.
- `tools/sound`: the sound set, synthesized in Python, plus the ElevenLabs source takes.
- `scripts/`: the test harness and packaging helpers.

## Credits

Voice lines were made with **ElevenLabs v4** and some sound effects with **ElevenLabs** ([elevenlabs.io](https://elevenlabs.io)).
Everything else, the libraries and their licences: [CREDITS.md](CREDITS.md).

Minecraft is a trademark of Mojang Studios. This is not an official Minecraft product and is not approved by or
associated with Mojang or Microsoft.

## Licence

- **Code** (Java, shaders, build files, the Python and shell tools): [MIT](LICENSE).
- **Assets** (textures, models, animations, sounds, music, text): All Rights Reserved. Don't re-upload them,
  redistribute them outside modpacks, or sell them.
- **Free to play, include in modpacks (link to the Modrinth page, don't re-upload), and make videos and streams,
  monetized ones included.**
- **Excluded from the MIT grant:** the ElevenLabs audio (credited, covered by ElevenLabs' terms) and
  `assets/minecraft/shaders/include/fog.glsl`, a modified Minecraft file that stays under Mojang's terms.

Details in [LICENSE](LICENSE) and [CREDITS.md](CREDITS.md).

## Spoilers

If you get further than the first boss, please keep the lower layers out of issue titles.
