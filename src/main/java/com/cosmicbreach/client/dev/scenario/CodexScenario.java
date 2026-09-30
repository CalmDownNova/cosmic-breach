package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.codex.CodexNavigation;
import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.client.progression.AttunementScreen;
import com.cosmicbreach.codex.CodexChapter;
import com.cosmicbreach.codex.Codices;
import com.cosmicbreach.codex.LairMote;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.GuardianTypes;
import com.cosmicbreach.onboarding.Codex;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.cosmicbreach.world.AetheriaWorld;
import guideme.Guide;
import guideme.GuidePage;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.color.SymbolicColor;
import guideme.compiler.ParsedGuidePage;
import guideme.document.block.LytNode;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowSpan;
import guideme.internal.GuideMEClient;
import guideme.internal.screen.GuideScreen;
import guideme.internal.search.GuideSearch;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The Starfall Codex, complete (F1): the guide is built in code with its tags; every page compiles without errors
 * with no chapter open and with all of them; the navigation shows only open chapters, a guardian's page sealed until
 * met; search never lists a closed page; the allocation link opens the screen; sneak-using the book in Aetheria
 * throws a mote toward the nearest reachable, undefeated guardian's lair, then the next, then the Breach, then
 * nothing; screenshots of the pages with scenes. The Lens Array hint is tested in {@code structures}.
 */
public final class CodexScenario implements Scenario {
    private final List<String> summary = new ArrayList<>();

    @Override
    public int timeBudgetSeconds() {
        return 600;
    }

    @Override
    public boolean generateStructures() {
        return true;
    }

