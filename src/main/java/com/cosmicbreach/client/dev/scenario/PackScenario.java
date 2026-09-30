package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.entity.shardling.AttackTokens;
import com.cosmicbreach.entity.shardling.ShardFragment;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.shardling.ShardlingMoves;
import com.cosmicbreach.entity.shardling.ShardlingPack;
import com.cosmicbreach.entity.shardling.Shardlings;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModSounds;
import com.cosmicbreach.sandbox.Sandbox;
import com.cosmicbreach.sandbox.WaveSchedule;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sandbox's Shardlings against a player who first stands still, then fights back.
 * <ol>
 *   <li>{@code /cosmicbreach sandbox}: the arena, the kit, the rules.</li>
 *   <li>The warm-up pair: two Shardlings at 1.5 times the rest between attacks, measured.</li>
 *   <li>They are cleared; the next pack (3 to 5, normal pace) comes 6 s later, and the player stands
 *       still against it for 10 s: at most 2 attack it at once, a Flanker settles 120 to 180 degrees
 *       behind at 4 to 5 blocks, the Taunter settles 4 to 6 blocks in front, and standing still is
 *       punished (the player takes hits).</li>
 *   <li>Meridian through the real key path until one dies and shatters; its shards tick and burst.</li>
 *   <li>{@code /cosmicbreach wave 4} and {@code /cosmicbreach stop}.</li>
 * </ol>
 * Screenshots: the approach (first person), a gold telegraph and its lunge (from the side), the flank
 * positions (from above and the side), the death's shards and ground timers, and the burst. The
 * player has Resistance IV: a lunge deals 1, which still counts as a hit, so every hit adds its Impact
 * and staggers the player (poise 10), with the stagger's grace in between, the way a real fight does.
 *
 * <p>A monitor on the server thread samples every server tick and files what it sees under the
 * stage it is in. The close-up cameras are stands that exist only on this client.
 */
public final class PackScenario implements Scenario {
    /** A settled Flanker counts as 4 to 5 blocks out within this much (its hold tolerance). */
    // Half a block: settled Flankers measure 3.6 to 5.0 across runs, because the player is knocked about by hits.
    // (Flankers on their way round to a slot, down to 3.2, were counted as settled until FLANK_ARRIVED.)
    private static final double FLANK_SLACK = 0.5;
    /** Stand still this many ticks from when the normal wave appears. */
    private static final int STAND_TICKS = 200;
    /** A member counts as settled after this many ticks idle in its role... */
    private static final int SETTLE_TICKS = 40;
    /** ...and, for the Taunter, this many since its last feint order... */
    private static final int FEINT_QUIET = 25;
    /**
     * ...and only while the player has stood still this long: every hit the player takes knocks it
     * back, and the pack only re-plans every 10 ticks, so right after a hit everyone is briefly measured
     * from where the player no longer stands.
     */
    private static final int PLAYER_STILL = 20;
    /**
     * A Flanker also has to be this close to where the pack sent it: one on its way round to a slot is idle
     * in its role, but not settled (circling, it passes 3.2 to 3.5 blocks behind the player). Not standing
     * still: the player is knocked back every second or so, so its slot keeps moving and it keeps creeping.
     */
    private static final double FLANK_ARRIVED = 1.0;
    /** With that, this long idle in its role will do (the 40 ticks gave it time to get back to its slot). */
    private static final int FLANK_IDLE = 20;

    enum Stage { SETUP, WARM_UP, BETWEEN, STAND, FIGHT, AFTER }

    private final Minecraft mc = Minecraft.getInstance();
    private final Monitor monitor = new Monitor();

