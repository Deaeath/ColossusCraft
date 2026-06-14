## ColossusCraft v1.0.5

Client automation mod for **All the Mods 10** (NeoForge 1.21.1).

---

### What's new in v1.0.5

#### Mining regression fix
- `/cc mine` was pathing forever instead of mining — caused by ChatGPT's file-target injection using `searchY=112` (solid deepslate) for the allthemodium mining dimension. Reverted to the committed heightmap approach that locks the spiral Y at the actual surface.

#### `/cc scan list` — ore position dump
- Dumps all block positions currently known to BlockTracker into `<gamedir>/colossuscraft/ore_locations.txt`
- Sorted by distance; nearest 5 are also printed to chat
- Use after `/cc mine allthemodium_slate_ore` has been running a while to inspect what was found

#### Netherite tool tier
- Added `NETHERITE` to `MiningRequirement` enum and `StorageHelper` — bot correctly identifies netherite pickaxe as satisfying diamond+ requirement
- `getCurrentMiningRequirement` now correctly reports NETHERITE when only netherite pickaxe is held

#### DestroyBlockTask auto-equip
- Equips the best tool for each block before mining starts (won't waste netherite on blocks that don't need it)
- Paths to within 5 blocks via `GetToBlockTask` when the target is out of arm's reach

#### BlockTracker.forceRefresh()
- New method for on-demand re-scan (clears known map, triggers immediate update)

---

## ColossusCraft v1.0.3

Client automation mod for **All the Mods 10** (NeoForge 1.21.1).

---

### What's new in v1.0.3

#### Warden flee (always-on)
- Flee now fires unconditionally within 20b — passive, like MLG bucket, regardless of what task is running
- Previously only triggered when a user task was active; now covers manual walking/exploring too
- Warden fight (`/cc warden`) and the `/ccguard warden flee` toggle still suppress it as before

#### Warden fight fix
- Fixed infinite loop: bot no longer circles forever when warden is pit-trapped but player has no arrows
- With no ranged weapon, bot now goes straight to melee on the trapped warden instead of waiting for a shot angle

#### /cc goto routed through task chain
- `/cc goto` now uses `GetToBlockTask` instead of raw Baritone — flee saves and restores it like any other task
- No more stopping every 2 seconds for path recalculation — Baritone only re-issues the goal when pathing actually stops

#### /cc mine improvements
- Removed legacy Baritone-direct `/cc mine` — all mining now goes through `MineAndCollectTask` (flee-restorable)
- Block scan range expanded to 128h × 256v — finds ore underground without descending first (covers full Mining Dimension depth)
- `#allowInventory false` now respected: bot uses hotbar tools only, never pulls from main inventory

#### Inventory protection (`#allowInventory false`)
- `PlayerInteractionFixChain` now respects Baritone's `allowInventory` setting
- Tool swaps from main inventory (slot 9+) are blocked when `allowInventory=false`; hotbar-to-hand swaps still work

---

## ColossusCraft v1.0.2

### What's new in v1.0.2

#### Warden flee (PveGuard)
- Bot automatically sprints away from any warden that is **targeting the player** or within 10 blocks
- Saves the current task (e.g. `/cc mine`), flees to 20 blocks safe distance, then restores and resumes it
- Separate from shriek dodge — flee handles proximity, dodge handles attacks
- Toggle with `/ccguard warden flee on|off`

#### Shriek dodge (PveGuard)
- On sonic boom windup (≤15b) or melee swing (≤6b) → fires `/home` immediately
- `/back` automatically fires 15 seconds later once safe
- Toggle with `/ccguard warden dodge on|off`

#### Biome-aware mining
- `/cc mine allthemodium_ore` (and vibranium, unobtainium) automatically targets the **deep dark** biome
  - Navigates to correct dimension first, then `GoToBiomeTask` walks to deep dark, then spiral mines inside it
- `--biome` flag for any ore: `/cc mine ancient_debris --biome nether_wastes`
- Spiral explorer skips up to 3 off-biome waypoints before accepting any chunk

