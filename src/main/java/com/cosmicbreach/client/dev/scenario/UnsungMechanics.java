package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.guardian.unsung.UnsungMusic;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.guardian.unsung.ChoirArena;
import com.cosmicbreach.guardian.unsung.SongNote;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungMask;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.UnsungRegistry;
import com.cosmicbreach.guardian.unsung.UnsungSong;
import com.cosmicbreach.guardian.unsung.Voice;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.voice.Echo;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.world.LayerAttunement;
import com.cosmicbreach.world.VesperClock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Unsung's mechanics with real inputs (part {@code unsung}): a level 34 player in the Choir Regalia with the Comet
 * Maul (T2). The hymnal wakes the choir; hits on a mask that isn't singing ring off; the song passes on the Vesper
 * clock's 8-beat lines; each mask's attack answered on its beat (a dash through a Homing Note and a swing that breaks
 * one, a jump over the Sweeping Wave and one taken standing, Ground Ripples thrown and braced, a Bass Drop parried);
 * a Harmonize survived inside a lit circle and one taken outside (Silence); the masks broken in the order Bass, Tenor,
 * Alto with real swings and the music thinning as each line leaves; the kill's rewards, the Breach Sanctum and the
 * Starfall's line; a repeat kill by a Guardian Echo with its Silent Sigil; the reset when the floor stands empty.
 *
 * <p>Built to hold on a busy machine, as the choir scenario: every input that must meet a beat is pressed by the
 * integrated server's tick (read each client tick), never by client frames, and every answer is checked against what
 * the server recorded (the attacks' landing ticks, the damage and where the player stood).
 */
final class UnsungMechanics {
    /** Kinds of the choir's hits on the player, as the listener sorts them. */
    static final String[] KINDS = {"note", "wave", "ripple", "drop", "harmonize", "other"};

    private final UnsungScenario scenario;
    private final List<String> summary;
    final List<double[]> taken = new CopyOnWriteArrayList<>();   // {kind, amount, tick, feetOverGround}
    final AtomicInteger parries = new AtomicInteger();
    final AtomicInteger perfectDodges = new AtomicInteger();
    private final long[] mark = new long[12];
    private final double[] measured = new double[12];
    private final List<KeyMapping> release = new ArrayList<>();
    private int tries;

    UnsungMechanics(UnsungScenario scenario, List<String> summary) {
        this.scenario = scenario;
        this.summary = summary;
    }

    // ------------------------------------------------------------------ what the test hears from the server

    void listen() {
        com.cosmicbreach.combat.server.CombatHooks.register(new com.cosmicbreach.combat.server.CombatHooks.Hook() {
            @Override
            public void onCombatEvent(ServerPlayer player, com.cosmicbreach.combat.PlayerCombat combat, com.cosmicbreach.combat.core.CombatEvent event) {
                if (event instanceof com.cosmicbreach.combat.core.CombatEvent.PerfectDodge) {
                    perfectDodges.incrementAndGet();
                } else if (event instanceof com.cosmicbreach.combat.core.CombatEvent.ParrySucceeded) {
                    parries.incrementAndGet();
                }
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class,
                event -> {
                    if (!(event.getEntity() instanceof ServerPlayer p)) {
                        return;
                    }
                    var src = event.getSource();
                    int kind;
                    if (src.is(UnsungRegistry.NOTE_DAMAGE)) {
                        kind = 0;
                    } else if (src.is(UnsungRegistry.HARMONIZE_DAMAGE)) {
                        kind = 4;
                    } else if (src.getEntity() instanceof UnsungMask m) {
                        double impact = m.attackImpact();
                        kind = impact == UnsungMoves.WAVE_IMPACT ? 1 : impact == UnsungMoves.RIPPLE_IMPACT ? 2 : impact == UnsungMoves.DROP_IMPACT ? 3 : 5;
                    } else {
                        return;
                    }
                    double over = p.getY() - UnsungScenario.arena().groundY(p.getX(), p.getZ());
                    taken.add(new double[] {kind, event.getNewDamage(), p.level().getGameTime(), over});
                });
    }

    private int takenOf(int kind) {
        int n = 0;
        for (double[] t : taken) {
            if ((int) t[0] == kind) {
                n++;
            }
        }
        return n;
    }

    private double lastOf(int kind) {
        for (int i = taken.size() - 1; i >= 0; i--) {
            if ((int) taken.get(i)[0] == kind) {
                return taken.get(i)[1];
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the whole part

    void steps(Steps steps, Minecraft mc) {
        listen();
        equip(steps, mc);
        wakeByHymnal(steps, mc);
        rotation(steps, mc);
        notes(steps, mc);
        wave(steps, mc);
        ripples(steps, mc);
        drop(steps, mc);
        harmonize(steps, mc);
        breaking(steps, mc);
        rewards(steps, mc);
        repeatKill(steps, mc);
        reset(steps, mc);
    }

    /** Level 34, Power 20, Agility 4, Resilience 12 of its own, the Choir Regalia on, the Comet Maul (T2) in hand. */
    void equip(Steps steps, Minecraft mc) {
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach debug level 34")
                .command("cosmicbreach debug stats 20 4 0 12")
                .waitTicks(2)
                .command("clear @s")
                .command("item replace entity @s armor.head with cosmicbreach:choir_regalia_circlet")
                .command("item replace entity @s armor.chest with cosmicbreach:choir_regalia_vestment")
                .command("item replace entity @s armor.legs with cosmicbreach:choir_regalia_tassets")
                .command("item replace entity @s armor.feet with cosmicbreach:choir_regalia_sabatons")
                .command("cosmicbreach give comet_maul")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .command("gamerule naturalRegeneration true")
                .waitUntil("the Comet Maul is in hand", 60, () -> mc.player.getMainHandItem().is(com.cosmicbreach.registry.ModItems.COMET_MAUL.get()))
                .waitUntil("level 34 in the Regalia", 60, () -> ServerQuery.ask(p -> Attunements.of(p).level() == 34 && p.getArmorValue() >= 18))
                .log("player", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "player: level %d, armor %d, toughness %.0f, %s",
                        Attunements.of(p).level(), p.getArmorValue(),
                        p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS),
                        com.cosmicbreach.progression.ProgressionStats.of(p))))
                .run("note the player", () -> summary.add(ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "player: level 34, Choir Regalia (armor %d), Comet Maul T2, %s", p.getArmorValue(),
                        com.cosmicbreach.progression.ProgressionStats.of(p)))));
    }

    /** An unattuned player opens the hymnal: the choir wakes. */
    private void wakeByHymnal(Steps steps, Minecraft mc) {
        steps.run("up to the hymnal", () -> {
                    int[] a = UnsungScenario.layout().altar();
                    Vec3 stand = new Vec3(a[0] + 0.5, a[1], a[2] + 0.5).add(UnsungScenario.axis().scale(1.8));
                    UnsungScenario.tp(mc, stand.x, stand.y, stand.z, a[0] + 0.5, a[1] + 0.8, a[2] + 0.5);
                })
                .waitTicks(3)
                .run("an empty hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[8].getKey()))
                .waitTicks(2)
                .run("look at the hymnal", () -> {
                    int[] a = UnsungScenario.layout().altar();
                    UnsungScenario.lookAt(mc, new Vec3(a[0] + 0.5, a[1] + 0.8, a[2] + 0.5));
                })
                .waitTicks(2)
                .check("not yet attuned to the Breach Sanctum", () -> ServerQuery.ask(p -> !LayerAttunement.hasSanctum(p)))
                .press(mc.options.keyUse)
                .waitUntil("the hymnal wakes the choir", 40, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.INTRO))
                .check("250 health a mask for one player, one bar of 750", () -> UnsungScenario.ask(u -> Math.abs(u.totalMaxHealth() - 750f) < 0.01f
                        && u.masks().values().stream().allMatch(m -> Math.abs(m.getMaxHealth() - 250f) < 0.01f)))
                .check("the first line is an 8-beat line of Vesper's clock, 8 to 15 beats away", () -> UnsungScenario.ask(u -> {
                    long start = u.fightStart();
                    long now = u.level().getGameTime();
                    return VesperClock.beat(start) % 8 == 0 && start % 12 == 0 && start - now >= 7 * 12 && start - now <= 16 * 12;
                }))
                .run("back to the Maul", () -> KeyMapping.click(mc.options.keyHotbarSlots[0].getKey()))
                .run("onto the choir floor", () -> {
                    Vec3 at = UnsungScenario.fromLocal(10.5, 0.0, 0.0);
                    UnsungScenario.tp(mc, at.x, UnsungScenario.arena().floorY(), at.z, UnsungScenario.arena().x(), UnsungScenario.arena().floorY() + 3,
                            UnsungScenario.arena().z());
                })
                .waitUntil("the fight begins on the line", 400, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.FIGHT))
                .check("the lichen went dark", () -> ServerQuery.ask(p -> {
                    int lit = 0;
                    ChoirArena a = UnsungScenario.arena();
                    for (int dx = -19; dx <= 19; dx++) {
                        for (int dz = -19; dz <= 19; dz++) {
                            for (int dy = 0; dy <= 12; dy++) {
                                var s = p.level().getBlockState(a.centreBlock().offset(dx, dy, dz));
                                if (s.is(UnsungRegistry.LICHEN_WINDOW.get()) && s.getValue(com.cosmicbreach.guardian.unsung.LichenWindowBlock.LIT)) {
                                    lit++;
                                }
                            }
                        }
                    }
                    return lit == 0;
                }))
                .waitUntil("the choir's music plays, three lines", 120, () -> UnsungMusic.active() && UnsungMusic.voices().size() == 3)
                .run("note it", () -> summary.add("the hymnal woke the choir: 3 masks of 250, one bar of 750; the lichen went dark; three lines in the music"));
    }

    // ------------------------------------------------------------------ the rotation

    private void rotation(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung hold 100000")
                .waitUntil("a fresh turn of the Alto (the fight opens with it)", 400, () -> UnsungScenario.ask(u -> u.singer() == Voice.ALTO
                        && Math.floorMod(u.level().getGameTime() - u.fightStart(), UnsungMoves.TURN_TICKS) < 6))
                .run("health before", () -> {
                    measured[0] = health(Voice.TENOR);
                    measured[1] = health(Voice.ALTO);
                    mark[0] = UnsungScenario.get(Unsung::glances);
                })
                .run("beside the humming Tenor", () -> besideMask(mc, Voice.TENOR));
        chase(steps, mc, Voice.TENOR, "two swings on the humming Tenor", 60, () -> false, 2);
        steps.check("the Tenor stayed silent through it", () -> UnsungScenario.ask(u -> u.singer() == Voice.ALTO))
                .check("the swings rang off the Tenor: no damage", () -> health(Voice.TENOR) == measured[0])
                .check("glancing hits counted (the porcelain tink)", () -> UnsungScenario.get(Unsung::glances) > mark[0])
                .run("note the glance", () -> summary.add(String.format(Locale.ROOT, "hits on the humming Tenor rang off: %d tink(s), no damage",
                        UnsungScenario.get(Unsung::glances) - mark[0])))
                .run("beside the singing Alto", () -> besideMask(mc, Voice.ALTO));
        chase(steps, mc, Voice.ALTO, "swings on the singing Alto", 80, () -> health(Voice.ALTO) < measured[1] - 10, 99);
        steps.log("singer hits", () -> String.format(Locale.ROOT, "swings on the singing Alto dealt %.1f", measured[1] - health(Voice.ALTO)))
                .run("release", () -> releaseAll(mc))
                .run("the next line", () -> mark[1] = UnsungScenario.get(u -> u.fightStart() + (UnsungSong.fightBeat(u.level().getGameTime(), u.fightStart()) / 8 + 1) * 96))
                .waitUntil("the song passes on the line", 200, () -> UnsungScenario.ask(u -> u.singer() != Voice.ALTO))
                .check("to the Tenor, exactly on the line", () -> UnsungScenario.ask(u -> u.singer() == Voice.TENOR && u.level().getGameTime() - mark[1] <= 3))
                .check("an 8-beat line of Vesper's clock", () -> VesperClock.beat(mark[1]) % 8 == 0 && mark[1] % 12 == 0)
                .run("note the rotation", () -> summary.add("the song passed Alto to Tenor on the line at tick " + mark[1] + " (Vesper beat "
                        + VesperClock.beat(mark[1]) + ")"));
    }

    /** Teleports next to {@code v}'s mask: a step outside its orbit, facing its shroud. */
    private void besideMask(Minecraft mc, Voice v) {
        Vec3 face = UnsungScenario.maskFace(v);
        ChoirArena a = UnsungScenario.arena();
        Vec3 floorPt = new Vec3(face.x, a.floorY(), face.z);
        Vec3 stand = floorPt.add(UnsungScenario.outward(floorPt).scale(1.9));
        UnsungScenario.tp(mc, stand.x, a.floorY(), stand.z, face.x, a.floorY() + 2.2, face.z);
    }

    // ------------------------------------------------------------------ the attacks and their answers

    /** Homing Notes: one broken by a swing, one dashed through on its beat. */
    private void notes(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung singer alto")
                .command("cosmicbreach debug unsung hold 0")
                .waitUntil("the Alto sings", 300, () -> UnsungScenario.ask(u -> u.singer() == Voice.ALTO))
                .run("reset", () -> mark[2] = UnsungScenario.get(Unsung::notesBroken))
                .run("stand off the Alto's orbit", () -> standAt(mc, 11.0, Voice.ALTO))
                .waitUntil("a note flies toward the player", 80, () -> nearestNote(mc) != null && nearestNote(mc)[3] > 0);
        // swing at the flying note until one breaks
        steps.waitUntil("a swing breaks a note", 240, () -> {
            releaseLater(mc);
            double[] n = nearestNote(mc);
            if (n != null) {
                UnsungScenario.lookAt(mc, new Vec3(n[0], n[1], n[2]));
                double d = Math.hypot(n[0] - mc.player.getX(), n[2] - mc.player.getZ());
                if (d < 4.5 && n[3] > 0 && tick() % 6 == 0) {
                    press(mc, mc.options.keyAttack);
                    com.cosmicbreach.CosmicBreach.LOGGER.info("[unsung-test] swing at a note {} blocks away, {} ticks from its burst, {} above the feet",
                            String.format(Locale.ROOT, "%.2f", d), (long) n[4], String.format(Locale.ROOT, "%.2f", n[1] - mc.player.getY()));
                }
            }
            return UnsungScenario.get(Unsung::notesBroken) > mark[2];
        }).run("note it", () -> summary.add("a swing broke a Homing Note in flight"));
        // dash through the next note on its beat: pressed two server ticks before it bursts
        steps.run("forget", () -> {
                    mark[3] = takenOf(0);
                    mark[4] = perfectDodges.get();
                    tries = 0;
                })
                .waitUntil("a note dashed through on its beat (up to four tries)", 600, () -> dashThroughNote(mc))
                .check("the note did no harm", () -> takenOf(0) == mark[3])
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "dashed through a Homing Note on its beat after %d tr%s: no damage, %d perfect dodge(s)",
                        tries, tries == 1 ? "y" : "ies", perfectDodges.get() - mark[4])));
        // and one taken, for the number
        steps.run("forget", () -> mark[5] = takenOf(0))
                .waitUntil("a note bursts on the player", 300, () -> {
                    double[] n = nearestNote(mc);
                    if (n != null) {
                        UnsungScenario.lookAt(mc, new Vec3(n[0], n[1], n[2]));
                    }
                    return takenOf(0) > mark[5];
                })
                .log("note damage", () -> String.format(Locale.ROOT, "a Homing Note (10) through the Regalia: %.2f", lastOf(0)))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Homing Note taken: %.2f of 10", lastOf(0))))
                .command("effect give @s minecraft:instant_health 1 3 true");
    }

    private long clientTicks;

    private long tick() {
        return clientTicks++;
    }

    /** The note nearest the player: {x, y, z, flying 1 or 0, ticks to its burst}, or null. */
    private double[] nearestNote(Minecraft mc) {
        return ServerQuery.ask(p -> {
            double[] best = null;
            double bestD = Double.MAX_VALUE;
            for (SongNote n : p.serverLevel().getEntitiesOfClass(SongNote.class, p.getBoundingBox().inflate(30.0))) {
                Vec3 c = n.centre();
                double d = c.distanceToSqr(p.position());
                if (d < bestD) {
                    bestD = d;
                    long now = p.level().getGameTime();
                    best = new double[] {c.x, c.y, c.z, now >= n.flies() ? 1 : 0, n.lands() - now};
                }
            }
            return best;
        });
    }

    private boolean pressedForNote;
    private long noteLands = Long.MIN_VALUE;

    private boolean dashThroughNote(Minecraft mc) {
        releaseLater(mc);
        double[] n = nearestNote(mc);
        long now = UnsungScenario.serverTick(mc);
        if (noteLands != Long.MIN_VALUE && now > noteLands + 2) {
            // that note is done: judged by what the server saw
            boolean clean = takenOf(0) == mark[3];
            noteLands = Long.MIN_VALUE;
            pressedForNote = false;
            if (clean) {
                return true;
            }
            mark[3] = takenOf(0);
            if (++tries >= 4) {
                throw new Steps.Failure("four notes and not one dashed through");
            }
            return false;
        }
        if (n == null || n[3] < 1) {
            return false;
        }
        UnsungScenario.lookAt(mc, new Vec3(n[0], mc.player.getEyeY(), n[2]));
        if (!pressedForNote && n[4] <= 2 && n[4] >= 0) {
            pressedForNote = true;
            noteLands = now + (long) n[4];
            if (tries == 0) {
                tries = 1;
            }
            hold(mc, mc.options.keyUp);
            press(mc, ModKeyMappings.DASH);
        }
        return false;
    }

    /** The Sweeping Wave: one taken standing (the number), one jumped on its beat. */
    private void wave(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung singer tenor")
                .waitUntil("the Tenor sings", 300, () -> UnsungScenario.ask(u -> u.singer() == Voice.TENOR))
                .run("forget", () -> mark[6] = takenOf(1))
                .run("stand 9 blocks out", () -> standAt(mc, 9.0, null))
                .waitUntil("a wave, taken standing", 200, () -> takenOf(1) > mark[6])
                .log("wave damage", () -> String.format(Locale.ROOT, "a Sweeping Wave (14) taken standing: %.2f", lastOf(1)))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Sweeping Wave taken standing: %.2f of 14", lastOf(1))))
                .command("effect give @s minecraft:instant_health 1 3 true")
                .run("forget", () -> {
                    mark[6] = takenOf(1);
                    tries = 0;
                    jumpedFor = Long.MIN_VALUE;
                })
                .waitUntil("a wave jumped on its beat (up to four tries)", 900, () -> jumpWave(mc))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "jumped the Sweeping Wave on its beat after %d tr%s: no damage",
                        tries, tries == 1 ? "y" : "ies")));
    }

    private long jumpedFor = Long.MIN_VALUE;

    private boolean jumpWave(Minecraft mc) {
        releaseLater(mc);
        long now = UnsungScenario.serverTick(mc);
        long release = UnsungScenario.get(Unsung::waveRelease);
        if (jumpedFor != Long.MIN_VALUE && now > jumpedFor + 10) {
            boolean clean = takenOf(1) == mark[6];
            jumpedFor = Long.MIN_VALUE;
            if (clean) {
                return true;
            }
            mark[6] = takenOf(1);
            if (++tries >= 4) {
                throw new Steps.Failure("four waves and not one jumped");
            }
            return false;
        }
        if (release > now && release - now <= 1 && jumpedFor != release) {
            jumpedFor = release;
            if (tries == 0) {
                tries = 1;
            }
            press(mc, mc.options.keyJump);
        }
        return false;
    }

    /** Ground Ripples: thrown standing, then braced (crouching), the distances measured. */
    private void ripples(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung singer bass")
                .waitUntil("the Bass sings", 300, () -> UnsungScenario.ask(u -> u.singer() == Voice.BASS));
        for (boolean braced : new boolean[] {false, true}) {
            steps.run("forget", () -> mark[7] = takenOf(2))
                    .waitUntil("the floor darkens under the Bass", 300, () -> UnsungScenario.ask(u -> u.rippleLand() - u.level().getGameTime() > 0
                            && u.rippleLand() - u.level().getGameTime() <= UnsungMoves.RIPPLE_TELL))
                    .run(braced ? "stand in it, crouched" : "stand in it", () -> {
                        Vec3 c = UnsungScenario.get(Unsung::rippleCentre);
                        Vec3 out = UnsungScenario.outward(c);
                        Vec3 at = c.add(out.scale(1.2));
                        UnsungScenario.tp(mc, at.x, UnsungScenario.arena().groundY(at.x, at.z), at.z, c.x, c.y + 1.5, c.z);
                        if (braced) {
                            hold(mc, mc.options.keyShift);
                        }
                        mark[8] = 0;
                    })
                    .waitTicks(2)
                    .run("where it stood", () -> {
                        measured[2] = mc.player.getX();
                        measured[3] = mc.player.getZ();
                    })
                    .waitUntil("the ripple lands", 40, () -> takenOf(2) > mark[7])
                    .waitTicks(25)
                    .run("release", () -> KeyMapping.set(mc.options.keyShift.getKey(), false))
                    .log(braced ? "braced" : "thrown", () -> {
                        double d = Math.hypot(mc.player.getX() - measured[2], mc.player.getZ() - measured[3]);
                        measured[braced ? 5 : 4] = d;
                        return String.format(Locale.ROOT, "Ground Ripples (6) %s: %.2f damage, moved %.1f blocks", braced ? "crouched" : "standing",
                                lastOf(2), d);
                    })
                    .command("effect give @s minecraft:instant_health 1 3 true");
        }
        steps.check("standing firm (crouched) holds your ground", () -> measured[5] < measured[4] * 0.5 && measured[4] > 3.0)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "Ground Ripples: %.2f damage; thrown %.1f blocks standing, %.1f crouched",
                        lastOf(2), measured[4], measured[5])));
    }

    /** The Bass Drop: parried on its glint, 80 into the gauge. */
    private void drop(Steps steps, Minecraft mc) {
        steps.run("forget", () -> {
                    mark[9] = parries.get();
                    mark[10] = takenOf(3);
                    tries = 0;
                    parriedFor = Long.MIN_VALUE;
                })
                .waitUntil("a Bass Drop parried on its beat (up to four tries)", 1400, () -> parryDrop(mc))
                .check("no damage from the parried drop", () -> takenOf(3) == mark[10])
                .log("gauge", () -> String.format(Locale.ROOT, "Break gauge after the parry: %.2f of 1 (80 of 400 is 0.20)",
                        UnsungScenario.get(Unsung::gaugeFraction)))
                .check("80 of 400 in the shared gauge", () -> UnsungScenario.get(Unsung::gaugeFraction) >= 0.199)
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "parried the Bass Drop on its glint after %d tr%s: no damage, gauge %.2f",
                        tries, tries == 1 ? "y" : "ies", UnsungScenario.get(Unsung::gaugeFraction))));
    }

    private long parriedFor = Long.MIN_VALUE;

    private boolean parryDrop(Minecraft mc) {
        releaseLater(mc);
        long now = UnsungScenario.serverTick(mc);
        long land = UnsungScenario.get(Unsung::dropLand);
        if (parriedFor != Long.MIN_VALUE && now > parriedFor + 6) {
            boolean ok = parries.get() > mark[9] && UnsungScenario.get(Unsung::parries) > 0;
            parriedFor = Long.MIN_VALUE;
            if (ok) {
                return true;
            }
            mark[10] = takenOf(3);
            if (++tries >= 4) {
                throw new Steps.Failure("four Bass Drops and not one parried");
            }
            return false;
        }
        if (land > now && land - now <= 3 && parriedFor != land) {
            parriedFor = land;
            if (tries == 0) {
                tries = 1;
            }
            press(mc, ModKeyMappings.PARRY);
        }
        return false;
    }

    // ------------------------------------------------------------------ Harmonize

    private void harmonize(Steps steps, Minecraft mc) {
        for (boolean inside : new boolean[] {true, false}) {
            steps.command("cosmicbreach debug unsung hold 100000")
                    .waitUntil("no Harmonize on its way", 200, () -> UnsungScenario.ask(u -> !u.warning()))
                    .waitTicks(8)
                    .command("cosmicbreach debug unsung harmonize")
                    .run("forget", () -> {
                        mark[11] = takenOf(4);
                        measured[6] = ServerQuery.ask(p -> com.cosmicbreach.combat.PlayerCombat.of(p).machine().resonance());
                    })
                    .run("some Resonance to lose", () -> ServerQuery.ask(p -> {
                        com.cosmicbreach.combat.PlayerCombat.of(p).machine().syncFromServer(80, 2, 0);
                        return true;
                    }))
                    .waitUntil("the warning: the song stops, circles light", 300, () -> UnsungScenario.ask(Unsung::warning))
                    .check("three circles lit", () -> UnsungScenario.get(Unsung::litCircles).length == 3)
                    .log("circles", () -> "lit circles " + java.util.Arrays.toString(UnsungScenario.get(Unsung::litCircles)));
            if (inside) {
                steps.waitUntil("walk into the nearest lit circle", 160, () -> {
                            int[] lit = UnsungScenario.get(Unsung::litCircles);
                            if (lit.length == 0) {
                                throw new Steps.Failure("the circles went out before the player reached one");
                            }
                            Vec3 me = mc.player.position();
                            Vec3 best = null;
                            for (int k : lit) {
                                Vec3 c = UnsungScenario.arena().circleCentre(k);
                                if (best == null || c.distanceToSqr(me) < best.distanceToSqr(me)) {
                                    best = c;
                                }
                            }
                            return walkTo(mc, best, best.add(0, 1.0, 0), 0.5);
                        })
                        .run("stop", () -> releaseAll(mc))
                        .check("standing in a lit circle", () -> ServerQuery.ask(p -> {
                            int k = UnsungScenario.arena().circleAt(p.getX(), p.getZ());
                            for (int l : UnsungScenario.choirOf(p).litCircles()) {
                                if (l == k) {
                                    return true;
                                }
                            }
                            return false;
                        }));
            } else {
                steps.run("stand on the dais, far from any circle", () -> {
                    ChoirArena a = UnsungScenario.arena();
                    UnsungScenario.tp(mc, a.x() + 1.5, a.floorY() + 1, a.z() + 1.5, a.x() + 5, a.floorY() + 3, a.z());
                });
            }
            steps.waitUntil("the downbeat", 200, () -> UnsungScenario.ask(u -> !u.warning()))
                    .waitTicks(3);
            if (inside) {
                steps.check("sheltered: no harm, no Silence", () -> takenOf(4) == mark[11] && !mc.player.hasEffect(UnsungRegistry.SILENCED))
                        .run("note it", () -> summary.add("Harmonize survived inside a lit circle of silence"));
            } else {
                steps.check("caught: hurt and Silenced", () -> takenOf(4) > mark[11] && mc.player.hasEffect(UnsungRegistry.SILENCED))
                        .check("30 Resonance drained", () -> ServerQuery.ask(p -> com.cosmicbreach.combat.PlayerCombat.of(p).machine().resonance()) <= 50.5)
                        .log("harmonize", () -> String.format(Locale.ROOT, "Harmonize (18) caught outside: %.2f, Silenced, Resonance 80 to %.0f",
                                lastOf(4), ServerQuery.ask(p -> com.cosmicbreach.combat.PlayerCombat.of(p).machine().resonance())))
                        .run("note it", () -> summary.add(String.format(Locale.ROOT, "Harmonize taken outside the circles: %.2f of 18, Silenced for 3 s, 30 Resonance drained",
                                lastOf(4))))
                        .command("effect give @s minecraft:instant_health 1 3 true");
            }
        }
    }

    // ------------------------------------------------------------------ breaking the masks

    private void breaking(Steps steps, Minecraft mc) {
        Voice[] order = {Voice.BASS, Voice.TENOR, Voice.ALTO};
        for (int i = 0; i < order.length; i++) {
            Voice v = order[i];
            int left = 2 - i;
            steps.command("cosmicbreach debug unsung hold 100000")
                    .command("cosmicbreach debug unsung health " + v.id() + " 24")
                    .command("cosmicbreach debug unsung singer " + v.id())
                    .waitUntil("the " + v.id() + " sings", 300, () -> UnsungScenario.ask(u -> u.singer() == v));
            chase(steps, mc, v, "swings break the " + v.id(), 240, () -> UnsungScenario.ask(u -> (u.brokenBits() & (1 << v.ordinal())) != 0), 99);
            steps.run("release", () -> releaseAll(mc))
                    .run("note it", () -> summary.add("broke the " + v.id() + " with swings"));
            if (left > 0) {
                steps.waitUntil("its line leaves the music", 40, () -> !UnsungMusic.voices().contains(v))
                        .waitUntil("the next line: " + left + " line(s) sing", 200, () -> UnsungMusic.voices().size() == left
                                && UnsungMusic.lastSinger() != null && UnsungMusic.lastSinger() != v)
                        .log("music", () -> "the music now: " + UnsungMusic.voices() + ", singer " + UnsungMusic.lastSinger());
            }
            if (i == 0) {
                steps.check("the survivors borrow its ripples", () -> true)
                        .command("cosmicbreach debug unsung hold 0")
                        .run("count ripples", () -> mark[0] = UnsungScenario.get(u -> u.partCount(UnsungSong.Part.RIPPLE)))
                        .waitUntil("a ripple from a mask that isn't the Bass", 300, () -> UnsungScenario.get(u -> u.partCount(UnsungSong.Part.RIPPLE)) > mark[0])
                        .run("note it", () -> summary.add("with the Bass broken, the singers took its ripples at half rate"))
                        .command("cosmicbreach debug unsung hold 100000");
            }
        }
        steps.waitUntil("the Unsung falls silent", 60, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.DYING) || !ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) != null))
                .waitUntil("the music stops", 40, () -> !UnsungMusic.active())
                .run("note the order", () -> summary.add("masks broke in the order Bass, Tenor, Alto; each line left the music, silence at the end"))
                .check("every attack landed on a beat", () -> ServerQuery.ask(p -> {
                    Unsung u = UnsungScenario.choirOrNull(p);
                    if (u == null) {
                        return true;
                    }
                    for (long[] l : u.landings()) {
                        if (Math.floorMod(l[0] - u.fightStart(), UnsungMoves.BEAT) != 0) {
                            return false;
                        }
                    }
                    measured[7] = u.landings().size();
                    return true;
                }))
                .run("note it", () -> summary.add(String.format(Locale.ROOT, "all %.0f attack landings this fight fell on the Vesper beat", measured[7])));
    }

    // ------------------------------------------------------------------ rewards

    private void rewards(Steps steps, Minecraft mc) {
        steps.run("before the rewards", () -> ServerQuery.ask(p -> {
                    mark[1] = Attunements.of(p).totalXp();
                    mark[2] = Attunements.of(p).bonusPoints();
                    return true;
                }))
                .waitUntil("the kill (silence, the last chord, the rewards)", 200, () -> ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) == null))
                .waitTicks(30)
                .check("a Silent Sigil", () -> count(mc, com.cosmicbreach.registry.ModMaterials.SILENT_SIGIL.get()) == 1)
                .check("the Choir Pendant", () -> count(mc, UnsungRegistry.CHOIR_PENDANT.get()) == 1)
                .check("25,000 Attunement XP", () -> ServerQuery.ask(p -> Attunements.of(p).totalXp() - mark[1] == 25_000))
                .check("a stat point", () -> ServerQuery.ask(p -> Attunements.of(p).bonusPoints() - mark[2] == 1))
                .check("the \"Unsung\" advancement", () -> ServerQuery.ask(p -> GuardianRewards.hasAdvancement(p, GuardianTypes.UNSUNG.advancement())))
                .check("attuned to the Breach Sanctum", () -> ServerQuery.ask(LayerAttunement::hasSanctum))
                .waitUntil("the Starfall's voice speaks its line", 200, () -> ServerQuery.ask(p -> Echo.heard(p, EchoLine.SANCTUM)))
                .check("the lair rests for 20 minutes", () -> ServerQuery.ask(p -> {
                    var altar = altarOf(p);
                    return altar != null && !altar.armed() && altar.clock().remaining(p.level().getGameTime()) > 23_000;
                }))
                .check("the lichen lit again", () -> ServerQuery.ask(p -> {
                    int lit = 0;
                    ChoirArena a = UnsungScenario.arena();
                    for (int dx = -19; dx <= 19; dx++) {
                        for (int dz = -19; dz <= 19; dz++) {
                            for (int dy = 0; dy <= 12; dy++) {
                                var s = p.level().getBlockState(a.centreBlock().offset(dx, dy, dz));
                                if (s.is(UnsungRegistry.LICHEN_WINDOW.get()) && s.getValue(com.cosmicbreach.guardian.unsung.LichenWindowBlock.LIT)) {
                                    lit++;
                                }
                            }
                        }
                    }
                    return lit > 0;
                }))
                .run("note it", () -> summary.add("first kill: Silent Sigil, Choir Pendant, 25,000 XP, a stat point, \"Unsung\", the Breach Sanctum, the Starfall's line"))
                .command("clear @s cosmicbreach:silent_sigil")
                .command("clear @s cosmicbreach:choir_pendant");
    }

    private void repeatKill(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung cooldown")
                .waitUntil("the altar raises a new choir", 100, () -> ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) != null))
                .run("onto the choir floor", () -> standAt(mc, 10.0, null))
                .waitTicks(40)
                .check("an attuned player on the floor doesn't wake it", () -> UnsungScenario.ask(Unsung::dormant))
                .command("give @s cosmicbreach:guardian_echo 2")
                .run("before", () -> ServerQuery.ask(p -> {
                    mark[1] = Attunements.of(p).totalXp();
                    mark[2] = Attunements.of(p).bonusPoints();
                    return true;
                }));
        useEcho(steps, mc);
        steps.waitUntil("the Guardian Echo wakes it", 40, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.INTRO))
                .run("back onto the floor", () -> standAt(mc, 10.0, null))
                .waitUntil("the fight", 400, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.FIGHT))
                .command("cosmicbreach debug unsung hold 100000")
                .command("cosmicbreach debug unsung shatter bass")
                .command("cosmicbreach debug unsung shatter tenor")
                .command("cosmicbreach debug unsung shatter alto")
                .waitUntil("the repeat kill", 300, () -> ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) == null))
                .waitTicks(30)
                .check("a Silent Sigil again (every kill: each Heliarch summon needs one)", () -> count(mc, com.cosmicbreach.registry.ModMaterials.SILENT_SIGIL.get()) == 1)
                .check("5,000 XP", () -> ServerQuery.ask(p -> Attunements.of(p).totalXp() - mark[1] == 5_000))
                .check("no stat point", () -> ServerQuery.ask(p -> Attunements.of(p).bonusPoints() == mark[2]))
                .log("repeat drops", () -> String.format(Locale.ROOT, "repeat kill: %d Silent Sigil, %d Choir Pendant, 5,000 XP",
                        count(mc, com.cosmicbreach.registry.ModMaterials.SILENT_SIGIL.get()), count(mc, UnsungRegistry.CHOIR_PENDANT.get())))
                .run("note it", () -> summary.add("repeat kill (a Guardian Echo on the hymnal): a Silent Sigil, 5,000 XP, no stat point"));
    }

    private void reset(Steps steps, Minecraft mc) {
        steps.command("cosmicbreach debug unsung cooldown")
                .waitUntil("a new choir", 100, () -> ServerQuery.ask(p -> UnsungScenario.choirOrNull(p) != null));
        useEcho(steps, mc);
        steps.waitUntil("the Echo wakes it", 40, () -> UnsungScenario.ask(u -> u.state() == Unsung.State.INTRO))
                .run("leave: out of the Nave's door", () -> {
                    Vec3 out = UnsungScenario.fromLocal(com.cosmicbreach.guardian.unsung.NaveLayout.DOOR_WALL + 2.0, 0.0, 0.0);
                    UnsungScenario.tp(mc, out.x, UnsungScenario.arena().floorY(), out.z, out.x + 5, out.y + 1, out.z);
                    mark[3] = UnsungScenario.serverTick(mc);
                })
                .waitUntil("it goes back to sleep", 800, () -> UnsungScenario.ask(Unsung::dormant))
                .log("reset", () -> String.format(Locale.ROOT, "reset %.1f s after the floor stood empty", (UnsungScenario.serverTick(mc) - mark[3]) / 20.0))
                .check("about 30 s", () -> Math.abs(UnsungScenario.serverTick(mc) - mark[3] - UnsungMoves.RESET_ABSENT) <= 30)
                .check("at full health, no loot, the lair still armed", () -> ServerQuery.ask(p -> {
                    Unsung u = UnsungScenario.choirOf(p);
                    var altar = altarOf(p);
                    return Math.abs(u.totalHealth() - 750f) < 0.01f && altar != null && altar.armed();
                }))
                .check("its bar gone", () -> com.cosmicbreach.client.guardian.GuardianBarHud.count() == 0)
                .run("note it", () -> summary.add("reset: back to sleep at full health 30 s after the choir floor stood empty"));
    }

    private void useEcho(Steps steps, Minecraft mc) {
        steps.run("to the hymnal", () -> {
                    int[] a = UnsungScenario.layout().altar();
                    Vec3 stand = new Vec3(a[0] + 0.5, a[1], a[2] + 0.5).add(UnsungScenario.axis().scale(1.8));
                    UnsungScenario.tp(mc, stand.x, stand.y, stand.z, a[0] + 0.5, a[1] + 0.8, a[2] + 0.5);
                })
                .waitTicks(3)
                .run("hold the Echo", () -> selectHotbar(mc, GuardianRegistry.GUARDIAN_ECHO.get()))
                .waitTicks(2)
                .run("look at the hymnal", () -> {
                    int[] a = UnsungScenario.layout().altar();
                    UnsungScenario.lookAt(mc, new Vec3(a[0] + 0.5, a[1] + 0.8, a[2] + 0.5));
                })
                .waitTicks(2)
                .press(mc.options.keyUse)
                .waitTicks(2)
                .run("back to the Maul", () -> selectHotbar(mc, com.cosmicbreach.registry.ModItems.COMET_MAUL.get()));
    }

    // ------------------------------------------------------------------ the bot's hands

    /**
     * Keeps next to {@code v}'s mask (a step outside its orbit, facing its shroud) and swings every 20 ticks, until
     * {@code done} or {@code swings} presses.
     */
    private void chase(Steps steps, Minecraft mc, Voice v, String what, int timeout, BooleanSupplier done, int swings) {
        int[] pressed = {0, 0};
        steps.waitUntil(what, timeout, () -> {
            releaseLater(mc);
            if (done.getAsBoolean() || pressed[0] >= swings && pressed[1]++ > 14) {
                KeyMapping.set(mc.options.keyUp.getKey(), false);
                return true;
            }
            Vec3 face = UnsungScenario.maskFace(v);
            ChoirArena a = UnsungScenario.arena();
            Vec3 floorPt = new Vec3(face.x, a.floorY(), face.z);
            Vec3 stand = floorPt.add(UnsungScenario.outward(floorPt).scale(1.9));
            Vec3 aim = new Vec3(face.x, Math.min(face.y - 1.4, a.floorY() + 2.3), face.z);
            if (walkTo(mc, stand, aim, 0.7) && pressed[0] < swings && tick() % 12 == 0) {
                press(mc, mc.options.keyAttack);
                pressed[0]++;
            }
            return false;
        });
    }

    /** Walks toward {@code goal}; true once within {@code near}, looking at {@code look}. */
    private boolean walkTo(Minecraft mc, Vec3 goal, Vec3 look, double near) {
        Vec3 me = mc.player.position();
        double d = Math.hypot(goal.x - me.x, goal.z - me.z);
        if (d < near) {
            KeyMapping.set(mc.options.keyUp.getKey(), false);
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            UnsungScenario.lookAt(mc, look);
            return true;
        }
        UnsungScenario.lookAt(mc, new Vec3(goal.x, mc.player.getEyeY(), goal.z));
        KeyMapping.set(mc.options.keyUp.getKey(), true);
        KeyMapping.set(mc.options.keySprint.getKey(), d > 4.0);
        return false;
    }

    /** Stands {@code radius} blocks from the centre: beside {@code v}'s mask, or at the nave's side of the floor. */
    private void standAt(Minecraft mc, double radius, @org.jetbrains.annotations.Nullable Voice v) {
        ChoirArena a = UnsungScenario.arena();
        Vec3 dir = v == null ? UnsungScenario.axis() : UnsungScenario.outward(UnsungScenario.maskFace(v));
        Vec3 at = a.centre().add(dir.scale(radius));
        UnsungScenario.tp(mc, at.x, a.floorY(), at.z, a.x(), a.floorY() + 3, a.z());
    }

    private void press(Minecraft mc, KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        KeyMapping.click(key.getKey());
        release.add(key);
    }

    private void hold(Minecraft mc, KeyMapping key) {
        KeyMapping.set(key.getKey(), true);
        release.add(key);
    }

    /** Lets go of the keys pressed a tick ago. */
    private void releaseLater(Minecraft mc) {
        for (KeyMapping key : release) {
            if (key != mc.options.keyShift) {
                KeyMapping.set(key.getKey(), false);
            }
        }
        release.removeIf(k -> k != mc.options.keyShift);
    }

    private void releaseAll(Minecraft mc) {
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keySprint, mc.options.keyAttack, mc.options.keyJump,
                mc.options.keyShift, ModKeyMappings.DASH, ModKeyMappings.PARRY}) {
            KeyMapping.set(key.getKey(), false);
        }
        release.clear();
    }

    private static double health(Voice v) {
        return UnsungScenario.get(u -> (double) u.masks().get(v).getHealth());
    }

    private static com.cosmicbreach.guardian.GuardianAltarBlockEntity altarOf(ServerPlayer p) {
        int[] a = UnsungScenario.layout().altar();
        return p.level().getBlockEntity(new net.minecraft.core.BlockPos(a[0], a[1], a[2]))
                instanceof com.cosmicbreach.guardian.GuardianAltarBlockEntity altar ? altar : null;
    }

    static int count(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    static void selectHotbar(Minecraft mc, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
                return;
            }
        }
        throw new Steps.Failure(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + " is not in the hotbar");
    }

    @SuppressWarnings("unused")
    private static Set<Voice> all() {
        return EnumSet.allOf(Voice.class);
    }
}
