# Ikaria Borders

Серверный и клиентский мод для сервера MC-Ikaria (Minecraft 1.21.1, NeoForge 21.1.x).

**Этап 0.1** (в ревью, без мержа): каркас мода, модель зон и формы, JSON-конфиг с проверкой, команды `/borders here|at|list|info|reload|debug`, публичное API и `PlayerZoneChangeEvent`, мягкая проекция Sable.

Ещё не в этом этапе: запрет приватов OPAC, применение PvP/урона/спавна, линии на карте Xaero, плагин KubeJS.

Канон: [docs/TZ_ikaria_borders_v2.md](docs/TZ_ikaria_borders_v2.md) (v2.1). Правила для агентов: [CLAUDE.md](CLAUDE.md).

- Mod id: `ikaria_borders`
- Пакет: `com.poligorn.ikariaborders`
- Конфиг: `config/ikaria_borders/zones.json` (пример: `zones.example.json`)
- Сборка: `./gradlew build` (Java 21). Автотесты геометрии и валидации — `./gradlew test`.

## Лицензия

MIT, copyright (c) 2026 Egor Skuratov / Poligorn. Полный текст — в файле [LICENSE](LICENSE).