#### Auto-sneak overhaul
- Sneaks only near **sculk sensors / calibrated sensors / sculk shriekers** (10 block scan, cached every 5 ticks)
- Uses Baritone-native input override — no more key binding spam in toggle mode
- Sneak drops immediately when a warden is nearby so flee can sprint
- `/cc sneak on|off` still forces it manually

#### Bug fixes
- Fixed character freezing in deep dark due to `allowSprint=false` + Baritone input override being set permanently
- Fixed warden freeze (`AncientCityHelper`) triggering outside of `/cc warden` task

---

## ColossusCraft v1.0.0

Client automation mod for **All the Mods 10** (NeoForge 1.21.1).

ColossusCraft automates gameplay: pathfinding, mining, combat, looting, bartering, food, gear, elytra travel, dimension traversal, structure looting, and full ATM Star quest progression — all via `/colossuscraft` (alias `/cc`). A `/goto` shortcut also exists.

---

### Installation
1. Drop `colossuscraft-neoforge-1.21.1-1.0.1.jar` into your `mods` folder
2. Launch and run `/cc on` to start the bot

**Requirements:** Minecraft 1.21.1 · NeoForge · All the Mods 10 (or any NeoForge 1.21.1 modpack)

---

### Bot core

| Command | Description |
|---|---|
| `/cc on` / `/cc off` | Start or pause the automation engine |
| `/cc stop` | Stop the bot and cancel all tasks |
| `/cc status` | Show current task and engine state |
| `/cc help` | List all bot commands |
| `/cc exec <command>` | Run a raw bot command |
| `/cc nav <command>` | Run a raw pathfinder command |

---

### Movement & navigation

| Command | Description |
|---|---|
| `/cc goto <x> <y> <z>` | Pathfind to coordinates |
| `/cc goto <player>` | Pathfind to a named player |
| `/cc goto entity <type>` | Pathfind to nearest entity of that type |
| `/cc goto item <type>` | Pathfind to nearest dropped item of that type |
| `/goto ...` | Alias — same as `/cc goto` |
| `/cc follow <player>` | Continuously follow a player |
| `/cc come` | Pathfind to the nearest other player |
| `/cc coords` | Print current XYZ coordinates |
| `/cc location <target>` | Go to a named location (overworld, nether, end, stronghold, village, etc.) |
| `/cc dimension nether\|overworld\|end\|stronghold` | Travel to a dimension or find the stronghold portal |

---

### Gathering & mining

| Command | Description |
|---|---|
| `/cc get <item> [count]` | Gather any item by name or registry ID (`elytra`, `diamond`, `allthemodium:allthemodium_ingot 64`) |
| `/cc mine <block> [count]` | Mine a block type — tab-complete includes all mod blocks |
| `/cc mine <count> <block1> <block2> ...` | Mine multiple block types at once |
| `/cc sweep once <item> [count]` | Pick up matching dropped items from the ground once |
| `/cc sweep on <item> [count]` | Continuously sweep up matching drops whenever seen |
| `/cc sweep add <item> [count]` | Add another item to the sweep list |
| `/cc sweep off` | Stop sweeping |
| `/cc findchest <item>` | Search previously-opened chests for an item, show location and count |
| `/cc findchest <item> goto` | Go to nearest chest known to hold that item |

> **findchest note:** Minecraft only sends chest contents to the client when opened — the bot can only index chests you (or it) have already opened.

---

### Combat

| Command | Description |
|---|---|
| `/cc kill <entity>` | Hunt and kill an entity type (auto-equips weapons, shield) |
| `/cc bow on\|off\|status` | Toggle tick-driven bow/crossbow aimbot |
| `/cc gamer` | Full vanilla Minecraft speedrun — gathers gear, finds stronghold, defeats Ender Dragon |
| `/cc marvion` | Alternate speedrun route (Marvion strategy) — different prep and execution order |
| `/cc hero` | Hunt and kill ALL hostile mobs in the world continuously |
| `/cc idle` | Stand completely still and do nothing |

Kill aura is always-on while the bot is active — hostile mobs in melee range are attacked automatically.

