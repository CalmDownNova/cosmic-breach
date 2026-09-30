package com.cosmicbreach.structure;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.array.ApertureBlock;
import com.cosmicbreach.structure.array.FilterBlock;
import com.cosmicbreach.structure.array.FocusBlock;
import com.cosmicbreach.structure.array.LensCoreBlock;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import com.cosmicbreach.structure.array.LensPiece;
import com.cosmicbreach.structure.array.LensPieceItem;
import com.cosmicbreach.structure.array.LensUmbralBlock;
import com.cosmicbreach.structure.array.MirrorBlock;
import com.cosmicbreach.structure.array.PedestalBlock;
import com.cosmicbreach.structure.array.ReceptorBlock;
import com.cosmicbreach.structure.array.SocketBlock;
import com.cosmicbreach.structure.array.SplitterBlock;
import com.cosmicbreach.structure.array.WardenEyeBlock;
import com.cosmicbreach.structure.gen.GyreObservatoryStructure;
import com.cosmicbreach.structure.gen.ObservatoryPiece;
import com.cosmicbreach.structure.gen.ReliquaryPiece;
import com.cosmicbreach.structure.gen.SpireReliquaryStructure;
import com.cosmicbreach.structure.trap.GuardMarkerBlock;
import com.cosmicbreach.structure.trap.GuardMarkerBlockEntity;
import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlockEntity;
import com.cosmicbreach.structure.trap.KineticThreadBlock;
import com.cosmicbreach.structure.trap.UpdraftBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything W5 registers (GDD 6.1, 6.2, 6.4): the Lens Array's blocks and loose pieces, the vaults, the
 * Kinetic Tripwires, the gravity lifts, the guard markers, the two structures, their sounds
 * ({@code tools/sound/structures.py}) and the {@code cosmicbreach:lens_piece} component. Textures and models
 * come from {@code tools/art/gen_structures.py}.
 */
