package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Starfall Codex (GDD 9.2): a GuideME guide, {@code cosmicbreach:codex}, defined by
 * {@code assets/cosmicbreach/guideme_guides/codex.json}, its pages Markdown in
 * {@code assets/cosmicbreach/guides/cosmicbreach/codex/}. A page's id is its file name there, in the
 * {@code cosmicbreach} namespace ({@code cosmicbreach:build_a_ring.md}).
 *
 * <p>For later tasks: add a page by dropping a Markdown file in that folder (frontmatter {@code navigation:
 * title, parent, position}); open the Codex at a page from the server with {@link #open}.
 */
public final class Codex {
    public static final ResourceLocation GUIDE = CosmicBreach.id("codex");
    public static final ResourceLocation WELCOME = page("index");
    public static final ResourceLocation STARFALL = page("starfall");
    public static final ResourceLocation BUILD_A_RING = page("build_a_ring");
    public static final ResourceLocation FALL_UP = page("fall_up");
    public static final ResourceLocation LANDING = page("landing");

    private Codex() {
    }

    /** The id of the page in file {@code <name>.md}. */
    public static ResourceLocation page(String name) {
        return CosmicBreach.id(name + ".md");
    }

    /** Opens the Codex for {@code player} at {@code page}, or where they last left it if null. Server side. */
    public static void open(ServerPlayer player, @Nullable ResourceLocation page) {
        if (page == null) {
            GuidesCommon.openGuide(player, GUIDE);
        } else {
            GuidesCommon.openGuide(player, GUIDE, PageAnchor.page(page));
        }
    }

    /** Opens the Codex the way its item does: at "Build a ring" the first time, after that where it was left. */
    public static void openFromItem(ServerPlayer player) {
        OnboardingState state = player.getData(OnboardingRegistry.STATE);
        if (!state.codexOpened()) {
            player.setData(OnboardingRegistry.STATE, state.withCodexOpened());
            open(player, BUILD_A_RING);
        } else {
            open(player, null);
        }
    }

    /** Gives {@code player} a Codex (into the inventory, or at their feet if it is full) and remembers it. */
    public static void give(ServerPlayer player) {
        ItemStack codex = new ItemStack(OnboardingRegistry.STARFALL_CODEX.get());
        if (!player.getInventory().add(codex)) {
            player.drop(codex, false);
        }
        player.setData(OnboardingRegistry.STATE, player.getData(OnboardingRegistry.STATE).withCodexGiven());
        player.displayClientMessage(Component.translatable("cosmicbreach.codex.given"), true);
    }

    /** True if {@code player} carries a Codex. */
    public static boolean carries(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(OnboardingRegistry.STARFALL_CODEX.get()));
    }
}
