package com.cosmicbreach.client.structure;

import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.array.PuzzleBlock;
import com.cosmicbreach.structure.lens.Lens;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Lens Array's light, drawn from its core: the sunbeam falling from the aperture to the focus, every segment
 * the server traced (like beacon beams, in their colours), brighter and wider while every receptor is lit and
 * the vault's 60 ticks run, and the hint's ghost: the right mirror, glowing gold where it belongs.
 */
public class LensBeamRenderer implements BlockEntityRenderer<LensCoreBlockEntity> {
    private static final float CORE = 0.09f;
    private static final float GLOW = 0.17f;

    @Override
    public void render(LensCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (core.getLevel() == null || core.phase() == LensCoreBlockEntity.DORMANT || core.source() < 0) {
            return;
        }
        long now = core.getLevel().getGameTime();
        float time = now + partialTick;
        BlockPos base = core.getBlockPos();
        int n = core.size();
        boolean solved = core.phase() == LensCoreBlockEntity.SOLVED;
        float hold = core.holdStart() >= 0 && !solved
                ? Mth.clamp((now - core.holdStart() + partialTick) / LensCoreBlockEntity.HOLD_TICKS, 0.0f, 1.0f) : 0.0f;
        float glow = GLOW + hold * 0.12f + (solved ? 0.05f : 0.0f);
        int glowAlpha = 32 + (int) (hold * 60) + (solved ? 24 : 0);

        // the sunbeam from the aperture down onto the focus
        int src = core.source();
        Vec3 focus = rel(core, base, src % n, src / n);
        Vec3 aperture = new Vec3(focus.x, 1 + core.ceiling(), focus.z);
        Beams.draw(pose, buffers, aperture, focus, Beams.rgb(Lens.WHITE), 0.12f, 0.24f + hold * 0.1f, 40 + glowAlpha / 2, time);

        int[] seg = core.segments();
        for (int i = 0; i + 5 < seg.length; i += 6) {
            Vec3 from = rel(core, base, seg[i], seg[i + 1]);
            Vec3 to = rel(core, base, seg[i + 2], seg[i + 3]);
            Beams.draw(pose, buffers, from, to, Beams.rgb(seg[i + 4]), CORE, glow, glowAlpha, time);
        }

        // each receptor shows the colour it wants: a short stub of light, tall and bright once lit
        int[] ports = core.ports();
        for (int p = 0; p < ports.length; p++) {
            if (!Lens.isReceptor(ports[p])) {
                continue;
            }
            boolean lit = (core.litMask() & 1 << p) != 0 || solved;
            Vec3 at = rel(core, base, Lens.portX(n, p), Lens.portZ(n, p)).add(0, 0.3, 0);
            int rgb = Beams.rgb(Lens.receptorColor(ports[p]));
            if (lit) {
                Beams.draw(pose, buffers, at, at.add(0, 3.2, 0), rgb, 0.07f, 0.2f, 90 + glowAlpha, time);
            } else {
                Beams.draw(pose, buffers, at, at.add(0, 1.1, 0), rgb, 0.035f, 0.1f, 36, time);
            }
        }

        int hint = core.hintCell();
        if (hint >= 0 && !solved) {
            ghost(core, base, pose, buffers, hint, core.clientHintPiece(), time);
        }
    }

    /** A grid point relative to the core's block. */
    private static Vec3 rel(LensCoreBlockEntity core, BlockPos base, int gx, int gz) {
        return core.gridPoint(gx, gz).subtract(base.getX(), base.getY(), base.getZ());
    }

    /**
     * The hint: a gold light standing on pedestal {@code cell} like a small beacon, and a pulsing gold ghost of the
     * right piece there (for a mirror, its plate at the turn it should have).
     */
    private static void ghost(LensCoreBlockEntity core, BlockPos base, PoseStack pose, MultiBufferSource buffers, int cell, int piece,
            float time) {
        int n = core.size();
        Vec3 c = rel(core, base, cell % n, cell / n);
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * 0.25));
        int alpha = 120 + (int) (100 * pulse);
        int argb = alpha << 24 | 0xFFC83C;
        if (Lens.kind(piece) == Lens.MIRROR) {
            double[][] dirs = {{1, 0}, {Math.sqrt(0.5), Math.sqrt(0.5)}, {0, 1}, {Math.sqrt(0.5), -Math.sqrt(0.5)}};
            double[] d = dirs[Lens.turn(piece)];
            double half = 0.6;
            double lo = -PuzzleBlock.BEAM_HEIGHT + 0.45;
            double hi = 1.0 - PuzzleBlock.BEAM_HEIGHT + 0.1;
            Vec3 a = c.add(-d[0] * half, lo, -d[1] * half);
            Vec3 b = c.add(d[0] * half, lo, d[1] * half);
            Beams.quad(pose, buffers, a, b, b.add(0, hi - lo, 0), a.add(0, hi - lo, 0), argb);
        }
        Beams.draw(pose, buffers, c.add(0, -PuzzleBlock.BEAM_HEIGHT + 0.5, 0), c.add(0, 3.0, 0), 0xFFC83C, 0.05f + 0.02f * pulse, 0.22f,
                60 + (int) (60 * pulse), time);
    }

    @Override
    public boolean shouldRenderOffScreen(LensCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(LensCoreBlockEntity core) {
        int reach = LensCoreBlockEntity.SPACING * (core.size() / 2 + 2);
        BlockPos p = core.getBlockPos();
        return new AABB(p.getX() - reach, p.getY(), p.getZ() - reach, p.getX() + reach + 1, p.getY() + core.ceiling() + 3, p.getZ() + reach + 1);
    }
}
