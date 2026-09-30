package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.weather.WeatherClient;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.DriftCurrents;
import com.cosmicbreach.world.weather.EclipseSurge;
import com.cosmicbreach.world.weather.GravityTide;
import com.cosmicbreach.world.weather.MeteorShower;
import com.cosmicbreach.world.weather.SolarFlare;
import com.cosmicbreach.world.weather.WeatherData;
import com.cosmicbreach.world.weather.WeatherKind;
import com.cosmicbreach.world.weather.WeatherSchedule;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Cosmic weather (W3b), each event forced in its layer, measured on both sides and photographed:
 *
 * <ol>
 *   <li>Solar Flare (Reach): the warning's sky against a calm one; Scorch in the open (about 1 a second)
 *       and under a roof (none); Resonance from a parry doubled on the server and in the client's
 *       prediction; fire a player deals 25% stronger; an exposed Shardling 30% faster, back to normal when
 *       the Flare ends.</li>
 *   <li>Meteor Shower (Reach): the warning's red streaks; the natural impacts near the player (cadence,
 *       distance, Meteorites); one meteor aimed next to the player, photographed falling and landing: its
 *       8 damage, the knockback out of the circle, the Meteorite it leaves.</li>
 *   <li>Gravity Tide (Drift): the dust swinging in the warning; gravity 0.15x on both sides; a jump's
 *       height and the current's push on the player (client) and on an item and a sheep (server); a
 *       22-block fall that does no damage.</li>
 *   <li>A Drift current: the player (slow falling) and an item carried 0.05 a tick along it.</li>
 *   <li>Eclipse Surge (Deep): Vesper silenced and the sky dimmed; the ability's cost 22.5 instead of 30 on
 *       both sides, and a real cast spending that; the Heliarch hook doubling the Surge countdown.</li>
 * </ol>
 * Parts run alone as {@code weather-flare}, {@code weather-shower}, {@code weather-tide}, {@code weather-current},
 * {@code weather-surge}.
 */
public final class WeatherScenario implements Scenario {
    public enum Part { FLARE, SHOWER, TIDE, CURRENT, SURGE }

    private static final int SETTLE_TICKS = 30;
    private final EnumSet<Part> parts;

    public WeatherScenario() {
        this.parts = EnumSet.allOf(Part.class);
    }

    public WeatherScenario(Part part) {
        this.parts = EnumSet.of(part);
    }

