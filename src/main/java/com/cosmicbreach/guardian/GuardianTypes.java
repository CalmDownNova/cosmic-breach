package com.cosmicbreach.guardian;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.colossus.CrownSpireStructure;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.world.Layer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** The guardians registered so far (GDD 7.2). The Leviathan and the Unsung add theirs here. */
public final class GuardianTypes {
    public static final ResourceKey<Structure> CROWN_SPIRE = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("crown_spire"));

    /** The Prism Colossus of the Crown Spire: its first kill ("Refracted") attunes to the Drift, and the Starfall's voice hears one more voice. */
    public static final GuardianType COLOSSUS = GuardianType.register(new GuardianType(CosmicBreach.id("prism_colossus"), "colossus",
            Layer.DRIFT, CosmicBreach.id("guardian/refracted"), CROWN_SPIRE, PrismColossus::spawnDormant, CrownSpireStructure::siteFor,
            EchoLine.DRIFT));

    /** The Thalassine Leviathan of the Leviathan Rift: its first kill ("Moored") attunes to the Deep. */
    public static final GuardianType LEVIATHAN = GuardianType.register(new GuardianType(CosmicBreach.id("thalassine_leviathan"), "leviathan",
            Layer.DEEP, CosmicBreach.id("guardian/moored"), com.cosmicbreach.guardian.leviathan.LeviathanRiftStructure.KEY,
            com.cosmicbreach.guardian.leviathan.ThalassineLeviathan::spawnDormant, com.cosmicbreach.guardian.leviathan.LeviathanRiftStructure::siteFor,
            EchoLine.DEEP));

    /** The Unsung of the Silent Nave: its first kill ("Unsung") attunes to the Breach Sanctum, which is no layer (its own kill grants it). */
    public static final GuardianType UNSUNG = GuardianType.register(new GuardianType(CosmicBreach.id("unsung"), "unsung",
            null, CosmicBreach.id("guardian/unsung"), com.cosmicbreach.guardian.unsung.UnsungRegistry.SILENT_NAVE,
            com.cosmicbreach.guardian.unsung.Unsung::spawnDormant, com.cosmicbreach.guardian.unsung.SilentNaveStructure::siteFor, EchoLine.SANCTUM));

    private GuardianTypes() {
    }

    /** Loads the class (and so registers every type) at mod construction. */
    public static void init() {
    }
}
