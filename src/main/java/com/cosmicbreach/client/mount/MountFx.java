package com.cosmicbreach.client.mount;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** The mounts' client visuals. */
final class MountFx {
    private MountFx() {
    }

    /** Phase Blink: a streak of star dust along the path and a burst where it arrives. */
    static void blink(Entity manta, Vec3 from, Vec3 to) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 d = to.subtract(from);
        int n = 28;
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n)).add(0, 0.4, 0);
            level.addParticle(ParticleTypes.END_ROD, p.x + (Math.random() - 0.5) * 0.8, p.y + (Math.random() - 0.5) * 0.4,
                    p.z + (Math.random() - 0.5) * 0.8, 0, 0, 0);
        }
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            level.addParticle(ParticleTypes.GLOW, to.x + Math.cos(a) * 1.2, to.y + 0.4, to.z + Math.sin(a) * 1.2,
                    Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08);
        }
    }
}