**Bow aimbot:** ballistic pitch correction (vanilla arrow physics: v=3 b/t, g=0.05 b/t²), target movement prediction, auto-equips bow/crossbow from hotbar, charges to full before releasing.

---

### Warden strategies

Full automated warden cheese — trigger shrieker, trap, bow down.

| Command | Description |
|---|---|
| `/cc warden fight` | Use current inventory, skip resource gathering |
| `/cc warden fight gather` | Gather bow, arrows, hoe, iron blocks, pumpkins first |
| `/cc warden golems [count]` | Standalone iron golem squad (default 6) |
| `/cc warden stop` | Cancel warden task |

**`warden fight` flow:**
1. Find nearest sculk shrieker
2. Build iron golem squad if iron blocks + carved pumpkins are available
3. Trigger the shrieker
4. Sprint to the warden spawn point during its 30-second emerge animation
5. Mine 2×2 pit 2 blocks deep around its feet (hoe on dirt, pickaxe on stone)
6. Retreat 21+ blocks (just outside sonic boom range)
7. Bow aimbot fires; melee finish at low HP
8. Emergency `/home` fires throughout as safety net

**`warden golems` flow:** gather materials → position 15–20 blocks from warden → build T-shaped golem structures (iron blocks + carved pumpkin placed last to trigger spawn) → lure warden into golems → retreat and hide → finish weakened warden.

---

### Deep dark / ancient city

- **Auto-sneak:** holds sneak in the `deep_dark` biome so sculk sensors are not triggered
- **Warden freeze:** stops all movement when a nearby warden anger >= 70 (before it locks on); resumes when it calms or targets the player
- **Sprint suppression:** Baritone `allowSprint` is disabled while sneaking — the bot never runs in the deep dark

| Command | Description |
|---|---|
| `/cc sneak on\|off\|status` | Force sneak on or off, or check current sneak state |

---

### Food & survival

| Command | Description |
|---|---|
| `/cc food <units>` | Gather food worth N food units |
| `/cc meat <count>` | Gather N pieces of raw meat |
| `/cc foodstock [units]` | Stock up on food before a long trip (default 60 units) |
| `/cc autohunt on\|off` | Toggle continuous background food gathering |
| `/cc sleep` | Sleep through the night using a placed or found bed |
| `/cc setspawn` | Place a bed and set spawn point |

BaritoneAutoEat runs automatically in the background, eating food when hungry without interrupting tasks.

---

### Gear & equipment

| Command | Description |
|---|---|
| `/cc equip [tier_or_item]` | Auto-equip best available armor (tiers: netherite, diamond, iron, gold, leather) |
| `/cc gear armor <tier_or_item>` | Same, alternate syntax |
| `/cc elytra` | Get an elytra — finds stronghold, defeats Ender Dragon, locates End Ship |

---

### Bartering (Piglins)

| Command | Description |
|---|---|
| `/cc barter <item> [count] [gold]` | Trade with Piglins (`/cc barter fire_resistance_potion 8`) |
| `/cc barter daemon on <item> [count] [gold]` | Run bartering continuously until target count is met |
| `/cc barter daemon off` | Stop background bartering |
| `/cc barter stop` | Cancel active barter task |
| `/cc barter status` | Show barter daemon state |

Default gold buffer is 32 ingots per call. Throws gold at Piglins and collects drops.

---

### Looting & structures

| Command | Description |
|---|---|
| `/cc task loot ruined_portals` | Find and loot all nearby ruined portals (gold, obsidian, flint and steel) |
| `/cc task loot desert_temples` | Find and loot desert temples |
| `/cc locate <structure>` | Find and pathfind to a structure (tab-complete lists options) |
| `/cc portal build` | Automatically build a Nether portal with available obsidian |

---

### Storage & depositing

| Command | Description |
|---|---|
| `/cc deposit [items]` | Deposit items into the nearest open container |
| `/cc stash open` | Dump full inventory into the open container |
| `/cc stash open <item> [count]` | Deposit a specific item into the open container |
| `/cc stash range <x0> <y0> <z0> <x1> <y1> <z1> [item] [count]` | Deposit into any container within a world region |
| `/cc inventory [item]` | Print inventory summary or count of a specific item |

