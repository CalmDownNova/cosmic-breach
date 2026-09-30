package com.cosmicbreach.client.sky;

import com.cosmicbreach.CosmicBreach;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Whether an Iris shader pack is drawing the world. Under a pack, Aetheria's sky and cloud hooks step
 * aside so the pack draws its own sky (the dimension id {@code cosmicbreach:aetheria} is public for pack
 * authors); our fog colours still apply. Iris is found by mod id and its public API by reflection, so it is
 * never a dependency.
 */
public final class ShaderPacks {
    /** Tests can pretend a pack is on (true) or off (false); null asks Iris. */
    public static volatile @Nullable Boolean forced;

    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static boolean looked;
    private static @Nullable Object api;
    private static @Nullable Method inUse;

    private ShaderPacks() {
    }

    public static boolean inUse() {
        Boolean f = forced;
        if (f != null) {
            return f;
        }
        if (!looked) {
            looked = true;
            find();
        }
        if (inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmicBreach.LOGGER.warn("[cosmicbreach] a shader mod's API call failed; the mod draws its own sky from now on", e);
            inUse = null;
            return false;
        }
    }

    private static void find() {
        ModList mods = ModList.get();
        if (mods == null || !(mods.isLoaded("iris") || mods.isLoaded("oculus"))) {
            return;
        }
        try {
            Class<?> type = Class.forName(API);
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
            CosmicBreach.LOGGER.debug("[cosmicbreach] Iris found: Aetheria's sky steps aside while a shader pack is on");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmicBreach.LOGGER.warn("[cosmicbreach] a shader mod is loaded but its API was not found; the mod draws its own sky", e);
            inUse = null;
        }
    }
}
