package com.cosmicbreach.gear.set;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.item.TieredGear;
import com.cosmicbreach.registry.ModAttributes;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A piece of an {@link ArmorSet}: vanilla armor (its armor, toughness and knockback resistance come from the
 * set's armor material) plus the piece's stat bonuses as {@code ADD_VALUE} modifiers on the progression
 * attributes, drawn as the set's GeckoLib model. Reforgeable from the set's tier.
 *
 * <p>The renderer is client code: the client hands {@link #clientRenderers} a factory at startup, and
 * GeckoLib asks for it the first time it draws the piece, so a server never loads it.
 */
public class SetArmorItem extends ArmorItem implements GeoItem, TieredGear {
    /** Set by the client on startup: the render provider for a set's pieces. */
    public static volatile Function<ArmorSet, GeoRenderProvider> clientRenderers = set -> GeoRenderProvider.DEFAULT;

    private final ArmorSet set;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<ItemAttributeModifiers> modifiers;

    public SetArmorItem(ArmorSet set, Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties properties) {
        super(material, type, properties);
        this.set = set;
        this.modifiers = Suppliers.memoize(() -> withStats(super.getDefaultAttributeModifiers()));
    }

    public ArmorSet set() {
        return set;
    }

    @Override
    public int unlockTier() {
        return set.tier();
    }

    /** The material's armor, toughness and knockback resistance, and this piece's stat bonuses. */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    private ItemAttributeModifiers withStats(ItemAttributeModifiers base) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        base.modifiers().forEach(entry -> builder.add(entry.attribute(), entry.modifier(), entry.slot()));
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(getType().getSlot());
        Map<Stat, Integer> stats = set.piece(getType()).map(ArmorSet.Piece::stats).orElse(Map.of());
        for (Stat stat : Stat.values()) {
            Integer points = stats.get(stat);
            if (points != null && points != 0) {
                builder.add(ModAttributes.of(stat), new AttributeModifier(statModifierId(getType()), points,
                        AttributeModifier.Operation.ADD_VALUE), group);
            }
        }
        return builder.build();
    }

    /** One id per armor slot, so the four pieces' bonuses add up instead of replacing each other. */
    public static net.minecraft.resources.ResourceLocation statModifierId(ArmorItem.Type type) {
        return CosmicBreach.id("set_piece." + type.getName());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int tier = GearTier.of(stack, set.tier());
        int steps = GearTier.steps(tier, set.tier());
        tooltip.add(Component.translatable("tooltip.cosmicbreach.gear_tier", GearTier.roman(tier))
                .withColor(steps > 0 ? GearTier.color(tier) : 0xAAAAAA));
        tooltip.add(Component.translatable("tooltip.cosmicbreach.set." + set.id().getPath()).withColor(set.color()));
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(clientRenderers.apply(set));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // no keyframe animations: the renderer poses the moving parts (the Vanguard's crest) from the wearer's motion
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
