package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.guardian.heliarch.HeliarchSky;
import com.cosmicbreach.client.sanctum.SanctumClient;
import com.cosmicbreach.client.voice.BossVoiceClient;
import com.cosmicbreach.client.voice.EchoClient;
import com.cosmicbreach.guardian.GuardianRewards;
import com.cosmicbreach.guardian.heliarch.CollapseSchedule;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HeliarchArenaRules;
import com.cosmicbreach.guardian.heliarch.HeliarchLoot;
import com.cosmicbreach.guardian.heliarch.HeliarchMoves;
import com.cosmicbreach.guardian.heliarch.HeliarchRegistry;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.guardian.heliarch.ReliquaryBlockEntity;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.structure.sanctum.SanctumThroneBlock;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.voice.boss.BossVoices;
import com.cosmicbreach.world.weather.EclipseSurge;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Heliarch's mechanics with real inputs (movement, jump, attack, dash, parry, use), each checked against the
 * server. {@code heliarch-regent}: the summon by a Heart on the throne, the arena's rules, a parried Sunderfall
 * filling the gauge and a Break, each phase 1 attack and its answer. {@code heliarch-hollow}: the Hollowing, the Eclipse
 * Beam behind a monolith and the pip it takes, a tendril cut, the Gravity Inversion, Nova let go once and broken once.
 * {@code heliarch-end}: the Collapse's schedule and Solar Rain, a death and the player_down line, the kill, the Echo,
 * the Reliquary opened once and the world's seal.
 */
final class HeliarchMechanics {
    private static final long HOLD = 100_000;
    private static final int SLOT_WEAPON = 0;
    private static final int SLOT_BLOCK = 1;
    private static final int SLOT_PEARL = 2;
    private static final int SLOT_HEART = 3;
    private static final int SLOT_EMPTY = 8;

    private final HeliarchScenario s;
    private final List<String> results;
    private float healthMark;
    private int tallyMark;
    private int[] pipsMark;
    private double resonanceMark;
    private int glowMark;
    private Vec3 posMark;
    private long xpMark;
    private int pointsMark;
    private boolean equipped;
    private long novaMark;

    HeliarchMechanics(HeliarchScenario s) {
        this.s = s;
        this.results = s.results();
    }

    // ------------------------------------------------------------------ the player

