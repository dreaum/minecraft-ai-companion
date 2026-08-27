# First Client Run

This project builds a dedicated Fabric client from the vendored AltoClef source in `client/altoclef`.

## Requirements

- Minecraft Java Edition 1.20.1 for the companion account.
- Java 17 to run Minecraft 1.20.1.
- Java 21 to build the current multi-version AltoClef source tree.
- A self-managed LAN world and a normal, independently accepted companion player identity.

## Build

From `client/altoclef`, run:

```powershell
.\gradlew.bat :1.20.1:build
```

The 1.20.1 remapped mod jar is written below `client/altoclef/versions/1.20.1/build/libs`.

## First-Run Safety Setup

After Fabric loads the mod once, create or edit the generated files in the companion client's game directory. AltoClef stores them below `altoclef/`:

- `altoclef/configs/butler.json`: keep `useButlerWhitelist` set to `true`.
- `altoclef/altoclef_butler_whitelist.txt`: add the exact Minecraft name of each player allowed to command the companion.
- `altoclef/altoclef_settings.json`: review the configured `homeBasePosition` before using `home`.

The companion accepts only private-message commands from its Butler whitelist. Each private message must contain exactly one approved action; chained commands are rejected before task execution. Supported actions are `collect <item> <count>`, `craft <item> <count>`, `smelt <item> <count>`, `goto <x> <y> <z>`, `follow`, `come`, `home`, `attack <entity> <count>`, `protect`, `unprotect`, `give <item> <count>`, `status`, `queue`, and `stop`. Counts are limited to 1 through 64. The first active requester becomes the single session owner; other players are rejected until the session finishes or receives `stop`.

Resource requests use AltoClef's catalogue and may mine, craft, smelt, place supporting blocks, and travel across dimensions. Do not add locations to the protected-position settings if the companion must modify them. `give` deliberately does not acquire missing items: it only transfers items already in the companion inventory.

## PCL Two-Client Setup

Run two separate PCL instances against the same LAN world:

- A normal vanilla 1.20.1 instance for the player.
- A Fabric 1.20.1 instance for the companion, with the built jar placed in that instance's `mods/` directory.

The companion account joins the LAN world as an ordinary player. Only the Fabric companion instance needs this mod.

## Verification

Use a self-managed LAN world with two normal client identities. From the whitelisted owner, test `follow`, `collect iron_ingot 1`, `craft torch 4`, `smelt iron_ingot 1`, `protect`, `give <item> <count>`, a higher-priority movement request, then `stop`. The companion should reply to the same player with queue, execution, completion, failure, or cancellation status. A player absent from the whitelist and a second whitelisted player without ownership must not be able to trigger a task.
