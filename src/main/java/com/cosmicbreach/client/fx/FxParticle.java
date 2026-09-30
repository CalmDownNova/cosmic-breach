package com.cosmicbreach.client.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * One of our particles: additive, full bright, set up by the effect that spawns it with chained
 * calls ({@code FxParticles.spark(level, at).velocity(v).color(GOLD).size(0.05f, 0.02f).life(8)}) and
 * handed to {@link FxBudget#spawn}. It fades through its colour rather than its alpha, so the particle
 * shader's alpha cut-off only ever trims the sprite's own faint edge.
 *
 * <p>Shapes: {@link Shape#BILLBOARD} faces the camera (and may spin), {@link Shape#STREAK} is drawn
 * along its motion (or a fixed direction), longer the faster it goes, and {@link Shape#FLAT} lies on
 * the ground.
 */
public class FxParticle extends TextureSheetParticle {
    public enum Shape { BILLBOARD, STREAK, FLAT }

    private Shape shape = Shape.BILLBOARD;
    private float red = 1f;
    private float green = 1f;
    private float blue = 1f;
    private float sizeFrom = 0.1f;
    private float sizeTo = 0.1f;
    /** Growth eases out with this power (1 is linear). */
    private float sizeEase = 1f;
    /** Share of the life spent fading in. */
    private float fadeIn;
    /** Brightness falls as (1 - life) to this power. */
    private float fadePower = 1f;
    /** A streak's length in blocks per block per tick of speed, and its shortest length. */
    private float streakPerSpeed = 1.6f;
    private float streakMin = 0.08f;
    private @Nullable Vector3f streakDirection;
    private float streakLength;
    private float spin;
    /** Darkens instead of glowing: its colour stays and its alpha fades ({@link #dark}). */
    private boolean dark;
    private float darkAlpha = 1f;
    /** {@link FxClock} tick of the last engine tick, for {@link FxBudget}'s count. */
    long lastTicked;

    public FxParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        setSprite(sprite);
        this.hasPhysics = false;
        this.friction = 1.0f;
        this.gravity = 0f;
        this.lifetime = 10;
        this.lastTicked = FxClock.ticks();
    }

    // ------------------------------------------------------------------ set-up, chained

    public FxParticle velocity(Vec3 v) {
        this.xd = v.x;
        this.yd = v.y;
        this.zd = v.z;
        return this;
    }

    /** Colour as 0xRRGGBB. */
    public FxParticle color(int rgb) {
        return color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    public FxParticle color(float r, float g, float b) {
        this.red = r;
        this.green = g;
        this.blue = b;
        return this;
    }

    /** Scales the colour, for effects that are dimmer or brighter than their palette colour. */
    public FxParticle brightness(float factor) {
        this.red *= factor;
        this.green *= factor;
        this.blue *= factor;
        return this;
    }

    /** Half the quad's width, in blocks, at birth and at death. */
    public FxParticle size(float from, float to) {
        this.sizeFrom = from;
        this.sizeTo = to;
        return this;
    }

    /** Size changes fast then slow ({@code power} above 1) instead of evenly. */
    public FxParticle sizeEase(float power) {
        this.sizeEase = power;
        return this;
    }

    public FxParticle life(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    /** Vanilla's gravity: 0.04 blocks per tick per tick for each 1. Negative rises. */
    public FxParticle gravity(float g) {
        this.gravity = g;
        return this;
    }

    /** Speed kept each tick (1 keeps it all). */
    public FxParticle drag(float keep) {
        this.friction = keep;
        return this;
    }

    /** Collides with blocks (sparks skitter to a stop on the ground). */
    public FxParticle physics() {
        this.hasPhysics = true;
        return this;
    }

    public FxParticle fade(float fadeInShare, float power) {
        this.fadeIn = fadeInShare;
        this.fadePower = power;
        return this;
    }

    /** Drawn along its motion: {@code perSpeed} blocks long per block per tick, at least {@code min}. */
    public FxParticle streak(float perSpeed, float min) {
        this.shape = Shape.STREAK;
        this.streakPerSpeed = perSpeed;
        this.streakMin = min;
        return this;
    }

    /** Drawn along a fixed direction, {@code length} blocks long. */
    public FxParticle streakAlong(Vec3 direction, float length) {
        this.shape = Shape.STREAK;
        this.streakDirection = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        this.streakLength = length;
        return this;
    }

    /** Lies flat, facing up, at its height. */
    public FxParticle flat() {
        this.shape = Shape.FLAT;
        return this;
    }

    /**
     * Blended over the scene instead of added to it, at up to {@code alpha}: the colour darkens what is
     * behind (dust, soot-free shadows); the fade then works on alpha, not brightness.
     */
    public FxParticle dark(float alpha) {
        this.dark = true;
        this.darkAlpha = alpha;
        return this;
    }

    /** Starts at a random angle and turns {@code radiansPerTick}. */
    public FxParticle spin(float radiansPerTick) {
        this.spin = radiansPerTick;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        return this;
    }

    // ------------------------------------------------------------------ life

    @Override
    public void tick() {
        lastTicked = FxClock.ticks();
        oRoll = roll;
        roll += spin;
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return dark ? FxRenderTypes.DARK_PARTICLES : FxRenderTypes.PARTICLES;
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.of(FxBudget.GROUP);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        float reach = Math.max(Math.max(sizeFrom, sizeTo), streakReach()) + 0.1f;
        return new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
    }

    private float streakReach() {
        if (shape != Shape.STREAK) {
            return 0f;
        }
        if (streakDirection != null) {
            return streakLength;
        }
        return (float) Math.max(streakMin, Math.sqrt(xd * xd + yd * yd + zd * zd) * streakPerSpeed);
    }

    /** Brightness over the life (0 to 1): a fade in, then a fall to zero. */
    float lightAt(float life) {
        float in = fadeIn > 0f ? Math.min(1f, life / fadeIn) : 1f;
        return in * (float) Math.pow(1f - life, fadePower);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float life = Mth.clamp((age + partialTicks) / lifetime, 0f, 1f);
        float light = lightAt(life);
        if (light <= 0.004f) {
            return;
        }
        float grown = sizeEase == 1f ? life : 1f - (float) Math.pow(1f - life, sizeEase);
        float size = Mth.lerp(grown, sizeFrom, sizeTo);
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        float r = dark ? red : red * light;
        float g = dark ? green : green * light;
        float b = dark ? blue : blue * light;
        alpha = dark ? Math.max(0.11f, darkAlpha * light) : 1f;
        switch (shape) {
            case FLAT -> flatQuad(buffer, px, py, pz, size, r, g, b);
            case STREAK -> streakQuad(buffer, px, py, pz, size, r, g, b);
            case BILLBOARD -> billboard(buffer, camera, px, py, pz, size, r, g, b, partialTicks);
        }
    }

    private void billboard(VertexConsumer buffer, Camera camera, float px, float py, float pz, float size,
                           float r, float g, float b, float partialTicks) {
        Quaternionf facing = new Quaternionf(camera.rotation());
        if (spin != 0f) {
            facing.rotateZ(Mth.lerp(partialTicks, oRoll, roll));
        }
        corner(buffer, facing, px, py, pz, 1f, -1f, size, getU1(), getV1(), r, g, b);
        corner(buffer, facing, px, py, pz, 1f, 1f, size, getU1(), getV0(), r, g, b);
        corner(buffer, facing, px, py, pz, -1f, 1f, size, getU0(), getV0(), r, g, b);
        corner(buffer, facing, px, py, pz, -1f, -1f, size, getU0(), getV1(), r, g, b);
    }

    private void corner(VertexConsumer buffer, Quaternionf facing, float px, float py, float pz, float dx, float dy,
                        float size, float u, float v, float r, float g, float b) {
        Vector3f p = new Vector3f(dx, dy, 0f).rotate(facing).mul(size).add(px, py, pz);
        vertex(buffer, p.x, p.y, p.z, u, v, r, g, b);
    }

    /** Wound to face whichever side the camera is on (particles are drawn with back faces culled). */
    private void flatQuad(VertexConsumer buffer, float px, float py, float pz, float s, float r, float g, float b) {
        if (py <= 0f) { // the camera is above: face up
            vertex(buffer, px - s, py, pz - s, getU0(), getV0(), r, g, b);
            vertex(buffer, px - s, py, pz + s, getU0(), getV1(), r, g, b);
            vertex(buffer, px + s, py, pz + s, getU1(), getV1(), r, g, b);
            vertex(buffer, px + s, py, pz - s, getU1(), getV0(), r, g, b);
        } else {
            vertex(buffer, px + s, py, pz - s, getU1(), getV0(), r, g, b);
            vertex(buffer, px + s, py, pz + s, getU1(), getV1(), r, g, b);
            vertex(buffer, px - s, py, pz + s, getU0(), getV1(), r, g, b);
            vertex(buffer, px - s, py, pz - s, getU0(), getV0(), r, g, b);
        }
    }

    /**
     * A quad {@code width} wide along the motion, its head just ahead of the particle and its tail
     * behind, turned about its length to face the camera.
     */
    private void streakQuad(VertexConsumer buffer, float px, float py, float pz, float width, float r, float g, float b) {
        Vector3f dir;
        float length;
        if (streakDirection != null) {
            dir = new Vector3f(streakDirection);
            length = streakLength;
        } else {
            dir = new Vector3f((float) xd, (float) yd, (float) zd);
            length = Math.max(streakMin, dir.length() * streakPerSpeed);
        }
        if (dir.lengthSquared() < 1e-10f) {
            dir.set(0f, 1f, 0f);
        }
        dir.normalize();
        Vector3f view = new Vector3f(px, py, pz);
        Vector3f side = new Vector3f(view).cross(dir); // facing the camera with this corner order
        if (side.lengthSquared() < 1e-10f) {
            return; // seen end on: nothing to draw
        }
        side.normalize().mul(width);
        float hx = px + dir.x * length * 0.25f;
        float hy = py + dir.y * length * 0.25f;
        float hz = pz + dir.z * length * 0.25f;
        float tx = px - dir.x * length * 0.75f;
        float ty = py - dir.y * length * 0.75f;
        float tz = pz - dir.z * length * 0.75f;
        vertex(buffer, tx - side.x, ty - side.y, tz - side.z, getU0(), getV1(), r, g, b);
        vertex(buffer, tx + side.x, ty + side.y, tz + side.z, getU1(), getV1(), r, g, b);
        vertex(buffer, hx + side.x, hy + side.y, hz + side.z, getU1(), getV0(), r, g, b);
        vertex(buffer, hx - side.x, hy - side.y, hz - side.z, getU0(), getV0(), r, g, b);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, float r, float g, float b) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, alpha).setLight(LightTexture.FULL_BRIGHT);
    }
}
