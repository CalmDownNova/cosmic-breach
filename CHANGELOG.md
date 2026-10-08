# Changelog

## 1.1.0

Answers the second round of playtest feedback. Worlds from 1.0.x load, but 1.0.x clients cannot join a 1.1 server: every
player and the server need 1.1.0. A dedicated server needs `allow-flight=true`.

- Food and materials: each layer now gives you what it needs inside the mod, with new foods, materials and pickaxes
  that grow in the layer's own places, so nothing has to be carried in from the overworld.
- Layer two: bigger asteroids and closer stepping stones, with a few wide gaps left for the dash and for mounts.
- The flying armor knights: a dive you can read and answer. It tells for a second, drops beside you, cuts once (parry
  it), then hangs in sword reach for two seconds. A glint and a rising whine sound just before the cut lands. A dive that
  would press into rock is skipped. Their loot goes to whoever kills them, even over open sky.
- Mounts: parked mounts stay safe and out of harm, an item stows one, a wild mount can be lured, and a mount in
  trouble in the void is rescued.
- The second boss's arena has air vents that lift anyone who falls, so a fall into the void is no longer the end.
- Shrines: a shrine at every boss. Right click one to keep your place; a death anywhere in the mod's layers then keeps
  your whole inventory and experience, and you wake beside the shrine. Nothing can destroy a shrine, the Wither and the
  Ender Dragon included.
- The way back up: golden currents under the shrines carry you up to the layer above. Falling players are never caught.
- Every boss now speaks, with captions. A line never makes a boss hold back an attack.
- The Forge screen: full item tooltips everywhere, names that always fit, and tabs for weapons, armor, tools, mount gear
  and other (a tab shows only when it has recipes), remembered between visits.
- Render fixes: tier outlines on forged gear and the flying knights' blade rings.
- Credits: the voices are made with ElevenLabs under a paid plan with a commercial licence; CREDITS.md lists them.

## 1.0.3

- Zenith: holding right click now keeps you in the air with the foes it launches. You rise about 3 blocks and hang
  there for as long as they do (1.5 seconds), sinking slowly; let go, dash or plunge to drop. Coming down from it never
  costs fall damage, though a drop below where you started still does.
- Boss bars: after dying to a guardian, a respawn or a trip to another dimension no longer leaves the old fight's bar
  (and its music) on screen, and a fight that ends because its lair unloaded takes its bar with it. Single player and
  servers alike.
- No other changes. Worlds from 1.0.2 load as they are; every player and the server need 1.0.3.

## 1.0.2

- Licence: code MIT, assets All Rights Reserved.
- Credits: ElevenLabs attribution in the mod list; CREDITS.md and LICENSE inside the jar.
- Quieter server console on startup.
- No gameplay changes.
