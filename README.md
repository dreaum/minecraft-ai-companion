# Minecraft AI Companion

[简体中文](README.zh-CN.md)

A companion-style Minecraft bot for self-managed LAN worlds.

**Initial Minecraft Java Edition target: 1.20.1.**

The project goal is not autonomous speedrunning. The bot should feel like a cooperative player: stay near the player, act on the owner's public-chat requests, help with small tasks, report danger, and stop immediately when asked.

## Architecture Decision

The companion runs in a dedicated Minecraft Java client. The baseline is Minecraft Java Edition 1.20.1, Fabric, and the maintained MiranCZ AltoClef fork, which bundles its compatible Baritone implementation. Minecraft 1.20.1 runs on Java 17; the current multi-version client source needs Java 21 to build it.

```text
Player Minecraft client                 Companion Minecraft client
        |                                          |
        +--------------- LAN world ----------------+
                                                   |
                                  Companion Mod
                                  - public-chat listener with whitelist auth
                                  - reflexive safety controller
                                  - AltoClef tasks and Baritone movement
                                  - WebSocket bridge to the Python LLM backend
```

This costs a second Minecraft client instance, but gives the companion the same client-side world model and interaction capabilities as a player. BaritonePlus is not a runtime dependency because its public codebase is much less actively maintained.

## Java Side

The Fabric mod now keeps exactly two responsibilities:

1. **Reflexive safety controller.** It never waits for Python or the LLM: food and saturation are kept topped up (so natural regeneration can heal), and nearby hostiles are attacked automatically with the strongest melee weapon on the hotbar. Environmental recovery — surfacing from water, escaping lava, extinguishing fire — is handled by AltoClef survival chains, which temporarily preempt the active task and let it continue afterwards instead of cancelling it. Tasks are never paused for water, lava, fire, hunger, or low health; only suffocation and truly unrecoverable falls still trigger a safety pause.
2. **Python bridge.** A local WebSocket bridge (\`AgentBridge\`) exposes a tool registry to the Python backend and returns tool results. Python owns the LLM conversation; Java only executes approved tools such as \`observe_world\`, \`altoclef_task\`, \`baritone_goal\`, \`move\`, \`look\`, \`attack_entity\`, \`use_item\`, \`interact_block\`, and \`chat_public\`.

The legacy Java-side LLM parser, tutorial index, task-experience store, and private-message command adapter have been removed.

## Chat Channel

A whitelisted player talks to the companion directly in the public chat channel — private whispers are not read. Each message is forwarded to the local Python agent, which maps deterministic companion requests (\`come\`, \`follow\`, \`collect\`, \`craft\`, \`smelt\`, \`goto\`, \`attack\`, \`protect\`, \`give\`, \`stop\`, \`status\`, \`queue\`) straight to \`altoclef_task\` and otherwise asks the LLM to choose from approved tools. Task counts are limited to 1 through 64; malformed items, entities, parameters, and verbs are rejected before execution.

Every task must return a verified success, failure, or cancelled state. The bot must never claim a task succeeded without checking the game state.

## Constraints

- Initial validation happens only in self-managed Minecraft Java 1.20.1 LAN worlds.
- The companion connects as an independently accepted player identity.
- Public-chat messages from non-whitelisted players are rejected; no authentication bypass, automated registration/login, or anti-cheat evasion is included.
- The bot does not broadcast in public chat beyond replies to the active whitelisted owner.
- Abilities such as dropping items, attacking entities, and modifying builds remain behind the bridge-tool layer; the LLM may only select approved high-level intents and cannot emit raw protocol or arbitrary chat.

## Roadmap

1. Establish the dedicated Fabric client, public-chat whitelist, and lifecycle logging.
2. Implement the reflexive safety controller: auto-eat, auto-defend, drowning escape, hazard stop.
3. Harden the Python bridge: reliable navigation, home memory, path-failure recovery, and status reports.
4. Add limited cooperative tasks such as nearby resource collection and basic protection.
5. Add short-term memory and personality for the LLM while keeping only allowlisted high-level intents.

See [docs/roadmap.md](docs/roadmap.md) for the acceptance scenarios and progress log.
See [docs/getting-started.md](docs/getting-started.md) for the local build and first-run safety setup.