    @Override
    public void steps(Steps steps) {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> monitor.sample(event.getServer()));
        Set<String> heard = ConcurrentHashMap.newKeySet();
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, event -> {
            if (event.getSound() != null) {
                heard.add(event.getSound().getLocation().toString());
            }
        });
        int[] mark = {0};
        int[] clock = {0};
        Shardling[] telling = {null};
        Vec3[] deathAt = {null};

        setUp(steps);
        steps.run("the warm-up", () -> monitor.stage = Stage.WARM_UP)
                .waitForChat("Pack of 2 incoming", 200)
                .waitUntil("the warm-up pair is on this client", 40, () -> shardlings().size() == 2)
                .check("the warm-up pair rests 1.5 times as long", () -> ServerQuery.ask(player ->
                        paces(player).equals(List.of(WaveSchedule.WARM_UP_PACE, WaveSchedule.WARM_UP_PACE))))
                .log("the pair", () -> "warm-up pair: " + describeClient())
                .waitUntil("the pair has run in to about 9 blocks", 200, () -> nearest() != null && nearest().distanceTo(mc.player) < 9.0)
                .screenshot("approach")
                .waitUntil("a warm-up Shardling has attacked, rested and attacked again", 600, () -> !monitor.gaps(true).isEmpty())
                .log("the warm-up rests", () -> "warm-up: ticks from the end of an attack to the next: " + monitor.gaps(true)
                        + "; hits taken " + monitor.stat(Stage.WARM_UP).hits)
                .check("every warm-up rest was at least 60 ticks (1.5 x 40)", () ->
                        monitor.gaps(true).stream().allMatch(g -> g >= Math.round(ShardlingMoves.REST_TICKS * WaveSchedule.WARM_UP_PACE)))
                .check("at most 2 attackers during the warm-up too", () -> monitor.stat(Stage.WARM_UP).maxAttackers <= AttackTokens.PER_TARGET)

                // ------------------------------------------------ the next pack, at the normal pace
                .run("between waves", () -> monitor.stage = Stage.BETWEEN)
                .command("kill @e[type=cosmicbreach:shardling]")
                .waitForChat("Pack cleared", 60)
                .run("note when the pair was cleared", () -> mark[0] = monitor.ticks())
                .waitForChat("Pack of [345] incoming", 200)
                .check("the next pack came 6 s after the pair fell", () -> {
                    int waited = monitor.ticks() - mark[0];
                    return waited >= 110 && waited <= 135;
                })
                .run("stand still against it", () -> {
                    monitor.stage = Stage.STAND;
                    mark[0] = monitor.ticks();
                    mc.gui.getChat().clearMessages(false);
                })
                .waitUntil("the pack of 3 to 5 is on this client", 40, () -> shardlings().size() >= 3)
                .check("it has 3 to 5 Shardlings at the normal pace", () -> shardlings().size() <= WaveSchedule.MAX_SIZE
                        && ServerQuery.ask(player -> paces(player).stream().allMatch(p -> p == 1.0f)))
                .log("the pack", () -> "normal pack: " + describeClient())

                // ------------------------------------------------ a gold telegraph and its lunge, close up
                .waitUntil("a Shardling crouches into a Splinter Lunge telegraph", 400, () -> {
                    telling[0] = shardlings().stream().filter(s -> s.phase() == ShardlingMoves.Phase.TELL).findFirst().orElse(null);
                    return telling[0] != null;
                })
                .run("a camera beside the telling Shardling", () -> sideCamera(telling[0], 3.2))
                .waitTicks(4)
                .screenshot("telegraph_gold")
                .waitUntil("its leap", 20, () -> telling[0].phase() == ShardlingMoves.Phase.LUNGE)
                .waitTicks(1)
                .screenshot("lunge")
                .run("the camera back to the player", () -> mc.setCameraEntity(mc.player))

                // ------------------------------------------------ the pack's shape
                .waitUntil("the pack's shape: the Taunter in front and a Flanker behind the player", 400, () -> monitor.formationNow)
                .run("a camera above the player", () -> topCamera(13.0))
                .waitTicks(1)
                .screenshot("flank_top")
                .run("a camera to the player's side", () -> playerSideCamera(9.0))
                .waitTicks(1)
                .screenshot("flank_side")
                .run("the camera back to the player", () -> mc.setCameraEntity(mc.player))
                .waitUntil("10 s of standing still since the pack appeared", 400, () -> monitor.ticks() >= mark[0] + STAND_TICKS)
                .run("the fight", () -> monitor.stage = Stage.FIGHT)
                .log("the pack, second by second", () -> String.join(System.lineSeparator(), monitor.seconds))
                .log("the stand-still", () -> monitor.stat(Stage.STAND).summary())
                .log("the player and the front", () -> monitor.stat(Stage.STAND).player())
                .check("the hits staggered the player", () -> monitor.stat(Stage.STAND).playerStaggers >= 1)
                // When every hit from behind handed the Taunter's role to the hitter, a full pack moved it about
                // once a second and nobody held the front. Now the front changes hands only once the hitter has
                // run round to it (2 to 3 s), so a few times in 10 s at most.
                .check("the front changed hands at most 3 times", () -> monitor.stat(Stage.STAND).handovers <= 3)
                .log("the rests", () -> "normal pace: ticks from the end of an attack to the next: " + monitor.gaps(false))
                .check("at most 2 Shardlings attacked the player at once", () -> monitor.stat(Stage.STAND).maxAttackers <= AttackTokens.PER_TARGET)
                .check("at most 2 attack tokens were out for the player", () -> monitor.stat(Stage.STAND).maxTokens <= AttackTokens.PER_TARGET)
                .check("the pressure did reach 2 attackers at once", () -> monitor.stat(Stage.STAND).maxAttackers == AttackTokens.PER_TARGET)
                .check("standing still is punished: the player took at least 3 hits in 10 s", () -> monitor.stat(Stage.STAND).hits >= 3)
                .check("a gold telegraph was seen", () -> monitor.stat(Stage.STAND).tells >= 1)
                .check("a settled Flanker was seen 120 to 180 degrees behind at 4 to 5 blocks",
                        () -> monitor.stat(Stage.STAND).flankSettled > 0)
                .check("every settled Flanker was 4 to 5 blocks out, give or take half a block", () -> monitor.stat(Stage.STAND).flankOutOfBand == 0)
                // The Taunter attacks and feints too, so a settled Taunter may not be seen at all in 10 s; the
                // shardling scenario checks its settling under control. Here: whenever it was seen, it was in band.
                .check("whenever the Taunter was settled, it was 4 to 6 blocks out", () -> monitor.stat(Stage.STAND).taunterOutOfBand == 0)
                .check("the normal pack's rests were at least 40 ticks", () -> monitor.gaps(false).stream()
                        .allMatch(g -> g >= ShardlingMoves.REST_TICKS))

                // ------------------------------------------------ fight back until one dies
                .run("first person with the HUD", () -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                    mc.gui.getChat().clearMessages(false);
                })
                .waitUntil("a Shardling dies to Meridian", 1200, () -> {
                    clock[0]++;
                    if (monitor.deaths.get() > 0) {
                        key(mc.options.keyUp, false);
                        key(mc.options.keyAttack, false);
                        return true;
                    }
                    fight(clock[0]);
                    return false;
                })
                .run("where it died", () -> deathAt[0] = monitor.lastDeath)
                .log("the kill", () -> "a Shardling died at " + fmt(deathAt[0]) + " after " + monitor.stat(Stage.FIGHT).playerHits
                        + " Meridian hits on Shardlings")
                .waitUntil("its three shards are on this client", 20, () -> fragments().size() >= 3)
                .waitTicks(3)
                .run("look at the shards", () -> lookAt(deathAt[0].add(0, -0.4, 0)))
                .waitTicks(1)
                .screenshot("death_shards")
                .run("a camera above the shards", () -> lookFrom(deathAt[0].add(2.0, 6.0, 2.0), deathAt[0]))
                .waitTicks(10)
                .screenshot("shard_timers_top")
                .run("the camera back to the player", () -> mc.setCameraEntity(mc.player))
                .run("look at the shards", () -> lookAt(deathAt[0].add(0, -0.4, 0)))
                .waitUntil("the shards burst", 40, () -> monitor.bursts.get() >= 1)
                .screenshot("shard_burst")
                .waitUntil("all three burst", 40, () -> monitor.bursts.get() >= 3)
                .log("the drops", () -> "near the death: " + drops(deathAt[0]))
                .check("it dropped Starshards and experience", () -> drops(deathAt[0]).contains("starshard") && drops(deathAt[0]).contains("xp"))
                .run("after the fight", () -> monitor.stage = Stage.AFTER)
                .log("the leaps", () -> "leaps, from the telegraph's spot to where the recovery ended: " + monitor.leaps)
                .check("a leap that missed carried about 5 blocks (4.5 to 5.5)", () -> monitor.leaps.stream()
                        .filter(l -> l.endsWith("missed")).allMatch(l -> {
                            double d = Double.parseDouble(l.substring(0, l.indexOf(' ')));
                            return d >= 4.5 && d <= 5.5;
                        }))
                .check("never more than 2 attackers at once, the whole run", () -> monitor.maxAttackersEver.get() <= AttackTokens.PER_TARGET)
                .check("a Taunter feinted", () -> monitor.feints.get() >= 1)
                .log("the Shardling sounds heard", () -> "sounds: " + heard.stream().filter(s -> s.contains("shardling")).sorted().toList())
                .check("every Shardling sound was played", () -> {
                    List<String> missing = new ArrayList<>();
                    for (var sound : List.of(ModSounds.SHARDLING_STEP, ModSounds.SHARDLING_TELL, ModSounds.SHARDLING_LUNGE,
                            ModSounds.SHARDLING_HURT, ModSounds.SHARDLING_DEATH, ModSounds.SHARDLING_SPIT, ModSounds.SHARDLING_NEEDLE_HIT,
                            ModSounds.SHARDLING_SHARD_TICK, ModSounds.SHARDLING_SHARD_BURST)) {
                        if (!heard.contains(sound.getId().toString())) {
                            missing.add(sound.getId().getPath());
                        }
                    }
                    if (!missing.isEmpty()) {
                        throw new Steps.Failure("never played: " + missing);
                    }
                    return true;
                })

                // ------------------------------------------------ the commands
                .run("count the Shardlings before the wave", () -> mark[0] = shardlings().size())
                .command("cosmicbreach wave 4")
                .waitForChat("Pack of 4 incoming", 60)
                .waitUntil("four more Shardlings are on this client", 40, () -> shardlings().size() >= mark[0] + 4)
                .command("cosmicbreach stop")
                .waitForChat("Sandbox stopped", 60)
                .waitUntil("no Shardling is left", 40, () -> shardlings().isEmpty())
                .run("put the HUD back", () -> mc.options.hideGui = false);
    }

    private void setUp(Steps steps) {
        steps.look(0, 0)
                .waitTicks(3)
                .command("cosmicbreach sandbox")
                .waitForChat("Arena ready", 200)
                .waitUntil("the player stands on the arena in survival", 100,
                        () -> !mc.player.isCreative() && mc.player.getY() > -40 && mc.player.onGround())
                .command("effect give @s minecraft:resistance infinite 3 true")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .check("the sandbox also gave a diamond sword and cooked beef", () ->
                        mc.player.getInventory().contains(s -> s.is(Items.DIAMOND_SWORD))
                                && mc.player.getInventory().contains(s -> s.is(Items.COOKED_BEEF)))
                .check("keepInventory is on and the sky is at noon", () -> ServerQuery.ask(player ->
                        player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                                && Math.abs(player.serverLevel().getDayTime() % 24000L - 6000L) < 200L))
                .check("the respawn point is on the arena and the sky is clear", () -> ServerQuery.ask(player -> {
                    Vec3 middle = Sandbox.middleOf(player.getUUID());
                    return middle != null && BlockPos.containing(middle).equals(player.getRespawnPosition())
                            && !player.serverLevel().isRaining() && !player.serverLevel().isThundering();
                }))
                .run("hide the HUD and chat for the shots", () -> {
                    mc.options.hideGui = true;
                    mc.gui.getChat().clearMessages(false);
                })
                .run("start the monitor", () -> monitor.start(mc.player.getUUID()))
                .log("the arena", () -> "standing at " + fmt(mc.player.position()) + " facing yaw " + fmt(mc.player.getYRot()));
    }

    /** The paces of the Shardlings round the player, in no particular order (server thread). */
    private static List<Float> paces(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Shardling.class, player.getBoundingBox().inflate(48), Entity::isAlive)
                .stream().map(Shardling::pace).toList();
    }

    @Override
    public int timeBudgetSeconds() {
        return 240;
    }

    // ------------------------------------------------------------------ the fight, through the keys

    /**
     * One tick of fighting: face the best Shardling (one reeling or recovering close by, else the
     * nearest), walk in while it is out of reach, and tap attack every 5 ticks when it is within reach.
     */
    private void fight(int tick) {
        Shardling target = shardlings().stream()
                .min(Comparator.comparingDouble(s -> s.distanceTo(mc.player) - (vulnerable(s) ? 3.0 : 0.0)))
                .orElse(null);
        if (target == null) {
            key(mc.options.keyUp, false);
            return;
        }
        lookAt(target.position().add(0, target.getBbHeight() * 0.5, 0));
        double distance = target.distanceTo(mc.player);
        key(mc.options.keyUp, distance > 2.6);
        if (mc.options.keyAttack.isDown()) {
            key(mc.options.keyAttack, false);
        } else if (distance < 3.8 && tick % 5 == 0) {
            key(mc.options.keyAttack, true);
        }
    }

    private static boolean vulnerable(Shardling s) {
        ShardlingMoves.Phase phase = s.phase();
        return phase == ShardlingMoves.Phase.RECOVER || phase == ShardlingMoves.Phase.STAGGER || phase == ShardlingMoves.Phase.SPIT;
    }

    /** The same calls the keyboard and mouse handlers make. */
    private static void key(KeyMapping key, boolean down) {
        InputConstants.Key input = key.getKey();
        if (down == key.isDown()) {
            return;
        }
        KeyMapping.set(input, down);
        if (down) {
            KeyMapping.click(input);
        }
    }

    private void lookAt(Vec3 point) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = point.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.hypot(d.x, d.z)) * Mth.RAD_TO_DEG);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
        mc.player.yBodyRot = yaw;
    }

    // ------------------------------------------------------------------ cameras (client-only stands)

    /** Looks from {@code eye} at yaw and pitch through an invisible stand that exists only on this client. */
    private void cameraAt(Vec3 eye, float yaw, float pitch) {
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.moveTo(eye.x, eye.y - stand.getEyeHeight(), eye.z, yaw, pitch);
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
        mc.setCameraEntity(stand);
    }

    private void lookFrom(Vec3 eye, Vec3 at) {
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.hypot(d.x, d.z)) * Mth.RAD_TO_DEG);
        cameraAt(eye, yaw, pitch);
    }

    /**
     * Beside a telling Shardling, {@code distance} blocks off the line from it to the player (it faces
     * the player through its telegraph), a little behind and above, looking at it: its profile, crouched.
     */
    private void sideCamera(Shardling shardling, double distance) {
        Vec3 body = shardling.position().add(0, 0.55, 0);
        Vec3 toPlayer = new Vec3(mc.player.getX() - shardling.getX(), 0, mc.player.getZ() - shardling.getZ());
        toPlayer = toPlayer.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0f, shardling.getYRot()) : toPlayer.normalize();
        Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);
        Vec3 eye = body.add(side.scale(distance)).add(toPlayer.scale(-0.9)).add(0, 0.9, 0);
        lookFrom(eye, body.add(toPlayer.scale(0.6)));
    }

    /** Straight down from {@code height} blocks over the player. */
    private void topCamera(double height) {
        cameraAt(mc.player.position().add(0, height, 0.01), mc.player.getYRot(), 90f);
    }

    /**
     * From the player's left (or right, if a pillar is in the way), {@code distance} blocks out and
     * raised, looking at the player's feet.
     */
    private void playerSideCamera(double distance) {
        Vec3 facing = Vec3.directionFromRotation(0f, mc.player.getYRot());
        Vec3 target = mc.player.position().add(0, 0.5, 0);
        Vec3 eye = null;
        for (double sign : new double[] {1, -1, 0}) {
            Vec3 side = sign == 0 ? facing.scale(-1) : new Vec3(facing.z * sign, 0, -facing.x * sign);
            Vec3 candidate = mc.player.position().add(side.scale(distance)).add(0, 4.0, 0);
            if (clear(candidate, target) && clear(candidate, candidate.add(0, -1.5, 0))) {
                eye = candidate;
                break;
            }
        }
        lookFrom(eye != null ? eye : mc.player.position().add(0, 10.0, 0.01), mc.player.position());
    }

    private boolean clear(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ what this client sees

    private List<Shardling> shardlings() {
        return mc.level.getEntitiesOfClass(Shardling.class, mc.player.getBoundingBox().inflate(48), Entity::isAlive);
    }

    private @Nullable Shardling nearest() {
        return shardlings().stream().min(Comparator.comparingDouble(s -> s.distanceTo(mc.player))).orElse(null);
    }

    private List<ShardFragment> fragments() {
        return mc.level.getEntitiesOfClass(ShardFragment.class, mc.player.getBoundingBox().inflate(48));
    }

    private String describeClient() {
        List<String> parts = new ArrayList<>();
        for (Shardling s : shardlings()) {
            parts.add(String.format(Locale.ROOT, "#%d at %.1f blocks", s.getId(), s.distanceTo(mc.player)));
        }
        return String.join(", ", parts);
    }

    /** "starshard x2, xp" and so on, for what lies within 5 blocks of {@code at} or was picked up. */
    private String drops(Vec3 at) {
        List<String> found = new ArrayList<>();
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(32),
                i -> i.position().distanceTo(at) < 5.0)) {
            if (item.getItem().is(ModItems.STARSHARD.get())) {
                found.add("starshard x" + item.getItem().getCount());
            }
        }
        int carried = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(ModItems.STARSHARD.get())) {
                carried += mc.player.getInventory().getItem(i).getCount();
            }
        }
        if (carried > 0) {
            found.add("starshard x" + carried + " picked up");
        }
        if (!mc.level.getEntitiesOfClass(ExperienceOrb.class, mc.player.getBoundingBox().inflate(32), o -> o.position().distanceTo(at) < 6.0).isEmpty()
                || monitor.xpSeen) {
            found.add("xp");
        }
        return String.join(", ", found);
    }

    private static String fmt(Vec3 v) {
        return v == null ? "nowhere" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    // ------------------------------------------------------------------ the server-side monitor

    /** What the monitor saw in one stage. Written on the server thread, read by the client after the stage. */
    static final class Stat {
        volatile int maxAttackers;
        volatile int maxTokens;
        volatile int hits;
        volatile int playerHits;
        volatile int tells;
        volatile int lunges;
        volatile int spits;
        volatile int staggers;
        volatile int flankSettled;
        volatile int flankOutOfBand;
        volatile int taunterSettled;
        volatile int taunterOutOfBand;
        // the player under the pack's hits, and whether the front stayed held
        volatile int ticksSampled;
        volatile int playerStaggers;
        volatile int staggerTicks;
        volatile float yawMin = Float.MAX_VALUE;
        volatile float yawMax = -Float.MAX_VALUE;
        volatile double drift;
        volatile int frontTicks;
        volatile int handovers;
        volatile double taunterMin = Double.MAX_VALUE;
        volatile double taunterMax;
        volatile double taunterMaxAngle;
        volatile double flankMin = Double.MAX_VALUE;
        volatile double flankMax;
        final List<String> flanks = new CopyOnWriteArrayList<>();
        /** The first few settled Flankers seen out of their band, in detail. */
        final List<String> flankMisses = new CopyOnWriteArrayList<>();

        String player() {
            return String.format(Locale.ROOT,
                    "over %d ticks the player was staggered %d times (%d ticks), faced yaw %.1f to %.1f and was knocked up to %.2f "
                            + "blocks from where it stood; a Taunter held the front (4 to 6 blocks, within 30 degrees) %d ticks; "
                            + "the front changed hands %d times",
                    ticksSampled, playerStaggers, staggerTicks, yawMin, yawMax, drift, frontTicks, handovers);
        }

        String summary() {
            return String.format(Locale.ROOT,
                    "max attackers on the player at once %d, max tokens out %d; telegraphs %d, lunges %d, spits %d, staggers %d; "
                            + "hits taken by the player %d; settled Flankers behind: %d ticks at %.2f to %.2f blocks (%d out of 4 to 5), "
                            + "e.g. %s; settled Taunter: %d ticks at %.2f to %.2f blocks, at most %.0f deg off straight ahead (%d out of 4 to 6)",
                    maxAttackers, maxTokens, tells, lunges, spits, staggers, hits, flankSettled,
                    flankSettled == 0 ? 0 : flankMin, flankMax, flankOutOfBand, flanks,
                    taunterSettled, taunterSettled == 0 ? 0 : taunterMin, taunterMax, taunterMaxAngle, taunterOutOfBand)
                    + (flankMisses.isEmpty() ? "" : "; Flankers out of band: " + flankMisses);
        }
    }

    /** Samples the Shardlings round the player on the server thread, every server tick. */
    private static final class Monitor {
        private volatile UUID player;
        volatile Stage stage = Stage.SETUP;
        private final AtomicInteger ticks = new AtomicInteger();
        private final Map<Stage, Stat> stats = new ConcurrentHashMap<>();
        final AtomicInteger maxAttackersEver = new AtomicInteger();
        final AtomicInteger feints = new AtomicInteger();
        final AtomicInteger deaths = new AtomicInteger();
        final AtomicInteger bursts = new AtomicInteger();
        /** For the shots: the Taunter idle 4 to 6 blocks in front and a Flanker idle behind at 4 to 5, right now. */
        volatile boolean formationNow;
        volatile boolean xpSeen;
        volatile Vec3 lastDeath;
        /** One line a second while standing still: every Shardling's role, phase, angle behind the player and distance. */
        final List<String> seconds = new CopyOnWriteArrayList<>();
        /** Each leap: blocks from where the telegraph stood to where the recovery ended, and hit or missed. */
        final List<String> leaps = new CopyOnWriteArrayList<>();
        /** Ticks from the end of an attack (not a stagger) to the start of the same Shardling's next one. */
        private final List<Integer> warmUpGaps = new CopyOnWriteArrayList<>();
        private final List<Integer> normalGaps = new CopyOnWriteArrayList<>();

        // per Shardling, server thread only
        private final Map<Integer, ShardlingMoves.Phase> lastPhase = new HashMap<>();
        private final Map<Integer, Float> lastHealth = new HashMap<>();
        private final Map<Integer, Vec3> lastSeenAt = new HashMap<>();
        private final Map<Integer, Vec3> tellAt = new HashMap<>();
        private final Map<Integer, Integer> restingSince = new HashMap<>();
        private final Map<Integer, Integer> idleSince = new HashMap<>();
        private final Map<Integer, Integer> roleSince = new HashMap<>();
        private final Map<Integer, ShardlingPack.Role> lastRole = new HashMap<>();
        private final Map<Integer, Integer> lastFeint = new HashMap<>();
        private int fragmentsBefore;
        private int playerHurtBefore;
        private @Nullable Vec3 playerWas;
        private @Nullable Vec3 standAt;
        private boolean wasStaggered;
        private final Map<ShardlingPack, Integer> taunterOf = new java.util.IdentityHashMap<>();
        private int playerMovedAt;

        void start(UUID id) {
            player = id;
        }

        int ticks() {
            return ticks.get();
        }

        Stat stat(Stage s) {
            return stats.computeIfAbsent(s, k -> new Stat());
        }

        List<Integer> gaps(boolean warmUp) {
            return List.copyOf(warmUp ? warmUpGaps : normalGaps);
        }

        void sample(MinecraftServer server) {
            UUID id = player;
            if (id == null) {
                return;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                return;
            }
            int now = ticks.incrementAndGet();
            Stage stage = this.stage;
            Stat stat = stat(stage);
            List<Shardling> all = p.serverLevel().getEntitiesOfClass(Shardling.class, p.getBoundingBox().inflate(48));
            ShardlingPack.Target t = new ShardlingPack.Target(p.getId(), p.getX(), p.getZ(), p.getYRot());
            if (playerWas == null || playerWas.distanceToSqr(p.position()) > 0.02 * 0.02) {
                playerMovedAt = now;
            }
            playerWas = p.position();
            boolean playerStill = now - playerMovedAt >= PLAYER_STILL;
            int attackers = 0;
            boolean flankBehind = false;
            boolean taunterInFront = false;
            List<String> line = new ArrayList<>();
            for (Shardling s : all) {
                if (!s.isAlive()) {
                    continue;
                }
                int sid = s.getId();
                ShardlingMoves moves = s.moves();
                if (moves.attacking() && s.attackTargetId() == p.getId()) {
                    attackers++;
                }
                ShardlingMoves.Phase phase = moves.phase();
                ShardlingMoves.Phase before = lastPhase.put(sid, phase);
                if (phase != before) {
                    phaseChanged(s, before, phase, now, stat);
                }
                Float health = lastHealth.put(sid, s.getHealth());
                if (health != null && s.getHealth() < health - 1e-3) {
                    stat.playerHits++;
                }
                lastSeenAt.put(sid, s.position());
                ShardlingPack.Role role = s.role();
                if (role != lastRole.put(sid, role)) {
                    roleSince.put(sid, now);
                }
                ShardlingPack pack = s.pack();
                ShardlingPack.Order order = pack == null ? null : pack.order(sid);
                if (role == ShardlingPack.Role.TAUNTER && order != null && order.feint()) {
                    Integer last = lastFeint.put(sid, now);
                    if (last == null || now - last > ShardlingPack.PLAN_INTERVAL) {
                        feints.incrementAndGet();
                    }
                }
                double angle = Math.abs(ShardlingPack.angleOf(t, s.getX(), s.getZ()));
                double distance = Math.hypot(s.getX() - p.getX(), s.getZ() - p.getZ());
                boolean inFlankBand = distance >= ShardlingPack.FLANK_RADIUS_MIN - FLANK_SLACK
                        && distance <= ShardlingPack.FLANK_RADIUS_MAX + FLANK_SLACK;
                if (moves.idle() && role == ShardlingPack.Role.FLANKER && angle >= ShardlingPack.FLANK_ANGLE_MIN && inFlankBand) {
                    flankBehind = true;
                }
                if (moves.idle() && role == ShardlingPack.Role.TAUNTER && angle <= 30.0
                        && distance >= ShardlingPack.TAUNT_MIN && distance <= ShardlingPack.TAUNT_MAX) {
                    taunterInFront = true;
                }
                boolean settled = playerStill && moves.idle() && now - idleSince.getOrDefault(sid, now) >= SETTLE_TICKS
                        && now - roleSince.getOrDefault(sid, now) >= SETTLE_TICKS;
                boolean arrived = order != null && Math.hypot(order.x() - s.getX(), order.z() - s.getZ()) <= FLANK_ARRIVED;
                boolean flankSettled = playerStill && moves.idle() && arrived && now - idleSince.getOrDefault(sid, now) >= FLANK_IDLE
                        && now - roleSince.getOrDefault(sid, now) >= FLANK_IDLE;
                if (flankSettled && role == ShardlingPack.Role.FLANKER && angle >= ShardlingPack.FLANK_ANGLE_MIN) {
                    stat.flankSettled++;
                    stat.flankMin = Math.min(stat.flankMin, distance);
                    stat.flankMax = Math.max(stat.flankMax, distance);
                    if (!inFlankBand) {
                        stat.flankOutOfBand++;
                        if (stat.flankMisses.size() < 6) {
                            ShardlingPack.Order o = order;
                            com.cosmicbreach.combat.PlayerCombat pc = com.cosmicbreach.combat.PlayerCombat.existing(p);
                            stat.flankMisses.add(String.format(Locale.ROOT,
                                    "t=%d #%d %.0f deg %.2f b, its order %s; the player still for %d ticks%s",
                                    now, sid, angle, distance, o == null ? "none" : String.format(Locale.ROOT, "%.2f b out, %.2f b from it",
                                            Math.hypot(o.x() - p.getX(), o.z() - p.getZ()), Math.hypot(o.x() - s.getX(), o.z() - s.getZ())),
                                    now - playerMovedAt,
                                    pc != null && pc.machine().isStaggered() ? ", staggered" : ""));
                        }
                    }
                    if (stat.flanks.size() < 6 && (stat.flanks.isEmpty() || now % 20 == 0)) {
                        stat.flanks.add(String.format(Locale.ROOT, "#%d %.0f deg %.2f blocks", sid, angle, distance));
                    }
                }
                if (settled && role == ShardlingPack.Role.TAUNTER && now - lastFeint.getOrDefault(sid, -1000) >= FEINT_QUIET) {
                    stat.taunterSettled++;
                    stat.taunterMin = Math.min(stat.taunterMin, distance);
                    stat.taunterMax = Math.max(stat.taunterMax, distance);
                    stat.taunterMaxAngle = Math.max(stat.taunterMaxAngle, angle);
                    if (distance < ShardlingPack.TAUNT_MIN || distance > ShardlingPack.TAUNT_MAX) {
                        stat.taunterOutOfBand++;
                    }
                }
                line.add(String.format(Locale.ROOT, "#%d %s %s %.0f deg %.1f b", sid, role, phase, angle, distance));
            }
            // A Shardling that was here last tick and is dead or gone now died (counted once the fight is on).
            for (Integer gone : List.copyOf(lastSeenAt.keySet())) {
                Entity e = p.serverLevel().getEntity(gone);
                if (e == null || !e.isAlive()) {
                    if (stage == Stage.FIGHT) {
                        deaths.incrementAndGet();
                        lastDeath = lastSeenAt.get(gone);
                    }
                    lastSeenAt.remove(gone);
                    lastPhase.remove(gone);
                    lastHealth.remove(gone);
                    restingSince.remove(gone);
                    idleSince.remove(gone);
                }
            }
            int fragmentsNow = p.serverLevel().getEntitiesOfClass(ShardFragment.class, p.getBoundingBox().inflate(48)).size();
            if (stage == Stage.FIGHT && fragmentsNow < fragmentsBefore) {
                bursts.addAndGet(fragmentsBefore - fragmentsNow);
            }
            fragmentsBefore = fragmentsNow;
            if (!p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(32)).isEmpty()) {
                xpSeen = true;
            }
            int hurt = p.hurtTime;
            if (hurt > 0 && playerHurtBefore == 0) {
                stat.hits++;
            }
            playerHurtBefore = hurt;
            formationNow = flankBehind && taunterInFront;
            if (stage == Stage.STAND) {
                stat.ticksSampled++;
                com.cosmicbreach.combat.PlayerCombat combat = com.cosmicbreach.combat.PlayerCombat.existing(p);
                boolean staggered = combat != null && combat.machine().isStaggered();
                if (staggered) {
                    stat.staggerTicks++;
                    if (!wasStaggered) {
                        stat.playerStaggers++;
                    }
                }
                wasStaggered = staggered;
                stat.yawMin = Math.min(stat.yawMin, p.getYRot());
                stat.yawMax = Math.max(stat.yawMax, p.getYRot());
                if (standAt == null) {
                    standAt = p.position();
                }
                stat.drift = Math.max(stat.drift, Math.hypot(p.getX() - standAt.x, p.getZ() - standAt.z));
                for (Shardling s : all) {
                    if (s.isAlive() && s.role() == ShardlingPack.Role.TAUNTER) {
                        double d = Math.hypot(s.getX() - p.getX(), s.getZ() - p.getZ());
                        if (d >= ShardlingPack.TAUNT_MIN && d <= ShardlingPack.TAUNT_MAX
                                && Math.abs(ShardlingPack.angleOf(t, s.getX(), s.getZ())) <= 30.0) {
                            stat.frontTicks++;
                            break;
                        }
                    }
                }
                for (Shardling s : all) {
                    if (s.isAlive() && s.pack() != null && s.role() == ShardlingPack.Role.TAUNTER) {
                        Integer before = taunterOf.put(s.pack(), s.getId());
                        if (before != null && before != s.getId()) {
                            stat.handovers++;
                        }
                    }
                }
            }
            int tokens = Shardlings.tokens().held(p.getId());
            maxAttackersEver.accumulateAndGet(attackers, Math::max);
            stat.maxAttackers = Math.max(stat.maxAttackers, attackers);
            stat.maxTokens = Math.max(stat.maxTokens, tokens);
            if (stage == Stage.STAND && now % 20 == 0 && !line.isEmpty()) {
                seconds.add(String.format(Locale.ROOT, "server t=%d: player (%.1f, %.1f) attackers %d, tokens %d | %s", now,
                        p.getX(), p.getZ(), attackers, tokens, String.join("; ", line)));
            }
        }

        private void phaseChanged(Shardling s, ShardlingMoves.Phase before, ShardlingMoves.Phase phase, int now, Stat stat) {
            int sid = s.getId();
            switch (phase) {
                case TELL -> {
                    stat.tells++;
                    tellAt.put(sid, s.position());
                }
                case LUNGE -> stat.lunges++;
                case SPIT_TELL -> stat.spits++;
                case STAGGER -> {
                    stat.staggers++;
                    restingSince.remove(sid); // a stagger's rest is shorter; don't measure it
                }
                default -> {
                }
            }
            if (phase == ShardlingMoves.Phase.TELL || phase == ShardlingMoves.Phase.SPIT_TELL) {
                Integer since = restingSince.remove(sid);
                if (since != null) {
                    (s.pace() > 1.01f ? warmUpGaps : normalGaps).add(now - since);
                }
            }
            if (phase == ShardlingMoves.Phase.NONE) {
                idleSince.put(sid, now);
                if (before == ShardlingMoves.Phase.RECOVER || before == ShardlingMoves.Phase.SPIT) {
                    restingSince.put(sid, now);
                }
            }
            if (before == ShardlingMoves.Phase.RECOVER && phase != ShardlingMoves.Phase.RECOVER) {
                Vec3 from = tellAt.remove(sid);
                if (from != null) {
                    leaps.add(String.format(Locale.ROOT, "%.2f %s", Math.hypot(s.getX() - from.x, s.getZ() - from.z),
                            s.lungeConnected() ? "hit" : "missed"));
                }
            }
        }
    }
}
