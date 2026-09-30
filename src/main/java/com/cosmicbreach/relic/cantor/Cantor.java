package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.combat.server.effect.ServerMoveEffect;
import com.cosmicbreach.combat.server.effect.ServerMoveEffects;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.cantor.CantorRules.Note;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Umbra Cantor on the server (GDD 7.3; the numbers are {@link CantorRules}), by effect id in its moves' data:
 * <ul>
 *   <li>{@link #UMBRA_SHOT}: the chain's shots, the charged shot, the Grace Note and the Syncopation. On the first
 *       active tick, {@code count} arrows ({@code spread} degrees fanned) leave the bow toward the crosshair's point (or
 *       {@code down} degrees below the level, the Grace Note from the air), each an {@link UmbraArrow} with the move's
 *       motion value and Impact; {@code note} arrows leave a note where they land.</li>
 *   <li>{@link #CADENCE}: the ability, three note arrows on its active ticks 0, 5 and 10, fanned left, far middle,
 *       right ({@link CantorRules#cadenceOffset}).</li>
 * </ul>
 * Notes ({@link ResonantNote}) and chords ({@link ChordField}) live here per player: a note that lands within 8 blocks of
 * two of its owner's loose notes that are within 8 of each other rings the three as a chord. Each note sounds its tone
 * for everyone where it lands; a chord sounds the triad. The clients hear of arrows landing and fading
 * ({@link #LAND}, {@link #FADE}, a {@link MoveEffectPayload} under {@link #UMBRA_SHOT}) and of notes, chords and
 * pulses ({@link #NOTE}, {@link #CHORD}, {@link #PULSE}, under {@link #NOTES}).
 */
public final class Cantor {
    public static final ResourceLocation UMBRA_SHOT = CosmicBreach.id("umbra_shot");
    public static final ResourceLocation CADENCE = CosmicBreach.id("cadence");
    public static final ResourceLocation NOTES = CosmicBreach.id("cantor_notes");
    public static final ResourceKey<DamageType> CHORD_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("chord"));
    public static final int LAND = 10;
    public static final int FADE = 11;
    public static final int NOTE = 0;
    public static final int CHORD = 1;
    public static final int PULSE = 2;
    /** Where a bolt leaves the bow: ahead of the eye, a little right and down (the bow is in the right hand). */
    private static final double HAND_AHEAD = 0.6;
    private static final double HAND_RIGHT = 0.2;
    private static final double HAND_DOWN = 0.12;

    /** Each player's loose notes (entity ids, oldest first) and how many notes it has placed, for their tones. */
    private static final Map<UUID, Score> SCORES = new ConcurrentHashMap<>();

    private static final class Score {
        final List<Integer> loose = new ArrayList<>();
        int placed;
    }

    private Cantor() {
    }

    public static void register(IEventBus game) {
        ServerMoveEffects.register(UMBRA_SHOT, new Shots());
        ServerMoveEffects.register(CADENCE, new Cadence());
        Silence.register(game);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SCORES.remove(event.getEntity().getUUID()));
        game.addListener(ServerStoppingEvent.class, event -> SCORES.clear());
    }

    // ------------------------------------------------------------------ shots

    /** The chain, the charged shot, the aerial and the dash attack: arrows on the first active tick. */
    static final class Shots implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            if (activeTick != 0) {
                return;
            }
            WeaponDef weapon = use.combat().machine().weapon();
            if (weapon == null) {
                return;
            }
            int count = Math.max(1, use.intParam("count", 1));
            double spread = use.param("spread", 0.0);
            double down = use.param("down", -1.0);
            boolean note = use.intParam("note", 0) != 0;
            byte style = note ? UmbraArrow.NOTE : use.intParam("heavy", 0) != 0 ? UmbraArrow.HEAVY : UmbraArrow.QUICK;
            for (int i = 0; i < count; i++) {
                double yaw = count == 1 ? 0.0 : -spread / 2.0 + spread * i / (count - 1);
                loose(use.player(), use.move(), weapon, yaw, 0.0, down, use.param("speed", 3.0), use.param("range", 48.0),
                        use.param("gravity", 0.012), note, style);
            }
        }
    }

    /** The ability: three note arrows in 12 ticks, landing as a triangle. */
    static final class Cadence implements ServerMoveEffect {
        @Override
        public boolean dealsItsOwnHits() {
            return true;
        }

        @Override
        public void activeTick(Use use, int activeTick) {
            int index = CantorRules.cadenceIndex(activeTick);
            WeaponDef weapon = use.combat().machine().weapon();
            if (index < 0 || weapon == null) {
                return;
            }
            double[] off = CantorRules.cadenceOffset(index, use.param("spread", 8.0), use.param("lift", 2.5));
            loose(use.player(), use.move(), weapon, off[0], off[1], -1.0, use.param("speed", 3.5), use.param("range", 48.0),
                    use.param("gravity", 0.012), true, UmbraArrow.NOTE);
            if (index > 0) {
                // the first shot's sound is the move's own swing; the next two loose with their own twang
                ServerCombatSounds.forEveryone(use.level(), use.player().getEyePosition(), Relics.UC_LOOSE, 0.9f, 1.0f + 0.06f * index);
            }
        }
    }

    /**
     * One arrow from {@code player}'s bow: toward the crosshair's point (yawed {@code yawOffset} degrees right and
     * lifted {@code upOffset} degrees), or {@code down} degrees below the level when that is 0 or more.
     */
    static void loose(ServerPlayer player, MoveInstance move, WeaponDef weapon, double yawOffset, double upOffset, double down,
                      double speed, double range, double gravity, boolean note, byte style) {
        Vec3 from = bowPoint(player);
        Vec3 dir;
        if (down >= 0) {
            dir = Vec3.directionFromRotation((float) down, player.getYRot());
        } else {
            dir = aimPoint(player, range).subtract(from);
            dir = dir.lengthSqr() < 1e-6 ? player.getLookAngle() : dir.normalize();
        }
        if (yawOffset != 0.0 || upOffset != 0.0) {
            float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
            float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
            dir = Vec3.directionFromRotation((float) (pitch - upOffset), (float) (yaw + yawOffset));
        }
        ItemStack stack = player.getMainHandItem().copy();
        UmbraArrow arrow = new UmbraArrow(player.level(), player, from, dir.scale(speed), style)
                .configure(move, weapon, stack, move.mv(), move.def().hit().impact(), range, gravity, note);
        player.level().addFreshEntity(arrow);
    }

    /** Where the arrows leave the bow. */
    static Vec3 bowPoint(ServerPlayer player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return player.getEyePosition().add(player.getLookAngle().scale(HAND_AHEAD)).add(right.scale(HAND_RIGHT)).add(0, -HAND_DOWN, 0);
    }

    /** Where the crosshair meets a block within {@code range}, else {@code range} blocks along the aim. */
    public static Vec3 aimPoint(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Tells everyone near the owner where an arrow ended and how (its style as the value, what it struck as ticks). */
    static void arrowMoment(ServerPlayer owner, int stage, Vec3 at, byte style, int struck) {
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), UMBRA_SHOT, stage, at, style, struck));
        if (stage == LAND) {
            ServerCombatSounds.forEveryone(owner.level(), at, Relics.UC_ARROW_HIT, style == UmbraArrow.QUICK ? 0.7f : 0.9f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ notes and chords

    /** A note arrow landed at {@code at} (on {@code on}, or a wall): a note, and a chord if it completes one. */
    static void placeNote(ServerPlayer owner, Vec3 at, @Nullable LivingEntity on) {
        ServerLevel level = owner.serverLevel();
        Score score = SCORES.computeIfAbsent(owner.getUUID(), id -> new Score());
        List<ResonantNote> loose = looseNotes(level, score);
        int evict = CantorRules.evict(asNotes(loose));
        if (evict >= 0) {
            ResonantNote old = loose.remove(evict);
            score.loose.remove((Integer) old.getId());
            old.discard();
        }
        int index = CantorRules.nextIndex(score.placed, loose.size());
        score.placed = index + 1;
        int tone = CantorRules.tone(index);
        ResonantNote note = new ResonantNote(level, owner, at, tone);
        level.addFreshEntity(note);
        ServerCombatSounds.forEveryone(level, at, Relics.UC_NOTE, 1.0f, CantorRules.pitch(tone));
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), NOTES, NOTE, at, tone, note.getId()));
        int[] pair = CantorRules.pick(asNote(note), asNotes(loose));
        if (pair == null) {
            score.loose.add(note.getId());
            return;
        }
        ResonantNote a = loose.get(pair[0]);
        ResonantNote b = loose.get(pair[1]);
        score.loose.remove((Integer) a.getId());
        score.loose.remove((Integer) b.getId());
        ItemStack stack = owner.getMainHandItem();
        double tier = stack.is(Relics.UMBRA_CANTOR.get()) ? ChordField.tierMultiplier(stack, 4) : 1.0;
        ChordField chord = new ChordField(level, owner, a.position(), b.position(), note.position(), tier);
        level.addFreshEntity(chord);
        a.discard();
        b.discard();
        note.discard();
        ServerCombatSounds.forEveryone(level, chord.position(), Relics.UC_CHORD, 1.2f, 1.0f);
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), NOTES, CHORD, chord.position(), 0f, chord.getId()));
    }

    /** The owner's notes still sounding, oldest first (the ids of those gone are dropped). */
    private static List<ResonantNote> looseNotes(ServerLevel level, Score score) {
        List<ResonantNote> out = new ArrayList<>();
        Iterator<Integer> it = score.loose.iterator();
        while (it.hasNext()) {
            Entity e = level.getEntity(it.next());
            if (e instanceof ResonantNote n && n.isAlive() && !CantorRules.expired(n.placed(), level.getGameTime())) {
                out.add(n);
            } else {
                it.remove();
            }
        }
        return out;
    }

    private static Note asNote(ResonantNote n) {
        return new Note(n.getId(), n.getX(), n.getY(), n.getZ(), n.placed());
    }

    private static List<Note> asNotes(List<ResonantNote> notes) {
        List<Note> out = new ArrayList<>(notes.size());
        notes.forEach(n -> out.add(asNote(n)));
        return out;
    }

    /** The owner's notes still sounding (server; the scenarios read it). */
    public static List<ResonantNote> notesOf(ServerPlayer owner) {
        Score score = SCORES.get(owner.getUUID());
        return score == null ? List.of() : looseNotes(owner.serverLevel(), score);
    }

    /** A chord's pulse, for the clients' ring (its centre, ticks how many it struck). */
    static void chordMoment(ServerPlayer owner, ChordField chord, int stage, int struck) {
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(owner.getId(), NOTES, stage, chord.position(), 0f,
                stage == PULSE ? struck : chord.getId()));
    }

    /** The chord's damage: {@code cosmicbreach:chord}, from the chord, caused by its player. */
    static DamageSource chordDamage(Level level, ServerPlayer owner, ChordField chord) {
        return level.damageSources().source(CHORD_DAMAGE, owner, chord);
    }

    /** A pulse struck {@code target}: its player gets the credit, as for any hit. */
    static void markStruck(ServerPlayer player, LivingEntity target) {
        player.setLastHurtMob(target);
    }
}
