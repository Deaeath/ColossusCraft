# ColossusCraft

Client automation suite for ATM10 NeoForge: patched pathfinding, combat guard, auto-eat, task automation, and ATM Star helpers.

All features are accessed via `/colossuscraft` (alias `/cc`).

## Install

Drop `colossuscraft-neoforge-1.21.1-1.0.1.jar` into your mods folder. That's it.

## Commands

### Core bot

```
/cc on|off|status|stop
/cc get <item> [count]
/cc goto <x> <y> <z>
/cc follow <player>
/cc kill <entity>
/cc food <units>
/cc come
/cc escape
/cc findchest <item> [goto]
/cc exec <internal-command>
/cc nav <pathfinder-command>
/cc help
```

### ATM10 quests (off by default)

```
/cc atm on|off|toggle|status
/cc atm next
/cc atm star
/cc atm starplan
/cc atm snapshot
/cc atm assess
/cc atm audit
/cc atm submit
/cc atm claim
/cc atm altar
/cc atm machines
/cc atm goal star|all
```

`/cc atm star` enables Star mode: writes `colossuscraft-atm-star-plan.txt`, submits/claims ready quests, routes structure/dimension/biome/kill steps, and starts Baritone mining for the current blocker.

### Utility & daemons

```
/cc barter <item> [count] [gold]
/cc barter daemon on <item> [count] [gold]
/cc barter daemon off
/cc sweep on <item> [count]
/cc sweep add <item> [count]
/cc sweep off
/cc utility status|stop|pause|resume
```

### Tasks & movement

```
/cc elytra
/cc portal build|nether
/cc dimension nether|overworld|end|stronghold
/cc location <target>
/cc sleep
/cc setspawn
/cc foodstock [units]
/cc gear armor <tier>
/cc stash open [item] [count]
/cc stash range <x0> <y0> <z0> <x1> <y1> <z1> [item] [count]
/cc task loot ruined_portals|desert_temples
```

### Emergency home

Always-on: teleports home (`/home`) when health is critical, falling into void, drowning, or burning with no fire resistance.

## Build from source

```powershell
.\build.ps1
```

Requires PrismLauncher with ATM10 installed and NeoForge moddev artifacts generated once via `gradle compileJava`.

## Revert

Delete `colossuscraft-neoforge-1.21.1-1.0.1.jar` and re-enable your original `baritone-standalone-neoforge-1.11.2.jar`.
