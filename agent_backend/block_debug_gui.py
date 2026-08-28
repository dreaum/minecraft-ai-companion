import asyncio
import json
import threading
import uuid
import tkinter as tk
from tkinter import ttk, messagebox

import websockets

WS_URL = "ws://127.0.0.1:8765"


class BlockDebugGui:
    def __init__(self, root):
        self.root = root
        self.root.title("Minecraft AI Companion - Nearby Blocks")
        self.root.geometry("760x520")
        self.loop = None
        self.ws = None
        self.pending = {}
        self.thread = threading.Thread(target=self._run_loop, daemon=True)
        self.thread.start()

        controls = ttk.Frame(root, padding=8)
        controls.pack(fill=tk.X)
        ttk.Label(controls, text="扫描半径").pack(side=tk.LEFT)
        self.radius = tk.StringVar(value="16")
        ttk.Entry(controls, textvariable=self.radius, width=8).pack(side=tk.LEFT, padx=(4, 12))
        ttk.Label(controls, text="最大结果").pack(side=tk.LEFT)
        self.maximum = tk.StringVar(value="500")
        ttk.Entry(controls, textvariable=self.maximum, width=8).pack(side=tk.LEFT, padx=(4, 12))
        self.button = ttk.Button(controls, text="请求伙伴周围方块", command=self.request)
        self.button.pack(side=tk.LEFT)
        self.status = tk.StringVar(value="正在连接 Java bridge...")
        ttk.Label(root, textvariable=self.status, padding=(8, 0)).pack(anchor=tk.W)

        columns = ("id", "x", "y", "z", "distance")
        self.table = ttk.Treeview(root, columns=columns, show="headings")
        for column, title, width in (("id", "方块", 330), ("x", "X", 80), ("y", "Y", 80), ("z", "Z", 80), ("distance", "距离", 100)):
            self.table.heading(column, text=title)
            self.table.column(column, width=width, anchor=tk.W)
        self.table.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)
        self.root.protocol("WM_DELETE_WINDOW", self.close)

    def _run_loop(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self._connect())
        self.loop.run_forever()

    async def _connect(self):
        try:
            self.ws = await websockets.connect(WS_URL)
            # Java bridge sends hello first; acknowledge it before issuing tool calls.
            asyncio.create_task(self._receive())
            self.root.after(0, lambda: self.status.set("已连接 Java bridge"))
        except Exception as exc:
            error_text = str(exc)
            self.root.after(0, lambda: self.status.set("连接失败: " + error_text))

    async def _receive(self):
        try:
            async for raw in self.ws:
                message = json.loads(raw)
                if message.get("type") == "tool_result":
                    future = self.pending.pop(message.get("id"), None)
                    if future and not future.done(): future.set_result(message)
                elif message.get("type") == "hello":
                    await self.ws.send(json.dumps({"type": "hello_ack", "protocol_version": 1}))
                    self.root.after(0, lambda: self.status.set("已连接 Java bridge"))
                elif message.get("type") == "hello_ack":
                    self.root.after(0, lambda: self.status.set("已连接 Java bridge"))
                elif message.get("type") == "agent_error":
                    self.root.after(0, lambda: self.status.set("Java bridge 错误: " + message.get("error", "unknown")))
        except Exception as exc:
            error_text = str(exc)
            self.root.after(0, lambda: self.status.set("连接断开: " + error_text))

    def request(self):
        try:
            radius = max(1, min(64, float(self.radius.get())))
            maximum = max(1, min(1000, int(self.maximum.get())))
        except ValueError:
            messagebox.showerror("参数错误", "半径必须是数字，最大结果必须是整数")
            return
        if not self.ws or not self.loop:
            messagebox.showerror("未连接", "Java bridge 尚未连接")
            return
        request_id = str(uuid.uuid4())
        future = asyncio.run_coroutine_threadsafe(self._call(request_id, radius, maximum), self.loop)
        def done(f):
            try: self._render(f.result())
            except Exception as exc:
                error_text = str(exc)
                self.root.after(0, lambda: self.status.set("请求失败: " + error_text))
        future.add_done_callback(done)

    async def _call(self, request_id, radius, maximum):
        future = self.loop.create_future()
        self.pending[request_id] = future
        await self.ws.send(json.dumps({"type": "tool_call", "id": request_id, "protocol_version": 1,
                                       "tool": "inspect_nearby_blocks", "arguments": {
                                           "radius": radius, "max_results": maximum}}))
        return await asyncio.wait_for(future, timeout=15)

    def _render(self, message):
        def update():
            for item in self.table.get_children(): self.table.delete(item)
            if message.get("status") != "completed":
                self.status.set("请求失败: " + str(message.get("error", message)))
                return
            result = message.get("result") or message.get("observation") or {}
            rows = result.get("blocks", [])
            for row in rows:
                self.table.insert("", tk.END, values=(row.get("id", ""), row.get("x", ""), row.get("y", ""), row.get("z", ""), f"{row.get('distance', 0):.2f}"))
            self.status.set(f"扫描完成: {len(rows)} 个方块，数据源: {result.get('source', 'BlockScanner')}")
        self.root.after(0, update)

    def close(self):
        if self.loop and self.ws:
            asyncio.run_coroutine_threadsafe(self.ws.close(), self.loop)
            self.loop.call_soon_threadsafe(self.loop.stop)
        self.root.destroy()


if __name__ == "__main__":
    app = tk.Tk()
    BlockDebugGui(app)
    app.mainloop()
