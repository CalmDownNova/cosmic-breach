package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.guardian.heliarch.HeliarchClient;
import com.cosmicbreach.client.guardian.heliarch.HeliarchSky;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.heliarch.CollapseSchedule;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * {@code heliarch-looks}: the Heliarch photographed in every phase from the arena's rim, the dais and above, the player
 * (in survival, made unhurtable) standing where its attacks aim. Attacks are held between shots and let go one at a
 * time, each photographed at the ticks that show its telegraph.
 */
final class HeliarchLooks {
    private static final long HOLD = 100_000;
    private final HeliarchScenario s;

    HeliarchLooks(HeliarchScenario s) {
        this.s = s;
    }

    void steps(Steps steps, Minecraft mc) {
        Vec3 core = HeliarchArena.core(HeliarchArena.CORE_HEIGHT);
        steps.command("gamemode survival")
                .command("cosmicbreach debug level 36")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("effect give @s minecraft:regeneration 100000 4 true")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .command("item replace entity @s armor.head with cosmicbreach:choir_regalia_circlet")
                .command("item replace entity @s armor.chest with cosmicbreach:choir_regalia_vestment")
                .command("item replace entity @s armor.legs with cosmicbreach:choir_regalia_tassets")
                .command("item replace entity @s armor.feet with cosmicbreach:choir_regalia_sabatons")
                .command("give @s cosmicbreach:meridian[cosmicbreach:gear_tier=3]")
                .run("on the east rim", () -> CryptKit.tp(mc, new Vec3(26.5, SanctumLayout.ARENA_Y, 0.5), core))
                .waitTicks(10)
                .command("cosmicbreach debug heliarch summon")
                .waitUntil("it rises", 60, HeliarchScenario::fighting)
                .command("cosmicbreach debug heliarch hold " + HOLD)
                // ---------------------------------------------------------------- the intro
                .run("a view from the south-east rim", () -> s.camera(mc, HeliarchScenario.at(135, 24, 2.2), core.add(0, 1.5, 0)))
                .waitUntil("the debris lifts", 100, () -> introAt(mc, 30))
                .screenshot("heliarch_intro_debris")
                .waitUntil("the plates fly in", 200, () -> introAt(mc, 105))
                .screenshot("heliarch_intro_assembling")
                .run("from the entrance, Solenne eclipsed behind it", () -> {
                    double entrance = HeliarchScenario.side() > 0 ? 180.0 : 0.0;
                    s.camera(mc, HeliarchScenario.at(entrance, 27.5, 1.8), HeliarchArena.CENTRE.add(0, 7.5, 0));
                })
                .waitUntil("the eclipse closes", 200, () -> introAt(mc, 150))
                .screenshot("heliarch_intro_eclipse")
                .run("back to the south-east rim", () -> s.camera(mc, HeliarchScenario.at(135, 24, 2.2), core.add(0, 1.5, 0)))
                .waitUntil("assembled, the bar full", 200, () -> introAt(mc, 185))
                .screenshot("heliarch_intro_assembled")
                .log("intro", () -> String.format(Locale.ROOT, "sky eclipse %.2f, lines heard %s", HeliarchSky.eclipse(), HeliarchClient.spoken()))
                .waitUntil("phase 1", 200, () -> HeliarchScenario.ask(h -> h.state() == State.REGENT));
        // ---------------------------------------------------------------- phase 1 at rest, three views
        views(steps, mc, "regent_closed", core);
        // ---------------------------------------------------------------- each telegraph
        attack(steps, mc, "sunderfall", Action.SUNDERFALL);
        steps.run("outside the rim, clear of the hand and the pillars", () -> s.camera(mc, HeliarchScenario.at(78, 32, 4.5),
                        new Vec3(25.5, 68.0, 0.5)))
                .waitUntil("the hand is up, the ring filling", 60, () -> actionAt(mc, 12))
                .screenshot("heliarch_sunderfall_ring")
                .waitUntil("the glint", 30, () -> actionAt(mc, 21))
                .screenshot("heliarch_sunderfall_glint")
                .waitUntil("the slam", 30, () -> actionAt(mc, 27))
                .screenshot("heliarch_sunderfall_slam")
                .waitUntil("it ends", 120, () -> idle(mc));
        attack(steps, mc, "corona_sweep", Action.CORONA_SWEEP);
        steps.run("from the south-east, high", () -> s.camera(mc, HeliarchScenario.at(135, 26, 9.0), core.add(0, -2.0, 0)))
                .waitUntil("the band glows", 60, () -> actionAt(mc, 22))
                .screenshot("heliarch_sweep_band")
                .run("inside the pillars, behind the wall's wake", () -> {
                    double wall = HeliarchScenario.ask(h -> (double) h.actionAngle()) + 126.0;
                    s.camera(mc, HeliarchScenario.at(wall - 30.0, 19.5, 6.0), HeliarchScenario.at(wall - 6.0, 13.0, 1.0));
                })
                .waitUntil("the wall runs", 60, () -> actionAt(mc, 37))
                .screenshot("heliarch_sweep_wall")
                .waitUntil("it ends", 120, () -> idle(mc));
        attack(steps, mc, "solar_lance", Action.SOLAR_LANCE);
        steps.run("from beside the player", () -> s.camera(mc, HeliarchScenario.at(45, 19, 3.0), core))
                .waitUntil("the line tracks", 60, () -> actionAt(mc, 10))
                .screenshot("heliarch_lance_tracking")
                .waitUntil("the line locks", 60, () -> actionAt(mc, 23))
                .screenshot("heliarch_lance_locked")
                .waitUntil("the lance fires", 60, () -> actionAt(mc, 28))
                .screenshot("heliarch_lance_fire")
                .waitUntil("it ends", 120, () -> idle(mc));
        attack(steps, mc, "halo_shed", Action.HALO_SHED);
        steps.run("from the south-east rim", () -> s.camera(mc, HeliarchScenario.at(135, 22, 3.0), core.add(0, 1.0, 0)))
                .waitUntil("the plates flash", 60, () -> actionAt(mc, 9))
                .screenshot("heliarch_shed_flash")
                .run("from above", () -> s.camera(mc, HeliarchScenario.at(160, 18, 30.0), HeliarchArena.CENTRE))
                .waitUntil("the plates hang at the rim", 90, () -> actionAt(mc, 60))
                .screenshot("heliarch_shed_out_above")
                .waitUntil("the red trails", 90, () -> actionAt(mc, 96))
                .screenshot("heliarch_shed_trails_above")
                .run("at eye level", () -> s.camera(mc, HeliarchScenario.at(180, 20, 1.6), core))
                .waitUntil("still the trails", 30, () -> actionAt(mc, 100))
                .screenshot("heliarch_shed_trails")
                .waitUntil("the plates come back", 30, () -> actionAt(mc, 110))
                .screenshot("heliarch_shed_return")
                .waitUntil("it ends", 120, () -> idle(mc));
        // ---------------------------------------------------------------- the Break
        steps.command("cosmicbreach debug heliarch break")
                .run("from the south", () -> s.camera(mc, HeliarchScenario.at(180, 20, 3.0), core.add(0, -1.0, 0)))
                .waitTicks(20)
                .screenshot("heliarch_broken")
                .waitUntil("the Break ends", 200, () -> !HeliarchScenario.ask(h -> h.brokenNow(h.level().getGameTime())));
        // ---------------------------------------------------------------- the Hollowing
        steps.command("cosmicbreach debug heliarch hollow")
                .run("from the south-east rim", () -> s.camera(mc, HeliarchScenario.at(135, 27, 3.5), core.add(0, 2.0, 0)))
                .waitUntil("the plates tear free", 60, () -> stateAt(mc, State.HOLLOWING, 20))
                .screenshot("heliarch_hollowing_tear")
                .waitUntil("the plates fall", 60, () -> stateAt(mc, State.HOLLOWING, 42))
                .screenshot("heliarch_hollowing_fall")
                .waitUntil("the monoliths stand", 60, () -> stateAt(mc, State.HOLLOWING, 62))
                .screenshot("heliarch_hollowing_monoliths")
                .waitUntil("phase 2", 120, () -> HeliarchScenario.ask(h -> h.state() == State.HOLLOW))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .waitTicks(10);
        views(steps, mc, "hollow", HeliarchArena.core(HeliarchArena.HIGH_HEIGHT - 6.0));
        steps.run("a monolith, close", () -> s.camera(mc, HeliarchScenario.at(40, 17, 2.2), HeliarchScenario.at(60, 12, 2.0)))
                .waitTicks(4)
                .screenshot("heliarch_monolith_close");
        // a lash: the player within a tendril's reach
        steps.run("the player near a tendril", () -> CryptKit.tp(mc, HeliarchScenario.at(45, 14, 0).add(0, 0, 0), core))
                .command("cosmicbreach debug heliarch hold 0")
                .waitUntil("a lash begins", 300, () -> anyLash(mc, 6))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .run("behind the tendril", () -> s.camera(mc, HeliarchScenario.at(70, 21, 5.0), HeliarchScenario.at(25, 9, 0.5)))
                .waitUntil("the cracks glow", 30, () -> anyLash(mc, 13))
                .screenshot("heliarch_lash_tell")
                .waitUntil("the lash lies", 30, () -> anyLash(mc, 27))
                .screenshot("heliarch_lash_down")
                .waitTicks(40);
        attack(steps, mc, "eclipse_beam", Action.ECLIPSE_BEAM);
        steps.run("from above", () -> s.camera(mc, HeliarchScenario.at(200, 16, 26.0), HeliarchArena.CENTRE))
                .waitUntil("the warning", 60, () -> actionAt(mc, 28))
                .screenshot("heliarch_beam_tell_above")
                .run("at eye level, side-on to where the wedge will be", () -> {
                    double at = HeliarchScenario.ask(h -> com.cosmicbreach.guardian.heliarch.EclipseCover.beamAngle(h.actionAngle(), h.beamDir(),
                            58 - com.cosmicbreach.guardian.heliarch.HeliarchMoves.BEAM_TELL));
                    s.camera(mc, HeliarchScenario.at(at + 55.0, 20, 2.2), HeliarchScenario.at(at, 11, 2.5));
                })
                .waitUntil("the sweep", 60, () -> actionAt(mc, 58))
                .screenshot("heliarch_beam")
                .run("from above", () -> s.camera(mc, HeliarchScenario.at(200, 16, 26.0), HeliarchArena.CENTRE))
                .waitUntil("later in the sweep", 60, () -> actionAt(mc, 80))
                .screenshot("heliarch_beam_above")
                .waitUntil("it ends", 200, () -> idle(mc));
        attack(steps, mc, "inversion", Action.INVERSION);
        steps.run("the seeds, from the dais", () -> s.camera(mc, HeliarchScenario.at(250, 9, 2.0), HeliarchScenario.at(60, 14, 3.0)))
                .waitUntil("seeds in the air", 120, () -> actionAt(mc, 70))
                .screenshot("heliarch_star_seeds")
                .waitUntil("it ends", 200, () -> idle(mc))
                // ------------------------------------------------------------ Nova
                .command("cosmicbreach debug heliarch nova")
                .waitUntil("Nova channels", 100, () -> HeliarchScenario.ask(h -> h.action() == Action.NOVA))
                .run("from the rim", () -> s.camera(mc, HeliarchScenario.at(135, 26, 4.0), HeliarchArena.core(HeliarchArena.HIGH_HEIGHT - 6.0)))
                .waitUntil("the shield and the clock", 200, () -> actionAt(mc, 150))
                .screenshot("heliarch_nova")
                .command("cosmicbreach debug heliarch shield")
                .waitUntil("broken", 40, () -> HeliarchScenario.ask(h -> h.action() != Action.NOVA))
                .run("the heart laid bare", () -> s.camera(mc, HeliarchScenario.at(135, 20, 3.0), core))
                .waitTicks(20)
                .screenshot("heliarch_nova_stunned")
                .waitUntil("the stun ends", 300, () -> HeliarchScenario.ask(h -> h.level().getGameTime() > h.stunUntil() + 25))
                // ------------------------------------------------------------ the Collapse
                .command("cosmicbreach debug heliarch collapse")
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .run("the player back to the dais", () -> CryptKit.tp(mc, HeliarchScenario.at(200, 6, 0), core))
                .run("from above, toward the first segment to fall", () -> {
                    HollowHeliarch hc = HeliarchClient.heliarch();
                    int seg = new CollapseSchedule(hc == null ? 1 : hc.side()).cracks().get(0).segment();
                    double mid = 45.0 * seg + 22.5;
                    s.camera(mc, HeliarchScenario.at(mid - 32, 18, 18.0), HeliarchScenario.at(mid, 26.5, 0.0));
                })
                .waitUntil("a rim segment cracks", 120, () -> collapseAt(mc, 60))
                .screenshot("heliarch_collapse_crack")
                .waitUntil("it falls", 120, () -> collapseAt(mc, 112))
                .screenshot("heliarch_collapse_fall")
                .waitUntil("Solar Rain", 200, () -> HeliarchScenario.ask(h -> h.rainCircles() != null))
                .waitTicks(10)
                .screenshot("heliarch_solar_rain")
                .command("cosmicbreach debug heliarch ahead 2450")
                .waitUntil("the rim and the outer ring are gone", 200, () -> HeliarchScenario.ask(h -> h.segmentsFallen() >= 16))
                .run("from above the south-east", () -> s.camera(mc, HeliarchScenario.at(140, 34, 22.0), HeliarchArena.CENTRE))
                .waitTicks(60)
                .screenshot("heliarch_collapse_two_minutes")
                .run("at eye level on the dais", () -> s.camera(mc, HeliarchScenario.at(160, 12, 1.8), core))
                .waitTicks(4)
                .screenshot("heliarch_collapse_eye")
                // ------------------------------------------------------------ the death
                .command("cosmicbreach debug heliarch kill")
                .run("from the dais", () -> s.camera(mc, HeliarchScenario.at(160, 12, 2.0), core.add(0, 1.0, 0)))
                .waitUntil("the eclipse breaks, the sky clears", 90, () -> stateAt(mc, State.DYING, 55))
                .screenshot("heliarch_dawn")
                .waitUntil("the Reliquaries", 200, () -> stateAt(mc, State.DYING, 112))
                .run("the Reliquary, close", () -> s.camera(mc, HeliarchScenario.at(20, 7.5, 2.4), HeliarchArena.CENTRE.add(0, 0.5, 0)))
                .waitTicks(3)
                .screenshot("heliarch_reliquary")
                .waitUntil("the fight is over", 200, () -> !HeliarchScenario.fighting())
                .waitUntil("the seal forms", 200, HeliarchSky::sealed)
                .run("the seal from the rim, looking up", () -> s.camera(mc, HeliarchScenario.at(135, 26, 2.0),
                        HeliarchArena.CENTRE.add(0, 44, 0)))
                .waitTicks(100)
                .screenshot("heliarch_seal_from_rim")
                .run("the seal from far above the Breach", () -> s.camera(mc, HeliarchScenario.at(135, 80, 90.0), HeliarchArena.CENTRE.add(0, 30, 0)))
                .waitTicks(10)
                .screenshot("heliarch_seal_from_afar")
                .log("seal", () -> "seal frames " + HeliarchSky.sealFrames() + ", eclipse frames " + HeliarchSky.eclipseFrames())
                .log("drawn", () -> "telegraph frames " + HeliarchClient.drawnFrames());
    }

