package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.codex.CodexNavigation;
import guideme.internal.MutableGuide;
import guideme.navigation.NavigationTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Starfall Codex shows only the chapters a player has opened (F1): GuideME builds one navigation tree from every
 * page, so the Codex's tree is filtered on the way out ({@link CodexNavigation}). Other guides are untouched.
 */
@Mixin(value = MutableGuide.class, remap = false)
public abstract class GuideNavigationMixin {
    @Inject(method = "getNavigationTree", at = @At("RETURN"), cancellable = true)
    private void cosmicbreach$codexChapters(CallbackInfoReturnable<NavigationTree> cir) {
        MutableGuide self = (MutableGuide) (Object) this;
        if (CodexNavigation.isCodex(self.getId()) && cir.getReturnValue() != null) {
            cir.setReturnValue(CodexNavigation.filter(self, cir.getReturnValue()));
        }
    }
}
