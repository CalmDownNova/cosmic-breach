package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.function.DoubleSupplier;

/**
 * Light gathering on a charging blade: a soft glow along the blade that swells and brightens as the
 * charge builds, pulsing gently, and fades out in a few ticks once the charge ends. It sits on the blade
 * as drawn ({@link BladeTracker}): in third person on the charge pose's blade, low behind the right hip;
 * through the charging player's own eyes on vanilla's first-person sword, which is drawn over the world,
 * so there it runs along the blade's upper edge and shows as a rim of light.
 */
final class ChargeGlowEffect implements WorldFx.Effect {
    private static final int FADE_TICKS = 3;
    private static final int[] SPOTS = {0, 1, 2};

    private final Player player;
    private final DoubleSupplier progress;
    /** The charge's colour, or -1 for Meridian's gold. */
    private final int color;
    private boolean ending;
    private int endTicks;

    ChargeGlowEffect(Player player, DoubleSupplier progress, int color) {
        this.player = player;
        this.progress = progress;
        this.color = color;
    }

    void end() {
        ending = true;
    }

    @Override
    public boolean tick() {
        if (player.isRemoved()) {
            return false;
        }
        return !ending || ++endTicks < FADE_TICKS;
    }

    @Override
    public void render(WorldFx.Frame f) {
        double charge = Math.max(0.0, Math.min(1.0, progress.getAsDouble()));
        double fade = ending ? Math.max(0.0, 1.0 - (endTicks + f.partialTick()) / FADE_TICKS) : 1.0;
        double pulse = 0.85 + 0.15 * Math.sin(f.now() * 0.9);
        float a = (float) ((0.25 + 0.6 * charge) * fade * pulse);
        if (a <= 0.01f) {
            return;
        }
        boolean own = WorldFx.firstPersonOf(player);
        double edge = CombatEffects.bladeDrawnOver(player) ? BladeTracker.halfWidth(player) : 0.0;
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        float[] c = WorldFx.rgb(color < 0 ? (charge >= 1.0 ? CombatEffects.WHITE_GOLD : CombatEffects.PALE_GOLD)
                : (charge >= 1.0 ? CombatEffects.mix(color, CombatEffects.WHITE, 0.6f) : color));
        for (int spot : SPOTS) {
            double along = 0.35 + 0.3 * spot;
            Vec3 at = f.relative(CombatEffects.bladePoint(player, along, edge));
            float size = (float) ((own ? 0.1 : 0.2) + (own ? 0.08 : 0.16) * charge) * (1f - 0.15f * spot);
            WorldFx.billboard(out, f.camera(), at, size, c[0], c[1], c[2], a);
        }
    }
}
