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

    public static void setUserEnabled(boolean v) {
        userEnabled = v;
        apply();
    }

    public static void setConfigSettings(MapSettings s) {
        fromConfig = s;
        apply();
    }

    /** Effective settings = config, minus the runtime toggle. */
    private static void apply() {
        MODEL.setSettings(fromConfig.withEnabled(fromConfig.enabled() && userEnabled));
    }

    /** Leaving a server: forget its zones, so nothing leaks to the next server (or a vanilla one). */
    public static void clearServerData() {
        MODEL.setData(com.poligorn.ikariaborders.map.ZoneMapData.EMPTY);
    }
}
