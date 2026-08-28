# Minecraft AI 玩伴

[English](README.md)

这是一个面向自主管理 Minecraft 局域网世界的陪伴型机器人项目。

**首个目标版本：Minecraft Java Edition 1.20.1。**

项目目标不是自主速通，而是让机器人更像一名合作玩家：陪在玩家身边、响应白名单玩家在公共聊天中的请求、完成有限协助任务、报告危险，并在玩家要求时立即停止。

## 架构决策

玩伴将在一个独立的 Minecraft Java 客户端中运行。首个基线是 Minecraft Java Edition 1.20.1、Fabric，以及内置兼容 Baritone 的 MiranCZ AltoClef 分支。Minecraft 1.20.1 的运行环境为 Java 17；当前多版本客户端源码的构建环境需要 Java 21。

```text
玩家的 Minecraft 客户端                 玩伴的 Minecraft 客户端
        |                                          |
        +--------------- 局域网世界 ----------------+
                                                   |
                                  Companion Mod
                                  - 公共聊天监听（白名单鉴权）
                                  - 反射式安全控制器
                                  - AltoClef 任务与 Baritone 移动
                                  - 与 Python LLM 后端的 WebSocket 桥接
```

这会需要第二个 Minecraft 客户端实例，但能让玩伴拥有与玩家相同的客户端世界模型和交互能力。BaritonePlus 的公开代码维护活跃度较低，因此不作为运行时依赖。

## Java 端职责

Fabric mod 现在只保留两个职责：

1. **反射式安全控制器**：不等待 Python/LLM。饥饿值与饱和度保持接近满值（从而支持自然回血），附近有敌对生物时自动用 hotbar 上攻击伤害最高的近战武器反击。环境脱险——水面换气、逃离岩浆、扑灭火焰——由 AltoClef 原生生存链临时抢占执行，脱险后原任务继续，不会取消。水、岩浆、着火、饥饿或低生命值都不会触发任务暂停；只有窒息和无法自动恢复的坠落仍会安全暂停。
2. **Python 桥接**：本地 WebSocket 桥接（`AgentBridge`）向 Python 后端暴露工具注册表并回传工具结果。Python 端持有 LLM 对话，Java 端只执行已批准工具，如 `observe_world`、`altoclef_task`、`baritone_goal`、`move`、`look`、`attack_entity`、`use_item`、`interact_block`、`chat_public`。

旧的 Java 端 LLM 解析器、教程索引、任务经验存储、私聊命令适配器均已删除。

## 聊天通道

白名单玩家直接在公共聊天栏中对玩伴说话——玩伴不再读取私聊。每条消息都会转发给本地 Python agent：确定性陪伴请求（`come`、`follow`、`collect`、`craft`、`smelt`、`goto`、`attack`、`protect`、`give`、`stop`、`status`、`queue`）会直接映射为 `altoclef_task`，其余请求交给 LLM 在已批准工具中选择。数量限制在 1 到 64；无效物品、实体、参数或未知动词会在启动任务前被拒绝。

每一条任务都必须依据游戏状态返回已验证的成功、失败或取消结果。机器人不得在未检查游戏状态时宣称任务成功。

## 约束

- 初始验证只在自主管理的 Minecraft Java 1.20.1 局域网世界中进行。
- 玩伴以被世界接受的独立玩家身份连接。
- 任何非白名单玩家的公共聊天都会被拒绝；不包含认证绕过、自动注册或登录、反作弊规避。
- 玩伴不会在公共聊天中无节制发言，只回复当前白名单主人的请求与状态。
- 丢物品、攻击实体、修改建筑等能力仍停留在桥接工具层之后；LLM 只能选择已批准的高层意图，不能输出原始协议包或任意聊天。

## 路线图

1. 完成专用 Fabric 客户端、公共聊天白名单与生命周期日志。
2. 实现反射式安全控制器：自动进食、自动反击、溺水逃脱、危险停止。
3. 加固 Python 桥接：可靠导航、家园记忆、寻路失败恢复与状态回报。
4. 增加有限的合作任务，如附近资源采集与基础保护。
5. 为 LLM 增加短期记忆与人格，同时保持只能选择已批准的高层意图。

完整的验收场景和进度记录见 [docs/roadmap.md](docs/roadmap.md)。
本机构建和首次运行的安全配置见 [docs/getting-started.md](docs/getting-started.md)。
