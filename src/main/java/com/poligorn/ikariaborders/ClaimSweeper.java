package com.poligorn.ikariaborders;

import com.mojang.logging.LogUtils;
import com.poligorn.ikariaborders.api.ZoneClaimsSweptEvent;
import com.poligorn.ikariaborders.claims.ClaimEntry;
import com.poligorn.ikariaborders.claims.ClaimRemover;
import com.poligorn.ikariaborders.claims.ClaimSource;
import com.poligorn.ikariaborders.claims.LegacyCleanupJob;
import com.poligorn.ikariaborders.claims.SweepJob;
import com.poligorn.ikariaborders.opac.OpacBridge;
import com.poligorn.ikariaborders.zone.GlobalSettings;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Schedules and drives scan / sweep / legacy-cleanup on the main thread (OPAC API is not thread-safe),
 * one job at a time, in per-tick batches (TZ §7, §12).
 */
public final class ClaimSweeper {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long BUDGET_NANOS = 2_000_000L; // ≤ 2 ms per tick
    private static final int START_DELAY_TICKS = 30 * 20;
    public static final List<String> LEGACY_NAMES = List.of("ikaria-spawn", "ikaria-500", "ikaria-wild", "ikaria-edge");

    public enum Mode {
        /** startup / periodic / after reload: honours {@code global.sweep.dryRun} */
        AUTO,
        /** manual {@code /borders claims sweep}: really removes */
        MANUAL,
        /** {@code /borders claims scan}: removes nothing */
        SCAN
    }

    private static SweepJob job;
    private static Mode jobMode;
    private static Consumer<String> jobReporter;
    private static LegacyCleanupJob legacy;
    private static Consumer<String> legacyReporter;
    private static MinecraftServer server;
    private static long tickCounter;
    private static long nextStartTick = -1;
    private static long nextPeriodicTick = -1;

    private ClaimSweeper() {}

    public static boolean busy() { return job != null || legacy != null; }

    public static void onServerStarted(MinecraftServer s) {
        server = s;
        tickCounter = 0;
        GlobalSettings.Sweep sw = ZoneManager.config().settings().sweep();
        nextStartTick = sw.onStart() && OpacBridge.isLoaded() ? START_DELAY_TICKS : -1;
        schedulePeriodic(sw);
    }

    public static void onServerStopped() {
        server = null;
        job = null;
        legacy = null;
        jobReporter = null;
        legacyReporter = null;
        nextStartTick = nextPeriodicTick = -1;
    }

    private static void schedulePeriodic(GlobalSettings.Sweep sw) {
        nextPeriodicTick = sw.intervalMinutes() > 0 && OpacBridge.isLoaded()
                ? tickCounter + sw.intervalMinutes() * 60L * 20L : -1;
    }

    /** After a successful reload: re-arm the timer and sweep if claim-relevant zones/rules changed. */
    public static void onReload(ZoneConfig before, ZoneConfig after) {
        if (server == null) return;
        schedulePeriodic(after.settings().sweep());
        if (!OpacBridge.isLoaded()) return;
        if (!before.claimSignature().equals(after.claimSignature())) {
            LOG.info("[ikaria_borders/claims] зоны/правила приватов изменились — чистка после reload");
            job = null; // results for the old config are meaningless
            jobReporter = null;
            start(Mode.AUTO, msg -> {});
        }
    }

    public static void tick(MinecraftServer s) {
        if (server == null) return;
        tickCounter++;
        if (nextStartTick >= 0 && tickCounter >= nextStartTick) {
            nextStartTick = -1;
            start(Mode.AUTO, m -> {});
        }
        if (nextPeriodicTick >= 0 && tickCounter >= nextPeriodicTick) {
            schedulePeriodic(ZoneManager.config().settings().sweep());
            if (!busy()) start(Mode.AUTO, m -> {});
        }
        try {
            if (job != null && job.step(System::nanoTime)) finishSweep();
            else if (legacy != null && legacy.step(System::nanoTime)) finishLegacy();
        } catch (RuntimeException e) {
            LOG.error("[ikaria_borders/claims] ошибка во время чистки, задача остановлена: {}", e.toString(), e);
            job = null;
            legacy = null;
            Consumer<String> r = jobReporter != null ? jobReporter : legacyReporter;
            jobReporter = legacyReporter = null;
            if (r != null) r.accept("Чистка прервана из-за ошибки, подробности в логе сервера.");
        }
    }

    /** @return false if OPAC is absent or another job is running (reported through {@code reporter}) */
    public static boolean start(Mode mode, Consumer<String> reporter) {
        if (server == null) { reporter.accept("Сервер ещё не запущен."); return false; }
        ClaimSource src = OpacBridge.source(server);
        ClaimRemover rem = OpacBridge.remover(server);
        if (src == null || rem == null) {
            reporter.accept("Open Parties and Claims не установлен (или не удалось подключиться): запрет приватов и чистка недоступны.");
            return false;
        }
        if (busy()) {
            reporter.accept("Уже идёт другая операция с приватами, подождите её окончания.");
            return false;
        }
        ZoneConfig cfg = ZoneManager.config();
        boolean apply = mode == Mode.MANUAL || (mode == Mode.AUTO && !cfg.settings().sweep().dryRun());
        jobMode = mode;
        jobReporter = reporter;
        job = new SweepJob(src, rem, cfg, apply, cfg.settings().sweep().perTick(), BUDGET_NANOS, ClaimSweeper::ownerDone);
        LOG.info("[ikaria_borders/claims] {} запущена ({})", mode == Mode.SCAN ? "проверка" : "чистка",
                apply ? "со снятием" : "только отчёт");
        return true;
    }

