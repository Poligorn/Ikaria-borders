package com.poligorn.ikariaborders;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.api.ZonesReloadedEvent;
import com.poligorn.ikariaborders.zone.ConfigIssue;
import com.poligorn.ikariaborders.zone.ConfigLoader;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/** Owns the active config. Swaps the reference atomically only after full validation. */
public final class ZoneManager {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String EXAMPLE_RESOURCE = "/ikaria_borders/zones.example.json";

    private static volatile ZoneConfig current = ZoneConfig.EMPTY;

    private ZoneManager() {}

    public static ZoneConfig config() {
        return current;
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(IkariaBorders.MODID);
    }

    public static Path configFile() {
        return configDir().resolve("zones.json");
    }

    public record ReloadResult(boolean ok, int zoneCount, List<ConfigIssue> errors, List<ConfigIssue> warnings) {}

    /** Startup load. Never throws: a broken config means empty zones and a loud log. */
    public static ReloadResult loadAtStartup(MinecraftServer server) {
        try {
            writeDefaults();
        } catch (IOException | RuntimeException e) {
            LOG.error("[ikaria_borders] Не удалось записать конфиг по умолчанию: {}", e.toString());
        }
        ReloadResult r = load(server, false);
        if (!r.ok()) {
            current = ZoneConfig.EMPTY;
            LOG.error("[ikaria_borders] ================================================================");
            LOG.error("[ikaria_borders] КОНФИГ {} НЕ ЗАГРУЖЕН — зон нет, работаем с пустым набором!", configFile());
            LOG.error("[ikaria_borders] Исправьте файл и выполните /borders reload");
            LOG.error("[ikaria_borders] ================================================================");
        }
        return r;
    }

    /** /borders reload. On error the old config stays active. */
    public static ReloadResult reload(MinecraftServer server) {
        return load(server, true);
    }

    public static void clear() {
        current = ZoneConfig.EMPTY;
    }

    private static ReloadResult load(MinecraftServer server, boolean announce) {
        String json;
        try {
            json = Files.readString(configFile(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            ConfigIssue issue = new ConfigIssue("zones.json", "не удалось прочитать файл: " + e);
            LOG.error("[ikaria_borders] {}", issue);
            return new ReloadResult(false, 0, List.of(issue), List.of());
        }
        ConfigLoader.Result res = ConfigLoader.parse(json, dimensionPredicate(server));
        for (ConfigIssue w : res.warnings()) LOG.warn("[ikaria_borders] предупреждение: {}", w);
        if (!res.ok()) {
            for (ConfigIssue e : res.errors()) LOG.error("[ikaria_borders] ошибка конфига: {}", e);
            return new ReloadResult(false, 0, res.errors(), res.warnings());
        }
        ZoneConfig before = current;
        current = res.config(); // atomic swap
        int n = res.config().allZones().size();
        LOG.info("[ikaria_borders] Конфиг загружен: {} зон(ы)", n);
        PlayerZoneTracker.invalidateAll();
        NeoForge.EVENT_BUS.post(new ZonesReloadedEvent(n));
        if (announce) ClaimSweeper.onReload(before, current); // sweep only on /borders reload, not at startup
        return new ReloadResult(true, n, List.of(), res.warnings());
    }

    private static Predicate<String> dimensionPredicate(MinecraftServer server) {
        if (server == null) return null;
        return dim -> {
            ResourceLocation id = ResourceLocation.tryParse(dim);
            return id != null && server.registryAccess().registryOrThrow(Registries.DIMENSION).containsKey(id);
        };
    }

    private static void writeDefaults() throws IOException {
        Files.createDirectories(configDir());
        byte[] example;
        try (InputStream in = ZoneManager.class.getResourceAsStream(EXAMPLE_RESOURCE)) {
            if (in == null) throw new IOException("нет ресурса " + EXAMPLE_RESOURCE);
            example = in.readAllBytes();
        }
        Files.write(configDir().resolve("zones.example.json"), example); // documentation copy, always refreshed
        if (!Files.exists(configFile())) {
            Files.write(configFile(), example);
            LOG.info("[ikaria_borders] Создан конфиг по умолчанию: {}", configFile());
        }
    }
}
