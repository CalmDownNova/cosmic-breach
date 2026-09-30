package com.cosmicbreach.client.codex;

import com.cosmicbreach.onboarding.Codex;
import guideme.Guide;
import guideme.GuideItemSettings;
import guideme.compiler.TagCompiler;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The Starfall Codex as a GuideME guide built in code (it was data-driven), so it can carry the tags its pages need:
 * {@code <Gate>} (content that waits for a chapter), {@code <LensHint/>}, {@code <AttunementStatus/>} and
 * {@code <AllocationLink>}. Its pages stay where they were ({@code assets/cosmicbreach/guides/cosmicbreach/codex/});
 * which pages the navigation shows is {@link CodexNavigation}'s. Built once, on the client, at mod construction.
 */
public final class CodexGuide {
    private static Guide guide;

    private CodexGuide() {
    }

    public static void build() {
        if (guide != null) {
            return;
        }
        guide = Guide.builder(Codex.GUIDE)
                .itemSettings(new GuideItemSettings(Optional.of(Component.translatable("item.cosmicbreach.starfall_codex")),
                        List.of(Component.translatable("item.cosmicbreach.starfall_codex.tooltip")), Optional.empty()))
                .extension(TagCompiler.EXTENSION_POINT, new CodexTags.Gate())
                .extension(TagCompiler.EXTENSION_POINT, new CodexTags.LensHint())
                .extension(TagCompiler.EXTENSION_POINT, new CodexTags.AttunementStatus())
                .extension(TagCompiler.EXTENSION_POINT, new CodexTags.AllocationLink())
                .build();
    }

    public static Guide guide() {
        return guide;
    }
}
