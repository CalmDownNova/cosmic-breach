package com.cosmicbreach.familiar;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.Scorch;
import com.cosmicbreach.world.weather.SolarFlare;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Emberwisp (GDD 8.2): a small sun-sprite orbiting its owner ({@link FamiliarPaths#wispOrbit}). Every
 * {@value FamiliarRules#SCORCH_EVERY} ticks it throws Scorch (4 s) on the enemy its owner hit most recently; its
 * owner's perfect dodges and parries give them Kindled ({@link FamiliarCombat}). It darts out for its own hits.
 */
public class Emberwisp extends FamiliarEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
    private static final RawAnimation FLARE = RawAnimation.begin().thenPlay("flare");

    private final Cadence scorch = new Cadence(FamiliarRules.SCORCH_EVERY);

    public Emberwisp(EntityType<? extends Emberwisp> type, Level level) {
        super(type, level);
    }

    @Override
    public FamiliarKind kind() {
        return FamiliarKind.EMBERWISP;
    }

    @Override
    protected Vec3 home(Player owner, double time) {
        return owner.position().add(FamiliarPaths.wispOrbit(time, owner.getYRot()));
    }

    @Override
    protected void steer(ServerPlayer owner, @Nullable LivingEntity target, long now) {
        boolean dart = darting(target, now);
        Vec3 goal = dart ? strikePoint(target) : home(owner, now + phase).subtract(0, getBbHeight() * 0.5, 0);
        flyTo(goal, dart ? 0.55 : 0.5);
        faceToward(dart ? target.position() : owner.position().add(FamiliarPaths.facing(owner.getYRot()).scale(4)), 0.3f);
    }

    @Override
    protected void special(ServerPlayer owner, @Nullable LivingEntity target, FamiliarMode mode, long now) {
        if (!mode.fights() || !scorch.ready(now)) {
            return;
        }
        LivingEntity victim = FamiliarSessions.ownerTarget(owner, this, now);
        if (victim == null || victim.getType().is(SolarFlare.SOLAR_IMMUNE) || victim.fireImmune()) {
            return;
        }
        victim.addEffect(new MobEffectInstance(GearRegistry.SCORCH, Scorch.TICKS, 0, false, true, true), owner);
        scorch.fire(now);
        level().playSound(null, getX(), getY(), getZ(), FamiliarRegistry.SCORCH.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        FamiliarNet.fx(this, FamiliarFxPayload.SCORCH, getId(), victim.getId(), victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0);
        act(ACT_FLARE);
    }

    /** When it may next throw Scorch (for checks): ticks left. */
    public int scorchLeft(long now) {
        return scorch.left(now);
    }

    /** When it last threw Scorch, or {@link Long#MIN_VALUE}. */
    public long lastScorch() {
        return scorch.last();
    }

    @Override
    protected SoundEvent strikeSound() {
        return FamiliarRegistry.WISP_STRIKE.get();
    }

    @Override
    protected RawAnimation idle() {
        return IDLE;
    }

    @Override
    protected @Nullable RawAnimation action(int act) {
        return act == ACT_STRIKE ? STRIKE : act == ACT_FLARE ? FLARE : null;
    }

    @Override
    protected int actionTicks(int act) {
        return act == ACT_FLARE ? 10 : 7;
    }

    /** Small and always near its owner's eyes: clicks go through it. */
    @Override
    public boolean isPickable() {
        return false;
    }
}