public final class StructureRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CosmicBreach.MOD_ID);

    /** The Tripwire's bolt: no attacker, so a dash's i-frames do not slip it (GDD 6.4: rushing is what it punishes). */
    public static final ResourceKey<DamageType> KINETIC_BOLT = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("kinetic_bolt"));

    // ------------------------------------------------------------------ the Lens Array

    public static final DeferredBlock<PedestalBlock> LENS_PEDESTAL = block("lens_pedestal", PedestalBlock::new,
            puzzle(MapColor.QUARTZ, SoundType.CALCITE), true);
    public static final DeferredBlock<MirrorBlock> LENS_MIRROR = block("lens_mirror", MirrorBlock::new,
            puzzle(MapColor.QUARTZ, SoundType.GLASS), true);
    public static final DeferredBlock<SplitterBlock> LENS_SPLITTER = block("lens_splitter", SplitterBlock::new,
            puzzle(MapColor.DIAMOND, SoundType.AMETHYST).lightLevel(state -> 5), true);
    public static final DeferredBlock<FilterBlock> LENS_FILTER = block("lens_filter", FilterBlock::new,
            puzzle(MapColor.GOLD, SoundType.GLASS).lightLevel(state -> 4), true);
    public static final DeferredBlock<LensUmbralBlock> LENS_UMBRAL = block("lens_umbral", LensUmbralBlock::new,
            puzzle(MapColor.COLOR_BLACK, SoundType.DEEPSLATE), true);
    public static final DeferredBlock<FocusBlock> LENS_FOCUS = block("lens_focus", FocusBlock::new,
            puzzle(MapColor.GOLD, SoundType.AMETHYST).lightLevel(state -> 12), true);
    public static final DeferredBlock<ReceptorBlock> LENS_RECEPTOR = block("lens_receptor", ReceptorBlock::new,
            puzzle(MapColor.DIAMOND, SoundType.AMETHYST_CLUSTER).lightLevel(state -> state.getValue(ReceptorBlock.LIT) ? 11 : 4), true);
    public static final DeferredBlock<WardenEyeBlock> WARDEN_EYE = block("warden_eye", WardenEyeBlock::new,
            puzzle(MapColor.COLOR_PURPLE, SoundType.SCULK_CATALYST).lightLevel(state -> state.getValue(WardenEyeBlock.AWAKE) ? 10 : 5), true);
    public static final DeferredBlock<SocketBlock> LENS_SOCKET = block("lens_socket", SocketBlock::new,
            puzzle(MapColor.QUARTZ, SoundType.CALCITE), true);
    public static final DeferredBlock<ApertureBlock> SUN_APERTURE = block("sun_aperture", ApertureBlock::new,
            puzzle(MapColor.GOLD, SoundType.AMETHYST).lightLevel(state -> state.getValue(ApertureBlock.OPEN) ? 15 : 6), true);
    public static final DeferredBlock<LensCoreBlock> LENS_CORE = block("lens_core", LensCoreBlock::new,
            puzzle(MapColor.QUARTZ, SoundType.CALCITE).lightLevel(state -> 7), false);

    public static final DeferredItem<LensPieceItem> LOOSE_MIRROR = ITEMS.registerItem("loose_mirror",
            p -> new LensPieceItem(p, LensPiece.Kind.MIRROR), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LensPieceItem> LOOSE_GOLD_FILTER = ITEMS.registerItem("loose_gold_filter",
            p -> new LensPieceItem(p, LensPiece.Kind.GOLD_FILTER), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LensPieceItem> LOOSE_TEAL_FILTER = ITEMS.registerItem("loose_teal_filter",
            p -> new LensPieceItem(p, LensPiece.Kind.TEAL_FILTER), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LensPieceItem> LOOSE_MAGENTA_FILTER = ITEMS.registerItem("loose_magenta_filter",
            p -> new LensPieceItem(p, LensPiece.Kind.MAGENTA_FILTER), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** A carried loose piece: which array it belongs to and the token that array expects back. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LensPiece>> LENS_PIECE =
            COMPONENTS.registerComponentType("lens_piece", builder -> builder.persistent(LensPiece.CODEC)
                    .networkSynchronized(LensPiece.STREAM_CODEC));

    // ------------------------------------------------------------------ vaults, traps, lifts, light

    public static final DeferredBlock<VaultBlock> RELIQUARY_VAULT = block("reliquary_vault", p -> new VaultBlock(p, 1),
            puzzle(MapColor.QUARTZ, SoundType.VAULT).lightLevel(state -> state.getValue(VaultBlock.READY) ? 12 : 4), true);
    public static final DeferredBlock<VaultBlock> OBSERVATORY_VAULT = block("observatory_vault", p -> new VaultBlock(p, 2),
            puzzle(MapColor.COLOR_LIGHT_BLUE, SoundType.VAULT).lightLevel(state -> state.getValue(VaultBlock.READY) ? 12 : 4), true);
    /** Pale gold glowing stone: the Reach structures' daylight panels. */
    public static final DeferredBlock<Block> SUNSTONE = block("sunstone", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f, 6f).sound(SoundType.CALCITE)
                    .requiresCorrectToolForDrops().lightLevel(state -> 15), true);
    /** A cyan-white lamp of Nebulite in Driftstone: the Drift structures' light. */
    public static final DeferredBlock<Block> NEBULITE_LAMP = block("nebulite_lamp", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5f, 6f).sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops().lightLevel(state -> 15), true);
    public static final DeferredBlock<KineticEmitterBlock> KINETIC_EMITTER = block("kinetic_emitter", KineticEmitterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(8f, 1200f).sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops().lightLevel(state -> 3), true);
    public static final DeferredBlock<KineticThreadBlock> KINETIC_THREAD = block("kinetic_thread", KineticThreadBlock::new,
            technical(), false);
    public static final DeferredBlock<UpdraftBlock> UPDRAFT = block("updraft", UpdraftBlock::new, technical(), false);
    public static final DeferredBlock<GuardMarkerBlock> GUARD_MARKER = block("guard_marker", GuardMarkerBlock::new, technical(), false);

    // ------------------------------------------------------------------ block entities

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LensCoreBlockEntity>> LENS_CORE_ENTITY =
            BLOCK_ENTITIES.register("lens_core", () -> BlockEntityType.Builder.of(LensCoreBlockEntity::new, LENS_CORE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VaultBlockEntity>> VAULT_ENTITY =
            BLOCK_ENTITIES.register("vault", () -> BlockEntityType.Builder.of(VaultBlockEntity::new, RELIQUARY_VAULT.get(),
                    OBSERVATORY_VAULT.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticEmitterBlockEntity>> KINETIC_EMITTER_ENTITY =
            BLOCK_ENTITIES.register("kinetic_emitter", () -> BlockEntityType.Builder.of(KineticEmitterBlockEntity::new,
                    KINETIC_EMITTER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardMarkerBlockEntity>> GUARD_MARKER_ENTITY =
            BLOCK_ENTITIES.register("guard_marker", () -> BlockEntityType.Builder.of(GuardMarkerBlockEntity::new,
                    GUARD_MARKER.get()).build(null));

    // ------------------------------------------------------------------ sounds

    public static final DeferredHolder<SoundEvent, SoundEvent> APERTURE_OPEN = sound("structures/aperture_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_TURN = sound("structures/mirror_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECEPTOR_HUM = sound("structures/receptor_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECEPTOR_LIT = sound("structures/receptor_lit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_EYE_WAKES = sound("structures/warden_eye");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_OPEN = sound("structures/vault_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UMBRAL_PUSH = sound("structures/umbral_push");
    public static final DeferredHolder<SoundEvent, SoundEvent> THREAD = sound("structures/thread");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT = sound("structures/bolt");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPDRAFT_RUSH = sound("structures/updraft");

    // ------------------------------------------------------------------ the structures

    public static final DeferredHolder<StructureType<?>, StructureType<SpireReliquaryStructure>> SPIRE_RELIQUARY =
            STRUCTURE_TYPES.register("spire_reliquary", () -> () -> SpireReliquaryStructure.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<GyreObservatoryStructure>> GYRE_OBSERVATORY =
            STRUCTURE_TYPES.register("gyre_observatory", () -> () -> GyreObservatoryStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RELIQUARY_PIECE =
            STRUCTURE_PIECES.register("spire_reliquary", () -> (StructurePieceType.ContextlessType) ReliquaryPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> OBSERVATORY_PIECE =
            STRUCTURE_PIECES.register("gyre_observatory", () -> (StructurePieceType.ContextlessType) ObservatoryPiece::new);

    private StructureRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        COMPONENTS.register(modBus);
    }

    /** Puzzle blocks can't be broken or pushed (GDD 6.2's anti-cheese) and drop nothing. */
    private static BlockBehaviour.Properties puzzle(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).sound(sound).noOcclusion();
    }

    /**
     * Invisible working parts: no collision, no outline, nothing drops, pistons can't move them, and neither blocks
     * nor fluids can take their place (each also refuses liquids).
     */
    private static BlockBehaviour.Properties technical() {
        return BlockBehaviour.Properties.of().noCollission().strength(-1.0f, 3_600_000.0f).noLootTable().noOcclusion()
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).forceSolidOff();
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
            BlockBehaviour.Properties properties, boolean item) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        if (item) {
            ITEMS.registerSimpleBlockItem(block);
        }
        return block;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
