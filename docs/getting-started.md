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

The companion accepts only private-message commands. Each private message must contain exactly one of `follow`, `come`, `home`, `stop`, `pause`, `unpause`, or `status`; arguments and chained commands are rejected before task execution. All other AltoClef commands, including resource collection, item transfer, and arbitrary coordinate travel, are rejected. Baritone's automatic block breaking and block placement are disabled, so companion movement only uses naturally passable routes.

## PCL Two-Client Setup

Run two separate PCL instances against the same LAN world:

- A normal vanilla 1.20.1 instance for the player.
- A Fabric 1.20.1 instance for the companion, with the built jar placed in that instance's `mods/` directory.

The companion account joins the LAN world as an ordinary player. Only the Fabric companion instance needs this mod.

## Verification

Use a self-managed LAN world with two normal client identities. From a whitelisted player, privately send `follow`, `come`, `home`, `status`, then `stop`. The companion should reply to the same player with task progress and stop confirmation. A player absent from the whitelist must not be able to trigger a task.
