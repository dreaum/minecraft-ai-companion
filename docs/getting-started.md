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

After Fabric loads the mod once, create or edit the generated files in the companion client's game directory:

- `configs/butler.json`: keep `useButlerWhitelist` set to `true`.
- `altoclef_butler_whitelist.txt`: add the exact Minecraft name of each player allowed to command the companion.

The companion accepts only private-message commands. In the current vertical slice, the command policy permits `follow`, `come`, `home`, `stop`, `pause`, `unpause`, and `status`; all other AltoClef commands, including resource collection, item transfer, and arbitrary coordinate travel, are rejected before task execution. Baritone's automatic block breaking and block placement are disabled, so companion movement only uses naturally passable routes.

## Verification

Use a self-managed LAN world with two normal client identities. From a whitelisted player, privately send `follow`, `come`, `home`, `status`, then `stop`. The companion should reply to the same player with task progress and stop confirmation. A player absent from the whitelist must not be able to trigger a task.
