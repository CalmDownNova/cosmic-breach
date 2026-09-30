package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The settings (F1): the mod's config screen is NeoForge's, reached from the mod list; it lists the client settings
 * and the four per-world ones (the way in, weather, structures, the Crypt); every value and section has a readable
 * label; the main screen and each section open in a world. Screenshots of the list and each section.
 */
public final class ConfigScenario implements Scenario {
    private static final List<String> FILES = List.of("cosmicbreach-client.toml", "cosmicbreach-onboarding.toml",
            "cosmicbreach-weather.toml", "cosmicbreach-structures.toml", "cosmicbreach-crypt.toml");

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        List<ModConfig> configs = new ArrayList<>();
        steps.run("our configs", () -> {
                    for (ModConfig.Type type : ModConfig.Type.values()) {
                        for (ModConfig c : ModConfigs.getConfigSet(type)) {
                            if (c.getModId().equals(CosmicBreach.MOD_ID)) {
                                configs.add(c);
                            }
                        }
                    }
                })
                .log("configs", () -> configs.stream().map(c -> c.getType() + " " + c.getFileName()).toList().toString())
                .check("the client settings and the four per-world ones are registered", () -> configs.size() == FILES.size()
                        && configs.stream().map(ModConfig::getFileName).toList().containsAll(FILES))
                .check("all of them loaded in this world", () -> configs.stream().allMatch(c -> ((ModConfigSpec) c.getSpec()).isLoaded()))
                .run("every value and section has a label", () -> {
                    List<String> missing = new ArrayList<>();
                    for (ModConfig c : configs) {
                        String file = c.getFileName().replaceAll("[^a-zA-Z0-9]+", ".").toLowerCase(java.util.Locale.ROOT);
                        for (String key : List.of(CosmicBreach.MOD_ID + ".configuration.section." + file,
                                CosmicBreach.MOD_ID + ".configuration.section." + file + ".title")) {
                            if (!I18n.exists(key)) {
                                missing.add(key);
                            }
                        }
                        ModConfigSpec spec = (ModConfigSpec) c.getSpec();
                        walk(spec, spec.getSpec(), new ArrayList<>(), missing);
                    }
                    if (!I18n.exists(CosmicBreach.MOD_ID + ".configuration.title")) {
                        missing.add(CosmicBreach.MOD_ID + ".configuration.title");
                    }
                    if (!missing.isEmpty()) {
                        throw new Steps.Failure("config labels missing: " + missing);
                    }
                })
                .check("the mod list opens NeoForge's config screen for it", () -> container().getCustomExtension(IConfigScreenFactory.class).isPresent())
                .run("open it", () -> mc.setScreen(new ConfigurationScreen(container(), null)))
                .waitTicks(5)
                .check("it is open", () -> mc.screen instanceof ConfigurationScreen)
                .screenshot("config");
        for (String file : FILES) {
            steps.run("open " + file, () -> {
                        ModConfig c = configs.stream().filter(x -> x.getFileName().equals(file)).findFirst().orElseThrow();
                        ConfigurationScreen parent = new ConfigurationScreen(container(), null);
                        mc.setScreen(new ConfigurationScreen.ConfigurationSectionScreen(parent, c.getType(), c,
                                parent.translatableConfig(c, ".title", "")));
                    })
                    .waitTicks(5)
                    .check(file + " is open", () -> mc.screen instanceof ConfigurationScreen.ConfigurationSectionScreen)
                    .screenshot("config_" + file.replace("cosmicbreach-", "").replace(".toml", ""));
        }
        steps.run("close it", () -> mc.setScreen(null));
    }

    private static ModContainer container() {
        return ModList.get().getModContainerById(CosmicBreach.MOD_ID).orElseThrow();
    }

    private static void walk(ModConfigSpec spec, UnmodifiableConfig config, List<String> path, List<String> missing) {
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            List<String> here = new ArrayList<>(path);
            here.add(e.getKey());
            String fallback = CosmicBreach.MOD_ID + ".configuration." + e.getKey();
            if (e.getValue() instanceof ModConfigSpec.ValueSpec value) {
                String key = value.getTranslationKey() != null ? value.getTranslationKey() : fallback;
                if (!I18n.exists(key)) {
                    missing.add(key);
                }
            } else if (e.getValue() instanceof UnmodifiableConfig section) {
                String key = spec.getLevelTranslationKey(here) != null ? spec.getLevelTranslationKey(here) : fallback;
                if (!I18n.exists(key)) {
                    missing.add(key);
                }
                walk(spec, section, here, missing);
            }
        }
    }
}
