package com.poligorn.ikariaborders.kubejs;

import com.poligorn.ikariaborders.script.FlagRegistry;
import com.poligorn.ikariaborders.script.IkariaBordersJS;
import com.poligorn.ikariaborders.script.ScriptBridge;
import com.poligorn.ikariaborders.script.ScriptRuntime;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * KubeJS plugin (TZ §21.1), listed in {@code kubejs.plugins.txt}. Registers the {@code IkariaBorders} binding and the
 * {@code IkariaBordersEvents} group, nothing else. KubeJS loads it only when KubeJS is installed.
 */
public final class IkariaKubeJSPlugin implements KubeJSPlugin {
    private static final KubeJsHooks HOOKS = new KubeJsHooks();

    @Override
    public void init() {
        ScriptBridge.register(HOOKS);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        if (registry.type().isServer()) registry.add("IkariaBorders", IkariaBordersJS.INSTANCE);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        ScriptBridge.register(HOOKS); // idempotent: init() may not run in every KubeJS lifecycle
        registry.register(IkariaEvents.group());
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        // KubeJS drops the old handlers on reload: counters, disabled flags, flags and caches start clean
        Dispatcher.invalidate();
        ScriptRuntime.GUARD.reset();
        FlagRegistry.clear();
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        Dispatcher.invalidate();
        ScriptBridge.requestReady(); // fired on the next server tick (also covers /reload at runtime)
    }
}
