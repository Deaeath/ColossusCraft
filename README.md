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

`baritone-minefix-tools-neoforge-1.21.1-1.0.1.jar`

That one tools jar contains:

- `baritoneautoeat`
- `pveguard`

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

## Revert

Delete `baritone-neoforge-1.11.2-minefix.jar`, then re-enable the original jar:

`baritone-standalone-neoforge-1.11.2.jar.disabled` -> `baritone-standalone-neoforge-1.11.2.jar`

Delete `baritone-minefix-tools-neoforge-1.21.1-1.0.1.jar` to remove AutoEat and PvE Guard.
