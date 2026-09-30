package com.cosmicbreach.accessory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * An accessory worn in a Curios slot (GDD 5.2). The item only says what it is: its slot comes from the
 * {@code curios:<slot>} item tags, what it does from the listeners in {@code accessory/} that ask whether it is worn
 * ({@link Worn}). The Heart of a Dying Star's +4 max health is its one attribute. Using one from the hand puts it on
 * (into a free slot of its kind, or swapped with the first taken one); a second copy never goes on, since two of the
 * same do nothing more.
 */
public class AccessoryItem extends Item implements ICurioItem {
    private final Accessory kind;

    public AccessoryItem(Properties properties, Accessory kind) {
        super(properties);
        this.kind = kind;
    }

    public Accessory kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach." + kind.path() + ".effect").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext context, ResourceLocation id,
                                                                                ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        if (kind == Accessory.HEART_OF_A_DYING_STAR) {
            map.put(Attributes.MAX_HEALTH, new AttributeModifier(id, AccessoryRules.HEART_HEALTH, AttributeModifier.Operation.ADD_VALUE));
        }
        return map;
    }

    /** Never two of the same: any other slot already holding this accessory refuses it. */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        LivingEntity wearer = context.entity();
        if (wearer == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(wearer).map(inventory -> inventory.findCurios(this).stream()
                .allMatch(worn -> worn.slotContext().identifier().equals(context.identifier())
                        && worn.slotContext().index() == context.index())).orElse(true);
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext context, ItemStack stack) {
        return new ICurio.SoundInfo(AccessoryRegistry.EQUIP.get(), 0.9f, 1.0f);
    }
}
