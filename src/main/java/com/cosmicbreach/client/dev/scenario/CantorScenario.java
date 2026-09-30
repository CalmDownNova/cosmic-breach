package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.guardian.unsung.UnsungRegistry;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.cantor.Cantor;
import com.cosmicbreach.relic.cantor.CantorRules;
import com.cosmicbreach.relic.cantor.ChordField;
import com.cosmicbreach.relic.cantor.ResonantNote;
import com.cosmicbreach.relic.cantor.UmbraArrow;
import com.cosmicbreach.relic.crown.CrownBlockEntity;
import com.cosmicbreach.relic.crown.HeliarchsCrownBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Umbra Cantor (G9b, GDD 7.3) through the real key path, measured on the server:
 * <ul>
 *   <li>looks: the bow in first person at rest and drawn, in third person at rest and drawn;</li>
 *   <li>three charged shots (a 20-tick draw each) into the ground: a note where each lands, its tone walking up D major
 *       pentatonic; the third within 8 blocks of the first two rings the chord;</li>
 *   <li>a rooted skeleton inside the triangle: it shoots before the chord; in the chord it is Silenced, every shot
 *       fizzles, and it takes 4 damage every 20 ticks, five times;</li>
 *   <li>a lone note lasts 100 ticks;</li>
 *   <li>Cadence: three arrows in 12 ticks for 30 Resonance, a second cast refused on its 7 s cooldown, and its three
 *       notes strike a chord;</li>
 *   <li>the Heliarch's Crown placed: light 12 at it, its block entity there for the orbiting sun, photographed twice.</li>
 * </ul>
 */
public final class CantorScenario implements Scenario {
    static final String TAG = "cb_cantor";
    private static final ResourceLocation CHORD = CosmicBreach.id("chord");

    private record Hit(int entity, double amount, long time, String type) {
    }

    private record Shot(long time, boolean arrived) {
    }

    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final List<ResourceLocation> started = new CopyOnWriteArrayList<>();
    private final List<Long> ownArrows = new CopyOnWriteArrayList<>();
    private final List<Shot> skeletonShots = new CopyOnWriteArrayList<>();
    private final List<String> summary = new CopyOnWriteArrayList<>();
    private volatile int skeletonId = -1;
    private BlockPos base = BlockPos.ZERO;
    private @Nullable DevCamera camera;

    @Override
    public int timeBudgetSeconds() {
        return 360;
    }

    private Vec3 at(double dx, double dz) {
        return new Vec3(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz);
    }

    private void stand(Minecraft mc, Vec3 look) {
        Vec3 a = at(0, 0);
        ColossusScenario.tp(mc, a.x, a.y, a.z, look.x, look.y, look.z);
    }

    private static CombatStateMachine machine(Minecraft mc) {
        return PlayerCombat.of(mc.player).machine();
    }

    private static void key(KeyMapping k, boolean down) {
        KeyMapping.set(k.getKey(), down);
    }

    private static long now() {
        return ServerQuery.ask(p -> p.level().getGameTime());
    }

    private static void fillAndReady() {
        ServerQuery.ask(p -> {
            CombatStateMachine m = PlayerCombat.of(p).machine();
            m.syncFromServer(m.maxResonance(), m.dashCharges(), 0);
            return true;
        });
    }

