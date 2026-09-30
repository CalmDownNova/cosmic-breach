package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.codex.CodexNavigation;
import guideme.Guide;
import guideme.internal.search.GuideSearch;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Codex search never lists a page whose chapter has not opened yet ({@link CodexNavigation#open}). */
@Mixin(value = GuideSearch.class, remap = false)
public abstract class GuideSearchMixin {
    @Inject(method = "searchGuide", at = @At("RETURN"), cancellable = true)
    private void cosmicbreach$hideSealedPages(String query, Guide guide, CallbackInfoReturnable<List<GuideSearch.SearchResult>> cir) {
        if (guide == null || !CodexNavigation.isCodex(guide.getId()) || cir.getReturnValue() == null) {
            return;
        }
        List<GuideSearch.SearchResult> kept = new ArrayList<>();
        for (GuideSearch.SearchResult r : cir.getReturnValue()) {
            if (CodexNavigation.open(guide, r.pageId())) {
                kept.add(r);
            }
        }
        cir.setReturnValue(kept);
    }
}
