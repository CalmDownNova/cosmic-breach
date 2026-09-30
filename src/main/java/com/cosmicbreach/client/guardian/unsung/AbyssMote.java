package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.client.fx.FxParticle;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Abyss's ambient mote ({@code UnsungRegistry.ABYSS_MOTE}, its biome's particle): a faint soft glow of violet
 * or teal light drifting slowly up through the dark, fading in and out. Added light like the other effects, but in its
 * own group, so the ambience can never crowd out a fight's effects in {@code FxBudget}.
 */
final class AbyssMote extends FxParticle {
    /** At most this many motes at once. */
    static final ParticleGroup GROUP = new ParticleGroup(600);
    private static final int[] COLOURS = {0x9A7BFF, 0xB58CFF, 0x6FD8E0, 0xD27BFF};

    private AbyssMote(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.random));
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.of(GROUP);
    }

    /** The provider the biome's ambience goes through. */
    static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, dx, dy, dz) -> {
            AbyssMote m = new AbyssMote(level, x, y, z, sprites);
            var r = level.random;
            m.velocity(new Vec3((r.nextDouble() - 0.5) * 0.006, 0.002 + r.nextDouble() * 0.006, (r.nextDouble() - 0.5) * 0.006))
                    .color(COLOURS[r.nextInt(COLOURS.length)]).brightness(0.32f + r.nextFloat() * 0.18f)
                    .size(0.03f + r.nextFloat() * 0.03f, 0.02f).life(70 + r.nextInt(70)).fade(0.35f, 1.4f);
            return m;
        };
    }
}