    private static <T extends Entity> List<T> around(ServerPlayer p, Class<T> type) {
        return p.serverLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(48.0), e -> true);
    }

    private static void clearAll() {
        ServerQuery.ask(p -> {
            around(p, LivingEntity.class).stream().filter(e -> e.getTags().contains(TAG)).forEach(Entity::discard);
            around(p, ChordField.class).forEach(Entity::discard);
            around(p, ResonantNote.class).forEach(Entity::discard);
            around(p, UmbraArrow.class).forEach(Entity::discard);
            around(p, AbstractArrow.class).forEach(Entity::discard);
            return true;
        });
    }

    private void listen() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            LivingEntity e = event.getEntity();
            if (!e.level().isClientSide() && e.getTags().contains(TAG)) {
                String type = event.getSource().typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?");
                hits.add(new Hit(e.getId(), event.getNewDamage(), e.level().getGameTime(), type));
            }
        });
        // the skeleton's shots: every one it looses (first in line), and whether it arrived (last in line, not cancelled)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() != null
                    && arrow.getOwner().getId() == skeletonId) {
                skeletonShots.add(new Shot(event.getLevel().getGameTime(), false));
            }
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof UmbraArrow) {
                ownArrows.add(event.getLevel().getGameTime());
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() != null
                    && arrow.getOwner().getId() == skeletonId) {
                skeletonShots.add(new Shot(event.getLevel().getGameTime(), true));
            }
        });
        ClientCombat.addEventListener(event -> {
            if (event instanceof CombatEvent.MoveStarted m) {
                started.add(m.move().id());
            }
        });
    }

    /** Draws a charged shot at {@code target} (a point on the ground, worked out when the step runs): hold 24 ticks, let go. */
    private void chargedShot(Steps steps, Minecraft mc, String what, java.util.function.Supplier<Vec3> target) {
        steps.run("aim " + what, () -> stand(mc, target.get()))
                .waitTicks(4)
                .run("draw", () -> key(mc.options.keyAttack, true))
                .waitUntil("drawn " + what, 60, () -> machine(mc).phase() == CombatStateMachine.Phase.CHARGING
                        && machine(mc).attackHeldTicks() >= CantorRules.DRAW_TICKS + 4)
                .run("loose", () -> key(mc.options.keyAttack, false));
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        listen();
        steps.command("difficulty normal")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("time set noon")
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .command("effect give @s minecraft:resistance 900 4 true")
                .run("where we stand", () -> base = ServerQuery.ask(p -> p.blockPosition()))
                .command("give @s cosmicbreach:umbra_cantor")
                .waitUntil("the Umbra Cantor in hand with its data", 60, () -> mc.player.getMainHandItem().is(Relics.UMBRA_CANTOR.get())
                        && machine(mc).weapon() != null && machine(mc).weapon().ability().isPresent())
                .run("look south", () -> stand(mc, at(0, 20).add(0, 1.6, 0)))
                .waitTicks(20)
                .run("clear the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("cantor_fp_idle")
                .run("draw", () -> key(mc.options.keyAttack, true))
                .waitUntil("at full draw", 60, () -> machine(mc).phase() == CombatStateMachine.Phase.CHARGING
                        && machine(mc).attackHeldTicks() >= CantorRules.DRAW_TICKS + 2)
                .screenshot("cantor_fp_drawn")
                .run("a camera to the side", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 p = mc.player.position();
                    camera.place(p.add(-2.8, 1.5, 1.4), p.add(0, 1.3, 0.3));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("cantor_third_drawn")
                .run("front camera", () -> camera.place(mc.player.position().add(1.8, 1.7, 3.0), mc.player.position().add(0, 1.3, 0)))
                .waitTicks(3)
                .screenshot("cantor_third_drawn_front")
                .run("loose", () -> key(mc.options.keyAttack, false))
                .waitTicks(30)
                .screenshot("cantor_third_idle")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .run("clear", CantorScenario::clearAll)
                .waitTicks(10);
        chord(steps, mc);
        noteLife(steps, mc);
        cadence(steps, mc);
        crown(steps, mc);
        steps.log("summary", () -> "SUMMARY\n  " + String.join("\n  ", summary));
    }

    // ------------------------------------------------------------------ notes and the chord

    private void chord(Steps steps, Minecraft mc) {
        long[] chordAt = {0};
        // (the base is only known once the run has started: every point is worked out as its step runs)
        chargedShot(steps, mc, "the first note (3 left, 7 out)", () -> at(-3.0, 7.0).add(0, 0.02, 0));
        steps.waitUntil("a note where it landed", 40, () -> ServerQuery.ask(p -> Cantor.notesOf(p).size()) == 1);
        chargedShot(steps, mc, "the second note (3 right, 7 out)", () -> at(3.0, 7.0).add(0, 0.02, 0));
        steps.waitUntil("two notes", 40, () -> ServerQuery.ask(p -> Cantor.notesOf(p).size()) == 2)
                .log("the notes", () -> ServerQuery.ask(p -> {
                    List<String> out = new ArrayList<>();
                    for (ResonantNote n : Cantor.notesOf(p)) {
                        out.add(String.format(Locale.ROOT, "tone %+d at (%.1f, %.1f, %.1f)", n.tone(), n.getX() - base.getX(),
                                n.getY() - base.getY(), n.getZ() - base.getZ()));
                    }
                    String s = "charged shots left notes: " + String.join(", ", out) + " (D, then E: up D major pentatonic)";
                    summary.add(s);
                    return s;
                }))
                .check("their tones are D then E", () -> ServerQuery.ask(p -> {
                    List<ResonantNote> notes = Cantor.notesOf(p);
                    return notes.size() == 2 && notes.get(0).tone() == 0 && notes.get(1).tone() == 2;
                }))
                .run("a rooted skeleton inside the coming triangle", () -> skeletonId = ServerQuery.ask(p -> {
                    Skeleton s = LastLightScenario.archer(p, at(1.4, 9.0), TAG);
                    return s.getId();
                }))
                .waitUntil("it shoots at us (not yet silenced)", 200, () -> skeletonShots.stream().anyMatch(Shot::arrived));
        chargedShot(steps, mc, "the third note (12 out): the chord", () -> at(0.0, 12.0).add(0, 0.02, 0));
        steps.waitUntil("the chord rings", 40, () -> ServerQuery.ask(p -> around(p, ChordField.class).size()) == 1)
                .run("mark", () -> {
                    chordAt[0] = now();
                    hits.clear();
                })
                .check("its notes became its corners", () -> ServerQuery.ask(p -> Cantor.notesOf(p).isEmpty()))
                .waitTicks(8)
                .check("the skeleton in it is Silenced", () -> ServerQuery.ask(p -> p.serverLevel().getEntity(skeletonId)
                        instanceof LivingEntity s && s.hasEffect(UnsungRegistry.SILENCED)))
                .run("a camera above and behind", () -> {
                    camera = DevCamera.create(mc.level);
                    camera.place(at(-4.0, 3.0).add(0, 4.2, 0), at(0.3, 9.0).add(0, 0.3, 0));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("cantor_chord")
                .waitUntil("a pulse", 30, () -> hits.stream().anyMatch(h -> h.type().equals(CHORD.toString())))
                .screenshot("cantor_chord_pulse")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .waitUntil("the chord's 100 ticks run out", 140, () -> ServerQuery.ask(p -> around(p, ChordField.class).isEmpty()))
                .log("the chord", () -> {
                    List<Hit> pulses = hits.stream().filter(h -> h.type().equals(CHORD.toString())).toList();
                    List<String> at = new ArrayList<>();
                    pulses.forEach(h -> at.add(String.format(Locale.ROOT, "%.2f@%d", h.amount(), h.time() - chordAt[0])));
                    long tried = skeletonShots.stream().filter(s -> !s.arrived() && s.time() >= chordAt[0] && s.time() <= chordAt[0] + 100).count();
                    long arrived = skeletonShots.stream().filter(s -> s.arrived() && s.time() >= chordAt[0] + 1 && s.time() <= chordAt[0] + 100).count();
                    String s = String.format(Locale.ROOT, "chord: the skeleton inside took %d pulses (%s; 4 every 20 ticks), "
                            + "loosed %d shots while in it, %d arrived", pulses.size(), String.join(" ", at), tried, arrived);
                    summary.add(s);
                    return s;
                })
                .check("five pulses of 4, 20 ticks apart", () -> {
                    List<Hit> pulses = hits.stream().filter(h -> h.type().equals(CHORD.toString())).toList();
                    if (pulses.size() != 5) {
                        return false;
                    }
                    for (int i = 0; i < pulses.size(); i++) {
                        if (Math.abs(pulses.get(i).amount() - CantorRules.PULSE_DAMAGE) > 1e-3
                                || (i > 0 && pulses.get(i).time() - pulses.get(i - 1).time() != CantorRules.PULSE_TICKS)) {
                            return false;
                        }
                    }
                    return true;
                })
                .check("Silenced, it shot nothing that arrived (and it did try)", () -> {
                    long tried = skeletonShots.stream().filter(s -> !s.arrived() && s.time() >= chordAt[0] && s.time() <= chordAt[0] + 100).count();
                    long arrived = skeletonShots.stream().filter(s -> s.arrived() && s.time() >= chordAt[0] + 1 && s.time() <= chordAt[0] + 100).count();
                    return tried >= 1 && arrived == 0;
                })
                .run("clear", CantorScenario::clearAll)
                .waitTicks(10);
    }

    private void noteLife(Steps steps, Minecraft mc) {
        long[] placed = {0};
        chargedShot(steps, mc, "a lone note", () -> at(0, 9.0).add(0, 0.02, 0));
        steps.waitUntil("the note", 40, () -> ServerQuery.ask(p -> Cantor.notesOf(p).size()) == 1)
                .run("mark", () -> placed[0] = ServerQuery.ask(p -> Cantor.notesOf(p).get(0).placed()))
                .waitUntil("95 ticks on", 120, () -> now() >= placed[0] + 95)
                .check("still sounding at 95 ticks", () -> ServerQuery.ask(p -> Cantor.notesOf(p).size()) == 1)
                .waitUntil("gone by 101", 20, () -> ServerQuery.ask(p -> around(p, ResonantNote.class).isEmpty()))
                .log("note life", () -> {
                    String s = "a lone note sounded 100 ticks (still there at 95, gone at " + (now() - placed[0]) + ")";
                    summary.add(s);
                    return s;
                })
                .run("clear", CantorScenario::clearAll)
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ Cadence

    private void cadence(Steps steps, Minecraft mc) {
        long[] from = {0};
        double[] res = {0, 0};
        long[] at = {0, 0};
        steps.run("aim at the ground 10 out", () -> stand(mc, at(0, 10.0)))
                .run("full and ready", CantorScenario::fillAndReady)
                .waitTicks(10)
                .run("mark", () -> {
                    from[0] = now();
                    ownArrows.clear();
                    res[0] = ServerQuery.ask(p -> PlayerCombat.of(p).machine().resonance());
                    at[0] = now();
                })
                .press(mc.options.keyUse)
                .waitUntil("three arrows", 30, () -> ownArrows.size() >= 3)
                .run("Resonance after", () -> {
                    res[1] = ServerQuery.ask(p -> PlayerCombat.of(p).machine().resonance());
                    at[1] = now();
                })
                .waitTicks(10)
                .press(mc.options.keyUse)
                .waitTicks(25)
                .log("Cadence", () -> {
                    List<String> t = new ArrayList<>();
                    ownArrows.forEach(x -> t.add(Long.toString(x - ownArrows.get(0))));
                    String s = String.format(Locale.ROOT, "Cadence: %d arrows at ticks %s (three in 12); Resonance %.2f -> %.2f "
                                    + "over %d ticks out of combat (the drift took %.2f): cost %.2f (GDD 30); a second press within "
                                    + "its 7 s cooldown loosed nothing; chords from its notes: %d",
                            ownArrows.size(), String.join(",", t), res[0], res[1], at[1] - at[0], drift(at), cost(res, at),
                            ServerQuery.ask(p -> around(p, ChordField.class).size()));
                    summary.add(s);
                    return s;
                })
                .check("three arrows within 12 ticks, 30 Resonance, the second press refused", () -> ownArrows.size() == 3
                        && ownArrows.get(2) - ownArrows.get(0) < 12 && Math.abs(cost(res, at) - 30.0) < 0.3)
                .check("its three notes struck a chord", () -> ServerQuery.ask(p -> around(p, ChordField.class).size()) == 1)
                .run("a camera above", () -> {
                    camera = DevCamera.create(mc.level);
                    camera.place(at(3.0, 5.5).add(0, 4.2, 0), at(0, 11.5));
                    camera.use();
                })
                .waitTicks(3)
                .screenshot("cantor_cadence_chord")
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                })
                .run("clear", CantorScenario::clearAll)
                .waitTicks(10);
    }

    /** Out of combat Resonance drifts toward 30% of its maximum at 0.25 a tick: what it took between the two reads. */
    private static double drift(long[] at) {
        return 0.25 * (at[1] - at[0]);
    }

    private static double cost(double[] res, long[] at) {
        return res[0] - res[1] - drift(at);
    }

    // ------------------------------------------------------------------ the Crown

    private void crown(Steps steps, Minecraft mc) {
        BlockPos[] pos = {BlockPos.ZERO};
        long[] t = {0, 0};
        steps.run("where the Crown goes", () -> pos[0] = base.offset(0, 0, 3))
                .run("place it", () -> ServerQuery.ask(p -> p.serverLevel().setBlockAndUpdate(pos[0], Relics.HELIARCHS_CROWN.get().defaultBlockState())))
                .waitUntil("its block entity on the client", 40, () -> mc.level.getBlockEntity(pos[0]) instanceof CrownBlockEntity)
                .waitTicks(10)
                .log("the Crown's light", () -> {
                    int at = ServerQuery.ask(p -> p.level().getBrightness(LightLayer.BLOCK, pos[0]));
                    int two = ServerQuery.ask(p -> p.level().getBrightness(LightLayer.BLOCK, pos[0].offset(2, 0, 0)));
                    String s = "the Heliarch's Crown gives block light " + at + " (two blocks off: " + two + ")";
                    summary.add(s);
                    return s;
                })
                .check("light 12 at the Crown", () -> ServerQuery.ask(p -> p.level().getBrightness(LightLayer.BLOCK, pos[0])) == HeliarchsCrownBlock.LIGHT)
                .run("a camera close by", () -> {
                    camera = DevCamera.create(mc.level);
                    Vec3 c = Vec3.atBottomCenterOf(pos[0]);
                    camera.place(c.add(1.4, 1.4, -1.2), c.add(0, 0.6, 0));
                    camera.use();
                    t[0] = mc.level.getGameTime();
                })
                .waitTicks(3)
                .screenshot("crown_orbit_a")
                .waitTicks(40)
                .run("the second look", () -> t[1] = mc.level.getGameTime())
                .screenshot("crown_orbit_b")
                .run("dusk for its glow", () -> ServerQuery.ask(p -> {
                    p.serverLevel().setDayTime(13000);
                    return true;
                }))
                .waitTicks(20)
                .screenshot("crown_dusk")
                .log("the Crown's sun", () -> {
                    double turned = Math.toDegrees(HeliarchsCrownBlock.orbitAngle(t[1], pos[0]) - HeliarchsCrownBlock.orbitAngle(t[0], pos[0]));
                    String s = String.format(Locale.ROOT, "the Crown's sun turned %.0f degrees between two looks %d ticks apart "
                            + "(one turn in %d ticks)", turned, t[1] - t[0], HeliarchsCrownBlock.ORBIT_TICKS);
                    summary.add(s);
                    return s;
                })
                .run("back to the eyes", () -> {
                    camera.remove();
                    camera = null;
                });
    }
}
