package com.poligorn.ikariaborders.xaero.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Enables each mixin only if the matching Xaero mod is installed (TZ §4.2). Mixin configs load very early,
 * before FML's {@code ModList} exists, so the early {@code LoadingModList} is used and {@code ModList} is the fallback.
 * No Minecraft classes here.
 */
public final class XaeroMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("WorldMapRegistryMixin")) return loaded("xaeroworldmap");
        if (mixinClassName.endsWith("MinimapRegistryMixin")) return loaded("xaerominimap");
        return false;
    }

    static boolean loaded(String modId) {
        try {
            var lml = net.neoforged.fml.loading.LoadingModList.get();
            if (lml != null) return lml.getModFileById(modId) != null;
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            var ml = net.neoforged.fml.ModList.get();
            return ml != null && ml.isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }
}
