package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.entity.shardling.ShardFragment;
import com.cosmicbreach.entity.shardling.ShardNeedle;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.shardling.ShardlingMoves;
import com.cosmicbreach.entity.shardling.ShardlingPack;
import com.cosmicbreach.entity.shardling.Shardlings;
import com.cosmicbreach.registry.ModEntities;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Close-ups of the Shardling for a look by eye, on the arena's floor (polished tuff with a smooth
 * quartz stripe): a still one (NoAI) from the side, front, three-quarter, behind and above; held in its
 * lunge telegraph (the crouch and the gold spines) and its spit telegraph (the red throat); a needle
 * hanging in flight and a shard with its ground timer; and a shard's crystal right in front of the
 * camera. Then the combat contract ({@link #combatContract}). The cameras are stands that exist only
 * on this client.
 */
public final class ShardlingScenario implements Scenario {
    private static final String TAG = "cosmicbreach_portrait";
    private final Minecraft mc = Minecraft.getInstance();
    /** Where the Shardling stands (its feet). */
    private final Vec3[] spot = {Vec3.ZERO};

    @Override
    public void steps(Steps steps) {
        steps.look(0, 0)
                .waitTicks(3)
                .command("fill ~-6 ~-1 ~-3 ~6 ~-1 ~9 minecraft:polished_tuff")
                .command("fill ~-6 ~-1 ~6 ~6 ~-1 ~6 minecraft:smooth_quartz")
                .run("hide the HUD", () -> mc.options.hideGui = true)
                .waitUntil("a still Shardling stands 4 blocks ahead", 10, () -> ServerQuery.ask(player -> {
                    ServerLevel level = player.serverLevel();
                    Shardling s = ModEntities.SHARDLING.get().create(level);
                    Vec3 at = player.position().add(0, 0, 4);
                    s.moveTo(at.x, at.y, at.z, 180f, 0f);
                    s.setYHeadRot(180f);
                    s.setYBodyRot(180f);
                    s.setNoAi(true);
                    s.addTag(TAG);
                    level.addFreshEntity(s);
                    spot[0] = at;
                    return true;
                }))
                .waitUntil("it is on this client", 40, () -> shardling() != null)
                .waitTicks(10);
        views(steps, "idle");
        steps.run("the lunge telegraph", () -> ServerQuery.ask(player -> {
                    serverShardling(player.serverLevel()).showPhase(ShardlingMoves.Phase.TELL);
                    return true;
                }))
                .waitTicks(14);
        views(steps, "tell");
        steps.run("back to standing", () -> resetOnServer())
                .waitTicks(5)
                .run("the spit telegraph", () -> ServerQuery.ask(player -> {
                    serverShardling(player.serverLevel()).showPhase(ShardlingMoves.Phase.SPIT_TELL);
                    return true;
                }))
                .waitTicks(9);
        views(steps, "spit_tell");
        steps.run("back to standing", () -> resetOnServer())
                .run("a needle hanging in the air, a shard on the ground", () -> ServerQuery.ask(player -> {
                    ServerLevel level = player.serverLevel();
                    Shardling owner = serverShardling(level);
                    ShardNeedle needle = new ShardNeedle(level, owner, spot[0].add(1.8, 0.8, -0.5));
                    needle.setNoGravity(true);
                    needle.shoot(-1, 0, -0.35, 0.0001f, 0f);
                    level.addFreshEntity(needle);
                    ShardFragment fragment = new ShardFragment(level, spot[0].add(-1.5, 0.1, -0.6), Vec3.ZERO);
                    level.addFreshEntity(fragment);
                    return true;
                }))
                .waitTicks(14)
                .run("a camera on the needle and shard", () -> lookFrom(spot[0].add(0.2, 1.9, -3.4), spot[0].add(0.1, 0.2, -0.4)))
                .waitTicks(2)
                .screenshot("needle_and_shard")
                .run("a camera right at the shard", () -> lookFrom(spot[0].add(-1.5, 0.55, -1.45), spot[0].add(-1.5, 0.3, -0.6)))
                .waitTicks(2)
                .screenshot("shard_close")
                .run("the camera back", () -> mc.setCameraEntity(mc.player))
                .run("remove the portrait entities", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    return true;
                }))
                .run("show the HUD", () -> mc.options.hideGui = false);
        combatContract(steps);
    }

    // ------------------------------------------------------------------ the combat contract, on the server

    /**
     * The Shardling against the real combat runtime, on the server thread with still (NoAI) Shardlings
     * whose timelines are stepped by hand: a parry cancels the Splinter Lunge in its active ticks and
     * staggers the Shardling; the unparried leap deals 5; a needle lands through a parry for 3; the
     * recovery takes 25% more; a stagger breaks off a telegraph and gives back the attack token; a death
     * leaves three shards, 25 experience and 1 to 2 Starshards; a shard bursting 1 block away deals 3,
     * one 3 blocks away nothing. Normal difficulty, survival, no armor.
     */
    private void combatContract(Steps steps) {
        int[] id = {-1};
        double[] lost = {0, 0};
        boolean[] parry = {false, false}; // the leap was parryable and the parry up; the parry cancelled it
        int[] death = {0, 0, 0};
        double[] burst = {0, 0, 0, 0}; // health, max health, shards left, entities cleared before
        List<String> trace = new java.util.concurrent.CopyOnWriteArrayList<>();
        int[] still = {0};
        int[] xpBefore = {0};
        Vec3[] at = {Vec3.ZERO};
        double[] leap = {0};
        Vec3[] from = {Vec3.ZERO};
        // A Shardling with its AI on but nothing to hunt (the player is still in creative) leaps along its facing.
        steps.run("a free Shardling 3 blocks to the side, facing away", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    Shardling s = ModEntities.SHARDLING.get().create(player.serverLevel());
                    Vec3 start = side(player, -3.0);
                    s.moveTo(start.x, start.y, start.z, player.getYRot(), 0f);
                    s.setYHeadRot(player.getYRot());
                    s.setYBodyRot(player.getYRot());
                    s.addTag(TAG);
                    player.serverLevel().addFreshEntity(s);
                    id[0] = s.getId();
                    return true;
                }))
                .waitTicks(5)
                .run("its lunge telegraph, and where it starts from", () -> ServerQuery.ask(player -> {
                    Shardling s = shardling(player, id[0]);
                    from[0] = s.position();
                    s.showPhase(ShardlingMoves.Phase.TELL);
                    return true;
                }))
                .waitTicks(ShardlingMoves.TELL_TICKS + ShardlingMoves.LUNGE_TICKS + ShardlingMoves.RECOVER_TICKS + 2)
                .check("the leap carried about 5 blocks (4.5 to 5.5)", () -> ServerQuery.ask(player -> {
                    Shardling s = shardling(player, id[0]);
                    leap[0] = Math.hypot(s.getX() - from[0].x, s.getZ() - from[0].z);
                    return leap[0] >= 4.5 && leap[0] <= 5.5;
                }))
                .log("the free leap", () -> String.format(java.util.Locale.ROOT, "a free leap carried %.2f blocks", leap[0]))
                .command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .run("a Shardling in the middle of its leap, 1.3 blocks ahead", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    heal(player);
                    Shardling s = still(player.serverLevel(), ahead(player, 1.3), player.getYRot() + 180f);
                    s.moves().startLunge();
                    for (int i = 0; i < ShardlingMoves.TELL_TICKS; i++) {
                        s.moves().tick();
                    }
                    id[0] = s.getId();
                    return true;
                }))
                .waitTicks(1)
                // A parry is up for only a few ticks, and more than that can pass between two steps (it failed
                // once that way): the parry goes up in the same server tick as the leap that meets it.
                .run("the player raises a parry and the leap meets it", () -> ServerQuery.ask(player -> {
                    Shardling s = shardling(player, id[0]);
                    PlayerCombat.of(player).apply(CombatAction.PARRY);
                    parry[0] = s.isParryableAttackActive() && PlayerCombat.of(player).machine().isParrying();
                    player.invulnerableTime = 0;
                    float before = player.getHealth();
                    boolean landed = s.doHurtTarget(player);
                    parry[1] = !landed && player.getHealth() >= before && s.moves().staggered();
                    return true;
                }))
                .check("the leap is in its parryable active ticks and the parry is up", () -> parry[0])
                .check("a parry cancels the Splinter Lunge and staggers the Shardling", () -> parry[1])
                .waitTicks(25)
                .check("the same leap, not parried, deals 5", () -> ServerQuery.ask(player -> {
                    Shardling s = still(player.serverLevel(), ahead(player, 1.3), player.getYRot() + 180f);
                    s.moves().startLunge();
                    for (int i = 0; i < ShardlingMoves.TELL_TICKS; i++) {
                        s.moves().tick();
                    }
                    heal(player);
                    float before = player.getHealth();
                    boolean landed = s.doHurtTarget(player);
                    lost[0] = before - player.getHealth();
                    return landed && Math.abs(lost[0] - 5.0) < 0.01;
                }))
                .log("the unparried leap", () -> "the unparried leap dealt " + lost[0])
                // the leap's Impact (10) staggered the player (poise 10): no parry until that is over
                .waitUntil("the player's stagger from the leap is over", 30,
                        () -> ServerQuery.ask(player -> !PlayerCombat.of(player).machine().isStaggered()))
                .check("a needle is not parryable: it lands through the parry for 3", () -> ServerQuery.ask(player -> {
                    PlayerCombat.of(player).apply(CombatAction.PARRY); // raised in the same tick, as above
                    if (!PlayerCombat.of(player).machine().isParrying()) {
                        throw new Steps.Failure("the second parry is not up");
                    }
                    Shardling owner = still(player.serverLevel(), ahead(player, 8.0), player.getYRot() + 180f);
                    ShardNeedle needle = new ShardNeedle(player.serverLevel(), owner, ahead(player, 1.0).add(0, 1, 0));
                    // flying at the player: a needle that never moved would knock the player back in a random direction
                    Vec3 toward = Vec3.directionFromRotation(0f, player.getYRot()).scale(-1.0);
                    needle.shoot(toward.x, 0.0, toward.z, ShardNeedle.SPEED, 0f);
                    heal(player);
                    float before = player.getHealth();
                    boolean landed = player.hurt(player.serverLevel().damageSources().mobProjectile(needle, owner), ShardNeedle.DAMAGE);
                    needle.discard();
                    lost[0] = before - player.getHealth();
                    return landed && Math.abs(lost[0] - ShardNeedle.DAMAGE) < 0.01;
                }))
                .check("in its recovery it takes 25% more damage", () -> ServerQuery.ask(player -> {
                    Shardling calm = still(player.serverLevel(), ahead(player, 4.0).add(1.5, 0, 0), 0f);
                    Shardling spent = still(player.serverLevel(), ahead(player, 4.0).add(-1.5, 0, 0), 0f);
                    spent.moves().startLunge();
                    for (int i = 0; i < ShardlingMoves.TELL_TICKS + ShardlingMoves.LUNGE_TICKS; i++) {
                        spent.moves().tick();
                    }
                    if (spent.moves().phase() != ShardlingMoves.Phase.RECOVER) {
                        throw new Steps.Failure("the timeline is not in recovery but " + spent.moves().phase());
                    }
                    float a = calm.getHealth();
                    float b = spent.getHealth();
                    calm.hurt(player.damageSources().playerAttack(player), 4.0f);
                    spent.hurt(player.damageSources().playerAttack(player), 4.0f);
                    lost[0] = a - calm.getHealth();
                    lost[1] = b - spent.getHealth();
                    // Armor 4 takes a little less off the bigger hit, so the ratio lands a bit above 1.25.
                    return lost[1] > lost[0] * 1.2 && lost[1] < lost[0] * 1.35;
                }))
                .log("the recovery", () -> String.format(java.util.Locale.ROOT,
                        "a 4 damage hit took %.2f off a calm Shardling and %.2f off one in recovery (x%.2f)", lost[0], lost[1], lost[1] / lost[0]))
                .check("a stagger breaks off a telegraph and gives back the attack token", () -> ServerQuery.ask(player -> {
                    Shardling s = still(player.serverLevel(), ahead(player, 4.0), player.getYRot() + 180f);
                    s.moves().startLunge();
                    if (!Shardlings.tokens().tryAcquire(player.getId(), s.getId())) {
                        throw new Steps.Failure("no attack token was free");
                    }
                    boolean staggered = PoiseTracker.addImpact(s, Shardling.POISE);
                    return staggered && s.moves().staggered() && !Shardlings.tokens().holds(s.getId());
                }))
                .run("clear the test Shardlings; one dies to the player 5 blocks ahead", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    Shardling s = still(player.serverLevel(), ahead(player, 5.0), player.getYRot() + 180f);
                    xpBefore[0] = player.totalExperience;
                    at[0] = s.position();
                    s.hurt(player.damageSources().playerAttack(player), 100.0f);
                    return true;
                }))
                .waitTicks(2)
                .run("count what the death left", () -> ServerQuery.ask(player -> {
                    net.minecraft.world.phys.AABB near = new net.minecraft.world.phys.AABB(at[0], at[0]).inflate(4.0);
                    int shards = player.serverLevel().getEntitiesOfClass(ShardFragment.class, near).size();
                    int xp = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, near).stream()
                            .mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum()
                            + player.totalExperience - xpBefore[0]; // orbs still flying plus any already picked up
                    int starshards = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, near,
                            i -> i.getItem().is(com.cosmicbreach.registry.ModItems.STARSHARD.get())).stream()
                            .mapToInt(i -> i.getItem().getCount()).sum();
                    death[0] = shards;
                    death[1] = xp;
                    death[2] = starshards;
                    return true;
                }))
                .log("the death", () -> String.format(java.util.Locale.ROOT, "the death left %d shards, %d experience, %d Starshards",
                        death[0], death[1], death[2]))
                .check("it shattered into three shards and dropped 25 experience and 1 to 2 Starshards",
                        () -> death[0] == 3 && death[1] == Shardling.EXPERIENCE && death[2] >= 1 && death[2] <= 2)
                .waitUntil("the player stands still on the ground (hits knock it back)", 100, () -> playerStill(still))
                .run("clear every shard, needle and drop; a lone shard 3 blocks to the side", () -> ServerQuery.ask(player -> {
                    burst[3] = clearTestEntities(player);
                    heal(player);
                    trace.clear();
                    Vec3 shardAt = side(player, 3.0);
                    trace.add(String.format(java.util.Locale.ROOT, "player at %s, the lone shard at %s", fmt(player.position()), fmt(shardAt)));
                    player.serverLevel().addFreshEntity(new ShardFragment(player.serverLevel(), shardAt, Vec3.ZERO));
                    return true;
                }))
                .waitUntil("the lone shard has burst on the server", ShardFragment.FUSE + 80,
                        () -> ServerQuery.ask(player -> traceTick(player, trace)) == 0)
                .run("measure the player", () -> measure(burst))
                .log("the far burst", () -> String.format(java.util.Locale.ROOT,
                        "a shard burst 3 blocks away: health %.2f of %.2f, shards left %d (%d entities cleared before it); %s",
                        burst[0], burst[1], (int) burst[2], (int) burst[3], String.join("; ", trace)))
                .check("a shard bursting 3 blocks away does nothing", () -> burst[0] >= burst[1] - 1e-3 && burst[2] == 0)
                .waitUntil("the player stands still on the ground again", 100, () -> playerStill(still))
                .run("clear again; a lone shard 1 block to the side", () -> ServerQuery.ask(player -> {
                    burst[3] = clearTestEntities(player);
                    heal(player);
                    player.serverLevel().addFreshEntity(new ShardFragment(player.serverLevel(), side(player, 1.0), Vec3.ZERO));
                    return true;
                }))
                .waitUntil("the lone shard has burst on the server", ShardFragment.FUSE + 80,
                        () -> ServerQuery.ask(ShardlingScenario::shardsNear) == 0)
                .run("measure the player", () -> measure(burst))
                .log("the near burst", () -> String.format(java.util.Locale.ROOT,
                        "a shard burst 1 block away: health %.2f of %.2f, shards left %d (%d entities cleared before it)",
                        burst[0], burst[1], (int) burst[2], (int) burst[3]))
                .check("a shard bursting 1 block away deals 3", () -> Math.abs(burst[1] - burst[0] - ShardFragment.DAMAGE) < 0.01
                        && burst[2] == 0)
                .run("clean up", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    heal(player);
                    return true;
                }));
        settling(steps);
        steps.command("gamemode creative");
    }

    // ------------------------------------------------------------------ the pack settles into its places

    /**
     * A real pack of two hunting the player, who stands still in survival with both its attack tokens
     * held by dummies, so nobody attacks and the pack only takes its places: the Taunter 4 to 6 blocks
     * in front, the Flanker 4 to 5 behind. Then the Taunter is put 2.8 blocks out, as after a dart, and
     * must be back in its band within 40 ticks and stay there between its feints (free and in place,
     * it feints every fifth plan: a dart to 2.8 and back, which doesn't count). Then, while the Taunter
     * holds the front, the Flanker is put 1.1 blocks behind the player and lands a hit, as after a lunge
     * from behind: it must circle round to the front without cutting close past the player and take the
     * role there, while the old Taunter keeps the front until it arrives.
     */
    private void settling(Steps steps) {
        int[] ids = {-1, -1};         // Taunter, Flanker
        int[] took = {0, 0};
        double[] closest = {Double.MAX_VALUE, 0, 0}; // blocks, degrees round from the front, after taking over (1)
        boolean[] out = {false};
        int[] back = {-1};            // ticks after the dart until it was back in its band
        int[] sinceFeint = {1000};    // ticks since the Taunter last had a feint order
        int[] between = {0, 0};       // samples between feints, and how many were out of its band
        double[] range = {Double.MAX_VALUE, 0};
        boolean[] lostFront = {false};
        steps.run("block the player's attack tokens; a pack of two 9 blocks ahead", () -> ServerQuery.ask(player -> {
                    clearTestEntities(player);
                    heal(player);
                    Shardlings.tokens().tryAcquire(player.getId(), DUMMY_A);
                    Shardlings.tokens().tryAcquire(player.getId(), DUMMY_B);
                    List<Shardling> pack = com.cosmicbreach.sandbox.Sandbox.spawnPack(player.serverLevel(), ahead(player, 9.0),
                            player.position(), 2, 1.0f);
                    pack.forEach(s -> s.addTag(TAG));
                    return pack.size() == 2;
                }))
                .waitUntil("the pack has taken its places: the Taunter in front, the Flanker behind", 300,
                        () -> ServerQuery.ask(player -> placed(player, ids)))
                .log("the places", () -> ServerQuery.ask(player -> describePack(player, ids)))
                .run("the Taunter darts in to 2.8 blocks", () -> ServerQuery.ask(player -> {
                    Vec3 at = ahead(player, 2.8);
                    shardling(player, ids[0]).teleportTo(at.x, at.y, at.z);
                    took[0] = 0;
                    back[0] = -1;
                    sinceFeint[0] = 1000;
                    return true;
                }))
                .waitUntil("watching the Taunter for 150 ticks after the dart", 160, () -> {
                    took[0]++;
                    double[] now = ServerQuery.ask(player -> {
                        Shardling taunter = shardling(player, ids[0]);
                        ShardlingPack.Order order = taunter.pack() == null ? null : taunter.pack().order(taunter.getId());
                        return new double[] {flatDistance(player, taunter), order != null && order.feint() ? 1 : 0};
                    });
                    sinceFeint[0] = now[1] > 0 ? 0 : sinceFeint[0] + 1;
                    boolean inBand = now[0] >= ShardlingPack.TAUNT_MIN && now[0] <= ShardlingPack.TAUNT_MAX;
                    if (back[0] < 0 && inBand) {
                        back[0] = took[0];
                    }
                    if (back[0] >= 0 && sinceFeint[0] >= 20) {
                        between[0]++;
                        between[1] += inBand ? 0 : 1;
                        range[0] = Math.min(range[0], now[0]);
                        range[1] = Math.max(range[1], now[0]);
                    }
                    return took[0] >= 150;
                })
                .log("the dart", () -> String.format(java.util.Locale.ROOT,
                        "after a dart to 2.8 blocks the Taunter was back in 4 to 6 after %d ticks; between its own feints it held "
                                + "%.2f to %.2f blocks over %d samples (%d out of band)", back[0], range[0], range[1], between[0], between[1]))
                .check("back in its band within 40 ticks of the dart", () -> back[0] >= 0 && back[0] <= 40)
                .check("between feints it held 4 to 6 blocks", () -> between[0] >= 20 && between[1] == 0)
                // one step on the server thread, so the front is still held when the hit lands
                .waitUntil("once the Taunter holds the front, the Flanker lands a hit from 1.1 blocks behind", 120, () -> ServerQuery.ask(player -> {
                    Shardling flanker = shardling(player, ids[1]);
                    if (!flanker.pack().frontHeld()) {
                        return false;
                    }
                    Vec3 at = ahead(player, -1.1);
                    flanker.teleportTo(at.x, at.y, at.z);
                    flanker.pack().onHitLanded(flanker.getId());
                    took[1] = 0;
                    out[0] = false;
                    closest[0] = Double.MAX_VALUE;
                    sinceFeint[0] = 1000;
                    lostFront[0] = false;
                    return true;
                }))
                .check("it is on its way to the front; the Taunter keeps it meanwhile", () -> ServerQuery.ask(player -> {
                    ShardlingPack pack = shardling(player, ids[1]).pack();
                    return pack.elect() == ids[1] && pack.taunter() == ids[0];
                }))
                .waitUntil("it circles to the front and takes it over", 240, () -> {
                    took[1]++;
                    double[] where = ServerQuery.ask(player -> {
                        Shardling s = shardling(player, ids[1]);
                        ShardlingPack pack = s.pack();
                        ShardlingPack.Target t = new ShardlingPack.Target(player.getId(), player.getX(), player.getZ(), player.getYRot());
                        ShardlingPack.Order order = pack == null ? null : pack.order(s.getId());
                        int taunter = pack == null ? -1 : pack.taunter();
                        return new double[] {flatDistance(player, s), Math.abs(ShardlingPack.angleOf(t, s.getX(), s.getZ())),
                                order != null && order.feint() ? 1 : 0, taunter == ids[1] ? 1 : 0, taunter == ids[0] ? 1 : 0};
                    });
                    if (where[3] == 0 && where[4] == 0) {
                        lostFront[0] = true; // the role went to neither of them
                    }
                    sinceFeint[0] = where[2] > 0 ? 0 : sinceFeint[0] + 1;
                    if (where[0] >= ShardlingPack.TAUNT_MIN) {
                        out[0] = true;
                    }
                    if (out[0] && sinceFeint[0] >= 20 && where[0] < closest[0]) { // a feint's dart is meant to come close
                        closest[0] = where[0];
                        closest[1] = where[1];
                        closest[2] = where[3];
                    }
                    return where[3] > 0 && where[0] >= ShardlingPack.TAUNT_MIN && where[0] <= ShardlingPack.TAUNT_MAX && where[1] <= 20.0;
                })
                .log("the handover", () -> String.format(java.util.Locale.ROOT,
                        "a Flanker that hit from 1.1 blocks behind held the front, in its band, after %d ticks; once out past 4 "
                                + "blocks it came no closer than %.2f (%.0f deg round, %s); now %s", took[1], closest[0], closest[1],
                        closest[2] > 0 ? "after taking over" : "on its way", ServerQuery.ask(player -> describePack(player, ids))))
                .check("the front only ever belonged to the old Taunter or the new one", () -> !lostFront[0])
                .check("circling round, it never cut back inside 3.8 blocks", () -> closest[0] >= 3.8)
                .run("give the tokens back and remove the pack", () -> ServerQuery.ask(player -> {
                    Shardlings.tokens().release(DUMMY_A);
                    Shardlings.tokens().release(DUMMY_B);
                    clearTestEntities(player);
                    return true;
                }));
    }

    /** Holders that keep the player's two attack tokens taken, so the pack never attacks. */
    private static final int DUMMY_A = -1001;
    private static final int DUMMY_B = -1002;

    /** True once the test pack's Taunter is idle 4 to 6 blocks in front and its Flanker idle 4 to 5 behind; fills {@code ids}. */
    private static boolean placed(ServerPlayer player, int[] ids) {
        List<Shardling> pack = player.serverLevel().getEntitiesOfClass(Shardling.class, player.getBoundingBox().inflate(24),
                s -> s.isAlive() && s.getTags().contains(TAG) && s.pack() != null);
        if (pack.size() != 2) {
            return false;
        }
        ShardlingPack.Target t = new ShardlingPack.Target(player.getId(), player.getX(), player.getZ(), player.getYRot());
        Shardling taunter = null;
        Shardling flanker = null;
        for (Shardling s : pack) {
            if (s.role() == ShardlingPack.Role.TAUNTER) {
                taunter = s;
            } else {
                flanker = s;
            }
        }
        if (taunter == null || flanker == null) {
            return false;
        }
        double td = flatDistance(player, taunter);
        double fd = flatDistance(player, flanker);
        boolean ok = Math.abs(ShardlingPack.angleOf(t, taunter.getX(), taunter.getZ())) <= 20.0
                && td >= ShardlingPack.TAUNT_MIN && td <= ShardlingPack.TAUNT_MAX
                && Math.abs(ShardlingPack.angleOf(t, flanker.getX(), flanker.getZ())) >= ShardlingPack.FLANK_ANGLE_MIN
                && fd >= ShardlingPack.FLANK_RADIUS_MIN && fd <= ShardlingPack.FLANK_RADIUS_MAX
                && taunter.getDeltaMovement().horizontalDistance() < 0.02 && flanker.getDeltaMovement().horizontalDistance() < 0.02;
        if (ok) {
            ids[0] = taunter.getId();
            ids[1] = flanker.getId();
        }
        return ok;
    }

    private static double flatDistance(ServerPlayer player, Shardling s) {
        return Math.hypot(s.getX() - player.getX(), s.getZ() - player.getZ());
    }

    private static String describePack(ServerPlayer player, int[] ids) {
        ShardlingPack.Target t = new ShardlingPack.Target(player.getId(), player.getX(), player.getZ(), player.getYRot());
        List<String> parts = new java.util.ArrayList<>();
        for (int id : ids) {
            if (player.serverLevel().getEntity(id) instanceof Shardling s) {
                parts.add(String.format(java.util.Locale.ROOT, "#%d %s %.0f deg %.2f blocks", id, s.role(),
                        Math.abs(ShardlingPack.angleOf(t, s.getX(), s.getZ())), flatDistance(player, s)));
            }
        }
        return String.join("; ", parts);
    }

    private static Shardling still(ServerLevel level, Vec3 at, float yaw) {
        Shardling s = ModEntities.SHARDLING.get().create(level);
        s.moveTo(at.x, at.y, at.z, yaw, 0f);
        s.setYHeadRot(yaw);
        s.setYBodyRot(yaw);
        s.setNoAi(true);
        s.addTag(TAG);
        level.addFreshEntity(s);
        return s;
    }

    private static Shardling shardling(ServerPlayer player, int id) {
        if (player.serverLevel().getEntity(id) instanceof Shardling s) {
            return s;
        }
        throw new Steps.Failure("the test Shardling " + id + " is gone");
    }

    private static Vec3 ahead(ServerPlayer player, double blocks) {
        return player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(blocks));
    }

    private static Vec3 side(ServerPlayer player, double blocks) {
        Vec3 f = Vec3.directionFromRotation(0f, player.getYRot());
        return player.position().add(new Vec3(-f.z, 0, f.x).scale(blocks));
    }

    private static void heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.invulnerableTime = 0;
    }

    /**
     * Discards (no death, no burst) the test Shardlings and every shard, needle, item and experience
     * orb within 64 blocks, whoever made them. Returns how many it removed.
     */
    private static int clearTestEntities(ServerPlayer player) {
        List<Entity> gone = player.serverLevel().getEntities((Entity) null, player.getBoundingBox().inflate(64),
                e -> e.getTags().contains(TAG) || e instanceof ShardNeedle || e instanceof ShardFragment
                        || e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb);
        gone.forEach(Entity::discard);
        return gone.size();
    }

    /**
     * One tick of watching a lone shard (server thread): notes where every shard near the player is and
     * any health the player lost this tick, with what hurt it. Returns the shards near the player.
     */
    private static int traceTick(ServerPlayer player, List<String> trace) {
        List<ShardFragment> shards = player.serverLevel().getEntitiesOfClass(ShardFragment.class, player.getBoundingBox().inflate(16));
        if (player.getHealth() < player.getMaxHealth() - 1e-3 && trace.stream().noneMatch(n -> n.startsWith("hurt"))) {
            var source = player.getLastDamageSource();
            trace.add(String.format(java.util.Locale.ROOT, "hurt to %.2f at player %s by %s (direct %s at %s); shards now %s",
                    player.getHealth(), fmt(player.position()), source == null ? "?" : source.getMsgId(),
                    source == null || source.getDirectEntity() == null ? "none" : source.getDirectEntity().getType().toShortString(),
                    source == null || source.getDirectEntity() == null ? "-" : fmt(source.getDirectEntity().position()),
                    shards.stream().map(f -> fmt(f.position()) + " age " + f.tickCount).toList()));
        }
        return shards.size();
    }

    private static String fmt(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    /** True once the player has stood on the ground without moving for 5 ticks in a row ({@code count} carries the run). */
    private static boolean playerStill(int[] count) {
        boolean now = ServerQuery.ask(player -> player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-5);
        count[0] = now ? count[0] + 1 : 0;
        return count[0] >= 5;
    }

    /** Shards within 16 blocks of the player (server thread). */
    private static int shardsNear(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(ShardFragment.class, player.getBoundingBox().inflate(16)).size();
    }

    /** The player's health, max health and the shards near it, all read in one server task. */
    private static void measure(double[] into) {
        ServerQuery.ask(player -> {
            into[0] = player.getHealth();
            into[1] = player.getMaxHealth();
            into[2] = shardsNear(player);
            return true;
        });
    }

    /** Side, front, three-quarter, behind and above, about 2.7 blocks out at the Shardling's own height. */
    private void views(Steps steps, String label) {
        double[][] around = {{90, 0.7, 2.7}, {180, 0.7, 2.7}, {135, 0.95, 3.0}, {0, 0.75, 2.7}, {150, 3.2, 2.0}};
        String[] names = {"side", "front", "three_quarter", "back", "above"};
        for (int i = 0; i < around.length; i++) {
            double a = Math.toRadians(around[i][0]);
            double height = around[i][1];
            double distance = around[i][2];
            String name = label + "_" + names[i];
            // Round the Shardling, which faces yaw 180 (toward -Z), in yaw's directions: 180 is in front of
            // its face, 0 behind it, 90 its left side. Worked out when the step runs, once it stands there.
            steps.run("camera " + name, () -> {
                Vec3 body = spot[0].add(0, 0.5, 0);
                lookFrom(body.add(-Math.sin(a) * distance, height, Math.cos(a) * distance), body);
            })
                    .waitTicks(2)
                    .screenshot(name);
        }
        steps.run("the camera back", () -> mc.setCameraEntity(mc.player));
    }

    private void resetOnServer() {
        ServerQuery.ask(player -> {
            Shardling old = serverShardling(player.serverLevel());
            Vec3 at = old.position();
            old.discard();
            Shardling s = ModEntities.SHARDLING.get().create(player.serverLevel());
            s.moveTo(at.x, at.y, at.z, 180f, 0f);
            s.setYHeadRot(180f);
            s.setYBodyRot(180f);
            s.setNoAi(true);
            s.addTag(TAG);
            player.serverLevel().addFreshEntity(s);
            return true;
        });
    }

    private static Shardling serverShardling(ServerLevel level) {
        List<Shardling> found = level.getEntitiesOfClass(Shardling.class, new net.minecraft.world.phys.AABB(-1e7, -1e3, -1e7, 1e7, 1e3, 1e7),
                s -> s.getTags().contains(TAG) && s.isAlive());
        if (found.isEmpty()) {
            throw new Steps.Failure("the portrait Shardling is gone");
        }
        return found.get(0);
    }

    private Shardling shardling() {
        List<Shardling> found = mc.level.getEntitiesOfClass(Shardling.class, mc.player.getBoundingBox().inflate(16));
        return found.isEmpty() ? null : found.get(0);
    }

    private void lookFrom(Vec3 eye, Vec3 at) {
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.hypot(d.x, d.z)) * Mth.RAD_TO_DEG);
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.moveTo(eye.x, eye.y - stand.getEyeHeight(), eye.z, yaw, pitch);
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
        mc.setCameraEntity(stand);
    }

    @Override
    public int timeBudgetSeconds() {
        return 120;
    }
}
