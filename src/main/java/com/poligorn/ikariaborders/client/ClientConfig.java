package com.poligorn.ikariaborders.client;

import com.poligorn.ikariaborders.map.MapSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code config/ikaria_borders-client.toml} (TZ §11). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED, WORLD_MAP, MINIMAP;
    private static final ModConfigSpec.DoubleValue OPACITY;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Рисовать границы зон на картах Xaero").define("enabled", true);
        WORLD_MAP = b.comment("Рисовать на Xaero's World Map").define("worldMap", true);
        MINIMAP = b.comment("Рисовать на Xaero's Minimap").define("minimap", true);
        OPACITY = b.comment("Множитель непрозрачности линий и заливки (0 — не видно, 1 — как в конфиге сервера)")
                .defineInRange("opacityMultiplier", 1.0, 0.0, 2.0);
        HIDDEN = b.comment("Список id зон, которые не рисовать (например [\"edge\"])")
                .defineListAllowEmpty("hiddenZones", List.of(), () -> "", o -> o instanceof String);
        SPEC = b.build();
    }

    private ClientConfig() {}

    /** Current values as a pure settings record (defaults before the file is loaded). */
    public static MapSettings snapshot() {
        if (!SPEC.isLoaded()) return MapSettings.DEFAULT;
        Set<String> hidden = new HashSet<>(HIDDEN.get());
        return new MapSettings(ENABLED.get(), WORLD_MAP.get(), MINIMAP.get(), OPACITY.get(), hidden);
    }
}