---

### Utility

| Command | Description |
|---|---|
| `/cc gamma [value]` | Set fullbright/gamma. No value toggles max. |
| `/cc list` | List all items the bot knows how to obtain |
| `/cc coverwithblocks` | Cover exposed nether lava with blocks (safe pathing) |
| `/cc coverwithsand` | Cover exposed nether lava with sand |
| `/cc punk <player>` | Hunt and kill a specific player |
| `/cc give <player> <item> [count]` | Collect an item and deliver it to a player |
| `/cc custom <task>` | Run a custom task by class name |
| `/cc test [name]` | Run a test task |
| `/cc utility stop\|pause\|resume\|status` | Control background utility daemons |

---

### Emergency home

Always-on safety net. Fires `/home` automatically on:
- HP at or below threshold (default 3 hearts)
- Lava without Fire Resistance
- Drowning
- Wither + low HP
- Burning + low HP
- Lethal fall with no water bucket
- Void fall

| Command | Description |
|---|---|
| `/cc home on\|off` | Enable or disable emergency home |
| `/cc home threshold <hearts>` | Set HP trigger (default 3 hearts = 6 HP) |
| `/cc home status` | Show state and threshold |
| `/cc escape` | Trigger `/home` immediately right now |

---

### ATM10 quest automation

Reads your FTB Quests progress and automatically works toward the ATM Star.

| Command | Description |
|---|---|
| `/cc atm on\|off\|toggle` | Enable or disable ATM quest automation |
| `/cc atm status` | Show goal, current quest target, and progress |
| `/cc atm next` | Preview the next quest step without starting automation |
| `/cc atm star` | Immediately work on the ATM Star — submits completed tasks, starts next step |
| `/cc atm starplan` | Write full ATM Star route to `colossuscraft-atm-star-plan.txt` |
| `/cc atm snapshot` / `/cc atm assess` | Write current-state snapshot to `colossuscraft-snapshot.txt` |
| `/cc atm audit` | Write full quest audit to `colossuscraft-atm10-audit.txt` |
| `/cc atm submit` | Manually submit all completed quest tasks |
| `/cc atm claim` | Manually claim all pending quest rewards |
| `/cc atm goal star` | Set goal to ATM Star only (default) |
| `/cc atm goal all` | Set goal to complete ALL quests |
| `/cc atm altar` | List minimum materials for the Runic Star Altar |
| `/cc atm machines` | Write machine report to `colossuscraft-atm10-machines.txt` |

**ATM Star path (automated):**
1. Gather Allthemodium — Overworld caves or allthemodium:mining dimension (via teleport pad)
2. Gather Vibranium — Nether or allthemodium:the_other dimension
3. Gather Unobtainium — The End
4. Kill Piglich in allthemodium:the_other (ancient pyramids) for Piglich Hearts
5. Build Modern Industrialization machines: runic_crucible, runic_enchanter, auto_forge, star_altar
6. Navigate eternal_starlight:starlight if needed (find portal_ruins, defeat Gatekeeper, activate with Orb of Prophecy)
7. Craft the ATM Star at the Runic Star Altar

The bot detects dimension routing gates automatically — if you need vibranium but are in the Overworld, it will tell you to go to the Nether first.

---

### AI chat (`/cc ai`)

| Command | Description |
|---|---|
| `/cc ai <message>` | Ask GPT-4o with a live screenshot attached |

Captures the current game screen and sends it to GPT-4o with your message. The AI can see exactly what you see and answer questions about the game, commands, or what to do next. Response appears in chat within seconds.

**Setup:** Create `colossuscraft-ai.key` in your Minecraft game directory (the folder containing `options.txt`, not the mods folder). Put your OpenAI API key on one line, plain text, no BOM. The file is never committed to any repository.

The AI knows every ColossusCraft command and the full ATM10 progression path — you can ask it anything about the mod.
