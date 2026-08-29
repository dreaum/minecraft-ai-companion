"""Small read-only Tkinter monitor for the Python agent backend."""
import json
import queue
import threading
import tkinter as tk
from tkinter import scrolledtext
import argparse
import asyncio
import websockets


class AgentGui:
    def __init__(self, title="Minecraft Agent"):
        self.title = title
        self.events = queue.Queue()
        self.thread = None

    def publish(self, event):
        self.events.put(event)

    async def monitor(self, url):
        async with websockets.connect(url) as socket:
            async for raw in socket:
                message = json.loads(raw)
                if message.get("type") == "agent_event":
                    self.publish(message.get("event") or {})

    def start_in_thread(self):
        if self.thread and self.thread.is_alive():
            return
        self.thread = threading.Thread(target=self._run, name="agent-gui", daemon=True)
        self.thread.start()

    def _run(self):
        self._run_window()

    def _run_window(self):
        root = tk.Tk()
        root.title(self.title)
        root.geometry("760x480")
        status = tk.StringVar(value="Backend running")
        tk.Label(root, textvariable=status, anchor="w").pack(fill="x", padx=8, pady=(8, 0))
        output = scrolledtext.ScrolledText(root, state="disabled", wrap=tk.WORD)
        output.pack(fill="both", expand=True, padx=8, pady=8)
        tk.Button(root, text="Clear", command=lambda: self._clear(output)).pack(anchor="e", padx=8, pady=(0, 8))

        def drain():
            try:
                while True:
                    event = self.events.get_nowait()
                    kind = event.get("kind", "event")
                    if kind == "user": line = f"USER [{event.get('user', '')}]: {event.get('text', '')}"
                    elif kind == "agent_message": line = f"AGENT: {event.get('text', '')}"
                    elif kind == "tool_call": line = f"TOOL -> {event.get('tool')}: {json.dumps(event.get('arguments', {}), ensure_ascii=False)}"
                    elif kind == "tool_result": line = f"TOOL <- {event.get('status')}: {event.get('error') or json.dumps(event.get('observation') or {}, ensure_ascii=False)}"
                    elif kind == "retry": line = f"RETRY: {event.get('reason', '')}"
                    else: line = json.dumps(event, ensure_ascii=False)
                    output.configure(state="normal"); output.insert(tk.END, line + "\n"); output.see(tk.END); output.configure(state="disabled")
                    status.set(f"Last event: {kind}")
            except queue.Empty:
                pass
            root.after(100, drain)

        drain()
        root.mainloop()

    @staticmethod
    def _clear(output):
        output.configure(state="normal"); output.delete("1.0", tk.END); output.configure(state="disabled")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="ws://127.0.0.1:8765/monitor")
    args = parser.parse_args()
    gui = AgentGui()
    gui.start_in_thread()
    asyncio.run(gui.monitor(args.url))


if __name__ == "__main__":
    main()
