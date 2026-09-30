package com.cosmicbreach.client.entity;

import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.entity.shardling.ShardFragment;
import com.cosmicbreach.entity.shardling.ShardNeedle;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.shardling.ShardlingEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The Shardling's effects, in the telegraph language (GDD section 4.1): gold glints rippling over the
 * spine tips for the Splinter Lunge (parryable; {@link ShardlingRenderer} also turns the crest itself
 * gold), a red glint at the throat for the Shard Spit (dodge; the eyes and jaw turn red); streaks
 * behind a leap, a crystal burst on death, and the needles' and shards' sparks. No smoke. Particle
 * counts go through {@link FxBudget}; glints and flashes are {@link WorldFx} shapes. Client thread.
 */
final class ShardlingFx implements ShardlingEffects.Handler {
    static final int GOLD = 0xFFC23A;
    static final int PALE_GOLD = 0xFFE7A6;
    static final int RED = 0xFF3526;
    static final int CRYSTAL = 0x48DCCF;
    static final int PALE = 0xC4F8F0;
    static final int WHITE = 0xFFFFFF;

    private final RandomSource random = RandomSource.create();
    /** Per Shardling: the phase count last seen and the tick it changed, so a phase knows its first tick and age. */
    private final Map<Shardling, Seen> seen = new WeakHashMap<>();

    private record Seen(int count, int since) {
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    // ------------------------------------------------------------------ telegraphs

    @Override
    public void shardlingTick(Shardling shardling) {
        if (!ready()) {
            return;
        }
        int count = shardling.phaseCount();
        Seen last = seen.get(shardling);
        boolean began = last == null || last.count() != count;
        if (began) {
            last = new Seen(count, shardling.tickCount);
            seen.put(shardling, last);
        }
        int age = shardling.tickCount - last.since();
        switch (shardling.phase()) {
            case TELL -> goldSpines(shardling, began, age);
            case SPIT_TELL -> redThroat(shardling, began, age);
            case LUNGE -> {
                if (began) {
                    leapStreaks(shardling);
                }
            }
            case STAGGER -> {
                if (began) {
                    reel(shardling);
                }
            }
            default -> {
            }
        }
    }

    /**
     * The lunge's telegraph: all six spine tips flash gold as it crouches, then glints ripple down the
     * crest, two a tick, for the whole 12 ticks.
     */
    private void goldSpines(Shardling shardling, boolean began, int age) {
        if (ShardlingPoints.of(shardling) == null) {
            return; // not being drawn: nobody to see it
        }
        if (began) {
            for (int i = 0; i < ShardlingPoints.SPINES.length; i++) {
                WorldFx.add(new ShardFx.Glint(spineTip(shardling, i), GOLD, i == 1 ? 0.3f : 0.26f, 7, 0.08f));
            }
            Vec3 crest = crest(shardling);
            if (crest != null) {
                WorldFx.add(new ShardFx.Flash(crest, ShardDraw.GLOW, false, true, 0.5f, 0.9f, PALE_GOLD, 0.7f, 6));
            }
            return;
        }
        for (int k = 0; k < 2; k++) {
            int i = (age * 2 + k) % ShardlingPoints.SPINES.length;
            WorldFx.add(new ShardFx.Glint(spineTip(shardling, i), random.nextFloat() < 0.5f ? GOLD : PALE_GOLD,
                    0.17f + random.nextFloat() * 0.06f, 4, 0.12f));
        }
    }

    /** The spit's telegraph: a red glint at the throat every tick, growing through the 10 ticks. */
    private void redThroat(Shardling shardling, boolean began, int age) {
        ShardlingPoints.Points points = ShardlingPoints.of(shardling);
        if (points == null) {
            return;
        }
        if (began) {
            WorldFx.add(new ShardFx.Flash(points.throat(), ShardDraw.GLOW, false, false, 0.25f, 0.45f, RED, 0.55f, 8));
        }
        WorldFx.add(new ShardFx.Glint(() -> {
            ShardlingPoints.Points p = ShardlingPoints.of(shardling);
            return p == null ? null : p.throat();
        }, RED, 0.14f + 0.02f * Math.min(age, 10), 3, 0.15f));
    }

    private static Supplier<Vec3> spineTip(Shardling shardling, int index) {
        return () -> {
            ShardlingPoints.Points p = ShardlingPoints.of(shardling);
            return p == null ? null : p.spineTips()[index];
        };
    }

    private static Vec3 crest(Shardling shardling) {
        ShardlingPoints.Points p = ShardlingPoints.of(shardling);
        if (p == null) {
            return null;
        }
        Vec3 sum = Vec3.ZERO;
        for (Vec3 tip : p.spineTips()) {
            sum = sum.add(tip);
        }
        return sum.scale(1.0 / p.spineTips().length);
    }

