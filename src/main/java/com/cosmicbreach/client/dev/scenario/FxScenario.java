package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.AbilityPassthrough;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Mannequin;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.fx.BladeTracker;
import com.cosmicbreach.client.fx.CombatEffects;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxRates;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.net.MoveStartedPayload;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * The effects and sounds, for a look by eye: screenshots of every effect in daylight, each driven
 * through the real key path, in first person and from the side (through an invisible armor stand's
 * eyes), some also from behind: every slash's trail on its contact frame (first person looking level
 * and 30 degrees down, from 10 blocks to the side, and on another player 10 blocks away); a charge and
 * its release into Meridian Line's line of light and its motes; a plunge landing's ring; a parry of
 * the zombie's hit; a perfect dodge and the crit it grants; a backstep's and a forward dash's streaks;
 * Zenith's pillar; Meridian's glow at 0, 50 and 100 Resonance, and its size next to a diamond sword.
 * Checks that every sound event resolves with its subtitle, that the combat sounds were played, that
 * a right click on a chest opens it instead of firing Zenith, and that our particles never exceed the
 * budget, even under a flood of crits. Test mobs and camera stands are discarded rather than killed,
 * so no death smoke or drops get into the shots. While the camera is a stand the client sends none of
 * the player's moves, so anything that depends on where the player went looks through the player first.
 */
public final class FxScenario implements Scenario {
    private static final String MOB_TAG = "cosmicbreach_fx";
    private static final String CAMERA_TAG = "cosmicbreach_fx_camera";

    private final Minecraft mc = Minecraft.getInstance();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private final Set<String> soundsPlayed = new TreeSet<>();
    private final int[] peakDuringPlay = {0};
    private final boolean[] recordingPeak = {true};
    /** Crit sounds heard: the server plays one for every crit that lands. */
    private final int[] critSounds = {0};
    private @Nullable DevCamera devCamera;
    /** Only the slash trails ({@code fx-trails}), for working on them. */
    private final boolean trailsOnly;

    public FxScenario() {
        this(false);
    }

    public FxScenario(boolean trailsOnly) {
        this.trailsOnly = trailsOnly;
    }

    /** Server ticks left to watch for the dash's perfect-dodge window (set by a step, counted on the server). */
    private volatile int dodgeWatch;
    /** True once the zombie struck into that window. */
    private volatile boolean dodgeStruck;

    /**
     * The perfect-dodge window is two server ticks: a client tick can miss it when the server runs behind, so it is
     * watched on the server's own ticks, and the zombie strikes on the first one that finds the dash in it.
     */
    private void watchDodgeWindow(ServerTickEvent.Post event) {
        if (dodgeWatch <= 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (PlayerCombat.of(player).machine().inPerfectDodgeWindow()) {
                dodgeWatch = 0;
                try {
                    zombieStrikes(player);
                    dodgeStruck = true;
                } catch (RuntimeException e) {
                    CosmicBreach.LOGGER.error("[cosmicbreach] fx: the zombie couldn't strike", e); // never into the server's tick
                }
                return;
            }
        }
        dodgeWatch--;
    }

    @Override
    public void steps(Steps steps) {
        ClientCombat.addEventListener(events::add);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::watchDodgeWindow);
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, event -> {
            if (event.getSound() != null) {
                ResourceLocation sound = event.getSound().getLocation();
                soundsPlayed.add(sound.toString());
                if (sound.equals(ModSounds.HIT_CRIT.getId())) {
                    critSounds[0]++;
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (recordingPeak[0]) {
                peakDuringPlay[0] = Math.max(peakDuringPlay[0], FxBudget.live());
            }
        });

        setUp(steps);
        soundRegistry(steps);
        if (trailsOnly) {
            trails(steps);
            remoteTrails(steps);
            return;
        }
        glowStages(steps);
        trails(steps);
        remoteTrails(steps);
        hitSparks(steps);
        chargeAndLine(steps);
        plunge(steps);
        parry(steps);
        perfectDodgeAndCrit(steps);
        dash(steps);
        zenith(steps);
        chestOpens(steps);
        budget(steps);
        soundsHeard(steps);
    }

    // ------------------------------------------------------------------ sections

