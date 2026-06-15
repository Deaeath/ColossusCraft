# ColossusCraft

Client automation suite for ATM10 NeoForge: patched pathfinding, combat guard, auto-eat, task automation, and ATM Star helpers.

All features are accessed via `/colossuscraft` (alias `/cc`).

## Install

Drop `colossuscraft-neoforge-1.21.1-1.0.6.jar` into your mods folder. That's it.

## Commands

### Core bot

```
/cc fortune [on|off]
/cc on|off|status|stop|help|idle|coords|list|reload
/cc get <item> [count]
/cc goto <x> <y> <z>
/cc follow <player>
/cc kill <entity>
/cc food <units>
/cc meat <count>
/cc come
/cc escape
/cc findchest <item> [goto]
/cc give <player> <item> [count]
/cc punk <player>
/cc inventory [item]
/cc locate <structure>
/cc gamma [value]
/cc equip [tier_or_item]
/cc deposit [items]
/cc custom <task>
/cc test [name]
/cc gamer
/cc marvion
/cc hero
/cc coverwithblocks
/cc coverwithsand
/cc exec <internal-command>
/cc nav <pathfinder-command>
/cc mine <block> [count]
/cc mine <count> <block1> <block2> ...
/cc mine <block> --biome <biome>
/cc sneak auto|on|packet|off|status
```

### PveGuard

```
/ccguard on|off|toggle
/ccguard combat on|off
/ccguard eat on|off
/ccguard warden on|off
/ccguard warden dodge on|off|toggle
/ccguard warden flee on|off|toggle
/ccguard range <blocks>
/ccguard status
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

## Sneak modes

| Mode | Behavior |
|------|----------|
| `auto` *(default)* | Packet sneak only while in the deep dark biome; off elsewhere so you can sneak normally |
| `packet` | Always-on packet sneak — server sees you sneaking, client moves freely (old default) |
| `on` | Real sneak — ledge protection active, movement slowed |
| `off` | No sneak — sculk sensors can trigger |

## Changelog

### v1.0.6
- **Mining fixed** — bot no longer stops randomly or paths to distant ores when closer ones exist; progress checker now respects approach phase
- **Phantom detection** — PVEGuard now kills Phantoms (20-block range, overhead LoS bypass)
- **Partial-block unstuck** — automatically breaks adjacent blocking blocks after 3s of no movement
- **Crafting fixed** — correct recipe selected via ingredient-matching (fixes wrong modpack recipe in ATM10); stops looping when ingredients run out
- **Smoker placement** — sneaks while placing smoker so crafting tables don't intercept the click
- **Food entity scan fixed** — entity blacklist now decays (3 strikes + resets when player gets closer); was permanently blocking sheep/cows after one timeout
- **Chat while container open** — pressing T or / now opens chat overlay even inside crafting tables, chests, etc.
- **`/cc fortune [on|off]`** — toggle fortune-preserve mode: routes fortune pickaxe to ore blocks only, uses non-fortune tools on everything else

### v1.0.5
- **`/cc mine` fixed** — restored Baritone goal-pathing so the bot actually walks to and mines target blocks instead of walking past them
- **Block name resolution** — `oak_log` now correctly resolves to `minecraft:oak_log` instead of matching modded blocks with the same path name
- **Sneak `auto` mode** — packet sneak activates only in the deep dark; off by default everywhere else (replaces always-on `packet` as the default)
- **Tool equip spam fixed** — `PlayerInteractionFixChain` throttled to 1×/sec (was every tick), stopping constant hotbar churn that reset block break progress
- **KillAura** — no longer raises shield against passive mobs (sheep, cows, etc.)
- **Spiral mining** — fixed descending to bedrock when chunk heightmap was unloaded
- **NETHERITE tier** — added to `MiningRequirement` and `SatisfyMiningRequirementTask`

## Build from source

```powershell
.\build.ps1
```

Requires PrismLauncher with ATM10 installed and NeoForge moddev artifacts generated once via `gradle compileJava`.

## Revert

Delete `colossuscraft-neoforge-1.21.1-1.0.5.jar` and re-enable your original `baritone-standalone-neoforge-1.11.2.jar`.