    /** Photographs from the rim, the dais and above. */
    private void views(Steps steps, Minecraft mc, String name, Vec3 core) {
        steps.run("from the rim", () -> s.camera(mc, HeliarchScenario.at(135, 27, 1.62), core))
                .waitTicks(4)
                .screenshot("heliarch_" + name + "_rim")
                .run("from the dais", () -> {
                    // in phase 1 the hands rest at its sides: stand a little off its front, clear of them; in phase 2
                    // between two tendrils
                    HollowHeliarch hc = HeliarchClient.heliarch();
                    double a = hc != null && hc.state() == State.REGENT ? hc.facing() + 25.0 : 180.0;
                    s.camera(mc, HeliarchScenario.at(a, 9.5, 1.62), core);
                })
                .waitTicks(4)
                .screenshot("heliarch_" + name + "_dais")
                .run("from above", () -> s.camera(mc, HeliarchScenario.at(135, 16, 26.0), core))
                .waitTicks(4)
                .screenshot("heliarch_" + name + "_above");
    }

    /** Lets one attack go: the hold off, the attack forced, the hold back on once it has begun. */
    private void attack(Steps steps, Minecraft mc, String name, Action a) {
        steps.command("cosmicbreach debug heliarch hold 0")
                .command("cosmicbreach debug heliarch attack " + name)
                .waitUntil(name + " begins", 120, () -> HeliarchScenario.ask(h -> h.action() == a))
                .command("cosmicbreach debug heliarch hold " + HOLD);
    }