    private void setUp(Steps steps) {
        steps.command("gamemode survival")
                .waitUntil("the player is in survival", 40, () -> !mc.player.isCreative())
                .command("effect give @s minecraft:resistance infinite 4 true")
                .command("effect give @s minecraft:saturation infinite 0 true")
                .command("cosmicbreach give")
                .waitUntil("Meridian is in the main hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get()))
                .look(0, 0)
                .waitUntil("the sky shows noon", 100, () -> Math.abs(mc.level.getDayTime() % 24000L - 6000L) < 100L)
                .waitUntil("the machine is idle with Meridian's data", 60,
                        () -> machine().weapon() != null && machine().phase() == Phase.IDLE);
    }

    /**
     * Every sound event of the mod resolves to its files and has a translated subtitle, whichever register
     * it came from (later tasks keep their own); the loop is not streamed.
     */
    private void soundRegistry(Steps steps) {
        steps.check("every sound event is in sounds.json with a translated subtitle", () -> {
            List<String> problems = new ArrayList<>();
            int count = 0;
            for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                if (!id.getNamespace().equals(CosmicBreach.MOD_ID)) {
                    continue;
                }
                count++;
                WeighedSoundEvents sound = mc.getSoundManager().getSoundEvent(id);
                if (sound == null) {
                    problems.add(id + " has no sounds");
                } else if (sound.getSubtitle() == null
                        || !(sound.getSubtitle().getContents() instanceof TranslatableContents subtitle)
                        || !I18n.exists(subtitle.getKey())) {
                    problems.add(id + " has no translated subtitle");
                }
            }
            if (!problems.isEmpty()) {
                throw new Steps.Failure(String.join("; ", problems));
            }
            return count >= ModSounds.SOUNDS.getEntries().size() && count >= 30; // Slice 0's 24 and the Comet Maul's 6, at least
        })
                .check("the Shardling tell's subtitle says what it means", () -> "Shardling readies a lunge"
                        .equals(I18n.get("subtitles.cosmicbreach.shardling.tell")))
                .check("the charge loop is loaded whole, not streamed", () -> {
                    WeighedSoundEvents loop = mc.getSoundManager().getSoundEvent(ModSounds.CHARGE_LOOP.getId());
                    return loop != null && !loop.getSound(mc.level.getRandom()).shouldStream();
                })
                .check("Meridian has the resonance item property", () -> ItemProperties.getProperty(
                        mc.player.getMainHandItem(), CosmicBreach.id("resonance")) != null);
    }

    /**
     * Meridian at 0, 50 and 100 Resonance in first person and close up from the side; then a diamond
     * sword the same way, for the size.
     */
    private void glowStages(Steps steps) {
        steps.run("hide the chat", this::clearChat);
        for (int value : new int[] {0, 50, 100}) {
            resonance(steps, value);
            steps.check("the item property reads " + value + "%", () -> Math.abs(property() - value / 100f) < 0.02f)
                    .screenshot("glow_" + value + "_first_person");
            itemCamera(steps);
            steps.screenshot("glow_" + value + "_side");
            playerCamera(steps);
        }
        steps.command("give @s minecraft:diamond_sword")
                .press(mc.options.keyHotbarSlots[1])
                .waitUntil("a diamond sword in hand", 40, () -> mc.player.getMainHandItem().is(Items.DIAMOND_SWORD))
                .run("hide the chat", this::clearChat)
                .waitTicks(15)
                .screenshot("compare_diamond_sword_first_person");
        itemCamera(steps);
        steps.screenshot("compare_diamond_sword_side");
        playerCamera(steps);
        steps.press(mc.options.keyHotbarSlots[0])
                .waitUntil("Meridian back in hand", 40, () -> mc.player.getMainHandItem().is(ModItems.MERIDIAN.get())
                        && machine().weapon() != null)
                .waitTicks(15)
                .screenshot("compare_meridian_first_person");
    }

    /**
     * Every slashing move's trail, driven through the real input path with no target in the way: in
     * first person looking level and looking 30 degrees down, then from a camera 10 blocks to the side
     * at the normal field of view. Each is shot on the frame of its first active tick (the contact)
     * and a tick later.
     */
    private void trails(Steps steps) {
        steps.run("hide the HUD for the effect shots", () -> mc.options.hideGui = true);
        removeTestEntities(steps);
        trailMoves(steps, "fp_p0", 0f);
        trailMoves(steps, "fp_p30", 30f);
        trailMoves(steps, "side10", 0f);
        steps.run("look through the player", () -> mc.setCameraEntity(mc.player))
                .waitTicks(3);
    }

