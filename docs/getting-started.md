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
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\gradlew.bat :1.20.1:build --offline --no-daemon
```

The 1.20.1 remapped mod jar is written below `client/altoclef/versions/1.20.1/build/libs`.

## First-Run Safety Setup

After Fabric loads the mod once, create or edit the generated files in the companion client's game directory. AltoClef stores them below `altoclef/`:

- `altoclef/configs/butler.json`: keep `useButlerWhitelist` set to `true`.
- `altoclef/altoclef_butler_whitelist.txt`: add the exact Minecraft name of each player allowed to command the companion.
- `altoclef/altoclef_settings.json`: review the configured `homeBasePosition` before using `home`.

The companion listens only to the public chat channel and ignores private whispers. A player on the butler whitelist talks directly in public chat; each message is forwarded to the local Python agent, which maps deterministic companion requests to `altoclef_task` and delegates the rest to the optional LLM. Each request must contain exactly one approved action; chained commands are rejected before task execution. Supported task commands are `collect <item> <count>`, `craft <item> <count>`, `smelt <item> <count>`, `goto <x> <y> <z>`, `follow`, `come`, `home`, `attack <entity> <count>`, `protect`, `unprotect`, `give <item> <count>`, `status`, `queue`, and `stop`. Counts are limited to 1 through 64. The first active requester becomes the single session owner; other whitelisted players are rejected until the session finishes or receives `stop`.

## Agent Backend

The Python agent backend (`agent_backend/`) owns the LLM conversation and connects to Java over a local WebSocket at `ws://127.0.0.1:8765`. The Java client auto-starts it from the `script` path in `agent/bridge.properties` and keeps retrying until it connects. Copy `agent/bridge.properties` into the companion instance's `agent/` directory and set an absolute `script` path to `agent_backend/start-agent.bat` (Windows), then adjust `host`/`port` if needed.

The live Agent monitor is separate. Start it with `python -m agent_backend.agent_gui` or `agent_backend/start-agent-gui.bat`; it connects to `/monitor` and displays live messages, tool calls, results, and retries.

## Optional Remote LLM

The optional LLM reads an OpenAI-compatible configuration from `agent/llm.properties` in the companion instance's game directory. It does not require JVM arguments. Copy the following shape and replace the values locally; do not commit the file because the API key is sensitive:

```properties
url=http://example.invalid/v1
model=your-model-id
key=your-api-key
```

The backend requests `POST {url}/chat/completions`. Both a server root (`https://host`) and a base URL ending in `/v1` are accepted. Before testing Minecraft, verify that `GET {url}/models` lists the configured model and that a minimal chat-completions request returns HTTP 200. A gateway returning HTTP 503 means the model backend is unavailable; the companion cannot produce an AI tool action until the service is restored.

Resource requests use AltoClef's catalogue and may mine, craft, smelt, place supporting blocks, and travel across dimensions. Do not add locations to the protected-position settings if the companion must modify them. `give` deliberately does not acquire missing items: it only transfers items already in the companion inventory.

## In-Game AI Configuration

The companion supports in-game model and URL configuration via the `@ai_config` command:

```
@ai_config show
@ai_config model agnes-2.5-flash
@ai_config url http://47.112.211.111:55418/v1
```

For security, API keys cannot be entered in Minecraft chat. Edit `agent/llm.properties` directly to set the `key` field, then restart the Minecraft client.

## PCL Two-Client Setup

Run two separate PCL instances against the same LAN world:

- A normal vanilla 1.20.1 instance for the player.
- A Fabric 1.20.1 instance for the companion, with the built jar placed in that instance's `mods/` directory.

The companion account joins the LAN world as an ordinary player. Only the Fabric companion instance needs this mod.

## Verification

Use a self-managed LAN world with two normal client identities. From the whitelisted owner, talk in public chat to test `collect iron_ingot 1`, `follow`, `protect`, `give <item> <count>`, then `stop`. `follow` and `protect` are persistent tasks with no two-minute timeout; stop them explicitly. The companion should reply in public chat with queue, execution, completion, failure, or cancellation status. A player absent from the whitelist must not be able to trigger a task, and a second whitelisted player without session ownership must be rejected.

## Debugging

### Nearby Blocks Inspector

Run the block debug GUI to verify the companion can see nearby blocks:

```bash
cd "F:/code/Codex/AI for Minecraft/minecraft-ai-companion"
python -m agent_backend.block_debug_gui
```

This connects to the same WebSocket bridge used by the Python agent and calls `inspect_nearby_blocks`. If the table remains empty, the companion's `BlockScanner` cache may be empty or the Java bridge may not be accepting connections from the debug client.

### Log Files

- Java log: `<instance>/logs/latest.log`
- Python log: `<instance>/agent/python-agent.log`