    private static void ownerDone(SweepJob.OwnerResult r) {
        if (job == null || !job.applying()) {
            LOG.info("[ikaria_borders/claims] нарушений {} у {} ({}) — не снимаем ({})", r.violations, r.name, r.owner,
                    jobMode == Mode.SCAN ? "проверка" : "dryRun");
            return;
        }
        if (r.removed <= 0) return;
        LOG.info("[ikaria_borders/claims] снято {} у {} ({})", r.removed, r.name, r.owner);
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(r.owner);
        if (online != null && !ModList.get().isLoaded("kubejs")) {
            online.sendSystemMessage(Component.literal("Ваши приваты (" + r.removed + ") сняты: они находились в зоне, где приватить нельзя."));
        }
        NeoForge.EVENT_BUS.post(new ZoneClaimsSweptEvent(r.owner, r.name, r.removed));
    }

    private static void finishSweep() {
        SweepJob j = job;
        Consumer<String> rep = jobReporter;
        Mode mode = jobMode;
        job = null;
        jobReporter = null;
        StringBuilder sb = new StringBuilder();
        sb.append(mode == Mode.SCAN ? "Проверка приватов завершена" : (j.applying() ? "Чистка приватов завершена" : "Чистка завершена в режиме dryRun (ничего не снято)"))
                .append(": просмотрено чанков — ").append(j.chunksProcessed())
                .append(", нарушений — ").append(j.totalViolations());
        if (j.applying()) sb.append(", снято — ").append(j.totalRemoved());
        if (j.exemptOwners() > 0) sb.append(", владельцев-исключений (серверные) пропущено — ").append(j.exemptOwners());
        for (SweepJob.OwnerResult r : j.results().values()) {
            sb.append("\n  ").append(r.name).append(" (").append(r.owner).append("): нарушений ").append(r.violations);
            if (j.applying()) sb.append(", снято ").append(r.removed)
                    .append(" (приваты ").append(r.claims).append(", форслоад ").append(r.forceloads).append(", якоря ").append(r.anchors).append(')');
        }
        LOG.info("[ikaria_borders/claims] {}", sb);
        if (rep != null) rep.accept(sb.toString());
    }

    // ---- legacy cleanup ----

    /** Collects leftover server claims in the legacy sub-configs and starts the batched removal. */
    public static boolean startLegacy(boolean dryRun, Consumer<String> reporter) {
        if (server == null) { reporter.accept("Сервер ещё не запущен."); return false; }
        ClaimRemover rem = OpacBridge.remover(server);
        Map<String, Integer> perName = new java.util.LinkedHashMap<>();
        List<ClaimEntry> entries = rem == null ? null : OpacBridge.legacyEntries(server, LEGACY_NAMES, perName);
        if (entries == null) {
            reporter.accept("Open Parties and Claims не установлен (или не удалось подключиться): legacy-cleanup недоступен.");
            return false;
        }
        if (busy()) {
            reporter.accept("Уже идёт другая операция с приватами, подождите её окончания.");
            return false;
        }
        StringBuilder sb = new StringBuilder("Найдено серверных приватов старых колец: " + entries.size());
        for (var e : perName.entrySet()) sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
        if (entries.isEmpty()) {
            reporter.accept(sb.append("\nНичего снимать.").toString());
            return true;
        }
        boolean apply = !dryRun;
        sb.append(apply ? "\nСнимаем порциями по " + ZoneManager.config().settings().sweep().perTick() + " чанков за тик…"
                : "\nРежим dryrun: ничего не снимается.");
        reporter.accept(sb.toString());
        LOG.info("[ikaria_borders/claims] legacy-cleanup: {}", sb.toString().replace('\n', ' '));
        legacyReporter = reporter;
        legacy = new LegacyCleanupJob(entries, rem, apply, ZoneManager.config().settings().sweep().perTick(), BUDGET_NANOS);
        return true;
    }

    private static void finishLegacy() {
        LegacyCleanupJob j = legacy;
        Consumer<String> rep = legacyReporter;
        legacy = null;
        legacyReporter = null;
        String msg = j.applying()
                ? "legacy-cleanup завершён: снято " + j.removed() + " из " + j.total()
                + " чанков. Сами подконфигурации OPAC не удаляются (в API нет такой операции): пустые могут остаться."
                : "legacy-cleanup (dryrun) завершён: нашлось " + j.total() + " чанков, ничего не снято.";
        LOG.info("[ikaria_borders/claims] {}", msg);
        if (rep != null) rep.accept(msg);
    }
}
