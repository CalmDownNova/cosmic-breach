package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.choir.ConductorBlock;
import com.cosmicbreach.structure.choir.ConductorBlockEntity;
import com.cosmicbreach.structure.choir.ConductorTopBlock;
import com.cosmicbreach.structure.crypt.trap.GravityPistonBlock;
import com.cosmicbreach.structure.crypt.trap.GravityPistonBlockEntity;
import com.cosmicbreach.structure.crypt.trap.GravitySigilBlock;
import com.cosmicbreach.structure.crypt.trap.StalkerMarkerBlock;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlock;
import com.cosmicbreach.structure.crypt.trap.StarfallChuteBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidPocketBlock;
import com.cosmicbreach.structure.crypt.trap.VoidPocketBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidRiftBlockEntity;
import com.cosmicbreach.structure.crypt.trap.VoidRiftTileBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything W6 registers (GDD 6.1, 6.3, 6.4): the Choir Floor (the Conductor, the resonance floor), the crypt's
 * traps (Void Rift tiles and the Void Pocket with its seal, the Crushing Gravity Plate's sigil and piston, the
 * Starfall Chute), the Hollow Stalker's spots, the crypt's vault, the Hollow Crypt itself, the sounds
 * ({@code tools/sound/crypt.py}) and the damage types. Textures and models come from {@code tools/art/gen_crypt.py};
 * names and subtitles live in {@code assets/cosmicbreach_crypt/lang/en_us.json}.
 */
