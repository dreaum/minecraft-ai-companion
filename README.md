# Minecraft AI Companion

[简体中文](README.zh-CN.md)

A companion-style Minecraft bot for self-managed LAN worlds.

**Initial Minecraft Java Edition target: 1.20.1.**

The project goal is not autonomous speedrunning. The bot should feel like a cooperative player: stay near the player, respond to private messages, help with small tasks, report danger, and stop immediately when asked.

## Architecture Decision

The companion runs in a dedicated Minecraft Java client. The baseline is Minecraft Java Edition 1.20.1, Java 17, Fabric, and the maintained MiranCZ AltoClef fork, which bundles its compatible Baritone implementation.

```text
Player Minecraft client                 Companion Minecraft client
        |                                          |
        +--------------- LAN world ----------------+
                                                   |
                                  Companion Mod
                                  - social behavior and permissions
                                  - private-message command adapter
                                  - safety controller and memory
                                  - AltoClef tasks and Baritone movement
                                  - optional LLM planner
```

This costs a second Minecraft client instance, but gives the companion the same client-side world model and interaction capabilities as a player. BaritonePlus is not a runtime dependency because its public codebase is much less actively maintained.

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

- Initial validation happens only in self-managed Minecraft Java 1.20.1 LAN worlds.
- The companion connects as an independently accepted player identity.
- No authentication bypass, automated registration/login, anti-cheat evasion, or uncontrolled public-chat behavior.
- High-risk actions such as dropping items, attacking players, or modifying builds are disabled by default.

## Roadmap

1. Establish the dedicated Fabric client, private-message whitelist, and lifecycle logging.
2. Implement the companion state machine: idle, following, waiting, returning home, safety pause, and stopped.
3. Add reliable navigation, home memory, path-failure recovery, and status reports.
4. Add limited cooperative tasks such as nearby resource collection and basic protection.
5. Add short-term memory, personality, and an LLM that selects only approved high-level intents.

See [docs/roadmap.md](docs/roadmap.md) for the acceptance scenarios and progress log.