    private void trailMoves(Steps steps, String view, float pitch) {
        KeyMapping attack = mc.options.keyAttack;
        // The light chain, each pressed as the one before ends.
        idle(steps, pitch, true);
        for (String move : new String[] {"l1", "l2", "l3"}) {
            steps.waitUntil("idle, the chain waiting for " + move, 40, () -> machine().phase() == Phase.IDLE);
            aimSide(steps, view, 1.0);
            steps.hold(attack).waitTicks(1).release(attack);
            contact(steps, view, MoveKind.LIGHT, move);
        }
        // Hold into the charge, release into Meridian Line.
        idle(steps, pitch, true);
        aimSide(steps, view, 1.0);
        steps.hold(attack)
                .waitUntil("charged past the minimum", 60, () -> machine().phase() == Phase.CHARGING && machine().attackHeldTicks() >= 20)
                .release(attack);
        contact(steps, view, MoveKind.CHARGED, "line");
        // A forward dash, then Pass out of its last ticks.
        idle(steps, pitch, false);
        steps.hold(mc.options.keyUp).hold(ModKeyMappings.DASH).waitTicks(1)
                .release(ModKeyMappings.DASH).release(mc.options.keyUp)
                .waitUntil("the dash's last ticks", 10, () -> machine().isDashing() && machine().dashElapsed() >= 4);
        aimSide(steps, view, 2.0);
        steps.hold(attack).waitTicks(1).release(attack);
        contact(steps, view, MoveKind.DASH_ATTACK, "pass");
        // Zenith: Resonance 100 and a held right click, rising with it.
        resonance(steps, 100);
        idle(steps, pitch, false);
        steps.waitUntil("the ability is ready", 200, () -> machine().abilityCooldown() == 0);
        aimSide(steps, view, 1.2);
        steps.hold(mc.options.keyUse);
        contact(steps, view, MoveKind.ABILITY, "zenith");
        steps.release(mc.options.keyUse)
                .waitUntil("back on the ground and idle", 80, () -> mc.player.onGround() && machine().phase() == Phase.IDLE);
    }

    /** The frame of the move's first active tick, and the one a tick later. */
    private void contact(Steps steps, String view, MoveKind kind, String move) {
        steps.waitUntil(move + "'s first active tick", 30, () -> active(kind, move))
                .screenshot("trail_" + move + "_" + view)
                .screenshot("trail_" + move + "_" + view + "_late")
                .log("the trail's blade poses", () -> describeTrail(move));
    }

    /** How many blade poses the trail had to draw from (for the log). */
    private String describeTrail(String move) {
        List<BladeTracker.BladePose> poses = BladeTracker.poses(mc.player);
        long ofMove = poses.stream().filter(p -> p.animation() != null && p.animation().getPath().endsWith(move)).count();
        BladeTracker.BladePose last = poses.isEmpty() ? null : poses.get(poses.size() - 1);
        return String.format(Locale.ROOT, "%s: %d blade poses of this move kept, the latest %s at animation time %.2f",
                move, ofMove, last == null ? "none" : last.space(), last == null ? 0.0 : last.animationTime());
    }

    /**
     * Idle on the ground facing south {@code pitch} down (the chain given time to reset if asked), looking
     * through the player meanwhile: through another camera the client sends none of the player's moves.
     */
    private void idle(Steps steps, float pitch, boolean resetChain) {
        steps.run("look through the player", () -> mc.setCameraEntity(mc.player))
                .waitUntil("idle with a dash charge", 100, () -> machine().phase() == Phase.IDLE && !machine().isDashing()
                        && machine().dashCharges() >= 1 && mc.player.onGround())
                .look(0, pitch)
                .waitTicks(resetChain ? 12 : 3)
                .run("hide the chat", this::clearChat);
    }

    /**
     * For a side view: a camera 10 blocks to the player's right at the normal field of view, looking at
     * the player's chest {@code ahead} blocks forward, placed where the player is now.
     */
    private void aimSide(Steps steps, String view, double ahead) {
        if (!view.startsWith("side")) {
            return;
        }
        steps.run("a camera 10 blocks to the side", () -> {
            if (devCamera == null) {
                devCamera = DevCamera.create(mc.level);
            }
            Vec3 feet = mc.player.position();
            devCamera.place(feet.add(-10.0, 1.5, ahead), feet.add(0.0, 1.2, ahead));
            devCamera.use();
        });
    }

