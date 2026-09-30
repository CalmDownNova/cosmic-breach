package com.cosmicbreach.combat.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Fills the server's {@link CombatData} from data packs, on world load and on {@code /reload}.
 * Two listeners, one per folder: {@code data/<ns>/combat/moves/} (any depth, so
 * {@code moves/meridian/l1.json} is {@code <ns>:meridian/l1}) and {@code data/<ns>/combat/weapons/}.
 */
public final class CombatDataLoader {
    public static final String MOVES_DIRECTORY = "combat/moves";
    public static final String WEAPONS_DIRECTORY = "combat/weapons";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private CombatDataLoader() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Moves());
        event.addListener(new Weapons());
    }

    static final class Moves extends SimpleJsonResourceReloadListener {
        Moves() {
            super(GSON, MOVES_DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, MoveDef> moves = CombatData.parseAll(files, MoveDef.CODEC, "move", LOGGER);
            CombatData.server().setMoves(moves);
            LOGGER.debug("[cosmicbreach] loaded {} combat moves ({} files)", moves.size(), files.size());
        }
    }

    static final class Weapons extends SimpleJsonResourceReloadListener {
        Weapons() {
            super(GSON, WEAPONS_DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, WeaponDef> weapons = CombatData.parseAll(files, WeaponDef.CODEC, "weapon", LOGGER);
            CombatData.server().setWeapons(weapons);
            LOGGER.debug("[cosmicbreach] loaded {} combat weapons ({} files)", weapons.size(), files.size());
        }
    }
}
