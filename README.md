# Minecraft AI Companion

A companion-style Minecraft bot for self-managed LAN worlds.

The project goal is not autonomous speedrunning. The bot should feel like a cooperative player: stay near the player, respond to private messages, help with small tasks, report danger, and stop immediately when asked.

## Architecture Decision

The companion runs as a lightweight protocol bot instead of a second full Java Minecraft client.

```text
Player Minecraft client
        |
   LAN world
        |
Node.js companion bot
  - Mineflayer
  - pathfinding and task state machine
  - private-message command adapter
  - memory and optional LLM planner
```

This avoids binding the companion to Fabric, Baritone, AltoClef, or a visible second Minecraft client. Baritone and AltoClef remain useful design references, not runtime dependencies.

## First Milestone

A whitelisted player can privately command the bot to:

- follow and keep a safe distance;
- stop immediately;
- come to the player;
- return to a named home location;
- report its current activity and danger state;
- pause or retreat when health, lava, falling, or path failure makes continuation unsafe.

Every command must return a verified success, failure, or cancelled state. The bot must never claim a task succeeded without checking the game state.

## Constraints

- Initial validation happens only in self-managed Minecraft Java LAN worlds.
- The companion connects as an independently accepted player identity.
- No authentication bypass, automated registration/login, anti-cheat evasion, or uncontrolled public-chat behavior.
- High-risk actions such as dropping items, attacking players, or modifying builds are disabled by default.

## Roadmap

1. Establish the Mineflayer connection, private-message whitelist, and lifecycle logging.
2. Implement the companion state machine: idle, following, waiting, returning home, safety pause, and stopped.
3. Add reliable navigation, home memory, path-failure recovery, and status reports.
4. Add limited cooperative tasks such as nearby resource collection and basic protection.
5. Add short-term memory, personality, and an LLM that selects only approved high-level intents.

See [docs/roadmap.md](docs/roadmap.md) for the acceptance scenarios and progress log.
