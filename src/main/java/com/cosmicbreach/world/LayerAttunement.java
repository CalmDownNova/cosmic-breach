package com.cosmicbreach.world;

import com.cosmicbreach.CosmicBreach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Attunement to a layer (GDD 2.1): what lets a player fall through a Shear band into the layer below. It is
 * two vanilla advancements with an impossible criterion, granted by the guardian kills (Prism Colossus: the
 * Drift; Thalassine Leviathan: the Deep) through {@link #grant}, or for tests with
 * {@code /advancement grant @s only cosmicbreach:attunement/drift}. Everyone is attuned to the Reach.
 *
 * <p>Not to be confused with {@code progression.Attunement}, the XP and levels.
 */
public final class LayerAttunement {
    public static final ResourceLocation DRIFT = CosmicBreach.id("attunement/drift");
    public static final ResourceLocation DEEP = CosmicBreach.id("attunement/deep");
    /**
     * Attunement to the Breach Sanctum (GDD 3.5: the Unsung's first kill): not a layer, the Sanctum's gate asks
     * {@link #hasSanctum}. Granted with the advancement "Unsung"; for tests
     * {@code /advancement grant @s only cosmicbreach:attunement/sanctum}.
     */
    public static final ResourceLocation SANCTUM = CosmicBreach.id("attunement/sanctum");

    private LayerAttunement() {
    }

    /** True if the player may enter the Breach Sanctum (killed the Unsung). Server side. */
    public static boolean hasSanctum(ServerPlayer player) {
        return done(player, SANCTUM);
    }

    /** Attunes the player to the Breach Sanctum. True if anything changed. */
    public static boolean grantSanctum(ServerPlayer player) {
        return complete(player, SANCTUM);
    }

    /** The advancement that attunes to {@code layer}, or null for the Reach (always open). */
    public static ResourceLocation advancement(Layer layer) {
        return switch (layer) {
            case REACH -> null;
            case DRIFT -> DRIFT;
            case DEEP -> DEEP;
        };
    }

    /** True if the player may fall into {@code layer}. Server side. */
    public static boolean has(ServerPlayer player, Layer layer) {
        ResourceLocation id = advancement(layer);
        return id == null || done(player, id);
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Attunes the player to {@code layer} (completes the advancement). True if anything changed. */
    public static boolean grant(ServerPlayer player, Layer layer) {
        ResourceLocation id = advancement(layer);
        return id != null && complete(player, id);
    }

    private static boolean complete(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        boolean changed = false;
        for (String criterion : remaining) {
            changed |= player.getAdvancements().award(holder, criterion);
        }
        return changed;
    }
}