    @Override
    public int timeBudgetSeconds() {
        return 1500;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        steps.command("gamerule doDaylightCycle false")
                .command("time set 6000")
                .command("gamerule naturalRegeneration false")
                .run("clouds off, render distance 12", () -> {
                    mc.options.cloudStatus().set(CloudStatus.OFF);
                    mc.options.renderDistance().set(12);
                })
                .command("execute in " + AetheriaWorld.LEVEL.location() + " run tp @s 620 400 -380")
                .run("fly", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .command("cosmicbreach weather clear")
                .waitUntil("the client has calm weather", 60, () -> allCalm());
        if (parts.contains(Part.FLARE)) {
            flare(steps, mc);
        }
        if (parts.contains(Part.SHOWER)) {
            shower(steps, mc);
        }
        if (parts.contains(Part.TIDE)) {
            tide(steps, mc);
        }
        if (parts.contains(Part.CURRENT)) {
            current(steps, mc);
        }
        if (parts.contains(Part.SURGE)) {
            surge(steps, mc);
        }
        steps.command("cosmicbreach weather status")
                .waitForChat("reach: ", 60)
                .log("weather log", () -> "changes seen: " + com.cosmicbreach.world.weather.WeatherScheduler.log());
    }

    // ------------------------------------------------------------------ Solar Flare

    private static void flare(Steps steps, Minecraft mc) {
        double[] sun = {0, -20};
        UUID[] shardling = {null};
        float[] parry = {0, 0};
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto reach")
                .waitUntil("standing on a Reach island", 1600, standing(mc, Layer.REACH))
                .log("where", () -> where(mc))
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("face Solenne's side of the sky", () -> {
                    float[] d = SkyState.frame().sunDir;
                    sun[0] = Math.toDegrees(Math.atan2(-d[0], d[2]));
                    sun[1] = -Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, d[1]))));
                    look(mc, (float) sun[0], -8f);
                })
                .log("sun", () -> String.format(Locale.ROOT, "Solenne at yaw %.0f, pitch %.0f", sun[0], sun[1]))
                .waitTicks(30)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("flare_0_calm_horizon")
                .run("up at Solenne", () -> look(mc, (float) sun[0], (float) Math.max(-75, Math.min(-20, sun[1]))))
                .waitTicks(3)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("flare_0_calm_sun")
                .run("back to the horizon", () -> look(mc, (float) sun[0], -8f))
                .command("cosmicbreach weather flare")
                .waitUntil("the client hears of the warning", 60, () -> CosmicWeather.client(Layer.REACH).is(WeatherKind.FLARE, Phase.WARNING))
                .check("the server agrees", () -> server(p -> SolarFlare.warning(p.level())))
                .check("the Flare hasn't broken yet", () -> !SolarFlare.active(mc.level))
                .waitTicks(160)
                .log("hook", () -> "Solenne's flare hook at 8 s: " + WeatherClient.hooks()[0])
                .check("Solenne is brightening", () -> WeatherClient.hooks()[0] > 0.55f)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("flare_1_warning_horizon")
                .run("up at Solenne", () -> look(mc, (float) sun[0], (float) Math.max(-75, Math.min(-20, sun[1]))))
                .waitTicks(3)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("flare_1_warning_sun")
                .run("back to the horizon", () -> look(mc, (float) sun[0], -8f))
                .waitUntil("the Flare breaks", 200, () -> SolarFlare.active(mc.level))
                .check("on the server too", () -> server(p -> SolarFlare.active(p.level())))
                .run("full health", () -> server(p -> {
                    p.setHealth(20f);
                    return null;
                }))
                .waitTicks(100)
                .run("scorched in the open", () -> parry[0] = 20f - server(p -> p.getHealth()))
                .log("scorch", () -> "Scorch in the open over 100 ticks: " + parry[0])
                .check("about 1 a second in the open", () -> parry[0] >= 4f && parry[0] <= 6f)
                .check("the client sees itself exposed and shows it", () -> SolarFlare.exposed(mc.player) && WeatherClient.exposureGlow() > 0.9f)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("flare_2_active_open")
                .command("execute at @s run setblock ~ ~3 ~ minecraft:stone")
                .waitUntil("the client sees shade", 40, () -> !SolarFlare.exposed(mc.player))
                .check("and the server", () -> server(p -> !SolarFlare.exposed(p)))
                .run("full health", () -> server(p -> {
                    p.setHealth(20f);
                    return null;
                }))
                .waitTicks(100)
                .run("scorched in the shade", () -> parry[1] = 20f - server(p -> p.getHealth()))
                .log("shade", () -> "Scorch under a roof over 100 ticks: " + parry[1])
                .check("none in the shade", () -> parry[1] == 0f)
                .waitTicks(10)
                .check("the glow is gone in the shade", () -> WeatherClient.exposureGlow() < 0.05f)
                .run("a parry on the server", () -> parry[0] = server(p -> {
                    CombatStateMachine m = PlayerCombat.of(p).machine();
                    m.syncFromServer(0, 2, 0);
                    m.onParrySuccess();
                    return (float) m.resonance();
                }))
                .run("and in the client's prediction", () -> {
                    CombatStateMachine m = PlayerCombat.of(mc.player).machine();
                    m.syncFromServer(0, 2, 0);
                    m.onParrySuccess();
                    parry[1] = (float) m.resonance();
                })
                .log("resonance", () -> "a parry in a Flare gives " + parry[0] + " (server) and " + parry[1] + " (client)")
                .check("Resonance gain doubles on both sides: 25 becomes 50", () -> parry[0] == 50f && parry[1] == 50f)
                .run("fire from a player", () -> parry[0] = server(p -> {
                    Sheep sheep = EntityType.SHEEP.create(p.level());
                    sheep.moveTo(p.getX() + 2, p.getY(), p.getZ());
                    sheep.setNoAi(true);
                    p.level().addFreshEntity(sheep);
                    sheep.invulnerableTime = 0;
                    float before = sheep.getHealth();
                    sheep.hurt(p.damageSources().source(DamageTypes.IN_FIRE, p), 4f);
                    float dealt = before - sheep.getHealth();
                    sheep.discard();
                    return dealt;
                }))
                .log("fire", () -> "4 fire damage from the player dealt " + parry[0])
                .check("fire a player deals in a Flare is 25% stronger", () -> Math.abs(parry[0] - 5f) < 1e-3)
                .run("a still Shardling out in the open", () -> shardling[0] = server(p -> {
                    ServerLevel level = p.serverLevel();
                    Mob s = ModEntities.SHARDLING.get().create(level);
                    int x = p.getBlockX() + 4;
                    int z = p.getBlockZ();
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    s.moveTo(x + 0.5, y, z + 0.5);
                    s.setNoAi(true);
                    level.addFreshEntity(s);
                    return s.getUUID();
                }))
                .waitTicks(5)
                .log("shardling", () -> "the exposed Shardling's speed: " + shardlingSpeed(shardling[0]))
                .check("an exposed Shardling runs 30% faster", () -> Math.abs(shardlingSpeed(shardling[0]) - 0.32 * 1.3) < 1e-6)
                .command("cosmicbreach weather clear reach")
                .waitTicks(3)
                .check("back to its own speed when the Flare ends", () -> Math.abs(shardlingSpeed(shardling[0]) - 0.32) < 1e-6)
                .check("the client saw it end", () -> !SolarFlare.active(mc.level))
                // a Flare cleared during its warning takes its hum with it (W3b's open note)
                .command("cosmicbreach weather flare reach")
                .waitUntil("a new warning, its hum held", 60, () -> CosmicWeather.client(Layer.REACH).is(WeatherKind.FLARE, Phase.WARNING)
                        && WeatherClient.warningHeld())
                .command("cosmicbreach weather clear reach")
                .waitUntil("cleared during the warning, the hum stops", 60, () -> !WeatherClient.warningHeld())
                .run("remove the Shardling and the roof", () -> server(p -> {
                    Entity s = p.serverLevel().getEntity(shardling[0]);
                    if (s != null) {
                        s.discard();
                    }
                    p.serverLevel().setBlockAndUpdate(p.blockPosition().above(3), Blocks.AIR.defaultBlockState());
                    return null;
                }))
                .waitTicks(100)
                .check("Solenne eased back", () -> WeatherClient.hooks()[0] < 0.05f);
    }

    private static double shardlingSpeed(UUID id) {
        return server(p -> {
            Entity e = p.serverLevel().getEntity(id);
            return e instanceof Mob mob ? mob.getAttributeValue(Attributes.MOVEMENT_SPEED) : -1.0;
        });
    }

    // ------------------------------------------------------------------ Meteor Shower

    private static void shower(Steps steps, Minecraft mc) {
        long[] started = {0};
        Vec3[] centre = {null};
        BlockPos[] ground = {null};
        double[] before = {0, 0};
        DevCamera[] camera = {null};
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto reach")
                .waitUntil("standing on a Reach island", 1600, standing(mc, Layer.REACH))
                .command("cosmicbreach weather shower reach")
                .waitUntil("the client hears of the warning", 60, () -> CosmicWeather.client(Layer.REACH).is(WeatherKind.SHOWER, Phase.WARNING))
                .run("look up, away from the radiant", () -> {
                    float heading = CosmicWeather.client(Layer.REACH).heading();
                    look(mc, (float) Math.toDegrees(heading) - 90f, -38f); // towards the radiant's side, where most streaks burn
                })
                .waitTicks(130)
                .log("streaks", () -> "streaks drawn at 6.5 s: " + WeatherClient.streaksDrawn())
                .check("red streaks cross the sky", () -> WeatherClient.streaksDrawn() > 0)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("shower_1_warning")
                .waitUntil("the meteors begin", 200, () -> CosmicWeather.client(Layer.REACH).is(WeatherKind.SHOWER, Phase.ACTIVE))
                .run("note when", () -> started[0] = server(p -> p.level().getGameTime()))
                .run("look along the ground", () -> look(mc, mc.player.getYRot(), 10f))
                .waitTicks(330)
                .log("impacts", () -> impactsSince(started[0]))
                .check("meteors near the player every 4 to 6 s, 12 to 32 blocks away, each leaving a Meteorite", () -> server(p -> {
                    List<MeteorShower.Impact> impacts = new ArrayList<>();
                    for (MeteorShower.Impact i : MeteorShower.impacts()) {
                        if (i.at() >= started[0]) {
                            impacts.add(i);
                        }
                    }
                    if (impacts.size() < 2 || impacts.size() > 5) {
                        return false;
                    }
                    for (int k = 0; k < impacts.size(); k++) {
                        MeteorShower.Impact i = impacts.get(k);
                        double d = Math.hypot(i.centre().x - p.getX(), i.centre().z - p.getZ());
                        if (d < 11.5 || d > 32.5 || !i.meteorite() || !p.level().getBlockState(i.ground()).is(ModBlocks.METEORITE.get())) {
                            return false;
                        }
                        if (k > 0) {
                            long gap = i.at() - impacts.get(k - 1).at();
                            // 4 to 6 s, plus a few half-second retries when a spot had no ground
                            if (gap < MeteorShower.MIN_GAP_TICKS || gap > MeteorShower.MAX_GAP_TICKS + 40) {
                                return false;
                            }
                        }
                    }
                    return true;
                }))
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("full health", () -> server(p -> {
                    p.setHealth(20f);
                    return null;
                }))
                .run("a meteor aimed 2 blocks east of the player", () -> {
                    ground[0] = server(p -> {
                        ServerLevel level = p.serverLevel();
                        int x = p.getBlockX() + 2;
                        int z = p.getBlockZ();
                        BlockPos g = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                        MeteorShower.strike(level, g, MeteorShower.TELEGRAPH_TICKS, 0f);
                        return g;
                    });
                    centre[0] = new Vec3(ground[0].getX() + 0.5, ground[0].getY() + 1.0, ground[0].getZ() + 0.5);
                    before[0] = Math.hypot(mc.player.getX() - centre[0].x, mc.player.getZ() - centre[0].z);
                    camera[0] = DevCamera.create(mc.level);
                    camera[0].place(centre[0].add(1.0, 5.0, -15.0), centre[0].add(-3.0, 6.0, 0.0));
                    camera[0].use();
                })
                .log("aimed", () -> String.format(Locale.ROOT, "circle at %s, player %.1f blocks from its centre", ground[0].toShortString(), before[0]))
                .check("the client drew the circle", () -> WeatherClient.meteorsLive() > 0)
                .waitTicks(35)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("shower_2_meteor_falling")
                .waitTicks(5)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("shower_3_meteor_impact")
                .run("back to the player's eyes", () -> camera[0].remove())
                .waitTicks(12)
                .run("measure", () -> {
                    before[1] = 20f - server(p -> p.getHealth());
                    before[0] = Math.hypot(mc.player.getX() - centre[0].x, mc.player.getZ() - centre[0].z) - before[0];
                })
                .log("hit", () -> String.format(Locale.ROOT, "damage %.1f, knocked %.2f blocks further from the centre", before[1], before[0]))
                .check("the impact dealt 8", () -> Math.abs(before[1] - MeteorShower.DAMAGE) < 1e-3)
                .check("and knocked the player out of the circle", () -> before[0] > 1.0)
                .check("a Meteorite where it hit", () -> server(p -> p.level().getBlockState(ground[0]).is(ModBlocks.METEORITE.get())))
                .command("cosmicbreach weather clear reach")
                .command("gamemode creative");
    }

    private static String impactsSince(long since) {
        return server(p -> {
            StringBuilder b = new StringBuilder("impacts since the shower began:");
            long last = -1;
            for (MeteorShower.Impact i : MeteorShower.impacts()) {
                if (i.at() < since) {
                    continue;
                }
                b.append(String.format(Locale.ROOT, " [t+%d, %.1f blocks, gap %s, meteorite %s]", i.at() - since,
                        Math.hypot(i.centre().x - p.getX(), i.centre().z - p.getZ()), last < 0 ? "-" : String.valueOf(i.at() - last),
                        i.meteorite()));
                last = i.at();
            }
            return b.toString();
        });
    }

    // ------------------------------------------------------------------ Gravity Tide

    private static void tide(Steps steps, Minecraft mc) {
        double[] m = {0, 0};
        double[] jump = {0, 0, 0};
        double[] push = {0, 0, 0, 0};
        long[] tick = {0};
        UUID[] things = {null, null};
        double[][] at = {null, null};
        int[] floor = {0, 0, 0};
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto drift")
                .waitUntil("standing on an asteroid", 1600, standing(mc, Layer.DRIFT))
                .log("where", () -> where(mc))
                .run("a floor to test on: 15 by 15, clear above", () -> server(p -> {
                    ServerLevel level = p.serverLevel();
                    BlockPos c = p.blockPosition();
                    floor[0] = c.getX();
                    floor[1] = c.getZ();
                    floor[2] = c.getY();
                    for (int dx = -7; dx <= 7; dx++) {
                        for (int dz = -7; dz <= 7; dz++) {
                            level.setBlockAndUpdate(c.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                            for (int dy = 0; dy < 6; dy++) {
                                level.setBlockAndUpdate(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    return null;
                }))
                .waitTicks(10)
                .run("gravity before", () -> {
                    m[0] = AetheriaGravity.multiplier(mc.level, mc.player.getY());
                    m[1] = server(p -> AetheriaGravity.multiplier(p.level(), p.getY()));
                })
                .check("the Drift's 0.4 on both sides", () -> Math.abs(m[0] - 0.4) < 1e-9 && Math.abs(m[1] - 0.4) < 1e-9)
                .command("cosmicbreach weather tide")
                .waitUntil("the client hears of the warning", 60, () -> CosmicWeather.client(Layer.DRIFT).is(WeatherKind.TIDE, Phase.WARNING))
                .run("look across the heading", () -> look(mc, (float) Math.toDegrees(CosmicWeather.client(Layer.DRIFT).heading()) - 90f + 90f, 5f))
                .waitTicks(130)
                .log("dust", () -> "dust motes spawned in a tick at 6.5 s: " + WeatherClient.dustSpawned())
                .check("dust is streaming", () -> WeatherClient.dustSpawned() > 0)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("tide_1_warning")
                .waitUntil("the Tide", 200, () -> GravityTide.active(mc.level))
                .run("gravity in the Tide", () -> {
                    m[0] = AetheriaGravity.multiplier(mc.level, mc.player.getY());
                    m[1] = server(p -> AetheriaGravity.multiplier(p.level(), p.getY()));
                })
                .log("gravity", () -> String.format(Locale.ROOT, "gravity in the Tide: client %.3f, server %.3f", m[0], m[1]))
                .check("0.15 on both sides", () -> Math.abs(m[0] - GravityTide.GRAVITY) < 1e-9 && Math.abs(m[1] - GravityTide.GRAVITY) < 1e-9)
                .waitTicks(20)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("tide_2_active")
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .waitUntil("on the floor", 200, () -> mc.player.onGround())
                .run("mark", () -> {
                    jump[0] = mc.player.getY();
                    jump[1] = jump[0];
                })
                .press(mc.options.keyJump)
                .waitTicks(3)
                .run("where it is going up", () -> {
                    push[0] = mc.player.getX();
                    push[1] = mc.player.getZ();
                    tick[0] = mc.player.tickCount;
                    jump[1] = Math.max(jump[1], mc.player.getY());
                })
                .waitUntil("20 ticks on", 40, () -> {
                    jump[1] = Math.max(jump[1], mc.player.getY());
                    return mc.player.tickCount - tick[0] >= 20;
                })
                .run("where it is now", () -> {
                    long dt = mc.player.tickCount - tick[0];
                    double h = CosmicWeather.client(Layer.DRIFT).heading();
                    double dx = mc.player.getX() - push[0];
                    double dz = mc.player.getZ() - push[1];
                    push[2] = (dx * Math.cos(h) + dz * Math.sin(h)) / dt;
                    push[3] = Math.abs(-dx * Math.sin(h) + dz * Math.cos(h)) / dt;
                })
                .waitUntil("landed again", 300, () -> {
                    jump[1] = Math.max(jump[1], mc.player.getY());
                    return mc.player.onGround() && mc.player.getY() < jump[1] - 0.5;
                })
                .log("jump", () -> String.format(Locale.ROOT, "Tide jump %.2f blocks; carried %.4f a tick along the heading, %.4f across",
                        jump[1] - jump[0], push[2], push[3]))
                .check("a Tide jump rises 6 to 11 blocks (about 2x the Drift's)", () -> jump[1] - jump[0] > 6.0 && jump[1] - jump[0] < 11.0)
                .check("the current carries the player 0.06 a tick", () -> Math.abs(push[2] - GravityTide.CURRENT) < 0.004 && push[3] < 0.004)
                .run("a floating item and a still, floating sheep, upstream over the cleared floor", () -> {
                    double h = CosmicWeather.client(Layer.DRIFT).heading();
                    double ux = floor[0] + 0.5 - Math.cos(h) * 2.5;
                    double uz = floor[1] + 0.5 - Math.sin(h) * 2.5;
                    double sx = -Math.sin(h) * 1.5;
                    double sz = Math.cos(h) * 1.5;
                    UUID[] ids = server(p -> {
                        ServerLevel level = p.serverLevel();
                        double y = floor[2] + 2.0;
                        ItemEntity item = new ItemEntity(level, ux + sx, y, uz + sz, new ItemStack(Items.STONE));
                        item.setNoGravity(true);
                        item.setNeverPickUp();
                        item.setDeltaMovement(Vec3.ZERO);
                        level.addFreshEntity(item);
                        Sheep sheep = EntityType.SHEEP.create(level);
                        sheep.moveTo(ux - sx, y, uz - sz);
                        sheep.setNoAi(true);
                        sheep.setNoGravity(true);
                        level.addFreshEntity(sheep);
                        return new UUID[] {item.getUUID(), sheep.getUUID()};
                    });
                    things[0] = ids[0];
                    things[1] = ids[1];
                })
                .waitTicks(2)
                .run("where they are", () -> {
                    at[0] = where(things[0]);
                    at[1] = where(things[1]);
                })
                .waitTicks(20)
                .run("how far they went", () -> {
                    double[] a = where(things[0]);
                    double[] b = where(things[1]);
                    double h = CosmicWeather.client(Layer.DRIFT).heading();
                    push[0] = ((a[0] - at[0][0]) * Math.cos(h) + (a[2] - at[0][2]) * Math.sin(h)) / (a[3] - at[0][3]);
                    push[1] = ((b[0] - at[1][0]) * Math.cos(h) + (b[2] - at[1][2]) * Math.sin(h)) / (b[3] - at[1][3]);
                })
                .log("server push", () -> String.format(Locale.ROOT, "the server carries the item %.4f and the sheep %.4f a tick", push[0], push[1]))
                .check("the server moves items and mobs 0.06 a tick", () -> Math.abs(push[0] - GravityTide.CURRENT) < 0.004
                        && Math.abs(push[1] - GravityTide.CURRENT) < 0.004)
                .run("remove them", () -> server(p -> {
                    for (UUID id : things) {
                        Entity e = p.serverLevel().getEntity(id);
                        if (e != null) {
                            e.discard();
                        }
                    }
                    return null;
                }))
                .run("22 blocks up, upstream of the floor's middle, at full health", () -> {
                    double h = CosmicWeather.client(Layer.DRIFT).heading();
                    jump[2] = server(p -> {
                        p.setHealth(20f);
                        double y = p.getY();
                        p.teleportTo(p.serverLevel(), floor[0] + 0.5 - Math.cos(h) * 3.0, y + 22.0, floor[1] + 0.5 - Math.sin(h) * 3.0,
                                java.util.Set.of(), p.getYRot(), p.getXRot());
                        p.setDeltaMovement(Vec3.ZERO);
                        return y;
                    });
                })
                .waitUntil("the client is up there", 60, () -> mc.player.getY() > jump[2] + 15.0)
                .waitUntil("landed on the floor", 400, () -> mc.player.onGround() && mc.player.getY() < jump[2] + 1.0)
                .waitTicks(5)
                .log("fall", () -> {
                    float health = server(p -> p.getHealth());
                    return String.format(Locale.ROOT, "fell 22 blocks in the Tide, health after %.1f", health);
                })
                .check("no fall damage in a Tide", () -> server(p -> p.getHealth()) == 20f)
                .command("cosmicbreach weather clear drift")
                .waitTicks(3)
                .run("gravity after", () -> {
                    m[0] = AetheriaGravity.multiplier(mc.level, mc.player.getY());
                    m[1] = server(p -> AetheriaGravity.multiplier(p.level(), p.getY()));
                })
                .check("the Drift's 0.4 again on both sides", () -> Math.abs(m[0] - 0.4) < 1e-9 && Math.abs(m[1] - 0.4) < 1e-9)
                .command("gamemode creative");
    }

    /** An entity's position on the server and the server's game time: {x, y, z, time}. */
    private static double[] where(UUID id) {
        return server(p -> {
            Entity e = p.serverLevel().getEntity(id);
            if (e == null) {
                throw new Steps.Failure("entity " + id + " is gone");
            }
            return new double[] {e.getX(), e.getY(), e.getZ(), p.level().getGameTime()};
        });
    }

    // ------------------------------------------------------------------ a Drift current

    private static void current(Steps steps, Minecraft mc) {
        DriftCurrents.Zone[] zone = {null};
        double[] p0 = {0, 0, 0};
        double[] push = {0, 0};
        UUID[] item = {null};
        double[][] at = {null};
        DevCamera[] camera = {null};
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto drift")
                .waitUntil("standing on an asteroid", 1600, standing(mc, Layer.DRIFT))
                .command("cosmicbreach weather clear drift")
                .run("the nearest current", () -> zone[0] = server(p -> {
                    long salt = CosmicWeather.currentSalt(p.level());
                    DriftCurrents.Zone best = null;
                    double bestD = Double.MAX_VALUE;
                    for (DriftCurrents.Zone z : DriftCurrents.near(salt, p.getX(), p.getZ(), 300)) {
                        double d = Math.hypot(z.x() - p.getX(), z.z() - p.getZ());
                        if (d < bestD) {
                            bestD = d;
                            best = z;
                        }
                    }
                    return best;
                }))
                .check("a current within 300 blocks", () -> zone[0] != null)
                .check("the client finds the same tube", () -> DriftCurrents.at(CosmicWeather.currentSalt(mc.level), zone[0].x(), zone[0].y(), zone[0].z()) != null)
                .log("zone", () -> "nearest current: " + zone[0])
                .command("gamemode survival")
                .run("into it, on a glass floor", () -> server(p -> {
                    ServerLevel level = p.serverLevel();
                    BlockPos feet = BlockPos.containing(zone[0].x(), zone[0].y() - 0.5, zone[0].z());
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dz = -4; dz <= 4; dz++) {
                            level.setBlockAndUpdate(feet.offset(dx, -1, dz), Blocks.GLASS.defaultBlockState());
                            for (int dy = 0; dy < 3; dy++) {
                                level.setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0f, 0f);
                    p.setDeltaMovement(Vec3.ZERO);
                    ItemEntity it = new ItemEntity(p.serverLevel(), zone[0].x() + zone[0].dirZ() * 2, zone[0].y(), zone[0].z() - zone[0].dirX() * 2,
                            new ItemStack(Items.STONE));
                    it.setNoGravity(true);
                    it.setNeverPickUp();
                    it.setDeltaMovement(Vec3.ZERO);
                    p.serverLevel().addFreshEntity(it);
                    item[0] = it.getUUID();
                    return null;
                }))
                .waitUntil("the client is there, standing", 400, () -> Math.hypot(mc.player.getX() - zone[0].x(), mc.player.getZ() - zone[0].z()) < 3.0
                        && mc.player.onGround() && chunksAround(mc, 2))
                .waitTicks(20)
                .run("mark", () -> {
                    p0[0] = mc.player.getX();
                    p0[1] = mc.player.getZ();
                    p0[2] = mc.player.tickCount;
                    at[0] = where(item[0]);
                })
                .waitTicks(20)
                .run("measure", () -> {
                    double h = zone[0].heading();
                    double dt = mc.player.tickCount - p0[2];
                    push[0] = ((mc.player.getX() - p0[0]) * Math.cos(h) + (mc.player.getZ() - p0[1]) * Math.sin(h)) / dt;
                    double[] a = where(item[0]);
                    push[1] = ((a[0] - at[0][0]) * Math.cos(h) + (a[2] - at[0][2]) * Math.sin(h)) / (a[3] - at[0][3]);
                })
                .log("current", () -> String.format(Locale.ROOT, "a current carries the player %.4f and an item %.4f a tick", push[0], push[1]))
                .check("0.05 a tick along it, on both sides", () -> Math.abs(push[0] - DriftCurrents.PUSH) < 0.004
                        && Math.abs(push[1] - DriftCurrents.PUSH) < 0.004)
                .command("gamemode creative")
                .run("fly, and a camera beside the tube", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                    Vec3 axis = new Vec3(zone[0].x(), zone[0].y(), zone[0].z());
                    Vec3 side = new Vec3(-zone[0].dirZ(), 0, zone[0].dirX());
                    camera[0] = DevCamera.create(mc.level);
                    camera[0].place(axis.add(side.scale(16)).add(0, 4, 0), axis.add(zone[0].dirX() * 6, 0, zone[0].dirZ() * 6));
                    camera[0].use();
                })
                .waitTicks(40)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("current_streams")
                .run("back to the player's eyes", () -> camera[0].remove())
                .run("remove the item", () -> server(p -> {
                    Entity e = p.serverLevel().getEntity(item[0]);
                    if (e != null) {
                        e.discard();
                    }
                    return null;
                }));
    }

    // ------------------------------------------------------------------ Eclipse Surge

    private static void surge(Steps steps, Minecraft mc) {
        double[] cost = {0, 0};
        double[] res = {0, 0, 0, 0};
        int[] countdown = {0, 0};
        steps.command("gamemode creative")
                .command("cosmicbreach debug goto deep")
                .waitUntil("standing in the Deep", 1600, standing(mc, Layer.DEEP))
                .log("where", () -> where(mc))
                .command("clear @s")
                .command("cosmicbreach give")
                .run("Meridian in hand", () -> mc.player.getInventory().selected = 0)
                .waitTicks(5)
                .run("the ability's cost", () -> {
                    cost[0] = PlayerCombat.of(mc.player).machine().abilityCost();
                    cost[1] = server(p -> PlayerCombat.of(p).machine().abilityCost());
                })
                .check("30 on both sides in calm weather", () -> cost[0] == 30.0 && cost[1] == 30.0)
                .run("look north, up at Vesper's quarter", () -> look(mc, 180f, -30f))
                .waitTicks(20)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("surge_0_calm")
                .command("cosmicbreach weather surge")
                .waitUntil("the client hears of the warning", 60, () -> CosmicWeather.client(Layer.DEEP).is(WeatherKind.SURGE, Phase.WARNING))
                .check("the server agrees", () -> server(p -> EclipseSurge.warning(p.level())))
                .waitTicks(200)
                .log("hooks", () -> String.format(Locale.ROOT, "at 10 s: Vesper's silence %.2f, the dimming %.2f", WeatherClient.hooks()[1], WeatherClient.hooks()[2]))
                .check("Vesper has stopped and the sky is dimming", () -> WeatherClient.hooks()[1] > 0.95f && WeatherClient.hooks()[2] > 0.35f)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("surge_1_warning")
                .waitUntil("the Surge", 260, () -> EclipseSurge.active(mc.level))
                .check("on the server too", () -> server(p -> EclipseSurge.active(p.level())))
                .waitTicks(40)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("surge_2_active")
                .run("look ahead, at the ground", () -> look(mc, 180f, 25f))
                .waitTicks(3)
                .run("quiet the chat", () -> mc.gui.getChat().clearMessages(false))
                .screenshot("surge_3_active_ground")
                .run("the ability's cost", () -> {
                    cost[0] = PlayerCombat.of(mc.player).machine().abilityCost();
                    cost[1] = server(p -> PlayerCombat.of(p).machine().abilityCost());
                })
                .log("cost", () -> "the ability costs " + cost[0] + " (client) and " + cost[1] + " (server) in the Surge")
                .check("25% less on both sides", () -> cost[0] == 22.5 && cost[1] == 22.5)
                .command("cosmicbreach debug resonance 60")
                .waitUntil("the client's Resonance is 60", 60, () -> Math.abs(PlayerCombat.of(mc.player).machine().resonance() - 60) < 1.0)
                .run("before", () -> {
                    res[0] = PlayerCombat.of(mc.player).machine().resonance();
                    res[1] = server(p -> PlayerCombat.of(p).machine().resonance());
                })
                .press(mc.options.keyUse)
                .waitTicks(4)
                .run("after", () -> {
                    res[2] = PlayerCombat.of(mc.player).machine().resonance();
                    res[3] = server(p -> PlayerCombat.of(p).machine().resonance());
                })
                .log("cast", () -> String.format(Locale.ROOT, "Zenith spent %.2f (client) and %.2f (server)", res[0] - res[2], res[1] - res[3]))
                // Resonance also drifts towards 30 (0.25 a tick) while out of combat, so a cast reads a little over its cost
                .check("a cast spends 22.5 on both sides, not 30", () -> res[0] - res[2] > 22.0 && res[0] - res[2] < 25.0
                        && res[1] - res[3] > 22.0 && res[1] - res[3] < 25.0)
                .run("the Heliarch falls", () -> {
                    countdown[0] = server(p -> WeatherData.get(p.serverLevel()).schedule().countdown(WeatherSchedule.Slot.DEEP_SURGE));
                    countdown[1] = server(p -> {
                        EclipseSurge.onHeliarchFirstDeath(p.server);
                        return WeatherData.get(p.serverLevel()).schedule().countdown(WeatherSchedule.Slot.DEEP_SURGE);
                    });
                })
                .log("heliarch", () -> "the Surge countdown " + countdown[0] + " became " + countdown[1])
                .check("Surges come half as often after the Heliarch", () -> countdown[1] == countdown[0] * 2
                        && server(p -> EclipseSurge.heliarchFallen(p.server)))
                .command("cosmicbreach weather clear deep")
                .waitTicks(120)
                .check("Vesper and the sky come back", () -> WeatherClient.hooks()[1] < 0.05f && WeatherClient.hooks()[2] < 0.05f);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean allCalm() {
        for (Layer layer : Layer.values()) {
            if (!CosmicWeather.client(layer).calm()) {
                return false;
            }
        }
        return true;
    }

    /** On the ground in {@code layer}, nearby chunks in and drawn for a moment. */
    private static BooleanSupplier standing(Minecraft mc, Layer layer) {
        int[] still = {0};
        return () -> {
            boolean ok = mc.player != null && mc.level != null && AetheriaWorld.is(mc.level) && mc.player.onGround()
                    && Layer.at(mc.player.getY()) == layer && chunksAround(mc, 3) && mc.levelRenderer.hasRenderedAllSections();
            still[0] = ok ? still[0] + 1 : 0;
            return still[0] >= SETTLE_TICKS;
        };
    }

    private static boolean chunksAround(Minecraft mc, int radius) {
        if (mc.screen != null || mc.level == null || mc.player == null) {
            return false;
        }
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static String where(Minecraft mc) {
        return String.format(Locale.ROOT, "at %s, layer %s", mc.player.blockPosition().toShortString(), Layer.at(mc.player.getY()));
    }

    private static <T> T server(Function<ServerPlayer, T> query) {
        return ServerQuery.ask(query);
    }
}
