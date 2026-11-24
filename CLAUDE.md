# Ikaria Borders — правила для агентов

Репозиторий: https://github.com/Poligorn/Ikaria-borders
ТЗ (канон): [docs/TZ_ikaria_borders_v2.md](docs/TZ_ikaria_borders_v2.md) — v2.1.

## Жёсткие правила

- Никогда не мержить, не ставить теги и не публиковать релиз без явного OK от Egor (Poligorn).
- Работа только в ветке + PR. Сейчас рабочая ветка: `feat/0.1-core`, база: `docs/tz`.
- Не печатать ключи, токены и секреты.
- Быть честным, что не проверено в игре.

## Идентификаторы (ответы Egor, не переоткрывать)

- Mod id: `ikaria_borders`
- Пакет: `com.poligorn.ikariaborders`
- API-пакет: `com.poligorn.ikariaborders.api`
- Конфиг: `config/ikaria_borders/zones.json` (JSON, не TOML/JSON5)
- Команда: `/borders`, алиас `/zones` (через redirect Brigadier)
- Лицензия в gradle/mods.toml: All Rights Reserved (как у других модов Ikaria; вопрос §19.12 ещё открыт, MIT не ставить)

## Стек

- Minecraft 1.21.1, NeoForge 21.1.x, Java 21, ModDevGradle.
- Целевая версия NeoForge: **21.1.250** (тестовый сервер) или совместимая 21.1.252; диапазон `[21.1.219,)`.
- Parchment: 1.21.1 / 2024.11.17.
- Жёстких зависимостей кроме NeoForge нет.
- Мягкие (compileOnly / optional, код устойчив к их отсутствию): Sable 2.0.6, позже OPAC / Xaero / KubeJS — не для этапа 0.1, кроме Sable-проекции.

## Этап 0.1 (текущий объём — не выходить за него)

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

## Не входит в 0.1

- Запрет приватов OPAC, sweep/scan, `legacy-cleanup`, `ZoneClaimDeniedEvent`, `ZoneClaimsSweptEvent`.
- Принудительное применение PvP/урона/взрывов/спавна (поля правил в модели и в выводе команд — да; обработчики событий — нет).
- Клиентская синхронизация и отрисовка Xaero (пакет sync можно не делать; reload клиентам не рассылать).
- Плагин KubeJS, динамические зоны, override, `/borders scripts`, `/borders resync`, концепт захвата (CONCEPT.md §7).
- Mixins.

## Сообщения и код

- Сообщения игрокам и ответы команд — по-русски.
- Комментарии в коде — по желанию, кратко, на английском или русском.
- Не коммитить секреты, `build/`, `.gradle/`, `run/`.
- Не пушить и не открывать PR, если об этом не попросили в текущем задании.
