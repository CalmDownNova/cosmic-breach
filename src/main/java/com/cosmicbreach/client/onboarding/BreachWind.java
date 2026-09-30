package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.onboarding.BreachBlock;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wind rising out of open Breaches: one seamless loop ({@code onboarding/breach_wind}, a recorded draught, a
 * 7.75 s loop the sound engine repeats without a gap) per Breach the player is near. It starts when they come
 * within {@link #START} blocks of the opening's middle (fading in over {@link #FADE_TICKS}), follows the normal
 * 16 block fall-off as they move, and fades out and stops once they are past {@link #REACH}, the ring closes or
 * the world changes. Nothing plays with the master or ambient volume at zero.
 */
public final class BreachWind {
    public static final double START = 16.0;
    public static final double REACH = 20.0;
    public static final int FADE_TICKS = 20;

    private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();
    private static int started;

    private BreachWind() {
    }

    /** Every client tick of an open Breach's north-west quarter (the ring's origin). */
    static void near(Level level, BlockPos origin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0f
                || mc.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0f) {
            return;             // nothing would be heard, and the engine would drop each try at once
        }
        Loop loop = LOOPS.get(origin);
        if (loop != null && !loop.isStopped() && mc.getSoundManager().isActive(loop)) {
            return;
        }
        if (mc.player.distanceToSqr(origin.getX() + 1.0, origin.getY() + 0.5, origin.getZ() + 1.0) > START * START) {
            return;
        }
        loop = new Loop(level, origin.immutable());
        LOOPS.put(loop.origin, loop);
        started++;
        mc.getSoundManager().play(loop);
    }

    /** Loops playing now (checks). */
    public static int playing() {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        for (Iterator<Loop> it = LOOPS.values().iterator(); it.hasNext(); ) {
            Loop loop = it.next();
            if (loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
                it.remove();
            } else {
                n++;
            }
        }
        return n;
    }

    /** The loop of the ring at {@code origin}, if one is playing (checks). */
    public static SoundInstance loopAt(BlockPos origin) {
        Loop loop = LOOPS.get(origin);
        return loop != null && !loop.isStopped() && Minecraft.getInstance().getSoundManager().isActive(loop) ? loop : null;
    }

    /** How many loops have started since the game started (checks: a loop starts once, not every few seconds). */
    public static int started() {
        return started;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final Level level;
        final BlockPos origin;

        Loop(Level level, BlockPos origin) {
            super(OnboardingRegistry.BREACH_WIND.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.level = level;
            this.origin = origin;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0f;
            this.x = origin.getX() + 1.0;
            this.y = origin.getY() + 0.5;
            this.z = origin.getZ() + 1.0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            boolean stay = mc.level == level && mc.player != null && open(level.getBlockState(origin))
                    && mc.player.distanceToSqr(x, y, z) <= REACH * REACH;
            volume = Mth.clamp(volume + (stay ? 1.0f : -1.0f) / FADE_TICKS, 0.0f, 1.0f);
            if (!stay && volume <= 0.0f) {
                stop();
            }
        }

        private static boolean open(BlockState state) {
            return state.is(OnboardingRegistry.BREACH.get()) && (!state.hasProperty(BreachBlock.QUARTER)
                    || state.getValue(BreachBlock.QUARTER) == BreachBlock.Quarter.NORTH_WEST);
        }
    }
}