    @Override
    public void steps(Steps steps) {
        Minecraft mc = Minecraft.getInstance();
        long[] mark = {0};
        steps.check("the Codex is a guide built in code, with its tags", () -> Guides.getById(Codex.GUIDE) != null
                        && guide().getExtensions().get(guideme.compiler.TagCompiler.EXTENSION_POINT).stream()
                        .anyMatch(t -> t.getTagNames().contains("Gate")))
                .log("pages", () -> guide().getPages().size() + " pages")
                .check("every page listed", () -> guide().getPages().size() >= 45)
                .command("cosmicbreach codex reset")
                .waitUntil("no chapter open on this client", 40, () -> Codices.of(mc.player).bits() == 0)
                .run("every page compiles, no chapter open", () -> compileAll("closed"))
                .check("closed: only the welcome and the way up", () -> roots().equals(List.of("index", "way_up")))
                .check("the way up has its four pages", () -> node("way_up").children().size() == 4)
                .check("a guardian's page keeps its secret when opened by link", () -> text("prism_colossus").contains("A sealed page")
                        && !text("prism_colossus").contains("Prism Slam"))
                .check("the index keeps the later chapters' names to itself", () -> !text("index").contains("Breach Sanctum"))

                .command("cosmicbreach codex unlock arrived")
                .waitUntil("the Reach opens on this client", 40, () -> Codices.of(mc.player).has(CodexChapter.ARRIVED))
                .check("the Reach and the systems appear", () -> roots().containsAll(List.of("reach", "combat", "attunement", "forge",
                        "arms", "armor", "accessories", "companions", "weather")) && !roots().contains("drift") && !roots().contains("deep"))
                .check("the Colossus is listed, sealed, under a plain title", () -> "A sealed page".equals(node("prism_colossus").title())
                        && node("prism_colossus").children().isEmpty())
                .check("pages for the lower layers stay hidden", () -> node("comet_maul") == null && node("driftweave") == null)

                .command("cosmicbreach codex unlock met_colossus")
                .waitUntil("met on this client", 40, () -> Codices.of(mc.player).has(CodexChapter.MET_COLOSSUS))
                .check("its page takes its name", () -> "The Prism Colossus".equals(node("prism_colossus").title()))
                .check("and its words", () -> text("prism_colossus").contains("Prism Slam") && !text("prism_colossus").contains("A sealed page"))

                .command("cosmicbreach codex unlock all")
                .waitUntil("every chapter open on this client", 40, () -> Codices.of(mc.player).has(CodexChapter.SEALED))
                .run("every page compiles, every chapter open", () -> compileAll("open"))
                .check("every page is in the navigation", () -> count(tree().getRootNodes()) == guide().getPages().size())
                .check("the index names every chapter now", () -> text("index").contains("Breach Sanctum") && text("index").contains("The Deep"))
                .check("the Attunement page shows the level", () -> text("attunement").contains("Level "))

                // search: GuideME indexes a guide when its search screen opens, over a few ticks
                .run("index the Codex as its search screen does", () -> GuideMEClient.instance().getSearch().index(guide()))
                .waitUntil("the search index is built", 400, () -> !search("Starfall").isEmpty())
                .check("an open chapter is found", () -> search("Leviathan").stream().anyMatch(r -> r.pageId().getPath().equals("thalassine_leviathan.md")))
                .command("cosmicbreach codex reset")
                .waitUntil("closed again on this client", 40, () -> Codices.of(mc.player).bits() == 0)
                .check("a closed chapter is never found", () -> search("Leviathan").stream().allMatch(r -> CodexNavigation.open(guide(), r.pageId()))
                        && search("Leviathan").stream().noneMatch(r -> r.pageId().getPath().equals("thalassine_leviathan.md")))
                .command("cosmicbreach codex unlock all")
                .waitUntil("open again", 40, () -> Codices.of(mc.player).has(CodexChapter.SEALED))

                // the pages as a player sees them
                .run("the HUD hidden", () -> mc.options.hideGui = true);
        for (String page : List.of("index", "lens_array", "choir_floor", "forge", "attunement", "prism_colossus", "heliarch")) {
            steps.run("open the Codex at " + page, () -> mc.setScreen(GuideScreen.openNew(guide(), PageAnchor.page(Codex.page(page)))))
                    .waitTicks(15)
                    .screenshot("codex_" + page);
        }
        steps.run("open the attunement page", () -> mc.setScreen(GuideScreen.openNew(guide(), PageAnchor.page(Codex.page("attunement")))))
                .waitTicks(5)
                .run("click its allocation link", () -> {
                    GuideScreen screen = (GuideScreen) mc.screen;
                    LytFlowLink link = firstLink(guide().getPage(Codex.page("attunement")));
                    if (link == null) {
                        throw new Steps.Failure("the attunement page has no link");
                    }
                    link.mouseClicked(screen, 0, 0, 0);
                })
                .waitTicks(2)
                .check("the allocation screen opened", () -> mc.screen instanceof AttunementScreen)
                .run("close it", () -> mc.setScreen(null))
                .run("the HUD on", () -> mc.options.hideGui = false);

        // the lair mote
        steps.command("gamemode survival")
                .command("effect give @s minecraft:resistance 100000 4 true")
                .command("cosmicbreach debug goto reach")
                .waitUntil("in Aetheria", 400, () -> AetheriaWorld.is(mc.level))
                .waitTicks(40)
                .command("clear @s")
                .command("give @s cosmicbreach:starfall_codex")
                .waitUntil("the Codex in the hotbar", 60, () -> slotOf(mc) >= 0)
                .run("take it in hand", () -> KeyMapping.click(mc.options.keyHotbarSlots[slotOf(mc)].getKey()))
                .waitUntil("the Codex in hand", 20, () -> mc.player.getMainHandItem().is(OnboardingRegistry.STARFALL_CODEX.get()))
                .run("reset the lair locator", () -> ServerQuery.ask(p -> {
                    LairMote.reset();
                    return true;
                }));
        sneakUse(steps, mc);
        steps.waitUntil("a mote flies", 40, () -> ServerQuery.ask(p -> LairMote.live() > 0))
                .check("the book stayed shut", () -> mc.screen == null)
                .check("toward the nearest Colossus lair", () -> ServerQuery.ask(p -> {
                    LairMote.Candidate t = LairMote.lastTarget();
                    return t != null && t.name().equals("colossus") && GuardianLairs.nearest((ServerLevel) p.level(), p.blockPosition(),
                            GuardianTypes.COLOSSUS).map(c -> c.equals(t.centre())).orElse(false);
                }))
                .log("mote", () -> ServerQuery.ask(p -> String.format(Locale.ROOT, "the first mote flew toward the colossus lair at %s, %.0f blocks away",
                        LairMote.lastTarget().centre().toShortString(), Math.sqrt(p.blockPosition().distSqr(LairMote.lastTarget().centre())))))
                .run("mark", () -> mark[0] = mc.level.getGameTime())
                .waitUntil("the mote fades", 120, () -> ServerQuery.ask(p -> LairMote.live() == 0))
                .log("life", () -> "it faded after " + (mc.level.getGameTime() - mark[0]) + " ticks in flight past the check")
                // the Colossus beaten and the Drift attuned: the Leviathan's lair is next
                .command("advancement grant @s only cosmicbreach:guardian/refracted")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .waitTicks(50);
        sneakUse(steps, mc);
        steps.waitUntil("the next mote flies toward the Leviathan's Rift", 40, () -> ServerQuery.ask(p ->
                        LairMote.lastTarget() != null && LairMote.lastTarget().name().equals("leviathan")))
                .command("advancement grant @s only cosmicbreach:guardian/moored")
                .command("advancement grant @s only cosmicbreach:attunement/deep")
                .command("advancement grant @s only cosmicbreach:guardian/unsung")
                .command("advancement grant @s only cosmicbreach:attunement/sanctum")
                .waitTicks(50);
        sneakUse(steps, mc);
        steps.waitUntil("with every guardian beaten it points to the Breach", 40, () -> ServerQuery.ask(p ->
                        LairMote.lastTarget() != null && LairMote.lastTarget().name().equals("sanctum")))
                .command("advancement grant @s only cosmicbreach:guardian/breach_sealed")
                .waitTicks(50);
        sneakUse(steps, mc);
        steps.waitTicks(5)
                .check("and once the Breach is sealed, the book is quiet", () -> ServerQuery.ask(p -> LairMote.lastTarget() == null))
                .check("the kills opened the chapters", () -> Codices.of(mc.player).has(CodexChapter.SEALED)
                        && Codices.of(mc.player).has(CodexChapter.MET_UNSUNG))
                .run("summary", () -> summary.forEach(s -> com.cosmicbreach.CosmicBreach.LOGGER.info("[autotest] {}", s)));
    }