    /**
     * Another player's trails: a mannequin (a remote player that exists only here, holding Meridian)
     * 10 blocks ahead, side on, told by the server's own payload that it started L2, L3 and Zenith.
     */
    private void remoteTrails(Steps steps) {
        Mannequin[] other = {null};
        steps.run("look through the player", () -> mc.setCameraEntity(mc.player))
                .look(0, 0)
                .waitTicks(3)
                .run("a mannequin 10 blocks ahead, facing east", () -> other[0] = Mannequin.spawn(mc.level,
                        mc.player.position().add(0, 0, 10), -90f, new ItemStack(ModItems.MERIDIAN.get())))
                .waitTicks(10);
        for (String move : new String[] {"l2", "l3", "zenith"}) {
            MoveDef def = CombatData.client().move(CosmicBreach.id("meridian/" + move));
            if (def == null) {
                throw new Steps.Failure("no move meridian/" + move);
            }
            int startup = def.timing().startup();
            // A remote player's animation shows its tick t during the t-th tick after the payload: its contact comes at startup.
            steps.run("the server says the mannequin started " + move, () -> ClientCombat.onMoveStarted(
                    new MoveStartedPayload(other[0].body().getId(), CosmicBreach.id("meridian/" + move))))
                    .waitTicks(startup)
                    .screenshot("trail_" + move + "_remote10")
                    .screenshot("trail_" + move + "_remote10_late")
                    .waitTicks(20);
        }
        steps.run("remove the mannequin", () -> other[0].remove());
    }

