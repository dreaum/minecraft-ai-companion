# Roadmap

## Project Status

The project is at the architecture stage. The runtime design has been chosen; no bot behavior is implemented yet.

## Milestone 0: Foundation

Establish a Node.js project using Mineflayer and a pinned Minecraft protocol version. Add configuration for a self-managed LAN world, companion name, approved player names, and a local home location.

Success signal: the companion connects, announces readiness only to approved players, and reconnects cleanly after a disconnect.

## Milestone 1: Reliable Companionship

Implement a single-owner state machine with these states:

| State | Entered by | Completion signal |
| --- | --- | --- |
| Idle | startup or task completion | accepts a new command |
| Following | approved player requests follow | remains near the player |
| Waiting | approved player requests stop | no movement command is active |
| Returning home | approved player requests home | position reaches the named home radius |
| Safety pause | danger or path failure | bot reports why it stopped |
| Stopped | cancellation or emergency stop | all navigation is cancelled |

Acceptance scenario: in a self-managed LAN world, an approved player can issue private-message commands to follow, stop, come, go home, and report status. Each command receives a completion, failure, or cancellation response based on observed game state.

## Milestone 2: Cooperative Tasks

Add bounded assistance tasks: collect a nearby requested block, pick up an explicitly requested item, and protect the companion's immediate area from hostile mobs. Tasks must stop when the player cancels them, the bot becomes unsafe, or their preconditions become false.

## Milestone 3: Conversation and Memory

Add a small persistent player-preference store and an optional LLM adapter. The LLM may select only allowlisted high-level intents; it cannot emit protocol packets, raw movement, arbitrary chat, or unrestricted actions.

## Decisions Log

- 2026-08-27: Use a protocol bot rather than a second Java client. The player sees an independent in-world teammate without the runtime cost and mod compatibility risk of Baritone or AltoClef.
- 2026-08-27: Limit initial testing to self-managed LAN worlds. The project does not include authentication bypass or anti-cheat evasion.
