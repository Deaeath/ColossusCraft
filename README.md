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

## Revert

Delete `baritone-neoforge-1.11.2-minefix.jar`, then re-enable the original jar:

`baritone-standalone-neoforge-1.11.2.jar.disabled` -> `baritone-standalone-neoforge-1.11.2.jar`
