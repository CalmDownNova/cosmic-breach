package com.cosmicbreach.shrine;

import com.cosmicbreach.CosmicBreach;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The shrines' registrations (1.1 design section 9): the four blocks and their block entity, the save and keep sounds
 * ({@code tools/sound/shrines.py}), a player's save (kept through deaths) and what a death kept (until the respawn).
 */
public final class ShrineRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);

    public static final DeferredBlock<ShrineBlock> COLOSSUS = shrine(ShrineKind.COLOSSUS, MapColor.QUARTZ);
    public static final DeferredBlock<ShrineBlock> LEVIATHAN = shrine(ShrineKind.LEVIATHAN, MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<ShrineBlock> UNSUNG = shrine(ShrineKind.UNSUNG, MapColor.TERRACOTTA_WHITE);
    public static final DeferredBlock<ShrineBlock> HELIARCH = shrine(ShrineKind.HELIARCH, MapColor.GOLD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrineBlockEntity>> SHRINE_ENTITY = BLOCK_ENTITIES.register("shrine",
            () -> BlockEntityType.Builder.of(ShrineBlockEntity::new, COLOSSUS.get(), LEVIATHAN.get(), UNSUNG.get(), HELIARCH.get()).build(null));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAVE_SOUND = sound("shrine/save");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEPT_SOUND = sound("shrine/kept");

    /** The shrine a player last saved at: saved with the player, kept through deaths. */
    public static final Supplier<AttachmentType<ShrineSave.Saved>> SAVED = ATTACHMENTS.register("shrine_save",
            () -> AttachmentType.builder(() -> ShrineSave.Saved.NONE).serialize(ShrineSave.Saved.CODEC).copyOnDeath().build());
    /** What a death kept, on the dead player until the respawn copies it back (saved, so a restart while dead keeps it). */
    public static final Supplier<AttachmentType<ShrineKeep.Kept>> KEPT = ATTACHMENTS.register("shrine_kept",
            () -> AttachmentType.builder(() -> ShrineKeep.Kept.NONE).serialize(ShrineKeep.Kept.CODEC).build());

    private ShrineRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    /** The shrines' tags, for the data run: no Wither and no Ender Dragon can remove one (a removed shrine is never placed again). */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>,
            net.minecraft.world.level.block.Block[]> tag) {
        net.minecraft.world.level.block.Block[] all = {COLOSSUS.get(), LEVIATHAN.get(), UNSUNG.get(), HELIARCH.get()};
        tag.accept(net.minecraft.tags.BlockTags.WITHER_IMMUNE, all);
        tag.accept(net.minecraft.tags.BlockTags.DRAGON_IMMUNE, all);
    }

    public static ShrineBlock block(ShrineKind kind) {
        return switch (kind) {
            case COLOSSUS -> COLOSSUS.get();
            case LEVIATHAN -> LEVIATHAN.get();
            case UNSUNG -> UNSUNG.get();
            case HELIARCH -> HELIARCH.get();
        };
    }

    private static DeferredBlock<ShrineBlock> shrine(ShrineKind kind, MapColor color) {
        return BLOCKS.register("shrine_" + kind.id(), () -> new ShrineBlock(kind, BlockBehaviour.Properties.of().mapColor(color)
                .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> 10).sound(SoundType.AMETHYST)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
