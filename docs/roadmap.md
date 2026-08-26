# Roadmap

## Project Status

The project has a vendored AltoClef/Fabric client baseline and an initial safe companion-command slice. It has not yet passed an end-to-end two-client LAN test.

## Milestone 0: Foundation

Establish a dedicated Minecraft Java Edition 1.20.1 client using Java 17, Fabric, and MiranCZ AltoClef with its bundled Baritone. Add companion session state, a self-managed LAN-world configuration, approved player names, and a local home location.

Current implementation: command authorization is limited to the Butler whitelist, only private-message companion commands are accepted, and Baritone block breaking/placing is disabled. The client also records companion state and cancels active companion movement when it detects lava, critically low health, or a dangerous fall. A real LAN run is pending.

## Milestone 1: Reliable Companionship

Implement a single-owner state machine with these states:

| State | Entered by | Completion signal |
| --- | --- | --- |
| Idle | startup or task completion | accepts a new command |
| Following | approved player requests follow | remains near the player |
| Paused | approved player requests pause | preserves the current task for an explicit resume |
| Returning home | approved player requests home | position reaches the named home radius |
| Safety pause | lava, critically low health, or dangerous fall | bot reports why it stopped and cancels navigation |
| Stopped | cancellation or emergency stop | all navigation is cancelled |

Acceptance scenario: in a self-managed LAN world, an approved player can issue private-message commands to follow, stop, come, go home, and report status. Each command receives a completion, failure, or cancellation response based on observed game state.

## Milestone 2: Cooperative Tasks

Add bounded assistance tasks: collect a nearby requested block, pick up an explicitly requested item, and protect the companion's immediate area from hostile mobs. Tasks must stop when the player cancels them, the bot becomes unsafe, or their preconditions become false.

## Milestone 3: Conversation and Memory

Add a small persistent player-preference store and an optional LLM adapter. The LLM may select only allowlisted high-level intents; it cannot emit protocol packets, raw movement, arbitrary chat, or unrestricted actions.

## Decisions Log

- 2026-08-27: Use a dedicated Java client rather than a protocol bot. This consumes more resources but provides the companion with a complete client-side world model and mature AltoClef/Baritone action execution.
- 2026-08-27: Limit initial testing to self-managed LAN worlds. The project does not include authentication bypass or anti-cheat evasion.
- 2026-08-27: Set Minecraft Java Edition 1.20.1 as the first supported protocol version. Later versions require their own compatibility verification before being advertised.
- 2026-08-27: Treat the product as a dedicated Fabric client that joins a world as a normal player, not as a Mineflayer/protocol bot. The AI layer will select only approved companion intents.
