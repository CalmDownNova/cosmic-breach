package com.cosmicbreach.familiar;

import com.cosmicbreach.status.Statuses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Prism Moth (GDD 8.2): a glass-winged moth looping over its owner's shoulders ({@link FamiliarPaths#mothLoop}).
 * Every {@value FamiliarRules#REFRACT_EVERY} ticks it throws a rainbow glint that puts a Refract stack on its owner's
 * target (up to three; an ability consumes them for +30%). Every {@value FamiliarRules#CLEANSE_EVERY} ticks it takes one
 * negative status off its owner (Rift first), waiting, ready, until there is one.
 */
public class PrismMoth extends FamiliarEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
    private static final RawAnimation GLINT = RawAnimation.begin().thenPlay("glint");

    private final Cadence refract = new Cadence(FamiliarRules.REFRACT_EVERY);
    private final Cadence cleanse = new Cadence(FamiliarRules.CLEANSE_EVERY);

    public PrismMoth(EntityType<? extends PrismMoth> type, Level level) {
        super(type, level);
    }

    @Override
    public FamiliarKind kind() {
        return FamiliarKind.PRISM_MOTH;
    }

    @Override
    protected Vec3 home(Player owner, double time) {
        return owner.position().add(FamiliarPaths.mothLoop(time, owner.getYRot()));
    }

    @Override
    protected void steer(ServerPlayer owner, @Nullable LivingEntity target, long now) {
        boolean dart = darting(target, now);
        Vec3 goal = dart ? strikePoint(target) : home(owner, now + phase).subtract(0, getBbHeight() * 0.5, 0);
        flyTo(goal, dart ? 0.5 : 0.3);
        Vec3 v = getDeltaMovement();
        faceToward(dart ? target.position() : v.horizontalDistanceSqr() > 1e-4 ? position().add(v.scale(10)) : owner.getEyePosition(), 0.25f);
    }

    @Override
    protected void special(ServerPlayer owner, @Nullable LivingEntity target, FamiliarMode mode, long now) {
        if (mode.fights() && refract.ready(now)) {
            LivingEntity victim = FamiliarSessions.ownerTarget(owner, this, now);
            if (victim != null) {
                int stacks = Refract.addStack(victim);
                refract.fire(now);
                level().playSound(null, getX(), getY(), getZ(), FamiliarRegistry.GLINT.get(), SoundSource.PLAYERS, 0.8f, 0.9f + 0.1f * stacks);
                Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                FamiliarNet.fx(this, FamiliarFxPayload.GLINT, getId(), victim.getId(), at, stacks);
                FamiliarNet.fx(victim, FamiliarFxPayload.REFRACT, victim.getId(), -1, at, stacks);
                act(ACT_GLINT);
            }
        }
        if (cleanse.ready(now) && cleanse(owner)) {
            cleanse.fire(now);
            level().playSound(null, owner.getX(), owner.getY() + 1.0, owner.getZ(), FamiliarRegistry.CLEANSE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            FamiliarNet.fx(owner, FamiliarFxPayload.CLEANSE, owner.getId(), getId(), owner.position(), 0);
            act(ACT_GLINT);
        }
    }

    /** Takes one negative status off {@code owner} (Rift first, else the longest); false if there was none. */
    private static boolean cleanse(ServerPlayer owner) {
        List<Holder<MobEffect>> effects = new ArrayList<>();
        List<FamiliarRules.Status> statuses = new ArrayList<>();
        for (MobEffectInstance e : owner.getActiveEffects()) {
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                effects.add(e.getEffect());
                statuses.add(new FamiliarRules.Status(e.getEffect().is(Statuses.RIFT.getKey()), e.isInfiniteDuration() ? -1 : e.getDuration()));
            }
        }
        int pick = FamiliarRules.pickCleanse(statuses);
        return pick >= 0 && owner.removeEffect(effects.get(pick));
    }

    /** Ticks until its next cleanse may go (for checks). */
    public int cleanseLeft(long now) {
        return cleanse.left(now);
    }

    /** Ticks until its next glint (for checks). */
    public int refractLeft(long now) {
        return refract.left(now);
    }

    @Override
    protected SoundEvent strikeSound() {
        return FamiliarRegistry.MOTH_STRIKE.get();
    }

    @Override
    protected RawAnimation idle() {
        return IDLE;
    }

    @Override
    protected @Nullable RawAnimation action(int act) {
        return act == ACT_STRIKE ? STRIKE : act == ACT_GLINT ? GLINT : null;
    }

    @Override
    protected int actionTicks(int act) {
        return act == ACT_GLINT ? 10 : 7;
    }

    /** Small, and over its owner's shoulders: clicks go through it. */
    @Override
    public boolean isPickable() {
        return false;
    }
}
