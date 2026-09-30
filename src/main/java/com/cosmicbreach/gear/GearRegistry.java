package com.cosmicbreach.gear;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.gear.forge.AstralForgeBlock;
import com.cosmicbreach.gear.forge.AstralForgeBlockEntity;
import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.gear.forge.ForgeTiers;
import com.cosmicbreach.gear.set.ArmorSet;
import com.cosmicbreach.gear.set.SetArmorItem;
import com.cosmicbreach.gear.set.SetState;
import com.cosmicbreach.gear.vanguard.StarfallVanguard;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.registry.ModMaterials;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Everything the gear system registers (GDD 3.5 and 5.1): the Astral Forge (block, block entity, menu,
 * recipe type), the armor sets' materials and pieces, the set state attachment, Scorch, the damage types
 * and the sounds.
 */
public final class GearRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CosmicBreach.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CosmicBreach.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ the Astral Forge

    public static final DeferredBlock<AstralForgeBlock> ASTRAL_FORGE = BLOCKS.registerBlock("astral_forge", AstralForgeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(3.5f, 1200f).sound(SoundType.ANVIL)
                    .noOcclusion().lightLevel(state -> ForgeTiers.light(state.getValue(AstralForgeBlock.TIER))));
    public static final DeferredItem<BlockItem> ASTRAL_FORGE_ITEM = ITEMS.registerSimpleBlockItem(ASTRAL_FORGE,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    @SuppressWarnings("DataFlowIssue") // no data fixer type, as for every modded block entity
    public static final Supplier<BlockEntityType<AstralForgeBlockEntity>> ASTRAL_FORGE_ENTITY = BLOCK_ENTITIES.register("astral_forge",
            () -> BlockEntityType.Builder.of(AstralForgeBlockEntity::new, ASTRAL_FORGE.get()).build(null));
    public static final Supplier<MenuType<AstralForgeMenu>> ASTRAL_FORGE_MENU = MENUS.register("astral_forge",
            () -> IMenuTypeExtension.create(AstralForgeMenu::new));
    public static final Supplier<RecipeType<ForgeRecipe>> FORGE_RECIPE_TYPE = RECIPE_TYPES.register("astral_forge",
            () -> RecipeType.simple(CosmicBreach.id("astral_forge")));
    public static final Supplier<RecipeSerializer<ForgeRecipe>> FORGE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("astral_forge",
            ForgeRecipe.Serializer::new);

    // ------------------------------------------------------------------ armor sets

    /** The four pieces of one set. */
    public record SetItems(ArmorSet set, DeferredItem<SetArmorItem> helmet, DeferredItem<SetArmorItem> chestplate,
                           DeferredItem<SetArmorItem> leggings, DeferredItem<SetArmorItem> boots) {
        public List<DeferredItem<SetArmorItem>> all() {
            return List.of(helmet, chestplate, leggings, boots);
        }
    }

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VANGUARD_MATERIAL = ARMOR_MATERIALS.register(
            StarfallVanguard.SET.id().getPath(), () -> material(StarfallVanguard.SET, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(ModMaterials.STARSTEEL_INGOT.get())));
    public static final SetItems VANGUARD = registerSet(StarfallVanguard.SET, VANGUARD_MATERIAL);

    /** A player's {@link SetState}: saved, and synced to the player and everyone who sees them (the armor glows with it). */
    public static final Supplier<AttachmentType<SetState>> SET_STATE = ATTACHMENTS.register("armor_set",
            () -> AttachmentType.builder(() -> SetState.NONE)
                    .serialize(SetState.CODEC)
                    .sync(SetState.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<MobEffect, Scorch> SCORCH = EFFECTS.register("scorch", Scorch::new);

    public static final ResourceKey<DamageType> METEOR_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("meteor"));
    public static final ResourceKey<DamageType> SHOCKWAVE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("shockwave"));

    // ------------------------------------------------------------------ sounds (tools/sound/forge.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_CRAFT = sound("forge/craft");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_TIER_UP = sound("forge/tier_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> VANGUARD_SHOCKWAVE = sound("vanguard/shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_INCOMING = sound("vanguard/meteor_incoming");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_IMPACT = sound("vanguard/meteor_impact");

    private GearRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        ARMOR_MATERIALS.register(modBus);
        ATTACHMENTS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, GearRegistry::fillCreativeTab);
    }

    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
            event.accept(ASTRAL_FORGE_ITEM.get());
            VANGUARD.all().forEach(piece -> event.accept(piece.get()));
        }
    }

    /** A set's armor material: its armor per piece, toughness and knockback resistance. GeckoLib draws it, so its layer is a name only. */
    private static ArmorMaterial material(ArmorSet set, Holder<SoundEvent> equipSound, Supplier<Ingredient> repair) {
        return new ArmorMaterial(set.defense(), 15, equipSound, repair, List.of(new ArmorMaterial.Layer(set.id())),
                set.toughness(), set.knockbackResistance());
    }

    /** The set's four pieces, named as the set says. */
    public static SetItems registerSet(ArmorSet set, Holder<ArmorMaterial> material) {
        return new SetItems(set, piece(set, material, ArmorItem.Type.HELMET), piece(set, material, ArmorItem.Type.CHESTPLATE),
                piece(set, material, ArmorItem.Type.LEGGINGS), piece(set, material, ArmorItem.Type.BOOTS));
    }

    private static DeferredItem<SetArmorItem> piece(ArmorSet set, Holder<ArmorMaterial> material, ArmorItem.Type type) {
        String name = set.piece(type).orElseThrow().name();
        return ITEMS.registerItem(name, properties -> new SetArmorItem(set, material, type, properties),
                new Item.Properties().durability(type.getDurability(set.durabilityMultiplier())).rarity(Rarity.UNCOMMON));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
