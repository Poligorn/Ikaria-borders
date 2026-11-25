# Ikaria Borders — правила для агентов

Репозиторий: https://github.com/Poligorn/Ikaria-borders
ТЗ (канон): [docs/TZ_ikaria_borders_v2.md](docs/TZ_ikaria_borders_v2.md) — v2.1.

## Жёсткие правила

- Никогда не мержить, не ставить теги и не публиковать релиз без явного OK от Egor (Poligorn).
- Работа только в ветке + PR. Сейчас рабочая ветка: `feat/0.4-client` (создана из `feat/0.3-core`), база: `docs/tz` (0.1 влит в `docs/tz`; 0.2 и 0.3 в ревью).
- Не печатать ключи, токены и секреты.
- Быть честным, что не проверено в игре.

## Идентификаторы (ответы Egor, не переоткрывать)

- Mod id: `ikaria_borders`
- Пакет: `com.poligorn.ikariaborders`
- API-пакет: `com.poligorn.ikariaborders.api`
- Конфиг: `config/ikaria_borders/zones.json` (JSON, не TOML/JSON5)
- Команда: `/borders`, алиас `/zones` (через redirect Brigadier)
- Лицензия репозитория: MIT (см. [LICENSE](LICENSE)), copyright 2026 Egor Skuratov / Poligorn; `mod_license=MIT` в gradle.properties. Вопрос §19.12 закрыт (25.11.2026). Пакет остаётся `com.poligorn.ikariaborders`.

## Стек