    /** Sneaks and uses the item in hand, with the real keys. */
    private static void sneakUse(Steps steps, Minecraft mc) {
        steps.hold(mc.options.keyShift)
                .waitTicks(3)
                .press(mc.options.keyUse)
                .waitTicks(2)
                .release(mc.options.keyShift);
    }

    private static int slotOf(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(OnboardingRegistry.STARFALL_CODEX.get())) {
                return i;
            }
        }
        return -1;
    }

    private static Guide guide() {
        return Guides.getById(Codex.GUIDE);
    }

    private static NavigationTree tree() {
        return guide().getNavigationTree();
    }

    private static List<String> roots() {
        List<String> out = new ArrayList<>();
        for (NavigationNode n : tree().getRootNodes()) {
            out.add(n.pageId().getPath().replace(".md", ""));
        }
        return out;
    }

    private static NavigationNode node(String page) {
        return tree().getNodeById(Codex.page(page));
    }

    private static int count(List<NavigationNode> nodes) {
        int n = 0;
        for (NavigationNode node : nodes) {
            n += 1 + count(node.children());
        }
        return n;
    }

    private static String text(String page) {
        GuidePage p = guide().getPage(Codex.page(page));
        return p == null ? "" : p.document().getTextContent();
    }

    private static List<GuideSearch.SearchResult> search(String query) {
        return GuideMEClient.instance().getSearch().searchGuide(query, guide());
    }

    /** Compiles every page and fails on any error GuideME wrote into one (an unknown tag, id, item or chapter). */
    private void compileAll(String state) {
        List<String> errors = new ArrayList<>();
        int compiled = 0;
        for (ParsedGuidePage parsed : guide().getPages()) {
            ResourceLocation id = parsed.getId();
            if (!CodexNavigation.reachable(guide(), id)) {
                continue; // a closed chapter: in no list, no search, and no link that is open points at it
            }
            compiled++;
            GuidePage page = guide().getPage(id);
            if (page == null) {
                errors.add(id + ": did not compile");
                continue;
            }
            int[] bad = {0};
            StringBuilder what = new StringBuilder();
            page.document().visit(new LytVisitor() {
                @Override
                public Result beforeFlowContent(LytFlowContent content) {
                    if (content instanceof LytFlowSpan span && span.getStyle().color() == SymbolicColor.ERROR_TEXT) {
                        bad[0]++;
                        if (what.length() < 200) {
                            what.append(' ').append(textOf(span));
                        }
                    }
                    return Result.CONTINUE;
                }
            });
            if (bad[0] > 0) {
                errors.add(id.getPath() + ":" + what);
            }
        }
        if (!errors.isEmpty()) {
            throw new Steps.Failure("Codex pages with errors (" + state + "): " + String.join(" | ", errors));
        }
        summary.add(compiled + " reachable Codex pages of " + guide().getPages().size() + " compile without errors, " + state);
    }

    private static String textOf(LytFlowSpan span) {
        StringBuilder sb = new StringBuilder();
        span.visit(new LytVisitor() {
            @Override
            public void text(String text) {
                sb.append(text);
            }
        });
        return sb.toString();
    }

    private static LytFlowLink firstLink(GuidePage page) {
        LytFlowLink[] found = {null};
        page.document().visit(new LytVisitor() {
            @Override
            public Result beforeFlowContent(LytFlowContent content) {
                if (found[0] == null && content instanceof LytFlowLink link && textOfLink(link).contains("open it from here")) {
                    found[0] = link;
                }
                return Result.CONTINUE;
            }
        });
        return found[0];
    }

    private static String textOfLink(LytFlowLink link) {
        StringBuilder sb = new StringBuilder();
        link.visit(new LytVisitor() {
            @Override
            public void text(String text) {
                sb.append(text);
            }
        });
        return sb.toString();
    }
}
