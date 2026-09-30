package com.cosmicbreach.client.codex;

import com.cosmicbreach.codex.CodexProgress;
import com.cosmicbreach.onboarding.Codex;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import guideme.PageCollection;
import guideme.compiler.ParsedGuidePage;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Which Codex pages the navigation shows (F1, GDD 9.4: chapters that unlock with progress). A page's frontmatter
 * may say {@code unlock: <condition>} (see {@link CodexProgress#satisfies}): until it holds, the page is left out of
 * the navigation, and out of search results, with everything under it; or, if it also says
 * {@code sealed_title: <title>}, it stays listed under that title with a torn-page icon (a guardian's page before
 * the guardian is met). GuideME asks for the tree every frame, so the filtered tree is cached per source tree and
 * chapter bits: a new instance only when either changes (the navigation bar rebuilds its rows on a new one).
 */
public final class CodexNavigation {
    private static NavigationTree cachedSource;
    private static long cachedBits = -1;
    private static NavigationTree cached;

    private CodexNavigation() {
    }

    /** True if GuideME's guide {@code id} is the Codex. */
    public static boolean isCodex(ResourceLocation id) {
        return Codex.GUIDE.equals(id);
    }

    public static synchronized NavigationTree filter(PageCollection guide, NavigationTree source) {
        CodexProgress progress = CodexTags.progress();
        if (source == cachedSource && progress.bits() == cachedBits && cached != null) {
            return cached;
        }
        Map<ResourceLocation, NavigationNode> index = new HashMap<>();
        List<NavigationNode> roots = new ArrayList<>();
        for (NavigationNode root : source.getRootNodes()) {
            NavigationNode kept = keep(guide, root, progress, index);
            if (kept != null) {
                roots.add(kept);
            }
        }
        cachedSource = source;
        cachedBits = progress.bits();
        cached = new NavigationTree(index, roots);
        return cached;
    }

    /** True if the page may be opened from search and links: its condition holds, or it has none. */
    public static boolean open(PageCollection guide, ResourceLocation pageId) {
        String unlock = property(guide, pageId, "unlock");
        return unlock == null || CodexTags.progress().satisfies(unlock);
    }

    /** True if the page can be reached at all right now: open, or listed under its sealed title. */
    public static boolean reachable(PageCollection guide, ResourceLocation pageId) {
        return open(guide, pageId) || property(guide, pageId, "sealed_title") != null;
    }

    private static @Nullable NavigationNode keep(PageCollection guide, NavigationNode node, CodexProgress progress,
            Map<ResourceLocation, NavigationNode> index) {
        String unlock = property(guide, node.pageId(), "unlock");
        NavigationNode out;
        if (unlock != null && !progress.satisfies(unlock)) {
            String sealed = property(guide, node.pageId(), "sealed_title");
            if (sealed == null) {
                return null;
            }
            ItemStack icon = new ItemStack(OnboardingRegistry.TORN_CODEX_PAGE.get());
            out = new NavigationNode(node.pageId(), sealed, icon, List.of(), node.position(), node.hasPage());
        } else {
            List<NavigationNode> children = new ArrayList<>();
            for (NavigationNode child : node.children()) {
                NavigationNode kept = keep(guide, child, progress, index);
                if (kept != null) {
                    children.add(kept);
                }
            }
            out = new NavigationNode(node.pageId(), node.title(), node.icon(), node.iconFactory(), children, node.position(), node.hasPage());
        }
        if (out.pageId() != null) {
            index.put(out.pageId(), out);
        }
        return out;
    }

    private static @Nullable String property(PageCollection guide, @Nullable ResourceLocation pageId, String key) {
        if (pageId == null) {
            return null;
        }
        ParsedGuidePage page = guide.getParsedPage(pageId);
        if (page == null) {
            return null;
        }
        Object value = page.getFrontmatter().additionalProperties().get(key);
        return value == null ? null : value.toString();
    }
}
