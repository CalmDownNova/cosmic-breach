package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.anim.EngineAnimations;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.client.dev.DevCamera;
import com.cosmicbreach.client.dev.Mannequin;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.net.CombatFxPayload;
import com.cosmicbreach.net.HitFxPayload;
import com.cosmicbreach.net.MoveStartedPayload;
import com.cosmicbreach.registry.ModItems;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The player animations in the real game: shots to look at by eye, and hard checks.
 * <ul>
 *   <li>Poses: every animation on a mannequin (a remote player that exists only in this client, holding
 *       Meridian, played through the same calls as any other player), held still at exact ticks: every
 *       tick from the side, the key ticks from the front and from behind.</li>
 *   <li>Moves: every move through the real input path (attack, hold, release, use, Dash, Parry), shot at
 *       its key ticks in first person, then again from the side. On each move's first active tick the
 *       frame must show the animation at the move's own tick (plus the frame's partial tick).</li>
 *   <li>Walking: the mannequin walks while it swings (its legs must walk, vanilla's way), stands (its
 *       legs must be the animation's) and dashes (planted); the local player walks into a swing too.</li>
 *   <li>Hit-stop: a hit on a zombie holds the local player's animation still for the hit's ticks, then
 *       it catches up and ends in step with the move.</li>
 *   <li>Remote: the mannequin driven by the server's payloads, through the combat runtime's handlers:
 *       each plays its animation; a charge stops when let go and gives way to Meridian Line when
 *       released; the dive keeps looping until its landing plays.</li>
 *   <li>Blade: markers where {@code tools/anim/blade.py} says the guard (blue) and tip (red) are, on
 *       held poses from the side and in first person: they must sit on the drawn blade.</li>
 * </ul>
 * The first-person comparison ({@link Section#FIRST_PERSON_OPTIONS}) is for choosing the first person
 * and is not part of {@code anim}.
 */
public final class AnimScenario implements Scenario {
    public enum Section { POSES, MOVES, WALKING, HIT_STOP, REMOTE, BLADE, FIRST_PERSON_OPTIONS }

    /** Every section but the first-person comparison. */
    public static final Section[] ALL = {Section.POSES, Section.MOVES, Section.WALKING, Section.HIT_STOP, Section.REMOTE,
            Section.BLADE};

    private static final String MOB_TAG = "cosmicbreach_anim";

    /** An animation to pose: every tick in {@code strip} from the side, {@code keys} from front and back. */
    private record Pose(ResourceLocation animation, boolean mirrored, int[] strip, int[] keys) {
        String label() {
            return animation.getPath() + (mirrored ? "_mirror" : "");
        }
    }

    private static final List<Pose> POSES = List.of(
            pose("meridian_l1", range(0, 10), 2, 3, 4, 6),
            pose("meridian_l2", range(0, 11), 2, 3, 4, 6),
            pose("meridian_l3", range(0, 16), 3, 4, 5, 6, 8),
            pose("meridian_charge", new int[] {0, 5, 10, 15}, 0, 10),
            pose("meridian_line", range(0, 19), 2, 3, 4, 5, 6, 9),
            pose("meridian_falling_star", new int[] {0, 1, 2, 5, 8, 11}, 1, 2, 8),
            pose("meridian_falling_star_land", range(0, 10), 0, 2, 5, 7),
            pose("meridian_pass", range(0, 12), 1, 2, 3, 4, 6),
            pose("meridian_zenith", range(0, 19), 3, 5, 6, 7, 8, 10),
            new Pose(EngineAnimations.DASH_FORWARD, false, range(0, 8), new int[] {2, 5, 7}),
            new Pose(EngineAnimations.DASH_BACK, false, range(0, 8), new int[] {2, 5}),
            new Pose(EngineAnimations.DASH_SIDE, false, range(0, 8), new int[] {2, 5}),
            new Pose(EngineAnimations.DASH_SIDE, true, range(0, 8), new int[] {2, 5}),
            new Pose(EngineAnimations.PARRY, false, range(0, 14), new int[] {2, 4, 8}),
            new Pose(EngineAnimations.PARRY_SUCCESS, false, range(0, 6), new int[] {1, 2, 3}),
            new Pose(EngineAnimations.STAGGER, false, range(0, 10), new int[] {2, 4, 6}));

    /** What a frame showed: after which tick, at which partial tick, and each layer's state. */
    private record Frame(long tick, float partial, PlayerAnimations.State local, @Nullable PlayerAnimations.State mannequin) {
    }

    /** The local player's layer right after its tick, with the move's own tick. */
    private record TickRecord(long tick, long moveTick, PlayerAnimations.State state) {
    }

    private final Set<Section> sections;
    private final Minecraft mc = Minecraft.getInstance();
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();
    private final Map<Long, Frame> firstFrames = new ConcurrentHashMap<>();
    private final List<TickRecord> ticks = new CopyOnWriteArrayList<>();
    private boolean recordingTicks;
    private @Nullable Mannequin mannequin;
    private @Nullable DevCamera camera;
    private Vec3 stand = Vec3.ZERO;
    private int originalFov = 70;
    /** Client ticks, counted at the start of each (after the scenario's own steps). */
    private long clock;
    private long lastFrameTick = -1;
    private long moveStartedAt = -1;
    private long firstActiveAt = -1;
    private @Nullable ResourceLocation startedMove;

    public AnimScenario(Section... sections) {
        this.sections = sections.length == 0 ? EnumSet.of(Section.POSES, ALL) : EnumSet.of(sections[0], sections);
    }

    @Override
    public void steps(Steps steps) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> clock++);
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, event -> {
            if (mc.player != null && clock != lastFrameTick) {
                lastFrameTick = clock;
                firstFrames.put(clock, new Frame(clock, event.getPartialTick().getGameTimeDeltaPartialTick(true),
                        PlayerAnimations.state(mc.player), mannequin == null ? null : PlayerAnimations.state(mannequin.body())));
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (recordingTicks && event.getEntity() == mc.player) {
                ticks.add(new TickRecord(clock, clock - moveStartedAt, PlayerAnimations.state(mc.player)));
            }
        });
        ClientCombat.addEventListener(event -> {
            events.add(event);
            if (event instanceof CombatEvent.MoveStarted started) {
                moveStartedAt = clock;
                startedMove = started.move().id();
                firstActiveAt = started.move().def().timing().startup() == 0 ? clock : -1;
            } else if (event instanceof CombatEvent.ActiveTick active && active.activeTick() == 0 && firstActiveAt < 0) {
                firstActiveAt = clock;
            }
        });

        setUp(steps);
        if (sections.contains(Section.POSES)) {
            poses(steps);
        }
        if (sections.contains(Section.MOVES)) {
            moves(steps, "first_person");
            moves(steps, "side");
        }
        if (sections.contains(Section.WALKING)) {
            walking(steps);
        }
        if (sections.contains(Section.HIT_STOP)) {
            hitStop(steps);
        }
        if (sections.contains(Section.REMOTE)) {
            remote(steps);
        }
        if (sections.contains(Section.BLADE)) {
            blade(steps);
        }
        if (sections.contains(Section.FIRST_PERSON_OPTIONS)) {
            firstPersonOptions(steps);
        }
        tearDown(steps);
    }

    // ------------------------------------------------------------------ set-up

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
                        () -> machine().weapon() != null && machine().phase() == Phase.IDLE)
                .run("hide the HUD and chat", () -> {
                    mc.options.hideGui = true;
                    clearChat();
                    originalFov = mc.options.fov().get();
                })
                .run("a mannequin 10 blocks east, facing south, and a camera", () -> {
                    stand = mc.player.position().add(10, 0, 0);
                    mannequin = Mannequin.spawn(mc.level, stand, 0f, mc.player.getMainHandItem().copy());
                    camera = DevCamera.create(mc.level);
                })
                .waitTicks(5)
                .check("the mannequin has its combat layer, idle", () -> PlayerAnimations.state(mannequin.body()).animation() == null
                        && !PlayerAnimations.state(mannequin.body()).layerActive());
    }

    // ------------------------------------------------------------------ poses

    /** Every animation held at exact ticks on the mannequin: side strips, front and back key poses. */
    private void poses(Steps steps) {
        steps.run("narrow field of view for the pose shots", () -> mc.options.fov().set(30));
        for (String view : new String[] {"side", "front", "back"}) {
            steps.run("camera " + view, () -> {
                placeCameraOn(stand, view);
                camera.use();
            }).waitTicks(12); // the camera eases its eye height over from the player's
            for (Pose pose : POSES) {
                int[] shots = view.equals("side") ? pose.strip() : pose.keys();
                for (int tick : shots) {
                    String label = String.format(Locale.ROOT, "pose_%s_%s_t%02d", pose.label(), view, tick);
                    steps.run("pose " + label, () -> PlayerAnimations.showPose(mannequin.body(), pose.animation(), pose.mirrored(), tick))
                            .screenshot(label);
                }
            }
            steps.run("the mannequin stands", () -> PlayerAnimations.stop(mannequin.body()));
        }
        steps.run("the player's eyes and field of view again", () -> {
            mc.setCameraEntity(mc.player);
            mc.options.fov().set(originalFov);
        }).waitTicks(12);
    }

    // ------------------------------------------------------------------ every move through real input

    /**
     * Every move through the real input path in {@code view}: first person (with the HUD, since F1 also
     * hides vanilla's hand) or from the side (a camera beside the player, placed afresh for each move).
     */
    private void moves(Steps steps, String view) {
        boolean side = view.equals("side");
        KeyMapping attack = mc.options.keyAttack;
        KeyMapping use = mc.options.keyUse;
        KeyMapping forward = mc.options.keyUp;
        KeyMapping left = mc.options.keyLeft;
        steps.run("view: " + view, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = side;
            mc.options.fov().set(side ? 40 : originalFov);
            mc.setCameraEntity(mc.player);
        });

        // The light chain: L1, L2, L3, each pressed as the one before ends.
        ready(steps, view, 10f, true);
        String[] chain = {"l1", "l2", "l3"};
        // L3 runs on past its end: its blend back to vanilla, and in first person vanilla's sword rising back.
        int[] length = {10, 11, 22};
        int[][] shots = {{0, 1, 2, 3, 4, 5, 7, 10}, {2, 3, 4, 5, 7}, {2, 3, 4, 5, 6, 8, 12, 16, 17, 18, 19, 20, 22}};
        for (int m = 0; m < chain.length; m++) {
            String move = chain[m];
            steps.waitUntil("idle, the chain waiting for " + move, 40, () -> machine().phase() == Phase.IDLE);
            aim(steps, view);
            steps.hold(attack);
            capture(steps, view, move, length[m], shots[m], true, () -> steps.release(attack));
        }

        // Hold into the charge (vanilla's hand in first person), release into Meridian Line.
        ready(steps, view, 10f, true);
        aim(steps, view);
        steps.hold(attack)
                .waitUntil("charging", 30, () -> machine().phase() == Phase.CHARGING)
                .waitTicks(6)
                .screenshot(view + "_charge_hold")
                .waitUntil("charged to full", 40, () -> machine().attackHeldTicks() >= 24)
                .screenshot(view + "_charge_full");
        aim(steps, view);
        steps.release(attack);
        capture(steps, view, "line", 19, new int[] {0, 1, 2, 3, 4, 5, 6, 9, 14}, true, () -> {});
        if (!side) {
            // Let go before the charge's minimum: the charge is cancelled and its hold stops.
            ready(steps, view, 10f, true);
            steps.hold(attack)
                    .waitUntil("charging", 30, () -> machine().phase() == Phase.CHARGING)
                    .check("the charge hold plays", () -> localPlaying("meridian_charge"))
                    .release(attack)
                    .waitUntil("the charge was cancelled", 5, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.ChargeCancelled))
                    .check("the hold stopped with it", () -> PlayerAnimations.state(mc.player).animation() == null)
                    .waitTicks(4)
                    .check("and blended out", () -> !PlayerAnimations.state(mc.player).layerActive());
        }

        // A forward dash, then Pass out of its last ticks.
        ready(steps, view, 10f, false);
        aim(steps, view);
        steps.hold(forward).hold(ModKeyMappings.DASH);
        capture(steps, view, "dash_forward", 3, new int[] {1, 2, 3}, false, () -> steps.release(ModKeyMappings.DASH).release(forward));
        steps.hold(attack);
        capture(steps, view, "pass", 12, new int[] {0, 1, 2, 3, 4, 6, 9}, true, () -> steps.release(attack));

        // Zenith: Resonance 100 and a held right click; the player rises.
        resonance(steps, 100);
        ready(steps, view, 10f, false);
        steps.waitUntil("the ability is ready", 200, () -> machine().abilityCooldown() == 0);
        aim(steps, view);
        steps.hold(use);
        capture(steps, view, "zenith", 19, new int[] {0, 3, 5, 6, 7, 8, 10, 15}, true, () -> {});
        steps.release(use).waitUntil("back on the ground", 80, () -> mc.player.onGround() && machine().phase() == Phase.IDLE);

        // Falling Star from 6 blocks up, looking down: the dive, then its landing.
        ready(steps, view, 10f, false);
        steps.command("tp @s ~ ~6 ~")
                .waitUntil("in the air", 20, () -> !mc.player.onGround())
                .look(0, 70)
                .waitTicks(2); // the server learns the pitch before the press uses it
        if (side) {
            steps.run("a side camera framing the fall and the landing", () -> {
                placeCameraOn(mc.player.position().add(0, -6, 0), "plunge_side");
                camera.use();
            });
        }
        steps.hold(attack);
        capture(steps, view, "falling_star", 3, new int[] {1, 2, 3}, false, () -> steps.release(attack));
        // The landing is seen the tick after it played: this capture starts on its tick 1.
        steps.waitUntil("the plunge landed", 60, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.PlungeLanded));
        capture(steps, view, "falling_star_land", 1, 9, new int[] {1, 2, 5, 8}, false, () -> {});
        steps.waitUntil("idle on the ground", 40, () -> mc.player.onGround() && machine().phase() == Phase.IDLE).look(0, 10);

        // The other dashes: back, and left (the side dash mirrored).
        ready(steps, view, 10f, false);
        aim(steps, view);
        steps.hold(ModKeyMappings.DASH);
        capture(steps, view, "dash_back", 8, new int[] {1, 2, 5}, false, () -> steps.release(ModKeyMappings.DASH));
        ready(steps, view, 10f, false);
        aim(steps, view);
        steps.hold(left).hold(ModKeyMappings.DASH);
        capture(steps, view, "dash_left", 8, new int[] {1, 2, 5}, false, () -> steps.release(ModKeyMappings.DASH).release(left));

        // A parry into nothing (the whiff), then one that catches a zombie's hit.
        ready(steps, view, 10f, false);
        aim(steps, view);
        steps.hold(ModKeyMappings.PARRY);
        capture(steps, view, "parry", 14, new int[] {1, 2, 4, 8, 12}, false, () -> steps.release(ModKeyMappings.PARRY));
        ready(steps, view, 10f, false);
        freshZombie(steps, 1.8);
        steps.waitUntil("parry ready", 40, () -> !machine().inParryWhiff() && !machine().isParrying())
                .run("clear the events", events::clear);
        aim(steps, view);
        steps.press(ModKeyMappings.PARRY)
                .waitUntil("the zombie struck into the server's parry", 10, () -> ServerQuery.ask(player -> {
                    if (!PlayerCombat.of(player).machine().isParrying()) {
                        return false;
                    }
                    zombieStrikes(player);
                    return true;
                }))
                .waitUntil("the parry reached this client", 10, () -> events.stream().anyMatch(e -> e instanceof CombatEvent.ParrySucceeded));
        capture(steps, view, "parry_success", 6, new int[] {0, 1, 2, 4}, false, () -> {});
        removeTestEntities(steps);

        // A stagger, as the server announces one.
        ready(steps, view, 10f, false);
        aim(steps, view);
        steps.run("the server says the player staggered", () -> ClientCombat.onCombatFx(
                new CombatFxPayload(mc.player.getId(), CombatFxPayload.Kind.STAGGER)));
        capture(steps, view, "stagger", 10, new int[] {1, 2, 4, 7}, false, () -> {});

        steps.run("the player's eyes again", () -> {
            mc.setCameraEntity(mc.player);
            mc.options.hideGui = true;
            mc.options.fov().set(originalFov);
        }).look(0, 0).waitTicks(5);
    }

    /**
     * One tick per step from the tick the move starts in (the steps before this ran in it): a shot at
     * each tick in {@code shots}, a wait at the others; {@code afterFirst} adds the steps that let
     * keys go, after the first tick. With {@code timed}, the shot on the first active tick must show
     * the animation at the move's own tick.
     */
    private void capture(Steps steps, String view, String name, int last, int[] shots, boolean timed, Runnable afterFirst) {
        capture(steps, view, name, 0, last, shots, timed, afterFirst);
    }

    /** As above, the first captured tick being the animation's tick {@code first} (the labels count from there). */
    private void capture(Steps steps, String view, String name, int first, int last, int[] shots, boolean timed, Runnable afterFirst) {
        for (int t = first; t <= last; t++) {
            int tick = t;
            if (Arrays.stream(shots).anyMatch(s -> s == tick)) {
                String label = String.format(Locale.ROOT, "%s_%s_t%02d", view, name, t);
                steps.screenshot(label);
                if (timed) {
                    // Runs at the start of the next tick, before this scenario's tick count moves on.
                    steps.log(label, () -> describeFrame(label, clock));
                }
            } else {
                steps.waitTicks(1);
            }
            if (t == first) {
                afterFirst.run();
            }
        }
        if (timed) {
            steps.log("the first active tick of " + name, () -> describeFrame(name + " first active", firstActiveAt))
                    .check(name + ": on its first active tick the frame showed the animation at the move's tick",
                            () -> frameMatchesMove(name));
        }
    }

    /** The move began, got to its first active tick, and the first frame after that tick showed its animation there. */
    private boolean frameMatchesMove(String name) {
        Frame frame = firstFrames.get(firstActiveAt);
        if (startedMove == null || !startedMove.getPath().endsWith(name) || firstActiveAt < 0 || frame == null
                || frame.local().animation() == null) {
            return false;
        }
        long moveTick = frame.tick() - moveStartedAt;
        return Math.abs(frame.local().time() - (moveTick + frame.partial())) < 0.05f;
    }

    private String describeFrame(String what, long tick) {
        Frame frame = firstFrames.get(tick);
        if (frame == null) {
            return what + ": no frame after tick " + tick;
        }
        return String.format(Locale.ROOT, "%s: frame after tick %d = move tick %d (first active at %d), partial %.3f, shows %s",
                what, frame.tick(), frame.tick() - moveStartedAt, firstActiveAt < 0 ? -1 : firstActiveAt - moveStartedAt,
                frame.partial(), frame.local());
    }

    /**
     * Idle, facing south {@code pitch} down, the chain reset if asked, a dash charge in hand. Looks
     * through the player meanwhile: through another camera the client sends none of the player's
     * moves, and the server has to know where the player went.
     */
    private void ready(Steps steps, String view, float pitch, boolean resetChain) {
        steps.run("look through the player", () -> mc.setCameraEntity(mc.player))
                .waitUntil("idle with a dash charge", 100, () -> machine().phase() == Phase.IDLE && !machine().isDashing()
                        && !machine().isParrying() && !machine().inParryWhiff() && machine().dashCharges() >= 1 && mc.player.onGround())
                .look(0, pitch)
                .waitTicks(resetChain ? 12 : 3)
                .run("clear the chat and events", () -> {
                    clearChat();
                    events.clear();
                });
    }

    /** From the side: a camera 9 blocks to the player's right, where it stands now. */
    private void aim(Steps steps, String view) {
        if (view.equals("side")) {
            steps.run("side camera on the player", () -> {
                placeCameraOn(mc.player.position(), "wide_side");
                camera.use();
            });
        }
    }

    private void resonance(Steps steps, int value) {
        steps.run("mark the fight on this client", () -> machine().onDamaged())
                .waitUntil("and on the server", 5, () -> ServerQuery.ask(player -> {
                    PlayerCombat.of(player).machine().onDamaged();
                    return true;
                }))
                .command("cosmicbreach debug resonance " + value)
                .waitUntil("the client's Resonance is " + value, 40, () -> Math.abs(machine().resonance() - value) < 0.5)
                .run("clear the chat", this::clearChat);
    }

    // ------------------------------------------------------------------ walking

    /**
     * Walking while attacking: the mannequin walks (0.2 blocks a tick, as a remote player moves) into
     * L1 (legs must walk) and stands for it (legs must be the animation's); it dashes (legs planted);
     * the local player walks into L1 through the keys.
     */
    private void walking(Steps steps) {
        ResourceLocation l1 = CosmicBreach.id("meridian/l1");
        float[] standingWeight = {-1f};
        steps.run("side view of the mannequin, wide", () -> {
            mc.options.fov().set(40);
            mannequin.moveTo(stand);
            placeCameraOn(stand.add(0, 0, 1.5), "wide_side");
            camera.use();
        }).waitTicks(12);
        // Standing: the swing's legs.
        steps.run("the mannequin swings standing", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(mannequin.body().getId(), l1)))
                .waitTicks(4)
                .screenshot("walk_standing_l1_side")
                .run("note the standing leg weight", () -> standingWeight[0] = PlayerAnimations.state(mannequin.body()).legWeight())
                .check("standing, the legs are the animation's", () -> standingWeight[0] == 0f)
                .waitTicks(12);
        // Walking forward (south): the swing's arms, vanilla's walking legs.
        steps.run("the mannequin walks south", () -> mannequin.walk(new Vec3(0, 0, 0.2)))
                .waitTicks(10)
                .run("and swings while walking", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(mannequin.body().getId(), l1)))
                .waitTicks(2)
                .screenshot("walk_l1_t3_side")
                .screenshot("walk_l1_t4_side")
                .log("walking legs", () -> "walking mannequin: " + PlayerAnimations.state(mannequin.body()))
                .check("walking, the legs walk (weight above 0.9)", () -> PlayerAnimations.state(mannequin.body()).legWeight() > 0.9f)
                .waitTicks(2)
                .screenshot("walk_l1_t7_side")
                .run("stop", () -> mannequin.stand())
                .waitTicks(15)
                .check("stopped, the legs are back to 0", () -> PlayerAnimations.state(mannequin.body()).legWeight() == 0f);
        // Walking sideways (west, its right): the legs turn toward the movement.
        steps.run("front view", () -> {
            mannequin.moveTo(stand);
            placeCameraOn(stand, "wide_front");
        }).waitTicks(2)
                .run("the mannequin walks to its right", () -> mannequin.walk(new Vec3(-0.2, 0, 0)))
                .waitTicks(8)
                .run("and swings", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(mannequin.body().getId(), l1)))
                .waitTicks(3)
                .screenshot("walk_sideways_l1_front")
                .run("stop", () -> mannequin.stand())
                .waitTicks(15);
        // Dashing: fast, but the dash's own legs.
        steps.run("side view again", () -> {
            mannequin.moveTo(stand);
            placeCameraOn(stand.add(0, 0, 1.5), "wide_side");
        }).waitTicks(2)
                .run("the mannequin dashes forward (0.62 a tick)", () -> {
                    mannequin.walk(new Vec3(0, 0, 0.62));
                    ClientCombat.onCombatFx(new CombatFxPayload(mannequin.body().getId(), CombatFxPayload.Kind.DASH));
                })
                .waitTicks(3)
                .screenshot("walk_dash_side")
                .check("dashing, the legs stay the dash's", () -> PlayerAnimations.state(mannequin.body()).legWeight() == 0f)
                .run("stop", () -> mannequin.stand())
                .waitTicks(15)
                .run("the mannequin back in its place", () -> mannequin.moveTo(stand));

        // The local player, seen from the front, walks forward and swings.
        KeyMapping forward = mc.options.keyUp;
        steps.run("the player seen from the front", () -> {
            mc.setCameraEntity(mc.player);
            mc.options.fov().set(originalFov);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        ready(steps, "front", 0f, true);
        steps.hold(forward)
                .waitTicks(10)
                .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                .waitTicks(2)
                .screenshot("walk_local_l1_front")
                .log("the local player's legs", () -> "walking local player: " + PlayerAnimations.state(mc.player))
                .check("the local player walks with vanilla's legs", () -> PlayerAnimations.state(mc.player).legWeight() > 0.9f)
                .waitTicks(2)
                .screenshot("walk_local_l1_front_late")
                .release(forward)
                .waitUntil("idle", 40, () -> machine().phase() == Phase.IDLE)
                .run("first person again", () -> mc.options.setCameraType(CameraType.FIRST_PERSON))
                .look(0, 0)
                .waitTicks(10);
    }

    // ------------------------------------------------------------------ hit-stop

    /** L1 on a zombie: the animation holds for the hit's ticks, catches up and ends with the move. */
    private void hitStop(Steps steps) {
        freshZombie(steps, 2.5);
        ready(steps, "first_person", 10f, true);
        steps.run("record every tick", () -> {
            ticks.clear();
            recordingTicks = true;
        })
                .hold(mc.options.keyAttack).waitTicks(1).release(mc.options.keyAttack)
                .waitTicks(14)
                .run("stop recording", () -> recordingTicks = false)
                .log("the ticks", this::describeTicks)
                .check("the hit held the animation still for 2 ticks, then it caught up in step with the move", this::hitStopHeldAndCaughtUp);
        removeTestEntities(steps);
    }

    private String describeTicks() {
        StringBuilder out = new StringBuilder("L1 into a zombie, the local player's layer after each tick:");
        for (TickRecord record : ticks) {
            out.append(String.format(Locale.ROOT, "%n  move tick %2d: %s", record.moveTick(), record.state()));
        }
        return out.toString();
    }

    private boolean hitStopHeldAndCaughtUp() {
        List<TickRecord> l1 = ticks.stream().filter(r -> r.state().animation() != null && r.state().animation().getPath().equals("meridian_l1")).toList();
        int held = 0;
        for (int i = 1; i < l1.size(); i++) {
            if (Math.abs(l1.get(i).state().time() - l1.get(i - 1).state().time()) < 0.01f) {
                held++;
            }
        }
        boolean heldSome = l1.stream().anyMatch(r -> r.state().holding()) && held >= 1;
        // By the move's last tick (9) the animation is back in step: time equals the move's tick.
        boolean inStep = l1.stream().anyMatch(r -> r.moveTick() >= 7 && r.moveTick() <= 12 && r.state().lag() == 0
                && Math.abs(r.state().time() - r.moveTick()) < 0.02f);
        if (!heldSome || !inStep) {
            throw new Steps.Failure("hit-stop: held ticks " + held + ", in step again " + inStep + "; " + describeTicks());
        }
        return true;
    }

    // ------------------------------------------------------------------ remote players

    /** The mannequin, driven by the server's payloads through the combat runtime's handlers. */
    private void remote(Steps steps) {
        int[] id = {0};
        steps.run("side view of the mannequin", () -> {
            mc.options.fov().set(30);
            mannequin.moveTo(stand);
            id[0] = mannequin.body().getId();
            placeCameraOn(stand, "side");
            camera.use();
        }).waitTicks(12);
        remoteMove(steps, id, "meridian/l2", "meridian_l2", 3);
        remoteMove(steps, id, "meridian/pass", "meridian_pass", 2);
        // A charge: it holds, stops when let go early, and gives way to the Line when released.
        steps.run("charge starts", () -> ClientCombat.onCombatFx(new CombatFxPayload(id[0], CombatFxPayload.Kind.CHARGE_START)))
                .waitTicks(8)
                .screenshot("remote_charge")
                .check("the charge hold plays", () -> playing("meridian_charge"))
                .run("charge let go early", () -> ClientCombat.onCombatFx(new CombatFxPayload(id[0], CombatFxPayload.Kind.CHARGE_STOP)))
                .check("it stops at once", () -> PlayerAnimations.state(mannequin.body()).animation() == null)
                .waitTicks(4)
                .check("and has blended out", () -> !PlayerAnimations.state(mannequin.body()).layerActive())
                .run("charge again", () -> ClientCombat.onCombatFx(new CombatFxPayload(id[0], CombatFxPayload.Kind.CHARGE_START)))
                .waitTicks(10)
                .run("released into Meridian Line", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(id[0], CosmicBreach.id("meridian/line"))))
                .check("the Line replaced the charge", () -> playing("meridian_line"))
                .waitTicks(4)
                .screenshot("remote_line_t4")
                .waitTicks(25);
        // The dive loops until its landing plays.
        steps.run("a plunge starts", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(id[0], CosmicBreach.id("meridian/falling_star"))))
                .waitTicks(30)
                .check("the dive still loops after 30 ticks", () -> playing("meridian_falling_star"))
                .screenshot("remote_dive")
                .run("the plunge lands", () -> ClientCombat.onCombatFx(new CombatFxPayload(id[0], CombatFxPayload.Kind.PLUNGE_LANDED, Vec3.ZERO, 6f)))
                .check("the landing replaced the dive", () -> playing("meridian_falling_star_land"))
                .waitTicks(1)
                .screenshot("remote_land_t1")
                .waitTicks(16)
                .check("and it ended", () -> !PlayerAnimations.state(mannequin.body()).layerActive());
        // Dash, parry, parry success, stagger.
        remoteFx(steps, id, CombatFxPayload.Kind.DASH, "combat_dash_forward");
        remoteFx(steps, id, CombatFxPayload.Kind.PARRY_START, "combat_parry");
        remoteFx(steps, id, CombatFxPayload.Kind.PARRY, "combat_parry_success");
        remoteFx(steps, id, CombatFxPayload.Kind.STAGGER, "combat_stagger");
        // Hit-stop on a remote attacker.
        steps.run("the mannequin swings", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(id[0], CosmicBreach.id("meridian/l1"))))
                .waitTicks(3)
                .run("and its hit lands", () -> ClientCombat.onHitFx(new HitFxPayload(id[0], mc.player.getId(),
                        mannequin.body().position().add(0, 1, 1), new Vec3(0, 0, 1), 6f, false, 3)))
                .check("its animation holds", () -> PlayerAnimations.state(mannequin.body()).holding())
                .waitTicks(20)
                .check("and ends", () -> !PlayerAnimations.state(mannequin.body()).layerActive())
                .run("the player's eyes again", () -> {
                    mc.setCameraEntity(mc.player);
                    mc.options.fov().set(originalFov);
                });
    }

    private void remoteMove(Steps steps, int[] id, String move, String animation, int shotTick) {
        steps.run("the server: " + move + " started", () -> ClientCombat.onMoveStarted(new MoveStartedPayload(id[0], CosmicBreach.id(move))))
                .check(animation + " plays", () -> playing(animation))
                .waitTicks(shotTick) // started before the entity ticks: its tick 0 shows after this tick
                .screenshot("remote_" + animation)
                .waitTicks(20)
                .check(animation + " ended", () -> !PlayerAnimations.state(mannequin.body()).layerActive());
    }

    private void remoteFx(Steps steps, int[] id, CombatFxPayload.Kind kind, String animation) {
        steps.run("the server: " + kind, () -> ClientCombat.onCombatFx(new CombatFxPayload(id[0], kind)))
                .check(animation + " plays", () -> playing(animation))
                .waitTicks(2)
                .screenshot("remote_" + animation)
                .waitTicks(20)
                .check(animation + " ended", () -> !PlayerAnimations.state(mannequin.body()).layerActive());
    }

    private boolean localPlaying(String animation) {
        ResourceLocation now = PlayerAnimations.state(mc.player).animation();
        return now != null && now.getPath().equals(animation);
    }

    private boolean playing(String animation) {
        ResourceLocation now = PlayerAnimations.state(mannequin.body()).animation();
        return now != null && now.getPath().equals(animation);
    }

    // ------------------------------------------------------------------ where the blade is

    /** A held pose and where tools/anim/blade.py puts its guard and tip: (right, up, forward) blocks. */
    private record BladeCheck(String animation, int tick, double[] guard, double[] tip, double[] guardFirstPerson,
                              double[] tipFirstPerson) {
    }

    /** From {@code python tools/anim/blade.py l1 charge --first-person --pitch 10}. */
    private static final List<BladeCheck> BLADE_CHECKS = List.of(
            new BladeCheck("meridian_l1", 0, new double[] {-0.009, 1.175, 0.451}, new double[] {-0.11, 1.627, 0.968},
                    new double[] {-0.009, -0.015, 0.927}, new double[] {-0.11, 0.651, 1.093}),
            new BladeCheck("meridian_l1", 3, new double[] {0.126, 1.065, 0.532}, new double[] {0.391, 1.43, 1.059},
                    new double[] {0.126, -0.059, 1.057}, new double[] {0.391, 0.543, 1.28}),
            new BladeCheck("meridian_charge", 0, new double[] {0.29, 0.858, 0.228}, new double[] {0.643, 0.373, -0.122},
                    null, null));

    /** Markers on held poses where the offline rig says the blade is: from the side, then in first person. */
    private void blade(Steps steps) {
        steps.run("the mannequin from the side, narrow", () -> {
            mc.options.fov().set(30);
            mannequin.moveTo(stand);
            placeCameraOn(stand, "side");
            camera.use();
        }).waitTicks(12);
        for (BladeCheck check : BLADE_CHECKS) {
            String label = "blade_" + check.animation() + "_t" + check.tick();
            steps.run("pose " + label, () -> PlayerAnimations.showPose(mannequin.body(), CosmicBreach.id(check.animation()), false, check.tick()))
                    .waitTicks(1)
                    .run("markers on " + label, () -> {
                        marker(bodyPoint(mannequin.body(), check.guard()), 0.2f, 0.4f, 1f);
                        marker(bodyPoint(mannequin.body(), check.tip()), 1f, 0.15f, 0.1f);
                    })
                    .screenshot(label + "_side")
                    .waitTicks(10);
        }
        steps.run("the mannequin stands", () -> PlayerAnimations.stop(mannequin.body()));
        steps.run("first person", () -> {
            mc.setCameraEntity(mc.player);
            mc.options.fov().set(originalFov);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
        });
        ready(steps, "first_person", 10f, false);
        for (BladeCheck check : BLADE_CHECKS) {
            if (check.guardFirstPerson() == null) {
                continue;
            }
            String label = "blade_" + check.animation() + "_t" + check.tick() + "_first_person";
            steps.run("pose " + label, () -> PlayerAnimations.showPose(mc.player, CosmicBreach.id(check.animation()), false, check.tick()))
                    .waitTicks(4) // the first-person framing settles
                    .run("markers on " + label, () -> {
                        marker(eyePoint(check.guardFirstPerson()), 0.2f, 0.4f, 1f);
                        marker(eyePoint(check.tipFirstPerson()), 1f, 0.15f, 0.1f);
                    })
                    .screenshot(label)
                    .run("stop", () -> PlayerAnimations.stop(mc.player))
                    .waitTicks(10);
        }
        steps.look(0, 0);
    }

    /** A point given as (right, up, forward) blocks from a player's feet, in its body's yaw frame, in the world. */
    private static Vec3 bodyPoint(AbstractClientPlayer player, double[] p) {
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return player.position().add(right.scale(p[0])).add(0, p[1], 0).add(forward.scale(p[2]));
    }

    /** A point given as (right, up, forward) blocks from the camera, in the camera's frame, in the world. */
    private Vec3 eyePoint(double[] p) {
        var camera = mc.gameRenderer.getMainCamera();
        org.joml.Vector3f left = camera.getLeftVector();
        org.joml.Vector3f up = camera.getUpVector();
        org.joml.Vector3f look = camera.getLookVector();
        return camera.getPosition().add(-left.x() * p[0] + up.x() * p[1] + look.x() * p[2],
                -left.y() * p[0] + up.y() * p[1] + look.y() * p[2],
                -left.z() * p[0] + up.z() * p[1] + look.z() * p[2]);
    }

    private void marker(Vec3 at, float r, float g, float b) {
        mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(r, g, b), 1.2f),
                at.x, at.y, at.z, 0, 0, 0);
    }

    // ------------------------------------------------------------------ first-person options (not in anim)

    /** A first-person option to compare: what is drawn, and how the model sits in front of the eye. */
    private record FirstPersonOption(String tag, FirstPersonMode mode, boolean arms, float push, float drop, float pitch) {
    }

    private static final List<FirstPersonOption> FIRST_PERSON_OPTIONS = List.of(
            new FirstPersonOption("arms15", FirstPersonMode.THIRD_PERSON_MODEL, true, 0.3f, 0.1f, 15f),
            new FirstPersonOption("blade15", FirstPersonMode.THIRD_PERSON_MODEL, false, 0.3f, 0.1f, 15f),
            new FirstPersonOption("blade25", FirstPersonMode.THIRD_PERSON_MODEL, false, 0.3f, 0.1f, 25f),
            new FirstPersonOption("blade35", FirstPersonMode.THIRD_PERSON_MODEL, false, 0.3f, 0.1f, 35f),
            new FirstPersonOption("vanilla", FirstPersonMode.NONE, false, 0f, 0f, Float.NaN));

    /** The light chain in first person at chosen ticks, once per option, the HUD shown. */
    private void firstPersonOptions(Steps steps) {
        KeyMapping attack = mc.options.keyAttack;
        steps.run("first person with the HUD", () -> {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        });
        String[] chain = {"l1", "l2", "l3"};
        int[] length = {10, 11, 16};
        int[][] shots = {{1, 2, 3, 4, 5, 6, 9}, {2, 3, 4, 5, 7}, {2, 3, 4, 5, 6, 7, 10}};
        for (float pitch : new float[] {10f, -20f}) {
            for (FirstPersonOption option : FIRST_PERSON_OPTIONS) {
                String tag = "fp_" + option.tag() + "_p" + (pitch < 0 ? "m" + (int) -pitch : String.valueOf((int) pitch));
                steps.run("first person: " + tag, () -> {
                    PlayerAnimations.overrideFirstPerson(option.mode(), option.arms(), true);
                    PlayerAnimations.overrideFirstPersonView(option.push(), option.drop(), option.pitch());
                });
                ready(steps, "first_person", pitch, true);
                for (int m = 0; m < chain.length; m++) {
                    String move = chain[m];
                    steps.waitUntil("idle, the chain waiting", 40, () -> machine().phase() == Phase.IDLE).hold(attack);
                    capture(steps, tag, move, length[m], shots[m], false, () -> steps.release(attack));
                }
            }
        }
        steps.run("each animation's own first person again", () -> {
            PlayerAnimations.overrideFirstPerson(null, false, false);
            PlayerAnimations.overrideFirstPersonView(Float.NaN, Float.NaN, Float.NaN);
            mc.options.hideGui = true;
        }).look(0, 0);
    }

    // ------------------------------------------------------------------ tear-down and helpers

    private void tearDown(Steps steps) {
        steps.run("remove the mannequin and camera", () -> {
            mc.setCameraEntity(mc.player);
            if (mannequin != null) {
                mannequin.remove();
            }
            if (camera != null) {
                camera.remove();
            }
            mc.options.fov().set(originalFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        removeTestEntities(steps);
    }

    private CombatStateMachine machine() {
        return PlayerCombat.of(mc.player).machine();
    }

    private void clearChat() {
        mc.gui.getChat().clearMessages(false);
    }

    /**
     * Side (from the right), front, or back (above and behind) of a player standing at {@code feet}
     * facing south; the wide views stand further off, for moves that travel.
     */
    private void placeCameraOn(Vec3 feet, String view) {
        Vec3 chest = feet.add(0, 1.1, 0);
        switch (view) {
            case "side" -> camera.place(feet.add(-7, 1.1, 0.3), chest.add(0, 0, 0.3));
            case "front" -> camera.place(feet.add(0, 1.1, 7), chest);
            case "wide_side" -> camera.place(feet.add(-9, 1.2, 1.5), chest.add(0, 0, 1.5));
            case "wide_front" -> camera.place(feet.add(0, 1.2, 9), chest);
            case "plunge_side" -> camera.place(feet.add(-14, 4.0, 0.5), feet.add(0, 3.5, 0.5));
            default -> camera.place(feet.add(0, 2.6, -6), chest.add(0, 0, 0.5));
        }
    }

    /**
     * A fresh zombie {@code blocks} ahead, facing the player: AI on but no goals, so it stands still
     * yet can be hit and moved (FxScenario's recipe). The pumpkin keeps it from burning.
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
                .waitUntil("the zombie is here", 60, () -> !mc.level.getEntitiesOfClass(Zombie.class,
                        mc.player.getBoundingBox().inflate(blocks + 2.0)).isEmpty());
    }

    /** The test zombie hits the player the way its melee attack does (server thread). */
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

    /** Removes the test zombies and dropped items without a death (no smoke, no drops). */
    private void removeTestEntities(Steps steps) {
        steps.waitUntil("the test entities are gone", 5, () -> ServerQuery.ask(player -> {
            for (Entity entity : player.serverLevel().getEntities((Entity) null, player.getBoundingBox().inflate(64),
                    e -> e.getTags().contains(MOB_TAG) || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
                entity.discard();
            }
            return true;
        })).waitTicks(2);
    }

    private static Pose pose(String path, int[] strip, int... keys) {
        return new Pose(CosmicBreach.id(path), false, strip, keys);
    }

    private static int[] range(int from, int to) {
        int[] out = new int[to - from + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }
}
