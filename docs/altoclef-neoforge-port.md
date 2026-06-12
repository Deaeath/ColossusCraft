# ColossusCraft Core Port

Source: `https://github.com/gaucho-matrero/altoclef`, branch `1.19.4--marvion`.

Goal order:

1. Port upstream AltoClef-derived core to NeoForge/MojMap 1.21.1.
2. Boot the upstream command/task/event lifecycle in-game.
3. Restore Baritone integration against the installed NeoForge Baritone jar.
4. Restore trackers, slot control, input control, UI, and mixin events.
5. Restore upstream tasks/commands.
6. Add ATM10 quest/star automation as a task pack on top.

Current port status:

- Imported compile-safe upstream command parsing pieces.
- Imported upstream command executor pieces.
- Imported compile-safe upstream event bus pieces.
- Remapped simple event payloads to MojMap names.
- Imported compile-safe upstream real-time/progress helpers.
- Added `AltoClefPort` and `AltoClefPlatform` boundary.
- Added NeoForge platform shell using MojMap client APIs.
- Ported slot model for player inventory, cursor, crafting table, furnace-family, smithing table, brewing stand, and chest screens.
- Ported input controls, player extra controller, slot click handler, item storage tracker, live entity tracker, live block tracker, chunk/misc block trackers.
- Added user task chain and a first runnable catalogue task (`CollectItemTask`) that can satisfy inventory goals, walk to visible drops, or delegate direct block mining to Baritone.
- Added common helper/blacklist shims and core-owned NeoForge event hooks for screen opening and block interaction/break start/stop.
- Added `colossuscraft_core` as the core NeoForge mod id and moved ATM10 automation to addon mod id `colossuscraft_atm10`.
- Wired `/altoclef on|off|status|help|get|exec|baritone|stop` plus the upstream chat-prefix command names: `@help`, `@custom`, `@get`, `@goto`, `@follow`, `@give`, `@equip`, `@food`, `@deposit`, `@stash`, `@coords`, `@inventory`, `@list`, `@gamma`, `@locate_structure`, `@reload_settings`, `@gamer`, `@test`, `@punk`, `@status`, and `@stop`.
- Added NeoForge/MojMap task class coverage for all upstream `adris.altoclef.tasks` filenames. Many high-risk Fabric/Baritone-internal tasks are compatibility wrappers over the ported collector, container, movement, combat, and speedrun primitives while MojMap-specific behavior is filled in.
- ATM10 addon commands now live under `/atmquests`; it shares the core `AltoClefPort` instance instead of owning a second port.
- ATM10 Star task ordering now ranks ready FTB tasks by carried progress, action type, dimension gate, quest position, and id instead of raw id order.
- ATM10 Star planning now compares both local `star_altar.js` final Star routes and uses the lower blocker score for carried inventory.
- Snapshot reporting includes the currently open container/ender chest menu items in addition to carried inventory and nearby entities/blocks.
- FTB Quests submit/claim packets are queued and throttled instead of sent in bursts.

Known hard ports:

- Upstream source is Fabric/Yarn. This repo builds NeoForge/MojMap.
- Upstream depends on non-obfuscated Baritone API/internal classes.
- Installed ATM10 Baritone jar exposes mostly obfuscated classes, so full integration needs an adapter or an unobfuscated Baritone dependency.
- Fabric mixin filenames are intentionally not copied; the port uses NeoForge event hooks and local trackers instead.
- Current resource task covers item/drop/block acquisition, visible mob kill/loot, recursive recipe-book crafting, basic furnace smelting, open-container deposit, Nether portal construction, Baritone goto/mine delegation, and upstream task-name compatibility. `@gamer` handles prep, eyes, locate-to-stronghold travel, visible portal-frame filling, End entry, and direct dragon/crystal attacks. Remaining hard behavior gaps are robust stronghold portal-room excavation, automatic stash chest search/building, full piglin barter mechanics, and advanced survival chain parity.
