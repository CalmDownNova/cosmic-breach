package com.cosmicbreach.astrolabe;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatStateMachine;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.combat.server.effect.ServerMoveEffect;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Choir Astrolabe's server effects (GDD 4.2), by id in its moves' data:
 * <ul>
 *   <li>{@link #STAR_BOLT}: the chain's bolts and the Starfall. On the first active tick, {@code count} bolts
 *       ({@code spread} degrees fanned, from the {@code hand}: -1 left, 1 right) leave toward the crosshair's point
 *       (or {@code down} degrees below the level, the Starfall), each a {@link StarBolt} with the move's motion value
 *       and Impact, a {@code splash} radius if it has one.</li>
 *   <li>{@link #CONSTELLATION}: while the charge is held, the aim marks up to five enemies ({@link Constellation}); the
 *       release's beam strikes each once at the move's motion value, one a tick along the chain.</li>
 *   <li>{@link #PARALLAX}: the dash attack leaves a {@link ParallaxDecoy} that fires two bolts.</li>
 *   <li>{@link #POCKET_STAR}: places a {@link PocketStar} where the aim meets something (up to 16 blocks).</li>
 *   <li>{@link #SUPERNOVA}: the second press detonates the star ({@link PocketStar#detonate}).</li>
 * </ul>
 * Every bolt is a chime ({@link AstrolabeTunes}): the owner's client plays its own at once; the server plays them for
 * everyone else. The Singularity is a {@link GravityWell.PullBoost}: a well with a Pocket Star in it pulls twice as hard.
 */
public final class AstrolabeEffects {
    public static final ResourceLocation STAR_BOLT = CosmicBreach.id("star_bolt");
    public static final ResourceLocation CONSTELLATION = CosmicBreach.id("constellation");
    public static final ResourceLocation PARALLAX = CosmicBreach.id("parallax");
    public static final ResourceLocation POCKET_STAR = CosmicBreach.id("pocket_star");
    public static final ResourceLocation SUPERNOVA = CosmicBreach.id("supernova");

    /** Constellation moments: a mark (at the enemy, value its index, ticks its id), the marks cleared, a beam link. */
    public static final int MARK = 0;
    public static final int CLEAR = 1;
    public static final int LINK = 2;
    /** A release with no marks still strikes what the crosshair is nearly on, this wide. */
    public static final double RELEASE_CONE = 6.0;

    /** The enemies each charging player has marked, in order. */
    private static final Map<ServerPlayer, List<Integer>> MARKS = new WeakHashMap<>();
    /** The beam each player's Constellation is striking: its move's serial and the chain. */
    private static final Map<ServerPlayer, Beam> BEAMS = new WeakHashMap<>();
    /** Each player's bar of the chain's four-bar phrase (the server's count, for the chimes others hear). */
    private static final Map<UUID, Integer> BARS = new ConcurrentHashMap<>();

    private record Beam(int serial, List<Integer> chain) {
    }

    private AstrolabeEffects() {
    }

    public static void register(IEventBus game) {
        ServerMoveEffects.register(STAR_BOLT, new Bolts());
        ServerMoveEffects.register(CONSTELLATION, new Beamer());
        ServerMoveEffects.register(PARALLAX, new Decoy());
        ServerMoveEffects.register(POCKET_STAR, new Star());
        ServerMoveEffects.register(SUPERNOVA, new Nova());
        GravityWell.boost((level, centre, radius) -> PocketStarRules.pullScale(PocketStar.anyInside(level, centre, radius)));
        game.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                tickMarks(player);
            }
        });
        game.addListener(ServerStoppingEvent.class, event -> {
            MARKS.clear();
            BEAMS.clear();
            BARS.clear();
            PocketStar.clearAll();
        });
    }

    // ------------------------------------------------------------------ aiming

    /** Where the crosshair meets a block within {@code range}, else {@code range} blocks along the aim. */
    public static Vec3 aimPoint(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** The enemy the crosshair is on or nearly on (within {@code cone} degrees), with a clear line; null if none. */
    public static @Nullable LivingEntity underCrosshair(ServerPlayer player, double range, double cone) {
        List<LivingEntity> near = candidates(player, range);
        List<Constellation.Candidate> boxes = new ArrayList<>();
        near.forEach(e -> boxes.add(new Constellation.Candidate(e.getId(), e.getBoundingBox())));
        int id = Constellation.pick(player.getEyePosition(), player.getLookAngle(), boxes, List.of(), range, cone);
        return id < 0 ? null : player.level().getEntity(id) instanceof LivingEntity l ? l : null;
    }

    /** Valid enemies within {@code range} of the eye that the eye can see. */
    private static List<LivingEntity> candidates(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
                e -> HitResolver.isValidTarget(player, e) && player.hasLineOfSight(e));
    }

    // ------------------------------------------------------------------ bolts

    /** The chain's bolts, the Triad and the Starfall. */
    static final class Bolts implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            WeaponDef weapon = use.combat().machine().weapon();
            if (weapon == null) {
                return;
            }
            int count = Math.max(1, use.intParam("count", 1));
            double spread = use.param("spread", 0.0);
            double down = use.param("down", -1.0);
            double splash = use.param("splash", 0.0);
            Vec3 from = handPoint(player, use.intParam("hand", 1));
            Vec3 dir;
            if (down >= 0) {
                dir = Vec3.directionFromRotation((float) down, player.getYRot());
            } else {
                LivingEntity target = underCrosshair(player, use.param("range", Homing.RANGE), 2.0);
                Vec3 to = target != null ? target.position().add(0, target.getBbHeight() * 0.55, 0)
                        : aimPoint(player, use.param("range", Homing.RANGE));
                dir = to.subtract(from);
                if (dir.lengthSqr() < 1e-6) {
                    dir = player.getLookAngle();
                }
                dir = dir.normalize();
            }
            byte style = down >= 0 ? StarBolt.STARFALL : count >= 3 ? StarBolt.TRIAD : StarBolt.BOLT;
            double speed = use.param("speed", Homing.SPEED);
            ItemStack stack = player.getMainHandItem().copy();
            MoveInstance move = use.move();
            for (int i = 0; i < count; i++) {
                double angle = count == 1 ? 0.0 : -spread / 2.0 + spread * i / (count - 1);
                Vec3 d = Homing.fan(dir, angle);
                StarBolt bolt = new StarBolt(player.level(), player, from, d.scale(speed), style)
                        .configure(move, weapon, stack, move.mv(), move.def().hit().impact(), use.param("range", Homing.RANGE),
                                use.param("cone", Homing.CONE), use.param("turn", Homing.TURN), splash);
                player.level().addFreshEntity(bolt);
            }
            chime(player, weapon, move.def(), use.move().id());
        }
    }

    /** Where a bolt leaves the hand: in front of the chest, to the {@code hand}'s side. */
    static Vec3 handPoint(ServerPlayer player, int hand) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return player.getEyePosition().add(player.getLookAngle().scale(1.0)).add(right.scale(0.32 * hand)).add(0, -0.3, 0);
    }

    /** The chimes others hear for a bolt move (the owner's client plays its own). */
    private static void chime(ServerPlayer player, WeaponDef weapon, MoveDef def, ResourceLocation id) {
        int slot = weapon.combo().indexOf(id);
        int bar = BARS.getOrDefault(player.getUUID(), 0);
        int[] notes;
        if (slot == 0) {
            bar++;
            BARS.put(player.getUUID(), bar);
            notes = new int[] {AstrolabeTunes.first(bar)};
        } else if (slot == 1) {
            notes = new int[] {AstrolabeTunes.second(bar)};
        } else if (slot == 2) {
            notes = AstrolabeTunes.chord(bar);
        } else {
            notes = new int[] {AstrolabeTunes.starfall(bar)};
        }
        for (int n : notes) {
            ServerCombatSounds.forOthers(player, Astrolabes.CHIME, notes.length > 1 ? 0.6f : 0.8f, AstrolabeTunes.pitch(n));
        }
    }

    // ------------------------------------------------------------------ the Constellation

    private static void tickMarks(ServerPlayer player) {
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat == null || !combat.isServerSide()) {
            return;
        }
        CombatStateMachine machine = combat.machine();
        boolean charging = machine.phase() == CombatStateMachine.Phase.CHARGING && chargedIsConstellation(machine.weapon());
        if (charging && player.isAlive()) {
            mark(player);
            return;
        }
        MoveInstance current = machine.current();
        boolean beaming = current != null && current.def().traits().effect(CONSTELLATION).isPresent();
        if (!beaming && MARKS.containsKey(player)) {
            clearMarks(player);
        }
    }

    private static boolean chargedIsConstellation(@Nullable WeaponDef weapon) {
        if (weapon == null || weapon.charged().isEmpty()) {
            return false;
        }
        MoveDef def = CombatData.server().move(weapon.charged().get());
        return def != null && def.traits().effect(CONSTELLATION).isPresent();
    }

    private static void mark(ServerPlayer player) {
        List<Integer> marks = MARKS.computeIfAbsent(player, p -> new ArrayList<>());
        if (marks.size() >= Constellation.MAX) {
            return;
        }
        List<Constellation.Candidate> boxes = new ArrayList<>();
        for (LivingEntity e : candidates(player, Constellation.RANGE)) {
            boxes.add(new Constellation.Candidate(e.getId(), e.getBoundingBox()));
        }
        int id = Constellation.pick(player.getEyePosition(), player.getLookAngle(), boxes, marks, Constellation.RANGE, Constellation.CONE);
        if (id >= 0 && player.level().getEntity(id) instanceof LivingEntity target) {
            marks.add(id);
            send(player, MARK, target.position().add(0, target.getBbHeight(), 0), marks.size() - 1, id);
            ServerCombatSounds.forEveryone(player.level(), target.position().add(0, target.getBbHeight(), 0), Astrolabes.MARK, 0.6f,
                    AstrolabeTunes.pitch(AstrolabeTunes.arpeggio(marks.size() - 1)));
        }
    }

    private static void clearMarks(ServerPlayer player) {
        MARKS.remove(player);
        send(player, CLEAR, player.position(), 0, 0);
    }

    /** The enemies {@code player} has marked so far (server; for tests). */
    public static List<Integer> marksOf(ServerPlayer player) {
        return List.copyOf(MARKS.getOrDefault(player, List.of()));
    }

    private static void send(ServerPlayer player, int stage, Vec3 at, float value, int ticks) {
        ModNetworking.sendToTrackersAndSelf(player, new MoveEffectPayload(player.getId(), CONSTELLATION, stage, at, value, ticks));
    }

    /** The release: one beam through every mark, striking them along the chain. */
    static final class Beamer implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            ServerPlayer player = use.player();
            WeaponDef weapon = use.combat().machine().weapon();
            if (weapon == null) {
                return;
            }
            Beam beam = BEAMS.get(player);
            if (activeTick == 0 || beam == null || beam.serial() != use.move().serial()) {
                List<Integer> chain = new ArrayList<>(MARKS.getOrDefault(player, List.of()));
                if (chain.isEmpty()) {
                    LivingEntity near = underCrosshair(player, Constellation.RANGE, RELEASE_CONE);
                    if (near != null) {
                        chain.add(near.getId());
                    }
                }
                beam = new Beam(use.move().serial(), chain);
                BEAMS.put(player, beam);
                for (int i = 0; i < chain.size(); i++) {
                    if (player.level().getEntity(chain.get(i)) instanceof LivingEntity t) {
                        send(player, LINK, t.position().add(0, t.getBbHeight() * 0.55, 0), i, t.getId());
                    }
                }
                if (chain.isEmpty()) {
                    send(player, LINK, aimPoint(player, Constellation.RANGE), -1, -1);
                }
            }
            int active = use.move().def().timing().active();
            ItemStack stack = player.getMainHandItem().copy();
            for (int i = 0; i < beam.chain().size(); i++) {
                if (Constellation.hitTick(i, active) != activeTick) {
                    continue;
                }
                if (player.level().getEntity(beam.chain().get(i)) instanceof LivingEntity target && target.isAlive()
                        && target.distanceTo(player) <= Constellation.RANGE + 4.0) {
                    Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
                    Vec3 dir = at.subtract(player.getEyePosition());
                    if (HitResolver.strikeRanged(player, use.combat(), use.move(), weapon, stack, target, use.move().mv(),
                            use.move().def().hit().impact(), at, dir.lengthSqr() < 1e-6 ? player.getLookAngle() : dir.normalize())) {
                        use.combat().machine().onHitLanded(use.move(), 1);
                    }
                }
            }
        }

        @Override
        public void ended(Use use, boolean cancelled) {
            BEAMS.remove(use.player());
            if (MARKS.containsKey(use.player())) {
                clearMarks(use.player());
            }
        }
    }

    /** The chain the player's running Constellation strikes (server; for tests). */
    public static List<Integer> beamOf(ServerPlayer player) {
        Beam beam = BEAMS.get(player);
        return beam == null ? List.of() : List.copyOf(beam.chain());
    }

    // ------------------------------------------------------------------ the Parallax

    static final class Decoy implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            WeaponDef weapon = use.combat().machine().weapon();
            if (weapon == null) {
                return;
            }
            Vec3 at = player.position().add(0, player.getBbHeight() * 0.62, 0);
            ParallaxDecoy decoy = new ParallaxDecoy(player.level(), player, at).configure(use.move(), weapon,
                    player.getMainHandItem().copy(), use.intParam("bolts", 2), use.intParam("first", 3), use.intParam("gap", 5),
                    use.intParam("life", 14));
            player.level().addFreshEntity(decoy);
            use.send(0, at, 0f, decoy.getId());
        }
    }

    // ------------------------------------------------------------------ the Pocket Star and the Supernova

    static final class Star implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            WeaponDef weapon = use.combat().machine().weapon();
            if (weapon == null) {
                return;
            }
            double range = use.param("range", PocketStarRules.RANGE);
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            Vec3 at = PocketStarRules.placement(eye, look, hit.getType() == HitResult.Type.MISS ? null : hit.getLocation(), range);
            PocketStar.replaceOf(player);
            PocketStar star = new PocketStar(player.level(), player, at).configure(use.move(), weapon, player.getMainHandItem().copy(),
                    use.intParam("life", PocketStarRules.LIFE), use.intParam("pulse_every", PocketStarRules.PULSE_EVERY),
                    use.param("pulse_radius", PocketStarRules.PULSE_RADIUS), use.param("pulse_mv", PocketStarRules.PULSE_MV),
                    use.param("pulse_impact", PocketStarRules.PULSE_IMPACT), use.param("swallow", PocketStarRules.SWALLOW_RADIUS),
                    use.intParam("light", PocketStarRules.LIGHT));
            player.level().addFreshEntity(star);
            use.send(PocketStar.PLACED, at, 0f, star.getId());
            ServerCombatSounds.forEveryone(player.level(), at, Astrolabes.STAR_PLACE, 0.9f, 1.0f);
        }
    }

    static final class Nova implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            ServerPlayer player = use.player();
            WeaponDef weapon = use.combat().machine().weapon();
            PocketStar star = PocketStar.of(player);
            if (weapon == null || star == null) {
                return;
            }
            int hit = star.detonate(use.move(), weapon, player.getMainHandItem().copy(), use.param("radius", PocketStarRules.NOVA_RADIUS),
                    use.param("base", PocketStarRules.NOVA_BASE), use.param("bonus", PocketStarRules.NOVA_BONUS),
                    use.move().def().hit().impact());
            if (hit > 0) {
                use.combat().machine().onHitLanded(use.move(), hit);
            }
        }
    }
}
