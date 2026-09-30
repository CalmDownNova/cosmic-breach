package com.cosmicbreach.onboarding;

import com.cosmicbreach.registry.ModBlocks;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breach Rings in the world (GDD 1.3): a shard used on a frame opens its ring ({@link #useShard}) or answers
 * with an action bar hint that says what is wrong; {@link #open} lights the 12 frames and turns the 2 by 2
 * middle into the Breach; a lit frame broken, or any quarter of the Breach removed, closes all four quarters
 * and darkens the frames ({@link #close}). The shape rule itself is {@link BreachRing}; rings are named by
 * their origin, the middle's north-west block.
 */
public final class BreachRings {
    /** Opened rings (for checks and the scenario's log). */
    private static int opened;
    /** A hint names at most this many places (so it fits the action bar). */
    private static final int MAX_PLACES = 3;
    /** Set while a ring is closing, so the quarters it removes don't each start closing it again. */
    private static boolean closing;

    private BreachRings() {
    }

    /** {@link BreachRing}'s view of a level. */
    public static BreachRing.View view(Level level) {
        return new BreachRing.View() {
            private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

            @Override
            public boolean frame(int x, int y, int z) {
                return level.getBlockState(p.set(x, y, z)).is(ModBlocks.BREACH_FRAME.get());
            }

            @Override
            public boolean open(int x, int y, int z) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(OnboardingRegistry.BREACH.get()));
            }

            @Override
            public boolean breach(int x, int y, int z) {
                return level.getBlockState(p.set(x, y, z)).is(OnboardingRegistry.BREACH.get());
            }
        };
    }

    /**
     * {@code player} used {@code shard} on the frame at {@code frame}: opens its ring (the shard is used up,
     * unless in creative) or shows what is wrong. True if a ring opened.
     */
    public static boolean useShard(ServerPlayer player, ServerLevel level, BlockPos frame, ItemStack shard) {
        BreachRing.Result r = BreachRing.find(view(level), level.dimension() == Level.OVERWORLD, frame.getX(), frame.getY(), frame.getZ());
        if (!r.ok()) {
            player.displayClientMessage(hint(r), true);
            return false;
        }
        BlockPos origin = new BlockPos(r.x(), r.y(), r.z());
        open(level, origin);
        shard.consume(1, player);
        level.playSound(null, origin.getX() + 1.0, origin.getY() + 0.5, origin.getZ() + 1.0, OnboardingRegistry.RING_ACTIVATE.get(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, origin.getX() + 1.0, origin.getY() + 0.6, origin.getZ() + 1.0, 60, 1.4, 0.3, 1.4, 0.08);
        player.displayClientMessage(Component.translatable("cosmicbreach.breach.opened"), true);
        com.cosmicbreach.voice.Echo.say(player, com.cosmicbreach.voice.EchoLine.RING_OPEN);
        return true;
    }

    /** Opens the ring whose origin is {@code origin}: its frames lit, the four quarters of the Breach in the middle. */
    public static void open(Level level, BlockPos origin) {
        for (int[] o : BreachRing.RING) {
            BlockPos p = origin.offset(o[0], 0, o[1]);
            BlockState s = level.getBlockState(p);
            if (s.is(ModBlocks.BREACH_FRAME.get())) {
                level.setBlock(p, s.setValue(BreachFrameBlock.ACTIVE, true), 3);
            }
        }
        for (int[] m : BreachRing.MIDDLE) {
            level.setBlock(origin.offset(m[0], 0, m[1]), OnboardingRegistry.BREACH.get().defaultBlockState()
                    .setValue(BreachBlock.QUARTER, BreachBlock.Quarter.at(m[0], m[1])), 3);
        }
        opened++;
    }

    /** The open ring whose origin is {@code origin} closes: all four quarters go, the frames go dark. */
    static void close(Level level, BlockPos origin) {
        if (closing) {
            return;
        }
        closing = true;
        try {
            boolean any = false;
            for (int[] m : BreachRing.MIDDLE) {
                BlockPos p = origin.offset(m[0], 0, m[1]);
                if (level.getBlockState(p).is(OnboardingRegistry.BREACH.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    any = true;
                }
            }
            for (int[] o : BreachRing.RING) {
                BlockPos p = origin.offset(o[0], 0, o[1]);
                BlockState s = level.getBlockState(p);
                if (s.is(ModBlocks.BREACH_FRAME.get()) && s.getValue(BreachFrameBlock.ACTIVE)) {
                    level.setBlock(p, s.setValue(BreachFrameBlock.ACTIVE, false), 3);
                }
            }
            if (any) {
                level.playSound(null, origin.getX() + 1.0, origin.getY() + 0.5, origin.getZ() + 1.0, OnboardingRegistry.RING_ACTIVATE.get(),
                        SoundSource.BLOCKS, 0.8f, 0.5f);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CLOUD, origin.getX() + 1.0, origin.getY() + 0.5, origin.getZ() + 1.0, 20, 0.8, 0.2, 0.8, 0.02);
                }
            }
        } finally {
            closing = false;
        }
    }

    /** A lit frame at {@code frame} is gone: the open ring it belonged to closes. */
    static void closeAround(Level level, BlockPos frame) {
        for (int[] o : BreachRing.RING) {
            BlockPos origin = frame.offset(-o[0], 0, -o[1]);
            BlockState s = level.getBlockState(origin);
            // an open ring's origin holds the Breach's north-west quarter
            if (s.is(OnboardingRegistry.BREACH.get()) && s.getValue(BreachBlock.QUARTER) == BreachBlock.Quarter.NORTH_WEST) {
                close(level, origin);
            }
        }
    }

    /** True if at least 8 of the 12 frames of the ring at {@code origin} still stand (the ring is still there). */
    public static boolean stands(Level level, BlockPos origin) {
        int frames = 0;
        for (int[] o : BreachRing.RING) {
            if (level.getBlockState(origin.offset(o[0], 0, o[1])).is(ModBlocks.BREACH_FRAME.get())) {
                frames++;
            }
        }
        return frames >= 8;
    }

    /** The action bar hint for a ring that did not open. */
    public static Component hint(BreachRing.Result r) {
        return switch (r.problem()) {
            case NONE -> Component.translatable("cosmicbreach.breach.opened");
            case NOT_OVERWORLD -> Component.translatable("cosmicbreach.breach.hint.not_overworld");
            case ALREADY_OPEN -> Component.translatable("cosmicbreach.breach.hint.open");
            case CENTRE_BLOCKED -> Component.translatable("cosmicbreach.breach.hint.centre");
            case NO_HEADROOM -> Component.translatable("cosmicbreach.breach.hint.headroom");
            case MISSING_FRAMES -> missing(r);
        };
    }

    /**
     * "Missing 3 of the ring's 12 frames: the north-east corner, two on the west side." Corners by name, side
     * frames counted per side, at most three places (more, and the hint points to the Codex); a frame one block
     * too high or low is named instead.
     */
    private static Component missing(BreachRing.Result r) {
        if (!r.shifted().isEmpty()) {
            int[] s = r.shifted().get(0);
            return Component.translatable(s[2] > 0 ? "cosmicbreach.breach.hint.high" : "cosmicbreach.breach.hint.low", place(s[0], s[1]));
        }
        MutableComponent places = Component.empty();
        Map<BreachRing.Edge, Integer> sides = new EnumMap<>(BreachRing.Edge.class);
        int parts = 0;
        for (int[] m : r.missing()) {
            if (BreachRing.corner(m[0], m[1])) {
                places.append(parts++ > 0 ? ", " : "").append(place(m[0], m[1]));
            } else {
                sides.merge(BreachRing.edge(m[0], m[1]), 1, Integer::sum);
            }
        }
        for (Map.Entry<BreachRing.Edge, Integer> e : sides.entrySet()) {
            Component side = Component.translatable("cosmicbreach.breach.place." + e.getKey().name().toLowerCase(java.util.Locale.ROOT));
            places.append(parts++ > 0 ? ", " : "")
                    .append(Component.translatable(e.getValue() > 1 ? "cosmicbreach.breach.missing.both" : "cosmicbreach.breach.missing.one", side));
        }
        if (parts > MAX_PLACES) {
            return Component.translatable("cosmicbreach.breach.hint.missing_many", r.missing().size());
        }
        return Component.translatable("cosmicbreach.breach.hint.missing", r.missing().size(), places);
    }

    /** "the north-west corner", "the east side" (translated), for a ring offset. */
    public static Component place(int dx, int dz) {
        String ns = dz < 0 ? "north" : dz > 1 ? "south" : "";
        String ew = dx < 0 ? "west" : dx > 1 ? "east" : "";
        String key = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "_" + ew;
        return Component.translatable("cosmicbreach.breach.place." + key);
    }

    /** Rings opened since the game started (checks). */
    public static int openedCount() {
        return opened;
    }
}
