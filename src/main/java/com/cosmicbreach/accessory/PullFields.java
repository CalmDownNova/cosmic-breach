package com.cosmicbreach.accessory;

import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.net.MoveEffectPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The accessories' pulls (GDD 5.2): the Gravity Loop's small Gravity Well where a long plunge lands, and the Event
 * Horizon Lens's black hole on a parried attacker. Each pulls every enemy that isn't a boss within its radius toward its
 * middle at the Gravity Well's pace ({@link AccessoryRules#pullStep}) for its ticks. The Loop's well is listed with the
 * Comet Maul's ({@link GravityWell#addLive}), so a Pocket Star inside it makes a Singularity and pulls it harder. The
 * clients draw each with the Gravity Well's own dust (a {@link MoveEffectPayload} under {@link GravityWell#ID}, keyed
 * by an id no entity has). Server only.
 */
public final class PullFields {
    /** What pulls: the Loop's well or the Lens's black hole. */
    public enum Kind { WELL, BLACK_HOLE }

    /** One pull running now. */
    public static final class Field {
        final Kind kind;
        final ServerLevel level;
        final Vec3 centre;
        final double radius;
        final ServerPlayer owner;
        final int visualId;
        final @Nullable GravityWell.Live live;
        int ticksLeft;
        int pulled;

        Field(Kind kind, ServerLevel level, Vec3 centre, double radius, int ticks, ServerPlayer owner, int visualId,
              @Nullable GravityWell.Live live) {
            this.kind = kind;
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.ticksLeft = ticks;
            this.owner = owner;
            this.visualId = visualId;
            this.live = live;
        }

        public Kind kind() {
            return kind;
        }

        public Vec3 centre() {
            return centre;
        }

        public double radius() {
            return radius;
        }

        public ServerPlayer owner() {
            return owner;
        }

        public int ticksLeft() {
            return ticksLeft;
        }

        /** How many body-ticks of pull it has given so far (for tests). */
        public int pulled() {
            return pulled;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    private static int nextVisual = -1_000_000;

    private PullFields() {
    }

    /** Opens a pull of {@code kind} at {@code centre} for {@code owner}. */
    public static Field open(Kind kind, ServerPlayer owner, Vec3 centre, double radius, int ticks) {
        ServerLevel level = owner.serverLevel();
        GravityWell.Live live = null;
        if (kind == Kind.WELL) {
            live = new GravityWell.Live(level, centre, radius, owner);
            GravityWell.addLive(live);
        }
        Field field = new Field(kind, level, centre, radius, ticks, owner, nextVisual--, live);
        FIELDS.add(field);
        ModNetworking.sendToTrackersAndSelf(owner, new MoveEffectPayload(field.visualId, GravityWell.ID, GravityWell.PLANTED,
                centre, (float) radius, ticks));
        return field;
    }

    /** The pulls running now (tests and the scenario read them). */
    public static List<Field> live() {
        return List.copyOf(FIELDS);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (FIELDS.isEmpty()) {
            return;
        }
        Iterator<Field> it = FIELDS.iterator();
        while (it.hasNext()) {
            Field field = it.next();
            if (field.owner.isRemoved() || field.ticksLeft <= 0) {
                close(field);
                it.remove();
                continue;
            }
            pull(field);
            field.ticksLeft--;
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (Field field : FIELDS) {
            if (field.live != null) {
                GravityWell.removeLive(field.live);
            }
        }
        FIELDS.clear();
    }

    private static void close(Field field) {
        if (field.live != null) {
            GravityWell.removeLive(field.live);
        }
        if (!field.owner.isRemoved()) {
            ModNetworking.sendToTrackersAndSelf(field.owner, new MoveEffectPayload(field.visualId, GravityWell.ID,
                    GravityWell.FADED, field.centre, (float) field.radius, 0));
        }
    }

    /** One tick: every enemy in range that isn't a boss, a step toward the middle. */
    private static void pull(Field field) {
        double scale = field.kind == Kind.WELL ? GravityWell.pullScale(field.level, field.centre, field.radius) : 1.0;
        double pull = AccessoryRules.PULL * scale;
        AABB area = new AABB(field.centre, field.centre).inflate(field.radius);
        if (field.owner.level() != field.level) {
            return;
        }
        for (LivingEntity target : HitResolver.candidates(field.owner, area)) {
            if (target.getType().is(Tags.EntityTypes.BOSSES) || target.position().distanceTo(field.centre) > field.radius
                    || com.cosmicbreach.combat.server.TargetShield.riddenByAnotherPlayer(target, field.owner)) {
                continue; // a mount another player rides is moved by that player's client, never pushed from here
            }
            double[] step = AccessoryRules.pullStep(field.centre.x - target.getX(), field.centre.z - target.getZ(), pull);
            if (step[0] == 0.0 && step[1] == 0.0) {
                continue;
            }
            Vec3 move = new Vec3(step[0], 0.0, step[1]);
            if (target instanceof Player) {
                target.setDeltaMovement(target.getDeltaMovement().add(move)); // a player's own client moves it
                target.hurtMarked = true;
            } else {
                target.move(MoverType.SELF, move);
            }
            field.pulled++;
        }
    }
}
