package com.cosmicbreach.client.crypt;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.structure.choir.ChoirPhrase;
import com.cosmicbreach.structure.choir.ChoirRules;
import com.cosmicbreach.structure.choir.ChoirSession;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The Choir Floor as its players see it (GDD 6.3), drawn from the Conductor's synced state and the game time: eight
 * petals round the Conductor, each in its colour with its glyph, faint at rest and flaring as the Conductor sounds
 * them (a ghost note dim, and not at all on a replay) or as a player steps one right; the ring turning a slot in
 * round 3; a metronome ring on the floor and a halo over the Conductor pulsing on Vesper's beat, gold while it
 * listens to itself, teal for the count-in (four dots, one a beat) and the answer, violet while it rests, red for a
 * Discord. All of it reads with the sound off.
 */
public class ConductorRenderer implements BlockEntityRenderer<ConductorBlockEntity> {
    private static final int TEAL = 0x3FF0D0;
    private static final int GOLD = 0xFFE7A3;

    @Override
    public void render(ConductorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        long t = level.getGameTime();
        double now = t + partialTick;
        ChoirSession.Phase phase = be.phase();
        Matrix4f m = pose.last().pose();
        double rot = displayedRotation(be, now);
        VertexConsumer fill = buffers.getBuffer(FxRenderTypes.additive(CryptClient.FILL));
        double sinceDiscord = be.discordAt() < 0 ? 1e9 : now - be.discordAt();
        for (int pad = 0; pad < ChoirRules.PADS; pad++) {
            float lit = litness(be, pad, now, phase);
            float base = phase == ChoirSession.Phase.SOLVED ? 0.32f : 0.13f;
            double angle = Math.toRadians((pad + rot) * ChoirRules.SLOT_DEGREES);
            wedge(fill, m, angle, CryptClient.PAD_RGB[pad], Math.max(base, lit) * 0.6f);
            if (sinceDiscord < 12) {
                wedge(fill, m, angle, 0xFF2030, (float) (0.45 * (1 - sinceDiscord / 12)));
            }
        }
        VertexConsumer glyphs = buffers.getBuffer(FxRenderTypes.additive(CryptClient.GLYPHS));
        for (int pad = 0; pad < ChoirRules.PADS; pad++) {
            float lit = litness(be, pad, now, phase);
            double angle = Math.toRadians((pad + rot) * ChoirRules.SLOT_DEGREES);
            glyph(glyphs, m, pad, angle, CryptClient.PAD_RGB[pad], 0.45f + 0.55f * lit);
        }
        metronome(be, buffers, m, t, partialTick, now, phase, sinceDiscord);
    }

    /** The ring's turn as drawn: the synced rotation, eased in over the beat after it turned. */
    static double displayedRotation(ConductorBlockEntity be, double now) {
        double rot = be.rotation();
        long at = be.rotatedAt();
        if (at != Long.MIN_VALUE && now >= at && now < at + ChoirRules.BEAT) {
            double u = (now - at) / ChoirRules.BEAT;
            rot = rot - 1 + u * u * (3 - 2 * u);
        }
        return rot;
    }

    /** How lit pad {@code pad} is now, 0 to 1: the Conductor sounding it, or a player stepping it right. */
    public static float litness(ConductorBlockEntity be, int pad, double now, ChoirSession.Phase phase) {
        float lit = 0;
        ChoirPhrase p = be.phrase();
        if (p != null && be.callStart() >= 0 && (phase == ChoirSession.Phase.CALL || phase == ChoirSession.Phase.ANSWER)) {
            for (int i = 0; i < p.size(); i++) {
                if (!p.sounds(i, pad)) {
                    continue;
                }
                boolean ghost = i == p.ghost();
                if (ghost && be.replaying()) {
                    continue;
                }
                double start = be.callStart() + p.onsets()[i];
                double len = Math.min(9, i + 1 < p.size() ? p.onsets()[i + 1] - p.onsets()[i] : ChoirRules.BEAT) + 6;
                if (now >= start && now < start + len) {
                    float k = (float) (1 - (now - start) / len);
                    lit = Math.max(lit, ghost ? 0.35f * k : k);
                }
            }
        }
        long[] h = be.hits();
        for (int j = 0; j + 1 < h.length; j += 2) {
            if (h[j] == pad && now >= h[j + 1] && now - h[j + 1] < 10) {
                lit = Math.max(lit, (float) (0.9 * (1 - (now - h[j + 1]) / 10)));
            }
        }
        return lit;
    }

    /** A petal: the band from R_IN to R_OUT round the slot's angle, its seams left dark. */
    private static void wedge(VertexConsumer out, Matrix4f m, double angle, int rgb, float alpha) {
        if (alpha <= 0.003f) {
            return;
        }
        float r = (rgb >> 16 & 255) / 255f;
        float g = (rgb >> 8 & 255) / 255f;
        float b = (rgb & 255) / 255f;
        int radial = 4;
        int around = 6;
        double r0 = ChoirRules.R_IN + 0.08;
        double r1 = ChoirRules.R_OUT - 0.08;
        for (int i = 0; i < radial; i++) {
            double ra = r0 + (r1 - r0) * i / radial;
            double rb = r0 + (r1 - r0) * (i + 1) / radial;
            double ha = half(ra);
            double hb = half(rb);
            for (int j = 0; j < around; j++) {
                double a0 = -ha + 2 * ha * j / around;
                double a1 = -ha + 2 * ha * (j + 1) / around;
                double b0 = -hb + 2 * hb * j / around;
                double b1 = -hb + 2 * hb * (j + 1) / around;
                vertex(out, m, ra, angle + a0, 0.02, 0, 0, r, g, b, alpha);
                vertex(out, m, rb, angle + b0, 0.02, 0, 1, r, g, b, alpha);
                vertex(out, m, rb, angle + b1, 0.02, 1, 1, r, g, b, alpha);
                vertex(out, m, ra, angle + a1, 0.02, 1, 0, r, g, b, alpha);
            }
        }
    }