    /** An ordinary hit: L1 on a zombie, its sparks and flash, in first person and from the side. */
    private void hitSparks(Steps steps) {
        for (String view : new String[] {"first_person", "side"}) {
            freshZombie(steps, 2.5);
            camera(steps, view);
            steps.hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                    .waitUntil("L1's first active tick", 10, () -> active(MoveKind.LIGHT, "l1"))
                    .waitTicks(1)
                    .screenshot("hit_sparks_" + view)
                    .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE);
            if (view.equals("side")) {
                playerCamera(steps);
            }
        }
        camera(steps, "first_person");
        removeTestEntities(steps);
    }

    /** Hold into a charge, release into Meridian Line: its line of light runs out, lingers and shatters. */
    private void chargeAndLine(Steps steps) {
        KeyMapping attack = mc.options.keyAttack;
        removeTestEntities(steps);
        steps.look(0, 20)
                .waitTicks(3)
                .run("show the hand for the charge", () -> mc.options.hideGui = false)
                .hold(attack)
                .waitUntil("charging past the minimum", 40, () -> machine().phase() == Phase.CHARGING && machine().attackHeldTicks() >= 16)
                .screenshot("charge_mid")
                .run("hide the HUD again", () -> mc.options.hideGui = true)
                .waitUntil("charged to full", 30, () -> machine().attackHeldTicks() >= 25)
                .release(attack)
                .waitUntil("the Line's first active tick", 20, () -> active(MoveKind.CHARGED, "line"))
                .waitTicks(2)
                .screenshot("line_running")
                .waitTicks(3)
                .screenshot("line_full")
                .waitTicks(8)
                .screenshot("line_lingers")
                .waitTicks(3)
                .screenshot("line_shatters")
                .waitTicks(3)
                .screenshot("line_motes")
                .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE)
                .look(0, 0);
        camera(steps, "side");
        steps.hold(attack)
                .waitUntil("charged", 60, () -> machine().phase() == Phase.CHARGING && machine().attackHeldTicks() >= 20)
                .screenshot("charge_side")
                .release(attack)
                .waitUntil("the Line's first active tick", 20, () -> active(MoveKind.CHARGED, "line"))
                .waitTicks(2)
                .screenshot("line_side")
                .waitTicks(5)
                .screenshot("line_side_late")
                .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE);
        playerCamera(steps);
    }

    /** Falling Star from 6 blocks up: the ground ring and sparks, first person (looking down) and from behind. */
    private void plunge(Steps steps) {
        for (boolean back : new boolean[] {false, true}) {
            String view = back ? "back" : "first_person";
            camera(steps, view);
            long[] landedBefore = {0L};
            steps.run("clear the events", events::clear)
                    .run("note the server's last plunge", () -> landedBefore[0] = ServerQuery.ask(player ->
                            PlayerCombat.of(player).server().plungeLandedAt()))
                    .command("tp @s ~ ~6 ~")
                    .waitUntil("in the air", 20, () -> !mc.player.onGround() && mc.player.getY() > -59)
                    .look(0, 90)
                    .waitTicks(2) // the server learns the pitch before the press uses it
                    .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                    .run("from behind, look down at the landing", () -> {
                        if (back) {
                            mc.player.setXRot(60f);
                            mc.player.xRotO = 60f;
                        }
                    })
                    .waitUntil("the plunge landed", 60, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.PlungeLanded))
                    .waitTicks(1)
                    .screenshot("plunge_ring_" + view)
                    .check("the server's machine landed the plunge too", () -> ServerQuery.ask(player ->
                            PlayerCombat.of(player).server().plungeLandedAt()) > landedBefore[0])
                    .waitTicks(3)
                    .screenshot("plunge_ring_" + view + "_late")
                    .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE && mc.player.onGround())
                    .log("the plunge", () -> events.stream().filter(e -> e instanceof CombatEvent.PlungeLanded)
                            .map(e -> String.format(Locale.ROOT, "plunge landed after %.2f blocks", ((CombatEvent.PlungeLanded) e).fallBlocks()))
                            .findFirst().orElse("no landing"))
                    .look(0, 0);
        }
        camera(steps, "first_person");
    }

    /** V, then the zombie's hit while the server's parry is up: the flash and the ring. */
    private void parry(Steps steps) {
        for (String view : new String[] {"first_person", "side"}) {
            freshZombie(steps, 1.8);
            camera(steps, view);
            steps.run("clear the events", events::clear)
                    .waitUntil("parry ready", 40, () -> !machine().inParryWhiff() && !machine().isParrying())
                    .press(ModKeyMappings.PARRY)
                    .waitUntil("the zombie struck into the server's parry", 10, () -> ServerQuery.ask(player -> {
                        if (!PlayerCombat.of(player).machine().isParrying()) {
                            return false;
                        }
                        zombieStrikes(player);
                        return true;
                    }))
                    .waitUntil("the parry landed on this client", 10, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.ParrySucceeded))
                    .screenshot("parry_flash_" + view)
                    .waitTicks(2)
                    .screenshot("parry_ring_" + view)
                    .waitTicks(20);
            if (view.equals("side")) {
                playerCamera(steps);
            }
        }
        camera(steps, "first_person");
    }

    /**
     * A backstep and the zombie's hit in its first two ticks: the glint burst; then the crit it grants
     * (the next attack within 20 ticks), on the zombie moved back in front of the player.
     */
    private void perfectDodgeAndCrit(Steps steps) {
        for (String view : new String[] {"first_person", "side"}) {
            boolean side = view.equals("side");
            freshZombie(steps, 1.8);
            camera(steps, view);
            steps.run("clear the events", events::clear)
                    .waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && machine().phase() == Phase.IDLE)
                    .run("watch the server's next 20 ticks for the perfect-dodge window", () -> {
                        dodgeStruck = false;
                        dodgeWatch = 20;
                    })
                    .press(ModKeyMappings.DASH)
                    .waitUntil("the server's watch is over", 200, () -> dodgeStruck || dodgeWatch <= 0)
                    .check("the zombie struck into the server's perfect dodge window", () -> dodgeStruck)
                    .waitUntil("the perfect dodge reached this client", 10, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.PerfectDodge))
                    .screenshot("perfect_dodge_" + view)
                    .waitTicks(2)
                    .screenshot("perfect_dodge_" + view + "_late")
                    .check("the next attack is a guaranteed crit", () -> machine().isCritGuaranteedWindow())
                    .waitUntil("the dash is over and the player stands still", 40, () -> !machine().isDashing()
                            && mc.player.getDeltaMovement().horizontalDistance() < 0.01);
            if (side) {
                // Looking through a stand, the client sends none of the player's moves: look through the
                // player for a moment, so the server learns where the dash went before placing the zombie.
                steps.run("look through the player", () -> mc.setCameraEntity(mc.player)).waitTicks(2);
            }
            steps.command("tp @e[tag=" + MOB_TAG + "] ^ ^ ^2.5")
                    .waitTicks(1)
                    .check("the crit window is still open", () -> machine().isCritGuaranteedWindow())
                    .run("count crits from here", () -> critSounds[0] = 0)
                    .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack);
            if (side) {
                steps.run("the side view again", () -> mc.setCameraEntity(cameraStand()));
            }
            steps.waitUntil("the crit landed (its sound came from the server)", 20, () -> critSounds[0] > 0)
                    .screenshot("crit_" + view)
                    .waitTicks(1)
                    .screenshot("crit_" + view + "_late")
                    .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE);
            if (side) {
                playerCamera(steps);
            }
        }
        camera(steps, "first_person");
    }

    /** A backstep in first person (the streaks hang in front) and a forward dash from the side. */
    private void dash(Steps steps) {
        removeTestEntities(steps);
        steps.waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && machine().phase() == Phase.IDLE && !machine().isDashing())
                .press(ModKeyMappings.DASH)
                .waitTicks(3)
                .screenshot("dash_backstep_first_person")
                .waitUntil("a dash charge", 80, () -> machine().dashCharges() >= 1 && !machine().isDashing() && mc.player.onGround());
        camera(steps, "side");
        steps.hold(mc.options.keyUp)
                .hold(ModKeyMappings.DASH)
                .waitTicks(1)
                .release(ModKeyMappings.DASH)
                .release(mc.options.keyUp)
                .waitTicks(4)
                .screenshot("dash_forward_side")
                .waitTicks(10);
        playerCamera(steps);
    }

    /** Resonance 100 and a held right click: Zenith's pillar, with the zombie launched through it. */
    private void zenith(Steps steps) {
        for (String view : new String[] {"first_person", "back", "side"}) {
            freshZombie(steps, 1.6);
            camera(steps, view);
            resonance(steps, 100);
            steps.waitUntil("the ability is ready", 200, () -> machine().abilityCooldown() == 0 && machine().phase() == Phase.IDLE)
                    .hold(mc.options.keyUse)
                    .waitUntil("Zenith's first active tick", 20, () -> active(MoveKind.ABILITY, "zenith"))
                    .waitTicks(2)
                    .screenshot("zenith_pillar_" + view)
                    .waitTicks(4)
                    .screenshot("zenith_pillar_" + view + "_late")
                    .release(mc.options.keyUse)
                    .waitUntil("back on the ground and idle", 80, () -> mc.player.onGround() && machine().phase() == Phase.IDLE);
            if (view.equals("side")) {
                playerCamera(steps);
            }
        }
        camera(steps, "first_person");
    }

    /** A right click on a chest with Meridian in hand opens the chest and fires nothing. */
    private void chestOpens(Steps steps) {
        double[] resonanceBefore = {0};
        int[] cooldownBefore = {0};
        removeTestEntities(steps);
        steps.run("show the HUD", () -> mc.options.hideGui = false)
                .look(0, 0)
                .waitTicks(3)
                .command("setblock ^ ^ ^2 minecraft:chest")
                .look(0, 25)
                .waitUntil("the crosshair is on the chest", 40, () -> mc.hitResult instanceof BlockHitResult hit
                        && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.CHEST))
                .check("the chest counts as interactive", () -> AbilityPassthrough.targetsInteractiveBlock(mc, mc.player))
                .run("note Resonance and the ability cooldown", () -> {
                    resonanceBefore[0] = machine().resonance();
                    cooldownBefore[0] = machine().abilityCooldown();
                })
                .run("clear the events", events::clear)
                .press(mc.options.keyUse)
                .waitUntil("the chest opened", 40, () -> mc.screen instanceof ContainerScreen)
                .screenshot("chest_opened")
                .log("after the click", () -> String.format(Locale.ROOT, "events %s, move %s, cooldown %d (was %d), Resonance %.2f (was %.2f)",
                        events, machine().current(), machine().abilityCooldown(), cooldownBefore[0], machine().resonance(),
                        resonanceBefore[0]))
                .check("no ability fired", () -> events.stream().noneMatch(e -> e instanceof CombatEvent.MoveStarted)
                        && machine().current() == null && machine().abilityCooldown() <= cooldownBefore[0]
                        && Math.abs(machine().resonance() - resonanceBefore[0]) < 1.0)
                .check("and not on the server either", () -> ServerQuery.ask(player -> PlayerCombat.of(player).machine().current() == null))
                .run("close the chest", () -> mc.player.closeContainer())
                .waitUntil("the chest closed", 20, () -> mc.screen == null)
                .command("setblock ^ ^ ^2 minecraft:air")
                .look(0, 0);
    }

    /** A flood of crits: the budget holds at the limit and drains back. */
    private void budget(Steps steps) {
        steps.run("stop the play peak", () -> recordingPeak[0] = false)
                .log("the peak during play", () -> "our particles, peak during the effect shots: " + peakDuringPlay[0])
                .check("the peak during play stayed within the budget", () -> peakDuringPlay[0] <= FxRates.LIMIT)
                .run("flood: 200 crits at once", () -> {
                    FxBudget.resetPeak();
                    Vec3 at = mc.player.position().add(0, 1.2, 3.0);
                    for (int i = 0; i < 200; i++) {
                        CombatEffects.hit(at, new Vec3(0, 0, 1), 25f, true);
                    }
                })
                .log("after the flood", () -> "our particles after 200 crits: " + FxBudget.live() + ", world effects " + WorldFx.count())
                .check("the flood filled the budget and no more", () -> FxBudget.live() == FxRates.LIMIT)
                .screenshot("budget_flood")
                .waitTicks(5)
                .check("still within the budget", () -> FxBudget.live() <= FxRates.LIMIT && FxBudget.peak() <= FxRates.LIMIT)
                .waitUntil("the flood drained away", 100, () -> FxBudget.live() == 0)
                .log("the flood's peak", () -> "peak " + FxBudget.peak() + " of " + FxRates.LIMIT);
    }

    private void soundsHeard(Steps steps) {
        steps.log("the sounds", () -> "sounds played: " + soundsPlayed)
                .check("every combat sound was played", () -> {
                    List<String> missing = new ArrayList<>();
                    for (DeferredHolder<SoundEvent, SoundEvent> sound : List.of(ModSounds.SWING_LIGHT, ModSounds.SWING_HEAVY,
                            ModSounds.HIT, ModSounds.HIT_CRIT, ModSounds.CHARGE_LOOP, ModSounds.CHARGE_READY,
                            ModSounds.CHARGE_RELEASE, ModSounds.PLUNGE_WHISTLE, ModSounds.PLUNGE_IMPACT, ModSounds.ZENITH,
                            ModSounds.DASH, ModSounds.PARRY, ModSounds.PERFECT_DODGE, ModSounds.RESONANCE_FULL)) {
                        if (!soundsPlayed.contains(sound.getId().toString())) {
                            missing.add(sound.getId().toString());
                        }
                    }
                    if (!missing.isEmpty()) {
                        throw new Steps.Failure("never played: " + missing);
                    }
                    return true;
                })
                .check("no vanilla placeholder combat sounds", () -> soundsPlayed.stream().noneMatch(s ->
                        s.contains("player.attack") || s.contains("mace.smash")))
                .run("show the HUD", () -> mc.options.hideGui = false);
        removeTestEntities(steps);
    }

    // ------------------------------------------------------------------ helpers

    private CombatStateMachine machine() {
        return PlayerCombat.of(mc.player).machine();
    }

    private float property() {
        return ItemProperties.getProperty(mc.player.getMainHandItem(), CosmicBreach.id("resonance"))
                .call(mc.player.getMainHandItem(), mc.level, mc.player, 0);
    }

    /** True in the first active tick of the move of this kind whose id ends with {@code name}. */
    private boolean active(MoveKind kind, String name) {
        MoveInstance current = machine().current();
        return current != null && current.def().kind() == kind && current.id().getPath().endsWith(name)
                && machine().phase() == Phase.ACTIVE && machine().phaseTick() < 1;
    }

    private void clearChat() {
        mc.gui.getChat().clearMessages(false);
    }

    /** First person, from behind, or from the side (through a camera stand). */
    private void camera(Steps steps, String view) {
        switch (view) {
            case "side" -> sideCamera(steps);
            case "back" -> steps.run("third person, back", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            default -> steps.run("first person", () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    /** Resonance to {@code value}, held there: both machines count as in a fight, so it doesn't drift. */
    private void resonance(Steps steps, int value) {
        steps.run("mark the fight on this client", () -> machine().onDamaged())
                .waitUntil("and on the server", 5, () -> ServerQuery.ask(player -> {
                    PlayerCombat.of(player).machine().onDamaged();
                    return true;
                }))
                .command("cosmicbreach debug resonance " + value)
                .waitUntil("the client's Resonance is " + value, 40, () -> Math.abs(machine().resonance() - value) < 0.5)
                .run("hide the chat", this::clearChat)
                .waitTicks(2);
    }

    /** Removes the test zombies, camera stands and dropped items without a death (no smoke, no drops). */
    private void removeTestEntities(Steps steps) {
        steps.run("look through the player", () -> mc.setCameraEntity(mc.player))
                .waitUntil("the test entities are gone", 5, () -> ServerQuery.ask(player -> {
                    for (Entity entity : player.serverLevel().getEntities((Entity) null, player.getBoundingBox().inflate(64),
                            e -> e.getTags().contains(MOB_TAG) || e.getTags().contains(CAMERA_TAG)
                                    || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
                        entity.discard();
                    }
                    return true;
                }))
                .waitTicks(2);
    }

    /**
     * A fresh zombie {@code blocks} ahead, facing the player, made on the server the way SelfTest does:
     * AI on but no goals, so it stands still yet moves when hit (a NoAI mob in 1.21.1 doesn't move at
     * all, so it could not show knockback or Zenith's launch). The pumpkin keeps it from burning.
     */
    private void freshZombie(Steps steps, double blocks) {
        removeTestEntities(steps);
        steps.look(0, 0)
                .waitTicks(3)
                .waitUntil("a zombie stands " + blocks + " blocks ahead on the server", 5, () -> ServerQuery.ask(player -> {
                    Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
                    if (zombie == null) {
                        throw new Steps.Failure("could not create a zombie");
                    }
                    Vec3 at = player.position().add(BodyMotion.forward(player.getYRot()).scale(blocks));
                    float yaw = player.getYRot() + 180f;
                    zombie.moveTo(at.x, at.y, at.z, yaw, 0f);
                    zombie.setYHeadRot(yaw);
                    zombie.setYBodyRot(yaw);
                    zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                    zombie.setDropChance(EquipmentSlot.HEAD, 0f);
                    zombie.goalSelector.removeAllGoals(goal -> true);
                    zombie.targetSelector.removeAllGoals(goal -> true);
                    zombie.setPersistenceRequired();
                    zombie.addTag(MOB_TAG);
                    player.serverLevel().addFreshEntity(zombie);
                    return true;
                }))
                .waitUntil("the zombie stands " + blocks + " blocks ahead", 60, () -> zombieAhead(blocks) != null)
                .run("hide the chat", this::clearChat);
    }

    /** The zombie with our tag hits the player the way its melee attack does (server thread). */
    private static void zombieStrikes(ServerPlayer player) {
        List<Zombie> zombies = player.serverLevel().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(6),
                z -> z.getTags().contains(MOB_TAG));
        if (zombies.isEmpty()) {
            throw new Steps.Failure("no test zombie near the player");
        }
        Zombie zombie = zombies.get(0);
        zombie.swing(InteractionHand.MAIN_HAND);
        player.invulnerableTime = 0;
        zombie.doHurtTarget(player);
    }

    private Zombie zombieAhead(double blocks) {
        Vec3 feet = mc.player.position();
        Vec3 forward = BodyMotion.forward(mc.player.getYRot());
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(blocks + 2.0))) {
            Vec3 d = z.position().subtract(feet);
            double along = d.x * forward.x + d.z * forward.z;
            if (Math.abs(along - blocks) < 0.35 && Math.abs(d.x * forward.z - d.z * forward.x) < 0.35) {
                return z;
            }
        }
        return null;
    }

    /**
     * Looks through an invisible armor stand 6 blocks to the player's right and a little ahead,
     * facing back across the player: the side view. The player keeps facing yaw 0 (south).
     */
    private void sideCamera(Steps steps) {
        standCamera(steps, "^-6 ^-0.2 ^1.2", "[-90f,8f]");
    }

    /** A close side view of the player's right hand, for the held item. */
    private void itemCamera(Steps steps) {
        standCamera(steps, "^-2.3 ^-0.6 ^0.6", "[-90f,12f]");
    }

    private void standCamera(Steps steps, String where, String rotation) {
        steps.look(0, 0)
                .waitTicks(3)
                .command("summon minecraft:armor_stand " + where + " {Invisible:1b,NoGravity:1b,Invulnerable:1b,Tags:[\""
                        + CAMERA_TAG + "\"],Rotation:" + rotation + "}")
                .waitUntil("the camera stand is there", 40, () -> cameraStand() != null)
                .run("look through the stand", () -> mc.setCameraEntity(cameraStand()))
                .run("hide the chat", this::clearChat)
                .waitTicks(3);
    }

    private void playerCamera(Steps steps) {
        removeTestCameras(steps);
    }

    private void removeTestCameras(Steps steps) {
        steps.run("look through the player again", () -> mc.setCameraEntity(mc.player))
                .waitUntil("the camera stands are gone", 5, () -> ServerQuery.ask(player -> {
                    for (Entity entity : player.serverLevel().getEntities((Entity) null, player.getBoundingBox().inflate(64),
                            e -> e.getTags().contains(CAMERA_TAG))) {
                        entity.discard();
                    }
                    return true;
                }))
                .waitTicks(2);
    }

    private ArmorStand cameraStand() {
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(10), ArmorStand::isInvisible);
        return stands.isEmpty() ? null : stands.get(0);
    }

    @Override
    public int timeBudgetSeconds() {
        return 300;
    }
}