- Minecraft 1.21.1, NeoForge 21.1.x, Java 21, ModDevGradle.
- Целевая версия NeoForge: **21.1.250** (тестовый сервер) или совместимая 21.1.252; диапазон `[21.1.219,)`.
- Parchment: 1.21.1 / 2024.11.17.
- Жёстких зависимостей кроме NeoForge нет.
- Мягкие (compileOnly / optional, код устойчив к их отсутствию): Xaero's World Map 1.47.0 и Minimap 26.6.0 (mod id `xaeroworldmap` / `xaerominimap`, только клиент, `compileOnly files(xaero_worldmap_jar, xaero_minimap_jar)`, в mods.toml `optional` без диапазона версий; jar'ы **не** в репозитории и не в моде, по умолчанию `../ikaria-zones/mod-libs/…`), KubeJS 2101.7.2 (mod id `kubejs`, `compileOnly files(kubejs_jar, rhino_jar)`, в mods.toml `optional` без диапазона версий; jar'ы **не** в репозитории, по умолчанию `../ikaria-zones/mod-libs/kubejs-neoforge-2101.7.2-build.368.jar` и `rhino-2101.2.7-build.85.jar`), Sable 2.0.6 (reflection), Open Parties and Claims 0.32.7 (mod id `openpartiesandclaims`, `compileOnly files(opac_jar)`, в mods.toml `optional`; jar в репозиторий **не** кладём, путь — свойство gradle `opac_jar`, по умолчанию `../ikaria-zones/mod-libs/…`). 

## Этап 0.4 (текущий, `feat/0.4-client`; в работе, не влит; не выходить за этот объём)

По ТЗ §4, §10, §11; тесты 11–13, 15 (нужен клиент Egor):

- **Синхронизация** (`sync.*`): один payload `ikaria_borders:sync` (`SyncPayload`, регистрация в `SyncNetwork` на `RegisterPayloadHandlersEvent`, канал **optional**). Тело — непрозрачный `byte[]` (≤ 512 КБ), формат в чистом `map.ZoneMapCodec` (без Minecraft, тестируется без игры). Содержит версию (FNV-1a по данным карты) и только зоны с `map.show: true` (в т.ч. `mapOnly`): id, имя, измерение, форма, приоритет, стиль, цвет, прозрачность, `side`, `label`. Правил нет, зоны по умолчанию с `show: false` не отправляются. Отправка: вход игрока, `/borders reload` (всем), `/borders resync [player]` (уровень 2). Клиентам без канала (`hasChannel`) не шлём.
- **Клиент** (`client.*`, `map.*`, `xaero.*`): чистое ядро `map.ZoneMapModel` (кэш регионов 32×32 чанка, LRU 256, AABB-отсечение, чанк по центру `cx*16+8`) + два тонких адаптера `xaero.WorldMapZoneHighlighter` / `xaero.MinimapZoneHighlighter`. Регистрация — mixin `HEAD` в `HighlighterRegistry.end()` обоих модов (`xaero.mixin.*`, `require = 0`, включаются плагином `XaeroMixinPlugin` только при наличии соответствующего Xaero). Логические цвета модели `ZoneMapModel.colors`: `[центр, С(−Z), В(+X), Ю(+Z), З(−X)]`, ARGB, плюс маска сторон с границей (цвет стороны может совпадать с заливкой, поэтому «сторона == центр» признаком границы быть не может). **Цвет в highlighter Xaero — BGRA `0xBBGGRRAA` (альфа в младшем байте), не ARGB** (`AbstractHighlighter.getBlend`, `ClaimsHighlighter`; из-за ARGB `#55FF55` рисовался лососевым); упаковка — `map.XaeroColor`. Highlighter'ы переопределяют `getChunkHighlitColor` и отдают растр 16×16 (`map.ChunkHighlightRaster`, индекс `(z<<4)|x`): заливка `opacity × opacityMultiplier`, поверх диагональная штриховка того же цвета (в мировых пикселях, стыкуется между чанками), на границе стороны с `outline` — жирная непрозрачная обводка. Приоритет пикселя: обводка > штрих > заливка. Штриховка только при `fill`. Ключи клиентского конфига (в ТЗ §11 их нет, значения выбраны разумные): `outlineWidth=2` (1–8), `hatchStep=4` (2–16), `hatchWidth=1`, `hatchOpacity=0.7` (× `opacityMultiplier`). Не проверено в игре после этого фикса: цвет, вид штриховки и обводки.
- Семантика: зона чанка — наивысший приоритет среди видимых зон; заливка от неё, если в `style` есть `fill`. Сторона — граница **формы** зоны с `outline` (`inner`: чанк внутри, сосед снаружи; `outer` — наоборот), побеждает больший приоритет. Поэтому `mapOnly`-зона `edge` рисует линию только на своей границе (10000), а не на границе `build`. `mapOnly`-зона без `fill` не затеняет заливку нижней зоны (`MapZone.mapOnly`, в формате кодека; иначе `edge` скрыл бы заливку wild). Пример: `wild` — заливка `#FF3030` 0.3 (штриховка = приват запрещён, подсказка «Дикие земли — приват запрещён»), `build` — синяя обводка `#3399FF`, `spawn` — зелёная обводка `#55FF55`, заливки у них нет.
- Клиентский конфиг `config/ikaria_borders-client.toml`: `enabled`, `worldMap`, `minimap`, `opacityMultiplier`, `hiddenZones`; клавиша-переключатель (по умолчанию не назначена, в памяти, не сохраняется). Сообщение — в чат, не actionbar. Миникарту в Аду не трогаем.
- Серверный код не грузит клиентские типы и Xaero: клиент — только в `client`/`xaero`; `XaeroCompat` трогает классы Xaero лишь если мод загружен. Любая ошибка Xaero — русский лог и работа без линий.
- Версия мода `0.4.0`.
- Не входит: динамические зоны, `overrideShape`, runtime `setRule`/`setColor`, `/borders dynamic|overrides` (0.45); захват; `fireSpread`; точные (по блокам) линии.
- Не проверено в игре: всё Xaero-специфичное — срабатывание mixin на `end()` (имя метода и сигнатуры взяты из `javap`), порядок сторон цвета (выведен из байткода `ChunkHighlighter.getChunkHighlitColor` и `TestHighlighter`, в игре не видели), `clearAllCachedHighlightHashes()` / `requestRefresh()` после reload (исчезают ли старые линии), `LoadingModList` в плагине mixin, optional-канал с ванильным клиентом, подсказки на World Map и миникарте, клавиша, поведение с OPAC на одном чанке, производительность на реальной карте (тесты 11–13, 15).

## Этап 0.3 (реализован в `feat/0.3-core`, в ревью)

По ТЗ §6.1–6.3, §21.1–21.3, §21.5 (данные и флаги), §21.7; тесты 6–9, 16, 17, 21, 22.

- **Правила зон** (`enforce.RuleEvents`, чистая логика в `rules.*`): PvP (`pvp`, `pvpCheck` both|victim, `pvpPets`), `damage.deny`/`damage.targets` (id и `#теги`), `explosions.blockDamage|entityDamage` (по центру блока/позиции сущности), `spawn.categories|reasons|denyEntities|allowEntities` (`PositionCheck` + страховка в `FinalizeSpawnEvent`), `mobs.removeHostileOnEnter` (раз в 2 с). Значения true/false/не задано (наследование, иначе ванилла). Перед поиском зоны — проекция Sable. Пример `zones.json` не меняем: PvP выключен только в `spawn`, остальное выключено. `fireSpread` не делаем (нужен mixin).
- **Порядок**: сначала встроенные правила, потом скрипты. Скрипт только ужесточает: для уже отменённого правилом событие скрипту не отдаётся. OPAC/OPAC Warfare защищают сами, действует строжайшее.
- **KubeJS** — мягкая зависимость через мост `script.ScriptBridge` + интерфейс `ScriptHooks` (без типов KubeJS). Типы KubeJS и Rhino только в пакете `kubejs` (плагин `IkariaKubeJSPlugin` в `kubejs.plugins.txt`); без KubeJS мод работает как в 0.2. Глобальный объект `IkariaBorders` (`script.IkariaBordersJS`, `ZoneJS`, `zone.data`/`zone.temp`, `registerFlag`/`zone.flag`) и группа событий `IkariaBordersEvents` со всеми событиями §21.2 (`dynamicZoneExpired` зарегистрировано, но не вызывается до 0.45). Запросы: `distanceToBorder`, `depth`, `zonesAt` есть и в Java API (`IkariaBordersAPI`).
- **Изоляция и бюджет** (§21.7): `script.ScriptGuard`, `ScriptBudget`, `ScriptStats`; слушатели вызываются по одному через `kubejs.Dispatcher`; команда `/borders scripts` (уровень 2). Всё на основном потоке; вызов API из другого потока — понятная ошибка.
- Не входит: динамические зоны, `overrideShape`, runtime `setRule`/`setColor`, `/borders dynamic|overrides` (0.45); клиент, пакеты, Xaero (0.4); захват (CONCEPT.md §7).
- Не проверено в игре: всё KubeJS-специфичное (загрузка плагина, целевые события `playerEnter('wild', …)`, `hasResult`/`cancel`, `EventExceptionHandler`, свойства бинов в Rhino), `PositionCheck` для всех причин спавна, чанки/`forEachLoadedChunk`, подмена `replace`, `ExplosionEvent.Detonate` на живых взрывах, поведение рядом с OPAC.

## Этап 0.2 (реализован в `feat/0.2-core`, в ревью)

По ТЗ §7, §14, §18 (тесты 1–5):

- Один слушатель OPAC (`IClaimActionListenerAPI`) на `ServerStartedEvent`; читает **текущий** конфиг, перерегистрация при reload не нужна. Зона центра чанка → `claims.allow|forceload|anchor`; UNCLAIM/UNFORCELOAD/UNANCHOR/UNCLAIM_UNANCHORED — всегда можно. Отказ: `FORBID` + `global.messages.claimDenied` (`{zone}`, `{id}`, `{dim}`, `{cx}`, `{cz}`, `{action}`) и `ZoneClaimDeniedEvent`. Actionbar мод **не пишет** (только hud_manager).
- Исключения: **только** серверные приваты (`SpecialClaimOwners.SERVER`, UUID 0-0). Ни операторов, ни списков UUID. `isClaimAllowed` учитывает `claims.allow` и это исключение.
- Чистка (`SweepJob`/`ClaimSweeper`): через 30 с после старта, после `/borders reload` при изменении claim-релевантных зон/правил, вручную, периодически (`global.sweep.intervalMinutes`, 60). Порции `perTick` (200) на главном потоке, бюджет ≤ 2 мс/тик. `dryRun` из конфига действует на автоматические чистки; ручной `claims sweep` снимает по-настоящему. Лог `[ikaria_borders/claims] снято N у <ник> (<uuid>)`, `ZoneClaimsSweptEvent`, системное сообщение владельцу онлайн, если нет KubeJS.
- Команды (уровень 2): `/borders claims scan|sweep`, `/borders legacy-cleanup [dryrun]` (серверные приваты в подконфигах `ikaria-spawn|500|wild|edge`; сами подконфиги через API не удалить — пустые могут остаться).
- Без OPAC мод грузится: нет запрета и чистки, команды сообщают, что OPAC отсутствует. Типы OPAC используются только в пакете `opac` (`OpacApi`), доступ через `OpacBridge`.
- Чистая логика (`claims.ClaimPolicy`, `SweepJob`, `LegacyCleanupJob`) не зависит от OPAC и Minecraft и покрыта тестами без игры.
- Не проверено в игре: поведение слушателя и чистки на живом OPAC, снятие форслоада через повторный `claim(..., false)`, сопоставление имён подконфигов, поведение при `/opac server-claims`.

## Этап 0.1 (реализован, влит в `docs/tz`)

По ТЗ §18:

- Каркас NeoForge-мода (один jar: сервер + клиент; клиентская отрисовка карт — не в 0.1).
- Модель зон и формы §5.2: `circle`, `square` (Chebyshev: внутри при `max(|dx|,|dz|) < half`), `rect` (включительно по блокам), `polygon` (even-odd, ≥3 точек), `union`, `difference`. Только X/Z, без Y.
- Вложенность: `priority` (больше побеждает; при равенстве — кто объявлен выше в конфиге, плюс предупреждение), `parent` и наследование правил до `global`, зона `default: true` на измерение, `mapOnly` / `rules: false`.
- JSON-конфиг с полной проверкой (§8.3): типы, уникальные `id`, существующий `parent`, нет циклов, многоугольник ≥3 точек. При ошибке `/borders reload` **не подменяет** конфиг; ошибки с путём к полю (`zones[2].shape.half`) в ответ команды и в лог. При старте с битым конфигом сервер не падает: пустые зоны и громкий лог.
- Команды 0.1: `/borders here|at|list|info|reload|debug` и `/borders debug log on|off`. Права: `here`/`list` — уровень 0, остальное — уровень 2. Ответы системными сообщениями **на русском**.
- Публичное API: `IkariaBordersAPI` + `ZoneView` (§9): `zoneAt`, `zoneOf` (с Sable и кэшем игрока раз в 10 тиков), `zoneOfChunk`, `zones`/`zone`. `PlayerZoneChangeEvent` на шине `NeoForge.EVENT_BUS` (вход, смена измерения, телепорт, тик). `ZonesReloadedEvent` после успешного reload. `isClaimAllowed` можно реализовать по правилам конфига (без OPAC). События приватов/чистки — не в 0.1.
- Sable-проекция §5.3: мягкая зависимость. Если Sable установлен и `getContaining(...)` не null, позиция прогоняется через `projectOutOfSubLevel` до определения зоны. Без Sable — обычные координаты. Сборка и тесты **не должны требовать** jar Sable (reflection или optional compileOnly + no-op).
- Автотесты (§15, без игры): геометрия всех форм (в т.ч. вогнутый многоугольник, точка на ребре), union/difference, приоритеты, наследование, циклы родителей, валидация конфига с путём к полю, «центр чанка» на границах 100 и 1500 (1500/16 = 93.75).
- Пример конфига из §8.2 (`zones.json` / `zones.example.json`).
- Сборка: `./gradlew build` и тесты зелёные.

## Не входит в 0.4 (0.45+)

- Динамические зоны, `overrideShape`, runtime-правила и методы карты, `/borders dynamic|overrides` (0.45), концепт захвата (CONCEPT.md §7), `fireSpread`.
- Серверные mixins (клиентские mixin'ы Xaero — только в 0.4, см. выше).

## Сообщения и код

- Сообщения игрокам и ответы команд — по-русски.
- Комментарии в коде — по желанию, кратко, на английском или русском.
- Не коммитить секреты, `build/`, `.gradle/`, `run/`.
- Не пушить и не открывать PR, если об этом не попросили в текущем задании.