    /** The petal's half-width at radius {@code r}, radians: the slot's 22.5 degrees less the seam. */
    private static double half(double r) {
        return Math.toRadians(ChoirRules.SLOT_DEGREES / 2) - Math.asin(Math.min(1, (ChoirRules.DIVIDER / 2 + 0.05) / r));
    }

    private static void vertex(VertexConsumer out, Matrix4f m, double radius, double angle, double y, float u, float v,
            float r, float g, float b, float a) {
        out.addVertex(m, (float) (0.5 + Math.cos(angle) * radius), (float) y, (float) (0.5 + Math.sin(angle) * radius))
                .setUv(u, v).setColor(r, g, b, a);
    }

    /** A pad's glyph, flat on its petal at the ring's radius, its top toward the outside. */
    private static void glyph(VertexConsumer out, Matrix4f m, int pad, double angle, int rgb, float alpha) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double cx = 0.5 + c * ChoirRules.RADIUS;
        double cz = 0.5 + s * ChoirRules.RADIUS;
        double h = 0.7;
        float u0 = pad / 8f;
        float u1 = (pad + 1) / 8f;
        float r = Math.min(1f, (rgb >> 16 & 255) / 255f * 0.5f + 0.5f);
        float g = Math.min(1f, (rgb >> 8 & 255) / 255f * 0.5f + 0.5f);
        float b = Math.min(1f, (rgb & 255) / 255f * 0.5f + 0.5f);
        float y = 0.03f;
        // tangent (-s, c), outward (c, s)
        out.addVertex(m, (float) (cx + s * h + c * h), y, (float) (cz - c * h + s * h)).setUv(u0, 0).setColor(r, g, b, alpha);
        out.addVertex(m, (float) (cx - s * h + c * h), y, (float) (cz + c * h + s * h)).setUv(u1, 0).setColor(r, g, b, alpha);
        out.addVertex(m, (float) (cx - s * h - c * h), y, (float) (cz + c * h - s * h)).setUv(u1, 1).setColor(r, g, b, alpha);
        out.addVertex(m, (float) (cx + s * h - c * h), y, (float) (cz - c * h - s * h)).setUv(u0, 1).setColor(r, g, b, alpha);
    }

    /** The metronome: a ring on the floor round the Conductor and a halo over it, pulsing on the beat; the count-in's dots. */
    private static void metronome(ConductorBlockEntity be, MultiBufferSource buffers, Matrix4f m, long t, float partialTick, double now,
            ChoirSession.Phase phase, double sinceDiscord) {
        float pulse = VesperClock.pulse(t, partialTick);
        int rgb;
        float a;
        boolean countIn = false;
        switch (phase) {
            case CALL -> {
                countIn = now >= be.answerStart() - (double) ChoirRules.COUNT_IN_BEATS * ChoirRules.BEAT;
                rgb = countIn ? TEAL : GOLD;
                a = countIn ? 0.3f + 0.6f * pulse : 0.2f + 0.55f * pulse;
            }
            case ANSWER -> {
                rgb = TEAL;
                a = 0.3f + 0.65f * pulse;
            }
            case REST -> {
                rgb = 0x8F6BFF;
                a = (float) (0.14 + 0.1 * Math.sin(now * 0.15));
            }
            case SOLVED -> {
                rgb = 0xFFD27A;
                a = 0.4f;
            }
            default -> {
                rgb = 0xDDE6FF;
                a = 0.1f + 0.12f * pulse;
            }
        }
        if (sinceDiscord < 12) {
            rgb = 0xFF3040;
            a = (float) (0.9 * (1 - sinceDiscord / 12));
        }
        VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(CryptClient.RING));
        flat(ring, m, 0.5, 0.025, 0.5, 1.75, rgb, a);
        flat(ring, m, 0.5, 2.3, 0.5, 0.8, rgb, Math.min(1f, a * 1.2f));
        if (countIn) {
            double start = be.answerStart() - (double) ChoirRules.COUNT_IN_BEATS * ChoirRules.BEAT;
            int beats = (int) Math.floor((now - start) / ChoirRules.BEAT) + 1;
            VertexConsumer dots = buffers.getBuffer(FxRenderTypes.additive(CryptClient.GLOW));
            for (int k = 0; k < ChoirRules.COUNT_IN_BEATS; k++) {
                double ang = Math.toRadians(-90 + 90 * k);
                float da = k < beats ? 0.95f : 0.15f;
                flat(dots, m, 0.5 + Math.cos(ang) * 1.3, 0.04, 0.5 + Math.sin(ang) * 1.3, 0.24, TEAL, da);
            }
        }
    }

    /** A flat square on the floor (or in the air) centred at (x, y, z), {@code half} blocks each way. */
    private static void flat(VertexConsumer out, Matrix4f m, double x, double y, double z, double half, int rgb, float a) {
        float r = (rgb >> 16 & 255) / 255f;
        float g = (rgb >> 8 & 255) / 255f;
        float b = (rgb & 255) / 255f;
        out.addVertex(m, (float) (x - half), (float) y, (float) (z - half)).setUv(0, 0).setColor(r, g, b, a);
        out.addVertex(m, (float) (x + half), (float) y, (float) (z - half)).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, (float) (x + half), (float) y, (float) (z + half)).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, (float) (x - half), (float) y, (float) (z + half)).setUv(0, 1).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(ConductorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ConductorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(8, 1, 8).expandTowards(0, 3, 0);
    }
}
