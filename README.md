# Minecraft AI Companion

[简体中文](README.zh-CN.md)

A companion-style Minecraft bot for self-managed LAN worlds.

**Initial Minecraft Java Edition target: 1.20.1.**

The project goal is not autonomous speedrunning. The bot should feel like a cooperative player: stay near the player, act on the owner's public-chat requests, help with small tasks, report danger, and stop immediately when asked.

## Architecture

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

This costs a second Minecraft client instance, but gives the companion the same client-side world model and interaction capabilities as a player.

## Java Side

The Fabric mod keeps exactly two responsibilities:

1. **Reflexive safety controller.** It never waits for Python or the LLM: food and saturation are kept topped up (so natural regeneration can heal), and nearby hostiles are attacked automatically with the strongest melee weapon on the hotbar. Environmental recovery — surfacing from water, escaping lava, extinguishing fire — is handled by AltoClef survival chains, which temporarily preempt the active task and let it continue afterwards instead of cancelling it. Tasks are never paused for water, lava, fire, hunger, or low health; only suffocation and truly unrecoverable falls still trigger a safety pause.

2. **Python bridge.** A local WebSocket bridge (`AgentBridge`) exposes a tool registry to the Python backend and returns tool results. Python owns the LLM conversation; Java only executes approved tools.

### Key Java Components

| Component | File | Role |
|-----------|------|------|
| `AltoClef` | `AltoClef.java` | Central mod entry point; initializes all managers, chains, trackers, and the bridge |
| `Butler` | `Butler.java` | Listens to public chat; forwards whitelisted player messages to the Python agent |
| `UserAuth` | `UserAuth.java` | Whitelist/blacklist authorization for butler users |
| `CompanionOrchestrator` | `CompanionOrchestrator.java` | Manages the companion task queue, safety pauses, preemption, and timeouts |
| `CompanionIntentParser` | `CompanionIntentParser.java` | Parses deterministic companion commands (collect, follow, goto, etc.) |
| `CompanionTaskFactory` | `CompanionTaskFactory.java` | Adapts approved companion intents to AltoClef tasks |
| `CompanionSession` | `CompanionSession.java` | Tracks companion state (IDLE, FOLLOWING, EXECUTING, PROTECTING, etc.) |
| `EasyAISafetyController` | `EasyAISafetyController.java` | Low-level reflexes: auto-defend, water-surfacing jump; never waits for Python |
| `CompanionSafetyController` | `CompanionSafetyController.java` | Monitors for suffocation and dangerous falls (5-tick debounced) |
| `CompanionSafetyRules` | `CompanionSafetyRules.java` | Pure safety policy; testable without a Minecraft client |
| `AgentBridge` | `AgentBridge.java` | WebSocket client connecting to the Python backend |
| `BuiltinAgentTools` | `BuiltinAgentTools.java` | 18 registered tools (observe_world, inspect_nearby_blocks, altoclef_task, etc.) |
| `BlockScanner` | `BlockScanner.java` | Scans loaded chunks for blocks; provides `scanNearbyBlocks()` and cached queries |
| `FoodChain` | `FoodChain.java` | Auto-eat: eats when hunger gap fits smallest food, or immediately when health <= 8 |
| `TaskCatalogue` | `TaskCatalogue.java` | Hardcoded list of all obtainable resources and their collection tasks |
| `MineAndCollectTask` | `MineAndCollectTask.java` | Resource collection task; scans nearby blocks, selects target, mines it |
| `AIConfigCommand` | `AIConfigCommand.java` | In-game command to view/update model, URL (API key excluded from chat) |
| `InputControls` | `InputControls.java` | Manages synthetic key presses (hold, release, tryPress) |

### Registered Bridge Tools

| Tool | Description |
|------|-------------|
| `observe_world` | Returns player position, health, food, nearby hostiles, nearby drops, inventory |
| `inspect_nearby_blocks` | Scans loaded blocks within radius and returns block ID, coordinates, distance |
| `altoclef_task` | Runs a companion command (collect, craft, smelt, follow, come, goto, etc.) |
| `baritone_goal` | Sets a Baritone pathfinding goal to a block position |
| `baritone_cancel` | Cancels the current Baritone path |
| `move` | Holds a movement key for a number of ticks |
| `look` | Sets the player's yaw and pitch |
| `press_key` | Presses a key for one frame |
| `release_key` | Releases a key |
| `attack_entity` | Attacks the nearest hostile entity |
| `use_item` | Right-clicks |
| `interact_block` | Interacts with a block at given coordinates |
| `chat_public` | Sends a message to public chat |
| `inventory` | Returns the player's inventory contents |
| `select_hotbar` | Selects a hotbar slot |
| `drop_item` | Drops an item from inventory |
| `pickup_item` | Picks up a dropped item |
| `wait_ticks` | Waits a number of ticks |
| `stop_all` | Cancels all tasks and releases all keys |

### Companion Commands

Players on the butler whitelist issue commands in public chat. The command prefix is `@` (or `.`).

