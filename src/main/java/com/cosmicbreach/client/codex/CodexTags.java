package com.cosmicbreach.client.codex;

import com.cosmicbreach.client.progression.AttunementScreen;
import com.cosmicbreach.codex.Codices;
import com.cosmicbreach.codex.CodexProgress;
import com.cosmicbreach.progression.Attunement;
import com.cosmicbreach.progression.Attunements;
import com.cosmicbreach.structure.array.LensArrays;
import com.cosmicbreach.structure.array.LensCoreBlockEntity;
import guideme.color.ColorValue;
import guideme.color.ConstantColor;
import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.PageCompiler;
import guideme.compiler.TagCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.compiler.tags.FlowTagCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytParagraph;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytFlowSpan;
import guideme.document.flow.LytFlowText;
import guideme.document.interaction.TextTooltip;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Codex's own GuideME tags (F1). Pages are compiled each time they are opened, so each reads the player's state
 * as it is then:
 *
 * <ul>
 *   <li>{@code <Gate when="drift">...</Gate>}: its content only once the condition holds (see
 *       {@link CodexProgress#satisfies}); before that one italic line, the {@code sealed} attribute's text or the
 *       default "not yet written" ({@code sealed="none"} shows nothing). Gated content is kept out of the search
 *       index. Works in a block or inside a paragraph.</li>
 *   <li>{@code <LensHint />}: the Lens Array page's hint: a link when the nearest room offers one.</li>
 *   <li>{@code <AttunementStatus />}: the player's level, XP to the next and unspent points.</li>
 *   <li>{@code <AllocationLink>text</AllocationLink>}: a link that opens the allocation screen.</li>
 * </ul>
 */
public final class CodexTags {
    /** The sealed-line colour: a quiet violet grey (light and dark themes). */
    static final ColorValue SEALED = new ConstantColor(0xFF6E6680, 0xFFA9A1BD);

    private CodexTags() {
    }

    static CodexProgress progress() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? CodexProgress.NONE : Codices.of(mc.player);
    }

    static LytParagraph quiet(String text) {
        LytParagraph p = new LytParagraph();
        LytFlowSpan span = new LytFlowSpan();
        span.modifyStyle(b -> b.italic(true).color(SEALED));
        span.append(LytFlowText.of(text));
        p.append(span);
        return p;
    }

    /** {@code <Gate when="...">}: content that waits for a chapter. */
    public static final class Gate implements TagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("Gate");
        }

        @Override
        public void compileBlockContext(PageCompiler compiler, LytBlockContainer parent, MdxJsxFlowElement el) {
            String when = el.getAttributeString("when", "");
            if (!CodexProgress.valid(when)) {
                parent.appendError(compiler, "Gate: unknown chapter in \"" + when + "\"", el);
                return;
            }
            if (progress().satisfies(when)) {
                compiler.compileBlockContext(el.children(), parent);
                return;
            }
            String sealed = el.getAttributeString("sealed", "");
            if (!"none".equals(sealed)) {
                parent.append(quiet(sealed.isEmpty() ? I18n.get("cosmicbreach.codex.unwritten") : sealed));
            }
        }

        @Override
        public void compileFlowContext(PageCompiler compiler, LytFlowParent parent, MdxJsxTextElement el) {
            String when = el.getAttributeString("when", "");
            if (!CodexProgress.valid(when)) {
                parent.appendError(compiler, "Gate: unknown chapter in \"" + when + "\"", el);
                return;
            }
            if (progress().satisfies(when)) {
                compiler.compileFlowContext(el.children(), parent);
                return;
            }
            String sealed = el.getAttributeString("sealed", "");
            if (!"none".equals(sealed) && !sealed.isEmpty()) {
                LytFlowSpan span = new LytFlowSpan();
                span.modifyStyle(b -> b.italic(true).color(SEALED));
                span.append(LytFlowText.of(sealed));
                parent.append(span);
            }
        }

        @Override
        public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
            // what a Gate hides stays out of the search, so a search never gives away a later chapter
        }
    }

    /** {@code <LensHint />}: offers the nearest Lens Array's hint once its wait is over. */
    public static final class LensHint extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("LensHint");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            Minecraft mc = Minecraft.getInstance();
            LensCoreBlockEntity core = mc.player == null ? null : LensArrays.nearest(mc.player);
            if (core == null || core.phase() != LensCoreBlockEntity.ACTIVE) {
                parent.append(quiet(I18n.get("cosmicbreach.codex.hint.page_none")));
                return;
            }
            if (!core.hintOffered()) {
                parent.append(quiet(I18n.get("cosmicbreach.codex.hint.page_wait")));
                return;
            }
            LytParagraph p = new LytParagraph();
            LytFlowLink link = new LytFlowLink();
            link.append(LytFlowText.of(I18n.get("cosmicbreach.codex.hint.page_offer")));
            link.setTooltip(new TextTooltip(Component.translatable("cosmicbreach.codex.hint.page_tooltip")));
            link.setClickCallback(host -> {
                PacketDistributor.sendToServer(Codices.HintPayload.INSTANCE);
                Minecraft.getInstance().setScreen(null);
            });
            p.append(link);
            parent.append(p);
        }
    }

    /** {@code <AttunementStatus />}: where the player stands on the Attunement curve. */
    public static final class AttunementStatus extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("AttunementStatus");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            Attunement a = Attunements.of(mc.player);
            LytParagraph p = new LytParagraph();
            LytFlowSpan span = new LytFlowSpan();
            span.modifyStyle(b -> b.bold(true));
            span.append(LytFlowText.of(I18n.get("cosmicbreach.codex.status.level", a.level())));
            p.append(span);
            p.append(LytFlowText.of(" "));
            p.append(LytFlowText.of(a.isMaxLevel() ? I18n.get("cosmicbreach.codex.status.max")
                    : I18n.get("cosmicbreach.codex.status.xp", String.format(java.util.Locale.ROOT, "%,d", a.xp()),
                    String.format(java.util.Locale.ROOT, "%,d", a.xpToNext()))));
            int unspent = a.unspent();
            if (unspent > 0) {
                p.append(LytFlowText.of(" "));
                p.append(LytFlowText.of(I18n.get(unspent == 1 ? "cosmicbreach.codex.status.point" : "cosmicbreach.codex.status.points", unspent)));
            }
            parent.append(p);
        }
    }

    /** {@code <AllocationLink>text</AllocationLink>}: opens the allocation screen. */
    public static final class AllocationLink extends FlowTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("AllocationLink");
        }

        @Override
        protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
            LytFlowLink link = new LytFlowLink();
            link.setTooltip(new TextTooltip(Component.translatable("cosmicbreach.codex.allocation_tooltip")));
            link.setClickCallback(host -> AttunementScreen.open());
            compiler.compileFlowContext(el.children(), link);
            parent.append(link);
        }
    }
}
