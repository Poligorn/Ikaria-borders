package com.poligorn.ikariaborders.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.poligorn.ikariaborders.ClaimSweeper;
import com.poligorn.ikariaborders.PlayerZoneTracker;
import com.poligorn.ikariaborders.SableCompat;
import com.poligorn.ikariaborders.ZoneManager;
import com.poligorn.ikariaborders.zone.ConfigIssue;
import com.poligorn.ikariaborders.zone.Zone;
import com.poligorn.ikariaborders.zone.ZoneConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** /borders (alias /zones). here, list: level 0; the rest: level 2. Replies are in Russian. */
public final class BordersCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private BordersCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralCommandNode<CommandSourceStack> root = d.register(Commands.literal("borders")
                .then(Commands.literal("here").requires(s -> s.hasPermission(0)).executes(c -> here(c.getSource())))
                .then(Commands.literal("list").requires(s -> s.hasPermission(0))
                        .executes(c -> list(c.getSource(), null))
                        .then(Commands.argument("dim", DimensionArgument.dimension())
                                .executes(c -> list(c.getSource(), DimensionArgument.getDimension(c, "dim")))))
                .then(Commands.literal("at").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(c -> at(c.getSource(), DoubleArgumentType.getDouble(c, "x"),
                                                DoubleArgumentType.getDouble(c, "z"), c.getSource().getLevel()))
                                        .then(Commands.argument("dim", DimensionArgument.dimension())
                                                .executes(c -> at(c.getSource(), DoubleArgumentType.getDouble(c, "x"),
                                                        DoubleArgumentType.getDouble(c, "z"), DimensionArgument.getDimension(c, "dim")))))))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        ZoneManager.config().allZones().stream().map(Zone::id), b))
                                .executes(c -> info(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> reload(c.getSource())))
                .then(Commands.literal("claims").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("scan").executes(c -> claimsRun(c.getSource(), ClaimSweeper.Mode.SCAN)))
                        .then(Commands.literal("sweep").executes(c -> claimsRun(c.getSource(), ClaimSweeper.Mode.MANUAL))))
                .then(Commands.literal("legacy-cleanup").requires(s -> s.hasPermission(2))
                        .executes(c -> legacy(c.getSource(), false))
                        .then(Commands.literal("dryrun").executes(c -> legacy(c.getSource(), true))))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("log")
                                .then(Commands.literal("on").executes(c -> debugLog(c.getSource(), true)))
                                .then(Commands.literal("off").executes(c -> debugLog(c.getSource(), false))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> debugPlayer(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
        d.register(Commands.literal("zones").requires(s -> s.hasPermission(0)).redirect(root));
    }

    // ---- here / at ----

    private static int here(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Эту команду может выполнить только игрок. Используйте /borders at <x> <z> [измерение]"));
            return 0;
        }
        SableCompat.Projection pr = SableCompat.project(p);
        String text = describePoint(p.level().dimension().location().toString(), pr.x(), pr.z());
        if (pr.onSubLevel()) text += "\n(на корабле Sable; координаты спроецированы)";
        reply(src, text);
        return 1;
    }

    private static int at(CommandSourceStack src, double x, double z, ServerLevel level) {
        reply(src, describePoint(level.dimension().location().toString(), x, z));
        return 1;
    }

    private static String describePoint(String dim, double x, double z) {
        ZoneConfig cfg = ZoneManager.config();
        Zone zone = cfg.zoneAt(dim, x, z);
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        Zone chunkZone = cfg.zoneOfChunk(dim, cx, cz);
        StringBuilder sb = new StringBuilder();
        sb.append("Точка ").append(fmt(x)).append(", ").append(fmt(z)).append(" в ").append(dim).append('\n');
        sb.append("Зона: ").append(zone.name()).append(" [").append(zone.id()).append("]\n");
        sb.append("Приоритет: ").append(zone.priority()).append('\n');
        sb.append("Родители: ").append(chain(zone)).append('\n');
        sb.append("Правила: ").append(zone.resolvedRules()).append('\n');
        sb.append("Приват в чанке ").append(cx).append(", ").append(cz).append(" (по центру чанка, зона ")
                .append(chunkZone.id()).append("): ")
                .append(chunkZone.claimsAllowed() ? "разрешён" : "запрещён");
        if (zone == Zone.NONE) sb.append("\n(для этого измерения нет зоны по умолчанию)");
        return sb.toString();
    }

    // ---- list / info ----

    private static int list(CommandSourceStack src, ServerLevel level) {
        ZoneConfig cfg = ZoneManager.config();
        List<String> dims = new ArrayList<>();
        if (level != null) dims.add(level.dimension().location().toString());
        else dims.addAll(cfg.dimensions());
        if (dims.isEmpty()) {
            reply(src, "Зон нет (конфиг пуст или не загружен).");
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (String dim : dims) {
            sb.append("== ").append(dim).append(" ==\n");
            List<Zone> zones = cfg.zones(dim);
            if (zones.isEmpty()) sb.append("  (зон нет)\n");
            for (Zone z : zones) {
                sb.append("  ").append(z.id()).append(" — ").append(z.isDefault() ? "всё измерение" : z.shape().describe())
                        .append(" — приоритет ").append(z.priority())
                        .append(" — карта: ").append(z.map().show() ? "да" : "нет");
                if (z.mapOnly()) sb.append(" (только карта)");
                sb.append('\n');
            }
        }
        reply(src, sb.toString().stripTrailing());
        return 1;
    }

    private static int info(CommandSourceStack src, String id) {
        Zone z = ZoneManager.config().zone(id);
        if (z == null) {
            src.sendFailure(Component.literal("Зона '" + id + "' не найдена. Список: /borders list"));
            return 0;
        }
        Zone.MapStyle m = z.map();
        StringBuilder sb = new StringBuilder();
        sb.append("Зона ").append(z.name()).append(" [").append(z.id()).append("]\n");
        sb.append("Измерение: ").append(z.dimension()).append('\n');
        sb.append("Форма: ").append(z.isDefault() ? "всё измерение (зона по умолчанию)" : z.shape().describe()).append('\n');
        sb.append("Приоритет: ").append(z.priority()).append(z.mapOnly() ? ", только для карты" : "").append('\n');
        sb.append("Родители: ").append(chain(z)).append('\n');
        sb.append("Свои правила: ").append(z.ownRules()).append('\n');
        sb.append("Итоговые правила: ").append(z.resolvedRules()).append('\n');
        sb.append("Карта: показ=").append(m.show()).append(", цвет=").append(m.color()).append(", стиль=").append(m.style())
                .append(", прозрачность=").append(m.opacity()).append(", сторона=").append(m.side())
                .append(", подпись=").append(m.label()).append('\n');
        sb.append("Meta: ").append(z.meta());
        reply(src, sb.toString());
        return 1;
    }

    // ---- reload ----

    private static int reload(CommandSourceStack src) {
        ZoneManager.ReloadResult r = ZoneManager.reload(src.getServer());
        if (!r.ok()) {
            StringBuilder sb = new StringBuilder("Перезагрузка отменена, остаётся прежний конфиг. Ошибок: " + r.errors().size());
            for (ConfigIssue e : r.errors()) sb.append("\n  ").append(e);
            src.sendFailure(Component.literal(sb.toString()));
            return 0;
        }
        StringBuilder sb = new StringBuilder("Конфиг перезагружен: зон — " + r.zoneCount());
        if (!r.warnings().isEmpty()) {
            sb.append(", предупреждений: ").append(r.warnings().size());
            for (ConfigIssue w : r.warnings()) sb.append("\n  ").append(w);
        }
        reply(src, sb.toString());
        return 1;
    }

    // ---- claims ----

    private static int claimsRun(CommandSourceStack src, ClaimSweeper.Mode mode) {
        boolean started = ClaimSweeper.start(mode, asyncReply(src));
        if (started) {
            reply(src, mode == ClaimSweeper.Mode.SCAN
                    ? "Проверка приватов запущена (ничего не снимается), отчёт придёт по окончании."
                    : "Чистка приватов запущена по-настоящему (dryRun из конфига здесь не действует), отчёт придёт по окончании.");
        }
        return started ? 1 : 0;
    }

    private static int legacy(CommandSourceStack src, boolean dry) {
        return ClaimSweeper.startLegacy(dry, asyncReply(src)) ? 1 : 0;
    }

    /** Replies later from the tick thread; failures are sent as failures. */
    private static java.util.function.Consumer<String> asyncReply(CommandSourceStack src) {
        return text -> {
            boolean failure = text.startsWith("Open Parties") || text.startsWith("Уже идёт") || text.startsWith("Сервер ещё")
                    || text.startsWith("Чистка прервана");
            if (failure) src.sendFailure(Component.literal(text));
            else reply(src, text);
        };
    }

    // ---- debug ----

    private static int debugLog(CommandSourceStack src, boolean on) {
        PlayerZoneTracker.setDebugLog(on);
        reply(src, "Журнал смен зон: " + (on ? "включён (с ограничением частоты)" : "выключен"));
        return 1;
    }

    private static int debugPlayer(CommandSourceStack src, ServerPlayer p) {
        String dim = p.level().dimension().location().toString();
        SableCompat.Projection pr = SableCompat.project(p);
        ZoneConfig cfg = ZoneManager.config();
        Zone exact = cfg.zoneAt(dim, pr.x(), pr.z());
        PlayerZoneTracker.Entry e = PlayerZoneTracker.entry(p.getUUID());
        StringBuilder sb = new StringBuilder();
        sb.append("Игрок ").append(p.getGameProfile().getName()).append(" в ").append(dim).append('\n');
        sb.append("Координаты: ").append(fmt(p.getX())).append(", ").append(fmt(p.getY())).append(", ").append(fmt(p.getZ())).append('\n');
        sb.append("Sable: подключён=").append(SableCompat.isAvailable() ? "да" : "нет")
                .append(", на корабле=").append(pr.onSubLevel() ? "да" : "нет");
        if (pr.onSubLevel()) sb.append(", проекция: ").append(fmt(pr.x())).append(", ").append(fmt(pr.z()));
        sb.append('\n');
        sb.append("Зона по кэшу: ").append(e == null || e.zone() == null ? "нет данных" : e.zone().id()).append('\n');
        sb.append("Зона по точному расчёту: ").append(exact.id());
        if (e != null && e.zone() != null && !e.zone().id().equals(exact.id())) sb.append("  ← РАСХОЖДЕНИЕ (кэш обновляется раз в 10 тиков)");
        sb.append('\n');
        if (e == null || e.lastChangeMillis() == 0) sb.append("Последняя смена зоны: нет данных");
        else {
            long ago = (System.currentTimeMillis() - e.lastChangeMillis()) / 1000;
            sb.append("Последняя смена зоны: ").append(TIME.format(Instant.ofEpochMilli(e.lastChangeMillis())))
                    .append(" (").append(ago).append(" с назад)");
        }
        reply(src, sb.toString());
        return 1;
    }

    // ---- helpers ----

    private static String chain(Zone z) {
        List<String> parts = new ArrayList<>(z.parentChain());
        parts.add("global");
        return z.parentChain().isEmpty() ? "global" : String.join(" → ", parts);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static void reply(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }
}
