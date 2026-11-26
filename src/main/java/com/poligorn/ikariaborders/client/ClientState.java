package com.poligorn.ikariaborders.client;

import com.poligorn.ikariaborders.map.MapSettings;
import com.poligorn.ikariaborders.map.ZoneMapModel;

/** Single client-side model shared by all Xaero highlighters. No Minecraft types: safe to touch from anywhere. */
public final class ClientState {
    public static final ZoneMapModel MODEL = new ZoneMapModel();

    /** Runtime toggle from the keybind (not persisted). */
    private static volatile boolean userEnabled = true;
    private static volatile MapSettings fromConfig = MapSettings.DEFAULT;

    private ClientState() {}

    public static boolean userEnabled() {
        return userEnabled;
    }

    /** @return true if the effective model changed (caller should refresh Xaero). */
    public static boolean setUserEnabled(boolean v) {
        userEnabled = v;
        return apply();
    }

    /** @return true if the effective model changed. */
    public static boolean setConfigSettings(MapSettings s) {
        fromConfig = s;
        return apply();
    }

    /** Effective settings = config, minus the runtime toggle. */
    private static boolean apply() {
        return MODEL.setSettings(fromConfig.withEnabled(fromConfig.enabled() && userEnabled));
    }

    /** Leaving a server: forget its zones, so nothing leaks to the next server (or a vanilla one). */
    public static boolean clearServerData() {
        return MODEL.setData(com.poligorn.ikariaborders.map.ZoneMapData.EMPTY);
    }
}
