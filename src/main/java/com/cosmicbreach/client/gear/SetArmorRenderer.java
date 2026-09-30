package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetArmorItem;
import com.cosmicbreach.item.GearTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import net.minecraft.Util;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Draws an armor set's pieces with its GeckoLib model, then its glow: the same bones again, right after the
 * plate and in the same pose, with the glowmask texture, full bright (an emissive translucent render type, as
 * GeckoLib's own glowing layer uses), in the piece's tier colour (T1 the set's own, then the tier's trim colour)
 * turning white-hot and brighter with the set's meter (the Vanguard's Heat). So the armor shows what it holds
 * and what it was reforged to.
 *
 * <p>The glow pass is pulled toward the camera ({@link #GLOW_TYPE}): at the plate's own depth it lost the depth test
 * and drew nothing (checked with a magenta glow in the dev client).
 */
public class SetArmorRenderer extends GeoArmorRenderer<SetArmorItem> {
    /**
     * Per set: the glow's colour at an empty and a full meter, the meter's full value, the empty glow's strength, and
     * whether it adds light (unshaded and additive, as {@link GuardianGlowLayer}: star dust on dark cloth) instead of
     * laying its colour over the plate (gold glyphs on ivory).
     */
    public record Glow(int cold, int hot, float full, float coldStrength, boolean additive) {
        public Glow(int cold, int hot, float full, float coldStrength) {
            this(cold, hot, full, coldStrength, false);
        }
    }

    /** Per set, a strength the glow is multiplied by every frame (the Choir Regalia's glyphs pulse on Vesper's beat). */
    @FunctionalInterface
    public interface Pulse {
        float strength(Entity wearer, float partialTick);
    }

    private static final Map<ResourceLocation, Glow> GLOWS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Pulse> PULSES = new ConcurrentHashMap<>();

    /**
     * Vanilla's emissive translucent entity type pulled toward the camera (the layering the armor glint uses): drawn
     * over the plate at the same depth, it otherwise loses the depth test to the plate it covers.
     */
    private static final Function<ResourceLocation, RenderType> GLOW_TYPE = Util.memoize(texture -> RenderType.create(
            "cosmicbreach_armor_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)));

    private final ArmorSet set;
    private final RenderType glowType;
    private final RenderType additiveGlowType;

    public SetArmorRenderer(ArmorSet set) {
        super(new SetArmorModel(set));
        this.set = set;
        this.glowType = GLOW_TYPE.apply(SetArmorModel.glowTexture(set));
        this.additiveGlowType = GuardianGlowLayer.renderType(SetArmorModel.glowTexture(set));
    }

    public static void glow(ResourceLocation setId, Glow glow) {
        GLOWS.put(setId, glow);
    }

    public static void pulse(ResourceLocation setId, Pulse pulse) {
        PULSES.put(setId, pulse);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, SetArmorItem animatable, BakedGeoModel model, RenderType renderType,
                               @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
                               float partialTick, int packedLight, int packedOverlay, int colour) {
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
        if (isReRender || bufferSource == null) {
            return;
        }
        int glow = glowColor(partialTick);
        if (glow != 0) {
            RenderType type = GLOWS.containsKey(set.id()) && GLOWS.get(set.id()).additive() ? additiveGlowType : glowType;
            super.actuallyRender(poseStack, animatable, model, type, bufferSource, bufferSource.getBuffer(type), true,
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, glow);
        }
    }

    /** ARGB of the glow on the piece being drawn now, or 0 for none (an invisible wearer). */
    private int glowColor(float partialTick) {
        Entity wearer = getCurrentEntity();
        ItemStack stack = getCurrentStack();
        if (wearer == null || stack == null || wearer.isInvisible()) {
            return 0;
        }
        Glow glow = GLOWS.getOrDefault(set.id(), new Glow(set.color(), 0xFFFFFF, 1f, 0.8f));
        float heat = meterShare(wearer, set, glow.full());
        int tier = GearTier.of(stack, set.tier());
        int base = tier > set.tier() ? GearTier.color(tier) : glow.cold();
        int color = mix(base, glow.hot(), heat);
        float strength = glow.coldStrength() + (1f - glow.coldStrength()) * heat;
        Pulse pulse = PULSES.get(set.id());
        if (pulse != null) {
            strength *= Math.max(0f, pulse.strength(wearer, partialTick));
        }
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * strength));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * strength));
        int b = Math.min(255, (int) ((color & 0xFF) * strength));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** {@code set}'s meter's share (0 to 1) of the wearer, 0 for anything that isn't a player. */
    static float meterShare(Entity wearer, ArmorSet set, float full) {
        if (!(wearer instanceof Player player) || !player.hasData(GearRegistry.SET_STATE) || full <= 0) {
            return 0f;
        }
        return Math.min(1f, ArmorSets.state(player).meter(set.id(), player.level().getGameTime()) / full);
    }

    static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Horizontal and vertical speed of the wearer, blocks a tick, from its last two positions. */
    static Vec3 velocity(Entity wearer) {
        return new Vec3(wearer.getX() - wearer.xo, wearer.getY() - wearer.yo, wearer.getZ() - wearer.zo);
    }
}