| Command | Example | Description |
|---------|---------|-------------|
| `collect <item> <count>` | `collect oak_log 1` | Collects a resource (1-64) |
| `craft <item> <count>` | `craft stick 4` | Crafts an item |
| `smelt <item> <count>` | `smelt iron_ingot 1` | Smelts an item |
| `goto <x> <y> <z>` | `goto 100 64 -50` | Walks to coordinates |
| `follow` | `follow` | Follows the owner |
| `come` | `come` | Comes to the owner |
| `home` | `home` | Returns to the configured home position |
| `attack <entity> <count>` | `attack skeleton 1` | Attacks an entity type |
| `protect` | `protect` | Protects the owner |
| `unprotect` | `unprotect` | Stops protecting |
| `give <item> <count>` | `give oak_log 1` | Gives items from inventory to the owner |
| `status` | `status` | Reports current companion state |
| `queue` | `queue` | Shows the task queue |
| `stop` | `stop` | Stops all tasks and clears the queue |

### Local Commands

The `@` prefix runs AltoClef built-in commands:

| Command | Description |
|---------|-------------|
| `@ai_config show` | Shows current model, URL, and whether the API key is set |
| `@ai_config model <name>` | Sets the LLM model name |
| `@ai_config url <http(s) URL>` | Sets the LLM API endpoint |
| `@reload_settings` | Reloads bot settings and butler whitelist/blacklist |
| `@get <item>` | Collects a resource |
| `@list` | Lists available resource catalogue items |
| `@inventory` | Shows the companion's inventory |
| `@coords` | Shows the companion's current coordinates |
| `@food` | Collects food |
| `@meat` | Collects meat |
| `@goto <x> <y> <z>` | Walks to coordinates |
| `@hero` | Runs the hero task |
| `@idle` | Runs the idle task |
| `@equip <item>` | Equips an item |
| `@deposit` | Deposits items into nearby containers |
| `@stash` | Stashes items |
| `@locate_structure <structure>` | Locates a structure |
| `@set_gamma <value>` | Sets gamma/brightness |
| `@gamer <seconds>` | Runs the gamer task |
| `@marvion` | Runs the Marvion task |
| `@give <item> <count>` | Gives items to the owner |

## Python Side

The Python agent backend (`agent_backend/`) owns the LLM conversation and connects to Java over a local WebSocket.

### Key Python Files

| File | Role |
|------|------|
| `server.py` | WebSocket server; connects to Java bridge, handles tool calls and world events |
| `llm_client.py` | OpenAI-compatible LLM client; sends chat completions, parses tool calls |
| `agent_loop.py` | Agent conversation loop; maps deterministic commands, manages message history |
| `protocol.py` | JSON protocol v1: message types, validation, encode/decode |
| `agent_gui.py` | Standalone Tkinter monitor for live Agent messages, tools, results, and retries |
| `block_debug_gui.py` | Tkinter debug GUI for inspecting nearby blocks |

### Protocol (v1)

The Java bridge acts as the WebSocket **client**; the Python backend is the **server**.

**Handshake:**
```
Java -> Python: hello (with tools array)
Python -> Java: hello_ack
```

**Tool calls (Python -> Java):**
```json
{"type": "tool_call", "id": "uuid", "tool": "observe_world", "arguments": {}}
```

**Tool results (Java -> Python):**
```json
{"type": "tool_result", "id": "uuid", "status": "completed", "observation": {...}}
```

**User requests (Java -> Python):**
```json
{"type": "user_request", "id": "uuid", "user": "player_name", "request": "collect oak_log 1"}
```

**World events (Java -> Python, every 20 ticks):**
```json
{"type": "world_event", "event": "tick", "observation": {...}}
```

### Direct Command Mapping

The Python agent maps common deterministic requests directly to `altoclef_task` without involving the LLM:

| Chat Input | Mapped Command |
|------------|---------------|
| `come`, `过来`, `来我这里` | `come` |
| `follow`, `follow me`, `跟随`, `跟着我` | `follow` |
| `protect`, `保护我` | `protect` |
| `stop`, `停止` | `stop` |
| `status`, `状态` | `status` |
| `queue`, `队列` | `queue` |
| `橡木原木`, `砍树`, `砍木头` | `collect oak_log 1` |
| `collect <item> <count>` | Passed through |
| `craft <item> <count>` | Passed through |
| `goto <x> <y> <z>` | Passed through |

### Agent Monitor GUI

Start the live monitor independently of Minecraft:

```bash
python -m agent_backend.agent_gui
```

It connects to `ws://127.0.0.1:8765/monitor` and displays user messages, Agent replies, tool calls, results, and retries. The backend can be started with `python -m agent_backend.server` or `agent_backend/start-agent.bat`.

### Debug GUI

The `block_debug_gui.py` script provides a standalone Tkinter window for inspecting nearby blocks without involving the LLM:

```bash
python -m agent_backend.block_debug_gui
```

It connects to `ws://127.0.0.1:8765`, calls `inspect_nearby_blocks`, and displays block ID, coordinates, and distance in a sortable table. This tool is useful for debugging collection task failures.

## Chat Channel

A whitelisted player talks to the companion directly in the public chat channel — private whispers are not read. Each message is forwarded to the local Python agent, which maps deterministic companion requests to `altoclef_task` and otherwise asks the LLM to choose from approved tools. Task counts are limited to 1 through 64; malformed items, entities, parameters, and verbs are rejected before execution.

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
