package com.cosmicbreach.client.dev;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin for {@code cosmicbreach.mixins.json}. The hidden-window mixins listed
 * below are only applied when {@code -Dcosmicbreach.hiddenWindow=true} is set (the test
 * client), so a normal game never carries them. Every other mixin in the config applies as
 * usual. The plugin lives outside the mixin package on purpose: Mixin refuses to load
 * ordinary classes from a mixin package.
 */
public final class HiddenWindowMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> HIDDEN_WINDOW_MIXINS = Set.of(
            "com.cosmicbreach.mixin.client.WindowHiddenMixin",
            "com.cosmicbreach.mixin.client.MinecraftNoDialogMixin");
    // Read directly (the constant is inlined) so the plugin never loads client classes on a server.
    private static final boolean HIDDEN_WINDOW_MODE = Boolean.getBoolean(HiddenWindow.PROPERTY);

    /** Pure decision, kept separate so a unit test can check both modes. */
    static boolean shouldApply(String mixinClassName, boolean hiddenWindowMode) {
        return hiddenWindowMode || !HIDDEN_WINDOW_MIXINS.contains(mixinClassName);
    }

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.debug("[cosmicbreach] hidden window mixins {}", HIDDEN_WINDOW_MODE ? "enabled" : "disabled");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApply(mixinClassName, HIDDEN_WINDOW_MODE);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
