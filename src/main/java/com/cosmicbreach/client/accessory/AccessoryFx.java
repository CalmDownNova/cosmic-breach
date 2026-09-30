package com.cosmicbreach.client.accessory;

import com.cosmicbreach.accessory.AccessoryFxPayload;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The accessories' moments as the clients see them (the server says when and where, {@link AccessoryFxPayload}):
 * <ul>
 *   <li>the Heart's nova: a white-gold flash, a ring racing out to its radius along the ground and another facing the
 *       eye, embers flung wide;</li>
 *   <li>the Gravity Loop's well: a violet ring on the ground at its edge (the Gravity Well's own dust comes with it);</li>
 *   <li>the Lens's black hole: a dark core on the attacker, a pale rim and a gold ring of light spun round it;</li>
 *   <li>the Hourglass's slow: a ring of dusk-gold sand sweeping out to 6 blocks (the slowed shed sand as they go);</li>
 *   <li>a Halo shard breaking (splinters, a glint), cutting (sparks) and growing back (a glint);</li>
 *   <li>the Leechstar Signet drinking: crimson motes drawn into the chest.</li>
 * </ul>
 */
public final class AccessoryFx {
    static final int GOLD = 0xFFD98A;
    static final int WHITE_GOLD = 0xFFF3D6;
    static final int VIOLET = 0xA46CFF;
    static final int SAND = 0xE8C27A;
    static final int SHARD = 0xFFF0C0;
    static final int CRIMSON = 0xE0405E;
    private static final RandomSource RANDOM = RandomSource.create();

    private AccessoryFx() {
    }

    public static void handle(AccessoryFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> play(payload));
    }

    static void play(AccessoryFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 at = p.at();
        switch (p.kind()) {
            case AccessoryFxPayload.NOVA -> {
                Pulse.flash(at, 2.6f, WHITE_GOLD, 0.95f, 7);
                Pulse.flash(at, 4.2f, GOLD, 0.45f, 10);
                Pulse.ring(at, 0.4f, p.value() * 1.1f, WHITE_GOLD, 0.9f, 10);
                Vec3 feet = at.subtract(0, 0.9, 0);
                Pulse.groundRing(feet, 0.5f, p.value() * 1.15f, GOLD, 1.0f, 12);
                Pulse.ground(feet, p.value() * 0.8f, GOLD, 0.5f, 14);
                burst(level, at, 26, 0.5, GOLD, 12);
            }
            case AccessoryFxPayload.WELL -> {
                Pulse.groundRing(at, p.value() * 0.3f, p.value() * 1.1f, VIOLET, 0.8f, 10);
                Pulse.ground(at, p.value() * 0.9f, VIOLET, 0.35f, Math.max(10, p.ticks()));
            }
            case AccessoryFxPayload.BLACK_HOLE -> {
                int life = Math.max(10, p.ticks());
                Pulse.darkCore(at, 0.75f, life);
                Pulse.ring(at, 0.9f, 1.1f, WHITE_GOLD, 0.8f, life);
                Pulse.ring(at, 2.6f, 0.9f, GOLD, 0.6f, 10);
                Pulse.groundRing(at.subtract(0, 0.9, 0), p.value() * 1.1f, p.value() * 0.9f, VIOLET, 0.6f, life);
            }
            case AccessoryFxPayload.SLOW -> {
                Pulse.groundRing(at, 0.5f, p.value() * 1.1f, SAND, 1.0f, 12);
                Pulse.groundRing(at, 0.3f, p.value() * 0.6f, WHITE_GOLD, 0.7f, 9);
                Pulse.flash(at.add(0, 1.0, 0), 1.4f, SAND, 0.6f, 6);
                burst(level, at.add(0, 1.0, 0), 16, 0.25, SAND, 16);
            }
            case AccessoryFxPayload.SHARD_BROKEN -> {
                Pulse.flash(at, 0.8f, SHARD, 0.9f, 5);
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(8, at, true); i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextDouble() * 1.2, RANDOM.nextGaussian()).normalize().scale(0.18);
                    FxBudget.spawn(FxParticles.shard(level, at).velocity(v).color(SHARD).size(0.07f, 0.03f).life(14 + RANDOM.nextInt(6)));
                }
            }
            case AccessoryFxPayload.SHARD_CUT -> burst(level, at, 6, 0.3, SHARD, 6);
            case AccessoryFxPayload.SHARD_REGROWN -> {
                Pulse.flash(at, 0.7f, SHARD, 0.6f, 6);
                if (FxParticles.ready()) {
                    FxBudget.spawn(FxParticles.glint(level, at).color(SHARD).size(0.25f, 0.05f).life(10));
                }
            }
            case AccessoryFxPayload.LEECH -> {
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(12, at, true); i++) {
                    double a = i * Mth.TWO_PI / 12.0;
                    Vec3 from = at.add(Math.cos(a) * 1.3, (RANDOM.nextDouble() - 0.4) * 1.2, Math.sin(a) * 1.3);
                    Vec3 v = at.subtract(from).scale(1.0 / 9.0);
                    FxBudget.spawn(FxParticles.glint(level, from).velocity(v).color(CRIMSON).size(0.12f, 0.04f).life(9));
                }
                Pulse.flash(at, 0.9f, CRIMSON, 0.5f, 8);
            }
            default -> {
            }
        }
    }

    /** Sparks flung out from {@code at}. */
    private static void burst(ClientLevel level, Vec3 at, int wanted, double speed, int color, int life) {
        if (!FxParticles.ready()) {
            return;
        }
        for (int i = 0; i < FxBudget.count(wanted, at, true); i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian()).normalize()
                    .scale(speed * (0.6 + RANDOM.nextDouble() * 0.6));
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(color).size(0.05f, 0.015f).life(life + RANDOM.nextInt(5))
                    .drag(0.86f));
        }
    }
}