public final class CryptRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);

    /** A Discord on the Choir Floor: no attacker (a dash's i-frames don't slip a wrong note). */
    public static final ResourceKey<DamageType> DISCORD = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("discord"));
    public static final ResourceKey<DamageType> STAR_ROCK = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("star_rock"));
    public static final ResourceKey<DamageType> GRAVITY_PISTON = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("gravity_piston"));
    /** The crypt vault's loot: T3, ending in the charms' sub-table for the Curios task. */
    public static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("vaults/crypt"));

    // ------------------------------------------------------------------ the Choir Floor

    public static final DeferredBlock<ConductorBlock> CONDUCTOR = block("conductor", ConductorBlock::new,
            puzzle(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 4), true);
    public static final DeferredBlock<ConductorTopBlock> CONDUCTOR_TOP = block("conductor_top", ConductorTopBlock::new,
            puzzle(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 3), false);
    public static final DeferredBlock<Block> RESONANCE_FLOOR = block("resonance_floor", Block::new,
            solid(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE), true);
    public static final DeferredBlock<VaultBlock> CRYPT_VAULT = block("crypt_vault", p -> new VaultBlock(p, 3),
            puzzle(MapColor.COLOR_PURPLE, SoundType.VAULT).lightLevel(s -> s.getValue(VaultBlock.READY) ? 12 : 4), true);

    // ------------------------------------------------------------------ the traps

    public static final DeferredBlock<VoidRiftTileBlock> VOID_RIFT_TILE = block("void_rift_tile", VoidRiftTileBlock::new,
            puzzle(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE), true);
    public static final DeferredBlock<VoidPocketBlock> VOID_POCKET = block("void_pocket", VoidPocketBlock::new,
            solid(MapColor.COLOR_PURPLE, SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 5), true);
    public static final DeferredBlock<Block> POCKET_SEAL = block("pocket_seal", Block::new,
            solid(MapColor.COLOR_PURPLE, SoundType.AMETHYST).lightLevel(s -> 6), true);
    public static final DeferredBlock<GravitySigilBlock> GRAVITY_SIGIL = block("gravity_sigil", GravitySigilBlock::new,
            solid(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE), true);
    public static final DeferredBlock<GravityPistonBlock> GRAVITY_PISTON_BLOCK = block("gravity_piston", GravityPistonBlock::new,
            solid(MapColor.COLOR_BLACK, SoundType.BASALT), true);
    public static final DeferredBlock<StarfallChuteBlock> STARFALL_CHUTE = block("starfall_chute", StarfallChuteBlock::new,
            puzzle(MapColor.COLOR_BLACK, SoundType.BASALT).lightLevel(s -> 3), true);
    public static final DeferredBlock<StalkerMarkerBlock> STALKER_MARKER = block("stalker_marker", StalkerMarkerBlock::new,
            technical(), false);
    /** W5's Kinetic Tripwire emitter in the Deep's stone: the same trap, dark enough not to give itself away. */
    public static final DeferredBlock<com.cosmicbreach.structure.trap.KineticEmitterBlock> UMBRAL_EMITTER = block("umbral_emitter",
            com.cosmicbreach.structure.trap.KineticEmitterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(8f, 1200f).sound(SoundType.DEEPSLATE_BRICKS).requiresCorrectToolForDrops().lightLevel(s -> 2), true);

    // ------------------------------------------------------------------ block entities

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConductorBlockEntity>> CONDUCTOR_ENTITY =
            BLOCK_ENTITIES.register("conductor", () -> BlockEntityType.Builder.of(ConductorBlockEntity::new, CONDUCTOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidRiftBlockEntity>> VOID_RIFT_ENTITY =
            BLOCK_ENTITIES.register("void_rift", () -> BlockEntityType.Builder.of(VoidRiftBlockEntity::new, VOID_RIFT_TILE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidPocketBlockEntity>> VOID_POCKET_ENTITY =
            BLOCK_ENTITIES.register("void_pocket", () -> BlockEntityType.Builder.of(VoidPocketBlockEntity::new, VOID_POCKET.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityPistonBlockEntity>> GRAVITY_PISTON_ENTITY =
            BLOCK_ENTITIES.register("gravity_piston", () -> BlockEntityType.Builder.of(GravityPistonBlockEntity::new,
                    GRAVITY_PISTON_BLOCK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarfallChuteBlockEntity>> STARFALL_CHUTE_ENTITY =
            BLOCK_ENTITIES.register("starfall_chute", () -> BlockEntityType.Builder.of(StarfallChuteBlockEntity::new,
                    STARFALL_CHUTE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StalkerMarkerBlock.Spot>> STALKER_MARKER_ENTITY =
            BLOCK_ENTITIES.register("stalker_marker", () -> BlockEntityType.Builder.of(StalkerMarkerBlock.Spot::new,
                    STALKER_MARKER.get()).build(null));

    // ------------------------------------------------------------------ sounds

    /** The eight pads' notes, D4 E4 F#4 A4 B4 D5 E5 F#5 ("Chime, first pad" ... "Chime, eighth pad"). */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> PAD_NOTES = padNotes();
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_CALL = sound("choir/call");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_TICK = sound("choir/tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_DISCORD = sound("choir/discord");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_ROUND = sound("choir/round");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_SOLVE = sound("choir/solve");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHOIR_ROTATE = sound("choir/rotate");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_CRACK = sound("crypt/rift_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_FALL = sound("crypt/rift_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL_OPEN = sound("crypt/seal_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_HUM = sound("crypt/plate_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTON_SLAM = sound("crypt/piston");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHUTE_GLINT = sound("crypt/chute_glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHUTE_IMPACT = sound("crypt/chute_impact");

    // ------------------------------------------------------------------ the structure

    public static final DeferredHolder<StructureType<?>, StructureType<HollowCryptStructure>> HOLLOW_CRYPT =
            STRUCTURE_TYPES.register("hollow_crypt", () -> () -> HollowCryptStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> CRYPT_PIECE =
            STRUCTURE_PIECES.register("hollow_crypt", () -> (StructurePieceType.ContextlessType) CryptPiece::new);

    private CryptRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
    }

    /** Puzzle and trap blocks can't be broken or pushed and drop nothing (GDD 6.2's anti-cheese, for 6.3 and 6.4 too). */
    private static BlockBehaviour.Properties puzzle(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).sound(sound).noOcclusion();
    }

    /** The same, but a full solid cube that hides its neighbours' faces (floors, walls, ceilings). */
    private static BlockBehaviour.Properties solid(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).sound(sound);
    }

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

    private static List<DeferredHolder<SoundEvent, SoundEvent>> padNotes() {
        List<DeferredHolder<SoundEvent, SoundEvent>> out = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            out.add(sound("choir/pad_" + i));
        }
        return List.copyOf(out);
    }
}