    private static boolean introAt(Minecraft mc, int t) {
        return HeliarchScenario.ask(h -> h.state() != State.INTRO || h.level().getGameTime() - h.stateStart() >= t);
    }

    private static boolean actionAt(Minecraft mc, int t) {
        return HeliarchScenario.ask(h -> h.level().getGameTime() - h.actionStart() >= t);
    }

    private static boolean stateAt(Minecraft mc, State st, int t) {
        return HeliarchScenario.ask(h -> h.state() == st && h.level().getGameTime() - h.stateStart() >= t);
    }

    private static boolean collapseAt(Minecraft mc, int t) {
        return HeliarchScenario.ask(h -> h.state() == State.COLLAPSE && h.level().getGameTime() - h.collapseStart() >= t);
    }

    private static boolean idle(Minecraft mc) {
        return HeliarchScenario.ask(h -> h.action() == Action.NONE);
    }

    private static boolean anyLash(Minecraft mc, int t) {
        return HeliarchScenario.ask(h -> {
            long now = h.level().getGameTime();
            for (int i = 0; i < 4; i++) {
                long ls = h.lashStart(i);
                if (ls != Long.MIN_VALUE && now - ls >= t && now - ls < t + 6) {
                    return true;
                }
            }
            return false;
        });
    }
}
