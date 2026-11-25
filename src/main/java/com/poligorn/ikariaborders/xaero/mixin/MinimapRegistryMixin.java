package com.poligorn.ikariaborders.xaero.mixin;

import com.poligorn.ikariaborders.xaero.XaeroHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Same for the minimap registry (separate class and list in Xaero). */
@Mixin(targets = "xaero.common.minimap.highlight.HighlighterRegistry", remap = false)
public abstract class MinimapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0, remap = false)
    private void ikariaBorders$beforeEnd(CallbackInfo ci) {
        XaeroHooks.registerMinimap(this);
    }
}
