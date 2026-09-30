package com.cosmicbreach.combat.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;

/**
 * The loaded moves and weapons. There is one copy per logical side, so a singleplayer game (server
 * and client in one JVM) never mixes them: the server copy is filled by {@link CombatDataLoader} from
 * {@code data/<ns>/combat/moves/**.json} and {@code data/<ns>/combat/weapons/*.json}; the client copy
 * by the data sync payload on login and after {@code /reload}.
 */
public final class CombatData {
    private static final CombatData SERVER = new CombatData();
    private static final CombatData CLIENT = new CombatData();

    private volatile Map<ResourceLocation, MoveDef> moves = Map.of();
    private volatile Map<ResourceLocation, WeaponDef> weapons = Map.of();

    CombatData() {
    }

    public static CombatData server() {
        return SERVER;
    }

    public static CombatData client() {
        return CLIENT;
    }

    /** The copy for a level's side: {@code CombatData.forSide(level.isClientSide())}. */
    public static CombatData forSide(boolean clientSide) {
        return clientSide ? CLIENT : SERVER;
    }

    public @Nullable MoveDef move(ResourceLocation id) {
        return moves.get(id);
    }

    public @Nullable WeaponDef weapon(ResourceLocation id) {
        return weapons.get(id);
    }

    /** Every move by id (immutable). */
    public Map<ResourceLocation, MoveDef> moves() {
        return moves;
    }

    /** Every weapon by id, which is the id of the item that uses it (immutable). */
    public Map<ResourceLocation, WeaponDef> weapons() {
        return weapons;
    }

    public void setMoves(Map<ResourceLocation, MoveDef> newMoves) {
        this.moves = Map.copyOf(newMoves);
    }

    public void setWeapons(Map<ResourceLocation, WeaponDef> newWeapons) {
        this.weapons = Map.copyOf(newWeapons);
    }

    public void replace(Map<ResourceLocation, MoveDef> newMoves, Map<ResourceLocation, WeaponDef> newWeapons) {
        setMoves(newMoves);
        setWeapons(newWeapons);
    }

    /**
     * Parses every file with {@code codec}. A file that doesn't parse is logged with its id and
     * skipped; the rest still load.
     */
    public static <T> Map<ResourceLocation, T> parseAll(Map<ResourceLocation, JsonElement> files, Codec<T> codec,
                                                        String kind, Logger log) {
        Map<ResourceLocation, T> out = new TreeMap<>();
        files.forEach((id, json) -> {
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            if (result.result().isPresent()) {
                out.put(id, result.result().get());
            } else {
                log.error("Couldn't parse combat {} {}: {}", kind, id,
                        result.error().map(DataResult.Error::message).orElse("unknown error"));
            }
        });
        return out;
    }
}
