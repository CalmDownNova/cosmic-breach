package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.combat.TwinBlades;
import com.cosmicbreach.client.entity.ThrownSickleRenderer;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.effect.EdgesEffects;
import com.cosmicbreach.combat.server.effect.TetherBlades;
import com.cosmicbreach.combat.server.effect.TetherMath;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Binary Edges' visuals (GDD 4.2): two thin trails, cyan off the right sickle and violet off the left
 * (the move's slash, see {@link SlashTrail} and {@code TwinBlades}); afterimages on their dashes; the thrown
 * blade spinning ({@link ThrownSickleRenderer}) with a violet trail and a chain of light back to the hand; the
 * blink as a streak with an afterimage left at the start and a flash where it arrives; the Binary Orbit's two
 * blades flung round the body on their chains; the Mark over an enemy; the Twin Meteor's landing rings. No
 * smoke. Also the client half of the Tether's rules: which players have a blade out (so their left hand is
 * empty and their combat plays one-bladed), and the local player's blink opening and closing when the server
 * says the blade stuck or came back.
 */
public final class EdgesVisuals {
    public static final ResourceLocation ORBIT = CosmicBreach.id("binary_orbit");
    public static final ResourceLocation METEOR = CosmicBreach.id("twin_meteor");
    public static final int CYAN = 0x5CE6FF;
    public static final int VIOLET = 0xB47CFF;
    public static final int PALE_CYAN = 0xCBF8FF;
    public static final int PALE_VIOLET = 0xE8D6FF;
    static final ResourceLocation ORBIT_ANIMATION = CosmicBreach.id("edges_orbit");
    /** The Orbit's blades leave the hands at this animation tick and are back by this one. */
    static final double ORBIT_RELEASE = 1.6;
    static final double ORBIT_CATCH = 14.5;
    /** After the throw's first active tick the left hand stays empty this long even before the blade shows up. */
    private static final int THROW_GRACE = 8;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Each thrower's blade, by the thrower's entity id. */
    private static final Map<Integer, ThrownSickle> BLADES = new HashMap<>();
    /** When each thrower's blade leaves its hand (FxClock ticks), for the ticks before the entity arrives. */
    private static final Map<Integer, Long> THROWN_AT = new HashMap<>();
    private static ItemStack leftStack = ItemStack.EMPTY;
    private static ItemStack rightStack = ItemStack.EMPTY;

