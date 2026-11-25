package com.poligorn.ikariaborders.xaero.mixin;

import com.poligorn.ikariaborders.xaero.XaeroHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds our highlighter to World Map's registry right before it is frozen (TZ §4.2). Target is a string: Xaero may be absent. */
@Mixin(targets = "xaero.map.highlight.HighlighterRegistry", remap = false)
public abstract class WorldMapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0, remap = false)
    private void ikariaBorders$beforeEnd(CallbackInfo ci) {
        XaeroHooks.registerWorldMap(this);
    }
}
