# Baritone Minefix

Patch for the ATM10 NeoForge Baritone jar.

## What it changes

Removes two broken `MixinItemStack` injection methods from Baritone:

- `onInit`
- `onItemDamageSet`

This keeps lazy hash recalculation through `getBaritoneHash()` and avoids the bad mixin injections.

## Build/install

Run:

```powershell
.\build.ps1
```

Output installs to the ATM10 `mods` folder as:

`baritone-neoforge-1.11.2-minefix.jar`

It also installs:

`baritone-minefix-tools-neoforge-1.21.1-1.3.4.jar`

That one tools jar contains:

- `baritoneautoeat`
- `pveguard`
- `altoclef`

## AutoEat

Client command:

```text
/autoeat on
/autoeat off
/autoeat status
/autoeat threshold 18
/autoeat inventory on
/autoeat offhand off
/autoeat restore on
/autoeat safety on
```

Default: on, eats at food <= 18, uses main hand so shield stays in offhand, never interrupts shielding/attacking/mining, waits until threat window is clear, can pull food from inventory, restores previous slot.

## PvE Guard

Client command:

```text
/pveguard on
/pveguard off
/pveguard status
/pveguard range 4.25
/pveguard weapon on
/pveguard inventoryweapon on
/pveguard dodge on
/pveguard shield on
```

## AltoClef NeoForge Port

Upstream AltoClef is archived, Fabric/Yarn, and pre-1.21. This jar contains the NeoForge/MojMap port core. ATM10 automation is an addon on top of that core.

Client command:

```text
/altoclef status
/altoclef on
/altoclef off
/altoclef help
/altoclef get minecraft:oak_log 16
/altoclef exec "get minecraft:diamond 3"
/altoclef baritone "goto 0 80 0"
/altoclef stop
@get minecraft:oak_log 16
@goto 0 80 0
@follow PlayerName
@give PlayerName minecraft:diamond 3
@equip iron
@food 20
@deposit
@stash x0 y0 z0 x1 y1 z1 [items...]
@gamer
@coords
@inventory
@list
@gamma 10
@locate_structure minecraft:stronghold
@reload_settings
@test food
@punk PlayerName
@status
@help
@stop
```

ATM10 addon command:

```text
/atmquests on
/atmquests off
/atmquests status
/atmquests next
/atmquests star
/atmquests starplan
/atmquests snapshot
/atmquests assess
/atmquests audit
/atmquests goal star
/atmquests goal all
/atmquests submit
/atmquests claim
/atmquests altar
/atmquests machines
/atmquests stop
```

Default: off, goal `star`. `/atmquests star` enables Star mode, writes `altoclef-atm-star-plan.txt`, submits/claims ready quests, routes visible structure/dimension/biome/kill steps, and starts safe Baritone mining for the current blocker. When enabled, the bot scans synced FTB Quests, submits ready item/checkmark tasks, claims rewards, blocks wrong-dimension mining, and asks Baritone to mine safe natural ore/block blockers when a matching target exists.

ATM10 readiness:

- mine/submit/claim: automated
- FTB Quests packets: queued and throttled to one payload every few ticks to avoid custom payload burst disconnects
- upstream core port: `/altoclef on|off|status`, all upstream `@` command names, and `@stop` smoke-test the NeoForge port shell
- upstream task path: `/altoclef get <item> [count]` runs the user task chain, inventory/entity/block trackers, recipe-book crafting, furnace smelting, visible mob loot, container deposit, dimension/portal movement, and Baritone mine/goto delegation
- Star quest scope: includes Star chapter, Allthemodium chapter, Star item tasks/rewards, shard rewards, and runic machine quests across chapters
- route gates: structure/dimension/biome/kill tasks are reported before recipe fallback; Allthemodium family ores gate to their valid dimensions, Piglich/Soul Lava gates to The Other, and Eternal Starlight items require `eternal_starlight:starlight` first
- Piglich hearts: if a Piglich is visible, Star mode routes to it instead of calling the item a generic craft/machine blocker
- quest order: ready tasks are ranked by carried progress, action type, dimension gate, and FTB quest position so it does not jump to late Star/Piglich branches just because their task id is smaller
- natural direct gather: safe world blocks like Velvetumoss, moss, logs, leaves, crystals, stone, sand, mud, and ores can be mined directly
- ATM Star recipe graph: built from the active ATM10 KubeJS paths for both final Star routes, core components, alloys, mini portals, shard fragments, and MI runic machines
- Star starter/live blocker: `/atmquests star`
- full Star plan + blocker audit output: `minecraft/altoclef-atm-star-plan.txt`
- snapshot output: `minecraft/altoclef-snapshot.txt` with dimension, position, current quest target, inventory key items, visible open container/ender chest items, nearby entities, and useful nearby blocks
- Runic Star Altar: `/atmquests altar` prints minimum active Modern Industrialization multiblock materials
- runic machine structures: `/atmquests machines` writes `minecraft/altoclef-atm10-machines.txt`
- machinery/reactors/fluids/energy routing: planned and reported; machine-specific IO still requires setup
- audit output: `minecraft/altoclef-atm10-audit.txt`

## Revert

Delete `baritone-neoforge-1.11.2-minefix.jar`, then re-enable the original jar:

`baritone-standalone-neoforge-1.11.2.jar.disabled` -> `baritone-standalone-neoforge-1.11.2.jar`

Delete `baritone-minefix-tools-neoforge-1.21.1-1.3.4.jar` to remove AutoEat, PvE Guard, and AltoClef ATM10 Quest Bot.