    private EdgesVisuals() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(ModEntities.THROWN_SICKLE.get(), ThrownSickleRenderer::new));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> ItemProperties.register(
                ModItems.BINARY_EDGES_LEFT.get(), WeaponGlow.PROPERTY, (ClampedItemPropertyFunction) EdgesVisuals::leftResonance)));
        gameBus.addListener(EntityJoinLevelEvent.class, EdgesVisuals::onJoin);
        gameBus.addListener(EntityLeaveLevelEvent.class, EdgesVisuals::onLeave);
        gameBus.addListener(RenderLevelStageEvent.class, Afterimages::onRenderStage);
        gameBus.addListener(RenderLevelStageEvent.class, EdgesVisuals::renderOrbitBlades);
        gameBus.addListener(RenderNameTagEvent.class, Afterimages::onNameTag);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            BLADES.clear();
            THROWN_AT.clear();
            ORBITS.clear();
        });
        TwinBlades.addAwayRule(EdgesVisuals::bladeAway);
        TwinBlades.addReleasedRule(EdgesVisuals::bladesReleased);
        CombatEffects.addDashListener(EdgesVisuals::dashed);

        ClientMoveEffects.register(TetherBlades.ID, new ClientMoveEffect() {
            @Override
            public void started(Player player, MoveDef def, MoveEffect effect) {
                THROWN_AT.put(player.getId(), FxClock.ticks() + def.timing().startup());
            }

            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload moment) {
                tetherMoment(player, moment);
            }
        });
        ClientMoveEffects.register(EdgesEffects.BLINK, new ClientMoveEffect() {
            @Override
            public void started(Player player, MoveDef def, MoveEffect effect) {
                blinkStarted(player, def, effect);
            }

            @Override
            public void active(Player player, MoveDef def, MoveEffect effect) {
                blinkArrived(player);
            }
        });
        ClientMoveEffects.register(ORBIT, new ClientMoveEffect() {
            @Override
            public void started(Player player, MoveDef def, MoveEffect effect) {
                WorldFx.add(new OrbitEffect(player, def, effect));
            }
        });
        ClientMoveEffects.register(METEOR, new ClientMoveEffect() {
            @Override
            public boolean landed(Player player, MoveDef def, MoveEffect effect, double fallBlocks) {
                meteorLanding(player, effect, fallBlocks);
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ who has a blade out

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof ThrownSickle blade) {
            Player owner = blade.ownerPlayer();
            if (owner != null) {
                BLADES.put(owner.getId(), blade);
                WorldFx.add(new TetherChain(blade, owner));
            }
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof ThrownSickle blade) {
            BLADES.values().removeIf(b -> b == blade);
        }
    }

    /** The thrower's blade, if one is out (or on its way home) in this client's level. */
    public static @Nullable ThrownSickle bladeOf(Player player) {
        ThrownSickle blade = BLADES.get(player.getId());
        return blade != null && !blade.isRemoved() ? blade : null;
    }

    /** The left blade is away from the hand: thrown and not yet back, or just leaving it. */
    static boolean bladeAway(Player player) {
        if (bladeOf(player) != null) {
            return true;
        }
        Long thrown = THROWN_AT.get(player.getId());
        long now = FxClock.ticks();
        return thrown != null && now >= thrown && now < thrown + THROW_GRACE;
    }

    /** Both blades are flung round the body: the Binary Orbit's animation between release and catch. */
    static boolean bladesReleased(Player player) {
        if (!(player instanceof AbstractClientPlayer client)) {
            return false;
        }
        PlayerAnimations.State state = PlayerAnimations.state(client);
        return ORBIT_ANIMATION.equals(state.animation()) && state.time() >= ORBIT_RELEASE && state.time() <= ORBIT_CATCH;
    }

    /** The left sickle's glow: the local player's Resonance while it holds the Edges, as the right one's. */
    static float leftResonance(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity != player || TwinBlades.offHandOf(player) == null) {
            return 0f;
        }
        PlayerCombat combat = PlayerCombat.existing(player);
        return combat == null ? 0f : WeaponGlow.fraction(combat.machine());
    }

    // ------------------------------------------------------------------ the Tether

    private static void tetherMoment(@Nullable Player player, MoveEffectPayload moment) {
        Minecraft mc = Minecraft.getInstance();
        boolean local = player != null && player == mc.player;
        switch (moment.stage()) {
            case TetherBlades.STUCK -> {
                if (local) {
                    CombatStateMachine machine = PlayerCombat.of(player).machine();
                    WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
                    if (weapon != null) {
                        weapon.ability().flatMap(WeaponDef.Ability::recast).ifPresent(r -> machine.armRecast(r.move(), moment.ticks()));
                    }
                }
                stickSparks(moment.at(), moment.value() > 0.5f);
            }
            case TetherBlades.RETURNED -> {
                if (local) {
                    PlayerCombat.of(player).machine().disarmRecast();
                }
            }
            case TetherBlades.MARKED -> {
                if (mc.level != null && mc.level.getEntity(moment.ticks()) instanceof LivingEntity target) {
                    WorldFx.add(new MarkGlyph(target, Math.max(1, Math.round(moment.value()))));
                }
            }
            default -> {
            }
        }
    }

    private static void stickSparks(Vec3 at, boolean inEnemy) {
        if (!FxParticles.ready()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        GlowEffect.flash(at, inEnemy ? 0.55f : 0.4f, PALE_VIOLET, 0.8f, 5);
        int sparks = FxBudget.count(8, at, false);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() + 0.4, RANDOM.nextGaussian()).normalize()
                    .scale(0.12 + RANDOM.nextDouble() * 0.12);
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(RANDOM.nextBoolean() ? VIOLET : PALE_VIOLET)
                    .size(0.028f, 0.01f).life(5 + RANDOM.nextInt(4)).gravity(0.3f).drag(0.84f).streak(1.6f, 0.08f));
        }
    }

    // ------------------------------------------------------------------ the blink

    /** Where a blink by {@code player} to its blade arrives (feet), or null with no blade to go to. */
    static @Nullable Vec3 blinkArrival(Player player) {
        ThrownSickle blade = bladeOf(player);
        if (blade == null) {
            return null;
        }
        Entity in = blade.stuckEntity();
        AABB box = in instanceof LivingEntity living ? living.getBoundingBox() : null;
        return TetherMath.arrival(player.position(), blade.position(), box, box == null ? blade.stuckFace() : null);
    }

    private static void blinkStarted(Player player, MoveDef def, MoveEffect effect) {
        int travel = effect.intParam("travel", def.timing().startup());
        Vec3 arrival = blinkArrival(player);
        Vec3 chest = new Vec3(0, player.getBbHeight() * 0.55, 0);
        if (player == Minecraft.getInstance().player) {
            if (arrival != null) {
                ClientCombat.startBlink(arrival, travel);
            }
            CombatAudio.playOwn(player, ModSounds.EDGES_BLINK, 1.0f, 1.0f);
        }
        Afterimages.leave(player, player.position(), CYAN, 0.42f, 12);
        if (arrival != null) {
            WorldFx.add(new BlinkStreak(player.position().add(chest), arrival.add(chest), travel));
            Vec3 dir = arrival.subtract(player.position());
            if (dir.lengthSqr() > 1e-4) {
                Vec3 along = dir.normalize();
                WorldFx.follow(player, travel, p -> speedLines(p, along));
            }
        }
    }

    /** Streaks rushing past the blinking body (seen from its own eyes too: they fly by the camera). */
    private static void speedLines(Entity player, Vec3 along) {
        if (!FxParticles.ready()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Vec3 up = side.cross(along).normalize();
        Vec3 eye = player.position().add(0, player.getBbHeight() * 0.8, 0);
        int lines = FxBudget.count(6, eye, true);
        for (int i = 0; i < lines; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double r = 0.55 + RANDOM.nextDouble() * 0.9;
            Vec3 at = eye.add(side.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r * 0.8))
                    .add(along.scale(0.5 + RANDOM.nextDouble() * 1.5));
            FxBudget.spawn(FxParticles.spark(level, at).streakAlong(along, (float) (1.2 + RANDOM.nextDouble() * 0.8))
                    .velocity(along.scale(-0.35)).size(0.03f, 0.01f).life(3 + RANDOM.nextInt(2))
                    .color(RANDOM.nextBoolean() ? PALE_CYAN : PALE_VIOLET));
        }
    }

    private static void blinkArrived(Player player) {
        if (!FxParticles.ready()) {
            return;
        }
        Vec3 chest = player.position().add(0, player.getBbHeight() * 0.55, 0);
        boolean own = WorldFx.firstPersonOf(player);
        GlowEffect.flash(chest, own ? 0.6f : 1.1f, PALE_CYAN, own ? 0.45f : 0.8f, 5);
        GlowEffect.ring(chest, 0.2f, own ? 1.0f : 1.6f, VIOLET, own ? 0.45f : 0.8f, 7);
        if (player == Minecraft.getInstance().player) {
            CombatAudio.playOwn(player, ModSounds.EDGES_CHIME, 0.9f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ dashes, the Twin Meteor

    /** A dash with the Edges in hand leaves three afterimages along its path, cyan and violet in turn. */
    private static void dashed(Entity entity) {
        if (!(entity instanceof Player player) || TwinBlades.offHandOf(player) == null) {
            return;
        }
        Afterimages.leave(player, player.position(), CYAN, 0.24f, 8);
        WorldFx.after(2, () -> Afterimages.leave(player, player.position(), VIOLET, 0.2f, 8));
        WorldFx.after(4, () -> Afterimages.leave(player, player.position(), CYAN, 0.16f, 8));
    }

    private static void meteorLanding(Player player, MoveEffect effect, double fall) {
        Vec3 feet = player.position();
        float grow = (float) (1.0 + Math.min(fall, 15.0) * effect.param("per_block", 0.05));
        float ring = (float) effect.param("ring", 2.2) * grow;
        GlowEffect.groundRing(feet, 0.3f, ring, CYAN, 1.0f, 10);
        GlowEffect.groundRing(feet.add(0, 0.01, 0), 0.2f, ring * 0.75f, VIOLET, 0.9f, 12);
        GlowEffect.ground(feet, ring * 0.6f, PALE_VIOLET, 0.5f, 8);
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 ground = feet.add(0, 0.08, 0);
        int sparks = FxBudget.count(16, ground, fall >= 4.0);
        for (int i = 0; i < sparks; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            FxBudget.spawn(FxParticles.spark(level, ground.add(out.scale(0.3))).velocity(out.scale(0.3 + RANDOM.nextDouble() * 0.3)
                            .add(0, 0.1 + RANDOM.nextDouble() * 0.2, 0))
                    .color(i % 2 == 0 ? CYAN : VIOLET).size(0.03f, 0.01f).life(6 + RANDOM.nextInt(5)).gravity(0.7f).drag(0.86f)
                    .physics().streak(1.6f, 0.1f));
        }
        if (WorldFx.firstPersonOf(player)) {
            ClientCombat.feelSlam(0.12 + Math.min(fall, 10.0) * 0.012, 1.2);
        }
    }

    // ------------------------------------------------------------------ the Orbit's blades

    /** The two sickles of every running Orbit, drawn as items (lit, solid) where they fly. */
    private static final List<OrbitEffect> ORBITS = new ArrayList<>();
    /** Their own buffers: flushing them leaves the level's batches alone. */
    private static final MultiBufferSource.BufferSource ORBIT_BUFFERS =
            MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 16));

    private static void renderOrbitBlades(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ORBITS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (leftStack.isEmpty()) {
            leftStack = new ItemStack(ModItems.BINARY_EDGES_LEFT.get());
            rightStack = new ItemStack(ModItems.BINARY_EDGES.get());
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = ORBIT_BUFFERS;
        PoseStack pose = new PoseStack();
        for (OrbitEffect orbit : ORBITS) {
            double now = FxClock.now(partialTick);
            for (int blade = 0; blade < 2; blade++) {
                OrbitEffect.Place place = orbit.place(blade, now, partialTick);
                if (place == null) {
                    continue;
                }
                pose.pushPose();
                pose.translate(place.at().x - cam.x, place.at().y - cam.y, place.at().z - cam.z);
                pose.mulPose(Axis.YP.rotation((float) (-place.angle()) - Mth.HALF_PI));
                pose.mulPose(Axis.XP.rotationDegrees(80f));
                pose.mulPose(Axis.ZP.rotationDegrees(-45f));
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(place.at()));
                mc.getItemRenderer().renderStatic(blade == 0 ? rightStack : leftStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, light,
                        OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, orbit.player.getId() + blade);
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    /**
     * The Binary Orbit: from the release to the catch both sickles circle the body at {@code radius} on their
     * chains, {@code turns} times round over the active ticks, opposite each other, at {@code height}, each with
     * a ring of its colour behind it; then they fly back to the hands.
     */
    static final class OrbitEffect implements WorldFx.Effect {
        record Place(Vec3 at, double angle, Vec3 hand) {
        }

        private final Player player;
        private final double born = FxClock.ticks();
        private final double release;
        private final double active;
        private final double radius;
        private final double turns;
        private final double height;
        private final double start;

        OrbitEffect(Player player, MoveDef def, MoveEffect effect) {
            this.player = player;
            this.release = def.timing().startup();
            this.active = Math.max(1, def.timing().active());
            this.radius = effect.param("radius", 2.7);
            this.turns = effect.param("turns", 3.0);
            this.height = effect.param("height", 1.0);
            this.start = Math.toRadians(player.getYRot()) + Math.PI / 2.0;
            ORBITS.add(this);
        }

        @Override
        public boolean tick() {
            boolean alive = !player.isRemoved() && FxClock.ticks() - born < release + active + 4;
            if (!alive) {
                ORBITS.remove(this);
            }
            return alive;
        }

        /** Where blade {@code i} (0 right, 1 left) is at time {@code now}, or null while in the hand. */
        @Nullable Place place(int i, double now, float partialTick) {
            double t = now - born;
            if (t < release - 1.0 || t > release + active + 3.0) {
                return null;
            }
            Vec3 centre = player.getPosition(partialTick).add(0, height, 0);
            Vec3 hand = handPoint(player, i == 1, partialTick);
            double orbitT = Mth.clamp((t - release) / active, 0.0, 1.0);
            double angle = start + (i == 1 ? Math.PI : 0.0) - orbitT * turns * Math.PI * 2.0;
            Vec3 onRing = centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Vec3 at;
            if (t < release) {
                at = hand.lerp(onRing, Mth.clamp(t - (release - 1.0), 0.0, 1.0)); // flung out
            } else if (t > release + active) {
                at = onRing.lerp(hand, Mth.clamp((t - release - active) / 3.0, 0.0, 1.0)); // reeled in
            } else {
                at = onRing;
            }
            return new Place(at, angle, hand);
        }

        @Override
        public void render(WorldFx.Frame f) {
            VertexConsumer beam = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            for (int i = 0; i < 2; i++) {
                Place place = place(i, f.now(), f.partialTick());
                if (place == null) {
                    continue;
                }
                int color = i == 0 ? CYAN : VIOLET;
                float[] c = WorldFx.rgb(color);
                // the chain of light from the hand
                strip(beam, f, f.relative(place.hand()), f.relative(place.at()), 0.025, c, 0.55f);
                // the ring behind the blade
                double t = f.now() - born;
                if (t >= release && t <= release + active + 1.0) {
                    Vec3 centre = player.getPosition(f.partialTick()).add(0, height, 0);
                    int steps = 14;
                    Vec3 prev = null;
                    for (int k = 0; k <= steps; k++) {
                        double back = place.angle() + k * (Math.PI * 0.85 / steps); // behind, the way it came from
                        Vec3 p = f.relative(centre.add(Math.cos(back) * radius, 0.05 * Math.sin(k), Math.sin(back) * radius));
                        if (prev != null) {
                            float a = (float) (0.75 * (1.0 - k / (double) steps));
                            strip(beam, f, prev, p, 0.07 * (1.0 - 0.6 * k / (double) steps), c, a);
                        }
                        prev = p;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the thrown blade's chain and trail

    /** A player's left hand as drawn: in first person the lower left of the view, else beside the body. */
    static Vec3 handPoint(Player player, boolean left, float partialTick) {
        if (WorldFx.firstPersonOf(player)) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = camera.getLookVector();
            Vector3f up = camera.getUpVector();
            Vector3f side = camera.getLeftVector();
            double s = left ? 0.36 : -0.36;
            return camera.getPosition().add(look.x() * 0.55 + side.x() * s - up.x() * 0.3,
                    look.y() * 0.55 + side.y() * s - up.y() * 0.3, look.z() * 0.55 + side.z() * s - up.z() * 0.3);
        }
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 leftward = new Vec3(forward.z, 0, -forward.x);
        return player.getPosition(partialTick).add(0, player.getBbHeight() * 0.5, 0)
                .add(leftward.scale(left ? 0.38 : -0.38)).add(forward.scale(0.3));
    }

    /**
     * While a thrown blade is out: a chain of light from the thrower's left hand to it (a thin line with bright
     * links along it), and behind the flying blade a short violet trail.
     */
    static final class TetherChain implements WorldFx.Effect {
        private static final double LINK_SPACING = 0.42;
        private final ThrownSickle blade;
        private final Player owner;
        private final Deque<Vec3> path = new ArrayDeque<>();

        TetherChain(ThrownSickle blade, Player owner) {
            this.blade = blade;
            this.owner = owner;
        }

        @Override
        public boolean tick() {
            if (blade.isRemoved() || owner.isRemoved()) {
                return false;
            }
            path.addLast(blade.position());
            while (path.size() > 5) {
                path.removeFirst();
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            if (blade.isRemoved()) {
                return;
            }
            Vec3 at = blade.getPosition(f.partialTick());
            Vec3 hand = handPoint(owner, true, f.partialTick());
            float[] violet = WorldFx.rgb(VIOLET);
            float[] pale = WorldFx.rgb(PALE_VIOLET);
            VertexConsumer beam = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            Vec3 a = f.relative(hand);
            Vec3 b = f.relative(at);
            boolean own = WorldFx.firstPersonOf(owner);
            strip(beam, f, a, b, own ? 0.05 : 0.14, violet, 0.5f);
            strip(beam, f, a, b, own ? 0.018 : 0.045, violet, 0.95f);
            strip(beam, f, a, b, own ? 0.007 : 0.016, pale, 0.9f);
            // the links: small glints along the chain, drifting toward the blade
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            double length = at.distanceTo(hand);
            double drift = (f.now() * 0.12) % LINK_SPACING;
            for (double d = drift; d < length; d += LINK_SPACING) {
                Vec3 p = f.relative(hand.lerp(at, d / Math.max(1e-6, length)));
                float size = (float) Math.min(own ? 0.05 : 0.1, p.length() * 0.025);
                WorldFx.billboard(glow, f.camera(), p, size * 1.8f, violet[0], violet[1], violet[2], 0.45f);
                WorldFx.billboard(glow, f.camera(), p, size, pale[0], pale[1], pale[2], 0.85f);
            }
            // behind a flying blade, a fading violet trail (a buffer asked for again: another type ended the last)
            beam = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            if (!blade.isStuck() && path.size() >= 2) {
                Vec3 prev = null;
                int n = path.size();
                int k = 0;
                for (Vec3 p : path) {
                    Vec3 q = f.relative(p);
                    if (prev != null) {
                        float alpha = 0.55f * k / n;
                        strip(beam, f, prev, q, 0.11 * k / n, violet, alpha);
                    }
                    prev = q;
                    k++;
                }
                strip(beam, f, prev, b, 0.12, violet, 0.6f);
            }
            glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.billboard(glow, f.camera(), b, blade.isStuck() ? 0.22f : 0.3f, violet[0], violet[1], violet[2],
                    blade.isStuck() ? 0.35f + 0.15f * (float) Math.sin(f.now() * 0.6) : 0.55f);
        }
    }

    // ------------------------------------------------------------------ the blink's streak, the Mark

    /** The blink's streak: a line of light from where it started to where it arrives, drawn out over the travel. */
    static final class BlinkStreak implements WorldFx.Effect {
        private final Vec3 from;
        private final Vec3 to;
        private final int travel;
        private final double born = FxClock.ticks();

        BlinkStreak(Vec3 from, Vec3 to, int travel) {
            this.from = from;
            this.to = to;
            this.travel = Math.max(1, travel);
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - born < travel + 10;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double t = f.now() - born;
            double head = Mth.clamp(t / travel, 0.0, 1.0);
            double fade = t <= travel ? 1.0 : Math.max(0.0, 1.0 - (t - travel) / 10.0);
            if (fade <= 0.0) {
                return;
            }
            Vec3 end = from.lerp(to, head);
            VertexConsumer beam = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            Vec3 a = f.relative(from);
            Vec3 b = f.relative(end);
            strip(beam, f, a, b, 0.05, WorldFx.rgb(PALE_CYAN), (float) (0.95 * fade));
            strip(beam, f, a, b, 0.16, WorldFx.rgb(CYAN), (float) (0.45 * fade));
            strip(beam, f, a, b, 0.32, WorldFx.rgb(VIOLET), (float) (0.18 * fade));
        }
    }

    /** The Mark: a violet star turning over the enemy's head, pulsing, for as long as it lasts. */
    static final class MarkGlyph implements WorldFx.Effect {
        private final LivingEntity target;
        private final int life;
        private final double born = FxClock.ticks();

        MarkGlyph(LivingEntity target, int life) {
            this.target = target;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return target.isAlive() && FxClock.ticks() - born < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double t = f.now() - born;
            float fade = (float) Math.min(1.0, Math.min(t / 3.0, (life - t) / 8.0));
            if (fade <= 0f) {
                return;
            }
            Vec3 at = f.relative(target.getPosition(f.partialTick()).add(0, target.getBbHeight() + 0.45, 0));
            float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.5);
            float[] violet = WorldFx.rgb(VIOLET);
            float[] pale = WorldFx.rgb(PALE_VIOLET);
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.billboard(glow, f.camera(), at, 0.32f * pulse, violet[0], violet[1], violet[2], 0.55f * fade);
            VertexConsumer beam = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            double spin = t * 0.08;
            Vector3f leftV = f.camera().getLeftVector();
            Vector3f upV = f.camera().getUpVector();
            Vec3 left = new Vec3(leftV.x(), leftV.y(), leftV.z());
            Vec3 up = new Vec3(upV.x(), upV.y(), upV.z());
            for (int k = 0; k < 2; k++) {
                double angle = spin + k * Math.PI / 2.0;
                Vec3 arm = left.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(0.36 * pulse);
                strip(beam, f, at.subtract(arm), at.add(arm), 0.035, pale, 0.9f * fade);
            }
        }
    }

    /**
     * A strip facing the camera from {@code a} to {@code b} (camera-relative), {@code half} to each side, with
     * the beam texture across it (bright core, soft sides).
     */
    static void strip(VertexConsumer out, WorldFx.Frame f, Vec3 a, Vec3 b, double half, float[] c, float alpha) {
        Vec3 along = b.subtract(a);
        Vec3 mid = a.add(along.scale(0.5));
        Vec3 across = along.cross(mid);
        double length = across.length();
        if (length < 1e-9 || alpha <= 0.003f) {
            return;
        }
        Vec3 side = across.scale(half / length);
        WorldFx.vertex(out, a.subtract(side), 0f, 0.45f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, a.add(side), 1f, 0.45f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, b.add(side), 1f, 0.55f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, b.subtract(side), 0f, 0.55f, c[0], c[1], c[2], alpha);
    }
}