    void equip(Steps steps, Minecraft mc) {
        if (equipped) {
            return;
        }
        equipped = true;
        steps.command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .command("cosmicbreach debug level 36")
                .command("cosmicbreach debug stats 20 4 0 12")
                .waitTicks(2)
                .command("clear @s")
                .command("item replace entity @s armor.head with cosmicbreach:choir_regalia_circlet")
                .command("item replace entity @s armor.chest with cosmicbreach:choir_regalia_vestment")
                .command("item replace entity @s armor.legs with cosmicbreach:choir_regalia_tassets")
                .command("item replace entity @s armor.feet with cosmicbreach:choir_regalia_sabatons")
                .command("item replace entity @s hotbar." + SLOT_WEAPON + " with cosmicbreach:meridian[cosmicbreach:gear_tier=3]")
                .command("effect give @s minecraft:saturation 100000 0 true")
                .command("gamerule naturalRegeneration true")
                .run("hold the weapon", () -> select(mc, SLOT_WEAPON))
                .waitUntil("level 36 in the Regalia with Meridian at tier III", 80, () -> ServerQuery.ask(p -> Attunements.of(p).level() == 36
                        && p.getArmorValue() >= 18 && p.getMainHandItem().is(com.cosmicbreach.registry.ModItems.MERIDIAN.get())))
                .run("note the player", () -> results.add(ServerQuery.ask(p -> String.format(Locale.ROOT,
                        "player: level 36, Choir Regalia (armor %d, toughness %.0f), Meridian tier III, health %.1f, %s", p.getArmorValue(),
                        p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), p.getMaxHealth(), com.cosmicbreach.progression.ProgressionStats.of(p)))));
    }

    /** A fight in phase 1, for a part run on its own (summoned by command; the Heart's summon is the regent part's). */
    private void ensureFight(Steps steps, Minecraft mc) {
        steps.run("on the east rim", () -> CryptKit.tp(mc, new Vec3(22.5, SanctumLayout.ARENA_Y, 0.5), HeliarchArena.core(HeliarchArena.CORE_HEIGHT)))
                .waitTicks(5)
                .command("cosmicbreach debug heliarch summon")
                .waitUntil("it rises", 60, HeliarchScenario::fighting)
                .waitUntil("phase 1", 400, () -> HeliarchScenario.ask(h -> h.state() == State.REGENT))
                .command("cosmicbreach debug heliarch hold " + HOLD);
    }

    // ------------------------------------------------------------------ 1. the Regent

    void regent(Steps steps, Minecraft mc) {
        equip(steps, mc);
        Vec3 core = HeliarchArena.core(HeliarchArena.CORE_HEIGHT);
        int side = HeliarchScenario.side();
        steps.command("setblock 20 64 3 minecraft:cobblestone")
                .waitTicks(3)
                .check("a block placed on the arena before the fight", () -> CryptKit.server(srv -> HeliarchScenario.level(srv)
                        .getBlockState(new BlockPos(20, 64, 3)).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)))
                // a first summon for this player, so the solo opener is the one that plays
                .command("advancement revoke @s only cosmicbreach:guardian/breach_sealed")
                .command("item replace entity @s hotbar." + SLOT_HEART + " with cosmicbreach:dying_star_heart")
                .run("before the throne", () -> CryptKit.tp(mc, new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5 + side * 2.6),
                        new Vec3(0.5, SanctumLayout.ARENA_Y + 0.6, 0.5)))
                .waitTicks(10)
                .run("hold the Heart", () -> select(mc, SLOT_HEART))
                .waitTicks(3)
                .run("aim at the throne's seat", () -> CryptKit.aim(mc, new Vec3(0.5, SanctumLayout.ARENA_Y + 0.5, 0.5)))
                // one voice with the guide: a boss line is dropped if the guide is still speaking, so the fight starts in her quiet
                .waitUntil("the guide is quiet", 900, () -> EchoClient.playing() == null && EchoClient.waiting().isEmpty())
                .press(mc.options.keyUse)
                .waitUntil("the Heliarch rises from the Heart", 60, HeliarchScenario::fighting)
                .check("1,200 health for one player, the Heart burning in the throne", () -> HeliarchScenario.ask(h -> Math.abs(h.maxHealth() - 1200)
                        < 1e-6 && h.playersAtStart() == 1) && CryptKit.server(srv -> {
                    BlockState t = HeliarchScenario.level(srv).getBlockState(SanctumArena.THRONE);
                    return t.is(SanctumRegistry.SANCTUM_THRONE.get()) && t.getValue(SanctumThroneBlock.HEART);
                }))
                .waitUntil("the Heart was spent", 20, () -> count(mc, SanctumRegistry.DYING_STAR_HEART.get()) == 0)
                .check("the block placed before the fight was cleared", () -> CryptKit.server(srv -> HeliarchScenario.level(srv)
                        .getBlockState(new BlockPos(20, 64, 3)).isAir()))
                .waitUntil("the intro line", 80, () -> BossVoiceClient.heard("heliarch", "intro"))
                .run("into the arena", () -> CryptKit.tp(mc, new Vec3(12.5, SanctumLayout.ARENA_Y, 0.5), core))
                .waitTicks(5)
                // nothing can be placed
                .command("item replace entity @s hotbar." + SLOT_BLOCK + " with minecraft:cobblestone 8")
                .run("hold the stone", () -> select(mc, SLOT_BLOCK))
                .waitTicks(3)
                .run("aim at the floor ahead", () -> CryptKit.aim(mc, new Vec3(10.5, SanctumLayout.ARENA_Y - 0.5, 0.5)))
                .press(mc.options.keyUse)
                .waitTicks(5)
                .log("placing", () -> String.format(Locale.ROOT, "stone %d, the spot %s, refused %d, holding %s, looking at %s",
                        count(mc, Items.COBBLESTONE), CryptKit.server(srv -> HeliarchScenario.level(srv).getBlockState(new BlockPos(10, 64, 0))),
                        refused(), mc.player.getMainHandItem(), mc.hitResult == null ? "nothing" : mc.hitResult.getLocation()))
                .check("the Sanctum refuses the block", () -> count(mc, Items.COBBLESTONE) == 8
                        && CryptKit.server(srv -> HeliarchScenario.level(srv).getBlockState(new BlockPos(10, 64, 0)).isAir())
                        && refused() >= 1)
                // no Ender Pearls
                .command("item replace entity @s hotbar." + SLOT_PEARL + " with minecraft:ender_pearl 4")
                .run("hold a pearl", () -> select(mc, SLOT_PEARL))
                .waitTicks(3)
                .run("mark where I stand", () -> posMark = mc.player.position())
                .run("aim across the arena", () -> CryptKit.aim(mc, new Vec3(-6.5, SanctumLayout.ARENA_Y + 3, 0.5)))
                .press(mc.options.keyUse)
                .waitUntil("the pearl lands and is refused", 80, () -> pearls() >= 1)
                .waitTicks(3)
                .check("still where I threw it from", () -> mc.player.position().distanceTo(posMark) < 1.5)
                .run("hold the weapon", () -> select(mc, SLOT_WEAPON))
                .waitUntil("phase 1", 300, () -> HeliarchScenario.ask(h -> h.state() == State.REGENT))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .check("Solenne eclipsed", () -> HeliarchSky.eclipse() > 0.4f)
                .run("on the rim, where the embers' glow would show", () -> {
                    CryptKit.tp(mc, new Vec3(25.5, SanctumLayout.ARENA_Y, 0.5), core);
                    glowMark = SanctumClient.glowFrames();
                })
                .waitTicks(20)
                .check("the embers' glow is hidden through the fight", () -> SanctumClient.glowFrames() == glowMark)
                // a projectile fired from beyond 40 blocks burns up
                .run("far out in the halls", () -> {
                    int sd = HeliarchScenario.side();
                    CryptKit.tp(mc, new Vec3(0.5, SanctumLayout.HALL_Y, 0.5 + sd * 55.0), core);
                })
                .waitUntil("in the halls", 100, () -> Math.abs(mc.player.getZ()) > 45)
                .run("mark the Heliarch's health", () -> healthMark = (float) (double) HeliarchScenario.ask(HollowHeliarch::health))
                .run("an arrow of mine, loosed from here, lands on the core", () -> CryptKit.server(srv -> {
                    ServerPlayer p = CryptKit.player(srv);
                    ServerLevel level = HeliarchScenario.level(srv);
                    Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), null);
                    Vec3 from = core.add(4.0, 0.0, 0.0);
                    arrow.moveTo(from.x, from.y, from.z);
                    arrow.shoot(-1.0, 0.0, 0.0, 2.5f, 0f);
                    level.addFreshEntity(arrow);
                    return null;
                }))
                .waitTicks(10)
                .check("it burned up: no damage", () -> Math.abs(HeliarchScenario.ask(HollowHeliarch::health) - healthMark) < 1e-6)
                .run("back to the rim", () -> CryptKit.tp(mc, new Vec3(22.5, SanctumLayout.ARENA_Y, 0.5), core))
                .waitUntil("back on the arena", 100, () -> HeliarchArena.inFight(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        // a parried Sunderfall fills the gauge: a Break
        steps.run("heal", CryptKit::heal)
                .command("cosmicbreach debug heliarch gauge 345")
                .run("mark", this::markHealth);
        force(steps, "sunderfall", Action.SUNDERFALL);
        steps.waitUntil("the gold glint", 60, () -> actionAt(mc, HeliarchMoves.SUNDER_GLINT + 1))
                .press(ModKeyMappings.PARRY)
                .waitUntil("the slam lands", 30, () -> actionAt(mc, HeliarchMoves.SUNDER_TELL + 2))
                .log("parry", () -> {
                    float now = CryptKit.health();
                    return HeliarchScenario.ask(h -> String.format(Locale.ROOT, "parries %d, breaks %d, gauge %.2f, health %.1f to %.1f",
                            h.sunderParries(), h.breaks(), h.gaugeFraction(), healthMark, now));
                })
                .check("parried at the glint: no damage, 60 to the gauge fills it, a Break", () -> HeliarchScenario.ask(h -> h.sunderParries() == 1
                        && h.breaks() == 1 && h.brokenNow(h.level().getGameTime())) && CryptKit.health() >= healthMark - 1e-3)
                .screenshot("heliarch_mech_broken")
                .run("into the fallen core's reach", () -> CryptKit.tp(mc, new Vec3(3.3, SanctumLayout.ARENA_Y, 0.5),
                        HeliarchArena.core(HeliarchArena.LOW_HEIGHT)))
                .waitTicks(3)
                .run("mark", () -> healthMark = (float) (double) HeliarchScenario.ask(HollowHeliarch::health))
                .waitUntil("strike the broken core", 90, swing(mc, () -> HeliarchArena.core(HeliarchArena.LOW_HEIGHT), () -> false, 80))
                .log("break hits", () -> String.format(Locale.ROOT, "in the Break: health %.1f to %.1f", healthMark,
                        HeliarchScenario.ask(HollowHeliarch::health)))
                .check("struck in the Break", () -> HeliarchScenario.ask(HollowHeliarch::health) < healthMark - 5)
                .waitUntil("the Break ends", 200, () -> HeliarchScenario.ask(h -> !h.brokenNow(h.level().getGameTime())
                        && h.level().getGameTime() > h.breakStart() + HeliarchMoves.BREAK_TICKS + 25));
        // Sunderfall: dash out of the ring after the glint
        steps.run("on the rim", () -> CryptKit.tp(mc, new Vec3(22.5, SanctumLayout.ARENA_Y, 0.5), core))
                .waitTicks(5)
                .run("heal", CryptKit::heal)
                .run("mark", this::markHealth)
                .run("mark the tally", () -> tallyMark = tally("sunderfall"));
        force(steps, "sunderfall", Action.SUNDERFALL);
        // (a dash takes eight ticks: pressed at 14, it is out of the ring before the slam at 24, whatever the ticks' phase)
        steps.waitUntil("the hand high", 60, () -> actionAt(mc, 14))
                .run("look along the rim", () -> CryptKit.face(mc, new Vec3(22.5, SanctumLayout.ARENA_Y, 20.5), 10f))
                .hold(mc.options.keyUp)
                .press(ModKeyMappings.DASH)
                .waitUntil("the slam lands", 30, () -> actionAt(mc, HeliarchMoves.SUNDER_TELL + 3))
                .release(mc.options.keyUp)
                .check("dashed out of the ring: no Sunderfall damage", () -> tally("sunderfall") == tallyMark && CryptKit.health() >= healthMark - 1e-3)
                .waitUntil("it ends", 120, () -> idle());
        // the control: standing in the ring, the slam hurts
        steps.run("mark", this::markHealth).run("mark the tally", () -> tallyMark = tally("sunderfall"));
        force(steps, "sunderfall", Action.SUNDERFALL);
        steps.waitUntil("the slam lands", 60, () -> actionAt(mc, HeliarchMoves.SUNDER_TELL + 3))
                .log("sunderfall taken", () -> String.format(Locale.ROOT, "standing in the ring: health %.1f to %.1f", healthMark, CryptKit.health()))
                .check("standing in the ring: the slam lands", () -> tally("sunderfall") == tallyMark + 1 && CryptKit.health() < healthMark - 1)
                .waitUntil("it ends", 120, () -> idle())
                .run("heal", CryptKit::heal);
        // Corona Sweep: jump the wall when it comes
        steps.run("in the band, east", () -> CryptKit.tp(mc, HeliarchScenario.at(90, 13.0, 0), core))
                .waitTicks(5)
                .run("mark", this::markHealth).run("mark the tally", () -> tallyMark = tally("corona sweep"));
        force(steps, "corona_sweep", Action.CORONA_SWEEP);
        steps.waitUntil("the wall is four ticks away", 80, () -> {
                    double[] w = HeliarchScenario.ask(h -> new double[] {h.actionAngle(), h.level().getGameTime() - h.actionStart()});
                    double along = ((HeliarchArena.angleOf(mc.player.getX(), mc.player.getZ()) - w[0]) % 360 + 360) % 360;
                    double hit = HeliarchMoves.SWEEP_TELL + along / (360.0 / HeliarchMoves.SWEEP_TICKS);
                    return w[1] >= hit - 4;
                })
                .press(mc.options.keyJump)
                .waitUntil("the wall has passed", 60, () -> actionAt(mc, HeliarchMoves.SWEEP_TELL + HeliarchMoves.SWEEP_TICKS + 2))
                .check("jumped the wall: no Corona Sweep damage", () -> tally("corona sweep") == tallyMark && CryptKit.health() >= healthMark - 1e-3)
                .waitUntil("it ends", 120, () -> idle());
        // Solar Lance: step aside once it locks
        steps.run("mid range", () -> CryptKit.tp(mc, HeliarchScenario.at(100, 12.0, 0), core))
                .waitTicks(5)
                .run("mark", this::markHealth).run("mark the tally", () -> tallyMark = tally("solar lance"));
        force(steps, "solar_lance", Action.SOLAR_LANCE);
        steps.waitUntil("the lance locks", 60, () -> HeliarchScenario.ask(HollowHeliarch::lanceLocked) && actionAt(mc, HeliarchMoves.LANCE_TRACK + 1))
                .run("face the throne", () -> CryptKit.face(mc, HeliarchArena.CENTRE, 0f))
                .hold(mc.options.keyLeft)
                .press(ModKeyMappings.DASH)
                .waitUntil("the lance has burned", 40, () -> actionAt(mc, HeliarchMoves.LANCE_TRACK + HeliarchMoves.LANCE_LOCK + HeliarchMoves.LANCE_BURN + 2))
                .release(mc.options.keyLeft)
                .check("stepped aside after the lock: no Solar Lance damage", () -> tally("solar lance") == tallyMark
                        && CryptKit.health() >= healthMark - 1e-3)
                .waitUntil("it ends", 120, () -> idle());
        // Corona Flare (G9c): standing inside its ring, it burns and throws me out
        steps.run("hugging the core", () -> CryptKit.tp(mc, HeliarchScenario.at(90, 3.0, 0), core))
                .waitTicks(10)
                .run("heal", CryptKit::heal)
                .run("mark", () -> {
                    markHealth();
                    tallyMark = tally("corona flare");
                });
        force(steps, "corona_flare", Action.CORONA_FLARE);
        steps.run("the ring from the rim", () -> s.camera(mc, HeliarchScenario.at(150, 16, 6.5), HeliarchArena.CENTRE.add(0, 1.0, 0)))
                .waitUntil("the ring fills", 40, () -> actionAt(mc, 13))
                .screenshot("heliarch_mech_flare")
                .run("back to the player", s::uncamera)
                .waitUntil("the flare has thrown me", 40, () -> actionAt(mc, HeliarchMoves.FLARE_TELL + 12))
                .log("flare taken", () -> String.format(Locale.ROOT, "standing in it: health %.1f to %.1f, thrown to %.1f from the throne",
                        healthMark, CryptKit.health(), HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ())))
                .check("standing in it: burned and thrown out of the ring", () -> tally("corona flare") == tallyMark + 1
                        && CryptKit.health() < healthMark - 1
                        && HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ()) > HeliarchMoves.FLARE_RADIUS + 0.5)
                .waitUntil("it ends", 120, () -> idle());
        // a backstep on its last ticks slips it: across the ring, staying inside it, so only the dash's i-frames save me
        steps.run("hugging the core again", () -> CryptKit.tp(mc, HeliarchScenario.at(90, 3.0, 0), core))
                .waitTicks(10)
                .run("heal", CryptKit::heal)
                .run("mark", () -> {
                    markHealth();
                    tallyMark = tally("corona flare");
                });
        force(steps, "corona_flare", Action.CORONA_FLARE);
        steps.run("face along the ring (a backstep runs round it)", () -> CryptKit.face(mc, mc.player.position().add(0, 0, -5), 0f))
                .waitUntil("two ticks before the flare, on the server's tick", 40, () -> actionAt(mc, HeliarchMoves.FLARE_TELL - 2))
                .press(ModKeyMappings.DASH)
                .waitUntil("the flare has passed", 20, () -> actionAt(mc, HeliarchMoves.FLARE_TELL + 2))
                .log("flare slipped", () -> String.format(Locale.ROOT, "backstep: health %.1f to %.1f, %.1f from the throne at the flare",
                        healthMark, CryptKit.health(), HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ())))
                .check("the dash's i-frames slipped it, inside the ring", () -> tally("corona flare") == tallyMark
                        && CryptKit.health() >= healthMark - 1e-3
                        && HeliarchArena.radiusOf(mc.player.getX(), mc.player.getZ()) < HeliarchMoves.FLARE_RADIUS)
                .waitUntil("it ends", 120, () -> idle());
        // Halo Shed: stand between two of the trails
        steps.run("mark", this::markHealth).run("mark the tally", () -> tallyMark = tally("halo shed"));
        force(steps, "halo_shed", Action.HALO_SHED);
        steps.run("between two return lines", () -> {
                    double a = HeliarchScenario.ask(HollowHeliarch::actionAngle);
                    CryptKit.tp(mc, HeliarchScenario.at(a + 60.0, 15.0, 0), core);
                })
                .waitUntil("the plates are home", 200, () -> idle())
                .check("between the trails: no Halo Shed damage", () -> tally("halo shed") == tallyMark && CryptKit.health() >= healthMark - 1e-3)
                .check("the core was exposed while the plates were out", () -> HeliarchScenario.ask(h -> h.sheds() >= 1))
                .waitUntil("it ends", 120, () -> idle())
                .log("regent", () -> HeliarchScenario.ask(HollowHeliarch::summary))
                .run("note", () -> results.add("regent: " + HeliarchScenario.ask(HollowHeliarch::summary)));
    }

    // ------------------------------------------------------------------ the regent's lines (A5.3)

    /**
     * Every line of the regent met in the fight ({@link VoiceSweep}), with its attacks held in phase 1: the voice forgets, each
     * line's own moment is raised, and the client must play it to its end on the Voice channel with its caption. Its opener and
     * its kill are the other parts' (the regent part hears them in a real summon and a real kill).
     */
    void lines(Steps steps, Minecraft mc) {
        List<String> problems = new ArrayList<>();
        List<String> heard = new ArrayList<>();
        equip(steps, mc);
        ensureFight(steps, mc);
        steps.run("let the sound engine play, inaudibly", () -> VoiceProbe.audible(true))
                .waitUntil("the guide is quiet", 900, () -> EchoClient.playing() == null && EchoClient.waiting().isEmpty());
        VoiceSweep.sweep(steps, "heliarch", problems, heard, () -> HeliarchScenario.ask(BossVoiceScenario::note));
        steps.run("mute again", () -> VoiceProbe.audible(false))
                .log("heard", () -> heard.size() + " lines met: " + String.join(", ", heard))
                .log("problems", () -> problems.size() + " problems: " + String.join("; ", problems))
                .check("every line met was heard to its end with its caption, on the Voice channel, as its catalog says", problems::isEmpty)
                .check("no line was cut short but by the rules, all on the Voice channel", () -> BossVoiceClient.interruptions() == 0
                        && BossVoiceClient.history().stream().allMatch(h -> h.source() == net.minecraft.sounds.SoundSource.VOICE));
    }

    // ------------------------------------------------------------------ 2. the Hollow

    void hollow(Steps steps, Minecraft mc) {
        Vec3 core = HeliarchArena.core(HeliarchArena.CORE_HEIGHT);
        if (!equipped) {
            equip(steps, mc);
            ensureFight(steps, mc);
        }
        steps.run("heal", CryptKit::heal)
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .command("cosmicbreach debug heliarch hollow")
                .waitUntil("the Hollowing's line", 80, () -> BossVoiceClient.heard("heliarch", "hollowing"))
                .waitUntil("phase 2", 200, () -> HeliarchScenario.ask(h -> h.state() == State.HOLLOW))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .waitUntil("the eclipse hangs over the pillars", 80, () -> HeliarchScenario.ask(h -> h.core(h.level().getGameTime()).y
                        - HeliarchArena.FLOOR > HeliarchArena.HIGH_HEIGHT - 1.0))
                .check("six monoliths of twelve blocks stand in the mid ring, three pips each", () -> CryptKit.server(srv -> {
                    ServerLevel level = HeliarchScenario.level(srv);
                    int blocks = 0;
                    for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
                        for (int[] c : m.columns()) {
                            for (int y = 0; y < HeliarchArena.MONOLITH_HEIGHT; y++) {
                                blocks += level.getBlockState(new BlockPos(c[0], 64 + y, c[1])).is(HeliarchRegistry.MONOLITH.get()) ? 1 : 0;
                            }
                        }
                    }
                    results.add("monolith blocks: " + blocks);
                    return blocks == 72;
                }) && java.util.Arrays.stream(HeliarchScenario.ask(HollowHeliarch::pips)).allMatch(p -> p == 3))
                .screenshot("heliarch_mech_hollow");
        // the Eclipse Beam: behind a monolith, and it loses a pip
        steps.run("behind the monolith at 60 degrees", () -> {
                    Vec3 m = HeliarchArena.monoliths().get(1).middle();
                    CryptKit.tp(mc, m.add(HeliarchArena.dir(60).scale(2.4)), core);
                })
                .waitTicks(5)
                .run("heal", CryptKit::heal)
                .run("mark", () -> {
                    markHealth();
                    tallyMark = tally("eclipse beam");
                    pipsMark = HeliarchScenario.ask(HollowHeliarch::pips);
                });
        force(steps, "eclipse_beam", Action.ECLIPSE_BEAM);
        steps.waitUntil("the beam has swept past", 160, () -> actionAt(mc, HeliarchMoves.BEAM_TELL + HeliarchMoves.BEAM_SWEEP + 2))
                .log("beam", () -> {
                    int[] p = HeliarchScenario.ask(HollowHeliarch::pips);
                    return String.format(Locale.ROOT, "pips %s to %s, health %.1f to %.1f, beam hits %d", java.util.Arrays.toString(pipsMark),
                            java.util.Arrays.toString(p), healthMark, CryptKit.health(), tally("eclipse beam") - tallyMark);
                })
                .check("behind the monolith: no beam damage, and the beam took one of its pips", () -> tally("eclipse beam") == tallyMark
                        && HeliarchScenario.ask(HollowHeliarch::pips)[1] == pipsMark[1] - 1)
                .waitUntil("it ends", 200, () -> HeliarchScenario.ask(h -> h.action() == Action.NONE || h.action() == Action.INVERSION))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .waitUntil("an inversion that followed is over", 300, () -> idle());
        // a tendril cut
        steps.run("beside a tendril", () -> {
                    Vec3 a = HeliarchArena.tendrilAnchor(0);
                    CryptKit.tp(mc, a.add(HeliarchArena.dir(HeliarchArena.TENDRIL_ANGLES[0]).scale(2.2)), a.add(0, 1.5, 0));
                })
                .waitTicks(5)
                .run("heal", CryptKit::heal)
                .run("mark Resonance and health", () -> {
                    resonanceMark = machineResonance();
                    healthMark = (float) (double) HeliarchScenario.ask(HollowHeliarch::health);
                })
                .waitUntil("cut it", 400, swing(mc, () -> HeliarchArena.tendrilAnchor(0).add(0, 1.4, 0),
                        () -> HeliarchScenario.ask(HollowHeliarch::cuts) >= 1, 390))
                .log("cut", () -> String.format(Locale.ROOT, "Resonance %.1f to %.1f", resonanceMark, machineResonance()))
                .waitUntil("the tendril is silenced", 20, () -> HeliarchScenario.ask(h -> h.tendrilSilent(0)))
                .log("bleed", () -> String.format(Locale.ROOT, "the eclipse through its tendril: health %.1f to %.1f", healthMark,
                        HeliarchScenario.ask(HollowHeliarch::health)))
                .check("blows on the tendril reached the eclipse", () -> HeliarchScenario.ask(HollowHeliarch::health) < healthMark - 1)
                .check("cutting it gave +15 Resonance", () -> machineResonance()
                        >= Math.min(resonanceMarkMax(), resonanceMark + HeliarchMoves.TENDRIL_RESONANCE) - 0.01);
        // the Gravity Inversion
        steps.run("onto the dais", () -> CryptKit.tp(mc, HeliarchScenario.at(270, 7.0, 0), core))
                .waitTicks(5);
        force(steps, "inversion", Action.INVERSION);
        steps.waitUntil("two Star Seeds are out", 120, () -> HeliarchScenario.ask(HollowHeliarch::seedsFired) >= 2)
                .log("gravity", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "player gravity %.4f", p.getAttributeValue(Attributes.GRAVITY))))
                .check("0.3x gravity for the player", () -> ServerQuery.ask(p -> Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08 * 0.3) < 1e-4))
                .waitUntil("the inversion ends", 220, () -> idle())
                .waitTicks(3)
                .check("gravity back to normal", () -> ServerQuery.ask(p -> Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1e-4))
                .check("six Star Seeds were fired", () -> HeliarchScenario.ask(HollowHeliarch::seedsFired) == HeliarchMoves.SEEDS);
        // Nova, let go behind a monolith
        steps.command("kill @e[type=cosmicbreach:star_seed]") // (seeds still homing from the high eclipse would knock me out of cover)
                .run("behind the monolith at 240 degrees", () -> {
                    Vec3 m = HeliarchArena.monoliths().get(4).middle();
                    CryptKit.tp(mc, m.add(HeliarchArena.dir(240).scale(2.4)), core);
                })
                .waitTicks(5)
                .run("heal", CryptKit::heal)
                .command("cosmicbreach debug heliarch nova")
                .waitUntil("Nova channels", 80, () -> HeliarchScenario.ask(h -> h.action() == Action.NOVA))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .waitUntil("Nova's line", 60, () -> BossVoiceClient.heard("heliarch", "nova"))
                .run("mark", () -> {
                    markHealth();
                    tallyMark = tally("nova (covered)");
                })
                .waitUntil("it detonates", HeliarchMoves.NOVA_CHANNEL + 60, () -> HeliarchScenario.ask(h -> h.nova().detonations() >= 1))
                .waitTicks(2)
                .log("nova", () -> String.format(Locale.ROOT, "detonation behind a monolith: health %.1f to %.1f", healthMark, CryptKit.health()))
                .check("behind the monolith it hit for the covered share", () -> tally("nova (covered)") == tallyMark + 1
                        && CryptKit.health() < healthMark && CryptKit.health() > healthMark - 20)
                .check("it comes back in 30 s", () -> HeliarchScenario.ask(h -> h.nova().nextChannel() - h.level().getGameTime()
                        > HeliarchMoves.NOVA_RETURN - 10))
                // Nova broken by real hits
                .run("heal", CryptKit::heal)
                .command("cosmicbreach debug heliarch nova")
                .waitUntil("Nova channels again", 80, () -> HeliarchScenario.ask(h -> h.action() == Action.NOVA))
                .run("mark the channel's start", () -> novaMark = HeliarchScenario.ask(h -> h.nova().start()))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .check("through the channel the eclipse stays over the pillars", () -> HeliarchScenario.ask(h -> h.core(h.level().getGameTime()).y
                        - HeliarchArena.FLOOR > HeliarchArena.HIGH_HEIGHT - 1.0))
                .run("beside a tendril: Nova draws through them", () -> {
                    Vec3 a = HeliarchArena.tendrilAnchor(1);
                    CryptKit.tp(mc, a.add(HeliarchArena.dir(HeliarchArena.TENDRIL_ANGLES[1]).scale(-2.4)), a.add(0, 1.5, 0));
                })
                .waitTicks(2)
                .waitUntil("break the Corona Shield on the tendril before the channel ends", HeliarchMoves.NOVA_CHANNEL,
                        swing(mc, () -> HeliarchArena.tendrilAnchor(1).add(0, 1.4, 0), () -> HeliarchScenario.ask(h -> h.nova().broken()),
                                HeliarchMoves.NOVA_CHANNEL))
                .log("nova break", () -> HeliarchScenario.ask(h -> String.format(Locale.ROOT, "shield broken %d ticks into the channel",
                        h.level().getGameTime() - novaMark)))
                .check("the shield broke: the heart exposed", () -> HeliarchScenario.ask(h -> h.nova().broken()
                        && h.level().getGameTime() < h.stunUntil()))
                .run("under the heart", () -> CryptKit.tp(mc, new Vec3(3.3, SanctumLayout.ARENA_Y, 0.5), HeliarchArena.core(HeliarchArena.LOW_HEIGHT)))
                .waitUntil("the heart is dragged down in reach", 40, () -> HeliarchScenario.ask(h -> h.core(h.level().getGameTime()).y
                        - HeliarchArena.FLOOR < HeliarchArena.LOW_HEIGHT + 1.0))
                .run("mark", () -> healthMark = (float) (double) HeliarchScenario.ask(HollowHeliarch::health))
                .waitUntil("strike the exposed heart", 120, swing(mc, () -> HeliarchArena.core(HeliarchArena.LOW_HEIGHT), () -> false, 100))
                .log("heart", () -> String.format(Locale.ROOT, "on the exposed heart: health %.1f to %.1f", healthMark,
                        HeliarchScenario.ask(HollowHeliarch::health)))
                .run("note", () -> results.add("hollow: " + HeliarchScenario.ask(HollowHeliarch::summary)));
    }

    // ------------------------------------------------------------------ 3. the Collapse and the end

    void end(Steps steps, Minecraft mc) {
        Vec3 core = HeliarchArena.core(HeliarchArena.CORE_HEIGHT);
        int side = HeliarchScenario.side();
        if (!equipped) {
            equip(steps, mc);
            ensureFight(steps, mc);
        }
        steps.command("cosmicbreach debug heliarch hold " + HOLD)
                .run("heal", CryptKit::heal)
                .run("to phase 2 if not there", () -> {
                    if (HeliarchScenario.ask(h -> h.state() == State.REGENT)) {
                        mc.player.connection.sendCommand("cosmicbreach debug heliarch hollow");
                    }
                })
                .waitUntil("phase 2", 200, () -> HeliarchScenario.ask(h -> h.state() == State.HOLLOW))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .run("onto the dais", () -> CryptKit.tp(mc, HeliarchScenario.at(200, 6.5, 0), core))
                .command("cosmicbreach debug heliarch collapse")
                .waitUntil("the Collapse", 60, () -> HeliarchScenario.ask(h -> h.state() == State.COLLAPSE))
                .command("cosmicbreach debug heliarch hold " + HOLD)
                .waitUntil("the Collapse's line", 60, () -> BossVoiceClient.heard("heliarch", "collapse"))
                .check("the first rim segment shakes", () -> HeliarchScenario.ask(h -> new CollapseSchedule(h.side())
                        .state(SanctumArena.Ring.RIM, CollapseSchedule.startSegment(h.side()), h.level().getGameTime() - h.collapseStart())
                        == CollapseSchedule.State.SHAKING))
                .check("and still stands", () -> CryptKit.server(srv -> SanctumArena.standing(HeliarchScenario.level(srv), SanctumArena.Ring.RIM,
                        CollapseSchedule.startSegment(side))))
                .waitUntil("it falls", HeliarchMoves.SHAKE_TICKS + 20, () -> CryptKit.server(srv -> !SanctumArena.standing(HeliarchScenario.level(srv),
                        SanctumArena.Ring.RIM, CollapseSchedule.startSegment(side))))
                .log("fall", () -> HeliarchScenario.ask(h -> String.format(Locale.ROOT, "the first rim segment fell %d ticks after it cracked",
                        h.level().getGameTime() - h.collapseStart())))
                .check("it shook its full 100 ticks first", () -> HeliarchScenario.ask(h -> h.level().getGameTime() - h.collapseStart()
                        >= HeliarchMoves.SHAKE_TICKS))
                .check("the next rim segment is still whole", () -> CryptKit.server(srv -> SanctumArena.standing(HeliarchScenario.level(srv),
                        SanctumArena.Ring.RIM, (CollapseSchedule.startSegment(side) + 1) % 8)))
                // Solar Rain: step out of the circle under me
                .waitUntil("a volley of Solar Rain", 140, () -> HeliarchScenario.ask(h -> h.rainCircles() != null
                        && h.rainLand() - h.level().getGameTime() > 12))
                .check("one circle falls on me", () -> {
                    List<Vec3> c = HeliarchScenario.ask(HollowHeliarch::rainCircles);
                    return c != null && c.size() == HeliarchMoves.RAIN_COUNT
                            && c.stream().anyMatch(v -> Math.hypot(v.x - mc.player.getX(), v.z - mc.player.getZ()) < 0.5);
                })
                .run("mark", () -> {
                    markHealth();
                    tallyMark = tally("solar rain");
                })
                .run("step out of it", () -> {
                    List<Vec3> c = HeliarchScenario.ask(HollowHeliarch::rainCircles);
                    Vec3 away = safeFrom(c, mc.player.position());
                    CryptKit.face(mc, away, 0f);
                })
                .hold(mc.options.keyUp)
                .press(ModKeyMappings.DASH)
                .waitUntil("the rain lands", 40, () -> HeliarchScenario.ask(h -> h.rainCircles() == null))
                .release(mc.options.keyUp)
                .check("out of the circles: no Solar Rain damage", () -> tally("solar rain") == tallyMark)
                // two minutes on
                .command("cosmicbreach debug heliarch ahead 2300")
                .waitUntil("the rim and the outer ring have fallen", 200, () -> HeliarchScenario.ask(HollowHeliarch::segmentsFallen) >= 16)
                .check("two minutes in, the arena ends at the mid ring", () -> CryptKit.server(srv -> {
                    ServerLevel level = HeliarchScenario.level(srv);
                    for (int seg = 0; seg < 8; seg++) {
                        if (SanctumArena.standing(level, SanctumArena.Ring.RIM, seg) || SanctumArena.standing(level, SanctumArena.Ring.OUTER, seg)
                                || !SanctumArena.standing(level, SanctumArena.Ring.MID, seg)) {
                            return false;
                        }
                    }
                    return true;
                }))
                .screenshot("heliarch_mech_collapse")
                // a death, and the player_down line: once the voice has settled, so the line is not held by the global gap
                .waitUntil("the regent's voice has settled", 800, () -> HeliarchScenario.ask(BossVoices::settled))
                .command("execute in cosmicbreach:aetheria run spawnpoint @s 0 64 4")
                .command("kill @s")
                .waitUntil("the player_down line", 60, () -> BossVoiceClient.heard("heliarch", "player_down"))
                .check("the fight saw it", () -> HeliarchScenario.ask(HollowHeliarch::playerDowns) >= 1)
                .waitTicks(10)
                .run("respawn", () -> mc.player.respawn())
                .waitUntil("back on the dais", 200, () -> mc.player != null && mc.player.isAlive() && mc.screen == null
                        && HeliarchArena.inFight(mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                .waitTicks(10)
                .run("heal", CryptKit::heal)
                .run("hold the weapon", () -> select(mc, SLOT_WEAPON))
                .run("mark XP and points", () -> {
                    xpMark = ServerQuery.ask(p -> Attunements.of(p).totalXp());
                    pointsMark = ServerQuery.ask(p -> Attunements.of(p).bonusPoints());
                })
                // the kill
                .command("cosmicbreach debug heliarch kill")
                .waitUntil("the death line", 60, () -> BossVoiceClient.heard("heliarch", "death"))
                .log("boss lines heard", () -> String.valueOf(BossVoiceClient.history()))
                .check("every line on the Voice channel, none cut short", () -> BossVoiceClient.interruptions() == 0
                        && BossVoiceClient.history().stream().allMatch(h -> h.source() == net.minecraft.sounds.SoundSource.VOICE))
                .waitUntil("a Reliquary rises for me", 200, () -> reliquary() != null)
                .waitUntil("the fight is over", 200, () -> !HeliarchScenario.fighting())
                .check("the arena rose again", () -> CryptKit.server(srv -> {
                    ServerLevel level = HeliarchScenario.level(srv);
                    for (int seg = 0; seg < 8; seg++) {
                        if (!SanctumArena.standing(level, SanctumArena.Ring.RIM, seg) || !SanctumArena.standing(level, SanctumArena.Ring.OUTER, seg)) {
                            return false;
                        }
                    }
                    return true;
                }))
                .check("the world changed: Eclipse Surges half as often, the seal over the Breach", () -> CryptKit.server(srv ->
                        EclipseSurge.heliarchFallen(srv)))
                .waitUntil("the seal on this client", 60, HeliarchSky::sealed)
                .waitUntil("the Starfall's voice: the Breach is sealed", 400, () -> EchoClient.playing() == EchoLine.SEALED
                        || EchoClient.history().stream().anyMatch(pl -> pl.line() == EchoLine.SEALED))
                // the Reliquary, opened once
                .run("before my Reliquary", () -> {
                    BlockPos r = reliquary();
                    Vec3 at = Vec3.atBottomCenterOf(r);
                    Vec3 out = new Vec3(at.x - HeliarchArena.CX, 0, at.z - HeliarchArena.CZ).normalize();
                    CryptKit.tp(mc, at.add(out.scale(1.8)), at.add(0, 0.5, 0));
                })
                .waitTicks(10)
                .run("an empty hand", () -> select(mc, SLOT_EMPTY))
                .waitTicks(2)
                .run("look at it", () -> CryptKit.aim(mc, Vec3.atCenterOf(reliquary()).add(0, 0.1, 0)))
                .press(mc.options.keyUse)
                .waitUntil("the share lands in my pack", 200, () -> count(mc, item(HeliarchLoot.LAST_LIGHT)) == 1 && count(mc, item(HeliarchLoot.CROWN)) == 1
                        && count(mc, item(HeliarchLoot.SOLAR_HEART)) == 1 && count(mc, item(HeliarchLoot.ECLIPSIUM)) >= 8)
                .log("loot", () -> String.format(Locale.ROOT, "Last Light %d, Crown %d, Solar Heart %d, Eclipsium %d, Umbra Cantor %d; XP +%d, "
                                + "stat points +%d", count(mc, item(HeliarchLoot.LAST_LIGHT)), count(mc, item(HeliarchLoot.CROWN)),
                        count(mc, item(HeliarchLoot.SOLAR_HEART)), count(mc, item(HeliarchLoot.ECLIPSIUM)), count(mc, item(HeliarchLoot.UMBRA_CANTOR)),
                        ServerQuery.ask(p -> Attunements.of(p).totalXp()) - xpMark, ServerQuery.ask(p -> Attunements.of(p).bonusPoints()) - pointsMark))
                .check("Breach Sealed, its two stat points and 40,000 XP", () -> ServerQuery.ask(p -> GuardianRewards.hasAdvancement(p,
                        ReliquaryBlockEntity.BREACH_SEALED) && Attunements.of(p).bonusPoints() == pointsMark + 2
                        && Attunements.of(p).totalXp() - xpMark >= HeliarchLoot.FIRST_XP - 1))
                .run("mark the items", () -> tallyMark = count(mc, item(HeliarchLoot.ECLIPSIUM)))
                .press(mc.options.keyUse)
                .waitTicks(20)
                .check("opened once: nothing more comes out", () -> count(mc, item(HeliarchLoot.ECLIPSIUM)) == tallyMark
                        && count(mc, item(HeliarchLoot.LAST_LIGHT)) == 1 && ReliquaryBlockEntity.openings() == 1)
                .run("the seal over the Breach, from the rim", () -> s.camera(mc, HeliarchScenario.at(135, 26, 2.0), HeliarchArena.CENTRE.add(0, 44, 0)))
                .waitTicks(60)
                .screenshot("heliarch_mech_seal")
                .run("back to the player", s::uncamera);
    }

    // ------------------------------------------------------------------ helpers

    private void force(Steps steps, String name, Action a) {
        steps.command("cosmicbreach debug heliarch attack " + name)
                .waitUntil(name + " begins", 120, () -> HeliarchScenario.ask(h -> h.action() == a))
                .command("cosmicbreach debug heliarch hold " + HOLD);
    }

    private void markHealth() {
        healthMark = CryptKit.health();
    }

    private static boolean actionAt(Minecraft mc, int t) {
        return HeliarchScenario.ask(h -> h.level().getGameTime() - h.actionStart() >= t);
    }

    private static boolean idle() {
        return HeliarchScenario.ask(h -> h.action() == Action.NONE);
    }

    private static int tally(String what) {
        return HeliarchScenario.ask(h -> h.tallies().getOrDefault(what, 0));
    }

    private static int refused() {
        return Integer.parseInt(CryptKit.server(srv -> HeliarchArenaRules.counters()).split(" ")[0]);
    }

    private static int pearls() {
        return Integer.parseInt(CryptKit.server(srv -> HeliarchArenaRules.counters()).split(" ")[1]);
    }

    private static double machineResonance() {
        return ComboScenario.machine(Minecraft.getInstance()).resonance();
    }

    private static double resonanceMarkMax() {
        return ComboScenario.machine(Minecraft.getInstance()).maxResonance();
    }

    private static void select(Minecraft mc, int slot) {
        KeyMapping.click(mc.options.keyHotbarSlots[slot].getKey());
    }

    private static Item item(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    private static int count(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack st : mc.player.getInventory().items) {
            if (st.is(item)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** My Reliquary's place on the dais, or null. */
    private static BlockPos reliquary() {
        return CryptKit.server(srv -> {
            ServerLevel level = HeliarchScenario.level(srv);
            ServerPlayer p = CryptKit.player(srv);
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = new BlockPos(x, 64, z);
                    if (level.getBlockEntity(pos) instanceof ReliquaryBlockEntity r && p.getUUID().equals(r.owner())) {
                        return pos;
                    }
                }
            }
            return null;
        });
    }

    /** A point to step toward that leaves every circle. */
    private static Vec3 safeFrom(List<Vec3> circles, Vec3 me) {
        Vec3 best = me.add(3, 0, 0);
        double bestScore = -1;
        for (int k = 0; k < 16; k++) {
            Vec3 d = HeliarchArena.dir(22.5 * k);
            Vec3 to = me.add(d.scale(4.5));
            if (HeliarchArena.radiusOf(to.x, to.z) > 8.0) {
                continue;
            }
            double score = Double.MAX_VALUE;
            for (Vec3 c : circles) {
                score = Math.min(score, Math.hypot(to.x - c.x, to.z - c.z));
            }
            if (score > bestScore) {
                bestScore = score;
                best = to;
            }
        }
        return best;
    }

    /**
     * Swings every 10 ticks at the point {@code aim} gives, until {@code done} holds or {@code ticks} have passed
     * (true then, so the step ends).
     */
    private static BooleanSupplier swing(Minecraft mc, Supplier<Vec3> aim, BooleanSupplier done, int ticks) {
        int[] t = {0};
        return () -> {
            KeyMapping.set(mc.options.keyAttack.getKey(), false);
            if (done.getAsBoolean() || t[0] >= ticks) {
                return true;
            }
            Vec3 a = aim.get();
            if (a != null) {
                CryptKit.aim(mc, a);
            }
            if (t[0] % 10 == 0) {
                KeyMapping.set(mc.options.keyAttack.getKey(), true);
                KeyMapping.click(mc.options.keyAttack.getKey());
            }
            t[0]++;
            return false;
        };
    }
}
