package com.cosmicbreach.familiar;

import com.cosmicbreach.CosmicBreach;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The familiars' registrations (G10, GDD 8.2): the three familiars, the Star Egg, the Familiar Lantern and the Brazier
 * of Solenne, the lantern's and the egg's data components, the statuses Refract and Kindled and the Gravikin's slow,
 * the sounds ({@code tools/sound/familiars.py}), the owner's synced "which familiar is out" and the familiars' damage
 * type {@code cosmicbreach:familiar}.
 */
public final class FamiliarRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ the familiars

    public static final DeferredHolder<EntityType<?>, EntityType<Emberwisp>> EMBERWISP = ENTITY_TYPES.register("emberwisp",
            () -> EntityType.Builder.of(Emberwisp::new, MobCategory.MISC).sized(0.45f, 0.45f).eyeHeight(0.22f).fireImmune()
                    .noSave().noSummon().clientTrackingRange(8).updateInterval(1).build(CosmicBreach.id("emberwisp").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<Gravikin>> GRAVIKIN = ENTITY_TYPES.register("gravikin",
            () -> EntityType.Builder.of(Gravikin::new, MobCategory.MISC).sized(0.6f, 0.7f).eyeHeight(0.55f).fireImmune()
                    .noSave().noSummon().clientTrackingRange(8).updateInterval(1).build(CosmicBreach.id("gravikin").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<PrismMoth>> PRISM_MOTH = ENTITY_TYPES.register("prism_moth",
            () -> EntityType.Builder.of(PrismMoth::new, MobCategory.MISC).sized(0.5f, 0.4f).eyeHeight(0.2f).fireImmune()
                    .noSave().noSummon().clientTrackingRange(8).updateInterval(1).build(CosmicBreach.id("prism_moth").toString()));

    // ------------------------------------------------------------------ data components

    /** A lantern's familiar: everything about it ({@link FamiliarBond}). Saved with the stack, synced. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FamiliarBond>> BOND =
            COMPONENTS.registerComponentType("familiar", b -> b.persistent(FamiliarBond.CODEC).networkSynchronized(FamiliarBond.STREAM_CODEC));
    /** What a Star Egg holds (set by the vault's loot table; an egg without it hatches something at random). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FamiliarKind>> EGG =
            COMPONENTS.registerComponentType("star_egg", b -> b.persistent(FamiliarKind.CODEC).networkSynchronized(FamiliarKind.STREAM_CODEC));

    // ------------------------------------------------------------------ blocks and items

    public static final DeferredBlock<BrazierBlock> BRAZIER = BLOCKS.register("brazier_of_solenne", () -> new BrazierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(BrazierBlock.LIT) ? 12 : 4)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrazierBlockEntity>> BRAZIER_ENTITY =
            BLOCK_ENTITIES.register("brazier_of_solenne", () -> BlockEntityType.Builder.of(BrazierBlockEntity::new, BRAZIER.get()).build(null));
    public static final DeferredItem<BlockItem> BRAZIER_ITEM = ITEMS.registerSimpleBlockItem(BRAZIER, new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<StarEggItem> STAR_EGG = ITEMS.registerItem("star_egg", StarEggItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<FamiliarLanternItem> LANTERN = ITEMS.registerItem("familiar_lantern", FamiliarLanternItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // ------------------------------------------------------------------ statuses

    public static final DeferredHolder<MobEffect, Refract> REFRACT = EFFECTS.register("refract", Refract::new);
    public static final DeferredHolder<MobEffect, Kindled> KINDLED = EFFECTS.register("kindled", Kindled::new);
    public static final DeferredHolder<MobEffect, GravityDrag> DRAG = EFFECTS.register("gravity_drag", GravityDrag::new);

    // ------------------------------------------------------------------ sounds

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("familiar/summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISMISS = sound("familiar/dismiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> MODE = sound("familiar/mode");
    public static final DeferredHolder<SoundEvent, SoundEvent> HATCH = sound("familiar/hatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> EGG_SET = sound("familiar/egg_set");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("familiar/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("familiar/death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_STRIKE = sound("familiar/wisp_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCORCH = sound("familiar/scorch");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINDLE = sound("familiar/kindled");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOP = sound("familiar/hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound("familiar/slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> TAUNT = sound("familiar/taunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_STRIKE = sound("familiar/moth_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("familiar/refract");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFRACT_BREAK = sound("familiar/refract_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLEANSE = sound("familiar/cleanse");

    // ------------------------------------------------------------------ the owner's state

    /** No familiar out. */
    public static final UUID NONE = new UUID(0L, 0L);

    /** The bond id of the familiar a player has out ({@link #NONE} for none): synced to that player alone, never saved. */
    public static final Supplier<AttachmentType<UUID>> ACTIVE = ATTACHMENTS.register("active_familiar",
            () -> AttachmentType.builder(() -> NONE).sync((holder, to) -> holder == to, UUIDUtil.STREAM_CODEC).build());

    // ------------------------------------------------------------------ damage

    /** A familiar's hit: from the familiar, credited to its owner (threat, kill credit and XP go to them). */
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("familiar"));

    private FamiliarRegistry() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    /** The vanilla block tags this feature adds to (written by {@code ModBlockTagsProvider} in the data run). */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        tag.accept(BlockTags.MINEABLE_WITH_PICKAXE, new Block[] {BRAZIER.get()});
    }

    /** The entity type of a familiar kind. */
    public static EntityType<? extends FamiliarEntity> type(FamiliarKind kind) {
        return switch (kind) {
            case EMBERWISP -> EMBERWISP.get();
            case GRAVIKIN -> GRAVIKIN.get();
            case PRISM_MOTH -> PRISM_MOTH.get();
        };
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
