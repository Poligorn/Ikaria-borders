# Ikaria Borders

Серверный и клиентский мод для сервера MC-Ikaria (Minecraft 1.21.1, NeoForge 21.1.x).

**Этап 0.1** (влит в `docs/tz`): каркас мода, модель зон и формы, JSON-конфиг с проверкой, команды `/borders here|at|list|info|reload|debug`, публичное API и `PlayerZoneChangeEvent`, мягкая проекция Sable.

**Этап 0.2** (в ревью, без мержа): мягкая интеграция с Open Parties and Claims — запрет приватов/форслоада/якорей по зонам (исключение только у серверных приватов), чистка существующих (`/borders claims scan|sweep`, `dryRun`, периодическая), `/borders legacy-cleanup [dryrun]`, события `ZoneClaimDeniedEvent` и `ZoneClaimsSweptEvent`. Без OPAC мод работает, но без запрета и чистки.

**Этап 0.3** (в ревью, не влит): применение правил зон — PvP, урон, взрывы, спавн мобов; мягкий плагин KubeJS (`IkariaBorders`, `IkariaBordersEvents`, свои флаги, данные зон, изоляция ошибок и бюджет времени), `/borders scripts`. Без KubeJS правила работают, скриптов нет. По умолчанию в конфиге PvP запрещён только на спавне, остальные правила выключены (ванилла).

**Этап 0.4** (в работе, не влит): клиентская часть — линии зон на World Map и миникарте Xaero (по чанкам, как проверка приватов), синхронизация по необязательному каналу `ikaria_borders:sync` (вход, `/borders reload`, `/borders resync [player]`), клиентский конфиг `config/ikaria_borders-client.toml` (`enabled`, `worldMap`, `minimap`, `opacityMultiplier`, `hiddenZones`, `outlineWidth`=2, `hatchStep`=4, `hatchWidth`=1, `hatchOpacity`=0.7 — последние четыре в ТЗ §11 не описаны, значения выбраны разумные) и клавиша-переключатель (по умолчанию не назначена). Клиент без мода входит на сервер как обычно; на сервере без мода клиентская часть молчит. Без Xaero линий нет. Заливка рисуется со штриховкой, граница — жирной непрозрачной обводкой; Xaero хранит цвет как BGRA, а не ARGB (поэтому раньше `#55FF55` выходил лососевым). Это не проверено в игре после последнего исправления: нужен клиент с Xaero.

Ещё не реализовано: динамические зоны и runtime-правила (0.45).

Канон: [docs/TZ_ikaria_borders_v2.md](docs/TZ_ikaria_borders_v2.md) (v2.1). Правила для агентов: [CLAUDE.md](CLAUDE.md).

- Mod id: `ikaria_borders`
- Пакет: `com.poligorn.ikariaborders`
- Конфиг: `config/ikaria_borders/zones.json` (пример: `zones.example.json`)
- Сборка: `./gradlew build` (Java 21). Для компиляции нужны jar OPAC 0.32.7, KubeJS 2101.7.2 с Rhino и Xaero World Map 1.47.0 / Minimap 26.6.0 (в репозиторий не входят, в мод не попадают): по умолчанию из `../ikaria-zones/mod-libs/`, иначе `-Popac_jar=…`, `-Pkubejs_jar=…`, `-Prhino_jar=…`, `-Pxaero_worldmap_jar=…`, `-Pxaero_minimap_jar=…`. Автотесты геометрии и валидации — `./gradlew test`.

## Лицензия

MIT, copyright (c) 2026 Egor Skuratov / Poligorn. Полный текст — в файле [LICENSE](LICENSE).