    /** The leap: thin streaks left hanging along its path for its 4 active ticks. */
    private void leapStreaks(Shardling shardling) {
        Vec3[] last = {shardling.position()};
        WorldFx.follow(shardling, 5, e -> {
            Vec3 now = e.position();
            Vec3 moved = now.subtract(last[0]);
            last[0] = now;
            if (moved.lengthSqr() < 0.01) {
                return;
            }
            Vec3 dir = moved.normalize();
            Vec3 across = new Vec3(-dir.z, 0, dir.x);
            int streaks = FxBudget.count(3, now, false);
            for (int i = 0; i < streaks; i++) {
                Vec3 at = now.add(0, 0.15 + random.nextDouble() * 0.6, 0).add(across.scale((random.nextDouble() - 0.5) * 0.5))
                        .subtract(dir.scale(random.nextDouble() * 0.5));
                FxBudget.spawn(FxParticles.spark(level(), at).streakAlong(dir, (float) (0.7 + random.nextDouble() * 0.5))
                        .velocity(dir.scale(0.03)).size(0.035f, 0.008f).life(5 + random.nextInt(3)).color(PALE));
            }
        });
    }

    /** A stagger: a few white glints knocked off round the head. */
    private void reel(Shardling shardling) {
        Vec3 head = shardling.position().add(0, shardling.getBbHeight() * 0.8, 0);
        int glints = FxBudget.count(5, head, false);
        for (int i = 0; i < glints; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), 0.6 + random.nextDouble(), random.nextGaussian()).normalize();
            FxBudget.spawn(FxParticles.glint(level(), head.add(out.scale(0.2))).velocity(out.scale(0.08)).drag(0.85f)
                    .size(0.12f, 0.02f).life(8 + random.nextInt(4)).color(WHITE).spin(0.2f));
        }
    }

    // ------------------------------------------------------------------ death, needles, shards

    /** It shatters: crystal shards thrown out, a white flash, a few glints. */
    @Override
    public void shardlingDied(Shardling shardling) {
        if (!ready()) {
            return;
        }
        Vec3 centre = shardling.position().add(0, shardling.getBbHeight() * 0.5, 0);
        shards(centre, 14, 0.22, 0.1f);
        WorldFx.add(new ShardFx.Flash(centre, ShardDraw.GLOW, false, true, 0.6f, 1.2f, PALE, 0.9f, 7));
        int glints = FxBudget.count(6, centre, true);
        for (int i = 0; i < glints; i++) {
            Vec3 out = randomUnit();
            FxBudget.spawn(FxParticles.glint(level(), centre.add(out.scale(0.2))).velocity(out.scale(0.14)).drag(0.8f)
                    .size(0.16f, 0.02f).life(8 + random.nextInt(4)).color(PALE).spin(0.2f));
        }
    }

    @Override
    public void needleTick(ShardNeedle needle) {
        if (!ready()) {
            return;
        }
        Vec3 v = needle.getDeltaMovement();
        if (v.lengthSqr() < 1e-4) {
            return;
        }
        Vec3 dir = v.normalize();
        Vec3 at = needle.position().add(0, needle.getBbHeight() * 0.5, 0).subtract(dir.scale(0.4));
        if (FxBudget.count(1, at, false) > 0) {
            FxBudget.spawn(FxParticles.spark(level(), at).streakAlong(dir, 0.6f).velocity(dir.scale(0.04))
                    .size(0.03f, 0.005f).life(4).color(PALE));
        }
    }

    @Override
    public void needleImpact(ShardNeedle needle) {
        if (!ready()) {
            return;
        }
        Vec3 at = needle.position().add(0, needle.getBbHeight() * 0.5, 0);
        int sparks = FxBudget.count(6, at, false);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = randomUnit().scale(0.12 + random.nextDouble() * 0.12);
            FxBudget.spawn(FxParticles.spark(level(), at).velocity(v).size(0.025f, 0.008f).life(5 + random.nextInt(3))
                    .gravity(0.4f).drag(0.85f).color(random.nextBoolean() ? PALE : CRYSTAL).streak(1.6f, 0.08f));
        }
        shards(at, 3, 0.1, 0.07f);
    }

    /** A shard bursts: crystal pieces, a flash, and a red ring swept flat over the ground it hit. */
    @Override
    public void fragmentBurst(ShardFragment fragment) {
        if (!ready()) {
            return;
        }
        Vec3 centre = fragment.position().add(0, 0.25, 0);
        shards(centre, 10, 0.2, 0.08f);
        WorldFx.add(new ShardFx.Flash(centre, ShardDraw.GLOW, false, true, 0.5f, 1.1f, PALE, 0.9f, 6));
        WorldFx.add(new ShardFx.Flash(fragment.position().add(0, 0.05, 0), ShardDraw.RING, true, false,
                0.4f, (float) ShardFragment.RADIUS * 1.15f, ShardFragmentRenderer.DANGER, 0.9f, 8));
    }

    private void shards(Vec3 centre, int wanted, double speed, float size) {
        int shards = FxBudget.count(wanted, centre, true);
        for (int i = 0; i < shards; i++) {
            Vec3 out = randomUnit();
            Vec3 v = new Vec3(out.x, Math.abs(out.y) * 0.8 + 0.3, out.z).normalize().scale(speed * (0.6 + random.nextDouble() * 0.6));
            FxBudget.spawn(FxParticles.shard(level(), centre.add(out.scale(0.15))).velocity(v)
                    .color(random.nextFloat() < 0.6f ? CRYSTAL : PALE).size(size, size * 0.6f).life(16 + random.nextInt(10))
                    .gravity(1.0f).drag(0.96f).spin(0.3f));
        }
    }

    private Vec3 randomUnit() {
        Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }
}
