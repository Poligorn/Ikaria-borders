# Ikaria Borders

Серверный и клиентский мод для сервера MC-Ikaria (Minecraft 1.21.1, NeoForge 21.1.x).

**Этап 0.1** (влит в `docs/tz`): каркас мода, модель зон и формы, JSON-конфиг с проверкой, команды `/borders here|at|list|info|reload|debug`, публичное API и `PlayerZoneChangeEvent`, мягкая проекция Sable.

**Этап 0.2** (в ревью, без мержа): мягкая интеграция с Open Parties and Claims — запрет приватов/форслоада/якорей по зонам (исключение только у серверных приватов), чистка существующих (`/borders claims scan|sweep`, `dryRun`, периодическая), `/borders legacy-cleanup [dryrun]`, события `ZoneClaimDeniedEvent` и `ZoneClaimsSweptEvent`. Без OPAC мод работает, но без запрета и чистки.

**Этап 0.3** (в ревью, не влит): применение правил зон — PvP, урон, взрывы, спавн мобов; мягкий плагин KubeJS (`IkariaBorders`, `IkariaBordersEvents`, свои флаги, данные зон, изоляция ошибок и бюджет времени), `/borders scripts`. Без KubeJS правила работают, скриптов нет. По умолчанию в конфиге PvP запрещён только на спавне, остальные правила выключены (ванилла).

Ещё не реализовано: динамические зоны и runtime-правила (0.45), линии на карте Xaero (0.4).

Канон: [docs/TZ_ikaria_borders_v2.md](docs/TZ_ikaria_borders_v2.md) (v2.1). Правила для агентов: [CLAUDE.md](CLAUDE.md).

- Mod id: `ikaria_borders`
- Пакет: `com.poligorn.ikariaborders`
- Конфиг: `config/ikaria_borders/zones.json` (пример: `zones.example.json`)
- Сборка: `./gradlew build` (Java 21). Для компиляции нужны jar OPAC 0.32.7 и KubeJS 2101.7.2 с Rhino (в репозиторий не входят, в мод не попадают): по умолчанию из `../ikaria-zones/mod-libs/`, иначе `-Popac_jar=…`, `-Pkubejs_jar=…`, `-Prhino_jar=…`. Автотесты геометрии и валидации — `./gradlew test`.

## Лицензия

MIT, copyright (c) 2026 Egor Skuratov / Poligorn. Полный текст — в файле [LICENSE](LICENSE).
